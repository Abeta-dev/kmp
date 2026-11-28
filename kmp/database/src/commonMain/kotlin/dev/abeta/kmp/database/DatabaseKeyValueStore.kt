package dev.abeta.kmp.database

import dev.abeta.kmp.core.coroutines.ioDispatcher
import dev.abeta.kmp.core.storage.ObservableKeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * [ObservableKeyValueStore] adapter bridging Room 3 [AbetaLocalDatabase]'s `key_value_records`
 * table directly to Layer 0's synchronous + reactive key-value port.
 *
 * Maintains an in-memory [MutableStateFlow] snapshot for immediate read-your-own-writes
 * consistency while serializing all mutations asynchronously into SQLite on [scope].
 */
class DatabaseKeyValueStore(
    private val database: AbetaLocalDatabase,
    private val namespace: String = DEFAULT_NAMESPACE,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + ioDispatcher),
) : ObservableKeyValueStore {
    private val storeJob = SupervisorJob(scope.coroutineContext[Job])
    private val workerScope = CoroutineScope(scope.coroutineContext + storeJob)
    private val snapshot = MutableStateFlow(DatabaseStoreSnapshot())
    private val writeQueue = Channel<DatabaseWriteOp>(capacity = Channel.UNLIMITED)

    init {
        require(namespace.isNotBlank()) { "namespace must not be blank." }
        workerScope.launch {
            runDatabaseSyncLoop(
                database = database,
                namespace = namespace,
                snapshot = snapshot,
                writeQueue = writeQueue,
            )
        }
    }

    override fun getString(key: String): String? = snapshot.value.entries[key]

    override fun putString(
        key: String,
        value: String,
    ) {
        require(key.isNotBlank()) { "key must not be blank." }
        enqueueWrite(DatabaseWriteOp.Put(key, value)) { entries -> entries + (key to value) }
    }

    override fun remove(key: String) {
        enqueueWrite(DatabaseWriteOp.Remove(key)) { entries -> entries - key }
    }

    override fun clear() {
        enqueueWrite(DatabaseWriteOp.Clear) { emptyMap() }
    }

    override fun contains(key: String): Boolean = key in snapshot.value.entries

    override fun keys(): Set<String> = snapshot.value.entries.keys

    override fun observeKey(key: String): Flow<String?> =
        snapshot
            .map { state -> state.entries[key] }
            .distinctUntilChanged()

    /** Reactive stream of string updates for [key], delegating to [observeKey]. */
    fun observeString(key: String): Flow<String?> = observeKey(key)

    /**
     * Suspends until initial SQLite hydration completes and all previously enqueued mutations
     * have been committed to [database].
     */
    suspend fun awaitSync() {
        snapshot.first { state -> state.closed || (state.hydrated && state.pendingWrites == 0) }
    }

    /** Stops background synchronization workers and closes the pending write queue. */
    fun close() {
        snapshot.update { current -> current.copy(closed = true, pendingWrites = 0) }
        writeQueue.close()
        storeJob.cancel()
    }

    private fun enqueueWrite(
        operation: DatabaseWriteOp,
        transform: (Map<String, String>) -> Map<String, String>,
    ) {
        var accepted = false
        snapshot.update { current ->
            if (current.closed) {
                accepted = false
                current
            } else {
                accepted = true
                current.copy(
                    entries = transform(current.entries),
                    pendingWrites = current.pendingWrites + 1,
                )
            }
        }
        if (accepted && writeQueue.trySend(operation).isFailure) {
            snapshot.update { current ->
                current.copy(pendingWrites = (current.pendingWrites - 1).coerceAtLeast(0))
            }
        }
    }

    companion object {
        /** Default `key_value_records` namespace used when none is specified. */
        const val DEFAULT_NAMESPACE: String = "abeta_kv"
    }
}
