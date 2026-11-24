package dev.abeta.kmp.network.client

import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.result.map
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer

private const val HTTP_OK_STATUS = 200
private const val HEADER_ETAG = "ETag"
private const val HEADER_IF_NONE_MATCH = "If-None-Match"

/**
 * Supported HTTP verbs for [HttpTransport].
 */
enum class HttpMethodType {
    GET,
    POST,
    PUT,
    PATCH,
    DELETE,
}

/**
 * Typed request body representation that avoids leaking transport-layer types in [HttpTransport].
 */
sealed interface HttpRequestBody {
    /** Pre-encoded JSON payload. */
    data class JsonValue(
        val element: JsonElement,
    ) : HttpRequestBody

    /** Typed DTO paired with its [SerializationStrategy]. */
    data class Serialized<T>(
        val value: T,
        val serializer: SerializationStrategy<T>,
    ) : HttpRequestBody
}

/**
 * HTTP response envelope preserving status code, response headers, and decoded [body].
 */
data class HttpResponseEnvelope<T>(
    val statusCode: Int,
    val headers: Map<String, String> = emptyMap(),
    val body: T,
) {
    /** Case-insensitive lookup for a response header by [name]. */
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    /** Value of the `ETag` response header, if present. */
    val etag: String?
        get() = header(HEADER_ETAG)
}

/**
 * Outcome of a conditional HTTP request using `If-None-Match` / `ETag` validation.
 */
sealed interface ConditionalHttpResult<out T> {
    /** Resource changed or no validator matched; [envelope] contains the new representation. */
    data class Modified<T>(
        val envelope: HttpResponseEnvelope<T>,
    ) : ConditionalHttpResult<T>

    /** Resource matched the client validator (`HTTP 304 Not Modified`). */
    data class NotModified(
        val etag: String? = null,
        val headers: Map<String, String> = emptyMap(),
    ) : ConditionalHttpResult<Nothing>
}

/**
 * Dependency-inverted HTTP client port returning typed [AppResult] outcomes without
 * leaking Ktor or platform-specific exceptions to callers.
 */
interface HttpTransport {
    /**
     * Executes an HTTP request and decodes a 2xx response body using [deserializer].
     */
    suspend fun <T> request(
        method: HttpMethodType,
        path: String,
        deserializer: DeserializationStrategy<T>,
        headers: Map<String, String> = emptyMap(),
        queryParams: Map<String, String> = emptyMap(),
        body: HttpRequestBody? = null,
    ): AppResult<T>

    /**
     * Executes an HTTP request and returns the decoded response body along with status code and headers.
     */
    suspend fun <T> requestWithResponse(
        method: HttpMethodType,
        path: String,
        deserializer: DeserializationStrategy<T>,
        headers: Map<String, String> = emptyMap(),
        queryParams: Map<String, String> = emptyMap(),
        body: HttpRequestBody? = null,
    ): AppResult<HttpResponseEnvelope<T>> =
        request(
            method = method,
            path = path,
            deserializer = deserializer,
            headers = headers,
            queryParams = queryParams,
            body = body,
        ).map { decoded ->
            HttpResponseEnvelope(
                statusCode = HTTP_OK_STATUS,
                headers = emptyMap(),
                body = decoded,
            )
        }

    /**
     * Executes a conditional `GET` request with an optional `If-None-Match` validator,
     * returning [ConditionalHttpResult.NotModified] on HTTP `304` or [ConditionalHttpResult.Modified] on `2xx`.
     */
    suspend fun <T> requestConditional(
        path: String,
        deserializer: DeserializationStrategy<T>,
        ifNoneMatch: String? = null,
        headers: Map<String, String> = emptyMap(),
        queryParams: Map<String, String> = emptyMap(),
    ): AppResult<ConditionalHttpResult<T>> {
        val mergedHeaders =
            if (!ifNoneMatch.isNullOrBlank()) {
                headers.filterKeys { !it.equals(HEADER_IF_NONE_MATCH, ignoreCase = true) } +
                    (HEADER_IF_NONE_MATCH to ifNoneMatch)
            } else {
                headers
            }
        return requestWithResponse(
            method = HttpMethodType.GET,
            path = path,
            deserializer = deserializer,
            headers = mergedHeaders,
            queryParams = queryParams,
        ).map { envelope ->
            ConditionalHttpResult.Modified(envelope)
        }
    }
}

/** Executes a typed `GET` request. */
suspend inline fun <reified T> HttpTransport.safeGet(
    path: String,
    headers: Map<String, String> = emptyMap(),
    queryParams: Map<String, String> = emptyMap(),
): AppResult<T> =
    request(
        method = HttpMethodType.GET,
        path = path,
        deserializer = serializer<T>(),
        headers = headers,
        queryParams = queryParams,
    )

/** Executes a typed `GET` request returning the full [HttpResponseEnvelope]. */
suspend inline fun <reified T> HttpTransport.safeGetWithResponse(
    path: String,
    headers: Map<String, String> = emptyMap(),
    queryParams: Map<String, String> = emptyMap(),
): AppResult<HttpResponseEnvelope<T>> =
    requestWithResponse(
        method = HttpMethodType.GET,
        path = path,
        deserializer = serializer<T>(),
        headers = headers,
        queryParams = queryParams,
    )

/** Executes a conditional `GET` request supporting `If-None-Match` and `304 Not Modified`. */
suspend inline fun <reified T> HttpTransport.safeGetConditional(
    path: String,
    ifNoneMatch: String? = null,
    headers: Map<String, String> = emptyMap(),
    queryParams: Map<String, String> = emptyMap(),
): AppResult<ConditionalHttpResult<T>> =
    requestConditional(
        path = path,
        deserializer = serializer<T>(),
        ifNoneMatch = ifNoneMatch,
        headers = headers,
        queryParams = queryParams,
    )

/** Executes a typed `POST` request with a serializable [body]. */
suspend inline fun <reified T, reified B> HttpTransport.safePost(
    path: String,
    body: B,
    headers: Map<String, String> = emptyMap(),
    queryParams: Map<String, String> = emptyMap(),
): AppResult<T> =
    request(
        method = HttpMethodType.POST,
        path = path,
        deserializer = serializer<T>(),
        headers = headers,
        queryParams = queryParams,
        body = HttpRequestBody.Serialized(value = body, serializer = serializer<B>()),
    )

/** Executes a typed `POST` request without a body. */
suspend inline fun <reified T> HttpTransport.safePost(
    path: String,
    headers: Map<String, String> = emptyMap(),
    queryParams: Map<String, String> = emptyMap(),
): AppResult<T> =
    request(
        method = HttpMethodType.POST,
        path = path,
        deserializer = serializer<T>(),
        headers = headers,
        queryParams = queryParams,
    )

/** Executes a typed `PUT` request with a serializable [body]. */
suspend inline fun <reified T, reified B> HttpTransport.safePut(
    path: String,
    body: B,
    headers: Map<String, String> = emptyMap(),
    queryParams: Map<String, String> = emptyMap(),
): AppResult<T> =
    request(
        method = HttpMethodType.PUT,
        path = path,
        deserializer = serializer<T>(),
        headers = headers,
        queryParams = queryParams,
        body = HttpRequestBody.Serialized(value = body, serializer = serializer<B>()),
    )

/** Executes a typed `DELETE` request. */
suspend inline fun <reified T> HttpTransport.safeDelete(
    path: String,
    headers: Map<String, String> = emptyMap(),
    queryParams: Map<String, String> = emptyMap(),
): AppResult<T> =
    request(
        method = HttpMethodType.DELETE,
        path = path,
        deserializer = serializer<T>(),
        headers = headers,
        queryParams = queryParams,
    )
