package dev.abeta.kmp.analytics

import dev.abeta.kmp.core.error.AppError
import dev.abeta.kmp.core.error.NetworkError
import dev.abeta.kmp.core.error.ValidationError
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.storage.KeyValueStore
import dev.abeta.kmp.core.time.Clock
import dev.abeta.kmp.core.time.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

private const val HTTP_CLIENT_ERROR_START = 400
private const val HTTP_CLIENT_ERROR_END = 499
private const val HTTP_REQUEST_TIMEOUT = 408
private const val HTTP_TOO_MANY_REQUESTS = 429

/**
 * Async sink receiving batched [AnalyticsEvent] payloads for network or file delivery.
 */
fun interface AnalyticsBatchSink {
    /** Delivers a non-empty [events] batch and returns [AppResult.Success] when durable server receipt is confirmed. */
    suspend fun sendBatch(events: List<AnalyticsEvent>): AppResult<Unit>
}

/**
 * Offline-buffered, batch-dispatching [AnalyticsTracker] backed by [KeyValueStore] durability.
 *
 * Events and impressions enqueue synchronously without blocking callers and flush either when
 * [batchSize] is reached, on a periodic background ticker, or via explicit [flush] invocations.
 */
class BufferedAnalyticsTracker(
    private val sink: AnalyticsBatchSink,
    private val backingStore: KeyValueStore,
    private val clock: Clock = SystemClock(),
    private val scope: CoroutineScope? = null,
    private val batchSize: Int = DEFAULT_BATCH_SIZE,
    private val flushIntervalMillis: Long = DEFAULT_FLUSH_INTERVAL_MILLIS,
    private val maxQueueSize: Int = DEFAULT_MAX_QUEUE_SIZE,
    private val storageKey: String = DEFAULT_STORAGE_KEY,
    private val json: Json = DefaultAnalyticsJson,
) : AnalyticsTracker {
    private data class QueuedEntry(
        val sequenceId: Long,
        val event: AnalyticsEvent,
    )

    private data class QueueState(
        val nextSequenceId: Long,
        val entries: List<QueuedEntry>,
    )

    private val flushMutex = Mutex()
    private val queueState: MutableStateFlow<QueueState>
    private val _queuedEvents: MutableStateFlow<List<AnalyticsEvent>>
    private val autoFlushScheduled = MutableStateFlow(false)
    private var periodicJob: Job? = null

    init {
        val loaded = loadPersisted()
        val initialEntries =
            loaded.mapIndexed { index, event ->
                QueuedEntry(sequenceId = index.toLong() + 1L, event = event)
            }
        queueState =
            MutableStateFlow(
                QueueState(
                    nextSequenceId = initialEntries.size.toLong(),
                    entries = initialEntries,
                ),
            )
        _queuedEvents = MutableStateFlow(loaded)
    }

    /** Observable snapshot of buffered events waiting to be delivered. */
    val queuedEvents: StateFlow<List<AnalyticsEvent>> = _queuedEvents.asStateFlow()

    /** Starts background periodic flushing when a [CoroutineScope] was provided. */
    @Suppress("TooGenericExceptionCaught")
    fun startPeriodicFlush() {
        val activeScope = scope ?: return
        if (periodicJob?.isActive == true || flushIntervalMillis <= 0L) return
        periodicJob =
            activeScope.launch {
                while (isActive) {
                    delay(flushIntervalMillis)
                    try {
                        flush()
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Throwable) {
                        // Keep periodic flushing alive across unexpected sink exceptions.
                    }
                }
            }
    }

    /** Cancels periodic background flushing without discarding queued events. */
    fun stopPeriodicFlush() {
        periodicJob?.cancel()
        periodicJob = null
    }

    override fun track(event: AnalyticsEvent) {
        val timestamped =
            if (event.timestampEpochMillis == null) {
                event.copy(timestampEpochMillis = clock.nowEpochMillis())
            } else {
                event
            }
        enqueueInternal(timestamped)
    }

    override fun trackImpression(payload: AnalyticsTrackingPayload) {
        enqueueInternal(
            AnalyticsEvent(
                name = payload.eventName,
                properties = payload.properties,
                timestampEpochMillis = clock.nowEpochMillis(),
                category = AnalyticsCategory.IMPRESSION,
            ),
        )
    }

    /**
     * Flushes buffered events in chunks up to [batchSize], preserving any events enqueued while a
     * batch is in flight, dropping non-retryable 4xx / [ValidationError] batches to prevent
     * head-of-line blocking, and halting on retryable failures. Returns the count of delivered events.
     */
    suspend fun flush(): AppResult<Int> =
        flushMutex.withLock {
            var delivered = 0
            val chunkSize = batchSize.coerceAtLeast(1)
            while (true) {
                val batchEntries = queueState.value.entries.take(chunkSize)
                if (batchEntries.isEmpty()) {
                    return@withLock AppResult.Success(delivered)
                }
                val batchEvents = batchEntries.map { it.event }
                when (val outcome = sink.sendBatch(batchEvents)) {
                    is AppResult.Success -> {
                        delivered += batchEntries.size
                        removeEntries(batchEntries)
                    }

                    is AppResult.Failure -> {
                        if (isNonRetryableBatchError(outcome.error)) {
                            removeEntries(batchEntries)
                        } else {
                            return@withLock AppResult.Failure(outcome.error)
                        }
                    }
                }
            }
            @Suppress("UNREACHABLE_CODE")
            AppResult.Success(delivered)
        }

    /** Discards all queued events from memory and persistent storage. */
    fun clearQueue() {
        queueState.update { current -> current.copy(entries = emptyList()) }
        syncSnapshotAndStorage()
    }

    private fun enqueueInternal(event: AnalyticsEvent) {
        val capacity = maxQueueSize.coerceAtLeast(1)
        val updated =
            queueState.updateAndGet { current ->
                val nextId = current.nextSequenceId + 1L
                val bounded = (current.entries + QueuedEntry(nextId, event)).takeLast(capacity)
                QueueState(nextSequenceId = nextId, entries = bounded)
            }
        syncSnapshotAndStorage()
        if (updated.entries.size >= batchSize) {
            scheduleCoalescedAutoFlush()
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun scheduleCoalescedAutoFlush() {
        val activeScope = scope ?: return
        if (!autoFlushScheduled.compareAndSet(expect = false, update = true)) {
            return
        }
        activeScope.launch {
            try {
                flush()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Retryable events remain persisted for future flush attempts.
            } finally {
                autoFlushScheduled.value = false
            }
        }
    }

    private fun removeEntries(processed: List<QueuedEntry>) {
        val processedIds = processed.map { it.sequenceId }.toHashSet()
        queueState.update { current ->
            current.copy(entries = current.entries.filterNot { it.sequenceId in processedIds })
        }
        syncSnapshotAndStorage()
    }

    private fun syncSnapshotAndStorage() {
        val events = queueState.value.entries.map { it.event }
        _queuedEvents.value = events
        if (events.isEmpty()) {
            backingStore.remove(storageKey)
        } else {
            backingStore.putString(storageKey, json.encodeToString(events))
        }
    }

    private fun loadPersisted(): List<AnalyticsEvent> {
        val raw = backingStore.getString(storageKey) ?: return emptyList()
        return runCatching { json.decodeFromString<List<AnalyticsEvent>>(raw) }.getOrElse { emptyList() }
    }

    companion object {
        const val DEFAULT_BATCH_SIZE: Int = 10
        const val DEFAULT_FLUSH_INTERVAL_MILLIS: Long = 5_000L
        const val DEFAULT_MAX_QUEUE_SIZE: Int = 1_000
        const val DEFAULT_STORAGE_KEY: String = "abeta_analytics_offline_queue"
    }
}

private fun isNonRetryableBatchError(error: AppError): Boolean =
    when (error) {
        is ValidationError -> {
            true
        }

        is NetworkError -> {
            val status = error.statusCode
            status != null &&
                status in HTTP_CLIENT_ERROR_START..HTTP_CLIENT_ERROR_END &&
                status != HTTP_REQUEST_TIMEOUT &&
                status != HTTP_TOO_MANY_REQUESTS
        }

        else -> {
            false
        }
    }

private val DefaultAnalyticsJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
