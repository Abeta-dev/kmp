package dev.abeta.kmp.network.sse

import dev.abeta.kmp.core.error.AppError
import dev.abeta.kmp.core.error.RateLimitError
import dev.abeta.kmp.core.error.isRetryable
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.time.Clock
import dev.abeta.kmp.core.time.SystemClock
import dev.abeta.kmp.network.client.HttpMethodType
import dev.abeta.kmp.network.client.containsHeaderIgnoreCase
import dev.abeta.kmp.network.client.elapsedSince
import dev.abeta.kmp.network.client.extractRetryAfterMillis
import dev.abeta.kmp.network.client.mapStatusError
import dev.abeta.kmp.network.client.mapTransportThrowable
import dev.abeta.kmp.network.ports.NetworkMetric
import dev.abeta.kmp.network.ports.NetworkTelemetrySink
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.utils.io.readLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow

private const val SSE_ACCEPT_HEADER = "text/event-stream"
private const val SSE_CACHE_CONTROL = "no-cache"
private const val HEADER_LAST_EVENT_ID = "Last-Event-ID"
private const val BACKOFF_MULTIPLIER = 2L
private const val MAX_BACKOFF_SHIFT = 30

/**
 * Ktor-backed streaming [SseTransport] using `prepareGet` and incremental `ByteReadChannel.readLine()`.
 *
 * Tracks `Last-Event-ID` and server-provided `retry:` hints across bounded reconnection attempts,
 * maps non-2xx responses and transport failures to typed [AppError] instances, and never swallows
 * [CancellationException].
 */
class KtorSseTransport(
    private val httpClient: HttpClient,
    private val clock: Clock = SystemClock(),
    private val telemetrySink: NetworkTelemetrySink? = null,
) : SseTransport {
    override fun connect(
        path: String,
        headers: Map<String, String>,
        queryParams: Map<String, String>,
        lastEventId: String?,
        reconnectPolicy: SseReconnectPolicy,
    ): Flow<AppResult<SseEvent>> =
        channelFlow {
            val requestSpec = SseRequestSpec(path = path, headers = headers, queryParams = queryParams)
            val initialEventId =
                lastEventId?.takeIf { it.isNotBlank() }
                    ?: headers.entries
                        .firstOrNull { it.key.equals(HEADER_LAST_EVENT_ID, ignoreCase = true) }
                        ?.value
                        ?.takeIf { it.isNotBlank() }
            val state =
                SseStreamState(
                    lastSeenEventId = initialEventId,
                    serverRetryMillis = null,
                )
            var reconnectAttempts = 0
            var active = true

            while (active) {
                when (val outcome = executeAttempt(requestSpec, state)) {
                    AttemptOutcome.Completed -> {
                        active = false
                    }

                    AttemptOutcome.StreamClosed -> {
                        active =
                            scheduleReconnectIfAllowed(
                                policy = reconnectPolicy,
                                attemptIndex = reconnectAttempts,
                                serverRetryMillis = state.serverRetryMillis,
                                rateLimitRetryAfterMillis = null,
                            )
                        if (active) {
                            reconnectAttempts += 1
                        }
                    }

                    is AttemptOutcome.Failed -> {
                        val canRetry =
                            outcome.error.isRetryable &&
                                scheduleReconnectIfAllowed(
                                    policy = reconnectPolicy,
                                    attemptIndex = reconnectAttempts,
                                    serverRetryMillis = state.serverRetryMillis,
                                    rateLimitRetryAfterMillis = outcome.retryAfterMillis,
                                )
                        if (canRetry) {
                            reconnectAttempts += 1
                        } else {
                            send(AppResult.Failure(outcome.error))
                            active = false
                        }
                    }
                }
            }
        }.buffer(Channel.RENDEZVOUS)

    @Suppress("TooGenericExceptionCaught")
    private suspend fun ProducerScope<AppResult<SseEvent>>.executeAttempt(
        spec: SseRequestSpec,
        state: SseStreamState,
    ): AttemptOutcome {
        val startedAt = clock.nowEpochMillis()
        return try {
            val statement =
                httpClient.prepareGet(spec.path) {
                    applySseHeaders(spec.headers, state.lastSeenEventId)
                    spec.queryParams.forEach { (name, value) -> parameter(name, value) }
                }
            statement.execute { response ->
                handlePreparedResponse(response, state, startedAt)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            val error = mapTransportThrowable(spec.path, throwable)
            emitMetric(
                url = spec.path,
                statusCode = null,
                durationMillis = elapsedSince(clock, startedAt),
                isSuccess = false,
                errorType = throwable::class.simpleName ?: error.code,
            )
            AttemptOutcome.Failed(error = error, retryAfterMillis = null)
        }
    }

    private suspend fun ProducerScope<AppResult<SseEvent>>.handlePreparedResponse(
        response: HttpResponse,
        state: SseStreamState,
        startedAt: Long,
    ): AttemptOutcome {
        val requestUrl =
            response.call.request.url
                .toString()
        val status = response.status
        return when {
            status == HttpStatusCode.NoContent -> {
                emitMetric(
                    url = requestUrl,
                    statusCode = status.value,
                    durationMillis = elapsedSince(clock, startedAt),
                    isSuccess = true,
                )
                AttemptOutcome.Completed
            }

            !status.isSuccess() -> {
                val retryAfterMillis = extractRetryAfterMillis(response.headers)
                val error = mapStatusError(status, requestUrl, retryAfterMillis)
                emitMetric(
                    url = requestUrl,
                    statusCode = status.value,
                    durationMillis = elapsedSince(clock, startedAt),
                    isSuccess = false,
                    errorType = error::class.simpleName ?: error.code,
                )
                AttemptOutcome.Failed(
                    error = error,
                    retryAfterMillis = (error as? RateLimitError)?.retryAfterMillis,
                )
            }

            else -> {
                streamEvents(response, state)
                emitMetric(
                    url = requestUrl,
                    statusCode = status.value,
                    durationMillis = elapsedSince(clock, startedAt),
                    isSuccess = true,
                )
                AttemptOutcome.StreamClosed
            }
        }
    }

    private suspend fun ProducerScope<AppResult<SseEvent>>.streamEvents(
        response: HttpResponse,
        state: SseStreamState,
    ) {
        val channel = response.bodyAsChannel()
        val parser = SseLineParser(initialLastEventId = state.lastSeenEventId)
        while (true) {
            val line = channel.readLine() ?: break
            val event = parser.parseLine(line)
            state.syncFrom(parser)
            if (event != null) {
                send(AppResult.Success(event))
            }
        }
        val trailingEvent = parser.flush()
        state.syncFrom(parser)
        if (trailingEvent != null) {
            send(AppResult.Success(trailingEvent))
        }
    }

    private suspend fun scheduleReconnectIfAllowed(
        policy: SseReconnectPolicy,
        attemptIndex: Int,
        serverRetryMillis: Long?,
        rateLimitRetryAfterMillis: Long?,
    ): Boolean {
        val allowed = policy.enabled && attemptIndex < policy.maxReconnectAttempts
        if (allowed) {
            val delayMillis =
                computeReconnectDelayMillis(
                    policy = policy,
                    attemptIndex = attemptIndex,
                    serverRetryMillis = serverRetryMillis,
                    rateLimitRetryAfterMillis = rateLimitRetryAfterMillis,
                )
            if (delayMillis > 0L) {
                delay(delayMillis)
            }
        }
        return allowed
    }

    private fun emitMetric(
        url: String,
        statusCode: Int?,
        durationMillis: Long,
        isSuccess: Boolean,
        errorType: String? = null,
    ) {
        telemetrySink?.onRequestComplete(
            NetworkMetric(
                method = HttpMethodType.GET.name,
                url = url,
                statusCode = statusCode,
                durationMillis = durationMillis,
                isSuccess = isSuccess,
                errorType = errorType,
            ),
        )
    }

    private data class SseRequestSpec(
        val path: String,
        val headers: Map<String, String>,
        val queryParams: Map<String, String>,
    )

    private class SseStreamState(
        var lastSeenEventId: String?,
        var serverRetryMillis: Long?,
    ) {
        fun syncFrom(parser: SseLineParser) {
            lastSeenEventId = parser.lastEventId
            if (parser.lastRetryMillis != null) {
                serverRetryMillis = parser.lastRetryMillis
            }
        }
    }

    private sealed interface AttemptOutcome {
        data object Completed : AttemptOutcome

        data object StreamClosed : AttemptOutcome

        data class Failed(
            val error: AppError,
            val retryAfterMillis: Long?,
        ) : AttemptOutcome
    }
}

private fun HttpRequestBuilder.applySseHeaders(
    headers: Map<String, String>,
    lastEventId: String?,
) {
    headers.forEach { (name, value) ->
        if (!name.equals(HEADER_LAST_EVENT_ID, ignoreCase = true)) {
            header(name, value)
        }
    }
    if (!headers.containsHeaderIgnoreCase(HttpHeaders.Accept)) {
        header(HttpHeaders.Accept, SSE_ACCEPT_HEADER)
    }
    if (!headers.containsHeaderIgnoreCase(HttpHeaders.CacheControl)) {
        header(HttpHeaders.CacheControl, SSE_CACHE_CONTROL)
    }
    if (!lastEventId.isNullOrBlank()) {
        header(HEADER_LAST_EVENT_ID, lastEventId)
    }
}

internal fun computeReconnectDelayMillis(
    policy: SseReconnectPolicy,
    attemptIndex: Int,
    serverRetryMillis: Long?,
    rateLimitRetryAfterMillis: Long?,
): Long {
    val hinted = rateLimitRetryAfterMillis ?: serverRetryMillis
    val computed =
        if (hinted != null) {
            hinted.coerceIn(0L, policy.maxDelayMillis)
        } else {
            exponentialBackoffMillis(policy, attemptIndex)
        }
    return computed
}

private fun exponentialBackoffMillis(
    policy: SseReconnectPolicy,
    attemptIndex: Int,
): Long {
    var delayMs = policy.initialDelayMillis
    if (delayMs > 0L) {
        repeat(attemptIndex.coerceAtMost(MAX_BACKOFF_SHIFT)) {
            delayMs =
                if (delayMs > policy.maxDelayMillis / BACKOFF_MULTIPLIER) {
                    policy.maxDelayMillis
                } else {
                    delayMs * BACKOFF_MULTIPLIER
                }
        }
    }
    return delayMs.coerceAtMost(policy.maxDelayMillis)
}
