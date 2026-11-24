package dev.abeta.kmp.layout.repository

import dev.abeta.kmp.actions.handler.ActionOutcome
import dev.abeta.kmp.actions.handler.ApiCallExecutor
import dev.abeta.kmp.actions.model.ActionContext
import dev.abeta.kmp.actions.model.CallApiPayload
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.core.error.AppError
import dev.abeta.kmp.core.error.NetworkError
import dev.abeta.kmp.core.error.NotFoundError
import dev.abeta.kmp.core.error.SerializationError
import dev.abeta.kmp.core.error.UnauthorizedError
import dev.abeta.kmp.core.error.ValidationError
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.network.client.ConditionalHttpResult
import dev.abeta.kmp.network.client.HttpMethodType
import dev.abeta.kmp.network.client.HttpRequestBody
import dev.abeta.kmp.network.client.HttpTransport
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * Repository port for retrieving SDUI [PageResponse] definitions by screen identifier.
 */
fun interface ScreenRepository {
    /** Fetches the SDUI [PageResponse] for [screenId]. */
    suspend fun fetchScreen(screenId: String): AppResult<PageResponse>
}

/**
 * Outcome of a conditional SDUI screen fetch validated via `If-None-Match` / `ETag`.
 */
sealed interface ConditionalScreenFetchResult {
    /** Screen changed or no validator matched; [page] holds the updated payload. */
    data class Modified(
        val page: PageResponse,
        val etag: String? = null,
    ) : ConditionalScreenFetchResult

    /** Screen matched the client validator (`HTTP 304 Not Modified`). */
    data class NotModified(
        val etag: String? = null,
    ) : ConditionalScreenFetchResult
}

/**
 * [ScreenRepository] extension supporting conditional `If-None-Match` / `304 Not Modified` fetches.
 */
interface ConditionalScreenRepository : ScreenRepository {
    /**
     * Fetches the SDUI screen for [screenId], passing [ifNoneMatch] as an `If-None-Match` validator
     * when non-blank.
     */
    suspend fun fetchScreenConditional(
        screenId: String,
        ifNoneMatch: String? = null,
    ): AppResult<ConditionalScreenFetchResult>
}

/**
 * Remote [ConditionalScreenRepository] fetching `ginmw.Envelope<PageResponse>` payloads via [HttpTransport].
 */
class RemoteScreenRepository(
    private val httpClient: HttpTransport,
    private val basePath: String = DEFAULT_BASE_PATH,
) : ConditionalScreenRepository {
    override suspend fun fetchScreen(screenId: String): AppResult<PageResponse> =
        when (val result = fetchScreenConditional(screenId = screenId, ifNoneMatch = null)) {
            is AppResult.Failure -> {
                result
            }

            is AppResult.Success -> {
                when (val conditional = result.value) {
                    is ConditionalScreenFetchResult.Modified -> {
                        AppResult.Success(conditional.page)
                    }

                    is ConditionalScreenFetchResult.NotModified -> {
                        AppResult.Failure(
                            SerializationError(
                                message = "Unexpected 304 Not Modified without validator for '$screenId'.",
                            ),
                        )
                    }
                }
            }
        }

    override suspend fun fetchScreenConditional(
        screenId: String,
        ifNoneMatch: String?,
    ): AppResult<ConditionalScreenFetchResult> {
        val cleanId = screenId.trim().removePrefix("/")
        if (cleanId.isEmpty()) {
            return AppResult.Failure(
                ValidationError(
                    field = "screenId",
                    message = "Screen ID must not be blank.",
                ),
            )
        }
        val normalizedBase = basePath.trimEnd('/')
        val path = "$normalizedBase/$cleanId"
        val result =
            httpClient.requestConditional(
                path = path,
                deserializer = BffEnvelope.serializer(PageResponse.serializer()),
                ifNoneMatch = ifNoneMatch,
            )
        return when (result) {
            is AppResult.Failure -> result
            is AppResult.Success -> unwrapConditionalResponse(result.value, cleanId, ifNoneMatch)
        }
    }

    private fun unwrapConditionalResponse(
        response: ConditionalHttpResult<BffEnvelope<PageResponse>>,
        screenId: String,
        ifNoneMatch: String?,
    ): AppResult<ConditionalScreenFetchResult> =
        when (response) {
            is ConditionalHttpResult.NotModified -> {
                AppResult.Success(
                    ConditionalScreenFetchResult.NotModified(
                        etag = response.etag ?: ifNoneMatch?.takeIf { it.isNotBlank() },
                    ),
                )
            }

            is ConditionalHttpResult.Modified -> {
                val httpEtag = response.envelope.etag?.takeIf { it.isNotBlank() }
                when (val unwrapped = unwrapPageEnvelope(response.envelope.body, screenId)) {
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
                        AppResult.Success(
                            ConditionalScreenFetchResult.Modified(
                                page = resolvedPage,
                                etag = resolvedEtag,
                            ),
                        )
                    }
                }
            }
        }

    private fun unwrapPageEnvelope(
        envelope: BffEnvelope<PageResponse>,
        screenId: String,
    ): AppResult<PageResponse> =
        when {
            envelope.success && envelope.data != null -> {
                AppResult.Success(envelope.data)
            }

            !envelope.success && envelope.error != null -> {
                AppResult.Failure(mapBffError(envelope.error, resource = screenId))
            }

            else -> {
                AppResult.Failure(
                    SerializationError(
                        message = "Missing page data in BFF envelope for '$screenId'.",
                    ),
                )
            }
        }

    companion object {
        const val DEFAULT_BASE_PATH: String = "/api/v1/screens"
    }
}

/**
 * [ApiCallExecutor] implementation backed by [HttpTransport] for executing [CallApiPayload]s.
 */
class RemoteApiCallExecutor(
    private val httpClient: HttpTransport,
    private val json: Json = SduiJson,
) : ApiCallExecutor {
    override suspend fun execute(
        payload: CallApiPayload,
        context: ActionContext,
    ): AppResult<ActionOutcome.ApiCallResult> {
        val method =
            parseHttpMethod(payload.method)
                ?: return AppResult.Failure(
                    ValidationError(
                        field = "method",
                        message = "Unsupported HTTP method '${payload.method}'.",
                    ),
                )
        val payloadBody = payload.body
        val requestBody =
            if (method != HttpMethodType.GET && payloadBody != null) {
                HttpRequestBody.JsonValue(payloadBody)
            } else {
                null
            }

        val responseResult =
            httpClient.request(
                method = method,
                path = payload.endpoint,
                deserializer = JsonElement.serializer(),
                headers = payload.headers,
                body = requestBody,
            )

        return when (responseResult) {
            is AppResult.Failure -> responseResult
            is AppResult.Success -> AppResult.Success(buildOutcome(payload, responseResult.value))
        }
    }

    private fun buildOutcome(
        payload: CallApiPayload,
        responseElement: JsonElement,
    ): ActionOutcome.ApiCallResult {
        val envelope = decodeEnvelopeOrNull(responseElement)
        if (envelope != null) {
            val followUps = extractFollowUpActions(envelope.data)
            return ActionOutcome.ApiCallResult(
                endpoint = payload.endpoint,
                method = payload.method,
                isSuccess = envelope.success,
                statusCode = if (envelope.success) HTTP_OK else null,
                responseBody = responseElement,
                errorMessage = envelope.error?.message,
                followUpActions = followUps,
            )
        }
        return ActionOutcome.ApiCallResult(
            endpoint = payload.endpoint,
            method = payload.method,
            isSuccess = true,
            statusCode = HTTP_OK,
            responseBody = responseElement,
        )
    }

    private fun decodeEnvelopeOrNull(element: JsonElement): BffEnvelope<JsonElement>? {
        val obj = element as? JsonObject
        return if (obj != null && "success" in obj) {
            runCatching {
                json.decodeFromJsonElement(BffEnvelope.serializer(JsonElement.serializer()), element)
            }.getOrNull()
        } else {
            null
        }
    }

    private fun extractFollowUpActions(data: JsonElement?): List<ActionRef> {
        val obj = data as? JsonObject
        return if (obj != null && "follow_up_actions" in obj) {
            runCatching {
                json.decodeFromJsonElement(ActionExecutionData.serializer(), obj).followUpActions
            }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
    }

    private fun parseHttpMethod(method: String): HttpMethodType? =
        when (method.trim().uppercase()) {
            "GET" -> HttpMethodType.GET
            "POST" -> HttpMethodType.POST
            "PUT" -> HttpMethodType.PUT
            "PATCH" -> HttpMethodType.PATCH
            "DELETE" -> HttpMethodType.DELETE
            else -> null
        }

    @Serializable
    private data class ActionExecutionData(
        @SerialName("follow_up_actions")
        val followUpActions: List<ActionRef> = emptyList(),
    )

    private companion object {
        const val HTTP_OK = 200
    }
}

internal fun mapBffError(
    error: BffApiError,
    resource: String? = null,
): AppError =
    when (error.code.uppercase()) {
        "NOT_FOUND" -> {
            NotFoundError(
                resource = resource,
                message = error.message,
                code = error.code,
            )
        }

        "UNAUTHORIZED", "FORBIDDEN" -> {
            UnauthorizedError(
                message = error.message,
                code = error.code,
            )
        }

        "BAD_REQUEST", "VALIDATION_ERROR", "UNPROCESSABLE" -> {
            ValidationError(
                field = error.detail,
                message = error.message,
                code = error.code,
            )
        }

        else -> {
            NetworkError(
                statusCode = null,
                message = error.message,
                code = error.code,
            )
        }
    }
