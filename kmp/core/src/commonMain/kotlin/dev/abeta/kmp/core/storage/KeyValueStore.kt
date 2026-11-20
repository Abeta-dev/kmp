package dev.abeta.kmp.core.storage

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Minimal synchronous key-value persistence port decoupled from platform storage SDKs.
 */
interface KeyValueStore {
    /** Returns the string value stored under [key], or `null` if absent. */
    fun getString(key: String): String?

    /** Stores [value] under [key], overwriting any existing entry. */
    fun putString(
        key: String,
        value: String,
    )

    /** Removes the entry associated with [key], if present. */
    fun remove(key: String)

    /** Removes all entries from this store. */
    fun clear()

    /** Returns `true` if an entry exists for [key]. */
    fun contains(key: String): Boolean

    /** Returns an immutable snapshot of all keys currently present in this store. */
    fun keys(): Set<String>
}

/**
 * Reactive extension of [KeyValueStore] exposing Flow-based change streams per key.
 */
interface ObservableKeyValueStore : KeyValueStore {
    /** Emits updates to the value stored under [key], starting with the current value. */
    fun observeKey(key: String): Flow<String?>
}

/**
 * Returns an immutable map snapshot of all entries currently in this [KeyValueStore].
 */
fun KeyValueStore.snapshot(): Map<String, String> =
    buildMap {
        keys().forEach { key ->
            getString(key)?.let { value -> put(key, value) }
        }
    }

/**
 * Inserts all key-value pairs from [entries] into this [KeyValueStore].
 */
fun KeyValueStore.putAll(entries: Map<String, String>) {
    entries.forEach { (key, value) -> putString(key, value) }
}

/**
 * Thread-safe, reactive in-memory [ObservableKeyValueStore] suitable for tests and ephemeral caches.
 */
class InMemoryKeyValueStore(
    initialValues: Map<String, String> = emptyMap(),
) : ObservableKeyValueStore {
    private val storage = MutableStateFlow(initialValues.toMap())

    override fun getString(key: String): String? = storage.value[key]

    override fun putString(
        key: String,
        value: String,
    ) {
        storage.update { current -> current + (key to value) }
    }

    override fun remove(key: String) {
        storage.update { current -> current - key }
    }

    override fun clear() {
        storage.value = emptyMap()
    }

    override fun contains(key: String): Boolean = key in storage.value

    override fun keys(): Set<String> = storage.value.keys

    override fun observeKey(key: String): Flow<String?> = storage.map { map -> map[key] }.distinctUntilChanged()
}

/**
 * [KeyValueStore] decorator scoping every read, write, and clear operation under a key [namespace] prefix.
 */
class NamespacedKeyValueStore(
    private val delegate: KeyValueStore,
    namespace: String,
) : ObservableKeyValueStore {
    private val prefix: String =
        namespace
            .trim()
            .trimEnd(':')
            .also { require(it.isNotEmpty()) { "Namespace prefix must not be blank" } } + ":"

    private fun scopedKey(key: String): String = "$prefix$key"

    override fun getString(key: String): String? = delegate.getString(scopedKey(key))

    override fun putString(
        key: String,
        value: String,
    ) {
        delegate.putString(scopedKey(key), value)
    }

    override fun remove(key: String) {
        delegate.remove(scopedKey(key))
    }

    override fun clear() {
        delegate
            .keys()
            .filter { it.startsWith(prefix) }
            .forEach(delegate::remove)
    }

    override fun contains(key: String): Boolean = delegate.contains(scopedKey(key))

    override fun keys(): Set<String> =
        delegate
            .keys()
            .asSequence()
            .filter { it.startsWith(prefix) }
            .map { it.removePrefix(prefix) }
            .toSet()

    override fun observeKey(key: String): Flow<String?> =
        if (delegate is ObservableKeyValueStore) {
            delegate.observeKey(scopedKey(key))
        } else {
            MutableStateFlow(getString(key))
        }
}
