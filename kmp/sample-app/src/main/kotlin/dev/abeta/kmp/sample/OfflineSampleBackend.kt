package dev.abeta.kmp.sample

import dev.abeta.kmp.actions.handler.ActionOutcome
import dev.abeta.kmp.actions.handler.ApiCallExecutor
import dev.abeta.kmp.actions.model.ActionContext
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.actions.model.CallApiPayload
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.core.error.NotFoundError
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.layout.repository.ScreenRepository
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Offline [ScreenRepository] serving rich built-in sample screens matching the Go BFF catalog.
 */
class OfflineScreenRepository : ScreenRepository {
    private val screens: Map<String, PageResponse> = SampleScreens.allPages()

    override suspend fun fetchScreen(screenId: String): AppResult<PageResponse> {
        val cleanId =
            screenId
                .trim()
                .removePrefix("/screens/")
                .removePrefix("/")
                .substringBefore("?")
        val page = screens[cleanId]
        return if (page != null) {
            AppResult.Success(page)
        } else {
            AppResult.Failure(
                NotFoundError(
                    resource = cleanId,
                    message = "Screen '$cleanId' not found in offline sample repository.",
                ),
            )
        }
    }
}

/**
 * Offline [ApiCallExecutor] simulating BFF API execution for sample-app and vertical actions.
 */
class OfflineApiCallExecutor : ApiCallExecutor {
    override suspend fun execute(
        payload: CallApiPayload,
        context: ActionContext,
    ): AppResult<ActionOutcome.ApiCallResult> {
        val followUps = resolveFollowUps(payload)
        return AppResult.Success(
            ActionOutcome.ApiCallResult(
                endpoint = payload.endpoint,
                method = payload.method,
                isSuccess = true,
                statusCode = HTTP_OK,
                responseBody =
                    buildJsonObject {
                        put("status", "executed")
                        put("endpoint", payload.endpoint)
                        put("offline", true)
                    },
                followUpActions = followUps,
            ),
        )
    }

    private fun resolveFollowUps(payload: CallApiPayload): List<ActionRef> =
        when (payload.endpoint.substringBefore("?")) {
            "/api/v1/orders/express",
            "/api/v1/fresh/orders/express",
            -> {
                celebrationWithToast(
                    idPrefix = "express_order",
                    title = "Order Confirmed!",
                    subtitle = "Abeta Fresh rider arriving in 8 minutes",
                    toastMessage = "Express order confirmed!",
                )
            }

            "/api/v1/bites/orders" -> {
                bitesOrderFollowUps()
            }

            "/api/v1/pulse/bookings" -> {
                celebrationWithToast(
                    idPrefix = "pulse_booking",
                    title = "Tickets Booked!",
                    subtitle = "Instant QR passes saved to your Abeta Pulse wallet",
                    toastMessage = "Tickets Booked! QR passes ready.",
                )
            }

            "/api/v1/mart/orders" -> {
                celebrationWithToast(
                    idPrefix = "mart_order",
                    title = "Order Placed!",
                    subtitle = "Same-day dispatch scheduled from Abeta Mart Hub",
                    toastMessage = "Order Placed! Tracking details sent.",
                )
            }

            "/api/v1/spark/swipe" -> {
                sparkSwipeFollowUps(payload)
            }

            "/api/v1/trade/rfq" -> {
                celebrationWithToast(
                    idPrefix = "trade_rfq",
                    title = "RFQ Submitted!",
                    subtitle = "Quote request #RFQ-9042 dispatched to supplier",
                    toastMessage = "RFQ #RFQ-9042 submitted to supplier!",
                )
            }

            "/api/v1/pay/transfer" -> {
                payTransferFollowUps()
            }

            else -> {
                emptyList()
            }
        }

    private fun bitesOrderFollowUps(): List<ActionRef> =
        celebrationWithToast(
            idPrefix = "bites_order",
            title = "Meal Order Placed!",
            subtitle = "Saffron Hearth Kitchen is preparing your order",
            toastMessage = "Order #BTS-4091 placed! Live tracking ready.",
        ) +
            ActionRef(
                id = "bites_order_nav_track",
                type = ActionTypes.NAVIGATE,
                payload = buildJsonObject { put("route", "bites-order-track") },
            )

    private fun payTransferFollowUps(): List<ActionRef> =
        celebrationWithToast(
            idPrefix = "pay_transfer",
            title = "Transfer Sent!",
            subtitle = "Instant settlement #PAY-3081 confirmed via Abeta Pay",
            toastMessage = "Transfer #PAY-3081 settled instantly!",
        ) +
            ActionRef(
                id = "pay_transfer_nav_receipt",
                type = ActionTypes.NAVIGATE,
                payload = buildJsonObject { put("route", "pay-receipt") },
            )

    private fun sparkSwipeFollowUps(payload: CallApiPayload): List<ActionRef> {
        val swipeAction =
            payload.body
                ?.get("action")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.lowercase()
        return if (swipeAction == "like" || swipeAction == "super_like") {
            celebrationWithToast(
                idPrefix = "spark_match",
                title = "It's a Match!",
                subtitle = "You both sparked — start a conversation in Matches",
                toastMessage = "It's a Match!",
            )
        } else {
            listOf(
                ActionRef(
                    id = "spark_pass_toast",
                    type = ActionTypes.SHOW_TOAST,
                    payload =
                        buildJsonObject {
                            put("message", "Passed — showing next profile")
                            put("duration_ms", PASS_TOAST_DURATION_MS)
                            put("variant", "info")
                        },
                ),
            )
        }
    }

    private fun celebrationWithToast(
        idPrefix: String,
        title: String,
        subtitle: String,
        toastMessage: String,
    ): List<ActionRef> =
        listOf(
            ActionRef(
                id = "${idPrefix}_celebration",
                type = ActionTypes.TRIGGER_FEEDBACK,
                payload =
                    buildJsonObject {
                        put("effect", "celebration")
                        put("title", title)
                        put("subtitle", subtitle)
                        put("duration_ms", CELEBRATION_DURATION_MS)
                    },
            ),
            ActionRef(
                id = "${idPrefix}_toast",
                type = ActionTypes.SHOW_TOAST,
                payload =
                    buildJsonObject {
                        put("message", toastMessage)
                        put("duration_ms", TOAST_DURATION_MS)
                        put("variant", "success")
                    },
            ),
        )

    private companion object {
        const val HTTP_OK = 200
        const val CELEBRATION_DURATION_MS = 2400L
        const val TOAST_DURATION_MS = 2500L
        const val PASS_TOAST_DURATION_MS = 1600L
    }
}
