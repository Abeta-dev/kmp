package dev.abeta.kmp.state.storage

import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dev.abeta.kmp.core.storage.ObservableKeyValueStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val DEFAULT_DATASTORE_NAMESPACE: String = "abeta_prefs"

/**
 * [ObservableKeyValueStore] adapter backed by official AndroidX [DataStore]`<`[Preferences]`>`.
 *
 * Maintains a thread-safe reactive [MutableStateFlow] snapshot cache so synchronous
 * [getString], [putString], [remove], [clear], [contains], and [keys] calls complete
 * immediately with read-your-own-writes consistency, while serializing every mutation
 * through atomic transactional `dataStore.edit { ... }` writes on [scope] and reflecting
 * external DataStore emissions reactively via [observeKey] and [observeString].
 */
class DataStoreKeyValueStore(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
    private val keyPrefix: String = "",
) : ObservableKeyValueStore {
    private val snapshot = MutableStateFlow(StoreSnapshot())
    private val writeQueue = Channel<WriteOperation>(capacity = Channel.UNLIMITED)

    init {
        scope.launch {
            dataStore.data
                .catch { cause ->
                    if (cause is IOException) {
                        emit(emptyPreferences())
                    } else {
                        throw cause
                    }
                }.collect { preferences ->
                    val persisted = extractScopedEntries(preferences, keyPrefix)
                    snapshot.update { current ->
                        if (current.pendingWrites == 0) {
                            current.copy(entries = persisted, hydrated = true)
                        } else {
                            current.copy(hydrated = true)
                        }
                    }
                }
        }

        scope.launch {
            for (operation in writeQueue) {
                val committed = commitWrite(operation)
                snapshot.update { current ->
                    val remaining = (current.pendingWrites - 1).coerceAtLeast(0)
                    if (remaining == 0 && committed != null) {
                        current.copy(
                            entries = extractScopedEntries(committed, keyPrefix),
                            pendingWrites = 0,
                            hydrated = true,
                        )
                    } else {
                        current.copy(pendingWrites = remaining)
                    }
                }
            }
        }
    }

    override fun getString(key: String): String? = snapshot.value.entries[key]

    override fun putString(
        key: String,
        value: String,
    ) {
        snapshot.update { current ->
            current.copy(
                entries = current.entries + (key to value),
                pendingWrites = current.pendingWrites + 1,
            )
        }
        writeQueue.trySend(WriteOperation.Put(scopedKey(keyPrefix, key), value))
    }

    override fun remove(key: String) {
        snapshot.update { current ->
            current.copy(
                entries = current.entries - key,
                pendingWrites = current.pendingWrites + 1,
            )
        }
        writeQueue.trySend(WriteOperation.Remove(scopedKey(keyPrefix, key)))
    }

    override fun clear() {
        snapshot.update { current ->
            current.copy(
                entries = emptyMap(),
                pendingWrites = current.pendingWrites + 1,
            )
        }
        writeQueue.trySend(WriteOperation.Clear)
    }

    override fun contains(key: String): Boolean = key in snapshot.value.entries

    override fun keys(): Set<String> = snapshot.value.entries.keys

    override fun observeKey(key: String): Flow<String?> =
        snapshot
            .map { state -> state.entries[key] }
            .distinctUntilChanged()

    /**
     * Reactive stream of string updates for [key], delegating to [observeKey].
     */
    fun observeString(key: String): Flow<String?> = observeKey(key)

    /**
     * Suspends until initial [DataStore] hydration has completed and all previously enqueued
     * mutations have been atomically committed to [dataStore].
     */
    suspend fun awaitSync() {
        snapshot.first { state -> state.hydrated && state.pendingWrites == 0 }
    }

    private suspend fun commitWrite(operation: WriteOperation): Preferences? =
        try {
            dataStore.edit { mutablePreferences ->
                applyWriteOperation(mutablePreferences, operation, keyPrefix)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            null
        }

    private data class StoreSnapshot(
        val entries: Map<String, String> = emptyMap(),
        val pendingWrites: Int = 0,
        val hydrated: Boolean = false,
    )
}

private sealed interface WriteOperation {
    data class Put(
        val scopedKey: String,
        val value: String,
    ) : WriteOperation

    data class Remove(
        val scopedKey: String,
    ) : WriteOperation

    data object Clear : WriteOperation
}

private fun scopedKey(
    keyPrefix: String,
    key: String,
): String = if (keyPrefix.isEmpty()) key else "$keyPrefix:$key"

private fun applyWriteOperation(
    mutablePreferences: MutablePreferences,
    operation: WriteOperation,
    keyPrefix: String,
) {
    when (operation) {
        is WriteOperation.Put -> {
            mutablePreferences[stringPreferencesKey(operation.scopedKey)] = operation.value
        }

        is WriteOperation.Remove -> {
            mutablePreferences.remove(stringPreferencesKey(operation.scopedKey))
        }

        WriteOperation.Clear -> {
            if (keyPrefix.isEmpty()) {
                mutablePreferences.clear()
            } else {
                val fullPrefix = "$keyPrefix:"
                val keysToRemove =
                    mutablePreferences
                        .asMap()
                        .keys
                        .filter { prefKey -> prefKey.name.startsWith(fullPrefix) }
                for (prefKey in keysToRemove) {
                    mutablePreferences.remove(prefKey)
                }
            }
        }
    }
}

private fun extractScopedEntries(
    preferences: Preferences,
    keyPrefix: String,
): Map<String, String> {
    val rawMap = preferences.asMap()
    if (rawMap.isEmpty()) return emptyMap()
    val fullPrefix = if (keyPrefix.isEmpty()) "" else "$keyPrefix:"
    return buildMap {
        for ((prefKey, rawValue) in rawMap) {
            val stringValue = rawValue as? String ?: continue
            if (fullPrefix.isEmpty()) {
                put(prefKey.name, stringValue)
            } else if (prefKey.name.startsWith(fullPrefix)) {
                put(prefKey.name.removePrefix(fullPrefix), stringValue)
            }
        }
    }
}

/**
 * Creates a platform-native durable [ObservableKeyValueStore] backed by official AndroidX
 * Preferences DataStore isolated under [namespace].
 */
expect fun createDataStoreKeyValueStore(namespace: String = DEFAULT_DATASTORE_NAMESPACE): ObservableKeyValueStore

internal fun sanitizeDataStoreNamespace(namespace: String): String =
    namespace
        .trim()
        .replace(Regex("[^A-Za-z0-9_.-]"), "_")
        .ifBlank { DEFAULT_DATASTORE_NAMESPACE }
