package dev.abeta.kmp.layout.engine

import dev.abeta.kmp.actions.handler.ActionOutcome
import dev.abeta.kmp.actions.handler.ApiCallExecutor
import dev.abeta.kmp.actions.model.CallApiPayload
import dev.abeta.kmp.core.error.OfflineError
import dev.abeta.kmp.core.identity.SequentialUuidGenerator
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.storage.InMemoryKeyValueStore
import dev.abeta.kmp.core.time.MutableClock
import dev.abeta.kmp.state.sync.FlushReport
import dev.abeta.kmp.state.sync.OfflineMutationQueue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OfflineActionCoordinatorTest {
    @Test
    fun enqueueOfflineCallAndFlushRetriesThenSucceedsWhenOnline() =
        runTest {
            val queue =
                OfflineMutationQueue(
                    backingStore = InMemoryKeyValueStore(),
                    clock = MutableClock(10_000L),
                    uuidGenerator = SequentialUuidGenerator(),
                )
            var isOffline = true
            val executedEndpoints = mutableListOf<String>()
            val executor =
                ApiCallExecutor { payload, _ ->
                    if (isOffline) {
                        AppResult.Failure(OfflineError("Offline"))
                    } else {
                        executedEndpoints.add(payload.endpoint)
                        AppResult.Success(
                            ActionOutcome.ApiCallResult(
                                endpoint = payload.endpoint,
                                method = payload.method,
                                isSuccess = true,
                                statusCode = 200,
                            ),
                        )
                    }
                }
            val coordinator = OfflineActionCoordinator(mutationQueue = queue, apiCallExecutor = executor)

            coordinator.enqueueOfflineCall(
                payload =
                    CallApiPayload(
                        endpoint = "/api/v1/actions/execute",
                        method = "POST",
                        body = buildJsonObject { put("form_id", "settings") },
                    ),
                reason = "Offline",
            )
            assertEquals(1, coordinator.status.value.pendingCount)

            val firstFlush = assertIs<AppResult.Success<FlushReport>>(coordinator.flushAll()).value
            assertEquals(0, firstFlush.succeeded)
            assertEquals(1, firstFlush.retainedForRetry)
            assertEquals(1, coordinator.status.value.pendingCount)

            isOffline = false
            val secondFlush = assertIs<AppResult.Success<FlushReport>>(coordinator.flushAll()).value
            assertEquals(1, secondFlush.succeeded)
            assertEquals(0, secondFlush.retainedForRetry)
            assertEquals(0, coordinator.status.value.pendingCount)
            assertEquals(listOf("/api/v1/actions/execute"), executedEndpoints)
        }

    @Test
    fun flushTreatsNonSuccessApiCallOutcomeAsRetryableFailure() =
        runTest {
            val queue =
                OfflineMutationQueue(
                    backingStore = InMemoryKeyValueStore(),
                    clock = MutableClock(20_000L),
                    uuidGenerator = SequentialUuidGenerator(),
                )
            val executor =
                ApiCallExecutor { payload, _ ->
                    AppResult.Success(
                        ActionOutcome.ApiCallResult(
                            endpoint = payload.endpoint,
                            method = payload.method,
                            isSuccess = false,
                            statusCode = 503,
                            errorMessage = "Service unavailable",
                        ),
                    )
                }
            val coordinator = OfflineActionCoordinator(mutationQueue = queue, apiCallExecutor = executor)
            coordinator.enqueueOfflineCall(
                payload = CallApiPayload(endpoint = "/api/v1/sync", method = "POST"),
                reason = "Initial failure",
            )

            val report = assertIs<AppResult.Success<FlushReport>>(coordinator.flushAll()).value
            assertEquals(0, report.succeeded)
            assertEquals(1, report.retainedForRetry)

            coordinator.clear()
            assertEquals(0, coordinator.status.value.pendingCount)
        }
}
