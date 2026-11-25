@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.core.security

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermissions
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val AES_ALGORITHM = "AES"
private const val AES_GCM_TRANSFORM = "AES/GCM/NoPadding"
private const val AES_KEY_BITS = 256
private const val AES_KEY_BYTES = 32
private const val GCM_IV_BYTES = 12
private const val GCM_TAG_BITS = 128
private const val MASTER_KEY_FILE = "master.key"
private const val ENTRY_EXTENSION = ".enc"
private const val DIR_POSIX_PERMS = "rwx------"
private const val FILE_POSIX_PERMS = "rw-------"
private const val HEX_MASK = 0xFF
private const val HEX_SHIFT = 4
private const val HEX_LOW_MASK = 0x0F
private const val HEX_CHARS = "0123456789abcdef"

/**
 * JVM [SecureKeyValueStore] encrypting entries with AES-256-GCM inside a restricted-permission
 * (`0600`/`0700` POSIX where supported) file-backed directory without Java Preferences size limits.
 */
@Suppress("TooManyFunctions")
internal class JvmSecureStore(
    serviceName: String,
    baseDirectoryOverride: Path? = null,
) : SecureKeyValueStore {
    private val storageDir: Path = resolveStorageDir(serviceName, baseDirectoryOverride)
    private val lock: Any = directoryLocks.computeIfAbsent(storageDir.toAbsolutePath().normalize()) { Any() }
    private val random = SecureRandom()
    private var cachedKey: SecretKey? = null

    override val storageBackend: String = "JvmAesGcmFileStore"
    override val isHardwareOrOsEncrypted: Boolean = false

    override fun getString(key: String): String? =
        synchronized(lock) {
            val file = entryPath(key)
            if (!Files.exists(file)) return null
            val record = readEntryLocked(file) ?: return null
            if (record.first == key) record.second else null
        }

    override fun putString(
        key: String,
        value: String,
    ) {
        synchronized(lock) {
            ensureStorageDirLocked()
            val encodedKey = Base64.getEncoder().encodeToString(key.encodeToByteArray())
            val encodedVal = Base64.getEncoder().encodeToString(value.encodeToByteArray())
            val encrypted = encryptLocked("$encodedKey\n$encodedVal")
            writeAtomicallyLocked(entryPath(key), encrypted)
        }
    }

    override fun remove(key: String) {
        synchronized(lock) {
            runCatching { Files.deleteIfExists(entryPath(key)) }
        }
    }

    override fun clear() {
        synchronized(lock) {
            if (!Files.exists(storageDir)) return
            runCatching {
                Files.list(storageDir).use { stream ->
                    stream
                        .filter { it.fileName.toString().endsWith(ENTRY_EXTENSION) }
                        .forEach { file -> runCatching { Files.deleteIfExists(file) } }
                }
            }
        }
    }

    override fun contains(key: String): Boolean = getString(key) != null

    override fun keys(): Set<String> =
        synchronized(lock) {
            if (!Files.exists(storageDir)) return emptySet()
            val result = linkedSetOf<String>()
            runCatching {
                Files.list(storageDir).use { stream ->
                    stream
                        .filter { it.fileName.toString().endsWith(ENTRY_EXTENSION) }
                        .forEach { file ->
                            readEntryLocked(file)?.first?.let(result::add)
                        }
                }
            }
            result
        }

    private fun entryPath(key: String): Path = storageDir.resolve("${sha256Hex(key)}$ENTRY_EXTENSION")

    private fun masterKeyPath(): Path = storageDir.resolve(MASTER_KEY_FILE)

    private fun ensureStorageDirLocked() {
        if (!Files.exists(storageDir)) {
            createDirWithRestrictedPerms(storageDir)
        }
    }

    private fun loadOrCreateKeyLocked(): SecretKey {
        cachedKey?.let { return it }
        ensureStorageDirLocked()
        val keyFile = masterKeyPath()
        val existingKey =
            if (Files.exists(keyFile)) {
                runCatching { Files.readAllBytes(keyFile) }
                    .getOrNull()
                    ?.let(::decodeMasterKeyBytes)
                    ?.let { bytes -> SecretKeySpec(bytes, AES_ALGORITHM) }
            } else {
                null
            }
        val resolved =
            existingKey ?: run {
                val generator = KeyGenerator.getInstance(AES_ALGORITHM)
                generator.init(AES_KEY_BITS, random)
                val generated = generator.generateKey()
                val encoded = Base64.getEncoder().encode(generated.encoded)
                writeAtomicallyLocked(keyFile, encoded)
                generated
            }
        cachedKey = resolved
        return resolved
    }

    private fun decodeMasterKeyBytes(raw: ByteArray): ByteArray? =
        runCatching {
            val trimmed = raw.decodeToString().trim()
            val decoded = Base64.getDecoder().decode(trimmed)
            decoded.takeIf { it.size == AES_KEY_BYTES }
        }.getOrNull()

    private fun encryptLocked(plaintext: String): ByteArray {
        val secretKey = loadOrCreateKeyLocked()
        val iv = ByteArray(GCM_IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext.encodeToByteArray())
        val combined = ByteArray(iv.size + ciphertext.size)
        iv.copyInto(combined, destinationOffset = 0)
        ciphertext.copyInto(combined, destinationOffset = iv.size)
        return combined
    }

    private fun readEntryLocked(file: Path): Pair<String, String>? {
        val plaintext =
            runCatching { Files.readAllBytes(file) }
                .getOrNull()
                ?.let(::decryptOrNullLocked)
        val newlineIndex = plaintext?.indexOf('\n') ?: -1
        return if (plaintext == null || newlineIndex < 0) {
            null
        } else {
            runCatching {
                val keyBytes = Base64.getDecoder().decode(plaintext.substring(0, newlineIndex))
                val valBytes = Base64.getDecoder().decode(plaintext.substring(newlineIndex + 1))
                keyBytes.decodeToString() to valBytes.decodeToString()
            }.getOrNull()
        }
    }

    private fun decryptOrNullLocked(combined: ByteArray): String? {
        if (combined.size <= GCM_IV_BYTES) return null
        val secretKey = loadOrCreateKeyLocked()
        return try {
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
    }

    private fun writeAtomicallyLocked(
        target: Path,
        bytes: ByteArray,
    ) {
        val tempFile = createRestrictedTempFile(storageDir)
        var moved = false
        try {
            FileChannel.open(tempFile, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) {
                    channel.write(buffer)
                }
                channel.force(true)
            }
            applyRestrictedFilePerms(tempFile)
            try {
                Files.move(
                    tempFile,
                    target,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING)
            }
            applyRestrictedFilePerms(target)
            moved = true
        } finally {
            if (!moved) {
                runCatching { Files.deleteIfExists(tempFile) }
            }
        }
    }

    private companion object {
        val directoryLocks = ConcurrentHashMap<Path, Any>()

        fun sanitizeNode(raw: String): String = raw.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "default" }

        fun resolveStorageDir(
            serviceName: String,
            baseDirectoryOverride: Path?,
        ): Path {
            val sanitized = sanitizeNode(serviceName)
            val userHome = System.getProperty("user.home")?.takeIf { it.isNotBlank() }
            val preferred = userHome?.let { Paths.get(it, ".abeta-kmp", "secure", sanitized) }
            val tmpBase = System.getProperty("java.io.tmpdir")?.takeIf { it.isNotBlank() } ?: "."
            return when {
                baseDirectoryOverride != null -> baseDirectoryOverride.resolve(sanitized)
                preferred != null && canPrepareDirectory(preferred) -> preferred
                else -> Paths.get(tmpBase, "abeta-kmp-secure", sanitized)
            }
        }

        fun canPrepareDirectory(dir: Path): Boolean =
            try {
                createDirWithRestrictedPerms(dir)
                Files.isWritable(dir)
            } catch (_: IOException) {
                false
            } catch (_: SecurityException) {
                false
            }

        fun createDirWithRestrictedPerms(dir: Path) {
            Files.createDirectories(dir)
            runCatching {
                Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString(DIR_POSIX_PERMS))
            }
        }

        fun createRestrictedTempFile(dir: Path): Path =
            try {
                val attr = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(FILE_POSIX_PERMS))
                Files.createTempFile(dir, ".abeta-sec-", ".tmp", attr)
            } catch (_: UnsupportedOperationException) {
                Files.createTempFile(dir, ".abeta-sec-", ".tmp")
            }

        fun applyRestrictedFilePerms(file: Path) {
            runCatching {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(FILE_POSIX_PERMS))
            }
        }

        fun sha256Hex(value: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(value.encodeToByteArray())
            val out = StringBuilder(digest.size * 2)
            for (b in digest) {
                val unsigned = b.toInt() and HEX_MASK
                out.append(HEX_CHARS[unsigned ushr HEX_SHIFT])
                out.append(HEX_CHARS[unsigned and HEX_LOW_MASK])
            }
            return out.toString()
        }
    }
}

/** Creates an AES-256-GCM encrypted [SecureKeyValueStore] backed by restricted-permission files on JVM. */
actual fun createSecureKeyValueStore(serviceName: String): SecureKeyValueStore = JvmSecureStore(serviceName)
