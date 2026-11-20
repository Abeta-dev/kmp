package dev.abeta.kmp.analytics

import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.storage.KeyValueStore
import dev.abeta.kmp.core.time.Clock
import dev.abeta.kmp.core.time.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

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
    private val mutex = Mutex()
    private val _queuedEvents = MutableStateFlow(loadPersisted())
    private var periodicJob: Job? = null

    /** Observable snapshot of buffered events waiting to be delivered. */
    val queuedEvents: StateFlow<List<AnalyticsEvent>> = _queuedEvents.asStateFlow()

    /** Starts background periodic flushing when a [CoroutineScope] was provided. */
    fun startPeriodicFlush() {
        val activeScope = scope ?: return
        if (periodicJob?.isActive == true || flushIntervalMillis <= 0L) return
        periodicJob =
            activeScope.launch {
                while (isActive) {
                    delay(flushIntervalMillis)
                    flush()
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
     * Flushes buffered events in chunks up to [batchSize], halting and retaining remaining batches if
     * the downstream [AnalyticsBatchSink] fails. Returns the count of delivered events.
     */
    suspend fun flush(): AppResult<Int> =
        mutex.withLock {
            val current = _queuedEvents.value.toMutableList()
            if (current.isEmpty()) {
                return@withLock AppResult.Success(0)
            }

            var delivered = 0
            val chunkSize = batchSize.coerceAtLeast(1)
            while (current.isNotEmpty()) {
                val batch = current.take(chunkSize)
                when (val outcome = sink.sendBatch(batch)) {
                    is AppResult.Success -> {
                        delivered += batch.size
                        repeat(batch.size) { current.removeAt(0) }
                        persist(current)
                    }

                    is AppResult.Failure -> {
                        persist(current)
                        return@withLock AppResult.Failure(outcome.error)
                    }
                }
            }
            AppResult.Success(delivered)
        }

    /** Discards all queued events from memory and persistent storage. */
    fun clearQueue() {
        persist(emptyList())
    }

    private fun enqueueInternal(event: AnalyticsEvent) {
        val bounded = (_queuedEvents.value + event).takeLast(maxQueueSize.coerceAtLeast(1))
        persist(bounded)
        if (bounded.size >= batchSize) {
            scope?.launch { flush() }
        }
    }

    private fun persist(events: List<AnalyticsEvent>) {
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

private val DefaultAnalyticsJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
