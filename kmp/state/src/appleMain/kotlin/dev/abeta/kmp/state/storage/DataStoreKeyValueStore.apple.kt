@file:OptIn(ExperimentalForeignApi::class)

package dev.abeta.kmp.state.storage

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import dev.abeta.kmp.core.coroutines.ioDispatcher
import dev.abeta.kmp.core.storage.ObservableKeyValueStore
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toPath
import platform.Foundation.NSFileManager
import platform.Foundation.NSLock

private val appleRegistryLock = NSLock()
private val appleDataStoreRegistry = mutableMapOf<String, ObservableKeyValueStore>()

actual fun createDataStoreKeyValueStore(namespace: String): ObservableKeyValueStore {
    val sanitized = sanitizeDataStoreNamespace(namespace)
    appleRegistryLock.lock()
    try {
        return appleDataStoreRegistry.getOrPut(sanitized) {
            val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
            val dataStore =
                PreferenceDataStoreFactory.createWithPath(
                    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                    scope = scope,
                    produceFile = { resolveAppleDataStorePath(sanitized).toPath() },
                )
            DataStoreKeyValueStore(
                dataStore = dataStore,
                scope = scope,
            )
        }
    } finally {
        appleRegistryLock.unlock()
    }
}

private fun resolveAppleDataStorePath(sanitizedNamespace: String): String {
    val directory = "${resolveAppleDocumentsDirectory().trimEnd('/')}/abeta-kmp/datastore"
    NSFileManager.defaultManager.createDirectoryAtPath(
        path = directory,
        withIntermediateDirectories = true,
        attributes = null,
        error = null,
    )
    return "$directory/$sanitizedNamespace.preferences_pb"
}

internal expect fun resolveAppleDocumentsDirectory(): String
