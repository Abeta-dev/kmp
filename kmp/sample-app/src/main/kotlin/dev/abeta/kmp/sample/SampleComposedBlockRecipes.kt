package dev.abeta.kmp.sample

import dev.abeta.kmp.contracts.ButtonSize
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.ComposedBlockSnippetData
import dev.abeta.kmp.contracts.ComposedNode
import dev.abeta.kmp.contracts.ComposedNodeKind
import dev.abeta.kmp.contracts.CountdownAtom
import dev.abeta.kmp.contracts.LayoutConfig
import dev.abeta.kmp.contracts.MotionDurationToken
import dev.abeta.kmp.contracts.MotionLoopEffect
import dev.abeta.kmp.contracts.MotionSpec
import dev.abeta.kmp.contracts.MotionTransition
import dev.abeta.kmp.contracts.ProgressAtom
import dev.abeta.kmp.contracts.ProgressKind
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TypographyTokenRef

private const val LOYALTY_BONUS_SECONDS = 840
private const val NODE_SPACING_DP = 8

/**
 * Server-molded `composed_block` showcase recipes used by `sample-app` store and vertical screens.
 */
@Suppress("MagicNumber")
internal object SampleComposedBlockRecipes {
    private val ui = SampleScreens
    private val fullWidthLayout = LayoutConfig(fillMaxWidth = true)

    fun loyaltyStampComposedBlock(
        snippetId: String,
        blockId: String,
    ): SnippetEnvelope {
        val children =
            listOf(
                loyaltyHeaderRow(blockId),
                textNode("${blockId}_title", "Abeta Fresh Club · Free Express Basket", "title.small"),
                textNode(
                    "${blockId}_sub",
                    "3 of 5 stamps collected — 2 more orders for a free basket.",
                    "body.small",
                    "text.secondary",
                ),
                progressNode("${blockId}_progress", 0.60f, "3 of 5 stamps"),
                loyaltyFooterRow(blockId),
            )
        return cardBlockEnvelope(
            snippetId = snippetId,
            blockId = blockId,
            recipeName = "loyalty_stamp_card",
            children = children,
            enter = MotionTransition.FADE_IN,
        )
    }

    fun tradeMoqSavingsComposedBlock(supplierRoute: String): SnippetEnvelope {
        val children =
            listOf(
                tradeMoqHeaderRow(),
                textNode("trade_moq_title", "Wholesale MOQ Bulk Savings · 500+ Units", "title.small"),
                textNode(
                    "trade_moq_sub",
                    "Pool 500+ cartons to unlock direct factory pricing & GST input credit.",
                    "body.small",
                    "text.secondary",
                ),
                progressNode("trade_moq_progress", 0.75f, "375 / 500 MOQ committed"),
                tradeMoqFooterRow(supplierRoute),
            )
        return cardBlockEnvelope(
            snippetId = "trade_moq_savings_block",
            blockId = "trade_moq_savings",
            recipeName = "moq_bulk_savings_card",
            children = children,
            enter = MotionTransition.SLIDE_UP,
        )
    }

    private fun loyaltyHeaderRow(blockId: String): ComposedNode =
        spaceBetweenRow(
            nodeId = "${blockId}_header_row",
            children =
                listOf(
                    ComposedNode(
                        nodeId = "${blockId}_stamp_tag",
                        kind = ComposedNodeKind.TAG,
                        tag =
                            TagAtom(
                                label = "3 / 5 STAMPS",
                                variant = TagVariant.BRAND,
                                motion = MotionSpec(loopEffect = MotionLoopEffect.PULSE),
                            ),
                    ),
                    ComposedNode(
                        nodeId = "${blockId}_bonus_cd",
                        kind = ComposedNodeKind.COUNTDOWN,
                        countdown =
                            CountdownAtom(
                                countdownId = "${blockId}_reward_cd",
                                remainingSeconds = LOYALTY_BONUS_SECONDS,
                                label = "2× stamps",
                            ),
                    ),
                ),
        )

    private fun loyaltyFooterRow(blockId: String): ComposedNode {
        val rewardToast = ui.toastAction("toast_$blockId", "2 more orders unlock a free basket!", "success")
        val redeemBtn =
            ui.button(
                id = "btn_${blockId}_rewards",
                label = "Redeem Perks",
                onClick = rewardToast,
                variant = ButtonVariant.OUTLINE,
                size = ButtonSize.SMALL,
            )
        return spaceBetweenRow(
            nodeId = "${blockId}_footer_row",
            children =
                listOf(
                    ComposedNode(
                        nodeId = "${blockId}_counter",
                        kind = ComposedNodeKind.ROLLING_COUNTER,
                        counterValue = "3 / 5 stamps",
                        counterTypography = TypographyTokenRef("label.large"),
                        counterColor = ColorTokenRef("brand.primary"),
                    ),
                    ComposedNode(nodeId = "${blockId}_cta", kind = ComposedNodeKind.BUTTON, button = redeemBtn),
                ),
        )
    }

    private fun tradeMoqHeaderRow(): ComposedNode =
        spaceBetweenRow(
            nodeId = "trade_moq_hdr",
            children =
                listOf(
                    ComposedNode(
                        nodeId = "trade_moq_tag",
                        kind = ComposedNodeKind.TAG,
                        tag =
                            TagAtom(
                                label = "BULK TIER · -18%",
                                variant = TagVariant.SUCCESS,
                                motion = MotionSpec(loopEffect = MotionLoopEffect.PULSE),
                            ),
                    ),
                    ComposedNode(
                        nodeId = "trade_moq_price",
                        kind = ComposedNodeKind.ROLLING_COUNTER,
                        counterValue = "\$0.68 / unit",
                        counterTypography = TypographyTokenRef("title.small"),
                        counterColor = ColorTokenRef("brand.primary"),
                    ),
                ),
        )

    private fun tradeMoqFooterRow(supplierRoute: String): ComposedNode {
        val nav = ui.navAction("nav_trade_bulk_quote", supplierRoute)
        val lockBtn = ui.button("btn_trade_lock_bulk", "Lock Bulk Rate", nav, ButtonVariant.PRIMARY, ButtonSize.SMALL)
        return spaceBetweenRow(
            nodeId = "trade_moq_footer",
            children =
                listOf(
                    ComposedNode(
                        nodeId = "trade_moq_gst",
                        kind = ComposedNodeKind.TAG,
                        tag = TagAtom(label = "GST VERIFIED", bgColor = ColorTokenRef("status.info_container")),
                    ),
                    ComposedNode(nodeId = "trade_moq_cta", kind = ComposedNodeKind.BUTTON, button = lockBtn),
                ),
        )
    }

    private fun cardBlockEnvelope(
        snippetId: String,
        blockId: String,
        recipeName: String,
        children: List<ComposedNode>,
        enter: MotionTransition,
    ): SnippetEnvelope {
        val root =
            ComposedNode(
                nodeId = "${blockId}_card",
                kind = ComposedNodeKind.CARD,
                spacingDp = NODE_SPACING_DP,
                layout = ui.cardLayout,
                children =
                    listOf(
                        ComposedNode(
                            nodeId = "${blockId}_col",
                            kind = ComposedNodeKind.COLUMN,
                            spacingDp = NODE_SPACING_DP,
                            layout = fullWidthLayout,
                            children = children,
                        ),
                    ),
            )
        val data = ComposedBlockSnippetData(blockId = blockId, recipeName = recipeName, root = root)
        return SnippetEnvelope(
            snippetId = snippetId,
            snippetType = SnippetTypes.COMPOSED_BLOCK,
            data = ui.encode(data),
            motion = MotionSpec(enter = enter, durationToken = MotionDurationToken.NORMAL),
        )
    }

    private fun spaceBetweenRow(
        nodeId: String,
        children: List<ComposedNode>,
    ): ComposedNode =
        ComposedNode(
            nodeId = nodeId,
            kind = ComposedNodeKind.ROW,
            mainAxisArrangement = "space_between",
            verticalAlignment = "center",
            spacingDp = NODE_SPACING_DP,
            layout = fullWidthLayout,
            children = children,
        )

    private fun textNode(
        nodeId: String,
        value: String,
        typography: String,
        color: String = "text.primary",
    ): ComposedNode =
        ComposedNode(
            nodeId = nodeId,
            kind = ComposedNodeKind.TEXT,
            text = ui.text(value, typography, color),
        )

    private fun progressNode(
        nodeId: String,
        progress: Float,
        label: String,
    ): ComposedNode =
        ComposedNode(
            nodeId = nodeId,
            kind = ComposedNodeKind.PROGRESS,
            progress =
                ProgressAtom(
                    kind = ProgressKind.LINEAR,
                    progress = progress,
                    label = label,
                    color = ColorTokenRef("status.success"),
                ),
            layout = fullWidthLayout,
        )
}
