package dev.abeta.kmp.layout.analytics

import dev.abeta.kmp.analytics.AnalyticsBatchSink
import dev.abeta.kmp.analytics.AnalyticsCategory
import dev.abeta.kmp.analytics.AnalyticsEvent
import dev.abeta.kmp.analytics.AnalyticsTracker
import dev.abeta.kmp.analytics.AnalyticsTrackingPayload
import dev.abeta.kmp.analytics.BufferedAnalyticsTracker
import dev.abeta.kmp.contracts.AnalyticsBatchRequest
import dev.abeta.kmp.contracts.AnalyticsEventContract
import dev.abeta.kmp.contracts.AnalyticsIngestResponse
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.core.error.NetworkError
import dev.abeta.kmp.core.identity.RandomUuidGenerator
import dev.abeta.kmp.core.identity.UuidGenerator
import dev.abeta.kmp.core.logging.AppLogger
import dev.abeta.kmp.core.logging.NoOpAppLogger
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.storage.KeyValueStore
import dev.abeta.kmp.core.time.Clock
import dev.abeta.kmp.core.time.SystemClock
import dev.abeta.kmp.layout.repository.BffEnvelope
import dev.abeta.kmp.layout.repository.mapBffError
import dev.abeta.kmp.network.client.HttpMethodType
import dev.abeta.kmp.network.client.HttpRequestBody
import dev.abeta.kmp.network.client.HttpTransport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Remote BFF-backed [AnalyticsTracker] wrapping Layer 0 [BufferedAnalyticsTracker] and Layer 2
 * [HttpTransport].
 *
 * Enqueues events durably into [KeyValueStore], converts [AnalyticsEvent] and
 * [AnalyticsTrackingPayload] records into [AnalyticsEventContract] / [AnalyticsBatchRequest] wire
 * DTOs, and flushes batches to `POST <endpointPath>` (default `"/api/v1/analytics/events"`).
 * Transport and server errors are logged and retained for retry without crashing UI callers.
 */
@Suppress("LongParameterList", "TooManyFunctions")
class RemoteBffAnalyticsTracker(
    private val httpTransport: HttpTransport,
    backingStore: KeyValueStore,
    private val endpointPath: String = DEFAULT_ANALYTICS_ENDPOINT,
    private val baseUrl: String? = null,
    private val sessionId: String? = null,
    private val platform: String? = null,
    private val appVersion: String? = null,
    private val clock: Clock = SystemClock(),
    private val uuidGenerator: UuidGenerator = RandomUuidGenerator(),
    scope: CoroutineScope? = null,
    batchSize: Int = BufferedAnalyticsTracker.DEFAULT_BATCH_SIZE,
    flushIntervalMillis: Long = BufferedAnalyticsTracker.DEFAULT_FLUSH_INTERVAL_MILLIS,
    maxQueueSize: Int = BufferedAnalyticsTracker.DEFAULT_MAX_QUEUE_SIZE,
    storageKey: String = DEFAULT_STORAGE_KEY,
    private val logger: AppLogger = NoOpAppLogger(),
    private val json: Json = SduiJson,
) : AnalyticsTracker {
    private var activeUserId: String? = null

    private val buffered =
        BufferedAnalyticsTracker(
            sink = AnalyticsBatchSink(::deliverBatch),
            backingStore = backingStore,
            clock = clock,
            scope = scope,
            batchSize = batchSize,
            flushIntervalMillis = flushIntervalMillis,
            maxQueueSize = maxQueueSize,
            storageKey = storageKey,
            json = json,
        )

    /** Observable snapshot of buffered analytics events awaiting remote BFF delivery. */
    val queuedEvents: StateFlow<List<AnalyticsEvent>>
        get() = buffered.queuedEvents

    /** Starts background periodic batch flushing when a [CoroutineScope] was provided. */
    fun startPeriodicFlush() = buffered.startPeriodicFlush()

    /** Stops background periodic batch flushing without discarding queued events. */
    fun stopPeriodicFlush() = buffered.stopPeriodicFlush()

    /** Flushes buffered events to the BFF ingestion endpoint, returning the delivered count. */
    suspend fun flush(): AppResult<Int> = buffered.flush()

    /** Discards all queued events from memory and persistent storage. */
    fun clearQueue() = buffered.clearQueue()

    override fun track(event: AnalyticsEvent) {
        val enriched =
            event.copy(
                eventId = event.eventId ?: uuidGenerator.nextId(),
                sessionId = event.sessionId ?: sessionId,
                userId = event.userId ?: activeUserId,
                timestampEpochMillis = event.timestampEpochMillis ?: clock.nowEpochMillis(),
            )
        buffered.track(enriched)
    }

    override fun trackImpression(payload: AnalyticsTrackingPayload) {
        val pageId = payload.properties["page_id"] ?: payload.properties["screen_name"]
        track(
            AnalyticsEvent(
                name = payload.eventName,
                properties = payload.properties,
                timestampEpochMillis = clock.nowEpochMillis(),
                category = AnalyticsCategory.IMPRESSION,
                eventId = uuidGenerator.nextId(),
                sessionId = sessionId,
                userId = activeUserId,
                screenName = pageId,
            ),
        )
    }

    override fun identify(
        userId: String,
        traits: Map<String, String>,
    ) {
        activeUserId = userId
        super.identify(userId = userId, traits = traits)
    }

    override fun reset() {
        activeUserId = null
    }

    /** Converts a domain [AnalyticsEvent] into a wire [AnalyticsEventContract]. */
    fun toEventContract(event: AnalyticsEvent): AnalyticsEventContract =
        AnalyticsEventContract(
            eventId = event.eventId ?: uuidGenerator.nextId(),
            eventName = event.name,
            category = mapCategory(event.category),
            pageId = event.screenName ?: event.properties["page_id"] ?: event.properties["screen_name"],
            snippetId = event.properties["snippet_id"],
            componentId = event.properties["component_id"] ?: event.properties["node_id"],
            actionId = event.properties["action_id"],
            actionType = event.properties["action_type"],
            timestampEpochMs = event.timestampEpochMillis ?: clock.nowEpochMillis(),
            properties = event.properties,
        )

    /** Builds a wire [AnalyticsBatchRequest] from a list of buffered [AnalyticsEvent]s. */
    fun buildBatchRequest(events: List<AnalyticsEvent>): AnalyticsBatchRequest =
        AnalyticsBatchRequest(
            sessionId = sessionId ?: events.asReversed().firstNotNullOfOrNull { it.sessionId },
            userId = activeUserId ?: events.asReversed().firstNotNullOfOrNull { it.userId },
            platform = platform,
            appVersion = appVersion,
            events = events.map(::toEventContract),
        )

    @Suppress("TooGenericExceptionCaught")
    private suspend fun deliverBatch(events: List<AnalyticsEvent>): AppResult<Unit> {
        val batchRequest = buildBatchRequest(events)
        val targetPath = resolveTargetPath()
        return try {
            val result =
                httpTransport.request(
                    method = HttpMethodType.POST,
                    path = targetPath,
                    deserializer = JsonElement.serializer(),
                    body =
                        HttpRequestBody.Serialized(
                            value = batchRequest,
                            serializer = AnalyticsBatchRequest.serializer(),
                        ),
                )
            handleDeliveryResult(result, batchRequest.events.size)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            logger.warn(TAG, "Unexpected analytics flush failure for '$targetPath': ${throwable.message}")
            AppResult.Failure(
                NetworkError(
                    statusCode = null,
                    message = throwable.message ?: "Analytics flush failed for $targetPath",
                    cause = throwable,
                ),
            )
        }
    }

    private fun handleDeliveryResult(
        result: AppResult<JsonElement>,
        batchCount: Int,
    ): AppResult<Unit> =
        when (result) {
            is AppResult.Failure -> {
                logger.warn(TAG, "Failed to flush $batchCount analytics event(s): ${result.error.message}")
                result
            }

            is AppResult.Success -> {
                validateResponsePayload(result.value, batchCount)
            }
        }

    private fun validateResponsePayload(
        element: JsonElement,
        batchCount: Int,
    ): AppResult<Unit> {
        val envelope = decodeEnvelopeOrNull(element)
        return when {
            envelope != null && !envelope.success -> {
                val mappedError =
                    envelope.error?.let { mapBffError(it, resource = endpointPath) }
                        ?: NetworkError(statusCode = null, message = "Analytics ingest rejected by BFF")
                logger.warn(TAG, "BFF rejected analytics batch ($batchCount): ${mappedError.message}")
                AppResult.Failure(mappedError)
            }

            else -> {
                val accepted = envelope?.data?.accepted ?: decodeIngestOrNull(element)?.accepted ?: batchCount
                logger.debug(TAG, "Flushed $accepted analytics event(s) to $endpointPath")
                AppResult.Success(Unit)
            }
        }
    }

    private fun decodeEnvelopeOrNull(element: JsonElement): BffEnvelope<AnalyticsIngestResponse>? {
        val obj = element as? JsonObject
        return if (obj != null && "success" in obj) {
            runCatching {
                json.decodeFromJsonElement(
                    BffEnvelope.serializer(AnalyticsIngestResponse.serializer()),
                    element,
                )
            }.getOrNull()
        } else {
            null
        }
    }

    private fun decodeIngestOrNull(element: JsonElement): AnalyticsIngestResponse? =
        runCatching {
            json.decodeFromJsonElement(AnalyticsIngestResponse.serializer(), element)
        }.getOrNull()

    private fun resolveTargetPath(): String {
        val trimmedBase = baseUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
        val normalizedEndpoint = "/" + endpointPath.trim().trimStart('/')
        return if (trimmedBase != null) "$trimmedBase$normalizedEndpoint" else normalizedEndpoint
    }

    private fun mapCategory(category: AnalyticsCategory): String =
        when (category) {
            AnalyticsCategory.SCREEN -> CATEGORY_PAGE_OPEN
            AnalyticsCategory.IMPRESSION -> CATEGORY_IMPRESSION
            AnalyticsCategory.ACTION -> CATEGORY_INTERACTION
            AnalyticsCategory.LIFECYCLE -> CATEGORY_LIFECYCLE
            AnalyticsCategory.SYSTEM, AnalyticsCategory.ERROR -> CATEGORY_CUSTOM
        }

    companion object {
        const val DEFAULT_ANALYTICS_ENDPOINT: String = "/api/v1/analytics/events"
        const val DEFAULT_STORAGE_KEY: String = "abeta_bff_analytics_queue"

        private const val TAG = "RemoteBffAnalytics"
        private const val CATEGORY_PAGE_OPEN = "page_open"
        private const val CATEGORY_IMPRESSION = "impression"
        private const val CATEGORY_INTERACTION = "interaction"
        private const val CATEGORY_LIFECYCLE = "lifecycle"
        private const val CATEGORY_CUSTOM = "custom"
    }
}
