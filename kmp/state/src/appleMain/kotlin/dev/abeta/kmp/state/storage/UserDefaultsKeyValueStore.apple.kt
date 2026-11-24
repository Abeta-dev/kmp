@file:Suppress("MatchingDeclarationName")

package dev.abeta.kmp.state.storage

import dev.abeta.kmp.core.storage.ObservableKeyValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import platform.Foundation.NSLock
import platform.Foundation.NSUserDefaults

/**
 * Zero-KSP, thread-safe, namespace-isolated [ObservableKeyValueStore] backed by Apple
 * [NSUserDefaults].
 *
 * Every key is stored under `"<sanitizedNamespace>:<key>"` so [keys] and [clear] operate strictly
 * within [namespace] without disturbing unrelated app or system defaults. Pass a non-null
 * [suiteName] to share state across Apple App Groups (for example, iOS and watchOS targets).
 */
class UserDefaultsKeyValueStore(
    namespace: String = DEFAULT_DISK_NAMESPACE,
    suiteName: String? = null,
) : ObservableKeyValueStore {
    private val lock = NSLock()
    private val prefix: String = "${sanitizeDiskNamespace(namespace)}:"
    private val defaults: NSUserDefaults =
        if (suiteName.isNullOrBlank()) {
            NSUserDefaults.standardUserDefaults
        } else {
            NSUserDefaults(suiteName = suiteName)
        }
    private val changes = MutableStateFlow(0L)

    override fun getString(key: String): String? =
        withLock {
            defaults.stringForKey(scopedKey(key))
        }

    override fun putString(
        key: String,
        value: String,
    ) {
        withLock {
            defaults.setObject(value, forKey = scopedKey(key))
        }
        changes.update { it + 1L }
    }

    override fun remove(key: String) {
        withLock {
            defaults.removeObjectForKey(scopedKey(key))
        }
        changes.update { it + 1L }
    }

    override fun clear() {
        withLock {
            val matchingKeys = findScopedKeysLocked()
            for (fullKey in matchingKeys) {
                defaults.removeObjectForKey(fullKey)
            }
        }
        changes.update { it + 1L }
    }

    override fun contains(key: String): Boolean =
        withLock {
            defaults.stringForKey(scopedKey(key)) != null
        }

    override fun keys(): Set<String> =
        withLock {
            findScopedKeysLocked()
                .map { fullKey -> fullKey.removePrefix(prefix) }
                .toSet()
        }

    override fun observeKey(key: String): Flow<String?> =
        changes
            .map { getString(key) }
            .distinctUntilChanged()

    private fun scopedKey(key: String): String = "$prefix$key"

    private fun findScopedKeysLocked(): List<String> =
        defaults
            .dictionaryRepresentation()
            .entries
            .mapNotNull { (rawKey, rawValue) ->
                val fullKey = rawKey as? String ?: return@mapNotNull null
                if (!fullKey.startsWith(prefix)) return@mapNotNull null
                if (rawValue !is String) return@mapNotNull null
                fullKey
            }

    private inline fun <T> withLock(block: () -> T): T {
        lock.lock()
        return try {
            block()
        } finally {
            lock.unlock()
        }
    }
}

actual fun createDiskKeyValueStore(
    namespace: String,
    directoryPath: String?,
): ObservableKeyValueStore = UserDefaultsKeyValueStore(namespace = namespace)
