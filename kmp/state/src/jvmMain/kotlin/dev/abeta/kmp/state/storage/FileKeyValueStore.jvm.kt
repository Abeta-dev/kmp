@file:Suppress("MatchingDeclarationName")

package dev.abeta.kmp.state.storage

import dev.abeta.kmp.core.storage.ObservableKeyValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
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
 * Zero-KSP, thread-safe, file-backed [ObservableKeyValueStore] for JVM Desktop.
 *
 * Stores a JSON key index alongside per-entry UTF-8 payload files inside
 * `<directoryPath>/<sanitizedNamespace>/`. Every write replaces its target atomically via a
 * same-directory temporary file and [Files.move] with [StandardCopyOption.ATOMIC_MOVE] (falling
 * back to [StandardCopyOption.REPLACE_EXISTING] on filesystems that do not support atomic moves).
 *
 * Unlike `java.util.prefs.Preferences`, values have no 8 KB ceiling and safely hold large SDUI
 * screen payloads across process restarts.
 */
class FileKeyValueStore(
    directoryPath: String,
    namespace: String = DEFAULT_DISK_NAMESPACE,
) : ObservableKeyValueStore {
    private val lock = Any()
    private val json = Json { ignoreUnknownKeys = true }
    private val sanitizedNamespace: String = sanitizeDiskNamespace(namespace)
    private val storeDir: Path = Paths.get(directoryPath).resolve(sanitizedNamespace)
    private val normalizedStoreDir: Path = storeDir.toAbsolutePath().normalize()
    private val indexFile: Path = storeDir.resolve(INDEX_FILE_NAME)

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
            val entryPath = storeDir.resolve(fileName)
            atomicWriteBytes(storeDir, entryPath, value.toByteArray(Charsets.UTF_8))
            val isNewKey = !index.containsKey(key)
            index[key] = fileName
            if (isNewKey || !Files.isRegularFile(indexFile)) {
                persistIndexLocked()
            }
            entriesFlow.update { current -> current + (key to value) }
        }
    }

    override fun remove(key: String) {
        synchronized(lock) {
            val fileName = index.remove(key) ?: return
            persistIndexLocked()
            deleteEntryFileBestEffort(fileName)
            entriesFlow.update { current -> current - key }
        }
    }

    override fun clear() {
        synchronized(lock) {
            val previousFiles = index.values.toList()
            index.clear()
            if (Files.exists(indexFile)) {
                persistIndexLocked()
            }
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
            if (Files.isRegularFile(indexFile)) {
                readIndexOrNull(indexFile, json)
            } else {
                null
            }
        val validIndex = linkedMapOf<String, String>()
        val validEntries = linkedMapOf<String, String>()
        persistedIndex?.forEach { (key, fileName) ->
            val content =
                resolveSafeEntryPath(normalizedStoreDir, fileName)
                    ?.let(::readEntryOrNull)
            if (content != null) {
                validIndex[key] = fileName
                validEntries[key] = content
            }
        }
        return LoadedStore(index = validIndex, entries = validEntries)
    }

    private fun persistIndexLocked() {
        val serialized = json.encodeToString<Map<String, String>>(index)
        atomicWriteBytes(storeDir, indexFile, serialized.toByteArray(Charsets.UTF_8))
    }

    private fun deleteEntryFileBestEffort(fileName: String) {
        val entryPath = resolveSafeEntryPath(normalizedStoreDir, fileName) ?: return
        try {
            Files.deleteIfExists(entryPath)
        } catch (_: IOException) {
            // Entry already removed from the persisted index; ignore cleanup failures.
        }
    }

    private data class LoadedStore(
        val index: Map<String, String>,
        val entries: Map<String, String>,
    )
}

private fun readIndexOrNull(
    indexFile: Path,
    json: Json,
): Map<String, String>? =
    try {
        val raw = Files.readString(indexFile, Charsets.UTF_8)
        json.decodeFromString<Map<String, String>>(raw)
    } catch (_: IOException) {
        null
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

private fun readEntryOrNull(entryPath: Path): String? =
    if (!Files.isRegularFile(entryPath)) {
        null
    } else {
        try {
            Files.readString(entryPath, Charsets.UTF_8)
        } catch (_: IOException) {
            null
        }
    }

private fun atomicWriteBytes(
    storeDir: Path,
    target: Path,
    bytes: ByteArray,
) {
    Files.createDirectories(storeDir)
    val tempFile = Files.createTempFile(storeDir, TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX)
    try {
        Files.write(tempFile, bytes)
        moveAtomically(tempFile, target)
    } finally {
        Files.deleteIfExists(tempFile)
    }
}

private fun moveAtomically(
    source: Path,
    target: Path,
) {
    try {
        Files.move(
            source,
            target,
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
    } catch (_: UnsupportedOperationException) {
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
    }
}

private fun resolveSafeEntryPath(
    normalizedStoreDir: Path,
    fileName: String,
): Path? {
    val invalidName = fileName.isBlank() || fileName.contains('/') || fileName.contains('\\')
    val candidate = if (invalidName) null else normalizedStoreDir.resolve(fileName).normalize()
    return candidate?.takeIf { it.parent == normalizedStoreDir }
}

actual fun createDiskKeyValueStore(
    namespace: String,
    directoryPath: String?,
): ObservableKeyValueStore {
    val resolvedDirectory =
        directoryPath
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: resolveDefaultJvmDiskCacheDirectory()
    return FileKeyValueStore(
        directoryPath = resolvedDirectory,
        namespace = namespace,
    )
}

private fun resolveDefaultJvmDiskCacheDirectory(): String {
    val home = System.getProperty("user.home")?.trim()?.takeIf { it.isNotEmpty() }
    if (home == null) {
        val tmpDir = System.getProperty("java.io.tmpdir", ".")
        return Paths.get(tmpDir, "abeta-kmp", "disk-cache").toAbsolutePath().toString()
    }
    val osName = System.getProperty("os.name").orEmpty().lowercase()
    val cachePath =
        when {
            osName.contains("mac") -> Paths.get(home, "Library", "Caches", "abeta-kmp")
            osName.contains("win") -> Paths.get(home, "AppData", "Local", "abeta-kmp", "cache")
            else -> Paths.get(home, ".cache", "abeta-kmp")
        }
    return cachePath.toAbsolutePath().toString()
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
