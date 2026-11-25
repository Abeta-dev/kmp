@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.watch

import dev.abeta.kmp.actions.handler.ActionOutcome
import dev.abeta.kmp.actions.handler.ApiCallExecutor
import dev.abeta.kmp.actions.model.ActionContext
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.actions.model.CallApiPayload
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.AdaptivePageSpec
import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.CardSnippetData
import dev.abeta.kmp.contracts.MetricCardSnippetData
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.ProgressAtom
import dev.abeta.kmp.contracts.ProgressKind
import dev.abeta.kmp.contracts.ScreenLayoutMode
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.core.error.AppError
import dev.abeta.kmp.core.error.ConflictError
import dev.abeta.kmp.core.error.ForbiddenError
import dev.abeta.kmp.core.error.NetworkError
import dev.abeta.kmp.core.error.NotFoundError
import dev.abeta.kmp.core.error.OfflineError
import dev.abeta.kmp.core.error.RateLimitError
import dev.abeta.kmp.core.error.SerializationError
import dev.abeta.kmp.core.error.TimeoutError
import dev.abeta.kmp.core.error.UnauthorizedError
import dev.abeta.kmp.core.error.ValidationError
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.storage.KeyValueStore
import dev.abeta.kmp.network.client.ConditionalHttpResult
import dev.abeta.kmp.network.client.HttpMethodType
import dev.abeta.kmp.network.client.HttpRequestBody
import dev.abeta.kmp.network.client.HttpTransport
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val DEFAULT_SCREENS_PATH = "/api/v1/screens"
private const val WATCH_CACHE_KEY_PREFIX = "watch_screen_cache:"
private const val HTTP_OK = 200
private const val HTTP_SERVER_ERROR_MIN = 500
private const val STARTER_MAX_WIDTH_DP = 280
private const val STARTER_PROGRESS = 0.78f

/**
 * Port retrieving SDUI [PageResponse] documents for wearable companion clients without Compose UI coupling.
 */
fun interface WatchPageSource {
    /** Fetches the SDUI [PageResponse] identified by [screenId]. */
    suspend fun fetchPage(screenId: String): AppResult<PageResponse>
}

@Serializable
internal data class WatchBffErrorDetail(
    val code: String? = null,
    val message: String,
    val detail: String? = null,
)

@Serializable
internal data class WatchBffEnvelope<T>(
    val success: Boolean = false,
    val data: T? = null,
    val error: WatchBffErrorDetail? = null,
)

@Serializable
internal data class WatchActionExecResultData(
    @SerialName("follow_up_actions")
    val followUpActions: List<ActionRef> = emptyList(),
)

@Serializable
internal data class CachedWatchScreenRecord(
    val page: PageResponse,
    val etag: String? = null,
)

/**
 * Remote [WatchPageSource] fetching Go BFF screens over [HttpTransport] (HTTP/2 + Brotli + Darwin TLS pinning)
 * with conditional `If-None-Match` / `304 Not Modified` ETag validation and optional offline caching.
 */
class RemoteWatchPageSource(
    private val httpTransport: HttpTransport,
    private val basePath: String = DEFAULT_SCREENS_PATH,
    private val cacheStore: KeyValueStore? = null,
) : WatchPageSource {
    override suspend fun fetchPage(screenId: String): AppResult<PageResponse> {
        val cleanId = screenId.trim().removePrefix("/")
        if (cleanId.isEmpty()) {
            return AppResult.Failure(
                ValidationError(field = "screenId", message = "Watch screen ID must not be blank."),
            )
        }
        val normalizedBase = basePath.trimEnd('/')
        val cached = readCachedRecord(cleanId)
        val response =
            httpTransport.requestConditional(
                path = "$normalizedBase/$cleanId",
                deserializer = WatchBffEnvelope.serializer(PageResponse.serializer()),
                ifNoneMatch = cached?.etag,
            )
        return when (response) {
            is AppResult.Failure -> {
                if (cached != null && isOfflineFallbackError(response.error)) {
                    AppResult.Success(cached.page)
                } else {
                    response
                }
            }

            is AppResult.Success -> {
                handleConditionalResponse(cleanId = cleanId, result = response.value, cached = cached)
            }
        }
    }

    private fun handleConditionalResponse(
        cleanId: String,
        result: ConditionalHttpResult<WatchBffEnvelope<PageResponse>>,
        cached: CachedWatchScreenRecord?,
    ): AppResult<PageResponse> =
        when (result) {
            is ConditionalHttpResult.NotModified -> {
                if (cached != null) {
                    val refreshedEtag = result.etag?.takeIf { it.isNotBlank() } ?: cached.etag
                    if (refreshedEtag != cached.etag) {
                        writeCachedRecord(cleanId, cached.copy(etag = refreshedEtag))
                    }
                    AppResult.Success(cached.page)
                } else {
                    AppResult.Failure(
                        SerializationError(
                            message = "Unexpected 304 Not Modified without cached watch screen for '$cleanId'.",
                        ),
                    )
                }
            }

            is ConditionalHttpResult.Modified -> {
                val httpEtag = result.envelope.etag?.takeIf { it.isNotBlank() }
                when (val unwrapped = unwrapEnvelope(result.envelope.body, cleanId)) {
                    is AppResult.Failure -> {
                        unwrapped
                    }

                    is AppResult.Success -> {
                        val page = unwrapped.value
                        val resolvedPage =
                            if (page.etag.isNullOrBlank() && httpEtag != null) {
                                page.copy(etag = httpEtag)
                            } else {
                                page
                            }
                        val resolvedEtag = resolvedPage.etag?.takeIf { it.isNotBlank() } ?: httpEtag
                        writeCachedRecord(cleanId, CachedWatchScreenRecord(page = resolvedPage, etag = resolvedEtag))
                        AppResult.Success(resolvedPage)
                    }
                }
            }
        }

    private fun unwrapEnvelope(
        envelope: WatchBffEnvelope<PageResponse>,
        screenId: String,
    ): AppResult<PageResponse> =
        when {
            envelope.success && envelope.data != null -> {
                AppResult.Success(envelope.data)
            }

            !envelope.success && envelope.error != null -> {
                AppResult.Failure(mapWatchBffError(envelope.error, resource = screenId))
            }

            else -> {
                AppResult.Failure(SerializationError(message = "Empty watch screen payload from BFF."))
            }
        }

    private fun readCachedRecord(screenId: String): CachedWatchScreenRecord? {
        val raw = cacheStore?.getString(cacheKey(screenId)) ?: return null
        return runCatching {
            SduiJson.decodeFromString(CachedWatchScreenRecord.serializer(), raw)
        }.getOrNull()
    }

    private fun writeCachedRecord(
        screenId: String,
        record: CachedWatchScreenRecord,
    ) {
        val store = cacheStore ?: return
        runCatching {
            store.putString(
                cacheKey(screenId),
                SduiJson.encodeToString(CachedWatchScreenRecord.serializer(), record),
            )
        }
    }

    private fun cacheKey(screenId: String): String = "$WATCH_CACHE_KEY_PREFIX$screenId"

    private fun isOfflineFallbackError(error: AppError): Boolean =
        when (error) {
            is OfflineError, is TimeoutError -> {
                true
            }

            is NetworkError -> {
                val status = error.statusCode
                status == null || status >= HTTP_SERVER_ERROR_MIN
            }

            else -> {
                false
            }
        }
}

/**
 * Remote [ApiCallExecutor] executing 1-tap wearable actions (e.g. express reorder) against the Go BFF.
 */
class WatchRemoteApiCallExecutor(
    private val httpTransport: HttpTransport,
    private val defaultActionEndpoint: String = "/api/v1/actions/execute",
) : ApiCallExecutor {
    override suspend fun execute(
        payload: CallApiPayload,
        context: ActionContext,
    ): AppResult<ActionOutcome.ApiCallResult> {
        val method = parseHttpVerb(payload.method)
        val path = payload.endpoint.ifBlank { defaultActionEndpoint }
        val payloadBody = payload.body
        val requestBody =
            if (method != HttpMethodType.GET && payloadBody != null) {
                HttpRequestBody.JsonValue(payloadBody)
            } else {
                null
            }
        val result =
            httpTransport.request(
                method = method,
                path = path,
                deserializer = JsonElement.serializer(),
                headers = payload.headers,
                body = requestBody,
            )
        return when (result) {
            is AppResult.Failure -> {
                AppResult.Failure(result.error)
            }

            is AppResult.Success -> {
                mapResponseElement(path = path, method = method.name, element = result.value)
            }
        }
    }

    private fun mapResponseElement(
        path: String,
        method: String,
        element: JsonElement,
    ): AppResult<ActionOutcome.ApiCallResult> {
        val envelope = decodeEnvelopeOrNull(element)
        return when {
            envelope == null -> {
                AppResult.Success(
                    ActionOutcome.ApiCallResult(
                        endpoint = path,
                        method = method,
                        isSuccess = true,
                        statusCode = HTTP_OK,
                        responseBody = element,
                    ),
                )
            }

            !envelope.success -> {
                val error =
                    envelope.error?.let { mapWatchBffError(it, resource = path) }
                        ?: NetworkError(
                            statusCode = HTTP_SERVER_ERROR_MIN,
                            message = "Watch BFF rejected API call.",
                        )
                AppResult.Failure(error)
            }

            else -> {
                val followUps = extractFollowUpActions(envelope.data)
                AppResult.Success(
                    ActionOutcome.ApiCallResult(
                        endpoint = path,
                        method = method,
                        isSuccess = true,
                        statusCode = HTTP_OK,
                        responseBody = envelope.data,
                        followUpActions = followUps,
                    ),
                )
            }
        }
    }

    private fun decodeEnvelopeOrNull(element: JsonElement): WatchBffEnvelope<JsonElement>? {
        val obj = element as? JsonObject
        return if (obj != null && "success" in obj) {
            runCatching {
                SduiJson.decodeFromJsonElement(WatchBffEnvelope.serializer(JsonElement.serializer()), obj)
            }.getOrNull()
        } else {
            null
        }
    }

    private fun extractFollowUpActions(data: JsonElement?): List<ActionRef> {
        val obj = data as? JsonObject
        return if (obj != null && "follow_up_actions" in obj) {
            runCatching {
                SduiJson.decodeFromJsonElement(WatchActionExecResultData.serializer(), obj).followUpActions
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
    }

    private fun parseHttpVerb(method: String): HttpMethodType =
        when (method.trim().uppercase()) {
            "GET" -> HttpMethodType.GET
            "PUT" -> HttpMethodType.PUT
            "PATCH" -> HttpMethodType.PATCH
            "DELETE" -> HttpMethodType.DELETE
            else -> HttpMethodType.POST
        }
}

internal fun mapWatchBffError(
    error: WatchBffErrorDetail,
    resource: String? = null,
): AppError {
    val code = error.code?.trim().orEmpty()
    return when (code.uppercase()) {
        "NOT_FOUND" -> {
            NotFoundError(
                resource = resource,
                message = error.message,
                code = code,
            )
        }

        "UNAUTHORIZED" -> {
            UnauthorizedError(
                message = error.message,
                code = code,
            )
        }

        "FORBIDDEN" -> {
            ForbiddenError(
                resource = resource,
                message = error.message,
                code = code,
            )
        }

        "BAD_REQUEST", "VALIDATION_ERROR", "UNPROCESSABLE" -> {
            ValidationError(
                field = error.detail,
                message = error.message,
                code = code,
            )
        }

        "CONFLICT" -> {
            ConflictError(
                resource = resource,
                message = error.message,
                code = code,
            )
        }

        "RATE_LIMITED", "TOO_MANY_REQUESTS" -> {
            RateLimitError(
                message = error.message,
                code = code,
            )
        }

        else -> {
            NetworkError(
                statusCode = null,
                message = error.message,
                code = code.ifEmpty { NetworkError.DEFAULT_CODE },
            )
        }
    }
}

/**
 * Built-in offline fallback [WatchPageSource] and [ApiCallExecutor] allowing standalone watchOS / Wear OS
 * simulator previews before connecting to a live Go BFF.
 */
object WatchOfflineStarterDefaults {
    /** Creates an offline fallback [WatchPageSource] serving compact status metrics & 1-tap reorder card. */
    fun createStarterPageSource(): WatchPageSource =
        WatchPageSource { screenId ->
            val cleanId = screenId.ifBlank { "watch-status" }
            AppResult.Success(
                PageResponse(
                    pageId = cleanId,
                    title = "Abeta Watch",
                    adaptiveLayout =
                        AdaptivePageSpec(
                            layoutMode = ScreenLayoutMode.SINGLE_COLUMN,
                            maxWidthDp = STARTER_MAX_WIDTH_DP,
                        ),
                    snippets =
                        listOf(
                            buildStarterEtaMetricEnvelope(),
                            buildStarterActiveOrderEnvelope(),
                        ),
                ),
            )
        }

    private fun buildStarterEtaMetricEnvelope(): SnippetEnvelope {
        val etaMetricData =
            SduiJson.encodeToJsonElement(
                MetricCardSnippetData.serializer(),
                MetricCardSnippetData(
                    label = TextAtom(text = "Express SLA"),
                    value = TextAtom(text = "8 mins"),
                    progress =
                        ProgressAtom(
                            kind = ProgressKind.LINEAR,
                            progress = STARTER_PROGRESS,
                            label = "Arriving in 8 mins",
                        ),
                ),
            ) as JsonObject
        return SnippetEnvelope(
            snippetId = "watch_eta_metric",
            snippetType = SnippetTypes.METRIC_CARD,
            data = etaMetricData,
        )
    }

    private fun buildStarterActiveOrderEnvelope(): SnippetEnvelope {
        val reorderAction =
            ActionRef(
                id = "watch_reorder",
                type = ActionTypes.CALL_API,
                payload =
                    buildJsonObject {
                        put("endpoint", "/api/v1/orders/express")
                        put("method", "POST")
                    },
                secondaryActions =
                    listOf(
                        ActionRef(
                            id = "watch_haptic",
                            type = ActionTypes.TRIGGER_FEEDBACK,
                            payload = buildJsonObject { put("effect", "haptic_tick") },
                        ),
                    ),
            )
        val activeOrderData =
            SduiJson.encodeToJsonElement(
                CardSnippetData.serializer(),
                CardSnippetData(
                    title = TextAtom(text = "Rider En Route · 8 min"),
                    subtitle = TextAtom(text = "Abeta Fresh · Bag #AF-409"),
                    badge = TagAtom(label = "LIVE"),
                    primaryButton =
                        ButtonAtom(
                            label = "1-Tap Reorder",
                            onClick = reorderAction,
                        ),
                ),
            ) as JsonObject
        return SnippetEnvelope(
            snippetId = "watch_active_order",
            snippetType = SnippetTypes.CARD,
            data = activeOrderData,
        )
    }
}
