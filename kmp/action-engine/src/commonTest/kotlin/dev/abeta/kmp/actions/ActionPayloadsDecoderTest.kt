package dev.abeta.kmp.actions

import dev.abeta.kmp.actions.codec.BuiltInActionDecoders
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.actions.model.BackPressPayload
import dev.abeta.kmp.actions.model.BatchPayload
import dev.abeta.kmp.actions.model.BiometricAuthPayload
import dev.abeta.kmp.actions.model.CallApiPayload
import dev.abeta.kmp.actions.model.DeleteStatePayload
import dev.abeta.kmp.actions.model.DismissOverlayPayload
import dev.abeta.kmp.actions.model.NavigatePayload
import dev.abeta.kmp.actions.model.OpenDialogPayload
import dev.abeta.kmp.actions.model.OpenUrlPayload
import dev.abeta.kmp.actions.model.RefreshPagePayload
import dev.abeta.kmp.actions.model.RequestPermissionPayload
import dev.abeta.kmp.actions.model.SaveStatePayload
import dev.abeta.kmp.actions.model.SharePayload
import dev.abeta.kmp.actions.model.ShowToastPayload
import dev.abeta.kmp.actions.model.TrackEventPayload
import dev.abeta.kmp.actions.model.TriggerFeedbackPayload
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.FeedbackEffect
import dev.abeta.kmp.contracts.OsPermissionKind
import dev.abeta.kmp.contracts.OverlayPresentation
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.TrackingContract
import dev.abeta.kmp.core.result.AppResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ActionPayloadsDecoderTest {
    private val decoderRegistry = BuiltInActionDecoders.createDefaultRegistry()

    @Test
    fun decodesNavigationAndOverlayPayloads() {
        val navResult =
            decoderRegistry.decode(
                type = ActionTypes.NAVIGATE,
                payload =
                    buildJsonObject {
                        put("route", "/checkout")
                        put("clear_back_stack", true)
                        put("launch_mode", "single_top")
                        put("params", buildJsonObject { put("cart_id", "c-9") })
                    },
            )
        val nav = assertIs<AppResult.Success<NavigatePayload>>(navResult).value
        assertEquals("/checkout", nav.route)
        assertTrue(nav.clearBackStack)
        assertEquals("single_top", nav.launchMode)
        assertEquals(mapOf("cart_id" to "c-9"), nav.params)

        val dialogResult =
            decoderRegistry.decode(
                type = ActionTypes.OPEN_DIALOG,
                payload =
                    buildJsonObject {
                        put("dialog_id", "confirm_exit")
                        put("title", "Leave checkout?")
                        put("message", "Unsaved changes will be lost.")
                        put("dismissible", false)
                    },
            )
        val dialog = assertIs<AppResult.Success<OpenDialogPayload>>(dialogResult).value
        assertEquals("confirm_exit", dialog.dialogId)
        assertEquals("Leave checkout?", dialog.title)
        assertEquals("Unsaved changes will be lost.", dialog.message)
        assertFalse(dialog.dismissible)
        assertEquals(OverlayPresentation.DIALOG, dialog.presentation)

        val back =
            assertIs<AppResult.Success<BackPressPayload>>(
                decoderRegistry.decode(
                    type = ActionTypes.BACK_PRESS,
                    payload =
                        buildJsonObject {
                            put("steps", 2)
                            put("result", buildJsonObject { put("confirmed", true) })
                        },
                ),
            ).value
        assertEquals(2, back.steps)
        assertEquals(JsonPrimitive(true), back.result["confirmed"])

        val dismiss =
            assertIs<AppResult.Success<DismissOverlayPayload>>(
                decoderRegistry.decode(
                    type = ActionTypes.DISMISS_OVERLAY,
                    payload = buildJsonObject { put("overlay_id", "confirm_exit") },
                ),
            ).value
        assertEquals("confirm_exit", dismiss.overlayId)
        assertFalse(dismiss.all)
    }

    @Test
    fun decodesOpenDialogPresentationBottomSheet() {
        val sheetResult =
            decoderRegistry.decode(
                type = ActionTypes.OPEN_DIALOG,
                payload =
                    buildJsonObject {
                        put("dialog_id", "filter_sheet")
                        put("title", "Filters")
                        put("presentation", "bottom_sheet")
                    },
            )
        val sheet = assertIs<AppResult.Success<OpenDialogPayload>>(sheetResult).value
        assertEquals(OverlayPresentation.BOTTOM_SHEET, sheet.presentation)
        assertTrue(sheet.snippets.isEmpty())
        assertEquals(null, sheet.sheetScreenId)
    }

    @Test
    fun decodesOpenDialogRichSnippetsSheetScreenIdAndActionRefTracking() {
        val sheetResult =
            decoderRegistry.decode(
                type = ActionTypes.OPEN_DIALOG,
                payload =
                    buildJsonObject {
                        put("dialog_id", "variant_picker")
                        put("title", "Choose Pack Size")
                        put("presentation", "bottom_sheet")
                        put("sheet_screen_id", "fresh-variants")
                        put(
                            "snippets",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("snippet_id", "variant_row_1")
                                        put("snippet_type", SnippetTypes.ACTION_ROW)
                                        put("data", buildJsonObject { put("label", "500 g") })
                                    },
                                )
                            },
                        )
                    },
            )
        val sheet = assertIs<AppResult.Success<OpenDialogPayload>>(sheetResult).value
        assertEquals(OverlayPresentation.BOTTOM_SHEET, sheet.presentation)
        assertEquals("fresh-variants", sheet.sheetScreenId)
        assertEquals(1, sheet.snippets.size)
        assertEquals("variant_row_1", sheet.snippets.first().snippetId)
        assertEquals(SnippetTypes.ACTION_ROW, sheet.snippets.first().snippetType)

        val trackedAction =
            ActionRef(
                id = "open_variants_1",
                type = ActionTypes.OPEN_DIALOG,
                payload = buildJsonObject { put("dialog_id", "variant_picker") },
                tracking =
                    TrackingContract(
                        eventName = "variant_sheet_opened",
                        properties = mapOf("sku" to "milk-1l"),
                    ),
            )
        val roundTripped = SduiJson.decodeFromString<ActionRef>(SduiJson.encodeToString(trackedAction))
        assertEquals(trackedAction, roundTripped)
        assertEquals("variant_sheet_opened", roundTripped.tracking?.eventName)
        assertEquals("milk-1l", roundTripped.tracking?.properties?.get("sku"))
    }

    @Test
    fun decodesStateAndFeedbackPayloads() {
        val toast =
            assertIs<AppResult.Success<ShowToastPayload>>(
                decoderRegistry.decode(
                    type = ActionTypes.SHOW_TOAST,
                    payload =
                        buildJsonObject {
                            put("message", "Saved")
                            put("duration_ms", 1500L)
                            put("variant", "success")
                        },
                ),
            ).value
        assertEquals("Saved", toast.message)
        assertEquals(1500L, toast.durationMs)
        assertEquals("success", toast.variant)

        val save =
            assertIs<AppResult.Success<SaveStatePayload>>(
                decoderRegistry.decode(
                    type = ActionTypes.SAVE_STATE,
                    payload =
                        buildJsonObject {
                            put("key", "coupon")
                            put("value", "SAVE10")
                            put("scope", "disk")
                        },
                ),
            ).value
        assertEquals("coupon", save.key)
        assertEquals(JsonPrimitive("SAVE10"), save.value)
        assertEquals("disk", save.scope)

        val delete =
            assertIs<AppResult.Success<DeleteStatePayload>>(
                decoderRegistry.decode(
                    type = ActionTypes.DELETE_STATE,
                    payload = buildJsonObject { put("key", "coupon") },
                ),
            ).value
        assertEquals("coupon", delete.key)
        assertEquals("session", delete.scope)
    }

    @Test
    fun decodesTrackingRefreshApiAndBatchPayloads() {
        val track =
            assertIs<AppResult.Success<TrackEventPayload>>(
                decoderRegistry.decode(
                    type = ActionTypes.TRACK_EVENT,
                    payload =
                        buildJsonObject {
                            put("event_name", "checkout_viewed")
                            put("properties", buildJsonObject { put("item_count", 3) })
                        },
                ),
            ).value
        assertEquals("checkout_viewed", track.eventName)
        assertEquals(JsonPrimitive(3), track.properties["item_count"])

        val refresh =
            assertIs<AppResult.Success<RefreshPagePayload>>(
                decoderRegistry.decode(
                    type = ActionTypes.REFRESH_PAGE,
                    payload =
                        buildJsonObject {
                            put("page_id", "home")
                            put("show_loader", true)
                        },
                ),
            ).value
        assertEquals("home", refresh.pageId)
        assertTrue(refresh.showLoader)

        val callApi =
            assertIs<AppResult.Success<CallApiPayload>>(
                decoderRegistry.decode(
                    type = ActionTypes.CALL_API,
                    payload =
                        buildJsonObject {
                            put("endpoint", "/api/v1/orders")
                            put("body", buildJsonObject { put("cart_id", "c-9") })
                        },
                ),
            ).value
        assertEquals("/api/v1/orders", callApi.endpoint)
        assertEquals(JsonPrimitive("c-9"), callApi.body?.get("cart_id"))

        val batch =
            assertIs<AppResult.Success<BatchPayload>>(
                decoderRegistry.decode(
                    type = ActionTypes.BATCH,
                    payload =
                        buildJsonObject {
                            put("stop_on_error", true)
                            put("parallel", true)
                            put("actions", buildJsonArray {})
                        },
                ),
            ).value
        assertTrue(batch.stopOnError)
        assertTrue(batch.parallel)
    }

    @Test
    fun decodesTriggerFeedbackPayloadsAcrossAllEffects() {
        val defaults =
            assertIs<AppResult.Success<TriggerFeedbackPayload>>(
                decoderRegistry.decode(
                    type = ActionTypes.TRIGGER_FEEDBACK,
                    payload = buildJsonObject {},
                ),
            ).value
        assertEquals(FeedbackEffect.CELEBRATION, defaults.effect)
        assertEquals(1800L, defaults.durationMs)
        assertEquals(null, defaults.title)
        assertEquals(null, defaults.subtitle)
        assertEquals(null, defaults.targetId)

        val cases =
            listOf(
                "celebration" to FeedbackEffect.CELEBRATION,
                "cart_bounce" to FeedbackEffect.CART_BOUNCE,
                "shake_error" to FeedbackEffect.SHAKE_ERROR,
                "haptic_tick" to FeedbackEffect.HAPTIC_TICK,
            )
        cases.forEach { (wireEffect, expectedEffect) ->
            val decoded =
                assertIs<AppResult.Success<TriggerFeedbackPayload>>(
                    decoderRegistry.decode(
                        type = ActionTypes.TRIGGER_FEEDBACK,
                        payload =
                            buildJsonObject {
                                put("effect", wireEffect)
                                put("title", "Order Placed")
                                put("subtitle", "Arriving in 10 mins")
                                put("duration_ms", 2200L)
                                put("target_id", "floating_cart")
                            },
                    ),
                ).value
            assertEquals(expectedEffect, decoded.effect)
            assertEquals("Order Placed", decoded.title)
            assertEquals("Arriving in 10 mins", decoded.subtitle)
            assertEquals(2200L, decoded.durationMs)
            assertEquals("floating_cart", decoded.targetId)
        }
    }

    @Test
    fun decodesOsBridgePayloads() {
        val openUrl =
            assertIs<AppResult.Success<OpenUrlPayload>>(
                decoderRegistry.decode(
                    type = ActionTypes.OPEN_URL,
                    payload =
                        buildJsonObject {
                            put("url", "https://abeta.dev/docs")
                            put("external_browser", true)
                            put("fallback_route", "/screens/home")
                        },
                ),
            ).value
        assertEquals("https://abeta.dev/docs", openUrl.url)
        assertTrue(openUrl.externalBrowser)
        assertEquals("/screens/home", openUrl.fallbackRoute)

        val share =
            assertIs<AppResult.Success<SharePayload>>(
                decoderRegistry.decode(
                    type = ActionTypes.SHARE,
                    payload =
                        buildJsonObject {
                            put("title", "Abeta Fresh")
                            put("text", "8 min express delivery")
                            put("url", "https://app.abeta.dev/screens/store-home")
                        },
                ),
            ).value
        assertEquals("Abeta Fresh", share.title)
        assertEquals("8 min express delivery", share.text)
        assertEquals("https://app.abeta.dev/screens/store-home", share.url)

        val permission =
            assertIs<AppResult.Success<RequestPermissionPayload>>(
                decoderRegistry.decode(
                    type = ActionTypes.REQUEST_PERMISSION,
                    payload =
                        buildJsonObject {
                            put("permission", "location")
                            put("rationale", "Find nearest dark store")
                        },
                ),
            ).value
        assertEquals(OsPermissionKind.LOCATION, permission.permission)
        assertEquals("Find nearest dark store", permission.rationale)

        val biometric =
            assertIs<AppResult.Success<BiometricAuthPayload>>(
                decoderRegistry.decode(
                    type = ActionTypes.BIOMETRIC_AUTH,
                    payload =
                        buildJsonObject {
                            put("reason", "Confirm express checkout")
                            put("allow_device_credential", false)
                        },
                ),
            ).value
        assertEquals("Confirm express checkout", biometric.reason)
        assertFalse(biometric.allowDeviceCredential)
    }
}
