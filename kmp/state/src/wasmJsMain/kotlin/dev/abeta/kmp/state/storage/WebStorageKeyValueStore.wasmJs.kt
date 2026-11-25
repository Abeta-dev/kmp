@file:OptIn(ExperimentalWasmJsInterop::class)
@file:Suppress("MatchingDeclarationName")

package dev.abeta.kmp.state.storage

import dev.abeta.kmp.core.storage.InMemoryKeyValueStore
import dev.abeta.kmp.core.storage.ObservableKeyValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

@JsFun(
    "() => { try { if (typeof window === 'undefined' || !window.localStorage) { return false; } " +
        "const probe = '__abeta_disk_probe__'; " +
        "window.localStorage.setItem(probe, '1'); " +
        "window.localStorage.removeItem(probe); " +
        "return true; } catch (e) { return false; } }",
)
private external fun jsIsLocalStorageAvailable(): Boolean

@JsFun(
    "(key) => { try { return window.localStorage.getItem(key); } catch (e) { return null; } }",
)
private external fun jsLocalStorageGet(key: String): String?

private const val STORAGE_WRITE_OK = 0
private const val STORAGE_WRITE_QUOTA_EXCEEDED = 1

@JsFun(
    "(key, value) => { try { window.localStorage.setItem(key, value); return 0; } " +
        "catch (e) { const name = (e && e.name) ? String(e.name) : ''; " +
        "const code = (e && typeof e.code === 'number') ? e.code : 0; " +
        "if (name === 'QuotaExceededError' || name === 'NS_ERROR_DOM_QUOTA_REACHED' || " +
        "code === 22 || code === 1014) { return 1; } return 2; } }",
)
private external fun jsLocalStorageSetStatus(
    key: String,
    value: String,
): Int

@JsFun(
    "(key) => { try { window.localStorage.removeItem(key); return true; } catch (e) { return false; } }",
)
private external fun jsLocalStorageRemove(key: String): Boolean

@JsFun(
    "() => { try { return window.localStorage.length; } catch (e) { return -1; } }",
)
private external fun jsLocalStorageLength(): Int

@JsFun(
    "(index) => { try { return window.localStorage.key(index); } catch (e) { return null; } }",
)
private external fun jsLocalStorageKey(index: Int): String?

/**
 * Zero-KSP, namespace-isolated [ObservableKeyValueStore] backed by browser `window.localStorage`
 * on WasmJS, with an automatic [InMemoryKeyValueStore] fallback when `localStorage` is unavailable
 * (for example, Web Workers, headless contexts, or restricted browser storage quotas).
 *
 * Every key is prefixed with `"<sanitizedNamespace>:"` so [keys] and [clear] affect only entries
 * within [namespace].
 */
class WebStorageKeyValueStore(
    namespace: String = DEFAULT_DISK_NAMESPACE,
) : ObservableKeyValueStore {
    private val prefix: String = "${sanitizeDiskNamespace(namespace)}:"
    private val fallback = InMemoryKeyValueStore()
    private val changes = MutableStateFlow(0L)
    private var useFallbackOnly: Boolean = !jsIsLocalStorageAvailable()

    override fun getString(key: String): String? =
        if (useFallbackOnly) {
            fallback.getString(key)
        } else {
            fallback.getString(key) ?: jsLocalStorageGet(scopedKey(key))
        }

    override fun putString(
        key: String,
        value: String,
    ) {
        if (useFallbackOnly) {
            fallback.putString(key, value)
        } else {
            when (jsLocalStorageSetStatus(scopedKey(key), value)) {
                STORAGE_WRITE_OK -> {
                    fallback.remove(key)
                }

                STORAGE_WRITE_QUOTA_EXCEEDED -> {
                    // Spill only the oversized entry in-memory so normal keys keep using localStorage.
                    jsLocalStorageRemove(scopedKey(key))
                    fallback.putString(key, value)
                }

                else -> {
                    migrateToFallback()
                    fallback.putString(key, value)
                }
            }
        }
        changes.update { it + 1L }
    }

    override fun remove(key: String) {
        fallback.remove(key)
        if (!useFallbackOnly && !jsLocalStorageRemove(scopedKey(key))) {
            migrateToFallback()
            fallback.remove(key)
        }
        changes.update { it + 1L }
    }

    override fun clear() {
        fallback.clear()
        if (!useFallbackOnly) {
            val scopedKeys = collectScopedKeys()
            for (fullKey in scopedKeys) {
                if (!jsLocalStorageRemove(fullKey)) {
                    migrateToFallback()
                    fallback.clear()
                    break
                }
            }
        }
        changes.update { it + 1L }
    }

    override fun contains(key: String): Boolean = getString(key) != null

    override fun keys(): Set<String> =
        if (useFallbackOnly) {
            fallback.keys()
        } else {
            val count = jsLocalStorageLength()
            if (count < 0) {
                useFallbackOnly = true
                fallback.keys()
            } else {
                buildSet {
                    addAll(fallback.keys())
                    for (index in 0 until count) {
                        val fullKey = jsLocalStorageKey(index) ?: continue
                        if (fullKey.startsWith(prefix)) {
                            add(fullKey.removePrefix(prefix))
                        }
                    }
                }
            }
        }

    override fun observeKey(key: String): Flow<String?> =
        changes
            .map { getString(key) }
            .distinctUntilChanged()

    private fun scopedKey(key: String): String = "$prefix$key"

    private fun collectScopedKeys(): List<String> {
        val count = jsLocalStorageLength()
        if (count <= 0) return emptyList()
        return buildList {
            for (index in 0 until count) {
                val fullKey = jsLocalStorageKey(index) ?: continue
                if (fullKey.startsWith(prefix)) {
                    add(fullKey)
                }
            }
        }
    }

    private fun migrateToFallback() {
        val scopedKeys = collectScopedKeys()
        for (fullKey in scopedKeys) {
            val existing = jsLocalStorageGet(fullKey) ?: continue
            fallback.putString(fullKey.removePrefix(prefix), existing)
        }
        useFallbackOnly = true
    }
}

actual fun createDiskKeyValueStore(
    namespace: String,
    directoryPath: String?,
): ObservableKeyValueStore = WebStorageKeyValueStore(namespace = namespace)
