package dev.abeta.kmp.storybook

import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.ButtonSize
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.ComposedBlockSnippetData
import dev.abeta.kmp.contracts.ComposedNode
import dev.abeta.kmp.contracts.ComposedNodeKind
import dev.abeta.kmp.contracts.CornerRadiusSpec
import dev.abeta.kmp.contracts.CountdownAtom
import dev.abeta.kmp.contracts.ImageAtom
import dev.abeta.kmp.contracts.ImageScaleMode
import dev.abeta.kmp.contracts.LayoutConfig
import dev.abeta.kmp.contracts.MotionLoopEffect
import dev.abeta.kmp.contracts.MotionSpec
import dev.abeta.kmp.contracts.MotionTransition
import dev.abeta.kmp.contracts.PriceAtom
import dev.abeta.kmp.contracts.ProgressAtom
import dev.abeta.kmp.contracts.ProgressKind
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.StepperAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TrackingContract
import dev.abeta.kmp.contracts.TypographyTokenRef
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val CONTAINER_SPACING_DP = 10
private const val INNER_SPACING_DP = 8
private const val STEPPER_MAX_QTY = 12
private const val FLASH_UNIT_CENTS = 899
private const val B2B_UNIT_CENTS = 4800
private const val EXPRESS_TILE_UNIT_CENTS = 349
private const val EXPRESS_TILE_IMAGE_HEIGHT_DP = 116
private const val EXPRESS_TILE_CORNER_DP = 10
private const val LOYALTY_BASE_STAMPS = 5
private const val LOYALTY_STAMPS_GOAL = 8
private const val RIDER_BASE_ETA_MINS = 4

/**
 * Dynamic SDUI molding builder and pretty-printed [SduiJson] formatter for [SnippetCrafterStory].
 */
internal object SnippetCrafterPresets {
    const val STEPPER_FIELD_ID = "crafter_inline_qty"

    val enterOptions: List<MotionTransition> =
        listOf(
            MotionTransition.NONE,
            MotionTransition.FADE_IN,
            MotionTransition.SLIDE_UP,
            MotionTransition.SPRING_BOUNCE,
        )

    val loopOptions: List<MotionLoopEffect> =
        listOf(
            MotionLoopEffect.NONE,
            MotionLoopEffect.WIGGLE,
            MotionLoopEffect.FLOAT,
            MotionLoopEffect.SHIMMER,
            MotionLoopEffect.HEARTBEAT,
            MotionLoopEffect.GLOW_RING,
        )

    private val prettyJson: Json =
        Json(SduiJson) {
            prettyPrint = true
        }

    fun defaultConfigFor(preset: CrafterPreset): CrafterConfig =
        when (preset) {
            CrafterPreset.FLASH_COMBO -> {
                CrafterConfig(
                    preset = preset,
                    rootContainer = CrafterRootContainer.COLUMN,
                    enterTransition = MotionTransition.SPRING_BOUNCE,
                    loopEffect = MotionLoopEffect.SHIMMER,
                )
            }

            CrafterPreset.LOYALTY_STAMP -> {
                CrafterConfig(
                    preset = preset,
                    rootContainer = CrafterRootContainer.COLUMN,
                    enterTransition = MotionTransition.FADE_IN,
                    loopEffect = MotionLoopEffect.FLOAT,
                    includeCountdown = false,
                )
            }

            CrafterPreset.LIVE_RIDER_ETA -> {
                CrafterConfig(
                    preset = preset,
                    rootContainer = CrafterRootContainer.ROW,
                    enterTransition = MotionTransition.SLIDE_UP,
                    loopEffect = MotionLoopEffect.GLOW_RING,
                    includeInlineStepper = false,
                )
            }

            CrafterPreset.B2B_BULK_QUOTE -> {
                CrafterConfig(
                    preset = preset,
                    rootContainer = CrafterRootContainer.COLUMN,
                    enterTransition = MotionTransition.SLIDE_UP,
                    loopEffect = MotionLoopEffect.NONE,
                )
            }

            CrafterPreset.EXPRESS_PRODUCT_TILE -> {
                CrafterConfig(
                    preset = preset,
                    rootContainer = CrafterRootContainer.COLUMN,
                    enterTransition = MotionTransition.SPRING_BOUNCE,
                    loopEffect = MotionLoopEffect.NONE,
                    includeCountdown = false,
                    includeProgressBar = false,
                )
            }
        }

    fun buildMoldedEnvelope(
        config: CrafterConfig,
        stepperQty: Int,
        motionNonce: Int = 0,
    ): SnippetEnvelope {
        val motion =
            MotionSpec(
                enter = config.enterTransition,
                loopEffect = config.loopEffect,
            ).takeIf { it.enter != MotionTransition.NONE || it.loopEffect != MotionLoopEffect.NONE }

        val infoNodes = buildInfoNodes(config)
        val actionNodes = buildActionNodes(config, stepperQty)
        val rootNode =
            if (config.rootContainer == CrafterRootContainer.ROW) {
                buildRowRootNode(infoNodes, actionNodes, motion, motionNonce)
            } else {
                buildColumnRootNode(infoNodes, actionNodes, motion, motionNonce)
            }

        val block =
            ComposedBlockSnippetData(
                blockId = "crafter_${config.preset.recipeName}",
                recipeName = config.preset.recipeName,
                root =
                    ComposedNode(
                        nodeId = "crafter_card_$motionNonce",
                        kind = ComposedNodeKind.CARD,
                        spacingDp = CONTAINER_SPACING_DP,
                        layout = SnippetsSamples.cardLayout,
                        motion = motion,
                        children = listOf(rootNode),
                    ),
            )
        return SnippetEnvelope(
            snippetId = "molded_${config.preset.recipeName}",
            snippetType = SnippetTypes.COMPOSED_BLOCK,
            data = SnippetsSamples.encode(block),
        )
    }

    fun formatEnvelopeJson(envelope: SnippetEnvelope): String =
        prettyJson.encodeToString(
            serializer = SnippetEnvelope.serializer(),
            value = envelope,
        )

    private fun buildInfoNodes(config: CrafterConfig): List<ComposedNode> =
        buildList {
            if (config.preset == CrafterPreset.EXPRESS_PRODUCT_TILE) {
                add(buildExpressOverlayBoxNode(config))
            } else if (config.includeBadgeTag) {
                add(
                    ComposedNode(
                        nodeId = "crafter_badge",
                        kind = ComposedNodeKind.TAG,
                        tag = TagAtom(label = config.preset.badgeLabel, variant = config.preset.badgeVariant),
                    ),
                )
            }
            add(
                ComposedNode(
                    nodeId = "crafter_title",
                    kind = ComposedNodeKind.TEXT,
                    text = SnippetsSamples.txt(config.preset.title, "title.medium"),
                ),
            )
            add(
                ComposedNode(
                    nodeId = "crafter_subtitle",
                    kind = ComposedNodeKind.TEXT,
                    text = SnippetsSamples.txt(config.preset.subtitle, "body.small", "text.secondary"),
                ),
            )
            if (config.preset == CrafterPreset.EXPRESS_PRODUCT_TILE) {
                add(
                    ComposedNode(
                        nodeId = "crafter_express_price",
                        kind = ComposedNodeKind.PRICE,
                        price =
                            PriceAtom(
                                id = "crafter_avocados_price",
                                amountText = "\$3.49",
                                originalAmountText = "\$4.49",
                                discountLabel = "20% OFF",
                                unitSuffix = "/ 500g",
                                emphasized = true,
                            ),
                    ),
                )
            }
            if (config.includeProgressBar) {
                add(
                    ComposedNode(
                        nodeId = "crafter_progress",
                        kind = ComposedNodeKind.PROGRESS,
                        progress =
                            ProgressAtom(
                                kind = ProgressKind.LINEAR,
                                progress = config.preset.progressFraction,
                                label = config.preset.progressLabel,
                            ),
                    ),
                )
            }
        }

    private fun buildExpressOverlayBoxNode(config: CrafterConfig): ComposedNode {
        val overlayChildren =
            buildList {
                add(buildExpressOverlayImageNode())
                if (config.includeBadgeTag) {
                    add(
                        buildExpressOverlayTag(
                            nodeId = "crafter_overlay_discount",
                            alignment = "top_start",
                            tag = TagAtom(label = config.preset.badgeLabel, variant = config.preset.badgeVariant),
                        ),
                    )
                }
                add(
                    buildExpressOverlayTag(
                        nodeId = "crafter_overlay_veg",
                        alignment = "top_end",
                        tag = TagAtom(label = "VEG", variant = TagVariant.SUCCESS),
                    ),
                )
                add(
                    buildExpressOverlayTag(
                        nodeId = "crafter_overlay_eta",
                        alignment = "bottom_start",
                        tag = TagAtom(label = "8 MINS", bgColor = ColorTokenRef("status.success_container")),
                    ),
                )
            }
        val trackingProps = mapOf("component_id" to "crafter_express_media_box", "molecule" to "express_tile")
        return ComposedNode(
            nodeId = "crafter_express_media_box",
            kind = ComposedNodeKind.BOX,
            layout = LayoutConfig(fillMaxWidth = true),
            tracking = TrackingContract(eventName = "node_impression", properties = trackingProps),
            clickTracking = TrackingContract(eventName = "interaction_click", properties = trackingProps),
            children = overlayChildren,
        )
    }

    private fun buildActionNodes(
        config: CrafterConfig,
        stepperQty: Int,
    ): List<ComposedNode> =
        buildList {
            buildTimingAndCounterNodes(config, stepperQty).forEach(::add)
            buildStepperAndCtaNodes(config, stepperQty).forEach(::add)
        }

    private fun buildTimingAndCounterNodes(
        config: CrafterConfig,
        stepperQty: Int,
    ): List<ComposedNode> =
        buildList {
            if (config.includeCountdown) {
                add(
                    ComposedNode(
                        nodeId = "crafter_countdown",
                        kind = ComposedNodeKind.COUNTDOWN,
                        countdown =
                            CountdownAtom(
                                countdownId = "crafter_cd_${config.preset.name.lowercase()}",
                                remainingSeconds = config.preset.countdownSeconds,
                                label = config.preset.countdownLabel,
                            ),
                    ),
                )
            }
            if (config.includeRollingCounter) {
                add(
                    ComposedNode(
                        nodeId = "crafter_counter",
                        kind = ComposedNodeKind.ROLLING_COUNTER,
                        counterValue = resolveCounterLabel(config.preset, stepperQty),
                        counterTypography = TypographyTokenRef("title.large"),
                        counterColor = ColorTokenRef("text.brand"),
                    ),
                )
            }
        }

    private fun buildStepperAndCtaNodes(
        config: CrafterConfig,
        stepperQty: Int,
    ): List<ComposedNode> =
        buildList {
            if (config.includeInlineStepper) {
                add(
                    ComposedNode(
                        nodeId = "crafter_stepper",
                        kind = ComposedNodeKind.STEPPER,
                        stepper =
                            StepperAtom(
                                fieldId = STEPPER_FIELD_ID,
                                quantity = stepperQty.coerceAtLeast(1),
                                minQuantity = 1,
                                maxQuantity = STEPPER_MAX_QTY,
                                onIncrement = ActionRef(id = "crafter_qty_inc", type = ActionTypes.TRACK_EVENT),
                                onDecrement = ActionRef(id = "crafter_qty_dec", type = ActionTypes.TRACK_EVENT),
                            ),
                    ),
                )
            }
            if (config.includeCtaButton) {
                add(
                    ComposedNode(
                        nodeId = "crafter_cta",
                        kind = ComposedNodeKind.BUTTON,
                        button =
                            ButtonAtom(
                                label = config.preset.ctaLabel,
                                size = ButtonSize.SMALL,
                                onClick =
                                    ActionRef(
                                        id = "crafter_cta_${config.preset.recipeName}",
                                        type = ActionTypes.SHOW_TOAST,
                                        payload = buildJsonObject { put("recipe", config.preset.recipeName) },
                                    ),
                            ),
                    ),
                )
            }
        }

    private fun buildColumnRootNode(
        infoNodes: List<ComposedNode>,
        actionNodes: List<ComposedNode>,
        motion: MotionSpec?,
        motionNonce: Int,
    ): ComposedNode {
        val children =
            buildList {
                addAll(infoNodes)
                if (actionNodes.isNotEmpty()) {
                    add(
                        ComposedNode(
                            nodeId = "crafter_action_flow",
                            kind = ComposedNodeKind.FLOW_ROW,
                            spacingDp = INNER_SPACING_DP,
                            children = actionNodes,
                        ),
                    )
                }
            }
        return ComposedNode(
            nodeId = "crafter_root_col_$motionNonce",
            kind = ComposedNodeKind.COLUMN,
            spacingDp = CONTAINER_SPACING_DP,
            motion = motion,
            children = children,
        )
    }

    private fun buildRowRootNode(
        infoNodes: List<ComposedNode>,
        actionNodes: List<ComposedNode>,
        motion: MotionSpec?,
        motionNonce: Int,
    ): ComposedNode {
        val leftColumn =
            ComposedNode(
                nodeId = "crafter_row_left",
                kind = ComposedNodeKind.COLUMN,
                spacingDp = INNER_SPACING_DP,
                weight = 1f,
                children = infoNodes,
            )
        val children =
            buildList {
                add(leftColumn)
                if (actionNodes.isNotEmpty()) {
                    add(
                        ComposedNode(
                            nodeId = "crafter_row_right",
                            kind = ComposedNodeKind.COLUMN,
                            spacingDp = INNER_SPACING_DP,
                            horizontalAlignment = "end",
                            children = actionNodes,
                        ),
                    )
                }
            }
        return ComposedNode(
            nodeId = "crafter_root_row_$motionNonce",
            kind = ComposedNodeKind.ROW,
            spacingDp = CONTAINER_SPACING_DP,
            mainAxisArrangement = "space_between",
            motion = motion,
            children = children,
        )
    }

    private fun resolveCounterLabel(
        preset: CrafterPreset,
        qty: Int,
    ): String {
        val safeQty = qty.coerceAtLeast(1)
        return when (preset) {
            CrafterPreset.FLASH_COMBO -> {
                AnimationNudgesFixtures.formatUsdCents(FLASH_UNIT_CENTS * safeQty)
            }

            CrafterPreset.LOYALTY_STAMP -> {
                "${(safeQty + LOYALTY_BASE_STAMPS).coerceAtMost(LOYALTY_STAMPS_GOAL)} / $LOYALTY_STAMPS_GOAL"
            }

            CrafterPreset.LIVE_RIDER_ETA -> {
                "${safeQty * 2 + RIDER_BASE_ETA_MINS} mins"
            }

            CrafterPreset.B2B_BULK_QUOTE -> {
                AnimationNudgesFixtures.formatUsdCents(B2B_UNIT_CENTS * safeQty)
            }

            CrafterPreset.EXPRESS_PRODUCT_TILE -> {
                AnimationNudgesFixtures.formatUsdCents(EXPRESS_TILE_UNIT_CENTS * safeQty)
            }
        }
    }
}

private fun buildExpressOverlayImageNode(): ComposedNode =
    ComposedNode(
        nodeId = "crafter_tile_img",
        kind = ComposedNodeKind.IMAGE,
        image =
            ImageAtom(
                id = "crafter_avocados_img",
                url = "https://cdn.abeta.dev/fresh/avocados.png",
                contentDescription = "Organic Hass Avocados",
                heightDp = EXPRESS_TILE_IMAGE_HEIGHT_DP,
                scaleMode = ImageScaleMode.CROP,
                cornerRadius = CornerRadiusSpec(all = EXPRESS_TILE_CORNER_DP),
            ),
    )

private fun buildExpressOverlayTag(
    nodeId: String,
    alignment: String,
    tag: TagAtom,
): ComposedNode =
    ComposedNode(
        nodeId = nodeId,
        kind = ComposedNodeKind.TAG,
        boxAlignment = alignment,
        tag = tag,
    )
