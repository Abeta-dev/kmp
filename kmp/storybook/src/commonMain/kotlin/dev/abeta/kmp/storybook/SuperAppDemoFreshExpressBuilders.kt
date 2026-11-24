package dev.abeta.kmp.storybook

import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.actions.model.OpenDialogPayload
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.BorderSpec
import dev.abeta.kmp.contracts.ButtonSize
import dev.abeta.kmp.contracts.ChipAtom
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.ComposedBlockSnippetData
import dev.abeta.kmp.contracts.ComposedNode
import dev.abeta.kmp.contracts.ComposedNodeKind
import dev.abeta.kmp.contracts.CornerRadiusSpec
import dev.abeta.kmp.contracts.DividerAtom
import dev.abeta.kmp.contracts.ImageAtom
import dev.abeta.kmp.contracts.ImageScaleMode
import dev.abeta.kmp.contracts.LayoutConfig
import dev.abeta.kmp.contracts.OptionItem
import dev.abeta.kmp.contracts.OverlayPresentation
import dev.abeta.kmp.contracts.PriceAtom
import dev.abeta.kmp.contracts.RadioGroupAtom
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.SpacingValues
import dev.abeta.kmp.contracts.StepperAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TrackingContract
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val TILE_IMAGE_HEIGHT_DP = 104
private const val UPSELL_IMAGE_WIDTH_DP = 156
private const val UPSELL_IMAGE_HEIGHT_DP = 84
private const val SHEET_IMAGE_SIZE_DP = 56
private const val MAX_STEPPER_QTY = 10
private const val OK_CONTAINER = "status.success_container"

internal data class ExpressTileSpec(
    val productId: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    val priceText: String,
    val originalPriceText: String,
    val discountRibbon: String,
    val topEndBadge: String,
    val topEndVariant: TagVariant,
    val topEndAction: ActionRef? = null,
    val etaLabel: String = "8 MINS",
    val stepperFieldId: String,
    val initialQty: Int,
    val variantChipLabel: String? = null,
    val variantSheetAction: ActionRef? = null,
)

internal data class ExpressVariantSheetSpec(
    val actionId: String,
    val dialogId: String,
    val productId: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    val priceText: String,
    val originalPriceText: String,
    val discountLabel: String,
    val selectedId: String,
    val options: List<OptionItem>,
)

/**
 * Reusable Express product tile (`BOX` overlays + `PriceAtom` + `StepperAtom` + telemetry)
 * and Rich SDUI Variant Picker `ModalBottomSheet` builders for [SuperAppDemoFreshExpressMolecules].
 */
@Suppress("MagicNumber")
internal object SuperAppDemoFreshExpressBuilders {
    private val ui = SuperAppDemoBuilder

    private val upsellTileCardLayout =
        LayoutConfig(
            padding = SpacingValues(all = 10),
            bgColor = ColorTokenRef("surface.primary"),
            cornerRadius = CornerRadiusSpec(all = 12),
            border = BorderSpec(widthDp = 1f, color = ColorTokenRef("border.subtle")),
            fillMaxWidth = false,
        )

    fun buildSectionHeaderRow(
        idPrefix: String,
        title: String,
        badge: TagAtom,
    ): ComposedNode =
        ComposedNode(
            nodeId = "${idPrefix}_header",
            kind = ComposedNodeKind.ROW,
            mainAxisArrangement = "space_between",
            verticalAlignment = "center",
            spacingDp = 8,
            layout = ui.fullWidthLayout,
            children =
                listOf(
                    ComposedNode("${idPrefix}_title", ComposedNodeKind.TEXT, text = ui.text(title, "title.medium")),
                    ComposedNode("${idPrefix}_badge", ComposedNodeKind.TAG, tag = badge),
                ),
        )

    fun buildExpressProductTile(
        spec: ExpressTileSpec,
        compactForScrollRow: Boolean,
    ): ComposedNode {
        val prefix = "tile_${spec.productId}"
        val trackingProps =
            mapOf("component_id" to prefix, "product_id" to spec.productId, "molecule" to "express_product_tile")
        val children =
            buildList {
                add(buildTileMediaBox(prefix, spec, compactForScrollRow))
                add(ComposedNode("${prefix}_title", ComposedNodeKind.TEXT, text = ui.text(spec.title, "title.small")))
                val subText = ui.text(spec.subtitle, "body.small", "text.secondary")
                add(ComposedNode("${prefix}_subtitle", ComposedNodeKind.TEXT, text = subText))
                buildVariantChipNode(prefix, spec)?.let(::add)
                add(buildTilePriceNode(prefix, spec))
                add(buildTileStepperNode(prefix, spec))
            }
        return ComposedNode(
            nodeId = prefix,
            kind = ComposedNodeKind.CARD,
            spacingDp = 6,
            layout = if (compactForScrollRow) upsellTileCardLayout else ui.compactCardLayout,
            onClick = ui.toastAction("peek_${spec.productId}", "${spec.title} · ${spec.priceText} (${spec.etaLabel})"),
            tracking = TrackingContract(eventName = "node_impression", properties = trackingProps),
            clickTracking = TrackingContract(eventName = "interaction_click", properties = trackingProps),
            children = children,
        )
    }

    private fun buildTileMediaBox(
        prefix: String,
        spec: ExpressTileSpec,
        compactForScrollRow: Boolean,
    ): ComposedNode {
        val imageAtom =
            ImageAtom(
                id = "${prefix}_img",
                url = spec.imageUrl,
                contentDescription = spec.title,
                widthDp = if (compactForScrollRow) UPSELL_IMAGE_WIDTH_DP else null,
                heightDp = if (compactForScrollRow) UPSELL_IMAGE_HEIGHT_DP else TILE_IMAGE_HEIGHT_DP,
                scaleMode = ImageScaleMode.CROP,
                cornerRadius = CornerRadiusSpec(all = 8),
            )
        val topEndTag = TagAtom(label = spec.topEndBadge, variant = spec.topEndVariant, onClick = spec.topEndAction)
        return ComposedNode(
            nodeId = "${prefix}_media_box",
            kind = ComposedNodeKind.BOX,
            layout = if (compactForScrollRow) null else ui.fullWidthLayout,
            children =
                listOf(
                    ComposedNode(nodeId = "${prefix}_img_node", kind = ComposedNodeKind.IMAGE, image = imageAtom),
                    ComposedNode(
                        nodeId = "${prefix}_discount_ribbon",
                        kind = ComposedNodeKind.TAG,
                        boxAlignment = "top_start",
                        tag = TagAtom(label = spec.discountRibbon, variant = TagVariant.ERROR),
                    ),
                    ComposedNode(
                        nodeId = "${prefix}_top_end_badge",
                        kind = ComposedNodeKind.TAG,
                        boxAlignment = "top_end",
                        tag = topEndTag,
                    ),
                    ComposedNode(
                        nodeId = "${prefix}_eta_pill",
                        kind = ComposedNodeKind.TAG,
                        boxAlignment = "bottom_start",
                        tag = TagAtom(label = spec.etaLabel, bgColor = ColorTokenRef(OK_CONTAINER)),
                    ),
                ),
        )
    }

    private fun buildVariantChipNode(
        prefix: String,
        spec: ExpressTileSpec,
    ): ComposedNode? =
        spec.variantChipLabel?.let { label ->
            spec.variantSheetAction?.let { action ->
                ComposedNode(
                    nodeId = "${prefix}_variant_chip",
                    kind = ComposedNodeKind.CHIP,
                    chip = ChipAtom(id = "${prefix}_variant", label = label, selected = false, onClick = action),
                )
            }
        }

    private fun buildTilePriceNode(
        prefix: String,
        spec: ExpressTileSpec,
    ): ComposedNode =
        ComposedNode(
            nodeId = "${prefix}_price",
            kind = ComposedNodeKind.PRICE,
            price =
                PriceAtom(
                    id = "${prefix}_price_atom",
                    amountText = spec.priceText,
                    originalAmountText = spec.originalPriceText,
                    discountLabel = spec.discountRibbon,
                    emphasized = true,
                ),
        )

    private fun buildTileStepperNode(
        prefix: String,
        spec: ExpressTileSpec,
    ): ComposedNode {
        val incAction =
            ActionRef(
                id = "inc_${spec.productId}",
                type = ActionTypes.TRIGGER_FEEDBACK,
                payload =
                    buildJsonObject {
                        put("effect", "cart_bounce")
                        put("target_id", "floating_cart_dock")
                    },
                tracking =
                    TrackingContract(
                        eventName = "cart_item_incremented",
                        properties = mapOf("product_id" to spec.productId, "surface" to "fresh-home"),
                    ),
            )
        val decAction =
            ActionRef(
                id = "dec_${spec.productId}",
                type = ActionTypes.TRACK_EVENT,
                payload =
                    buildJsonObject {
                        put("event_name", "cart_item_decremented")
                        put("properties", buildJsonObject { put("product_id", spec.productId) })
                    },
            )
        return ComposedNode(
            nodeId = "${prefix}_stepper",
            kind = ComposedNodeKind.STEPPER,
            stepper =
                StepperAtom(
                    fieldId = spec.stepperFieldId,
                    label = "ADD",
                    quantity = spec.initialQty,
                    maxQuantity = MAX_STEPPER_QTY,
                    onIncrement = incAction,
                    onDecrement = decAction,
                ),
        )
    }

    fun buildVariantPickerSheetAction(spec: ExpressVariantSheetSpec): ActionRef {
        val blockId = "${spec.dialogId}_content"
        val sheetRoot =
            ComposedNode(
                nodeId = "${blockId}_root",
                kind = ComposedNodeKind.COLUMN,
                spacingDp = 12,
                children =
                    listOf(
                        buildVariantSheetHeaderRow(blockId, spec),
                        ComposedNode("${blockId}_div", ComposedNodeKind.DIVIDER, divider = DividerAtom()),
                        buildVariantRadioNode(blockId, spec),
                        buildVariantApplyButtonNode(blockId, spec),
                    ),
            )
        val sheetSnippet =
            SnippetEnvelope(
                snippetId = "${spec.dialogId}_snippet",
                snippetType = SnippetTypes.COMPOSED_BLOCK,
                data = ui.encode(ComposedBlockSnippetData(blockId, "express_variant_picker_sheet", sheetRoot)),
                tracking =
                    TrackingContract(
                        eventName = "snippet_impression",
                        properties = mapOf("snippet_id" to "${spec.dialogId}_snippet", "product_id" to spec.productId),
                    ),
            )
        return ui.openDialogAction(
            id = spec.actionId,
            payload =
                OpenDialogPayload(
                    dialogId = spec.dialogId,
                    title = spec.title,
                    message = spec.subtitle,
                    dismissible = true,
                    presentation = OverlayPresentation.BOTTOM_SHEET,
                    snippets = listOf(sheetSnippet),
                    sheetScreenId = "variant-picker-${spec.productId}",
                ),
            tracking =
                TrackingContract(
                    eventName = "variant_picker_opened",
                    properties = mapOf("product_id" to spec.productId, "dialog_id" to spec.dialogId),
                ),
        )
    }

    private fun buildVariantSheetHeaderRow(
        blockId: String,
        spec: ExpressVariantSheetSpec,
    ): ComposedNode {
        val thumb =
            ImageAtom(
                id = "${blockId}_img_atom",
                url = spec.imageUrl,
                contentDescription = spec.title,
                widthDp = SHEET_IMAGE_SIZE_DP,
                heightDp = SHEET_IMAGE_SIZE_DP,
                scaleMode = ImageScaleMode.CROP,
                cornerRadius = CornerRadiusSpec(all = 8),
            )
        val priceAtom =
            PriceAtom(
                id = "${blockId}_price_atom",
                amountText = spec.priceText,
                originalAmountText = spec.originalPriceText,
                discountLabel = spec.discountLabel,
                emphasized = true,
            )
        val etaTag = TagAtom(label = "8 MINS COLD-CHAIN", bgColor = ColorTokenRef(OK_CONTAINER))
        return ComposedNode(
            nodeId = "${blockId}_header_row",
            kind = ComposedNodeKind.ROW,
            spacingDp = 12,
            verticalAlignment = "center",
            children =
                listOf(
                    ComposedNode("${blockId}_img", ComposedNodeKind.IMAGE, image = thumb),
                    ComposedNode(
                        nodeId = "${blockId}_price_col",
                        kind = ComposedNodeKind.COLUMN,
                        spacingDp = 4,
                        weight = 1f,
                        children =
                            listOf(
                                ComposedNode("${blockId}_price", ComposedNodeKind.PRICE, price = priceAtom),
                                ComposedNode("${blockId}_eta", ComposedNodeKind.TAG, tag = etaTag),
                            ),
                    ),
                ),
        )
    }

    private fun buildVariantRadioNode(
        blockId: String,
        spec: ExpressVariantSheetSpec,
    ): ComposedNode =
        ComposedNode(
            nodeId = "${blockId}_radio",
            kind = ComposedNodeKind.RADIO_GROUP,
            radioGroup =
                RadioGroupAtom(
                    fieldId = "variant_${spec.productId}",
                    label = "Choose pack size",
                    options = spec.options,
                    selectedId = spec.selectedId,
                    onChange =
                        ui
                            .saveStateAction("change_variant_${spec.productId}", "variant_sel_${spec.productId}", "1")
                            .copy(
                                tracking =
                                    TrackingContract(
                                        eventName = "variant_option_selected",
                                        properties = mapOf("product_id" to spec.productId),
                                    ),
                            ),
                ),
        )

    private fun buildVariantApplyButtonNode(
        blockId: String,
        spec: ExpressVariantSheetSpec,
    ): ComposedNode {
        val saveAct =
            ui.saveStateAction("save_variant_${spec.productId}", "variant_${spec.productId}", spec.selectedId)
        val toastAct =
            ui.toastAction("toast_variant_${spec.productId}", "Variant applied for ${spec.title}!", "success")
        val dismissAct = ui.dismissOverlayAction("dismiss_${spec.dialogId}", spec.dialogId)
        val applyAction =
            ui
                .batchAction(
                    id = "apply_variant_${spec.productId}",
                    actions = listOf(saveAct, toastAct, dismissAct),
                ).copy(
                    tracking =
                        TrackingContract(
                            eventName = "variant_applied",
                            properties = mapOf("product_id" to spec.productId, "dialog_id" to spec.dialogId),
                        ),
                )
        return ComposedNode(
            nodeId = "${blockId}_apply_btn",
            kind = ComposedNodeKind.BUTTON,
            button =
                ui.button(
                    id = "btn_apply_variant_${spec.productId}",
                    label = "Apply Variant",
                    onClick = applyAction,
                    size = ButtonSize.MEDIUM,
                    fullWidth = true,
                ),
        )
    }
}
