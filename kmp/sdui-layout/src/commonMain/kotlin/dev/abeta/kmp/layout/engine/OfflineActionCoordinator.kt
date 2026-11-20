package dev.abeta.kmp.layout.engine

import dev.abeta.kmp.actions.handler.ApiCallExecutor
import dev.abeta.kmp.actions.model.ActionContext
import dev.abeta.kmp.actions.model.CallApiPayload
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.core.error.NetworkError
import dev.abeta.kmp.core.error.NotFoundError
import dev.abeta.kmp.core.error.SerializationError
import dev.abeta.kmp.core.logging.AppLogger
import dev.abeta.kmp.core.logging.NoOpAppLogger
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.result.runCatchingResult
import dev.abeta.kmp.state.sync.FlushReport
import dev.abeta.kmp.state.sync.OfflineMutationQueue
import dev.abeta.kmp.state.sync.OfflineQueueStatus
import dev.abeta.kmp.state.sync.QueuedMutation
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Coordinates durable offline [CallApiPayload] queueing via [OfflineMutationQueue] and
 * replays queued mutations through [ApiCallExecutor] when connectivity recovers.
 */
class OfflineActionCoordinator(
    private val mutationQueue: OfflineMutationQueue,
    private val apiCallExecutor: ApiCallExecutor,
    private val logger: AppLogger = NoOpAppLogger(),
    private val json: Json = SduiJson,
    private val contextProvider: () -> ActionContext = { ActionContext() },
) {
    /** Observable queue status counters and flush progress. */
    val status: StateFlow<OfflineQueueStatus> = mutationQueue.status

    /**
     * Enqueues a failed [CallApiPayload] with optional diagnostic [reason] for later replay.
     */
    suspend fun enqueueOfflineCall(
        payload: CallApiPayload,
        reason: String? = null,
    ): QueuedMutation {
        val envelope = OfflineApiCallEnvelope(payload = payload, reason = reason)
        val encoded = json.encodeToJsonElement(OfflineApiCallEnvelope.serializer(), envelope)
        logger.info(
            TAG,
            "Enqueued offline call ${payload.method} ${payload.endpoint} (reason=${reason.orEmpty()})",
        )
        return mutationQueue.enqueue(
            type = MUTATION_TYPE_CALL_API,
            payload = encoded,
        )
    }

    /**
     * Replays all pending queued mutations through [apiCallExecutor] sequentially.
     */
    suspend fun flushAll(): AppResult<FlushReport> =
        runCatchingResult {
            mutationQueue.flush(::executeMutation)
        }

    /**
     * Clears all pending mutations from the underlying [OfflineMutationQueue].
     */
    suspend fun clear() {
        mutationQueue.clear()
    }

    private suspend fun executeMutation(mutation: QueuedMutation): AppResult<Unit> {
        val payload = if (mutation.type == MUTATION_TYPE_CALL_API) decodePayload(mutation) else null
        return when {
            mutation.type != MUTATION_TYPE_CALL_API -> {
                AppResult.Failure(
                    NotFoundError(
                        resource = mutation.type,
                        message = "Unsupported offline mutation type '${mutation.type}'.",
                    ),
                )
            }

            payload == null -> {
                AppResult.Failure(
                    SerializationError(
                        message = "Failed to decode queued API payload for mutation '${mutation.id}'.",
                    ),
                )
            }

            else -> {
                executeApiPayload(payload)
            }
        }
    }

    private suspend fun executeApiPayload(payload: CallApiPayload): AppResult<Unit> =
        when (val result = apiCallExecutor.execute(payload, contextProvider())) {
            is AppResult.Failure -> {
                result
            }

            is AppResult.Success -> {
                if (result.value.isSuccess) {
                    AppResult.Success(Unit)
                } else {
                    AppResult.Failure(
                        NetworkError(
                            statusCode = result.value.statusCode,
                            message = result.value.errorMessage ?: "API call failed for ${payload.endpoint}",
                        ),
                    )
                }
            }
        }

    private fun decodePayload(mutation: QueuedMutation): CallApiPayload? =
        runCatching {
            json.decodeFromJsonElement(OfflineApiCallEnvelope.serializer(), mutation.payload).payload
        }.getOrNull()
            ?: runCatching {
                json.decodeFromJsonElement(CallApiPayload.serializer(), mutation.payload)
            }.getOrNull()

    @Serializable
    private data class OfflineApiCallEnvelope(
        val payload: CallApiPayload,
        val reason: String? = null,
    )

    private companion object {
        const val TAG = "OfflineActionCoordinator"
        const val MUTATION_TYPE_CALL_API = "call_api"
    }
}
