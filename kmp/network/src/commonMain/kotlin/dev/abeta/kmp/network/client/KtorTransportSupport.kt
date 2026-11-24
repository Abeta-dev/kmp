package dev.abeta.kmp.network.client

import dev.abeta.kmp.core.error.AppError
import dev.abeta.kmp.core.error.ConflictError
import dev.abeta.kmp.core.error.ForbiddenError
import dev.abeta.kmp.core.error.NetworkError
import dev.abeta.kmp.core.error.NotFoundError
import dev.abeta.kmp.core.error.RateLimitError
import dev.abeta.kmp.core.error.TimeoutError
import dev.abeta.kmp.core.error.UnauthorizedError
import dev.abeta.kmp.core.time.Clock
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

private const val MILLIS_PER_SECOND = 1_000L

internal fun HttpRequestBuilder.applyBody(
    json: Json,
    body: HttpRequestBody?,
) {
    if (body == null) return
    contentType(ContentType.Application.Json)
    when (body) {
        is HttpRequestBody.JsonValue -> {
            setBody(json.encodeToString(JsonElement.serializer(), body.element))
        }

        is HttpRequestBody.Serialized<*> -> {
            @Suppress("UNCHECKED_CAST")
            val typed = body as HttpRequestBody.Serialized<Any?>
            setBody(json.encodeToString(typed.serializer, typed.value))
        }
    }
}

internal fun elapsedSince(
    clock: Clock,
    startedAt: Long,
): Long = (clock.nowEpochMillis() - startedAt).coerceAtLeast(0L)

internal fun Headers.toHeaderMap(): Map<String, String> =
    buildMap {
        entries().forEach { (name, values) ->
            if (values.isNotEmpty()) {
                put(name, values.joinToString(separator = ", "))
            }
        }
    }

internal fun extractRetryAfterMillis(headers: Headers): Long? =
    headers[HttpHeaders.RetryAfter]
        ?.trim()
        ?.toLongOrNull()
        ?.coerceAtLeast(0L)
        ?.times(MILLIS_PER_SECOND)

internal fun mapTransportThrowable(
    path: String,
    throwable: Throwable,
): AppError =
    when (throwable) {
        is HttpRequestTimeoutException -> {
            TimeoutError(message = throwable.message ?: "Request timed out", cause = throwable)
        }

        is ConnectTimeoutException -> {
            TimeoutError(message = throwable.message ?: "Connect timed out", cause = throwable)
        }

        is SocketTimeoutException -> {
            TimeoutError(message = throwable.message ?: "Socket timed out", cause = throwable)
        }

        else -> {
            NetworkError(
                statusCode = null,
                message = throwable.message ?: "Network request failed for $path",
                cause = throwable,
            )
        }
    }

internal fun mapStatusError(
    status: HttpStatusCode,
    url: String,
    retryAfterMillis: Long?,
): AppError =
    when (status) {
        HttpStatusCode.Unauthorized -> {
            UnauthorizedError(message = "Unauthorized HTTP 401 for $url")
        }

        HttpStatusCode.Forbidden -> {
            ForbiddenError(resource = url, message = "Forbidden HTTP 403 for $url")
        }

        HttpStatusCode.NotFound -> {
            NotFoundError(resource = url, message = "HTTP 404 Not Found for $url")
        }

        HttpStatusCode.Conflict -> {
            ConflictError(resource = url, message = "HTTP 409 Conflict for $url")
        }

        HttpStatusCode.TooManyRequests -> {
            RateLimitError(retryAfterMillis = retryAfterMillis, message = "HTTP 429 Rate Limited for $url")
        }

        HttpStatusCode.RequestTimeout, HttpStatusCode.GatewayTimeout -> {
            TimeoutError(message = "HTTP ${status.value} Timeout for $url")
        }

        else -> {
            NetworkError(
                statusCode = status.value,
                message = "HTTP ${status.value} (${status.description}) for $url",
            )
        }
    }

internal fun HttpMethodType.toKtorMethod(): HttpMethod =
    when (this) {
        HttpMethodType.GET -> HttpMethod.Get
        HttpMethodType.POST -> HttpMethod.Post
        HttpMethodType.PUT -> HttpMethod.Put
        HttpMethodType.PATCH -> HttpMethod.Patch
        HttpMethodType.DELETE -> HttpMethod.Delete
    }

internal fun Map<String, String>.containsHeaderIgnoreCase(name: String): Boolean {
    val found = extractHeaderIgnoreCase(this, name)
    return found != null
}

internal fun Map<String, String>.withHeaderOverride(
    name: String,
    value: String,
): Map<String, String> = filterKeys { !it.equals(name, ignoreCase = true) } + (name to value)

internal fun extractHeaderIgnoreCase(
    headers: Map<String, String>,
    name: String,
): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
