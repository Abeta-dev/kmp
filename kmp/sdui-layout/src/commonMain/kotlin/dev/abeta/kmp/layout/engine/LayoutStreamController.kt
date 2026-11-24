package dev.abeta.kmp.layout.engine

import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetDeltaPatch
import dev.abeta.kmp.contracts.SseStreamSpec
import dev.abeta.kmp.core.logging.AppLogger
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.network.sse.SseEvent
import dev.abeta.kmp.network.sse.SseReconnectPolicy
import dev.abeta.kmp.network.sse.SseTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException

internal class LayoutStreamController(
    private val scope: CoroutineScope,
    private val sseTransport: SseTransport?,
    private val logger: AppLogger,
    private val onPatch: (SnippetDeltaPatch) -> Unit,
    private val onStreamingChanged: (Boolean) -> Unit,
) {
    private var activeJob: Job? = null
    private var generation: Long = 0L

    fun observe(
        path: String,
        headers: Map<String, String> = emptyMap(),
        lastEventId: String? = null,
        reconnectPolicy: SseReconnectPolicy = SseReconnectPolicy(),
    ): Job? {
        stop()
        val transport = sseTransport
        val cleanPath = path.trim()
        if (transport == null || cleanPath.isEmpty()) return null
        val activeGen = ++generation
        onStreamingChanged(true)
        val job =
            scope.launch {
                try {
                    transport
                        .connect(
                            path = cleanPath,
                            headers = headers,
                            lastEventId = lastEventId,
                            reconnectPolicy = reconnectPolicy,
                        ).transformWhile<AppResult<SseEvent>, Unit> { result ->
                            processEventResult(result, cleanPath)
                        }.collect()
                } finally {
                    if (generation == activeGen) {
                        activeJob = null
                        onStreamingChanged(false)
                    }
                }
            }
        activeJob = job
        return job
    }

    fun observeFromSpec(spec: SseStreamSpec): Job? {
        if (!spec.autoConnect || sseTransport == null) return null
        return observe(
            path = spec.path,
            headers = spec.headers,
            reconnectPolicy = buildReconnectPolicy(spec),
        )
    }

    fun stop() {
        generation += 1L
        activeJob?.cancel()
        activeJob = null
        onStreamingChanged(false)
    }

    private fun processEventResult(
        result: AppResult<SseEvent>,
        path: String,
    ): Boolean =
        when (result) {
            is AppResult.Failure -> {
                logger.warn(TAG, "SSE stream error on '$path': ${result.error.message}")
                true
            }

            is AppResult.Success -> {
                val patch = decodePatchOrNull(result.value, path)
                if (patch != null) {
                    onPatch(patch)
                    !patch.done
                } else {
                    true
                }
            }
        }

    private fun decodePatchOrNull(
        event: SseEvent,
        path: String,
    ): SnippetDeltaPatch? {
        val raw = event.data.trim()
        if (raw.isEmpty()) return null
        return try {
            SduiJson.decodeFromString(SnippetDeltaPatch.serializer(), raw)
        } catch (exception: SerializationException) {
            logger.warn(TAG, "Ignoring malformed SSE delta frame on '$path': ${exception.message}")
            null
        } catch (exception: IllegalArgumentException) {
            logger.warn(TAG, "Ignoring invalid SSE delta frame on '$path': ${exception.message}")
            null
        }
    }

    private fun buildReconnectPolicy(spec: SseStreamSpec): SseReconnectPolicy {
        val retryDelay = spec.retryMs?.coerceAtLeast(0L)
        return if (retryDelay != null) {
            SseReconnectPolicy(
                initialDelayMillis = retryDelay,
                maxDelayMillis = maxOf(retryDelay, SseReconnectPolicy.DEFAULT_MAX_DELAY_MILLIS),
            )
        } else {
            SseReconnectPolicy()
        }
    }

    private companion object {
        const val TAG = "LayoutController"
    }
}
