package dev.abeta.kmp.sample

import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.AdaptivePageSpec
import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.ButtonSize
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.CardSnippetData
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.MetricCardSnippetData
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.ProgressAtom
import dev.abeta.kmp.contracts.ProgressKind
import dev.abeta.kmp.contracts.ScreenLayoutMode
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TrackingContract
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val WATCH_ETA_PROGRESS = 0.78f
private const val WATCH_MAX_WIDTH_DP = 280
private const val WATCH_ETA_MINS = 8

internal object SampleWatchScreen {
    fun watchStatus(): PageResponse =
        PageResponse(
            pageId = "watch-status",
            title = "Abeta Watch",
            adaptiveLayout =
                AdaptivePageSpec(
                    layoutMode = ScreenLayoutMode.SINGLE_COLUMN,
                    maxWidthDp = WATCH_MAX_WIDTH_DP,
                ),
            snippets = listOf(buildWatchEtaMetric(), buildWatchOrderCard()),
            tracking =
                TrackingContract(
                    eventName = "watch_screen_viewed",
                    properties = mapOf("page_id" to "watch-status"),
                ),
        )

    private fun buildWatchEtaMetric(): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = "watch_eta_metric",
            snippetType = SnippetTypes.METRIC_CARD,
            data =
                SampleScreens.encode(
                    MetricCardSnippetData(
                        label = SampleScreens.text("Express SLA", "label.small", "text.secondary"),
                        value = SampleScreens.text("8 mins", "title.medium", "brand.primary"),
                        progress =
                            ProgressAtom(
                                kind = ProgressKind.LINEAR,
                                progress = WATCH_ETA_PROGRESS,
                                label = "Arriving in 8 mins",
                                color = ColorTokenRef("brand.primary"),
                            ),
                        onClick =
                            ActionRef(
                                id = "watch_open_docs",
                                type = ActionTypes.OPEN_URL,
                                payload = buildJsonObject { put("url", "https://abeta.dev/docs") },
                            ),
                    ),
                ),
        )

    private fun buildWatchReorderAction(): ActionRef =
        ActionRef(
            id = "watch_express_reorder",
            type = ActionTypes.CALL_API,
            payload =
                buildJsonObject {
                    put("endpoint", "/api/v1/orders/express")
                    put("method", "POST")
                    put(
                        "body",
                        buildJsonObject {
                            put("source", "watch_companion")
                            put("delivery_eta_mins", WATCH_ETA_MINS)
                        },
                    )
                },
            secondaryActions =
                listOf(
                    ActionRef(
                        id = "watch_haptic_confirm",
                        type = ActionTypes.TRIGGER_FEEDBACK,
                        payload = buildJsonObject { put("effect", "haptic_tick") },
                    ),
                ),
        )

    private fun buildWatchOrderCard(): SnippetEnvelope {
        val reorderAction = buildWatchReorderAction()
        val shareAction =
            ActionRef(
                id = "watch_share_eta",
                type = ActionTypes.SHARE,
                payload =
                    buildJsonObject {
                        put("title", "Abeta Fresh Delivery")
                        put("text", "My Abeta Fresh express order arrives in 8 minutes!")
                        put("url", "https://app.abeta.dev/screens/store-home")
                    },
            )
        return SnippetEnvelope(
            snippetId = "watch_active_order",
            snippetType = SnippetTypes.CARD,
            data =
                SampleScreens.encode(
                    CardSnippetData(
                        title = SampleScreens.text("Rider En Route · 8 min", "title.small"),
                        subtitle = SampleScreens.text("Abeta Fresh · Bag #AF-409", "body.small", "text.secondary"),
                        badge = TagAtom(label = "LIVE", variant = TagVariant.SUCCESS),
                        primaryButton =
                            ButtonAtom(
                                label = "1-Tap Reorder",
                                variant = ButtonVariant.PRIMARY,
                                size = ButtonSize.SMALL,
                                onClick = reorderAction,
                            ),
                        secondaryButton =
                            ButtonAtom(
                                label = "Share ETA",
                                variant = ButtonVariant.OUTLINE,
                                size = ButtonSize.SMALL,
                                onClick = shareAction,
                            ),
                    ),
                ),
        )
    }
}
