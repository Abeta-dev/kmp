package dev.abeta.kmp.storybook

import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.ButtonSize
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.ComposedBlockSnippetData
import dev.abeta.kmp.contracts.ComposedNode
import dev.abeta.kmp.contracts.ComposedNodeKind
import dev.abeta.kmp.contracts.CountdownAtom
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.MarqueeItem
import dev.abeta.kmp.contracts.MarqueeStripSnippetData
import dev.abeta.kmp.contracts.MotionLoopEffect
import dev.abeta.kmp.contracts.MotionSpec
import dev.abeta.kmp.contracts.NudgeBarSnippetData
import dev.abeta.kmp.contracts.OrderTrackerSnippetData
import dev.abeta.kmp.contracts.OrderTrackerStep
import dev.abeta.kmp.contracts.ProgressAtom
import dev.abeta.kmp.contracts.ProgressKind
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.StepperAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TypographyTokenRef
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val NUDGE_COUNTDOWN_SEC = 165
private const val NUDGE_PROGRESS = 0.72f
private const val MARQUEE_VELOCITY_DP = 50
private const val TRACKER_ETA_SEC = 420
private const val COMBO_COUNTDOWN_SEC = 210
private const val COMBO_PROGRESS = 0.84f
private const val COMBO_MAX_QTY = 10
private const val ICON_SM_DP = 18
private const val ROW_SPACING_DP = 8
private const val CARD_SPACING_DP = 10

/**
 * Provides canonical [SnippetEnvelope] fixtures for the 4 quick-commerce & atomic molding snippets
 * (`nudge_bar`, `marquee_strip`, `order_tracker`, `composed_block`).
 */
internal object SnippetsNudgeSamples {
    fun allItems(): List<SnippetSampleItem> =
        listOf(
            SnippetSampleItem("Nudge Bar", buildNudgeBar()),
            SnippetSampleItem("Marquee Strip", buildMarqueeStrip()),
            SnippetSampleItem("Order Tracker", buildOrderTracker()),
            SnippetSampleItem("Composed Block", buildComposedBlock()),
        )

    private fun buildNudgeBar(): SnippetEnvelope {
        val nudge =
            NudgeBarSnippetData(
                nudgeId = "story_free_delivery_nudge",
                title = SnippetsSamples.txt("Add \$4.20 more for FREE express delivery", "title.small"),
                subtitle =
                    SnippetsSamples.txt(
                        "8-minute dispatch locked in while your deal timer is active.",
                        "body.small",
                        "text.secondary",
                    ),
                icon = IconAtom(name = "star", sizeDp = ICON_SM_DP, color = ColorTokenRef("icon.brand")),
                badge = TagAtom(label = "NUDGE", variant = TagVariant.BRAND),
                countdown =
                    CountdownAtom(
                        countdownId = "story_nudge_cd",
                        remainingSeconds = NUDGE_COUNTDOWN_SEC,
                        label = "Ends",
                    ),
                progress = NUDGE_PROGRESS,
                cta =
                    ButtonAtom(
                        label = "Quick Add \$4.99 Loaf",
                        size = ButtonSize.SMALL,
                        onClick = ActionRef(id = "story_nudge_cta", type = ActionTypes.SHOW_TOAST),
                    ),
                dismissible = true,
                pointerEdge = "bottom",
                onDismiss = ActionRef(id = "story_nudge_dismiss", type = ActionTypes.TRACK_EVENT),
                layout = SnippetsSamples.cardLayout,
            )
        return SnippetEnvelope(
            snippetId = "story_nudge_bar",
            snippetType = SnippetTypes.NUDGE_BAR,
            data = SnippetsSamples.encode(nudge),
        )
    }

    private fun buildMarqueeStrip(): SnippetEnvelope {
        val marquee =
            MarqueeStripSnippetData(
                stripId = "story_promo_marquee",
                velocityDpPerSec = MARQUEE_VELOCITY_DP,
                items =
                    listOf(
                        MarqueeItem(
                            id = "story_mq_fresh",
                            text = "20% OFF Organic Produce on Abeta Fresh",
                            icon = IconAtom(name = "star"),
                            badge = TagAtom(label = "8 MIN", variant = TagVariant.SUCCESS),
                            onClick = ActionRef(id = "marquee_fresh_tap", type = ActionTypes.SHOW_TOAST),
                        ),
                        MarqueeItem(
                            id = "story_mq_bites",
                            text = "Zero Delivery Fee Above \$15",
                            badge = TagAtom(label = "HOT", variant = TagVariant.BRAND),
                            onClick = ActionRef(id = "marquee_fee_tap", type = ActionTypes.TRACK_EVENT),
                        ),
                        MarqueeItem(
                            id = "story_mq_trade",
                            text = "Verified GST Bulk Tiers Live on Abeta Trade",
                            icon = IconAtom(name = "check"),
                        ),
                    ),
            )
        return SnippetEnvelope(
            snippetId = "story_marquee_strip",
            snippetType = SnippetTypes.MARQUEE_STRIP,
            data = SnippetsSamples.encode(marquee),
        )
    }

    private fun buildOrderTracker(): SnippetEnvelope {
        val tracker =
            OrderTrackerSnippetData(
                trackerId = "story_live_tracker",
                orderCode = "#AB-4821",
                headline = SnippetsSamples.txt("Rider arriving in 7 minutes", "title.medium"),
                etaCountdown =
                    CountdownAtom(
                        countdownId = "story_tracker_eta",
                        remainingSeconds = TRACKER_ETA_SEC,
                        label = "ETA",
                    ),
                etaBadge = TagAtom(label = "EXPRESS", variant = TagVariant.SUCCESS),
                riderName = "Kabir Sharma",
                riderCaption = "EV Scooter · 4.96★ · 600m away",
                steps =
                    listOf(
                        OrderTrackerStep(id = "confirmed", label = "Confirmed", caption = "14:02", completed = true),
                        OrderTrackerStep(id = "packed", label = "Packed", caption = "14:04", completed = true),
                        OrderTrackerStep(
                            id = "en_route",
                            label = "On the Way",
                            caption = "600m",
                            icon = IconAtom(name = "arrow_forward"),
                            active = true,
                        ),
                        OrderTrackerStep(id = "delivered", label = "Delivered", caption = "ETA 7m"),
                    ),
                primaryAction =
                    ButtonAtom(
                        label = "Call Rider",
                        size = ButtonSize.SMALL,
                        onClick =
                            ActionRef(
                                id = "story_tracker_call",
                                type = ActionTypes.SHOW_TOAST,
                                payload = buildJsonObject { put("message", "Connecting to rider...") },
                            ),
                    ),
                secondaryAction =
                    ButtonAtom(
                        label = "Order Help",
                        variant = ButtonVariant.OUTLINE,
                        size = ButtonSize.SMALL,
                        onClick = ActionRef(id = "story_tracker_help", type = ActionTypes.OPEN_DIALOG),
                    ),
                layout = SnippetsSamples.cardLayout,
            )
        return SnippetEnvelope(
            snippetId = "story_order_tracker",
            snippetType = SnippetTypes.ORDER_TRACKER,
            data = SnippetsSamples.encode(tracker),
        )
    }

    private fun buildComposedBlock(): SnippetEnvelope {
        val block =
            ComposedBlockSnippetData(
                blockId = "story_molded_combo",
                recipeName = "flash_combo_deal_v1",
                root =
                    ComposedNode(
                        nodeId = "story_combo_card",
                        kind = ComposedNodeKind.CARD,
                        spacingDp = CARD_SPACING_DP,
                        layout = SnippetsSamples.cardLayout,
                        children =
                            listOf(
                                buildComboHeaderRow(),
                                ComposedNode(
                                    kind = ComposedNodeKind.TEXT,
                                    text = SnippetsSamples.txt("Morning Brew + Sourdough Bundle", "title.medium"),
                                ),
                                ComposedNode(
                                    kind = ComposedNodeKind.PROGRESS,
                                    progress =
                                        ProgressAtom(
                                            kind = ProgressKind.LINEAR,
                                            progress = COMBO_PROGRESS,
                                            label = "84% claimed today",
                                        ),
                                ),
                                buildComboFooterRow(),
                            ),
                    ),
            )
        return SnippetEnvelope(
            snippetId = "story_composed_block",
            snippetType = SnippetTypes.COMPOSED_BLOCK,
            data = SnippetsSamples.encode(block),
        )
    }

    private fun buildComboHeaderRow(): ComposedNode =
        ComposedNode(
            nodeId = "story_combo_header",
            kind = ComposedNodeKind.ROW,
            spacingDp = ROW_SPACING_DP,
            mainAxisArrangement = "space_between",
            children =
                listOf(
                    ComposedNode(
                        kind = ComposedNodeKind.TAG,
                        tag =
                            TagAtom(
                                label = "MOLDED COMBO",
                                variant = TagVariant.BRAND,
                                motion = MotionSpec(loopEffect = MotionLoopEffect.SHIMMER),
                            ),
                    ),
                    ComposedNode(
                        kind = ComposedNodeKind.COUNTDOWN,
                        countdown =
                            CountdownAtom(
                                countdownId = "story_combo_cd",
                                remainingSeconds = COMBO_COUNTDOWN_SEC,
                                label = "Ends",
                            ),
                    ),
                ),
        )

    private fun buildComboFooterRow(): ComposedNode =
        ComposedNode(
            nodeId = "story_combo_footer",
            kind = ComposedNodeKind.ROW,
            spacingDp = ROW_SPACING_DP,
            mainAxisArrangement = "space_between",
            children =
                listOf(
                    ComposedNode(
                        kind = ComposedNodeKind.ROLLING_COUNTER,
                        counterValue = "\$8.99",
                        counterTypography = TypographyTokenRef("title.large"),
                        counterColor = ColorTokenRef("text.brand"),
                    ),
                    ComposedNode(
                        kind = ComposedNodeKind.STEPPER,
                        stepper =
                            StepperAtom(
                                fieldId = "story_molded_qty",
                                quantity = 1,
                                maxQuantity = COMBO_MAX_QTY,
                            ),
                    ),
                    ComposedNode(
                        kind = ComposedNodeKind.BUTTON,
                        button =
                            ButtonAtom(
                                label = "Grab Combo",
                                size = ButtonSize.SMALL,
                                onClick = ActionRef(id = "story_combo_claim", type = ActionTypes.SHOW_TOAST),
                            ),
                    ),
                ),
        )
}
