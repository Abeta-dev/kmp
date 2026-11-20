package dev.abeta.kmp.state

import app.cash.turbine.test
import com.russhwolf.settings.MapSettings
import dev.abeta.kmp.core.error.NetworkError
import dev.abeta.kmp.core.identity.SequentialUuidGenerator
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.storage.InMemoryKeyValueStore
import dev.abeta.kmp.core.time.MutableClock
import dev.abeta.kmp.state.flags.FeatureFlagKey
import dev.abeta.kmp.state.flags.InMemoryFeatureFlagProvider
import dev.abeta.kmp.state.flags.LayeredFeatureFlagProvider
import dev.abeta.kmp.state.flags.PersistentFeatureFlagStore
import dev.abeta.kmp.state.flags.getDouble
import dev.abeta.kmp.state.flags.getInt
import dev.abeta.kmp.state.flags.getString
import dev.abeta.kmp.state.flags.isEnabled
import dev.abeta.kmp.state.preferences.DefaultPreferenceStore
import dev.abeta.kmp.state.preferences.PreferenceKey
import dev.abeta.kmp.state.preferences.getBoolean
import dev.abeta.kmp.state.preferences.getDouble
import dev.abeta.kmp.state.preferences.getInt
import dev.abeta.kmp.state.preferences.getLong
import dev.abeta.kmp.state.preferences.getObject
import dev.abeta.kmp.state.preferences.observeBoolean
import dev.abeta.kmp.state.preferences.observeDouble
import dev.abeta.kmp.state.preferences.observeObject
import dev.abeta.kmp.state.preferences.putBoolean
import dev.abeta.kmp.state.preferences.putDouble
import dev.abeta.kmp.state.preferences.putInt
import dev.abeta.kmp.state.preferences.putLong
import dev.abeta.kmp.state.preferences.putObject
import dev.abeta.kmp.state.session.AuthSessionState
import dev.abeta.kmp.state.session.InMemorySessionStore
import dev.abeta.kmp.state.session.PersistentSessionStore
import dev.abeta.kmp.state.session.SessionLifecycleManager
import dev.abeta.kmp.state.storage.SettingsKeyValueStore
import dev.abeta.kmp.state.sync.OfflineMutationQueue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Serializable
private data class UserProfilePrefsV2(
    val theme: String,
    val compactMode: Boolean,
)

class StateModulesTest {
    @Test
    fun `settings key value store and reactive preferences support migration and snapshots`() =
        runTest {
            val backing = SettingsKeyValueStore(MapSettings(), keyPrefix = "prefs")
            val store = DefaultPreferenceStore(backing)
            val darkModeKey = PreferenceKey<Boolean>("dark_mode")
            val timeoutKey = PreferenceKey<Long>("timeout_ms")
            val ratioKey = PreferenceKey<Double>("ratio")
            val profileKey = PreferenceKey<UserProfilePrefsV2>("profile")

            store.observeBoolean(darkModeKey, defaultValue = false).test {
                assertEquals(false, awaitItem())
                store.putBoolean(darkModeKey, true)
                assertEquals(true, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            store.putInt(PreferenceKey("retries"), 4)
            store.putLong(timeoutKey, 5000L)
            store.putDouble(ratioKey, 0.75)
            assertEquals(4, store.getInt(PreferenceKey("retries")))
            assertEquals(5000L, store.getLong(timeoutKey))
            assertEquals(0.75, store.getDouble(ratioKey))

            store.observeDouble(ratioKey).test {
                assertEquals(0.75, awaitItem())
                store.putDouble(ratioKey, 1.25)
                assertEquals(1.25, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            // Simulate v1 legacy stored object migrating to v2
            store.putString(
                PreferenceKey("profile"),
                """{"_v":1,"_data":{"theme":"emerald"}}""",
            )
            val migrated =
                store.getObject(
                    key = profileKey,
                    serializer = UserProfilePrefsV2.serializer(),
                    version = 2,
                ) { oldData, oldVersion ->
                    assertEquals(1, oldVersion)
                    UserProfilePrefsV2(
                        theme = oldData.jsonObject["theme"]?.jsonPrimitive?.content ?: "indigo",
                        compactMode = true,
                    )
                }
            assertEquals(UserProfilePrefsV2(theme = "emerald", compactMode = true), migrated)

            store.observeObject(profileKey, UserProfilePrefsV2.serializer(), version = 2).test {
                assertEquals(UserProfilePrefsV2(theme = "emerald", compactMode = true), awaitItem())
                val updated = UserProfilePrefsV2(theme = "amber", compactMode = false)
                store.putObject(profileKey, updated, UserProfilePrefsV2.serializer(), version = 2)
                assertEquals(updated, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            val exported = store.exportSnapshotJson()
            val restoredStore = DefaultPreferenceStore(InMemoryKeyValueStore())
            assertTrue(restoredStore.importSnapshotJson(exported, replaceExisting = true))
            assertEquals(true, restoredStore.getBoolean(darkModeKey))
        }

    @Test
    fun `persistent session store and session lifecycle handle auth and expiry`() =
        runTest {
            val inMemory = InMemorySessionStore(mapOf("boot" to JsonPrimitive(true)))
            assertEquals(true, inMemory.get("boot")?.jsonPrimitive?.boolean)

            val backing = InMemoryKeyValueStore()
            val sessionA = PersistentSessionStore(backing)
            sessionA.put("cart_count", JsonPrimitive(3))
            val sessionB = PersistentSessionStore(backing)
            assertEquals(3, sessionB.get("cart_count")?.jsonPrimitive?.int)

            val clock = MutableClock(10_000L)
            val uuids = SequentialUuidGenerator()
            val lifecycle =
                SessionLifecycleManager(
                    backingStore = InMemoryKeyValueStore(),
                    clock = clock,
                    uuidGenerator = uuids,
                    idleTimeoutMillis = 5_000L,
                )

            assertTrue(lifecycle.authState.value is AuthSessionState.Unauthenticated)
            val initialSession = lifecycle.metadata.value.sessionId
            val anonId = lifecycle.metadata.value.anonymousId

            lifecycle.signIn(userId = "user-42", displayName = "Ada", roles = setOf("admin"))
            val authed = lifecycle.authState.value as AuthSessionState.Authenticated
            assertEquals("user-42", authed.userId)
            assertEquals(anonId, authed.anonymousId)

            val authedSession = lifecycle.metadata.value.sessionId
            assertNotEquals(initialSession, authedSession)

            // Idle expiry rotates session id while staying resilient
            clock.advanceByMillis(6_000L)
            val touched = lifecycle.touch()
            assertNotEquals(authedSession, touched.sessionId)

            lifecycle.markExpired()
            assertTrue(lifecycle.authState.value is AuthSessionState.Expired)
            lifecycle.signOut()
            assertTrue(lifecycle.authState.value is AuthSessionState.Unauthenticated)
        }

    @Test
    fun `layered feature flags combine local overrides remote cache and defaults`() =
        runTest {
            val remote = PersistentFeatureFlagStore(InMemoryKeyValueStore())
            assertTrue(
                remote.applyRemoteJson(
                    """{"enable_http2":true,"welcome_banner":"remote","max_items":25,"ratio":0.5}""",
                ),
            )
            val overrides = InMemoryFeatureFlagProvider()
            val layered = LayeredFeatureFlagProvider(overrides, remote)

            val http2Flag = FeatureFlagKey("enable_http2", false)
            val welcomeFlag = FeatureFlagKey("welcome_banner", "default")
            val maxItemsFlag = FeatureFlagKey("max_items", 10)
            val ratioFlag = FeatureFlagKey("ratio", 0.1)

            assertTrue(layered.isEnabled(http2Flag))
            assertEquals("remote", layered.getString(welcomeFlag))
            assertEquals(25, layered.getInt(maxItemsFlag))
            assertEquals(0.5, layered.getDouble(ratioFlag))

            layered.observeBoolean("enable_http2").test {
                assertEquals(true, awaitItem())
                overrides.setFlag("enable_http2", false)
                assertEquals(false, awaitItem())
                overrides.removeFlag("enable_http2")
                assertEquals(true, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `offline mutation queue persists retries and drops dead letters after max retries`() =
        runTest {
            val kv = InMemoryKeyValueStore()
            val clock = MutableClock(1_000L)
            val queue =
                OfflineMutationQueue(
                    backingStore = kv,
                    clock = clock,
                    uuidGenerator = SequentialUuidGenerator(),
                    maxQueueSize = 10,
                    maxRetries = 2,
                )

            val m1 = queue.enqueue("analytics_event", JsonPrimitive("ev1"))
            val m2 = queue.enqueue("profile_update", JsonPrimitive("ev2"))
            assertEquals(2, queue.status.value.pendingCount)

            // Reload from persisted KeyValueStore to confirm durability
            val reloaded = OfflineMutationQueue(backingStore = kv, clock = clock, maxRetries = 2)
            assertEquals(2, reloaded.queue.value.size)

            // First flush: m1 succeeds, m2 fails (retryCount -> 1)
            val report1 =
                reloaded.flush { item ->
                    if (item.id == m1.id) {
                        AppResult.Success(Unit)
                    } else {
                        AppResult.Failure(NetworkError(message = "offline"))
                    }
                }
            assertEquals(1, report1.succeeded)
            assertEquals(1, report1.retainedForRetry)
            assertEquals(0, report1.droppedDeadLetters)

            // Second flush: m2 fails again and hits maxRetries == 2 -> dropped as dead letter
            val report2 =
                reloaded.flush { AppResult.Failure(NetworkError(message = "still offline")) }
            assertEquals(0, report2.succeeded)
            assertEquals(0, report2.retainedForRetry)
            assertEquals(1, report2.droppedDeadLetters)
            assertEquals(0, reloaded.status.value.pendingCount)
            assertFalse(reloaded.queue.value.any { it.id == m2.id })
            assertNull(kv.getString(OfflineMutationQueue.DEFAULT_STORAGE_KEY))
        }
}
