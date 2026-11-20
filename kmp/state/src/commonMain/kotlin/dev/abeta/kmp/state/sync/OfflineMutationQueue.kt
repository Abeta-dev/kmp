package dev.abeta.kmp.state.sync

import dev.abeta.kmp.core.identity.RandomUuidGenerator
import dev.abeta.kmp.core.identity.UuidGenerator
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.storage.KeyValueStore
import dev.abeta.kmp.core.time.Clock
import dev.abeta.kmp.core.time.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Durable offline mutation record waiting to be synchronized with a remote backend.
 */
@Serializable
data class QueuedMutation(
    val id: String,
    val type: String,
    val payload: JsonElement,
    val createdAtEpochMillis: Long,
    val retryCount: Int = 0,
)

/**
 * Aggregate metrics describing the current [OfflineMutationQueue] backlog.
 */
data class OfflineQueueStatus(
    val pendingCount: Int,
    val isFlushing: Boolean = false,
    val lastFlushedEpochMillis: Long? = null,
    val droppedDeadLetters: Int = 0,
)

/**
 * Summary emitted after executing [OfflineMutationQueue.flush].
 */
data class FlushReport(
    val succeeded: Int,
    val retainedForRetry: Int,
    val droppedDeadLetters: Int,
)

/**
 * Local-first durable FIFO mutation queue backed by a [KeyValueStore] and bounded capacity.
 *
 * Ensures mutations triggered while offline survive app restarts and retry safely when flushed.
 */
class OfflineMutationQueue(
    private val backingStore: KeyValueStore,
    private val clock: Clock = SystemClock(),
    private val uuidGenerator: UuidGenerator = RandomUuidGenerator(),
    private val maxQueueSize: Int = DEFAULT_MAX_QUEUE_SIZE,
    private val maxRetries: Int = DEFAULT_MAX_RETRIES,
    private val storageKey: String = DEFAULT_STORAGE_KEY,
    private val json: Json = DefaultSyncJson,
) {
    private val mutex = Mutex()
    private val _queue = MutableStateFlow(loadPersisted())
    private val _status = MutableStateFlow(OfflineQueueStatus(pendingCount = _queue.value.size))

    /** Observable ordered list of pending mutations. */
    val queue: StateFlow<List<QueuedMutation>> = _queue.asStateFlow()

    /** Observable summary counters and flush state. */
    val status: StateFlow<OfflineQueueStatus> = _status.asStateFlow()

    /** Appends a mutation of [type] with [payload], evicting the oldest entry if [maxQueueSize] is reached. */
    suspend fun enqueue(
        type: String,
        payload: JsonElement,
        id: String = uuidGenerator.nextId(),
    ): QueuedMutation =
        mutex.withLock {
            val item =
                QueuedMutation(
                    id = id,
                    type = type,
                    payload = payload,
                    createdAtEpochMillis = clock.nowEpochMillis(),
                )
            val combined = (_queue.value.filterNot { it.id == item.id } + item).takeLast(maxQueueSize.coerceAtLeast(1))
            persistAndEmit(combined)
            item
        }

    /** Removes a single queued mutation by [id]. */
    suspend fun remove(id: String) {
        mutex.withLock {
            persistAndEmit(_queue.value.filterNot { it.id == id })
        }
    }

    /** Clears all pending mutations from memory and durable storage. */
    suspend fun clear() {
        mutex.withLock {
            _queue.value = emptyList()
            backingStore.remove(storageKey)
            _status.value = OfflineQueueStatus(pendingCount = 0)
        }
    }

    /**
     * Attempts to flush every pending mutation sequentially through [handler].
     *
     * Successful items are dequeued; failures increment `retryCount` and are evicted once
     * `retryCount >= maxRetries`.
     */
    suspend fun flush(handler: suspend (QueuedMutation) -> AppResult<Unit>): FlushReport =
        mutex.withLock {
            val snapshot = _queue.value
            if (snapshot.isEmpty()) {
                return@withLock FlushReport(succeeded = 0, retainedForRetry = 0, droppedDeadLetters = 0)
            }
            _status.value = _status.value.copy(isFlushing = true)

            val remaining = mutableListOf<QueuedMutation>()
            var succeeded = 0
            var deadLetters = 0

            for (item in snapshot) {
                when (handler(item)) {
                    is AppResult.Success -> {
                        succeeded++
                    }

                    is AppResult.Failure -> {
                        val updatedRetry = item.retryCount + 1
                        if (updatedRetry >= maxRetries) {
                            deadLetters++
                        } else {
                            remaining.add(item.copy(retryCount = updatedRetry))
                        }
                    }
                }
            }

            persistAndEmit(
                updated = remaining,
                isFlushing = false,
                lastFlushed = clock.nowEpochMillis(),
                droppedIncrement = deadLetters,
            )

            FlushReport(
                succeeded = succeeded,
                retainedForRetry = remaining.size,
                droppedDeadLetters = deadLetters,
            )
        }

    private fun persistAndEmit(
        updated: List<QueuedMutation>,
        isFlushing: Boolean = _status.value.isFlushing,
        lastFlushed: Long? = _status.value.lastFlushedEpochMillis,
        droppedIncrement: Int = 0,
    ) {
        _queue.value = updated
        if (updated.isEmpty()) {
            backingStore.remove(storageKey)
        } else {
            backingStore.putString(storageKey, json.encodeToString(updated))
        }
        _status.value =
            _status.value.copy(
                pendingCount = updated.size,
                isFlushing = isFlushing,
                lastFlushedEpochMillis = lastFlushed,
                droppedDeadLetters = _status.value.droppedDeadLetters + droppedIncrement,
            )
    }

    private fun loadPersisted(): List<QueuedMutation> {
        val raw = backingStore.getString(storageKey) ?: return emptyList()
        return runCatching { json.decodeFromString<List<QueuedMutation>>(raw) }.getOrElse { emptyList() }
    }

    companion object {
        const val DEFAULT_MAX_QUEUE_SIZE: Int = 500
        const val DEFAULT_MAX_RETRIES: Int = 5
        const val DEFAULT_STORAGE_KEY: String = "abeta_offline_mutation_queue"
    }
}

private val DefaultSyncJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
