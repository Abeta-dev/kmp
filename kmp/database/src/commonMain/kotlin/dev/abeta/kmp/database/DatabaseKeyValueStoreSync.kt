@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.database

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal data class DatabaseStoreSnapshot(
    val entries: Map<String, String> = emptyMap(),
    val lastPersistedEntries: Map<String, String> = emptyMap(),
    val pendingWrites: Int = 0,
    val hydrated: Boolean = false,
    val closed: Boolean = false,
)

internal sealed interface DatabaseWriteOp {
    data class Put(
        val key: String,
        val value: String,
    ) : DatabaseWriteOp

    data class Remove(
        val key: String,
    ) : DatabaseWriteOp

    data object Clear : DatabaseWriteOp
}

internal suspend fun runDatabaseSyncLoop(
    database: AbetaLocalDatabase,
    namespace: String,
    snapshot: MutableStateFlow<DatabaseStoreSnapshot>,
    writeQueue: Channel<DatabaseWriteOp>,
) {
    val persisted = readNamespaceEntries(database, namespace).toMutableMap()
    snapshot.update { current ->
        if (current.pendingWrites == 0) {
            current.copy(
                entries = persisted.toMap(),
                lastPersistedEntries = persisted.toMap(),
                hydrated = true,
            )
        } else {
            current.copy(
                lastPersistedEntries = persisted.toMap(),
                hydrated = true,
            )
        }
    }
    for (operation in writeQueue) {
        val committed = applyDatabaseWrite(database, namespace, operation, persisted)
        val authoritative =
            if (committed) {
                persisted.toMap()
            } else {
                val refreshed = readNamespaceEntries(database, namespace)
                persisted.clear()
                persisted.putAll(refreshed)
                refreshed
            }
        snapshot.update { current ->
            val remaining = (current.pendingWrites - 1).coerceAtLeast(0)
            if (remaining == 0) {
                current.copy(
                    entries = authoritative,
                    lastPersistedEntries = authoritative,
                    pendingWrites = 0,
                    hydrated = true,
                )
            } else {
                current.copy(
                    lastPersistedEntries = authoritative,
                    pendingWrites = remaining,
                )
            }
        }
    }
}

@Suppress("TooGenericExceptionCaught")
private suspend fun readNamespaceEntries(
    database: AbetaLocalDatabase,
    namespace: String,
): Map<String, String> =
    try {
        val keys = database.listKeys(namespace)
        buildMap(keys.size) {
            for (key in keys) {
                database.getValue(namespace = namespace, key = key)?.let { value ->
                    put(key, value)
                }
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        emptyMap()
    }

@Suppress("TooGenericExceptionCaught")
private suspend fun applyDatabaseWrite(
    database: AbetaLocalDatabase,
    namespace: String,
    operation: DatabaseWriteOp,
    persisted: MutableMap<String, String>,
): Boolean =
    try {
        when (operation) {
            is DatabaseWriteOp.Put -> {
                database.putValue(namespace = namespace, key = operation.key, value = operation.value)
                persisted[operation.key] = operation.value
            }

            is DatabaseWriteOp.Remove -> {
                database.removeValue(namespace = namespace, key = operation.key)
                persisted.remove(operation.key)
            }

            DatabaseWriteOp.Clear -> {
                database.clearNamespace(namespace = namespace)
                persisted.clear()
            }
        }
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        false
    }
