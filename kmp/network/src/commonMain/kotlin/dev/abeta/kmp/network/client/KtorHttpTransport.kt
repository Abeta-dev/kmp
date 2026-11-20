package dev.abeta.kmp.network.client

import dev.abeta.kmp.core.error.AppError
import dev.abeta.kmp.core.error.ConflictError
import dev.abeta.kmp.core.error.ForbiddenError
import dev.abeta.kmp.core.error.NetworkError
import dev.abeta.kmp.core.error.NotFoundError
import dev.abeta.kmp.core.error.RateLimitError
import dev.abeta.kmp.core.error.SerializationError
import dev.abeta.kmp.core.error.TimeoutError
import dev.abeta.kmp.core.error.UnauthorizedError
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.time.Clock
import dev.abeta.kmp.core.time.SystemClock
import dev.abeta.kmp.network.ports.NetworkMetric
import dev.abeta.kmp.network.ports.NetworkTelemetrySink
import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

private const val MILLIS_PER_SECOND = 1_000L

/**
 * Ktor-backed implementation of [HttpTransport].
 *
 * Maps HTTP statuses, timeouts, and transport/decoding errors to typed [AppError] subtypes and
 * emits request metrics to an optional [NetworkTelemetrySink].
 */
class KtorHttpTransport(
    private val httpClient: HttpClient,
    private val json: Json = HttpClientFactory.defaultJson,
    private val telemetrySink: NetworkTelemetrySink? = null,
    private val clock: Clock = SystemClock(),
) : HttpTransport {
    override suspend fun <T> request(
        method: HttpMethodType,
        path: String,
        deserializer: DeserializationStrategy<T>,
        headers: Map<String, String>,
        queryParams: Map<String, String>,
        body: HttpRequestBody?,
    ): AppResult<T> {
        val startedAt = clock.nowEpochMillis()
        val rawResult = executeRawRequest(method, path, headers, queryParams, body, startedAt)
        return when (rawResult) {
            is AppResult.Failure -> rawResult
            is AppResult.Success -> handleResponse(rawResult.value, deserializer, method.name, startedAt)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun executeRawRequest(
        method: HttpMethodType,
        path: String,
        headers: Map<String, String>,
        queryParams: Map<String, String>,
        body: HttpRequestBody?,
        startedAt: Long,
    ): AppResult<HttpResponse> =
        try {
            val response =
                httpClient.request(path) {
                    this.method = method.toKtorMethod()
                    headers.forEach { (name, value) -> header(name, value) }
                    queryParams.forEach { (name, value) -> parameter(name, value) }
                    applyBody(json, body)
                }
            AppResult.Success(response)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (timeout: HttpRequestTimeoutException) {
            recordTransportException(
                method.name,
                path,
                startedAt,
                TimeoutError(message = timeout.message ?: "Request timed out", cause = timeout),
                timeout,
            )
        } catch (timeout: ConnectTimeoutException) {
            recordTransportException(
                method.name,
                path,
                startedAt,
                TimeoutError(message = timeout.message ?: "Connect timed out", cause = timeout),
                timeout,
            )
        } catch (timeout: SocketTimeoutException) {
            recordTransportException(
                method.name,
                path,
                startedAt,
                TimeoutError(message = timeout.message ?: "Socket timed out", cause = timeout),
                timeout,
            )
        } catch (throwable: Throwable) {
            val error =
                NetworkError(
                    statusCode = null,
                    message = throwable.message ?: "Network request failed for $path",
                    cause = throwable,
                )
            recordTransportException(method.name, path, startedAt, error, throwable)
        }

    private fun recordTransportException(
        methodName: String,
        path: String,
        startedAt: Long,
        error: AppError,
        throwable: Throwable,
    ): AppResult.Failure =
        recordFailure(
            methodName = methodName,
            url = path,
            statusCode = null,
            durationMillis = elapsedSince(clock, startedAt),
            error = error,
            errorType = throwable::class.simpleName ?: error.code,
        )

    private suspend fun <T> handleResponse(
        response: HttpResponse,
        deserializer: DeserializationStrategy<T>,
        methodName: String,
        startedAt: Long,
    ): AppResult<T> {
        val durationMillis = elapsedSince(clock, startedAt)
        val requestUrl =
            response.call.request.url
                .toString()
        val status = response.status
        return if (!status.isSuccess()) {
            val retryAfterMs =
                response.headers[HttpHeaders.RetryAfter]
                    ?.toLongOrNull()
                    ?.times(MILLIS_PER_SECOND)
            val error = mapStatusError(status, requestUrl, retryAfterMs)
            recordFailure(
                methodName = methodName,
                url = requestUrl,
                statusCode = status.value,
                durationMillis = durationMillis,
                error = error,
                errorType = error::class.simpleName ?: error.code,
            )
        } else {
            decodeSuccess(
                response = response,
                deserializer = deserializer,
                methodName = methodName,
                requestUrl = requestUrl,
                durationMillis = durationMillis,
            )
        }
    }

    private suspend fun <T> decodeSuccess(
        response: HttpResponse,
        deserializer: DeserializationStrategy<T>,
        methodName: String,
        requestUrl: String,
        durationMillis: Long,
    ): AppResult<T> {
        val statusCode = response.status.value
        if (deserializer.descriptor == Unit.serializer().descriptor) {
            emitMetric(methodName, requestUrl, statusCode, durationMillis, isSuccess = true)
            @Suppress("UNCHECKED_CAST")
            return AppResult.Success(Unit as T)
        }

        return when (val bodyText = readBodyText(response, methodName, requestUrl, durationMillis)) {
            is AppResult.Failure -> {
                bodyText
            }

            is AppResult.Success -> {
                decodeJsonBody(bodyText.value, deserializer, methodName, requestUrl, statusCode, durationMillis)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun readBodyText(
        response: HttpResponse,
        methodName: String,
        requestUrl: String,
        durationMillis: Long,
    ): AppResult<String> =
        try {
            AppResult.Success(response.bodyAsText())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            val error =
                NetworkError(
                    statusCode = response.status.value,
                    message = throwable.message ?: "Failed to read response body",
                    cause = throwable,
                )
            recordFailure(
                methodName = methodName,
                url = requestUrl,
                statusCode = response.status.value,
                durationMillis = durationMillis,
                error = error,
                errorType = NetworkError::class.simpleName,
            )
        }

    private fun <T> decodeJsonBody(
        rawBody: String,
        deserializer: DeserializationStrategy<T>,
        methodName: String,
        requestUrl: String,
        statusCode: Int,
        durationMillis: Long,
    ): AppResult<T> =
        try {
            val decoded = json.decodeFromString(deserializer, rawBody)
            emitMetric(methodName, requestUrl, statusCode, durationMillis, isSuccess = true)
            AppResult.Success(decoded)
        } catch (exception: SerializationException) {
            recordFailure(
                methodName = methodName,
                url = requestUrl,
                statusCode = statusCode,
                durationMillis = durationMillis,
                error =
                    SerializationError(
                        message = exception.message ?: "Failed to deserialize response from $requestUrl",
                        cause = exception,
                    ),
                errorType = SerializationError::class.simpleName,
            )
        } catch (exception: IllegalArgumentException) {
            recordFailure(
                methodName = methodName,
                url = requestUrl,
                statusCode = statusCode,
                durationMillis = durationMillis,
                error =
                    SerializationError(
                        message = exception.message ?: "Invalid payload from $requestUrl",
                        cause = exception,
                    ),
                errorType = SerializationError::class.simpleName,
            )
        }

    private fun recordFailure(
        methodName: String,
        url: String,
        statusCode: Int?,
        durationMillis: Long,
        error: AppError,
        errorType: String?,
    ): AppResult.Failure {
        emitMetric(
            method = methodName,
            url = url,
            statusCode = statusCode,
            durationMillis = durationMillis,
            isSuccess = false,
            errorType = errorType,
        )
        return AppResult.Failure(error)
    }

    private fun emitMetric(
        method: String,
        url: String,
        statusCode: Int?,
        durationMillis: Long,
        isSuccess: Boolean,
        errorType: String? = null,
    ) {
        telemetrySink?.onRequestComplete(
            NetworkMetric(
                method = method,
                url = url,
                statusCode = statusCode,
                durationMillis = durationMillis,
                isSuccess = isSuccess,
                errorType = errorType,
            ),
        )
    }
}

private fun HttpRequestBuilder.applyBody(
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

private fun elapsedSince(
    clock: Clock,
    startedAt: Long,
): Long = (clock.nowEpochMillis() - startedAt).coerceAtLeast(0L)

private fun mapStatusError(
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

private fun HttpMethodType.toKtorMethod(): HttpMethod =
    when (this) {
        HttpMethodType.GET -> HttpMethod.Get
        HttpMethodType.POST -> HttpMethod.Post
        HttpMethodType.PUT -> HttpMethod.Put
        HttpMethodType.PATCH -> HttpMethod.Patch
        HttpMethodType.DELETE -> HttpMethod.Delete
    }
