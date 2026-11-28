@file:Suppress("MatchingDeclarationName")

package dev.abeta.kmp.state.storage

import android.os.Build
import dev.abeta.kmp.core.platform.AbetaAndroidContext
import dev.abeta.kmp.core.storage.ObservableKeyValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

private const val INDEX_FILE_NAME = "index.json"
private const val ENTRY_FILE_SUFFIX = ".entry"
private const val TEMP_FILE_PREFIX = ".abeta-tmp-"
private const val TEMP_FILE_SUFFIX = ".tmp"
private const val SHA_256_ALGORITHM = "SHA-256"
private const val HEX_CHARS = "0123456789abcdef"
private const val HIGH_NIBBLE_SHIFT = 4
private const val NIBBLE_MASK = 0x0F
private const val BYTE_MASK = 0xFF

/**
 * Zero-KSP, thread-safe, file-backed [ObservableKeyValueStore] for Android and Wear OS.
 *
 * Stores a JSON key index alongside per-entry UTF-8 payload files inside
 * `<directoryPath>/<sanitizedNamespace>/`. Every write flushes via `fd.sync()` and atomically
 * replaces its target via `Files.move(..., ATOMIC_MOVE)` on API 26+ (or POSIX `renameTo` on API 24-25).
 */
class FileKeyValueStore(
    directoryPath: String,
    namespace: String = DEFAULT_DISK_NAMESPACE,
) : ObservableKeyValueStore {
    private val lock = Any()
    private val json = Json { ignoreUnknownKeys = true }
    private val sanitizedNamespace: String = sanitizeDiskNamespace(namespace)
    private val storeDir: File = File(directoryPath, sanitizedNamespace)
    private val normalizedStoreDir: File = storeDir.canonicalFile
    private val indexFile: File = File(storeDir, INDEX_FILE_NAME)

    private val index: MutableMap<String, String>
    private val entriesFlow: MutableStateFlow<Map<String, String>>

    init {
        val loaded = loadFromDisk()
        index = loaded.index.toMutableMap()
        entriesFlow = MutableStateFlow(loaded.entries)
    }

    override fun getString(key: String): String? = entriesFlow.value[key]

    override fun putString(
        key: String,
        value: String,
    ) {
        synchronized(lock) {
            val fileName = entryFileName(key)
            val entryFile = File(storeDir, fileName)
            atomicWriteBytes(storeDir, entryFile, value.toByteArray(Charsets.UTF_8))
            val isNewKey = !index.containsKey(key)
            if (isNewKey || !indexFile.isFile) {
                val nextIndex = index.toMutableMap().apply { put(key, fileName) }
                persistIndexLocked(nextIndex)
            }
            index[key] = fileName
            entriesFlow.update { current -> current + (key to value) }
        }
    }

    override fun remove(key: String) {
        synchronized(lock) {
            val fileName = index[key] ?: return
            val nextIndex = index.toMutableMap().apply { remove(key) }
            persistIndexLocked(nextIndex)
            index.remove(key)
            deleteEntryFileBestEffort(fileName)
            entriesFlow.update { current -> current - key }
        }
    }

    override fun clear() {
        synchronized(lock) {
            val previousFiles = index.values.toList()
            if (indexFile.exists()) {
                persistIndexLocked(emptyMap())
            }
            index.clear()
            previousFiles.forEach(::deleteEntryFileBestEffort)
            entriesFlow.value = emptyMap()
        }
    }

    override fun contains(key: String): Boolean = key in entriesFlow.value

    override fun keys(): Set<String> = entriesFlow.value.keys

    override fun observeKey(key: String): Flow<String?> =
        entriesFlow
            .map { entries -> entries[key] }
            .distinctUntilChanged()

    private fun loadFromDisk(): LoadedStore {
        val persistedIndex =
            if (indexFile.isFile) {
                readIndexOrNull(indexFile, json)
            } else {
                null
            }
        val validIndex = linkedMapOf<String, String>()
        val validEntries = linkedMapOf<String, String>()
        persistedIndex?.forEach { (key, fileName) ->
            val content =
                resolveSafeEntryFile(normalizedStoreDir, fileName)
                    ?.let(::readEntryOrNull)
            if (content != null) {
                validIndex[key] = fileName
                validEntries[key] = content
            }
        }
        return LoadedStore(index = validIndex, entries = validEntries)
    }

    private fun persistIndexLocked(nextIndex: Map<String, String>) {
        val serialized = json.encodeToString<Map<String, String>>(nextIndex)
        atomicWriteBytes(storeDir, indexFile, serialized.toByteArray(Charsets.UTF_8))
    }

    private fun deleteEntryFileBestEffort(fileName: String) {
        val entryFile = resolveSafeEntryFile(normalizedStoreDir, fileName) ?: return
        runCatching { entryFile.delete() }
    }

    private data class LoadedStore(
        val index: Map<String, String>,
        val entries: Map<String, String>,
    )
}

private fun readIndexOrNull(
    indexFile: File,
    json: Json,
): Map<String, String>? =
    try {
        val raw = indexFile.readText(Charsets.UTF_8)
        json.decodeFromString<Map<String, String>>(raw)
    } catch (_: IOException) {
        null
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

private fun readEntryOrNull(entryFile: File): String? =
    if (!entryFile.isFile) {
        null
    } else {
        try {
            entryFile.readText(Charsets.UTF_8)
        } catch (_: IOException) {
            null
        }
    }

private fun atomicWriteBytes(
    storeDir: File,
    target: File,
    bytes: ByteArray,
) {
    if (!storeDir.exists()) {
        storeDir.mkdirs()
    }
    val tempFile = File.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX, storeDir)
    var moved = false
    try {
        FileOutputStream(tempFile).use { output ->
            output.write(bytes)
            output.fd.sync()
        }
        moveAtomically(tempFile, target)
        moved = true
    } finally {
        if (!moved) {
            runCatching { tempFile.delete() }
        }
    }
}

private fun moveAtomically(
    source: File,
    target: File,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (_: UnsupportedOperationException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } else if (!source.renameTo(target)) {
        target.delete()
        if (!source.renameTo(target)) {
            throw IOException("Failed to atomically move ${source.name} to ${target.name}")
        }
    }
}

private fun resolveSafeEntryFile(
    normalizedStoreDir: File,
    fileName: String,
): File? {
    val invalidName = fileName.isBlank() || fileName.contains('/') || fileName.contains('\\')
    if (invalidName) return null
    val candidate = runCatching { File(normalizedStoreDir, fileName).canonicalFile }.getOrNull()
    return candidate?.takeIf { it.parentFile == normalizedStoreDir }
}

actual fun createDiskKeyValueStore(
    namespace: String,
    directoryPath: String?,
): ObservableKeyValueStore {
    val resolvedDirectory =
        directoryPath
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: resolveDefaultAndroidDiskCacheDirectory()
    return FileKeyValueStore(
        directoryPath = resolvedDirectory,
        namespace = namespace,
    )
}

private fun resolveDefaultAndroidDiskCacheDirectory(): String {
    val context = AbetaAndroidContext.applicationContextOrNull()
    val baseDir =
        if (context != null) {
            File(context.filesDir, "abeta-kmp-kv")
        } else {
            val tmpDir = System.getProperty("java.io.tmpdir", ".")
            File(tmpDir, "abeta-kmp-kv")
        }
    return baseDir.absolutePath
}

private fun entryFileName(key: String): String {
    val digest = MessageDigest.getInstance(SHA_256_ALGORITHM).digest(key.toByteArray(Charsets.UTF_8))
    val chars = CharArray(digest.size * 2)
    for (i in digest.indices) {
        val value = digest[i].toInt() and BYTE_MASK
        chars[i * 2] = HEX_CHARS[value ushr HIGH_NIBBLE_SHIFT]
        chars[i * 2 + 1] = HEX_CHARS[value and NIBBLE_MASK]
    }
    return "${chars.concatToString()}$ENTRY_FILE_SUFFIX"
}
