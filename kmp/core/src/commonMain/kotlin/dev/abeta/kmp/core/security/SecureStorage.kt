@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.security

import dev.abeta.kmp.core.storage.InMemoryKeyValueStore
import dev.abeta.kmp.core.storage.KeyValueStore
import kotlinx.coroutines.flow.Flow

/**
 * Security-focused specialization of [KeyValueStore] for sensitive secrets such as bearer tokens
 * or session credentials backed by OS keychains / encrypted storage when supported.
 */
interface SecureKeyValueStore : KeyValueStore {
    /** Human-readable name of the active security backing mechanism (e.g. `"AppleKeychain"`, `"AesGcm"`). */
    val storageBackend: String

    /** `true` when secrets are persisted using OS cryptographic / keychain primitives. */
    val isHardwareOrOsEncrypted: Boolean
}

/**
 * Deterministic in-memory [SecureKeyValueStore] for unit tests and ephemeral preview containers.
 */
class InMemorySecureKeyValueStore(
    initialValues: Map<String, String> = emptyMap(),
) : SecureKeyValueStore {
    private val delegate = InMemoryKeyValueStore(initialValues)

    override val storageBackend: String = "InMemorySecureStore"
    override val isHardwareOrOsEncrypted: Boolean = false

    override fun getString(key: String): String? = delegate.getString(key)

    override fun putString(
        key: String,
        value: String,
    ) = delegate.putString(key, value)

    override fun remove(key: String) = delegate.remove(key)

    override fun clear() = delegate.clear()

    override fun contains(key: String): Boolean = delegate.contains(key)

    override fun keys(): Set<String> = delegate.keys()

    /** Exposes reactive observation of secret changes in tests. */
    fun observeKey(key: String): Flow<String?> = delegate.observeKey(key)
}

/**
 * Creates a platform-native [SecureKeyValueStore] scoped by [serviceName].
 */
expect fun createSecureKeyValueStore(serviceName: String = "dev.abeta.kmp.secure"): SecureKeyValueStore
