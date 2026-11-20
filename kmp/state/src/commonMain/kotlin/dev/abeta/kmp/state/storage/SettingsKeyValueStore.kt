package dev.abeta.kmp.state.storage

import com.russhwolf.settings.Settings
import dev.abeta.kmp.core.security.SecureKeyValueStore
import dev.abeta.kmp.core.security.createSecureKeyValueStore
import dev.abeta.kmp.core.storage.ObservableKeyValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * [ObservableKeyValueStore] adapter backed by Multiplatform [Settings] for cross-platform
 * durable storage across JVM (`Preferences`), Apple (`NSUserDefaults`), and Wasm (`localStorage`).
 */
class SettingsKeyValueStore(
    private val settings: Settings,
    private val keyPrefix: String = "",
) : ObservableKeyValueStore {
    private val changes = MutableStateFlow(0L)

    private fun scoped(key: String): String = if (keyPrefix.isEmpty()) key else "$keyPrefix:$key"

    override fun getString(key: String): String? = settings.getStringOrNull(scoped(key))

    override fun putString(
        key: String,
        value: String,
    ) {
        settings.putString(scoped(key), value)
        changes.update { it + 1L }
    }

    override fun remove(key: String) {
        settings.remove(scoped(key))
        changes.update { it + 1L }
    }

    override fun clear() {
        if (keyPrefix.isEmpty()) {
            settings.clear()
        } else {
            val fullPrefix = "$keyPrefix:"
            settings
                .keys
                .filter { it.startsWith(fullPrefix) }
                .forEach(settings::remove)
        }
        changes.update { it + 1L }
    }

    override fun contains(key: String): Boolean = settings.hasKey(scoped(key))

    override fun keys(): Set<String> =
        if (keyPrefix.isEmpty()) {
            settings.keys
        } else {
            val fullPrefix = "$keyPrefix:"
            settings.keys
                .asSequence()
                .filter { it.startsWith(fullPrefix) }
                .map { it.removePrefix(fullPrefix) }
                .toSet()
        }

    override fun observeKey(key: String): Flow<String?> =
        changes
            .map { getString(key) }
            .distinctUntilChanged()
}

/**
 * Creates a platform-native durable [ObservableKeyValueStore] isolated under [namespace].
 */
fun createPersistentKeyValueStore(namespace: String = "abeta_kmp"): ObservableKeyValueStore =
    SettingsKeyValueStore(
        settings = createPlatformSettings(namespace),
        keyPrefix = namespace,
    )

/**
 * Creates a platform-native encrypted/keychain [SecureKeyValueStore] isolated under [namespace].
 */
fun createSecurePersistentKeyValueStore(namespace: String = "abeta_kmp_secure"): SecureKeyValueStore =
    createSecureKeyValueStore(serviceName = namespace)

internal expect fun createPlatformSettings(name: String): Settings
