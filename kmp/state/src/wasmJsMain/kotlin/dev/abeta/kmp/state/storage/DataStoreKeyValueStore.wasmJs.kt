package dev.abeta.kmp.state.storage

import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.emptyPreferences
import dev.abeta.kmp.core.coroutines.ioDispatcher
import dev.abeta.kmp.core.storage.ObservableKeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okio.Path.Companion.toPath

private val wasmDataStoreRegistry = mutableMapOf<String, ObservableKeyValueStore>()

actual fun createDataStoreKeyValueStore(namespace: String): ObservableKeyValueStore {
    val sanitized = sanitizeDataStoreNamespace(namespace)
    return wasmDataStoreRegistry.getOrPut(sanitized) {
        val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
        val dataStore =
            PreferenceDataStoreFactory.createWithPath(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                scope = scope,
                produceFile = { "$sanitized.preferences_pb".toPath() },
            )
        DataStoreKeyValueStore(
            dataStore = dataStore,
            scope = scope,
        )
    }
}
