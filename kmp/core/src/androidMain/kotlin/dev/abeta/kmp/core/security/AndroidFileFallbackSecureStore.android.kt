@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.security

import java.io.File
import java.io.FileOutputStream
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

private const val FALLBACK_AES_ALGORITHM = "AES"
private const val FALLBACK_AES_GCM_TRANSFORM = "AES/GCM/NoPadding"
private const val FALLBACK_AES_KEY_BITS = 256
private const val FALLBACK_AES_KEY_BYTES = 32
private const val FALLBACK_GCM_IV_BYTES = 12
private const val FALLBACK_GCM_TAG_BITS = 128
private const val FALLBACK_MASTER_KEY_FILE = "master.key"
private const val FALLBACK_ENTRY_EXT = ".enc"
private const val FALLBACK_HEX_MASK = 0xFF
private const val FALLBACK_HEX_SHIFT = 4
private const val FALLBACK_HEX_LOW_MASK = 0x0F
private const val FALLBACK_HEX_CHARS = "0123456789abcdef"

/**
 * Restricted file-backed AES-256-GCM [SecureKeyValueStore] fallback used when running Android
 * host unit tests without an initialized Android [android.content.Context].
 */
@Suppress("TooManyFunctions")
internal class AndroidFileFallbackSecureStore(
    serviceName: String,
    baseDirectoryOverride: File? = null,
) : SecureKeyValueStore {
    private val storageDir: File = resolveStorageDir(serviceName, baseDirectoryOverride)
    private val lock: Any = directoryLocks.computeIfAbsent(storageDir.absolutePath) { Any() }
    private val random = SecureRandom()
    private var cachedKey: SecretKey? = null

    override val storageBackend: String = "AndroidAesGcmFileFallback"
    override val isHardwareOrOsEncrypted: Boolean = false

    override fun getString(key: String): String? =
        synchronized(lock) {
            val file = entryFile(key)
            if (!file.isFile) return null
            val record = readEntryLocked(file) ?: return null
            if (record.first == key) record.second else null
        }

    override fun putString(
        key: String,
        value: String,
    ) {
        synchronized(lock) {
            ensureStorageDirLocked()
            val encodedKey = Base64.encode(key.encodeToByteArray())
            val encodedValue = Base64.encode(value.encodeToByteArray())
            val encrypted = encryptLocked("$encodedKey\n$encodedValue")
            writeAtomicallyLocked(entryFile(key), encrypted)
        }
    }

    override fun remove(key: String) {
        synchronized(lock) {
            runCatching { entryFile(key).delete() }
        }
    }

    override fun clear() {
        synchronized(lock) {
            if (!storageDir.isDirectory) return
            storageDir
                .listFiles { _, name -> name.endsWith(FALLBACK_ENTRY_EXT) }
                ?.forEach { file -> runCatching { file.delete() } }
        }
    }

    override fun contains(key: String): Boolean = getString(key) != null

    override fun keys(): Set<String> =
        synchronized(lock) {
            if (!storageDir.isDirectory) return emptySet()
            val result = linkedSetOf<String>()
            storageDir
                .listFiles { _, name -> name.endsWith(FALLBACK_ENTRY_EXT) }
                ?.forEach { file -> readEntryLocked(file)?.first?.let(result::add) }
            result
        }

    private fun entryFile(key: String): File = File(storageDir, "${sha256Hex(key)}$FALLBACK_ENTRY_EXT")

    private fun masterKeyFile(): File = File(storageDir, FALLBACK_MASTER_KEY_FILE)

    private fun ensureStorageDirLocked() {
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        applyOwnerOnlyPerms(storageDir, isDirectory = true)
    }

    private fun loadOrCreateKeyLocked(): SecretKey {
        cachedKey?.let { return it }
        ensureStorageDirLocked()
        val keyFile = masterKeyFile()
        val existingKey =
            if (keyFile.isFile) {
                runCatching { keyFile.readBytes() }
                    .getOrNull()
                    ?.let(::decodeMasterKeyBytes)
                    ?.let { bytes -> SecretKeySpec(bytes, FALLBACK_AES_ALGORITHM) }
            } else {
                null
            }
        val resolved =
            existingKey ?: run {
                val generator = KeyGenerator.getInstance(FALLBACK_AES_ALGORITHM)
                generator.init(FALLBACK_AES_KEY_BITS, random)
                val generated = generator.generateKey()
                val encoded = Base64.encode(generated.encoded).encodeToByteArray()
                writeAtomicallyLocked(keyFile, encoded)
                generated
            }
        cachedKey = resolved
        return resolved
    }

    private fun decodeMasterKeyBytes(raw: ByteArray): ByteArray? =
        runCatching {
            val decoded = Base64.decode(raw.decodeToString().trim())
            decoded.takeIf { it.size == FALLBACK_AES_KEY_BYTES }
        }.getOrNull()

    private fun encryptLocked(plaintext: String): ByteArray {
        val secretKey = loadOrCreateKeyLocked()
        val iv = ByteArray(FALLBACK_GCM_IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(FALLBACK_AES_GCM_TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(FALLBACK_GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext.encodeToByteArray())
        val combined = ByteArray(iv.size + ciphertext.size)
        iv.copyInto(combined, destinationOffset = 0)
        ciphertext.copyInto(combined, destinationOffset = iv.size)
        return combined
    }

    private fun readEntryLocked(file: File): Pair<String, String>? {
        val plaintext =
            runCatching { file.readBytes() }
                .getOrNull()
                ?.let(::decryptOrNullLocked)
        val newlineIndex = plaintext?.indexOf('\n') ?: -1
        return if (plaintext == null || newlineIndex < 0) {
            null
        } else {
            runCatching {
                val keyBytes = Base64.decode(plaintext.substring(0, newlineIndex))
                val valBytes = Base64.decode(plaintext.substring(newlineIndex + 1))
                keyBytes.decodeToString() to valBytes.decodeToString()
            }.getOrNull()
        }
    }

    private fun decryptOrNullLocked(combined: ByteArray): String? {
        if (combined.size <= FALLBACK_GCM_IV_BYTES) return null
        val secretKey = loadOrCreateKeyLocked()
        return try {
            val iv = combined.copyOfRange(0, FALLBACK_GCM_IV_BYTES)
            val ciphertext = combined.copyOfRange(FALLBACK_GCM_IV_BYTES, combined.size)
            val cipher = Cipher.getInstance(FALLBACK_AES_GCM_TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(FALLBACK_GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext).decodeToString()
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: GeneralSecurityException) {
            null
        }
    }

    private fun writeAtomicallyLocked(
        target: File,
        bytes: ByteArray,
    ) {
        val tempFile = File.createTempFile(".abeta-sec-", ".tmp", storageDir)
        var moved = false
        try {
            applyOwnerOnlyPerms(tempFile, isDirectory = false)
            FileOutputStream(tempFile).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            if (!tempFile.renameTo(target)) {
                target.delete()
                check(tempFile.renameTo(target)) { "Failed to replace secure store file: ${target.name}" }
            }
            applyOwnerOnlyPerms(target, isDirectory = false)
            moved = true
        } finally {
            if (!moved) {
                runCatching { tempFile.delete() }
            }
        }
    }

    private companion object {
        val directoryLocks = ConcurrentHashMap<String, Any>()

        fun resolveStorageDir(
            serviceName: String,
            baseDirectoryOverride: File?,
        ): File {
            val sanitized = sanitizeAndroidSecureServiceName(serviceName)
            val base =
                baseDirectoryOverride
                    ?: File(
                        System.getProperty("java.io.tmpdir")?.takeIf { it.isNotBlank() } ?: ".",
                        "abeta-kmp-android-secure",
                    )
            return File(base, sanitized)
        }

        fun applyOwnerOnlyPerms(
            file: File,
            isDirectory: Boolean,
        ) {
            runCatching {
                file.setReadable(false, false)
                file.setReadable(true, true)
                file.setWritable(false, false)
                file.setWritable(true, true)
                file.setExecutable(false, false)
                if (isDirectory) {
                    file.setExecutable(true, true)
                }
            }
        }

        fun sha256Hex(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray())
            val out = StringBuilder(digest.size * 2)
            for (b in digest) {
                val unsigned = b.toInt() and FALLBACK_HEX_MASK
                out.append(FALLBACK_HEX_CHARS[unsigned ushr FALLBACK_HEX_SHIFT])
                out.append(FALLBACK_HEX_CHARS[unsigned and FALLBACK_HEX_LOW_MASK])
            }
            return out.toString()
        }
    }
}
