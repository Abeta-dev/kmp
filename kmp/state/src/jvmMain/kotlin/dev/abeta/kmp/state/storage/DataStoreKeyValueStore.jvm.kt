package dev.abeta.kmp.state.storage

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import dev.abeta.kmp.core.coroutines.ioDispatcher
import dev.abeta.kmp.core.storage.ObservableKeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okio.Path
import okio.Path.Companion.toPath
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private val jvmDataStoreRegistry = ConcurrentHashMap<String, ObservableKeyValueStore>()

actual fun createDataStoreKeyValueStore(namespace: String): ObservableKeyValueStore {
    val sanitized = sanitizeDataStoreNamespace(namespace)
    return jvmDataStoreRegistry.computeIfAbsent(sanitized) { key ->
        val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
        val dataStore =
            PreferenceDataStoreFactory.createWithPath(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = scope,
                produceFile = { resolveJvmDataStorePath(key) },
            )
        DataStoreKeyValueStore(
            dataStore = dataStore,
            scope = scope,
        )
    }
}

private fun resolveJvmDataStorePath(sanitizedNamespace: String): Path {
    val baseDir =
        System
            .getProperty("user.home")
            ?.takeIf { it.isNotBlank() }
            ?.let { File(it, ".abeta-kmp/datastore") }
            ?: File(System.getProperty("java.io.tmpdir", "."), "abeta-kmp/datastore")
    baseDir.mkdirs()
    return File(baseDir, "$sanitizedNamespace.preferences_pb").absolutePath.toPath()
}
