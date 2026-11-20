@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.security

import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import java.util.prefs.Preferences
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val AES_ALGORITHM = "AES"
private const val AES_GCM_TRANSFORM = "AES/GCM/NoPadding"
private const val AES_KEY_BITS = 256
private const val GCM_IV_BYTES = 12
private const val GCM_TAG_BITS = 128
private const val MASTER_KEY_PREF = "__abeta_master_key__"
private const val ENTRY_PREFIX = "enc:"

/**
 * JVM [SecureKeyValueStore] encrypting values with AES-256-GCM before persisting in Java [Preferences].
 */
private class JvmSecureStore(
    serviceName: String,
) : SecureKeyValueStore {
    private val prefs: Preferences =
        Preferences.userRoot().node("dev/abeta/kmp/secure/${sanitizeNode(serviceName)}")
    private val random = SecureRandom()
    private val secretKey: SecretKey by lazy(::loadOrCreateKey)

    override val storageBackend: String = "JvmAesGcmPreferences"
    override val isHardwareOrOsEncrypted: Boolean = true

    override fun getString(key: String): String? {
        val raw = prefs.get(ENTRY_PREFIX + key, null) ?: return null
        return decryptOrNull(raw)
    }

    override fun putString(
        key: String,
        value: String,
    ) {
        prefs.put(ENTRY_PREFIX + key, encrypt(value))
    }

    override fun remove(key: String) {
        prefs.remove(ENTRY_PREFIX + key)
    }

    override fun clear() {
        keys().forEach { key -> prefs.remove(ENTRY_PREFIX + key) }
    }

    override fun contains(key: String): Boolean = prefs.get(ENTRY_PREFIX + key, null) != null

    override fun keys(): Set<String> =
        prefs
            .keys()
            .filter { it.startsWith(ENTRY_PREFIX) }
            .map { it.removePrefix(ENTRY_PREFIX) }
            .toSet()

    private fun loadOrCreateKey(): SecretKey {
        val encoded = prefs.get(MASTER_KEY_PREF, null)
        if (encoded != null) {
            val decoded = Base64.getDecoder().decode(encoded)
            return SecretKeySpec(decoded, AES_ALGORITHM)
        }
        val generator = KeyGenerator.getInstance(AES_ALGORITHM)
        generator.init(AES_KEY_BITS, random)
        val generated = generator.generateKey()
        prefs.put(MASTER_KEY_PREF, Base64.getEncoder().encodeToString(generated.encoded))
        return generated
    }

    private fun encrypt(plaintext: String): String {
        val iv = ByteArray(GCM_IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext.encodeToByteArray())
        val combined = ByteArray(iv.size + ciphertext.size)
        iv.copyInto(combined, destinationOffset = 0)
        ciphertext.copyInto(combined, destinationOffset = iv.size)
        return Base64.getEncoder().encodeToString(combined)
    }

    private fun decryptOrNull(payload: String): String? =
        try {
            val combined = Base64.getDecoder().decode(payload)
            if (combined.size <= GCM_IV_BYTES) return null
            val iv = combined.copyOfRange(0, GCM_IV_BYTES)
            val ciphertext = combined.copyOfRange(GCM_IV_BYTES, combined.size)
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext).decodeToString()
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: GeneralSecurityException) {
            null
        }

    private companion object {
        fun sanitizeNode(raw: String): String = raw.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "default" }
    }
}

/** Creates an AES-256-GCM encrypted [SecureKeyValueStore] backed by JVM [Preferences]. */
actual fun createSecureKeyValueStore(serviceName: String): SecureKeyValueStore = JvmSecureStore(serviceName)
