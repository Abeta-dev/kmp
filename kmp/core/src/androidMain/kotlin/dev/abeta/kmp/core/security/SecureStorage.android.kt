@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dev.abeta.kmp.core.platform.AbetaAndroidContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.io.encoding.Base64

private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
private const val AES_GCM_TRANSFORM = "AES/GCM/NoPadding"
private const val AES_KEY_SIZE_BITS = 256
private const val GCM_TAG_BITS = 128

private class AndroidKeyStoreSecureStore(
    context: Context,
    serviceName: String,
) : SecureKeyValueStore {
    private val sanitizedService = sanitizeAndroidSecureServiceName(serviceName)
    private val alias = "abeta_kmp_aes_gcm_$sanitizedService"
    private val prefs =
        context.applicationContext.getSharedPreferences(
            "dev.abeta.kmp.secure.$sanitizedService",
            Context.MODE_PRIVATE,
        )

    override val storageBackend: String = "AndroidKeyStoreAesGcm"
    override val isHardwareOrOsEncrypted: Boolean = true

    override fun getString(key: String): String? {
        val blob = prefs.getString(key, null) ?: return null
        return decryptOrNull(blob)
    }

    override fun putString(
        key: String,
        value: String,
    ) {
        val sealed =
            runCatching { encrypt(value) }
                .recoverCatching {
                    resetInvalidatedKey()
                    encrypt(value)
                }.getOrNull() ?: return
        prefs.edit().putString(key, sealed).apply()
    }

    override fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    override fun contains(key: String): Boolean = prefs.contains(key)

    override fun keys(): Set<String> = prefs.all.keys.toSet()

    private fun resetInvalidatedKey() {
        runCatching {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
            if (keyStore.containsAlias(alias)) {
                keyStore.deleteEntry(alias)
            }
        }
        prefs.edit().clear().apply()
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(AES_KEY_SIZE_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val sealed = cipher.doFinal(plaintext.encodeToByteArray())
        val ivEncoded = Base64.encode(cipher.iv)
        val ciphertextEncoded = Base64.encode(sealed)
        return "$ivEncoded:$ciphertextEncoded"
    }

    private fun decryptOrNull(payload: String): String? =
        runCatching {
            val parts = payload.split(':', limit = 2)
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0])
            val ciphertext = Base64.decode(parts[1])
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext).decodeToString()
        }.getOrNull()
}

private val SERVICE_NAME_INVALID_CHARS = Regex("[^A-Za-z0-9._-]")

internal fun sanitizeAndroidSecureServiceName(raw: String): String {
    val cleaned = raw.replace(SERVICE_NAME_INVALID_CHARS, "_")
    return cleaned.ifBlank { "default" }
}

/**
 * Creates an Android [SecureKeyValueStore] backed by Android KeyStore AES-256-GCM and
 * `SharedPreferences` when [AbetaAndroidContext] is initialized, degrading to a restricted
 * AES-256-GCM file-backed store in JVM host unit tests without an Android `Context`.
 */
actual fun createSecureKeyValueStore(serviceName: String): SecureKeyValueStore {
    val context = AbetaAndroidContext.applicationContextOrNull()
    return if (context != null) {
        AndroidKeyStoreSecureStore(context = context, serviceName = serviceName)
    } else {
        AndroidFileFallbackSecureStore(serviceName = serviceName)
    }
}
