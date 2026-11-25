package dev.abeta.kmp.actions

import dev.abeta.kmp.actions.codec.BuiltInActionDecoders
import dev.abeta.kmp.actions.handler.ActionOutcome
import dev.abeta.kmp.actions.handler.ApiCallExecutor
import dev.abeta.kmp.actions.handler.BuiltInActionHandlers
import dev.abeta.kmp.actions.handler.DefaultActionDispatcher
import dev.abeta.kmp.actions.model.ActionContext
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.core.error.ValidationError
import dev.abeta.kmp.core.result.AppResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ActionDispatcherResilienceTest {
    @Test
    fun sequentialBatchPropagatesStateMutatedIntoSubsequentContext() =
        runTest {
            val dispatcher = createDispatcher()
            val batch =
                ActionRef(
                    type = ActionTypes.BATCH,
                    payload =
                        buildJsonObject {
                            put("stop_on_error", true)
                            put(
                                "actions",
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put("type", ActionTypes.SAVE_STATE)
                                            put(
                                                "payload",
                                                buildJsonObject {
                                                    put("key", "promo_code")
                                                    put("value", "FRESH50")
                                                },
                                            )
                                        },
                                    )
                                    add(
                                        buildJsonObject {
                                            put("type", ActionTypes.NAVIGATE)
                                            put("payload", buildJsonObject { put("route", "/checkout") })
                                            put(
                                                "validations",
                                                buildJsonArray {
                                                    add(
                                                        buildJsonObject {
                                                            put("field_id", "promo_code")
                                                            put("rule", "equals")
                                                            put("value", "FRESH50")
                                                            put("message", "Promo missing")
                                                        },
                                                    )
                                                },
                                            )
                                        },
                                    )
                                },
                            )
                        },
                )

            val propagated = dispatcher.dispatch(batch, ActionContext())
            assertEquals(2, propagated.size)
            assertIs<ActionOutcome.StateMutated>(assertIs<AppResult.Success<ActionOutcome>>(propagated[0]).value)
            assertIs<ActionOutcome.NavigationRequested>(
                assertIs<AppResult.Success<ActionOutcome>>(propagated[1]).value,
            )
        }

    @Test
    fun failedApiCallSkipsSecondaryActionsWhileRunningOnError() =
        runTest {
            val dispatcher = createDispatcher()
            val failedApiWithSecondary =
                ActionRef(
                    type = ActionTypes.CALL_API,
                    payload =
                        buildJsonObject {
                            put("endpoint", "/v1/fail")
                            put(
                                "on_error",
                                buildJsonObject {
                                    put("type", ActionTypes.SHOW_TOAST)
                                    put("payload", buildJsonObject { put("message", "Failed") })
                                },
                            )
                        },
                    secondaryActions =
                        listOf(
                            ActionRef(
                                type = ActionTypes.TRACK_EVENT,
                                payload = buildJsonObject { put("event_name", "should_not_fire") },
                            ),
                        ),
                )

            val results = dispatcher.dispatch(failedApiWithSecondary)
            assertEquals(2, results.size)
            assertFalse(
                assertIs<ActionOutcome.ApiCallResult>(
                    assertIs<AppResult.Success<ActionOutcome>>(results[0]).value,
                ).isSuccess,
            )
            assertEquals(
                "Failed",
                assertIs<ActionOutcome.ToastRequested>(
                    assertIs<AppResult.Success<ActionOutcome>>(results[1]).value,
                ).message,
            )
        }

    @Test
    fun maxTotalActionsCapsRunawayBatchExecution() =
        runTest {
            val cappedDispatcher =
                DefaultActionDispatcher(
                    decoderRegistry = BuiltInActionDecoders.createDefaultRegistry(),
                    actionRegistry = BuiltInActionHandlers.createDefaultRegistry(),
                    maxTotalActions = 3,
                )
            val oversizedBatch =
                ActionRef(
                    type = ActionTypes.BATCH,
                    payload =
                        buildJsonObject {
                            put("stop_on_error", false)
                            put(
                                "actions",
                                buildJsonArray {
                                    repeat(5) { idx ->
                                        add(
                                            buildJsonObject {
                                                put("type", ActionTypes.SHOW_TOAST)
                                                put("payload", buildJsonObject { put("message", "toast-$idx") })
                                            },
                                        )
                                    }
                                },
                            )
                        },
                )

            val cappedResults = cappedDispatcher.dispatch(oversizedBatch)
            assertEquals(5, cappedResults.size)
            assertTrue(cappedResults.take(2).all { it is AppResult.Success })
            assertTrue(cappedResults.drop(2).all { it is AppResult.Failure && it.error is ValidationError })
        }

    private fun createDispatcher(): DefaultActionDispatcher {
        val apiExecutor =
            ApiCallExecutor { payload, _ ->
                val ok = payload.endpoint == "/v1/ok"
                AppResult.Success(
                    ActionOutcome.ApiCallResult(
                        endpoint = payload.endpoint,
                        method = payload.method,
                        isSuccess = ok,
                        statusCode = if (ok) 200 else 500,
                    ),
                )
            }
        return DefaultActionDispatcher(
            decoderRegistry = BuiltInActionDecoders.createDefaultRegistry(),
            actionRegistry = BuiltInActionHandlers.createDefaultRegistry(apiExecutor),
        )
    }
}
