package dev.abeta.kmp.sample

import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ButtonSize
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.ChipGroupSnippetData
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.ComposedBlockSnippetData
import dev.abeta.kmp.contracts.ComposedNode
import dev.abeta.kmp.contracts.ComposedNodeKind
import dev.abeta.kmp.contracts.CornerRadiusSpec
import dev.abeta.kmp.contracts.CountdownAtom
import dev.abeta.kmp.contracts.DividerAtom
import dev.abeta.kmp.contracts.FilterChipItem
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.LayoutConfig
import dev.abeta.kmp.contracts.MarqueeItem
import dev.abeta.kmp.contracts.MarqueeStripSnippetData
import dev.abeta.kmp.contracts.MotionDurationToken
import dev.abeta.kmp.contracts.MotionLoopEffect
import dev.abeta.kmp.contracts.MotionSpec
import dev.abeta.kmp.contracts.MotionTransition
import dev.abeta.kmp.contracts.NudgeBarSnippetData
import dev.abeta.kmp.contracts.OrderTrackerSnippetData
import dev.abeta.kmp.contracts.OrderTrackerStep
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.SpacingValues
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TypographyTokenRef
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal const val PAY_OK_BG = "status.success_container"
internal const val PAY_INFO_BG = "status.info_container"
internal const val PAY_WARN_BG = "status.warning_container"
internal const val PAY_BRAND_BG = "brand.container"

private const val PAY_CASHBACK_SECONDS = 480
private const val PAY_SETTLEMENT_SECONDS = 12
private const val PAY_URGENT_SECONDS = 60
private const val PAY_MARQUEE_VELOCITY_DP = 48
private const val PAY_NODE_SPACING_DP = 8
private const val PAY_ICON_SM = 16
private const val PAY_ICON_MD = 20

private val payFullWidth = LayoutConfig(fillMaxWidth = true)
private val payMarqueeLayout =
    LayoutConfig(
        padding = SpacingValues(horizontal = 12, vertical = 8),
        margin = SpacingValues(horizontal = 16, vertical = 4),
        bgColor = ColorTokenRef("surface.secondary"),
        cornerRadius = CornerRadiusSpec(all = 12),
        fillMaxWidth = true,
    )

/**
 * Reusable Wave 6 primitives (marquee perks, cashback nudge, molded Metal Card, and `#PAY-3081`
 * 4-stage settlement tracker) shared by [SamplePayVertical].
 */
@Suppress("MagicNumber")
internal object SamplePayPrimitives {
    private val ui = SampleScreens

    fun perksMarquee(): SnippetEnvelope {
        val items =
            listOf(
                MarqueeItem(
                    id = "pay_mq_instant",
                    text = "Zero-fee instant P2P transfers",
                    icon = IconAtom(name = "star", sizeDp = PAY_ICON_SM, color = ColorTokenRef("brand.primary")),
                    badge = TagAtom(label = "INSTANT", bgColor = ColorTokenRef(PAY_OK_BG)),
                    onClick = ui.toastAction("tap_pay_instant", "Instant P2P transfers settle in < 2s"),
                ),
                MarqueeItem(
                    id = "pay_mq_cashback",
                    text = "5% cashback on Metal Card",
                    icon = IconAtom(name = "check", sizeDp = PAY_ICON_SM, color = ColorTokenRef("status.success")),
                    badge = TagAtom(label = "5% BACK", bgColor = ColorTokenRef(PAY_BRAND_BG)),
                    onClick = ui.toastAction("tap_pay_cashback", "5% cashback active on Abeta Metal!", "success"),
                ),
                MarqueeItem(
                    id = "pay_mq_fx",
                    text = "0% international FX markup",
                    icon = IconAtom(name = "info", sizeDp = PAY_ICON_SM, color = ColorTokenRef("status.info")),
                    badge = TagAtom(label = "GLOBAL", bgColor = ColorTokenRef(PAY_INFO_BG)),
                ),
            )
        val data =
            MarqueeStripSnippetData(
                stripId = "pay_live_perks",
                items = items,
                velocityDpPerSec = PAY_MARQUEE_VELOCITY_DP,
                separator = "•",
                layout = payMarqueeLayout,
            )
        return SnippetEnvelope(
            snippetId = "pay_perks_marquee",
            snippetType = SnippetTypes.MARQUEE_STRIP,
            data = ui.encode(data),
            motion = MotionSpec(enter = MotionTransition.FADE_IN, durationToken = MotionDurationToken.FAST),
        )
    }

    fun quickChips(): SnippetEnvelope {
        val chips =
            listOf(
                FilterChipItem("all", "Wallet", selected = true, onClick = ui.navAction("chip_pay_all", "pay-home")),
                FilterChipItem("send", "Send Money", onClick = ui.navAction("chip_pay_send", "pay-transfer")),
                FilterChipItem("cards", "Smart Cards", onClick = ui.navAction("chip_pay_cards", "pay-cards")),
                FilterChipItem("bills", "Bill Pay", onClick = ui.navAction("chip_pay_bills", "pay-home?filter=bills")),
                FilterChipItem("receipts", "Receipt #PAY-3081", onClick = ui.navAction("chip_pay_rcpt", "pay-receipt")),
            )
        return SnippetEnvelope(
            snippetId = "pay_quick_chips",
            snippetType = SnippetTypes.CHIP_GROUP,
            data = ui.encode(ChipGroupSnippetData(groupId = "pay_filters", chips = chips, layout = ui.chipRowLayout)),
        )
    }

    fun cashbackNudge(): SnippetEnvelope {
        val badge =
            TagAtom(
                label = "5% CASHBACK",
                bgColor = ColorTokenRef(PAY_WARN_BG),
                motion = MotionSpec(loopEffect = MotionLoopEffect.WIGGLE),
            )
        val countdown =
            CountdownAtom(
                countdownId = "pay_cashback_cd",
                remainingSeconds = PAY_CASHBACK_SECONDS,
                label = "Boost ends in",
                urgentThresholdSeconds = PAY_URGENT_SECONDS,
                motion = MotionSpec(loopEffect = MotionLoopEffect.PULSE),
            )
        val cta =
            ui.button(
                id = "btn_pay_nudge_send",
                label = "Send Now",
                onClick = ui.navAction("nav_pay_nudge_transfer", "pay-transfer"),
                variant = ButtonVariant.PRIMARY,
                size = ButtonSize.SMALL,
            )
        val sub = ui.text("Send P2P or pay utilities before boost closes", "body.small", "text.secondary")
        val icon = IconAtom(name = "notifications", sizeDp = PAY_ICON_MD, color = ColorTokenRef("brand.primary"))
        val data =
            NudgeBarSnippetData(
                nudgeId = "pay_zero_fee_cashback",
                title = ui.text("Zero-Fee 5% Cashback Window", "title.small"),
                subtitle = sub,
                icon = icon,
                badge = badge,
                countdown = countdown,
                progress = 0.72f,
                cta = cta,
                dismissible = true,
                pointerEdge = "bottom",
                onDismiss = ui.toastAction("dismiss_pay_nudge", "Cashback reminder dismissed"),
                layout = ui.cardLayout,
            )
        return SnippetEnvelope(
            snippetId = "pay_cashback_nudge",
            snippetType = SnippetTypes.NUDGE_BAR,
            data = ui.encode(data),
            motion = MotionSpec(enter = MotionTransition.SLIDE_DOWN, durationToken = MotionDurationToken.FAST),
        )
    }

    fun metalVirtualCardBlock(): SnippetEnvelope {
        val root =
            ComposedNode(
                nodeId = "pay_metal_virtual_card_root",
                kind = ComposedNodeKind.CARD,
                spacingDp = PAY_NODE_SPACING_DP,
                layout = ui.cardLayout,
                motion = MotionSpec(loopEffect = MotionLoopEffect.GLOW_RING),
                children =
                    listOf(
                        ComposedNode(
                            nodeId = "pay_metal_col",
                            kind = ComposedNodeKind.COLUMN,
                            spacingDp = PAY_NODE_SPACING_DP,
                            layout = payFullWidth,
                            children = metalCardChildren(),
                        ),
                    ),
            )
        val data = ComposedBlockSnippetData("pay_metal_virtual_card", "metal_virtual_card", root)
        return SnippetEnvelope(
            snippetId = "pay_metal_card_block",
            snippetType = SnippetTypes.COMPOSED_BLOCK,
            data = ui.encode(data),
            motion = MotionSpec(enter = MotionTransition.SLIDE_UP, durationToken = MotionDurationToken.NORMAL),
        )
    }

    fun settlementTracker(shareAction: ActionRef): SnippetEnvelope {
        val etaCountdown =
            CountdownAtom(
                countdownId = "pay_settlement_cd",
                remainingSeconds = PAY_SETTLEMENT_SECONDS,
                label = "Ledger sync",
                urgentThresholdSeconds = 5,
                motion = MotionSpec(loopEffect = MotionLoopEffect.PULSE),
            )
        val etaBadge =
            TagAtom(
                label = "INSTANT",
                bgColor = ColorTokenRef(PAY_OK_BG),
                motion = MotionSpec(loopEffect = MotionLoopEffect.PULSE),
            )
        val primary =
            ui.button("btn_pay_share_receipt", "Share Receipt", shareAction, ButtonVariant.PRIMARY, ButtonSize.SMALL)
        val secondary =
            ui.button(
                id = "btn_pay_back_wallet",
                label = "Back to Wallet",
                onClick = ui.navAction("nav_pay_receipt_home", "pay-home"),
                variant = ButtonVariant.OUTLINE,
                size = ButtonSize.SMALL,
            )
        val data =
            OrderTrackerSnippetData(
                trackerId = "pay_settlement_3081",
                orderCode = "#PAY-3081",
                headline = ui.text("\$125.00 sent to @maya.chen · Instant Settlement", "title.medium"),
                etaCountdown = etaCountdown,
                etaBadge = etaBadge,
                riderName = "Abeta Real-Time Clearing",
                riderCaption = "Ref #PAY-3081 · Zero fee · +\$6.25 (5%) cashback credited",
                steps = settlementSteps(),
                primaryAction = primary,
                secondaryAction = secondary,
                layout = ui.cardLayout,
            )
        return SnippetEnvelope(
            snippetId = "pay_settlement_tracker",
            snippetType = SnippetTypes.ORDER_TRACKER,
            data = ui.encode(data),
            motion = MotionSpec(enter = MotionTransition.SLIDE_UP, durationToken = MotionDurationToken.NORMAL),
        )
    }

    fun saveSessionFlag(
        id: String,
        key: String,
        value: Boolean,
    ): ActionRef =
        ActionRef(
            id = id,
            type = ActionTypes.SAVE_STATE,
            payload =
                buildJsonObject {
                    put("key", key)
                    put("value", value)
                    put("scope", "session")
                },
        )

    fun saveSessionLimit(
        id: String,
        key: String,
        value: Int,
    ): ActionRef =
        ActionRef(
            id = id,
            type = ActionTypes.SAVE_STATE,
            payload =
                buildJsonObject {
                    put("key", key)
                    put("value", value)
                    put("scope", "session")
                },
        )

    private fun metalCardChildren(): List<ComposedNode> =
        listOf(
            metalCardHeaderRow(),
            ComposedNode(
                nodeId = "pay_metal_number",
                kind = ComposedNodeKind.TEXT,
                text = ui.text("••••  ••••  ••••  4092", "title.medium"),
            ),
            ComposedNode(
                nodeId = "pay_metal_balance",
                kind = ComposedNodeKind.ROLLING_COUNTER,
                counterValue = "\$2,480.50",
                counterTypography = TypographyTokenRef("headline.small"),
                counterColor = ColorTokenRef("brand.primary"),
            ),
            ComposedNode(
                nodeId = "pay_metal_holder",
                kind = ComposedNodeKind.TEXT,
                text = ui.text("ALEX RIVERA · EXP 09/29 · CVV •••", "label.medium", "text.secondary"),
            ),
            ComposedNode(
                nodeId = "pay_metal_divider",
                kind = ComposedNodeKind.DIVIDER,
                divider = DividerAtom(thicknessDp = 1f, color = ColorTokenRef("divider.default")),
            ),
            metalCardFooterRow(),
        )

    private fun metalCardHeaderRow(): ComposedNode =
        ComposedNode(
            nodeId = "pay_metal_hdr_row",
            kind = ComposedNodeKind.ROW,
            mainAxisArrangement = "space_between",
            verticalAlignment = "center",
            spacingDp = PAY_NODE_SPACING_DP,
            layout = payFullWidth,
            children =
                listOf(
                    ComposedNode(
                        nodeId = "pay_metal_brand_tag",
                        kind = ComposedNodeKind.TAG,
                        tag =
                            TagAtom(
                                label = "ABETA METAL",
                                bgColor = ColorTokenRef(PAY_BRAND_BG),
                                motion = MotionSpec(loopEffect = MotionLoopEffect.PULSE),
                            ),
                    ),
                    ComposedNode(
                        nodeId = "pay_metal_virtual_tag",
                        kind = ComposedNodeKind.TAG,
                        tag = TagAtom(label = "VIRTUAL · ACTIVE", bgColor = ColorTokenRef(PAY_OK_BG)),
                    ),
                ),
        )

    private fun metalCardFooterRow(): ComposedNode {
        val sendNav = ui.navAction("nav_metal_send", "pay-transfer")
        val sendBtn = ui.button("btn_metal_send", "Send Money", sendNav, size = ButtonSize.SMALL)
        val ctrlBtn =
            ui.button(
                id = "btn_metal_controls",
                label = "Card Controls",
                onClick = ui.navAction("nav_metal_controls", "pay-cards"),
                variant = ButtonVariant.OUTLINE,
                size = ButtonSize.SMALL,
            )
        return ComposedNode(
            nodeId = "pay_metal_footer_row",
            kind = ComposedNodeKind.ROW,
            mainAxisArrangement = "space_between",
            verticalAlignment = "center",
            spacingDp = PAY_NODE_SPACING_DP,
            layout = payFullWidth,
            children =
                listOf(
                    ComposedNode(nodeId = "pay_metal_send_btn", kind = ComposedNodeKind.BUTTON, button = sendBtn),
                    ComposedNode(nodeId = "pay_metal_controls_btn", kind = ComposedNodeKind.BUTTON, button = ctrlBtn),
                ),
        )
    }

    private fun settlementSteps(): List<OrderTrackerStep> =
        listOf(
            OrderTrackerStep(
                id = "step_initiated",
                label = "Initiated",
                caption = "09:42:01",
                icon = IconAtom(name = "check", sizeDp = PAY_ICON_SM, color = ColorTokenRef("status.success")),
                completed = true,
            ),
            OrderTrackerStep(
                id = "step_verified",
                label = "Biometric Verified",
                caption = "09:42:02",
                icon = IconAtom(name = "check", sizeDp = PAY_ICON_SM, color = ColorTokenRef("status.success")),
                completed = true,
            ),
            OrderTrackerStep(
                id = "step_clearing",
                label = "Instant Clearing",
                caption = "09:42:03",
                icon = IconAtom(name = "check", sizeDp = PAY_ICON_SM, color = ColorTokenRef("status.success")),
                completed = true,
            ),
            OrderTrackerStep(
                id = "step_settled",
                label = "Settled",
                caption = "Confirmed",
                icon = IconAtom(name = "star", sizeDp = PAY_ICON_SM, color = ColorTokenRef("text.inverse")),
                active = true,
            ),
        )
}
