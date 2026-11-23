package dev.abeta.kmp.sample

import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.FeedbackEffect
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.layout.engine.LayoutUiState
import dev.abeta.kmp.tokens.ThemePreset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val PAY_TRANSFER_AMOUNT_CENTS = 12500
private const val PAY_SETTLEMENT_TIMEOUT_MS = 3000L

/**
 * Metadata describing a BFF-driven vertical showcase app in the Super-App launcher.
 */
data class ShowcaseAppSpec(
    val id: String,
    val name: String,
    val category: String,
    val tenantId: String,
    val entryScreen: String,
)

/**
 * Structured summary emitted by [SampleContainer.runVerticalsHeadlessVerification].
 */
data class VerticalsVerificationSummary(
    val loadedVerticals: Map<String, Int>,
    val swipeCelebrationTriggered: Boolean,
    val expressOrderCelebrationTriggered: Boolean,
    val bookingCelebrationTriggered: Boolean,
    val rfqSubmitted: Boolean,
    val payTransferCelebrationTriggered: Boolean,
)

internal enum class SampleLauncherMode {
    SHOWCASE_APPS,
    KIT_REFERENCE,
}

internal fun launcherModeForScreen(screenId: String): SampleLauncherMode =
    if (SampleShowcaseApps.isVerticalScreen(screenId)) {
        SampleLauncherMode.SHOWCASE_APPS
    } else {
        SampleLauncherMode.KIT_REFERENCE
    }

/**
 * Registry of the seven Abeta showcase vertical apps, their screen routes, and theme presets.
 */
internal object SampleShowcaseApps {
    val allApps: List<ShowcaseAppSpec> =
        listOf(
            ShowcaseAppSpec("abeta-fresh", "Abeta Fresh", "8-Min Grocery", "abeta-fresh", "fresh-home"),
            ShowcaseAppSpec("abeta-bites", "Abeta Bites", "Food Delivery", "abeta-bites", "bites-home"),
            ShowcaseAppSpec("abeta-pulse", "Abeta Pulse", "Events & Movies", "abeta-pulse", "pulse-home"),
            ShowcaseAppSpec("abeta-mart", "Abeta Mart", "Marketplace", "abeta-mart", "mart-home"),
            ShowcaseAppSpec("abeta-spark", "Abeta Spark", "Swipe Discovery", "abeta-spark", "spark-deck"),
            ShowcaseAppSpec("abeta-trade", "Abeta Trade", "B2B Wholesale", "abeta-trade", "trade-home"),
            ShowcaseAppSpec("abeta-pay", "Abeta Pay", "Fintech & Wallet", "abeta-pay", "pay-home"),
        )

    private val screenToAppId: Map<String, String> =
        mapOf(
            "fresh-home" to "abeta-fresh",
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
            "pay-home" to "abeta-pay",
            "pay-transfer" to "abeta-pay",
            "pay-cards" to "abeta-pay",
            "pay-receipt" to "abeta-pay",
        )

    fun normalizeBaseScreenId(screenId: String): String =
        screenId
            .trim()
            .removePrefix("/screens/")
            .removePrefix("/")
            .substringBefore("?")

    fun findAppForScreen(screenId: String): ShowcaseAppSpec? {
        val appId = screenToAppId[normalizeBaseScreenId(screenId)] ?: return null
        return allApps.firstOrNull { it.id == appId }
    }

    fun isVerticalScreen(screenId: String): Boolean = findAppForScreen(screenId) != null

    fun themePresetForScreen(screenId: String): ThemePreset =
        when (findAppForScreen(screenId)?.id) {
            "abeta-fresh", "abeta-trade" -> ThemePreset.EMERALD
            "abeta-bites", "abeta-mart" -> ThemePreset.AMBER
            else -> ThemePreset.INDIGO
        }
}

internal suspend fun verifyVerticalsHeadless(container: SampleContainer): VerticalsVerificationSummary {
    val loadedVerticals = loadAndValidateAllVerticalEntries(container)
    val expressCelebration =
        container.executeVerticalApiAndCheckCelebration(
            screenId = "fresh-home",
            actionId = "headless_fresh_express_order",
            endpoint = "/api/v1/fresh/orders/express",
            body = buildJsonObject { put("vertical", "abeta-fresh") },
        )
    val swipeCelebration =
        container.executeVerticalApiAndCheckCelebration(
            screenId = "spark-deck",
            actionId = "headless_spark_swipe_like",
            endpoint = "/api/v1/spark/swipe",
            body =
                buildJsonObject {
                    put("profile_id", "spark_maya")
                    put("action", "like")
                },
        )
    val bookingCelebration =
        container.executeVerticalApiAndCheckCelebration(
            screenId = "pulse-home",
            actionId = "headless_pulse_booking",
            endpoint = "/api/v1/pulse/bookings",
            body =
                buildJsonObject {
                    put("event_id", "neon_horizon_symphony")
                    put("tier", "arena_gold")
                    put("quantity", 2)
                },
        )
    val rfqSubmitted = container.executeTradeRfqCheck()
    val payTransferCelebration = container.executePayTransferCheck()
    return VerticalsVerificationSummary(
        loadedVerticals = loadedVerticals,
        swipeCelebrationTriggered = swipeCelebration,
        expressOrderCelebrationTriggered = expressCelebration,
        bookingCelebrationTriggered = bookingCelebration,
        rfqSubmitted = rfqSubmitted,
        payTransferCelebrationTriggered = payTransferCelebration,
    )
}

private suspend fun loadAndValidateAllVerticalEntries(container: SampleContainer): Map<String, Int> {
    val loaded = linkedMapOf<String, Int>()
    for (screenId in SampleContainer.VERTICAL_SCREENS) {
        val ready = container.loadReadyScreen(screenId)
        val types =
            ready.page.snippets
                .map { it.snippetType }
                .toSet()
        check(ready.page.snippets.isNotEmpty()) { "Expected non-empty snippets on '$screenId'" }
        check(SnippetTypes.CHIP_GROUP in types) { "Expected '${SnippetTypes.CHIP_GROUP}' on '$screenId'" }
        if (screenId == "spark-deck") {
            check(SnippetTypes.SWIPE_DECK in types) { "Expected '${SnippetTypes.SWIPE_DECK}' on 'spark-deck'" }
        } else {
            check(SnippetTypes.SEARCH_BAR in types) { "Expected '${SnippetTypes.SEARCH_BAR}' on '$screenId'" }
        }
        loaded[screenId] = ready.page.snippets.size
    }
    return loaded
}

private suspend fun SampleContainer.loadReadyScreen(screenId: String): LayoutUiState.Ready {
    navigateTo(screenId, clearBackStack = true).join()
    return controller.uiState.value as? LayoutUiState.Ready
        ?: error("Expected Ready state for '$screenId', got ${controller.uiState.value}")
}

private suspend fun SampleContainer.executeVerticalApiAndCheckCelebration(
    screenId: String,
    actionId: String,
    endpoint: String,
    body: JsonObject,
): Boolean {
    loadReadyScreen(screenId)
    val action =
        ActionRef(
            id = actionId,
            type = ActionTypes.CALL_API,
            payload =
                buildJsonObject {
                    put("endpoint", endpoint)
                    put("method", "POST")
                    put("body", body)
                },
        )
    controller.onAction(action).join()
    val afterState =
        controller.uiState.value as? LayoutUiState.Ready
            ?: error("Expected Ready state after $endpoint on '$screenId'")
    return afterState.activeFeedback?.effect == FeedbackEffect.CELEBRATION
}

private suspend fun SampleContainer.executeTradeRfqCheck(): Boolean {
    loadReadyScreen("trade-home")
    val rfqAction =
        ActionRef(
            id = "headless_trade_rfq",
            type = ActionTypes.CALL_API,
            payload =
                buildJsonObject {
                    put("endpoint", "/api/v1/trade/rfq")
                    put("method", "POST")
                    put(
                        "body",
                        buildJsonObject {
                            put("supplier_id", "apex_corrugated")
                            put("quantity", 500)
                            put("company", "Northstar Retail")
                            put("email", "procurement@northstar.example")
                        },
                    )
                },
        )
    controller.onAction(rfqAction).join()
    val afterState =
        controller.uiState.value as? LayoutUiState.Ready
            ?: error("Expected Ready state after /api/v1/trade/rfq")
    return afterState.activeToast != null || afterState.activeFeedback?.effect == FeedbackEffect.CELEBRATION
}

private suspend fun SampleContainer.executePayTransferCheck(): Boolean {
    loadReadyScreen("pay-transfer")
    val transferAction =
        ActionRef(
            id = "headless_pay_transfer",
            type = ActionTypes.CALL_API,
            payload =
                buildJsonObject {
                    put("endpoint", "/api/v1/pay/transfer")
                    put("method", "POST")
                    put(
                        "body",
                        buildJsonObject {
                            put("vertical", "abeta-pay")
                            put("recipient", "@maya.chen")
                            put("amount_cents", PAY_TRANSFER_AMOUNT_CENTS)
                            put("currency", "USD")
                        },
                    )
                },
        )
    controller.onAction(transferAction).join()
    val settledState =
        withTimeoutOrNull(PAY_SETTLEMENT_TIMEOUT_MS) {
            controller.uiState.first { state ->
                state is LayoutUiState.Ready &&
                    state.page.pageId == "pay-receipt" &&
                    state.activeFeedback?.effect == FeedbackEffect.CELEBRATION
            }
        }
    return settledState != null
}
