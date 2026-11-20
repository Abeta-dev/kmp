package dev.abeta.kmp.state.preferences

import dev.abeta.kmp.core.storage.KeyValueStore
import dev.abeta.kmp.core.storage.ObservableKeyValueStore
import dev.abeta.kmp.core.storage.putAll
import dev.abeta.kmp.core.storage.snapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json

/**
 * Strongly-typed preference identifier whose type parameter [T] constrains callers at compile time.
 */
@Suppress("UnusedPrivateProperty")
data class PreferenceKey<T>(
    val name: String,
)

/**
 * Typed persistence abstraction over key-value user or application preferences, supporting
 * reactive [Flow] observation, versioned JSON objects, and snapshot export/import.
 *
 * Primitive and object extensions live cleanly over this minimal persistence contract.
 */
interface PreferenceStore {
    /** Returns the string stored for [key], or [defaultValue] if absent. */
    fun getString(
        key: PreferenceKey<String>,
        defaultValue: String? = null,
    ): String?

    /** Stores a string [value] under [key]. */
    fun putString(
        key: PreferenceKey<String>,
        value: String,
    )

    /** Observes raw string updates stored under [keyName]. */
    fun observeRaw(keyName: String): Flow<String?>

    /** Removes any value stored under [key]. */
    fun <T> remove(key: PreferenceKey<T>)

    /** Returns `true` if a value is currently stored under [key]. */
    fun <T> contains(key: PreferenceKey<T>): Boolean

    /** Clears all stored preferences. */
    fun clear()

    /** Exports all raw preference entries as a serialized JSON string snapshot. */
    fun exportSnapshotJson(): String

    /** Restores preferences from a previously exported JSON snapshot string. */
    fun importSnapshotJson(
        snapshotJson: String,
        replaceExisting: Boolean = false,
    ): Boolean
}

internal val DefaultPreferenceJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

/**
 * Default reactive [PreferenceStore] delegating persistence to an injected [KeyValueStore].
 */
class DefaultPreferenceStore(
    private val store: KeyValueStore,
    private val json: Json = DefaultPreferenceJson,
) : PreferenceStore {
    private val mutationTick = MutableStateFlow(0L)

    override fun getString(
        key: PreferenceKey<String>,
        defaultValue: String?,
    ): String? = store.getString(key.name) ?: defaultValue

    override fun putString(
        key: PreferenceKey<String>,
        value: String,
    ) {
        store.putString(key.name, value)
        mutationTick.update { it + 1L }
    }

    override fun observeRaw(keyName: String): Flow<String?> =
        if (store is ObservableKeyValueStore) {
            store.observeKey(keyName)
        } else {
            mutationTick
                .map { store.getString(keyName) }
                .distinctUntilChanged()
        }

    override fun <T> remove(key: PreferenceKey<T>) {
        store.remove(key.name)
        mutationTick.update { it + 1L }
    }

    override fun <T> contains(key: PreferenceKey<T>): Boolean = store.contains(key.name)

    override fun clear() {
        store.clear()
        mutationTick.update { it + 1L }
    }

    override fun exportSnapshotJson(): String = json.encodeToString(store.snapshot())

    override fun importSnapshotJson(
        snapshotJson: String,
        replaceExisting: Boolean,
    ): Boolean {
        val decoded =
            runCatching { json.decodeFromString<Map<String, String>>(snapshotJson) }
                .getOrNull()
                ?: return false
        if (replaceExisting) {
            store.clear()
        }
        store.putAll(decoded)
        mutationTick.update { it + 1L }
        return true
    }
}
