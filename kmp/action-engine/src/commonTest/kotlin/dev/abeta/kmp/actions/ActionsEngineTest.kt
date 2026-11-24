package dev.abeta.kmp.actions

import dev.abeta.kmp.actions.codec.ActionDecoderRegistry
import dev.abeta.kmp.actions.codec.BuiltInActionDecoders
import dev.abeta.kmp.actions.codec.kotlinxActionDecoder
import dev.abeta.kmp.actions.handler.ActionHandler
import dev.abeta.kmp.actions.handler.ActionOutcome
import dev.abeta.kmp.actions.handler.ActionRegistry
import dev.abeta.kmp.actions.handler.ApiCallExecutor
import dev.abeta.kmp.actions.handler.BuiltInActionHandlers
import dev.abeta.kmp.actions.handler.DefaultActionDispatcher
import dev.abeta.kmp.actions.model.ActionContext
import dev.abeta.kmp.actions.model.ActionPayload
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.actions.model.BatchPayload
import dev.abeta.kmp.actions.model.NavigatePayload
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.FeedbackEffect
import dev.abeta.kmp.contracts.OsPermissionKind
import dev.abeta.kmp.contracts.OverlayPresentation
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.TrackingContract
import dev.abeta.kmp.core.error.NotFoundError
import dev.abeta.kmp.core.error.SerializationError
import dev.abeta.kmp.core.error.UnexpectedError
import dev.abeta.kmp.core.error.ValidationError
import dev.abeta.kmp.core.result.AppResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ActionsEngineTest {
    @Serializable
    private data class SharePromoPayload(
        @SerialName("promo_code")
        val promoCode: String,
        val channel: String = "whatsapp",
    ) : ActionPayload

    @Test
    fun customActionTypeRegistersWithoutTouchingCore() =
        runTest {
            val captured = mutableListOf<SharePromoPayload>()
            val dispatcher =
                DefaultActionDispatcher(
                    decoderRegistry =
                        BuiltInActionDecoders
                            .registerDefaults(ActionDecoderRegistry.builder())
                            .register("share_promo", kotlinxActionDecoder<SharePromoPayload>())
                            .build(),
                    actionRegistry =
                        BuiltInActionHandlers
                            .registerDefaults(ActionRegistry.builder())
                            .register<SharePromoPayload> { payload, _ ->
                                captured.add(payload)
                                AppResult.Success(
                                    ActionOutcome.ToastRequested("Shared ${payload.promoCode} via ${payload.channel}"),
                                )
                            }.build(),
                )

            val results =
                dispatcher.dispatch(
                    ActionRef(
                        id = "act-custom-1",
                        type = "share_promo",
                        payload =
                            buildJsonObject {
                                put("promo_code", "ABETA50")
                                put("channel", "sms")
                            },
                    ),
                )

            assertEquals(1, results.size)
            val toast =
                assertIs<ActionOutcome.ToastRequested>(
                    assertIs<AppResult.Success<ActionOutcome>>(results.single()).value,
                )
            assertEquals("Shared ABETA50 via sms", toast.message)
            assertEquals(SharePromoPayload(promoCode = "ABETA50", channel = "sms"), captured.single())
        }

    @Test
    fun callApiDispatchesOnSuccessAndSecondaryActions() =
        runTest {
            val dispatcher = createApiDispatcher()
            val action =
                ActionRef(
                    type = ActionTypes.CALL_API,
                    payload =
                        buildJsonObject {
                            put("method", "POST")
                            put("endpoint", "/v1/orders")
                            put(
                                "on_success",
                                buildJsonObject {
                                    put("type", ActionTypes.SHOW_TOAST)
                                    put("payload", buildJsonObject { put("message", "Order placed") })
                                },
                            )
                        },
                    secondaryActions =
                        listOf(
                            ActionRef(
                                type = ActionTypes.TRACK_EVENT,
                                payload =
                                    buildJsonObject {
                                        put("event_name", "order_submitted")
                                        put("properties", buildJsonObject { put("source", "checkout") })
                                    },
                            ),
                        ),
                )

            val results = dispatcher.dispatch(action)
            assertEquals(3, results.size)
            assertTrue(
                assertIs<ActionOutcome.ApiCallResult>(
                    assertIs<AppResult.Success<ActionOutcome>>(results[0]).value,
                ).isSuccess,
            )
            assertEquals(
                "Order placed",
                assertIs<ActionOutcome.ToastRequested>(
                    assertIs<AppResult.Success<ActionOutcome>>(results[1]).value,
                ).message,
            )
            assertEquals(
                "order_submitted",
                assertIs<ActionOutcome.TrackingRequested>(
                    assertIs<AppResult.Success<ActionOutcome>>(results[2]).value,
                ).eventName,
            )
        }

    @Test
    fun callApiDispatchesOnErrorWhenApiReturnsFailureOutcome() =
        runTest {
            val dispatcher = createApiDispatcher()
            val errorAction =
                ActionRef(
                    type = ActionTypes.CALL_API,
                    payload =
                        buildJsonObject {
                            put("endpoint", "/v1/fail")
                            put(
                                "on_error",
                                buildJsonObject {
                                    put("type", ActionTypes.SHOW_TOAST)
                                    put(
                                        "payload",
                                        buildJsonObject {
                                            put("message", "Request failed")
                                            put("variant", "error")
                                        },
                                    )
                                },
                            )
                        },
                )

            val errorResults = dispatcher.dispatch(errorAction)
            assertEquals(2, errorResults.size)
            val failedApi =
                assertIs<ActionOutcome.ApiCallResult>(
                    assertIs<AppResult.Success<ActionOutcome>>(errorResults[0]).value,
                )
            assertFalse(failedApi.isSuccess)
            val errorToast =
                assertIs<ActionOutcome.ToastRequested>(
                    assertIs<AppResult.Success<ActionOutcome>>(errorResults[1]).value,
                )
            assertEquals("Request failed", errorToast.message)
            assertEquals("error", errorToast.variant)
        }

    @Test
    fun batchActionExecutesAllChildrenAndRespectsStopOnError() =
        runTest {
            val dispatcher = createApiDispatcher()
            val children = sampleBatchChildren()

            val allResults =
                dispatcher.dispatch(
                    ActionRef(
                        type = ActionTypes.BATCH,
                        payload =
                            buildJsonObject {
                                put("stop_on_error", false)
                                put("actions", children)
                            },
                    ),
                )
            assertEquals(3, allResults.size)
            assertIs<ActionOutcome.StateMutated>(assertIs<AppResult.Success<ActionOutcome>>(allResults[0]).value)
            assertIs<NotFoundError>(assertIs<AppResult.Failure>(allResults[1]).error)
            assertIs<ActionOutcome.NavigationRequested>(
                assertIs<AppResult.Success<ActionOutcome>>(allResults[2]).value,
            )

            val stoppedResults =
                dispatcher.dispatch(
                    ActionRef(
                        type = ActionTypes.BATCH,
                        payload =
                            buildJsonObject {
                                put("stop_on_error", true)
                                put("actions", children)
                            },
                        secondaryActions =
                            listOf(
                                ActionRef(
                                    type = ActionTypes.SHOW_TOAST,
                                    payload = buildJsonObject { put("message", "Skipped") },
                                ),
                            ),
                    ),
                )
            assertEquals(2, stoppedResults.size)
            assertIs<ActionOutcome.StateMutated>(assertIs<AppResult.Success<ActionOutcome>>(stoppedResults[0]).value)
            assertIs<NotFoundError>(assertIs<AppResult.Failure>(stoppedResults[1]).error)
        }

    private fun sampleBatchChildren() =
        buildJsonArray {
            add(
                buildJsonObject {
                    put("type", ActionTypes.SAVE_STATE)
                    put(
                        "payload",
                        buildJsonObject {
                            put("key", "coupon")
                            put("value", "SAVE10")
                        },
                    )
                },
            )
            add(buildJsonObject { put("type", "unknown_action") })
            add(
                buildJsonObject {
                    put("type", ActionTypes.NAVIGATE)
                    put("payload", buildJsonObject { put("route", "/home") })
                },
            )
        }

    @Test
    fun goldenActionBatchJsonDecodesAndDispatchesThroughDispatcher() =
        runTest {
            val decoderRegistry = BuiltInActionDecoders.createDefaultRegistry()
            val actionRef = SduiJson.decodeFromString<ActionRef>(GOLDEN_ACTION_BATCH_JSON)
            val batchPayload =
                assertIs<AppResult.Success<BatchPayload>>(
                    decoderRegistry.decode(actionRef.type, actionRef.payload),
                ).value
            assertEquals("batch_onboarding_submit", actionRef.id)
            assertTrue(batchPayload.stopOnError)
            assertEquals(2, batchPayload.actions.size)

            val dispatcher = createApiDispatcher(successEndpoint = "/api/v1/actions/execute")
            val invalidResults =
                dispatcher.dispatch(
                    actionRef,
                    ActionContext(
                        sessionState =
                            mapOf(
                                "full_name" to JsonPrimitive("Jane Doe"),
                                "work_email" to JsonPrimitive("not-an-email"),
                            ),
                    ),
                )
            assertEquals(2, invalidResults.size)
            assertEquals(
                "work_email",
                assertIs<ValidationError>(assertIs<AppResult.Failure>(invalidResults[1]).error).field,
            )

            val validResults =
                dispatcher.dispatch(
                    actionRef,
                    ActionContext(
                        sessionState =
                            mapOf(
                                "full_name" to JsonPrimitive("Jane Doe"),
                                "work_email" to JsonPrimitive("jane@abeta.dev"),
                            ),
                    ),
                )
            assertEquals(4, validResults.size)
            assertIs<ActionOutcome.StateMutated>(assertIs<AppResult.Success<ActionOutcome>>(validResults[0]).value)
            assertIs<ActionOutcome.ApiCallResult>(assertIs<AppResult.Success<ActionOutcome>>(validResults[1]).value)
            assertTrue(
                assertIs<ActionOutcome.NavigationRequested>(
                    assertIs<AppResult.Success<ActionOutcome>>(validResults[2]).value,
                ).clearBackStack,
            )
            assertEquals(
                "onboarding_batch_dispatched",
                assertIs<ActionOutcome.TrackingRequested>(
                    assertIs<AppResult.Success<ActionOutcome>>(validResults[3]).value,
                ).eventName,
            )
        }

    @Test
    fun malformedPayloadAndThrowingHandlerProduceTypedFailuresWithoutSecondaryDispatch() =
        runTest {
            val dispatcher =
                DefaultActionDispatcher(
                    decoderRegistry = BuiltInActionDecoders.createDefaultRegistry(),
                    actionRegistry =
                        BuiltInActionHandlers
                            .registerDefaults(ActionRegistry.builder())
                            .register<NavigatePayload>(ActionHandler { _, _ -> error("Simulated crash") })
                            .build(),
                )
            val secondary =
                listOf(
                    ActionRef(
                        type = ActionTypes.SHOW_TOAST,
                        payload = buildJsonObject { put("message", "Should not fire") },
                    ),
                )

            val decodeFail =
                dispatcher.dispatch(
                    ActionRef(type = ActionTypes.NAVIGATE, secondaryActions = secondary),
                )
            assertEquals(1, decodeFail.size)
            assertIs<SerializationError>(assertIs<AppResult.Failure>(decodeFail.single()).error)

            val handlerFail =
                dispatcher.dispatch(
                    ActionRef(
                        type = ActionTypes.NAVIGATE,
                        payload = buildJsonObject { put("route", "/boom") },
                        secondaryActions = secondary,
                    ),
                )
            assertEquals(1, handlerFail.size)
            assertIs<UnexpectedError>(assertIs<AppResult.Failure>(handlerFail.single()).error)
        }

    @Test
    fun openDialogPropagatesPresentationToOverlayRequestedOutcome() =
        runTest {
            val dispatcher = createApiDispatcher()

            val dialogResults =
                dispatcher.dispatch(
                    ActionRef(
                        type = ActionTypes.OPEN_DIALOG,
                        payload =
                            buildJsonObject {
                                put("dialog_id", "confirm_delete")
                                put("title", "Delete item?")
                            },
                    ),
                )
            val dialogOutcome =
                assertIs<ActionOutcome.OverlayRequested>(
                    assertIs<AppResult.Success<ActionOutcome>>(dialogResults.single()).value,
                )
            assertEquals(OverlayPresentation.DIALOG, dialogOutcome.presentation)

            val sheetResults =
                dispatcher.dispatch(
                    ActionRef(
                        type = ActionTypes.OPEN_DIALOG,
                        payload =
                            buildJsonObject {
                                put("dialog_id", "sort_options")
                                put("title", "Sort by")
                                put("presentation", "bottom_sheet")
                            },
                    ),
                )
            val sheetOutcome =
                assertIs<ActionOutcome.OverlayRequested>(
                    assertIs<AppResult.Success<ActionOutcome>>(sheetResults.single()).value,
                )
            assertEquals(OverlayPresentation.BOTTOM_SHEET, sheetOutcome.presentation)
            assertEquals("sort_options", sheetOutcome.dialogId)
            assertTrue(sheetOutcome.snippets.isEmpty())
            assertEquals(null, sheetOutcome.sheetScreenId)
        }

    @Test
    fun openDialogPropagatesSnippetsAndSheetScreenIdToOverlayRequested() =
        runTest {
            val dispatcher = createApiDispatcher()
            val richSheetAction =
                ActionRef(
                    id = "open_coupon_drawer",
                    type = ActionTypes.OPEN_DIALOG,
                    payload =
                        buildJsonObject {
                            put("dialog_id", "coupon_drawer")
                            put("title", "Apply Coupon")
                            put("presentation", "bottom_sheet")
                            put("sheet_screen_id", "fresh-coupons")
                            put(
                                "snippets",
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put("snippet_id", "coupon_save20")
                                            put("snippet_type", SnippetTypes.CARD)
                                            put("sticky", true)
                                        },
                                    )
                                },
                            )
                        },
                    tracking =
                        TrackingContract(
                            eventName = "coupon_drawer_opened",
                            properties = mapOf("cart" to "fresh"),
                        ),
                )
            val richSheetResults = dispatcher.dispatch(richSheetAction)
            val richSheetOutcome =
                assertIs<ActionOutcome.OverlayRequested>(
                    assertIs<AppResult.Success<ActionOutcome>>(richSheetResults.single()).value,
                )
            assertEquals("coupon_drawer", richSheetOutcome.dialogId)
            assertEquals("fresh-coupons", richSheetOutcome.sheetScreenId)
            assertEquals(1, richSheetOutcome.snippets.size)
            assertEquals("coupon_save20", richSheetOutcome.snippets.first().snippetId)
            assertTrue(richSheetOutcome.snippets.first().sticky)
        }

    @Test
    fun triggerFeedbackDispatchesAcrossAllEffectModes() =
        runTest {
            val dispatcher = createApiDispatcher()
            val modes =
                listOf(
                    "celebration" to FeedbackEffect.CELEBRATION,
                    "cart_bounce" to FeedbackEffect.CART_BOUNCE,
                    "shake_error" to FeedbackEffect.SHAKE_ERROR,
                    "haptic_tick" to FeedbackEffect.HAPTIC_TICK,
                )

            modes.forEach { (wireEffect, expectedEffect) ->
                val results =
                    dispatcher.dispatch(
                        ActionRef(
                            id = "feedback_$wireEffect",
                            type = ActionTypes.TRIGGER_FEEDBACK,
                            payload =
                                buildJsonObject {
                                    put("effect", wireEffect)
                                    put("title", "Success")
                                    put("subtitle", "Cart updated")
                                    put("duration_ms", 1500L)
                                    put("target_id", "cart_pill")
                                },
                        ),
                    )
                assertEquals(1, results.size)
                val outcome =
                    assertIs<ActionOutcome.FeedbackTriggered>(
                        assertIs<AppResult.Success<ActionOutcome>>(results.single()).value,
                    )
                assertEquals(expectedEffect, outcome.effect)
                assertEquals("Success", outcome.title)
                assertEquals("Cart updated", outcome.subtitle)
                assertEquals(1500L, outcome.durationMs)
                assertEquals("cart_pill", outcome.targetId)
            }

            val defaultResults =
                dispatcher.dispatch(
                    ActionRef(type = ActionTypes.TRIGGER_FEEDBACK, payload = buildJsonObject {}),
                )
            val defaultOutcome =
                assertIs<ActionOutcome.FeedbackTriggered>(
                    assertIs<AppResult.Success<ActionOutcome>>(defaultResults.single()).value,
                )
            assertEquals(FeedbackEffect.CELEBRATION, defaultOutcome.effect)
            assertEquals(1800L, defaultOutcome.durationMs)
        }

    @Test
    fun dispatchesOsBridgeOutcomesCleanly() =
        runTest {
            val dispatcher = createApiDispatcher()

            val openRes =
                dispatcher.dispatch(
                    ActionRef(
                        type = ActionTypes.OPEN_URL,
                        payload = buildJsonObject { put("url", "https://abeta.dev/docs") },
                    ),
                )
            val openOutcome =
                assertIs<ActionOutcome.OpenUrlRequested>(
                    assertIs<AppResult.Success<ActionOutcome>>(openRes.single()).value,
                )
            assertEquals("https://abeta.dev/docs", openOutcome.url)

            val shareRes =
                dispatcher.dispatch(
                    ActionRef(
                        type = ActionTypes.SHARE,
                        payload =
                            buildJsonObject {
                                put("title", "Abeta")
                                put("text", "Express delivery")
                            },
                    ),
                )
            val shareOutcome =
                assertIs<ActionOutcome.ShareRequested>(
                    assertIs<AppResult.Success<ActionOutcome>>(shareRes.single()).value,
                )
            assertEquals("Express delivery", shareOutcome.text)

            val permRes =
                dispatcher.dispatch(
                    ActionRef(
                        type = ActionTypes.REQUEST_PERMISSION,
                        payload = buildJsonObject { put("permission", "notifications") },
                    ),
                )
            val permOutcome =
                assertIs<ActionOutcome.PermissionRequested>(
                    assertIs<AppResult.Success<ActionOutcome>>(permRes.single()).value,
                )
            assertEquals(OsPermissionKind.NOTIFICATIONS, permOutcome.permission)

            val bioRes =
                dispatcher.dispatch(
                    ActionRef(
                        type = ActionTypes.BIOMETRIC_AUTH,
                        payload = buildJsonObject { put("reason", "Confirm checkout") },
                    ),
                )
            val bioOutcome =
                assertIs<ActionOutcome.BiometricAuthRequested>(
                    assertIs<AppResult.Success<ActionOutcome>>(bioRes.single()).value,
                )
            assertEquals("Confirm checkout", bioOutcome.reason)
        }

    private fun createApiDispatcher(successEndpoint: String = "/v1/orders"): DefaultActionDispatcher {
        val apiExecutor =
            ApiCallExecutor { payload, _ ->
                val ok = payload.endpoint == successEndpoint
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

    private companion object {
        // Mirrors bff/testdata/golden/action_batch.json
        private val GOLDEN_ACTION_BATCH_JSON =
            """
            {
              "id": "batch_onboarding_submit",
              "type": "batch",
              "payload": {
                "actions": [
                  {
                    "id": "save_onboarding_draft",
                    "type": "save_state",
                    "payload": {
                      "key": "onboarding_completed",
                      "value": true,
                      "scope": "disk"
                    }
                  },
                  {
                    "id": "submit_onboarding",
                    "type": "call_api",
                    "payload": {
                      "endpoint": "/api/v1/actions/execute",
                      "method": "POST",
                      "body": {
                        "form_id": "workspace_onboarding"
                      },
                      "on_success": {
                        "id": "nav_home_after_onboarding",
                        "type": "navigate",
                        "payload": {
                          "route": "/screens/home",
                          "clear_back_stack": true
                        }
                      }
                    },
                    "validations": [
                      {
                        "field_id": "full_name",
                        "rule": "required",
                        "message": "Full name is required"
                      },
                      {
                        "field_id": "work_email",
                        "rule": "regex",
                        "value": "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$",
                        "message": "Enter a valid email address"
                      }
                    ]
                  }
                ],
                "stop_on_error": true
              },
              "secondary_actions": [
                {
                  "id": "track_onboarding_batch",
                  "type": "track_event",
                  "payload": {
                    "event_name": "onboarding_batch_dispatched",
                    "properties": {
                      "screen": "form-demo"
                    }
                  }
                }
              ]
            }
            """.trimIndent()
    }
}
