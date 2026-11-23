package dev.abeta.kmp.storybook

import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ButtonSize
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.ComposedBlockSnippetData
import dev.abeta.kmp.contracts.ComposedNode
import dev.abeta.kmp.contracts.ComposedNodeKind
import dev.abeta.kmp.contracts.CountdownAtom
import dev.abeta.kmp.contracts.FloatingCartSpec
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
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TypographyTokenRef

/**
 * Pure Kotlin Multiplatform builders for Wave 6 nudge bars, marquees, order trackers,
 * molded `composed_block` recipes, and threshold floating cart docks in Storybook demos.
 */
@Suppress("MagicNumber", "LongParameterList")
internal object SuperAppDemoNudgeBuilders {
    private val ui = SuperAppDemoBuilder

    fun marquee(
        snippetId: String,
        stripId: String,
        items: List<MarqueeItem>,
    ): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = snippetId,
            snippetType = SnippetTypes.MARQUEE_STRIP,
            data =
                ui.encode(
                    MarqueeStripSnippetData(
                        stripId = stripId,
                        items = items,
                        velocityDpPerSec = 48,
                        separator = "•",
                        layout = ui.marqueeLayout,
                    ),
                ),
        )

    fun nudgeBar(
        snippetId: String,
        nudgeId: String,
        title: String,
        subtitle: String,
        badgeLabel: String,
        badgeEffect: MotionLoopEffect,
        ctaLabel: String,
        ctaAction: ActionRef,
        countdownSeconds: Int? = null,
        countdownLabel: String? = null,
        progress: Float? = null,
    ): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = snippetId,
            snippetType = SnippetTypes.NUDGE_BAR,
            data =
                ui.encode(
                    NudgeBarSnippetData(
                        nudgeId = nudgeId,
                        title = ui.text(title, "title.small"),
                        subtitle = ui.text(subtitle, "body.small", "text.secondary"),
                        icon = IconAtom(name = "notifications", sizeDp = 20, color = ColorTokenRef("brand.primary")),
                        badge =
                            TagAtom(
                                label = badgeLabel,
                                bgColor = ColorTokenRef("status.warning_container"),
                                motion = MotionSpec(loopEffect = badgeEffect),
                            ),
                        countdown =
                            countdownSeconds?.let { sec ->
                                CountdownAtom(
                                    countdownId = "${nudgeId}_cd",
                                    remainingSeconds = sec,
                                    label = countdownLabel,
                                    urgentThresholdSeconds = 60,
                                )
                            },
                        progress = progress,
                        cta = ui.button("btn_$nudgeId", ctaLabel, ctaAction, ButtonVariant.PRIMARY, ButtonSize.SMALL),
                        dismissible = true,
                        layout = ui.cardLayout,
                    ),
                ),
        )

    fun orderTracker(
        snippetId: String,
        trackerId: String,
        orderCode: String,
        headline: String,
        etaBadge: String,
        etaSeconds: Int,
        riderName: String,
        riderCaption: String,
        steps: List<OrderTrackerStep>,
        primaryLabel: String,
        primaryAction: ActionRef,
        secondaryLabel: String,
        secondaryAction: ActionRef,
    ): SnippetEnvelope {
        val primary = ui.button("btn_${trackerId}_1", primaryLabel, primaryAction, size = ButtonSize.SMALL)
        val secondary =
            ui.button("btn_${trackerId}_2", secondaryLabel, secondaryAction, ButtonVariant.OUTLINE, ButtonSize.SMALL)
        return SnippetEnvelope(
            snippetId = snippetId,
            snippetType = SnippetTypes.ORDER_TRACKER,
            data =
                ui.encode(
                    OrderTrackerSnippetData(
                        trackerId = trackerId,
                        orderCode = orderCode,
                        headline = ui.text(headline, "title.medium"),
                        etaCountdown =
                            CountdownAtom(
                                countdownId = "${trackerId}_eta",
                                remainingSeconds = etaSeconds,
                                label = "Arriving in",
                                urgentThresholdSeconds = 120,
                            ),
                        etaBadge =
                            TagAtom(
                                label = etaBadge,
                                bgColor = ColorTokenRef("status.success_container"),
                                motion = MotionSpec(loopEffect = MotionLoopEffect.PULSE),
                            ),
                        riderName = riderName,
                        riderCaption = riderCaption,
                        steps = steps,
                        primaryAction = primary,
                        secondaryAction = secondary,
                        layout = ui.cardLayout,
                    ),
                ),
        )
    }

    fun moldedCardBlock(
        snippetId: String,
        blockId: String,
        recipeName: String,
        badgeLabel: String,
        badgeVariant: TagVariant,
        trailingNode: ComposedNode,
        title: String,
        subtitle: String,
        progress: Float,
        progressLabel: String,
        footerCounter: String,
        ctaLabel: String,
        ctaAction: ActionRef,
    ): SnippetEnvelope {
        val headerRow =
            moldedCardRow(
                nodeId = "${blockId}_hdr",
                leading = moldedBadgeNode(blockId, badgeLabel, badgeVariant),
                trailing = trailingNode,
            )
        val children =
            listOf(
                headerRow,
                ComposedNode(
                    nodeId = "${blockId}_t",
                    kind = ComposedNodeKind.TEXT,
                    text = ui.text(title, "title.small"),
                ),
                ComposedNode(
                    nodeId = "${blockId}_s",
                    kind = ComposedNodeKind.TEXT,
                    text = ui.text(subtitle, "body.small", "text.secondary"),
                ),
                moldedProgressNode(blockId, progress, progressLabel),
                moldedFooterRow(blockId, footerCounter, ctaLabel, ctaAction),
            )
        val root =
            ComposedNode(
                nodeId = "${blockId}_card",
                kind = ComposedNodeKind.CARD,
                spacingDp = 8,
                layout = ui.cardLayout,
                children =
                    listOf(
                        ComposedNode(
                            nodeId = "${blockId}_col",
                            kind = ComposedNodeKind.COLUMN,
                            spacingDp = 8,
                            layout = ui.fullWidthLayout,
                            children = children,
                        ),
                    ),
            )
        return SnippetEnvelope(
            snippetId = snippetId,
            snippetType = SnippetTypes.COMPOSED_BLOCK,
            data = ui.encode(ComposedBlockSnippetData(blockId = blockId, recipeName = recipeName, root = root)),
        )
    }

    private fun moldedFooterRow(
        blockId: String,
        footerCounter: String,
        ctaLabel: String,
        ctaAction: ActionRef,
    ): ComposedNode =
        moldedCardRow(
            nodeId = "${blockId}_ftr",
            leading =
                ComposedNode(
                    nodeId = "${blockId}_cnt",
                    kind = ComposedNodeKind.ROLLING_COUNTER,
                    counterValue = footerCounter,
                    counterTypography = TypographyTokenRef("label.large"),
                    counterColor = ColorTokenRef("brand.primary"),
                ),
            trailing =
                ComposedNode(
                    nodeId = "${blockId}_btn",
                    kind = ComposedNodeKind.BUTTON,
                    button = ui.button("btn_$blockId", ctaLabel, ctaAction, ButtonVariant.OUTLINE, ButtonSize.SMALL),
                ),
        )

    fun floatingCart(
        label: String,
        eta: String,
        ctaLabel: String,
        action: ActionRef,
        freeDeliveryThresholdCents: Int? = null,
        unlockedLabel: String? = null,
    ): FloatingCartSpec =
        FloatingCartSpec(
            label = label,
            etaBadge =
                TagAtom(
                    label = eta,
                    bgColor = ColorTokenRef("status.success_container"),
                    motion = MotionSpec(loopEffect = MotionLoopEffect.PULSE),
                ),
            cta = ui.button("btn_cart_${action.id}", ctaLabel, action),
            freeDeliveryThresholdCents = freeDeliveryThresholdCents,
            nudgePrefix = if (freeDeliveryThresholdCents != null) "Add" else null,
            unlockedLabel = unlockedLabel,
        )

    private fun moldedBadgeNode(
        blockId: String,
        label: String,
        variant: TagVariant,
    ): ComposedNode =
        ComposedNode(
            nodeId = "${blockId}_badge",
            kind = ComposedNodeKind.TAG,
            tag = TagAtom(label = label, variant = variant, motion = MotionSpec(loopEffect = MotionLoopEffect.PULSE)),
        )

    private fun moldedProgressNode(
        blockId: String,
        progress: Float,
        label: String,
    ): ComposedNode =
        ComposedNode(
            nodeId = "${blockId}_prog",
            kind = ComposedNodeKind.PROGRESS,
            progress =
                ProgressAtom(
                    kind = ProgressKind.LINEAR,
                    progress = progress,
                    label = label,
                    color = ColorTokenRef("status.success"),
                ),
            layout = ui.fullWidthLayout,
        )

    private fun moldedCardRow(
        nodeId: String,
        leading: ComposedNode,
        trailing: ComposedNode,
    ): ComposedNode =
        ComposedNode(
            nodeId = nodeId,
            kind = ComposedNodeKind.ROW,
            mainAxisArrangement = "space_between",
            verticalAlignment = "center",
            spacingDp = 8,
            layout = ui.fullWidthLayout,
            children = listOf(leading, trailing),
        )
}
