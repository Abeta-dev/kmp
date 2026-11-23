package dev.abeta.kmp.storybook

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
import dev.abeta.kmp.tokens.ThemePreset
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Descriptor for each vertical app playable live inside Storybook's 7-in-1 Super-App Demos.
 */
internal data class DemoVerticalApp(
    val id: String,
    val name: String,
    val subtitle: String,
    val entryScreen: String,
    val preset: ThemePreset,
    val isWatch: Boolean = false,
)

/**
 * Canonical catalog of all 7 live Super-App showcase verticals plus the wearable watch glance.
 */
internal object SuperAppDemoCatalog {
    const val DEFAULT_SCREEN_ID = "fresh-home"

    val apps: List<DemoVerticalApp> =
        listOf(
            DemoVerticalApp("abeta-fresh", "Abeta Fresh", "8-Min Grocery", "fresh-home", ThemePreset.EMERALD),
            DemoVerticalApp("abeta-bites", "Abeta Bites", "Food Delivery", "bites-home", ThemePreset.AMBER),
            DemoVerticalApp("abeta-pulse", "Abeta Pulse", "Events & Movies", "pulse-home", ThemePreset.INDIGO),
            DemoVerticalApp("abeta-mart", "Abeta Mart", "Marketplace", "mart-home", ThemePreset.AMBER),
            DemoVerticalApp("abeta-spark", "Abeta Spark", "Swipe Discovery", "spark-deck", ThemePreset.INDIGO),
            DemoVerticalApp("abeta-trade", "Abeta Trade", "B2B Wholesale", "trade-home", ThemePreset.EMERALD),
            DemoVerticalApp("abeta-pay", "Abeta Pay", "Fintech & Wallet", "pay-home", ThemePreset.INDIGO),
            DemoVerticalApp(
                id = "abeta-watch",
                name = "Abeta Watch",
                subtitle = "Wearable Glance",
                entryScreen = "watch-status",
                preset = ThemePreset.EMERALD,
                isWatch = true,
            ),
        )

    private val screenToAppId: Map<String, String> =
        mapOf(
            "fresh-home" to "abeta-fresh",
            "fresh-item-detail" to "abeta-fresh",
            "fresh-cart" to "abeta-fresh",
            "fresh-order-track" to "abeta-fresh",
            "store-home" to "abeta-fresh",
            "bites-home" to "abeta-bites",
            "bites-restaurant" to "abeta-bites",
            "bites-cart" to "abeta-bites",
            "bites-order-track" to "abeta-bites",
            "pulse-home" to "abeta-pulse",
            "pulse-event-detail" to "abeta-pulse",
            "pulse-checkout" to "abeta-pulse",
            "pulse-ticket-pass" to "abeta-pulse",
            "mart-home" to "abeta-mart",
            "mart-product-detail" to "abeta-mart",
            "mart-checkout" to "abeta-mart",
            "mart-order-track" to "abeta-mart",
            "spark-deck" to "abeta-spark",
            "spark-matches" to "abeta-spark",
            "spark-chat" to "abeta-spark",
            "spark-preferences" to "abeta-spark",
            "trade-home" to "abeta-trade",
            "trade-supplier" to "abeta-trade",
            "trade-quotes" to "abeta-trade",
            "trade-po-track" to "abeta-trade",
            "pay-home" to "abeta-pay",
            "pay-transfer" to "abeta-pay",
            "pay-cards" to "abeta-pay",
            "pay-receipt" to "abeta-pay",
            "watch-status" to "abeta-watch",
        )

    fun normalizeRoute(rawRoute: String): String =
        rawRoute
            .trim()
            .removePrefix("/screens/")
            .removePrefix("/")
            .substringBefore("?")

    fun appForScreen(screenId: String): DemoVerticalApp {
        val base = normalizeRoute(screenId)
        val appId = screenToAppId[base] ?: "abeta-fresh"
        return apps.firstOrNull { it.id == appId } ?: apps.first()
    }

    fun resolveInitialScreen(rawAppOrScreen: String?): String {
        val clean = rawAppOrScreen?.trim()?.lowercase().orEmpty()
        if (clean.isEmpty()) return DEFAULT_SCREEN_ID
        val byApp =
            apps.firstOrNull { app ->
                app.id == clean || app.id.removePrefix("abeta-") == clean || app.entryScreen == clean
            }
        return byApp?.entryScreen ?: if (clean in screenToAppId) clean else DEFAULT_SCREEN_ID
    }
}

/**
 * Pure Kotlin Multiplatform in-memory [ScreenRepository] serving all 7 live Super-App verticals,
 * multi-step subroutes, live `?q=` search, `?filter=` chips, `?stage=` trackers, and `?amount=`
 * quick selectors with zero server required.
 */
internal class StorybookDemoScreenRepository : ScreenRepository {
    private val routeFactories: Map<String, (Map<String, String>) -> PageResponse> =
        mapOf(
            "fresh-home" to { p -> SuperAppDemoCommercePages.freshHome(p.q(), p.filter()) },
            "store-home" to { p -> SuperAppDemoCommercePages.freshHome(p.q(), p.filter()) },
            "home" to { p -> SuperAppDemoCommercePages.freshHome(p.q(), p.filter()) },
            "fresh-item-detail" to { _ -> SuperAppDemoFreshWorkflows.freshItemDetail() },
            "fresh-cart" to { p -> SuperAppDemoFreshWorkflows.freshCart(p["slot"].orEmpty(), p["promo"].orEmpty()) },
            "fresh-order-track" to { p -> SuperAppDemoFreshWorkflows.freshOrderTrack(p.stage(DEFAULT_TRACK_STAGE)) },
            "bites-home" to { p -> SuperAppDemoCommercePages.bitesHome(p.q(), p.filter()) },
            "bites-restaurant" to { _ -> SuperAppDemoCommercePages.bitesRestaurant() },
            "bites-cart" to { p -> SuperAppDemoCommerceWorkflows.bitesCart(p["promo"].orEmpty()) },
            "bites-order-track" to { p -> SuperAppDemoCommerceWorkflows.bitesOrderTrack(p.stage(DEFAULT_TRACK_STAGE)) },
            "mart-home" to { p -> SuperAppDemoCommercePages.martHome(p.q(), p.filter()) },
            "catalog" to { p -> SuperAppDemoCommercePages.martHome(p.q(), p.filter()) },
            "mart-product-detail" to { _ -> SuperAppDemoCommercePages.martProductDetail() },
            "mart-checkout" to { p -> SuperAppDemoCommerceWorkflows.martCheckout(p["shipping"].orEmpty()) },
            "mart-order-track" to { p -> SuperAppDemoCommerceWorkflows.martOrderTrack(p.stage(DEFAULT_SECOND_STAGE)) },
            "pulse-home" to { p -> SuperAppDemoDiscoveryPages.pulseHome(p.q(), p.filter()) },
            "pulse-event-detail" to { _ -> SuperAppDemoDiscoveryPages.pulseEventDetail() },
            "pulse-checkout" to { p -> SuperAppDemoDiscoveryWorkflows.pulseCheckout(p["tier"].orEmpty()) },
            "pulse-ticket-pass" to { _ -> SuperAppDemoDiscoveryWorkflows.pulseTicketPass() },
            "spark-deck" to { p -> SuperAppDemoDiscoveryPages.sparkDeck(p["filter"].orEmpty().ifEmpty { "nearby" }) },
            "spark-matches" to { _ -> SuperAppDemoDiscoveryPages.sparkMatches() },
            "spark-chat" to { _ -> SuperAppDemoDiscoveryWorkflows.sparkChat() },
            "spark-preferences" to { _ -> SuperAppDemoDiscoveryWorkflows.sparkPreferences() },
            "trade-home" to { p -> SuperAppDemoDiscoveryPages.tradeHome(p.q(), p.filter()) },
            "form-demo" to { p -> SuperAppDemoDiscoveryPages.tradeHome(p.q(), p.filter()) },
            "trade-supplier" to { _ -> SuperAppDemoDiscoveryPages.tradeSupplier() },
            "trade-quotes" to { _ -> SuperAppDemoDiscoveryWorkflows.tradeQuotes() },
            "trade-po-track" to { p -> SuperAppDemoDiscoveryWorkflows.tradePoTrack(p.stage(DEFAULT_SECOND_STAGE)) },
            "pay-home" to { _ -> SuperAppDemoPayPages.payHome() },
            "pay-transfer" to { p -> SuperAppDemoPayPages.payTransfer(p["amount"].orEmpty()) },
            "pay-cards" to { _ -> SuperAppDemoPayPages.payCards() },
            "pay-receipt" to { p -> SuperAppDemoPayPages.payReceipt(p.stage(DEFAULT_SECOND_STAGE)) },
            "profile" to { _ -> SuperAppDemoDiscoveryPages.tradeSupplier() },
            "containers" to { _ -> SuperAppDemoDiscoveryPages.tradeSupplier() },
            "watch-status" to { _ -> SuperAppDemoDiscoveryPages.watchStatus() },
        )

    override suspend fun fetchScreen(screenId: String): AppResult<PageResponse> {
        val trimmed = screenId.trim().removePrefix("/screens/").removePrefix("/")
        val base = trimmed.substringBefore("?")
        val params = parseQueryParams(trimmed.substringAfter("?", ""))
        val page = routeFactories[base]?.invoke(params)
        return if (page != null) {
            AppResult.Success(page)
        } else {
            AppResult.Failure(NotFoundError(resource = base, message = "Demo screen '$base' not found."))
        }
    }

    private fun Map<String, String>.q(): String = this["q"].orEmpty()

    private fun Map<String, String>.filter(): String = this["filter"].orEmpty().ifEmpty { "all" }

    private fun Map<String, String>.stage(defaultStage: Int): Int = this["stage"]?.toIntOrNull() ?: defaultStage

    private fun parseQueryParams(queryString: String): Map<String, String> {
        if (queryString.isBlank()) return emptyMap()
        return queryString
            .split("&")
            .mapNotNull { pair ->
                val parts = pair.split("=", limit = 2)
                val key = parts.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val rawVal = parts.getOrNull(1).orEmpty().replace("+", " ")
                key to decodePercent(rawVal)
            }.toMap()
    }

    private fun decodePercent(input: String): String {
        val sb = StringBuilder(input.length)
        var i = 0
        while (i < input.length) {
            if (input[i] == '%' && i + 2 < input.length) {
                val hex = input.substring(i + 1, i + PERCENT_TOKEN_LEN).toIntOrNull(HEX_RADIX)
                if (hex != null) {
                    sb.append(hex.toChar())
                    i += PERCENT_TOKEN_LEN
                    continue
                }
            }
            sb.append(input[i])
            i += 1
        }
        return sb.toString()
    }

    private companion object {
        const val HEX_RADIX = 16
        const val PERCENT_TOKEN_LEN = 3
        const val DEFAULT_SECOND_STAGE = 2
        const val DEFAULT_TRACK_STAGE = 3
    }
}

/**
 * Pure Kotlin Multiplatform [ApiCallExecutor] simulating all 7 live vertical checkout, booking,
 * swipe, chat, RFQ, PO, and instant P2P transfer endpoints with celebration overlays and routing.
 */
internal class StorybookDemoApiExecutor : ApiCallExecutor {
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
                        put("demo_mode", true)
                    },
                followUpActions = followUps,
            ),
        )
    }

    private val endpointCelebrations: Map<String, EndpointOutcomeSpec> =
        mapOf(
            "/api/v1/orders/express" to
                EndpointOutcomeSpec(
                    "fresh_order",
                    "Express Order Confirmed!",
                    "Abeta Fresh rider Aarav K. arriving in 6 minutes",
                    "Order #FRH-8042 placed! Live rider tracker opened.",
                    "fresh-order-track?stage=2",
                ),
            "/api/v1/fresh/orders/express" to
                EndpointOutcomeSpec(
                    "fresh_order",
                    "Express Order Confirmed!",
                    "Abeta Fresh rider Aarav K. arriving in 6 minutes",
                    "Order #FRH-8042 placed! Live rider tracker opened.",
                    "fresh-order-track?stage=2",
                ),
            "/api/v1/bites/orders" to
                EndpointOutcomeSpec(
                    "bites_order",
                    "Meal Order Placed!",
                    "Saffron Hearth Kitchen is preparing your order",
                    "Order #BTS-4091 placed! Live tracking ready.",
                    "bites-order-track?stage=2",
                ),
            "/api/v1/mart/orders" to
                EndpointOutcomeSpec(
                    "mart_order",
                    "Mart Order Placed!",
                    "Same-day dispatch scheduled · 10% cashback applied",
                    "Shipment #MRT-5518 confirmed! Tracking opened.",
                    "mart-order-track?stage=2",
                ),
            "/api/v1/pulse/bookings" to
                EndpointOutcomeSpec(
                    "pulse_booking",
                    "QR Pass Issued!",
                    "Pass #PLS-7712 · Gate 4B · Row C Seat 12–13 ready",
                    "Tickets Booked! Digital QR pass opened.",
                    "pulse-ticket-pass",
                ),
            "/api/v1/pulse/checkout" to
                EndpointOutcomeSpec(
                    "pulse_booking",
                    "QR Pass Issued!",
                    "Pass #PLS-7712 · Gate 4B · Row C Seat 12–13 ready",
                    "Tickets Booked! Digital QR pass opened.",
                    "pulse-ticket-pass",
                ),
            "/api/v1/trade/rfq" to
                EndpointOutcomeSpec(
                    "trade_rfq",
                    "RFQ Submitted!",
                    "3 live factory quotes received for RFQ #RFQ-9042",
                    "RFQ #RFQ-9042 submitted — comparing 3 factory bids!",
                    "trade-quotes",
                ),
            "/api/v1/trade/po" to
                EndpointOutcomeSpec(
                    "trade_po",
                    "Purchase Order Issued!",
                    "PO #TRD-9024 confirmed · Live freight tracker active",
                    "PO #TRD-9024 issued! Tracking freight milestones.",
                    "trade-po-track?stage=2",
                ),
            "/api/v1/pay/transfer" to
                EndpointOutcomeSpec(
                    "pay_transfer",
                    "Money Sent Instantly!",
                    "Receipt #PAY-3081 · Zero fee · 5% reward queued",
                    "Instant transfer #PAY-3081 dispatched to @maya.chen!",
                    "pay-receipt?stage=2",
                ),
        )

    private fun resolveFollowUps(payload: CallApiPayload): List<ActionRef> {
        val path = payload.endpoint.substringBefore("?")
        val spec = endpointCelebrations[path]
        return when {
            spec != null -> {
                celebrationWithToast(spec.idPrefix, spec.title, spec.subtitle, spec.toastMessage) +
                    navFollowUp("${spec.idPrefix}_nav", spec.route)
            }

            path == "/api/v1/spark/swipe" -> {
                sparkFollowUps(payload)
            }

            path == "/api/v1/spark/chat" -> {
                listOf(toastFollowUp("spark_chat_sent", "Message sent to Maya Chen!", "success", TOAST_MS))
            }

            else -> {
                emptyList()
            }
        }
    }

    private data class EndpointOutcomeSpec(
        val idPrefix: String,
        val title: String,
        val subtitle: String,
        val toastMessage: String,
        val route: String,
    )

    private fun sparkFollowUps(payload: CallApiPayload): List<ActionRef> {
        val action =
            payload.body
                ?.get("action")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.lowercase()
        return if (action == "like" || action == "super_like") {
            celebrationWithToast(
                idPrefix = "spark_match",
                title = "It's a Match!",
                subtitle = "You both sparked — tap Matches to start chatting",
                toastMessage = "It's a Match!",
            )
        } else {
            listOf(toastFollowUp("spark_pass_toast", "Passed — showing next profile", "info", PASS_TOAST_MS))
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
                        put("duration_ms", CELEBRATION_MS)
                    },
            ),
            toastFollowUp("${idPrefix}_toast", toastMessage, "success", TOAST_MS),
        )

    private fun toastFollowUp(
        id: String,
        message: String,
        variant: String,
        durationMs: Long,
    ): ActionRef =
        ActionRef(
            id = id,
            type = ActionTypes.SHOW_TOAST,
            payload =
                buildJsonObject {
                    put("message", message)
                    put("duration_ms", durationMs)
                    put("variant", variant)
                },
        )

    private fun navFollowUp(
        id: String,
        route: String,
    ): ActionRef = ActionRef(id = id, type = ActionTypes.NAVIGATE, payload = buildJsonObject { put("route", route) })

    private companion object {
        const val HTTP_OK = 200
        const val CELEBRATION_MS = 2400L
        const val TOAST_MS = 2500L
        const val PASS_TOAST_MS = 1500L
    }
}
