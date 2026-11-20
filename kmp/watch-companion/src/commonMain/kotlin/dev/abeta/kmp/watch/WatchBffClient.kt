@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.watch

import dev.abeta.kmp.actions.handler.ActionOutcome
import dev.abeta.kmp.actions.handler.ApiCallExecutor
import dev.abeta.kmp.actions.model.ActionContext
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.actions.model.CallApiPayload
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.core.error.NetworkError
import dev.abeta.kmp.core.error.SerializationError
import dev.abeta.kmp.core.error.ValidationError
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.network.client.HttpMethodType
import dev.abeta.kmp.network.client.HttpRequestBody
import dev.abeta.kmp.network.client.HttpTransport
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

private const val DEFAULT_SCREENS_PATH = "/api/v1/screens"
private const val HTTP_OK = 200
private const val HTTP_UNKNOWN_ERROR = 500

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

/**
 * Remote [WatchPageSource] fetching Go BFF screens over [HttpTransport] (HTTP/2 + Brotli + Darwin TLS pinning).
 */
class RemoteWatchPageSource(
    private val httpTransport: HttpTransport,
    private val basePath: String = DEFAULT_SCREENS_PATH,
) : WatchPageSource {
    override suspend fun fetchPage(screenId: String): AppResult<PageResponse> {
        val cleanId = screenId.trim().removePrefix("/")
        val normalizedBase = basePath.trimEnd('/')
        val response =
            if (cleanId.isEmpty()) {
                AppResult.Failure(ValidationError(field = "screenId", message = "Watch screen ID must not be blank."))
            } else {
                httpTransport.request(
                    method = HttpMethodType.GET,
                    path = "$normalizedBase/$cleanId",
                    deserializer = WatchBffEnvelope.serializer(PageResponse.serializer()),
                )
            }
        return when (response) {
            is AppResult.Failure -> response
            is AppResult.Success -> unwrapEnvelope(response.value)
        }
    }

    private fun unwrapEnvelope(envelope: WatchBffEnvelope<PageResponse>): AppResult<PageResponse> =
        if (envelope.success && envelope.data != null) {
            AppResult.Success(envelope.data)
        } else {
            AppResult.Failure(
                SerializationError(message = envelope.error?.message ?: "Empty watch screen payload from BFF."),
            )
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
        val result =
            httpTransport.request(
                method = method,
                path = path,
                deserializer = WatchBffEnvelope.serializer(JsonObject.serializer()),
                headers = payload.headers,
                body = payload.body?.let(HttpRequestBody::JsonValue),
            )
        return when (result) {
            is AppResult.Failure -> {
                AppResult.Failure(result.error)
            }

            is AppResult.Success -> {
                mapSuccessEnvelope(path = path, method = method.name, envelope = result.value)
            }
        }
    }

    private fun mapSuccessEnvelope(
        path: String,
        method: String,
        envelope: WatchBffEnvelope<JsonObject>,
    ): AppResult<ActionOutcome.ApiCallResult> =
        if (!envelope.success) {
            AppResult.Failure(
                NetworkError(
                    statusCode = HTTP_UNKNOWN_ERROR,
                    message = envelope.error?.message ?: "Watch BFF rejected API call.",
                ),
            )
        } else {
            val followUps = extractFollowUpActions(envelope.data)
            AppResult.Success(
                ActionOutcome.ApiCallResult(
                    endpoint = path,
                    method = method,
                    isSuccess = true,
                    statusCode = HTTP_OK,
                    responseBody = envelope.data as? JsonElement,
                    followUpActions = followUps,
                ),
            )
        }

    private fun extractFollowUpActions(data: JsonObject?): List<ActionRef> =
        data?.let {
            runCatching { SduiJson.decodeFromJsonElement(WatchActionExecResultData.serializer(), it) }
                .getOrNull()
                ?.followUpActions
                .orEmpty()
        } ?: emptyList()

    private fun parseHttpVerb(method: String): HttpMethodType =
        when (method.trim().uppercase()) {
            "GET" -> HttpMethodType.GET
            "PUT" -> HttpMethodType.PUT
            "PATCH" -> HttpMethodType.PATCH
            "DELETE" -> HttpMethodType.DELETE
            else -> HttpMethodType.POST
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
            val jsonPayload =
                """
                {
                  "page_id": "$cleanId",
                  "title": "Abeta Watch",
                  "adaptive_layout": { "layout_mode": "single_column", "max_width_dp": 280 },
                  "snippets": [
                    {
                      "snippet_id": "watch_eta_metric",
                      "snippet_type": "metric_card",
                      "data": {
                        "label": { "text": "Express SLA" },
                        "value": { "text": "8 mins" },
                        "progress": { "kind": "linear", "progress": 0.78, "label": "Arriving in 8 mins" }
                      }
                    },
                    {
                      "snippet_id": "watch_active_order",
                      "snippet_type": "card_v1",
                      "data": {
                        "title": { "text": "Rider En Route · 8 min" },
                        "subtitle": { "text": "Abeta Fresh · Bag #AF-409" },
                        "badge": { "label": "LIVE" },
                        "primary_button": {
                          "label": "1-Tap Reorder",
                          "on_click": {
                            "id": "watch_reorder",
                            "type": "${ActionTypes.CALL_API}",
                            "payload": { "endpoint": "/api/v1/orders/express", "method": "POST" },
                            "secondary_actions": [
                              {
                                "id": "watch_haptic",
                                "type": "${ActionTypes.TRIGGER_FEEDBACK}",
                                "payload": { "effect": "haptic_tick" }
                              }
                            ]
                          }
                        }
                      }
                    }
                  ]
                }
                """.trimIndent()
            AppResult.Success(SduiJson.decodeFromString(PageResponse.serializer(), jsonPayload))
        }
}
