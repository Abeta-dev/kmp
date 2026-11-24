package dev.abeta.kmp.state.storage

import dev.abeta.kmp.core.storage.ObservableKeyValueStore

internal const val DEFAULT_DISK_NAMESPACE: String = "abeta_disk_cache"

private val DISK_NAMESPACE_INVALID_CHARS = Regex("[^A-Za-z0-9_.-]")
private val CONSECUTIVE_DOTS = Regex("\\.{2,}")

/**
 * Creates a lightweight, zero-KSP / zero-Room persistent [ObservableKeyValueStore] scoped to
 * [namespace].
 *
 * Designed for storing arbitrarily large payloads such as SDUI screen JSON caches and durable
 * session state without JVM `java.util.prefs.Preferences` 8 KB value caps or Room/KSP code
 * generation overhead:
 * - **JVM Desktop**: atomic file-backed store (`FileKeyValueStore`) under [directoryPath] (or an
 *   OS-appropriate cache directory under `user.home` when [directoryPath] is `null`).
 * - **Apple (iOS, macOS, watchOS)**: namespace-isolated `NSUserDefaults` store
 *   (`UserDefaultsKeyValueStore`).
 * - **WasmJS**: namespace-isolated browser `localStorage` store (`WebStorageKeyValueStore`) with
 *   an automatic in-memory fallback when `localStorage` is unavailable.
 */
expect fun createDiskKeyValueStore(
    namespace: String = DEFAULT_DISK_NAMESPACE,
    directoryPath: String? = null,
): ObservableKeyValueStore

internal fun sanitizeDiskNamespace(namespace: String): String =
    namespace
        .trim()
        .replace(DISK_NAMESPACE_INVALID_CHARS, "_")
        .trim('.')
        .replace(CONSECUTIVE_DOTS, "_")
        .ifBlank { DEFAULT_DISK_NAMESPACE }
