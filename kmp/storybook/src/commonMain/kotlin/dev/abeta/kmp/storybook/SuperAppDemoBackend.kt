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
 * Descriptor for each vertical app playable live inside Storybook's 6-in-1 Super-App Demos.
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
 * Canonical catalog of all 6 live Super-App showcase verticals plus the wearable watch glance.
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
            "fresh-order-track" to "abeta-fresh",
            "store-home" to "abeta-fresh",
            "bites-home" to "abeta-bites",
            "bites-restaurant" to "abeta-bites",
            "bites-order-track" to "abeta-bites",
            "pulse-home" to "abeta-pulse",
            "pulse-event-detail" to "abeta-pulse",
            "mart-home" to "abeta-mart",
            "mart-product-detail" to "abeta-mart",
            "spark-deck" to "abeta-spark",
            "spark-matches" to "abeta-spark",
            "trade-home" to "abeta-trade",
            "trade-supplier" to "abeta-trade",
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
        val byApp = apps.firstOrNull { it.id == clean || it.entryScreen == clean }
        return byApp?.entryScreen ?: if (clean in screenToAppId) clean else DEFAULT_SCREEN_ID
    }
}

/**
 * Pure Kotlin Multiplatform in-memory [ScreenRepository] serving all 6 live Super-App verticals,
 * multi-screen subroutes, live `?q=` search filtering, and `?filter=` chip filtering with zero server required.
 */
internal class StorybookDemoScreenRepository : ScreenRepository {
    private val routeFactories: Map<String, (String, String) -> PageResponse> =
        mapOf(
            "fresh-home" to { q, f -> SuperAppDemoCommercePages.freshHome(q, f) },
            "store-home" to { q, f -> SuperAppDemoCommercePages.freshHome(q, f) },
            "home" to { q, f -> SuperAppDemoCommercePages.freshHome(q, f) },
            "fresh-order-track" to { _, _ -> SuperAppDemoCommercePages.freshOrderTrack() },
            "bites-home" to { q, f -> SuperAppDemoCommercePages.bitesHome(q, f) },
            "bites-restaurant" to { _, _ -> SuperAppDemoCommercePages.bitesRestaurant() },
            "bites-order-track" to { _, _ -> SuperAppDemoCommercePages.bitesOrderTrack() },
            "mart-home" to { q, f -> SuperAppDemoCommercePages.martHome(q, f) },
            "catalog" to { q, f -> SuperAppDemoCommercePages.martHome(q, f) },
            "mart-product-detail" to { _, _ -> SuperAppDemoCommercePages.martProductDetail() },
            "pulse-home" to { q, f -> SuperAppDemoDiscoveryPages.pulseHome(q, f) },
            "pulse-event-detail" to { _, _ -> SuperAppDemoDiscoveryPages.pulseEventDetail() },
            "spark-deck" to { _, f -> SuperAppDemoDiscoveryPages.sparkDeck(f.ifEmpty { "nearby" }) },
            "spark-matches" to { _, _ -> SuperAppDemoDiscoveryPages.sparkMatches() },
            "trade-home" to { q, f -> SuperAppDemoDiscoveryPages.tradeHome(q, f) },
            "form-demo" to { q, f -> SuperAppDemoDiscoveryPages.tradeHome(q, f) },
            "trade-supplier" to { _, _ -> SuperAppDemoDiscoveryPages.tradeSupplier() },
            "profile" to { _, _ -> SuperAppDemoDiscoveryPages.tradeSupplier() },
            "containers" to { _, _ -> SuperAppDemoDiscoveryPages.tradeSupplier() },
            "watch-status" to { _, _ -> SuperAppDemoDiscoveryPages.watchStatus() },
        )

    override suspend fun fetchScreen(screenId: String): AppResult<PageResponse> {
        val trimmed = screenId.trim().removePrefix("/screens/").removePrefix("/")
        val base = trimmed.substringBefore("?")
        val params = parseQueryParams(trimmed.substringAfter("?", ""))
        val query = params["q"].orEmpty()
        val filter = params["filter"].orEmpty().ifEmpty { "all" }
        val page = routeFactories[base]?.invoke(query, filter)
        return if (page != null) {
            AppResult.Success(page)
        } else {
            AppResult.Failure(NotFoundError(resource = base, message = "Demo screen '$base' not found."))
        }
    }

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
    }
}

/**
 * Pure Kotlin Multiplatform [ApiCallExecutor] simulating all 6 live vertical checkout, booking,
 * swipe, and RFQ BFF endpoints with confetti celebration overlays and follow-up navigation.
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

    private fun resolveFollowUps(payload: CallApiPayload): List<ActionRef> =
        when (payload.endpoint.substringBefore("?")) {
            "/api/v1/orders/express",
            "/api/v1/fresh/orders/express",
            -> {
                celebrationWithToast(
                    idPrefix = "fresh_order",
                    title = "Express Order Confirmed!",
                    subtitle = "Abeta Fresh rider Aarav K. arriving in 6 minutes",
                    toastMessage = "Order #FRH-8042 placed! Live rider tracker opened.",
                ) + navFollowUp("fresh_nav_track", "fresh-order-track")
            }

            "/api/v1/bites/orders" -> {
                celebrationWithToast(
                    idPrefix = "bites_order",
                    title = "Meal Order Placed!",
                    subtitle = "Saffron Hearth Kitchen is preparing your order",
                    toastMessage = "Order #BTS-4091 placed! Live tracking ready.",
                ) + navFollowUp("bites_nav_track", "bites-order-track")
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
                    toastMessage = "Order Placed! Same-day dispatch confirmed.",
                )
            }

            "/api/v1/spark/swipe" -> {
                sparkFollowUps(payload)
            }

            "/api/v1/trade/rfq" -> {
                celebrationWithToast(
                    idPrefix = "trade_rfq",
                    title = "RFQ Submitted!",
                    subtitle = "Quote request #RFQ-9042 dispatched to GST supplier",
                    toastMessage = "RFQ #RFQ-9042 submitted to supplier!",
                )
            }

            else -> {
                emptyList()
            }
        }

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
            listOf(
                ActionRef(
                    id = "spark_pass_toast",
                    type = ActionTypes.SHOW_TOAST,
                    payload =
                        buildJsonObject {
                            put("message", "Passed — showing next profile")
                            put("duration_ms", PASS_TOAST_MS)
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
                        put("duration_ms", CELEBRATION_MS)
                    },
            ),
            ActionRef(
                id = "${idPrefix}_toast",
                type = ActionTypes.SHOW_TOAST,
                payload =
                    buildJsonObject {
                        put("message", toastMessage)
                        put("duration_ms", TOAST_MS)
                        put("variant", "success")
                    },
            ),
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
