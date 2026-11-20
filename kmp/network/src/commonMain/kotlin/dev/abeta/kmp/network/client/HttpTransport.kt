package dev.abeta.kmp.network.client

import dev.abeta.kmp.core.result.AppResult
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer

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
