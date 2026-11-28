package dev.abeta.kmp.database

import app.cash.turbine.test
import dev.abeta.kmp.core.coroutines.ioDispatcher
import dev.abeta.kmp.core.time.MutableClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class DatabaseKeyValueStoreTest {
    private fun openInMemory(clock: MutableClock = MutableClock(1_000L)): AbetaLocalDatabase =
        createAbetaLocalDatabase(
            config = AbetaDatabaseConfig(inMemory = true),
            clock = clock,
        )

    @Test
    fun putGetRemoveClearAndKeysPersistToRoomDatabase() =
        runTest {
            val localDb = openInMemory()
            val store = DatabaseKeyValueStore(database = localDb, namespace = "session_kv")
            try {
                store.awaitSync()
                assertNull(store.getString("missing"))
                assertFalse(store.contains("missing"))
                assertTrue(store.keys().isEmpty())

                store.putString("token", "tok-001")
                store.putString("locale", "en-US")
                assertEquals("tok-001", store.getString("token"))
                assertEquals("en-US", store.getString("locale"))
                assertTrue(store.contains("token"))
                assertEquals(setOf("token", "locale"), store.keys())

                store.awaitSync()
                assertEquals("tok-001", localDb.getValue("session_kv", "token"))
                assertEquals("en-US", localDb.getValue("session_kv", "locale"))

                store.putString("token", "tok-002")
                store.awaitSync()
                assertEquals("tok-002", store.getString("token"))
                assertEquals("tok-002", localDb.getValue("session_kv", "token"))

                store.remove("locale")
                assertNull(store.getString("locale"))
                assertFalse(store.contains("locale"))
                assertEquals(setOf("token"), store.keys())
                store.awaitSync()
                assertNull(localDb.getValue("session_kv", "locale"))

                store.clear()
                assertTrue(store.keys().isEmpty())
                store.awaitSync()
                assertEquals(emptyList(), localDb.listKeys("session_kv"))
            } finally {
                store.close()
                localDb.close()
            }
        }

    @Test
    fun hydratesExistingRowsAndPersistsAcrossInstances() =
        runTest {
            val localDb = openInMemory()
            val writer = DatabaseKeyValueStore(database = localDb, namespace = "persisted")
            try {
                writer.putString("user_id", "usr-42")
                writer.putString("theme", "dark")
                writer.awaitSync()
            } finally {
                writer.close()
            }

            val reader = DatabaseKeyValueStore(database = localDb, namespace = "persisted")
            try {
                reader.awaitSync()
                assertEquals("usr-42", reader.getString("user_id"))
                assertEquals("dark", reader.getString("theme"))
                assertEquals(setOf("user_id", "theme"), reader.keys())
            } finally {
                reader.close()
            }

            // Writes issued immediately before hydration finishes must preserve existing rows too.
            val immediateWriter = DatabaseKeyValueStore(database = localDb, namespace = "persisted")
            try {
                immediateWriter.putString("theme", "light")
                immediateWriter.putString("region", "us-west")
                immediateWriter.awaitSync()
                assertEquals("usr-42", immediateWriter.getString("user_id"))
                assertEquals("light", immediateWriter.getString("theme"))
                assertEquals("us-west", immediateWriter.getString("region"))
                assertEquals(setOf("user_id", "theme", "region"), immediateWriter.keys())
            } finally {
                immediateWriter.close()
                localDb.close()
            }
        }

    @Test
    fun isolatesNamespacesOnPutKeysRemoveAndClear() =
        runTest {
            val localDb = openInMemory()
            val alpha = DatabaseKeyValueStore(database = localDb, namespace = "alpha")
            val beta = DatabaseKeyValueStore(database = localDb, namespace = "beta")
            try {
                alpha.putString("shared", "from-alpha")
                alpha.putString("only_alpha", "1")
                beta.putString("shared", "from-beta")
                beta.putString("only_beta", "2")
                alpha.awaitSync()
                beta.awaitSync()

                assertEquals("from-alpha", alpha.getString("shared"))
                assertEquals("from-beta", beta.getString("shared"))
                assertEquals(setOf("shared", "only_alpha"), alpha.keys())
                assertEquals(setOf("shared", "only_beta"), beta.keys())

                alpha.clear()
                alpha.awaitSync()
                assertTrue(alpha.keys().isEmpty())
                assertEquals("from-beta", localDb.getValue("beta", "shared"))
                assertEquals("from-beta", beta.getString("shared"))
                assertEquals(setOf("shared", "only_beta"), beta.keys())
            } finally {
                alpha.close()
                beta.close()
                localDb.close()
            }
        }

    @Test
    fun observeStringAndObserveKeyEmitDistinctReactiveUpdates() =
        runTest {
            val localDb = openInMemory()
            val store = DatabaseKeyValueStore(database = localDb, namespace = "reactive")
            try {
                store.awaitSync()
                store.observeString("cart_count").test(timeout = 5.seconds) {
                    assertNull(awaitItem())

                    store.putString("cart_count", "1")
                    assertEquals("1", awaitItem())

                    // Duplicate value or unrelated key must not re-emit.
                    store.putString("cart_count", "1")
                    store.putString("other_key", "ignored")
                    store.awaitSync()
                    expectNoEvents()

                    store.putString("cart_count", "4")
                    assertEquals("4", awaitItem())

                    store.remove("cart_count")
                    assertNull(awaitItem())

                    store.putString("cart_count", "2")
                    assertEquals("2", awaitItem())

                    store.clear()
                    assertNull(awaitItem())
                    store.awaitSync()
                    cancelAndIgnoreRemainingEvents()
                }
            } finally {
                store.close()
                localDb.close()
            }
        }

    @Test
    fun validatesBlankIdentifiersAndCloseLeavesCallerScopeActive() =
        runTest {
            val localDb = openInMemory()
            val externalScope = CoroutineScope(SupervisorJob() + ioDispatcher)
            try {
                assertFailsWith<IllegalArgumentException> {
                    DatabaseKeyValueStore(database = localDb, namespace = "   ")
                }

                val store = DatabaseKeyValueStore(database = localDb, scope = externalScope)
                assertFailsWith<IllegalArgumentException> {
                    store.putString("   ", "val")
                }
                store.putString("k", "v")
                store.awaitSync()
                assertEquals("v", localDb.getValue(DatabaseKeyValueStore.DEFAULT_NAMESPACE, "k"))

                store.close()
                assertTrue(externalScope.isActive)
                // Writes after close are safely ignored without hanging awaitSync.
                store.putString("after_close", "ignored")
                store.awaitSync()
                assertNull(localDb.getValue(DatabaseKeyValueStore.DEFAULT_NAMESPACE, "after_close"))
            } finally {
                localDb.close()
            }
        }
}
