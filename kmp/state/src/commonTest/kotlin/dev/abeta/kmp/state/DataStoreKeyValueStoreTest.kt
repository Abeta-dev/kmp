package dev.abeta.kmp.state

import androidx.datastore.core.InterProcessCoordinator
import androidx.datastore.core.ReadScope
import androidx.datastore.core.Storage
import androidx.datastore.core.StorageConnection
import androidx.datastore.core.WriteScope
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import dev.abeta.kmp.state.preferences.DefaultPreferenceStore
import dev.abeta.kmp.state.preferences.PreferenceKey
import dev.abeta.kmp.state.preferences.getInt
import dev.abeta.kmp.state.preferences.observeBoolean
import dev.abeta.kmp.state.preferences.putBoolean
import dev.abeta.kmp.state.preferences.putInt
import dev.abeta.kmp.state.storage.DataStoreKeyValueStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DataStoreKeyValueStoreTest {
    @Test
    fun `synchronous put get remove clear and keys persist atomically to DataStore`() =
        runTest(UnconfinedTestDispatcher()) {
            val storage = InMemoryPreferencesStorage()
            val dataStore =
                PreferenceDataStoreFactory.create(
                    storage = storage,
                    scope = backgroundScope,
                )
            val store = DataStoreKeyValueStore(dataStore = dataStore, scope = backgroundScope)

            store.putString("user_id", "usr-101")
            store.putString("region", "us-west")

            assertEquals("usr-101", store.getString("user_id"))
            assertEquals("us-west", store.getString("region"))
            assertTrue(store.contains("user_id"))
            assertEquals(setOf("user_id", "region"), store.keys())

            store.awaitSync()
            val persisted = dataStore.data.first()
            assertEquals("usr-101", persisted[stringPreferencesKey("user_id")])
            assertEquals("us-west", persisted[stringPreferencesKey("region")])

            store.remove("region")
            assertNull(store.getString("region"))
            assertFalse(store.contains("region"))
            store.awaitSync()
            assertNull(dataStore.data.first()[stringPreferencesKey("region")])

            store.clear()
            assertTrue(store.keys().isEmpty())
            store.awaitSync()
            assertTrue(
                dataStore.data
                    .first()
                    .asMap()
                    .isEmpty(),
            )
        }

    @Test
    fun `observeString and observeKey reactively emit local mutations and external DataStore edits`() =
        runTest(UnconfinedTestDispatcher()) {
            val storage = InMemoryPreferencesStorage()
            val dataStore =
                PreferenceDataStoreFactory.create(
                    storage = storage,
                    scope = backgroundScope,
                )
            val store = DataStoreKeyValueStore(dataStore = dataStore, scope = backgroundScope)

            store.observeString("theme").test {
                assertNull(awaitItem())

                store.putString("theme", "dark")
                assertEquals("dark", awaitItem())

                // Distinct check: identical writes must not emit duplicates.
                store.putString("theme", "dark")
                store.awaitSync()
                expectNoEvents()

                // Direct atomic edit on underlying DataStore propagates into reactive observers.
                dataStore.edit { prefs ->
                    prefs[stringPreferencesKey("theme")] = "solarized"
                }
                assertEquals("solarized", awaitItem())

                store.remove("theme")
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            store.observeKey("locale").test {
                assertNull(awaitItem())
                store.putString("locale", "en-IN")
                assertEquals("en-IN", awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `key prefix isolates namespaces during reads writes and clear`() =
        runTest(UnconfinedTestDispatcher()) {
            val storage = InMemoryPreferencesStorage()
            val dataStore =
                PreferenceDataStoreFactory.create(
                    storage = storage,
                    scope = backgroundScope,
                )
            val alpha =
                DataStoreKeyValueStore(
                    dataStore = dataStore,
                    scope = backgroundScope,
                    keyPrefix = "alpha",
                )
            val beta =
                DataStoreKeyValueStore(
                    dataStore = dataStore,
                    scope = backgroundScope,
                    keyPrefix = "beta",
                )

            alpha.putString("token", "a-token")
            beta.putString("token", "b-token")
            alpha.awaitSync()
            beta.awaitSync()

            assertEquals("a-token", alpha.getString("token"))
            assertEquals("b-token", beta.getString("token"))
            assertEquals(setOf("token"), alpha.keys())
            assertEquals(setOf("token"), beta.keys())

            alpha.clear()
            alpha.awaitSync()
            beta.awaitSync()

            assertNull(alpha.getString("token"))
            assertEquals("b-token", beta.getString("token"))
            assertEquals("b-token", dataStore.data.first()[stringPreferencesKey("beta:token")])
            assertNull(dataStore.data.first()[stringPreferencesKey("alpha:token")])
        }

    @Test
    fun `hydrates pre-existing DataStore entries and integrates with DefaultPreferenceStore`() =
        runTest(UnconfinedTestDispatcher()) {
            val prePopulated =
                preferencesOf(
                    stringPreferencesKey("session_locale") to "en-US",
                )
            val storage = InMemoryPreferencesStorage(initial = prePopulated)
            val dataStore =
                PreferenceDataStoreFactory.create(
                    storage = storage,
                    scope = backgroundScope,
                )
            val backing = DataStoreKeyValueStore(dataStore = dataStore, scope = backgroundScope)
            backing.awaitSync()

            assertEquals("en-US", backing.getString("session_locale"))

            val prefStore = DefaultPreferenceStore(backing)
            val darkMode = PreferenceKey<Boolean>("dark_mode")
            val retries = PreferenceKey<Int>("retries")

            prefStore.observeBoolean(darkMode, defaultValue = false).test {
                assertEquals(false, awaitItem())
                prefStore.putBoolean(darkMode, true)
                assertEquals(true, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            prefStore.putInt(retries, 7)
            assertEquals(7, prefStore.getInt(retries))
            backing.awaitSync()
            assertEquals("7", dataStore.data.first()[stringPreferencesKey("retries")])
        }
}

private class InMemoryPreferencesStorage(
    initial: Preferences = emptyPreferences(),
) : Storage<Preferences> {
    private val mutex = Mutex()
    private var current: Preferences = initial
    private val coordinator = InMemoryCoordinator()

    override fun createConnection(): StorageConnection<Preferences> =
        object : StorageConnection<Preferences> {
            override val coordinator: InterProcessCoordinator = this@InMemoryPreferencesStorage.coordinator

            override suspend fun <R> readScope(block: suspend ReadScope<Preferences>.(locked: Boolean) -> R): R =
                mutex.withLock {
                    val scope =
                        object : ReadScope<Preferences> {
                            override suspend fun readData(): Preferences = current

                            override fun close() = Unit
                        }
                    scope.block(true)
                }

            override suspend fun writeScope(block: suspend WriteScope<Preferences>.() -> Unit) {
                mutex.withLock {
                    val scope =
                        object : WriteScope<Preferences> {
                            override suspend fun readData(): Preferences = current

                            override suspend fun writeData(value: Preferences) {
                                current = value
                            }

                            override fun close() = Unit
                        }
                    scope.block()
                }
            }

            override fun close() = Unit
        }
}

private class InMemoryCoordinator : InterProcessCoordinator {
    private val lockMutex = Mutex()
    private var version: Int = 0

    override val updateNotifications: Flow<Unit> = emptyFlow()

    override suspend fun <T> lock(block: suspend () -> T): T = lockMutex.withLock { block() }

    override suspend fun <T> tryLock(block: suspend (Boolean) -> T): T =
        if (lockMutex.tryLock()) {
            try {
                block(true)
            } finally {
                lockMutex.unlock()
            }
        } else {
            block(false)
        }

    override suspend fun getVersion(): Int = version

    override suspend fun incrementAndGetVersion(): Int {
        version += 1
        return version
    }
}
