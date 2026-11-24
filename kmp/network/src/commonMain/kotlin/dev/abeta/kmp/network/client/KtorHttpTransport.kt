package dev.abeta.kmp.network.client

import dev.abeta.kmp.core.error.AppError
import dev.abeta.kmp.core.error.NetworkError
import dev.abeta.kmp.core.error.SerializationError
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.result.map
import dev.abeta.kmp.core.time.Clock
import dev.abeta.kmp.core.time.SystemClock
import dev.abeta.kmp.network.ports.CsrfTokenProvider
import dev.abeta.kmp.network.ports.NetworkMetric
import dev.abeta.kmp.network.ports.NetworkTelemetrySink
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Ktor-backed implementation of [HttpTransport].
 *
 * Maps HTTP statuses, timeouts, and transport/decoding errors to typed [AppError] subtypes,
 * supports conditional `If-None-Match` / `304 Not Modified` requests, injects CSRF tokens with
 * a single `403` refresh-and-retry cycle, and emits request metrics to an optional [NetworkTelemetrySink].
 */
class KtorHttpTransport(
    private val httpClient: HttpClient,
    private val json: Json = HttpClientFactory.defaultJson,
    private val telemetrySink: NetworkTelemetrySink? = null,
    private val clock: Clock = SystemClock(),
    private val csrfTokenProvider: CsrfTokenProvider? = null,
) : HttpTransport {
    override suspend fun <T> request(
        method: HttpMethodType,
        path: String,
        deserializer: DeserializationStrategy<T>,
        headers: Map<String, String>,
        queryParams: Map<String, String>,
        body: HttpRequestBody?,
    ): AppResult<T> =
        requestWithResponse(
            method = method,
            path = path,
            deserializer = deserializer,
            headers = headers,
            queryParams = queryParams,
            body = body,
        ).map { envelope -> envelope.body }

    override suspend fun <T> requestWithResponse(
        method: HttpMethodType,
        path: String,
        deserializer: DeserializationStrategy<T>,
        headers: Map<String, String>,
        queryParams: Map<String, String>,
        body: HttpRequestBody?,
    ): AppResult<HttpResponseEnvelope<T>> {
        val spec =
            RequestSpec(
                method = method,
                path = path,
                headers = headers,
                queryParams = queryParams,
                body = body,
                startedAt = clock.nowEpochMillis(),
            )
        return when (val rawResult = executeWithCsrf(spec)) {
            is AppResult.Failure -> rawResult
            is AppResult.Success -> handleEnvelopeResponse(rawResult.value, deserializer, spec)
        }
    }

    override suspend fun <T> requestConditional(
        path: String,
        deserializer: DeserializationStrategy<T>,
        ifNoneMatch: String?,
        headers: Map<String, String>,
        queryParams: Map<String, String>,
    ): AppResult<ConditionalHttpResult<T>> {
        val mergedHeaders =
            if (!ifNoneMatch.isNullOrBlank()) {
                headers.withHeaderOverride(HttpHeaders.IfNoneMatch, ifNoneMatch)
            } else {
                headers
            }
        val spec =
            RequestSpec(
                method = HttpMethodType.GET,
                path = path,
                headers = mergedHeaders,
                queryParams = queryParams,
                body = null,
                startedAt = clock.nowEpochMillis(),
            )
        return when (val rawResult = executeWithCsrf(spec)) {
            is AppResult.Failure -> rawResult
            is AppResult.Success -> handleConditionalResponse(rawResult.value, deserializer, ifNoneMatch, spec)
        }
    }

    private suspend fun executeWithCsrf(spec: RequestSpec): AppResult<HttpResponse> {
        val provider = csrfTokenProvider?.takeIf { spec.method in it.protectedMethods }
        val initialHeaders =
            if (provider != null && !spec.headers.containsHeaderIgnoreCase(provider.headerName)) {
                val token = fetchCsrfTokenSafely { provider.currentToken() }
                if (!token.isNullOrBlank()) spec.headers + (provider.headerName to token) else spec.headers
            } else {
                spec.headers
            }

        val firstResult = executeRawRequest(spec.copy(headers = initialHeaders))
        val shouldRetry =
            provider != null &&
                firstResult is AppResult.Success &&
                firstResult.value.status == HttpStatusCode.Forbidden

        return if (!shouldRetry) {
            firstResult
        } else {
            val refreshedToken = fetchCsrfTokenSafely { provider.refreshToken() }
            if (refreshedToken.isNullOrBlank()) {
                firstResult
            } else {
                val retryHeaders = initialHeaders.withHeaderOverride(provider.headerName, refreshedToken)
                executeRawRequest(spec.copy(headers = retryHeaders))
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun executeRawRequest(spec: RequestSpec): AppResult<HttpResponse> =
        try {
            val response =
                httpClient.request(spec.path) {
                    this.method = spec.method.toKtorMethod()
                    spec.headers.forEach { (name, value) -> header(name, value) }
                    spec.queryParams.forEach { (name, value) -> parameter(name, value) }
                    applyBody(json, spec.body)
                }
            AppResult.Success(response)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            val error = mapTransportThrowable(spec.path, throwable)
            val context =
                MetricContext(
                    methodName = spec.method.name,
                    url = spec.path,
                    statusCode = null,
                    durationMillis = elapsedSince(clock, spec.startedAt),
                )
            recordFailure(telemetrySink, context, error, throwable::class.simpleName ?: error.code)
        }

    private suspend fun <T> handleConditionalResponse(
        response: HttpResponse,
        deserializer: DeserializationStrategy<T>,
        ifNoneMatch: String?,
        spec: RequestSpec,
    ): AppResult<ConditionalHttpResult<T>> {
        if (response.status == HttpStatusCode.NotModified) {
            val responseHeaders = response.headers.toHeaderMap()
            val extractedEtag = extractHeaderIgnoreCase(responseHeaders, HttpHeaders.ETag)
            val context =
                MetricContext(
                    methodName = spec.method.name,
                    url =
                        response.call.request.url
                            .toString(),
                    statusCode = HttpStatusCode.NotModified.value,
                    durationMillis = elapsedSince(clock, spec.startedAt),
                )
            emitMetric(telemetrySink, context, isSuccess = true)
            return AppResult.Success(
                ConditionalHttpResult.NotModified(
                    etag = extractedEtag ?: ifNoneMatch?.takeIf { it.isNotBlank() },
                    headers = responseHeaders,
                ),
            )
        }
        return handleEnvelopeResponse(response, deserializer, spec).map { envelope ->
            ConditionalHttpResult.Modified(envelope)
        }
    }

    private suspend fun <T> handleEnvelopeResponse(
        response: HttpResponse,
        deserializer: DeserializationStrategy<T>,
        spec: RequestSpec,
    ): AppResult<HttpResponseEnvelope<T>> {
        val status = response.status
        val context =
            MetricContext(
                methodName = spec.method.name,
                url =
                    response.call.request.url
                        .toString(),
                statusCode = status.value,
                durationMillis = elapsedSince(clock, spec.startedAt),
            )
        return if (!status.isSuccess()) {
            val error = mapStatusError(status, context.url, extractRetryAfterMillis(response.headers))
            recordFailure(telemetrySink, context, error, error::class.simpleName ?: error.code)
        } else {
            val responseHeaders = response.headers.toHeaderMap()
            decodeSuccess(response, deserializer, context).map { decoded ->
                HttpResponseEnvelope(
                    statusCode = status.value,
                    headers = responseHeaders,
                    body = decoded,
                )
            }
        }
    }

    private suspend fun <T> decodeSuccess(
        response: HttpResponse,
        deserializer: DeserializationStrategy<T>,
        context: MetricContext,
    ): AppResult<T> {
        if (deserializer.descriptor == Unit.serializer().descriptor) {
            emitMetric(telemetrySink, context, isSuccess = true)
            @Suppress("UNCHECKED_CAST")
            return AppResult.Success(Unit as T)
        }
        return when (val bodyText = readBodyText(response, telemetrySink, context)) {
            is AppResult.Failure -> bodyText
            is AppResult.Success -> decodeJsonBody(json, bodyText.value, deserializer, telemetrySink, context)
        }
    }

    private data class RequestSpec(
        val method: HttpMethodType,
        val path: String,
        val headers: Map<String, String>,
        val queryParams: Map<String, String>,
        val body: HttpRequestBody?,
        val startedAt: Long,
    )
}

private data class MetricContext(
    val methodName: String,
    val url: String,
    val statusCode: Int?,
    val durationMillis: Long,
)

@Suppress("TooGenericExceptionCaught")
private suspend fun fetchCsrfTokenSafely(block: suspend () -> String?): String? =
    try {
        block()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
    }

@Suppress("TooGenericExceptionCaught")
private suspend fun readBodyText(
    response: HttpResponse,
    telemetrySink: NetworkTelemetrySink?,
    context: MetricContext,
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
        recordFailure(telemetrySink, context, error, NetworkError::class.simpleName)
    }

private fun <T> decodeJsonBody(
    json: Json,
    rawBody: String,
    deserializer: DeserializationStrategy<T>,
    telemetrySink: NetworkTelemetrySink?,
    context: MetricContext,
): AppResult<T> =
    try {
        val decoded = json.decodeFromString(deserializer, rawBody)
        emitMetric(telemetrySink, context, isSuccess = true)
        AppResult.Success(decoded)
    } catch (exception: SerializationException) {
        val error =
            SerializationError(
                message = exception.message ?: "Failed to deserialize response from ${context.url}",
                cause = exception,
            )
        recordFailure(telemetrySink, context, error, SerializationError::class.simpleName)
    } catch (exception: IllegalArgumentException) {
        val error =
            SerializationError(
                message = exception.message ?: "Invalid payload from ${context.url}",
                cause = exception,
            )
        recordFailure(telemetrySink, context, error, SerializationError::class.simpleName)
    }

private fun recordFailure(
    telemetrySink: NetworkTelemetrySink?,
    context: MetricContext,
    error: AppError,
    errorType: String?,
): AppResult.Failure {
    emitMetric(telemetrySink, context, isSuccess = false, errorType = errorType)
    return AppResult.Failure(error)
}

private fun emitMetric(
    telemetrySink: NetworkTelemetrySink?,
    context: MetricContext,
    isSuccess: Boolean,
    errorType: String? = null,
) {
    telemetrySink?.onRequestComplete(
        NetworkMetric(
            method = context.methodName,
            url = context.url,
            statusCode = context.statusCode,
            durationMillis = context.durationMillis,
            isSuccess = isSuccess,
            errorType = errorType,
        ),
    )
}
