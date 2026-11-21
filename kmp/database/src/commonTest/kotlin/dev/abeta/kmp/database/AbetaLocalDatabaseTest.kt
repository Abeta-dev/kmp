package dev.abeta.kmp.database

import app.cash.turbine.test
import dev.abeta.kmp.core.identity.SequentialUuidGenerator
import dev.abeta.kmp.core.time.MutableClock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class AbetaLocalDatabaseTest {
    private fun openInMemory(
        clock: MutableClock = MutableClock(1_000L),
        uuidGenerator: SequentialUuidGenerator = SequentialUuidGenerator(prefix = "mut-"),
    ): AbetaLocalDatabase =
        createAbetaLocalDatabase(
            config = AbetaDatabaseConfig(inMemory = true),
            clock = clock,
            uuidGenerator = uuidGenerator,
        )

    @Test
    fun screenCacheUpsertTtlFilteringEtagUpdateAndEviction() =
        runTest {
            val clock = MutableClock(10_000L)
            val localDb = openInMemory(clock = clock)
            try {
                val initial =
                    localDb.putScreenCache(
                        screenId = "home",
                        payloadJson = """{"title":"Home v1"}""",
                        etag = "\"v1\"",
                        ttlMillis = 5_000L,
                    )
                assertEquals("home", initial.screenId)
                assertEquals(10_000L, initial.cachedAtEpochMillis)
                assertEquals(15_000L, initial.expiresAtEpochMillis)

                val permanent =
                    localDb.putScreenCache(
                        screenId = "profile",
                        payloadJson = """{"title":"Profile"}""",
                        etag = "\"p1\"",
                        ttlMillis = null,
                    )
                assertNull(permanent.expiresAtEpochMillis)
                assertEquals(2, localDb.screenCacheCount())

                // Update ETag and payload in-place
                clock.advanceByMillis(1_000L)
                localDb.putScreenCache(
                    screenId = "home",
                    payloadJson = """{"title":"Home v2"}""",
                    etag = "\"v2\"",
                    ttlMillis = 5_000L,
                )
                val updated = localDb.getFreshScreenCache("home")
                assertNotNull(updated)
                assertEquals("""{"title":"Home v2"}""", updated.payloadJson)
                assertEquals("\"v2\"", updated.etag)
                assertEquals(16_000L, updated.expiresAtEpochMillis)
                assertEquals(2, localDb.screenCacheCount())

                // Advance to exact expiration boundary -> stale for fresh lookup, then evicted
                clock.setEpochMillis(16_000L)
                assertNull(localDb.getFreshScreenCache("home"))
                assertNotNull(localDb.getFreshScreenCache("profile"))

                val evicted = localDb.evictExpiredScreens()
                assertEquals(1, evicted)
                assertEquals(1, localDb.screenCacheCount())
                assertEquals(1, localDb.deleteScreenCache("profile"))
                assertEquals(0, localDb.screenCacheCount())
            } finally {
                localDb.close()
            }
        }

    @Test
    fun screenCacheFlowEmitsReactiveUpdates() =
        runTest {
            val clock = MutableClock(2_000L)
            val localDb = openInMemory(clock = clock)
            try {
                localDb.observeScreenCache("store-home").test(timeout = 5.seconds) {
                    assertNull(awaitItem())

                    localDb.putScreenCache(
                        screenId = "store-home",
                        payloadJson = """{"version":1}""",
                        etag = "\"etag-1\"",
                    )
                    val first = awaitItem()
                    assertNotNull(first)
                    assertEquals("\"etag-1\"", first.etag)

                    localDb.putScreenCache(
                        screenId = "store-home",
                        payloadJson = """{"version":2}""",
                        etag = "\"etag-2\"",
                    )
                    val second = awaitItem()
                    assertNotNull(second)
                    assertEquals("\"etag-2\"", second.etag)

                    localDb.clearScreenCache()
                    assertNull(awaitItem())
                    cancelAndIgnoreRemainingEvents()
                }
            } finally {
                localDb.close()
            }
        }

    @Test
    fun offlineMutationsPreserveFifoTrackRetriesPurgeDeadLettersAndTrim() =
        runTest {
            val clock = MutableClock(50_000L)
            val localDb = openInMemory(clock = clock)
            try {
                // Enqueue two mutations at the same timestamp to verify deterministic rowid FIFO order
                val m1 = localDb.enqueueMutation("cart_add", """{"sku":"A"}""", id = "m-1")
                val m2 = localDb.enqueueMutation("cart_add", """{"sku":"B"}""", id = "m-2")
                clock.advanceByMillis(100L)
                val m3 = localDb.enqueueMutation("checkout", """{"cart":1}""", id = "m-3")

                assertEquals(listOf(m1.id, m2.id, m3.id), localDb.pendingMutations().map { it.id })

                // Increment retries with diagnostic errors
                assertEquals(1, localDb.markMutationRetry("m-1", error = "503 Service Unavailable"))
                assertEquals(1, localDb.markMutationRetry("m-1", error = "504 Gateway Timeout"))
                assertEquals(1, localDb.markMutationRetry("m-2", error = "Network offline"))

                val afterRetry = localDb.pendingMutations().associateBy { it.id }
                assertEquals(2, afterRetry["m-1"]?.retryCount)
                assertEquals("504 Gateway Timeout", afterRetry["m-1"]?.lastError)
                assertEquals(1, afterRetry["m-2"]?.retryCount)
                assertEquals(0, afterRetry["m-3"]?.retryCount)

                // Purge dead letters with maxRetries = 2 -> evicts m-1
                assertEquals(1, localDb.purgeDeadLetters(maxRetries = 2))
                assertEquals(listOf("m-2", "m-3"), localDb.pendingMutations().map { it.id })

                // Complete m-2 -> only m-3 remains
                assertEquals(1, localDb.completeMutation("m-2"))
                assertEquals(listOf("m-3"), localDb.pendingMutations().map { it.id })

                // Enqueue additional mutations and enforce maxQueueSize trimming (retains newest)
                clock.advanceByMillis(50L)
                localDb.enqueueMutation("ping", """{"n":1}""", id = "m-4", maxQueueSize = 2)
                clock.advanceByMillis(50L)
                localDb.enqueueMutation("ping", """{"n":2}""", id = "m-5", maxQueueSize = 2)

                assertEquals(listOf("m-4", "m-5"), localDb.pendingMutations().map { it.id })

                assertEquals(1, localDb.trimMutationQueue(maxSize = 1))
                assertEquals(listOf("m-5"), localDb.pendingMutations().map { it.id })

                localDb.clearMutations()
                assertEquals(emptyList(), localDb.pendingMutations())
            } finally {
                localDb.close()
            }
        }

    @Test
    fun offlineMutationPendingCountFlowEmitsUpdates() =
        runTest {
            val localDb = openInMemory()
            try {
                localDb.observePendingMutationCount().test(timeout = 5.seconds) {
                    assertEquals(0, awaitItem())

                    val first = localDb.enqueueMutation("sync_profile", """{"name":"Ada"}""")
                    assertEquals(1, awaitItem())

                    val second = localDb.enqueueMutation("sync_prefs", """{"theme":"dark"}""")
                    assertEquals(2, awaitItem())

                    localDb.completeMutation(first.id)
                    assertEquals(1, awaitItem())

                    localDb.completeMutation(second.id)
                    assertEquals(0, awaitItem())
                    cancelAndIgnoreRemainingEvents()
                }
            } finally {
                localDb.close()
            }
        }

    @Test
    fun keyValueRecordsSupportNamespacesTtlExpirationAndReactiveFlow() =
        runTest {
            val clock = MutableClock(100_000L)
            val localDb = openInMemory(clock = clock)
            try {
                localDb.putValue(namespace = "session", key = "token", value = "abc", ttlMillis = 1_000L)
                localDb.putValue(namespace = "session", key = "region", value = "us-east")
                localDb.putValue(namespace = "prefs", key = "token", value = "unrelated-pref")

                assertEquals("abc", localDb.getValue("session", "token"))
                assertEquals("us-east", localDb.getValue("session", "region"))
                assertEquals("unrelated-pref", localDb.getValue("prefs", "token"))
                assertEquals(listOf("region", "token"), localDb.listKeys("session"))
                assertEquals(listOf("token"), localDb.listKeys("prefs"))

                // Expire only the TTL entry in `session`
                clock.advanceByMillis(1_000L)
                assertNull(localDb.getValue("session", "token"))
                assertEquals("us-east", localDb.getValue("session", "region"))
                assertEquals(1, localDb.evictExpiredValues())
                assertEquals(listOf("region"), localDb.listKeys("session"))

                // Clear `session` without touching `prefs`
                assertEquals(1, localDb.clearNamespace("session"))
                assertEquals(emptyList(), localDb.listKeys("session"))
                assertEquals("unrelated-pref", localDb.getValue("prefs", "token"))

                // Observe reactive changes on `prefs/theme`
                localDb.observeValue("prefs", "theme").test(timeout = 5.seconds) {
                    assertNull(awaitItem())

                    localDb.putValue("prefs", "theme", "light")
                    assertEquals("light", awaitItem())

                    localDb.putValue("prefs", "theme", "dark")
                    assertEquals("dark", awaitItem())

                    localDb.removeValue("prefs", "theme")
                    assertNull(awaitItem())
                    cancelAndIgnoreRemainingEvents()
                }
            } finally {
                localDb.close()
            }
        }

    @Test
    fun inputValidationRejectsBlankIdentifiersAndInvalidBounds() =
        runTest {
            assertFailsWith<IllegalArgumentException> {
                AbetaDatabaseConfig(databaseName = "   ", inMemory = false)
            }
            val localDb = openInMemory()
            try {
                assertFailsWith<IllegalArgumentException> {
                    localDb.putScreenCache(screenId = " ", payloadJson = "{}")
                }
                assertFailsWith<IllegalArgumentException> {
                    localDb.putScreenCache(screenId = "home", payloadJson = "{}", ttlMillis = 0L)
                }
                assertFailsWith<IllegalArgumentException> {
                    localDb.enqueueMutation(mutationType = "", payloadJson = "{}")
                }
                assertFailsWith<IllegalArgumentException> {
                    localDb.purgeDeadLetters(maxRetries = 0)
                }
                assertFailsWith<IllegalArgumentException> {
                    localDb.putValue(namespace = "", key = "k", value = "v")
                }
                assertFailsWith<IllegalArgumentException> {
                    localDb.putValue(namespace = "ns", key = "k", value = "v", ttlMillis = -5L)
                }
            } finally {
                localDb.close()
            }
        }
}
