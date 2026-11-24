package dev.abeta.kmp.storybook

import dev.abeta.kmp.contracts.ChipAtom
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.ComposedBlockSnippetData
import dev.abeta.kmp.contracts.ComposedNode
import dev.abeta.kmp.contracts.ComposedNodeKind
import dev.abeta.kmp.contracts.CornerRadiusSpec
import dev.abeta.kmp.contracts.ImageAtom
import dev.abeta.kmp.contracts.ImageScaleMode
import dev.abeta.kmp.contracts.LayoutConfig
import dev.abeta.kmp.contracts.MediaPagerAtom
import dev.abeta.kmp.contracts.OptionItem
import dev.abeta.kmp.contracts.ResponsiveColumnsSpec
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.SpacingValues
import dev.abeta.kmp.contracts.SwitchAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TextFieldAtom
import dev.abeta.kmp.contracts.TrackingContract

private const val HERO_PAGER_HEIGHT_DP = 152
private const val HERO_AUTO_ADVANCE_MS = 4500
private const val WARN_CONTAINER = "status.warning_container"

/**
 * Wave A & Wave B Express Quick-Commerce server-molded SDUI sections for **Abeta Fresh**:
 * hero `MediaPagerAtom`, molded product tile grid, `SCROLL_ROW` upsell shelf, and delivery instructions card.
 */
@Suppress("MagicNumber")
internal object SuperAppDemoFreshExpressMolecules {
    private val ui = SuperAppDemoBuilder
    private val builders = SuperAppDemoFreshExpressBuilders
    private val sectionLayout =
        LayoutConfig(
            padding = SpacingValues(horizontal = 16, vertical = 6),
            fillMaxWidth = true,
        )

    fun heroMediaPagerBlock(): SnippetEnvelope {
        val bannerTap =
            ui
                .toastAction("fresh_hero_banner_tap", "Morning Harvest promo unlocked — 25% OFF produce!", "success")
                .copy(
                    tracking =
                        TrackingContract(
                            eventName = "hero_banner_clicked",
                            properties = mapOf("surface" to "fresh-home", "campaign" to "morning_harvest"),
                        ),
                )
        val pager =
            MediaPagerAtom(
                id = "fresh_hero_pager",
                heightDp = HERO_PAGER_HEIGHT_DP,
                autoAdvanceMs = HERO_AUTO_ADVANCE_MS,
                showIndicators = true,
                cornerRadius = CornerRadiusSpec(all = 12),
                onPageClick = bannerTap,
                items = buildHeroSlides(),
            )
        val root =
            ComposedNode(
                nodeId = "fresh_hero_pager_node",
                kind = ComposedNodeKind.COLUMN,
                layout = sectionLayout,
                tracking =
                    TrackingContract(
                        eventName = "node_impression",
                        properties = mapOf("component_id" to "fresh_hero_pager_node", "surface" to "fresh-home"),
                    ),
                children =
                    listOf(
                        ComposedNode(
                            kind = ComposedNodeKind.MEDIA_PAGER,
                            mediaPager = pager,
                        ),
                    ),
            )
        return SnippetEnvelope(
            snippetId = "fresh_hero_media_block",
            snippetType = SnippetTypes.COMPOSED_BLOCK,
            data = ui.encode(ComposedBlockSnippetData("fresh_hero_media", "express_hero_media_pager", root)),
            tracking =
                TrackingContract(
                    eventName = "snippet_impression",
                    properties = mapOf("snippet_id" to "fresh_hero_media_block", "surface" to "fresh-home"),
                ),
        )
    }

    private fun buildHeroSlides(): List<ImageAtom> =
        listOf(
            buildFreshHeroSlide("fresh_hero_slide_1", "hero-harvest.png", "Morning Harvest · 25% OFF"),
            buildFreshHeroSlide("fresh_hero_slide_2", "hero-dairy.png", "Chilled A2 Dairy in 8 Mins"),
            buildFreshHeroSlide("fresh_hero_slide_3", "hero-bakery.png", "Warm Artisan Sourdough"),
        )

    fun expressProductTilesBlock(): SnippetEnvelope {
        val tiles = buildBestsellerSpecs().map { builders.buildExpressProductTile(it, compactForScrollRow = false) }
        val heading =
            builders.buildSectionHeaderRow(
                idPrefix = "fresh_molded_tiles",
                title = "Molded Express Bestsellers",
                badge = TagAtom(label = "BOX OVERLAYS", variant = TagVariant.BRAND),
            )
        val grid =
            ComposedNode(
                nodeId = "fresh_molded_tiles_grid",
                kind = ComposedNodeKind.GRID,
                columns = 2,
                columnsByWidth = ResponsiveColumnsSpec(compact = 2, medium = 3, expanded = 4),
                spacingDp = 10,
                children = tiles,
            )
        val root =
            ComposedNode(
                nodeId = "fresh_molded_tiles_root",
                kind = ComposedNodeKind.COLUMN,
                spacingDp = 10,
                layout = sectionLayout,
                children = listOf(heading, grid),
            )
        return SnippetEnvelope(
            snippetId = "fresh_express_tiles_block",
            snippetType = SnippetTypes.COMPOSED_BLOCK,
            data = ui.encode(ComposedBlockSnippetData("fresh_express_tiles", "express_product_grid", root)),
            tracking =
                TrackingContract(
                    eventName = "snippet_impression",
                    properties = mapOf("snippet_id" to "fresh_express_tiles_block", "surface" to "fresh-home"),
                ),
        )
    }

    private fun buildBestsellerSpecs(): List<ExpressTileSpec> {
        val favAvocado =
            ui
                .toastAction("fav_fresh_avocados", "Saved Organic Hass Avocados to favorites", "success")
                .copy(
                    tracking =
                        TrackingContract(
                            eventName = "product_favorited",
                            properties = mapOf("product_id" to "fresh_avocados", "surface" to "fresh-home"),
                        ),
                )
        return listOf(
            ExpressTileSpec(
                productId = "fresh_avocados",
                title = "Organic Hass Avocados",
                subtitle = "Ready to eat · Orchard lot",
                imageUrl = "https://cdn.abeta.dev/fresh/avocados.png",
                priceText = "\$3.49",
                originalPriceText = "\$4.49",
                discountRibbon = "20% OFF",
                topEndBadge = "♥",
                topEndVariant = TagVariant.ERROR,
                topEndAction = favAvocado,
                stepperFieldId = "cart_qty_fresh_avocados",
                initialQty = 1,
                variantChipLabel = "500g ▼",
                variantSheetAction = buildAvocadoVariantAction(),
            ),
            ExpressTileSpec(
                productId = "fresh_sourdough",
                title = "Artisan Sourdough Loaf",
                subtitle = "Stone-baked · Zero additives",
                imageUrl = "https://cdn.abeta.dev/fresh/sourdough.png",
                priceText = "\$4.99",
                originalPriceText = "\$5.79",
                discountRibbon = "14% OFF",
                topEndBadge = "VEG",
                topEndVariant = TagVariant.SUCCESS,
                stepperFieldId = "cart_qty_fresh_sourdough",
                initialQty = 0,
                variantChipLabel = "450g ▼",
                variantSheetAction = buildSourdoughVariantAction(),
            ),
        )
    }

    fun impulseUpsellShelfBlock(): SnippetEnvelope {
        val cards = buildUpsellSpecs().map { builders.buildExpressProductTile(it, compactForScrollRow = true) }
        val header =
            builders.buildSectionHeaderRow(
                idPrefix = "fresh_upsell",
                title = "Before You Checkout",
                badge = TagAtom(label = "QUICK ADD", bgColor = ColorTokenRef(WARN_CONTAINER)),
            )
        val rail =
            ComposedNode(
                nodeId = "fresh_upsell_scroll_row",
                kind = ComposedNodeKind.SCROLL_ROW,
                spacingDp = 10,
                children = cards,
            )
        val root =
            ComposedNode(
                nodeId = "fresh_upsell_root",
                kind = ComposedNodeKind.COLUMN,
                spacingDp = 8,
                layout = sectionLayout,
                tracking =
                    TrackingContract(
                        eventName = "node_impression",
                        properties = mapOf("component_id" to "fresh_upsell_root", "molecule" to "impulse_upsell_rail"),
                    ),
                children = listOf(header, rail),
            )
        return SnippetEnvelope(
            snippetId = "fresh_upsell_rail",
            snippetType = SnippetTypes.COMPOSED_BLOCK,
            data = ui.encode(ComposedBlockSnippetData("fresh_upsell", "express_impulse_upsell_rail", root)),
            tracking =
                TrackingContract(
                    eventName = "snippet_impression",
                    properties = mapOf("snippet_id" to "fresh_upsell_rail", "molecule" to "impulse_upsell_rail"),
                ),
        )
    }

    private fun buildUpsellSpecs(): List<ExpressTileSpec> =
        listOf(
            ExpressTileSpec(
                productId = "yuzu_water",
                title = "Sparkling Yuzu Water",
                subtitle = "330 ml · Chilled",
                imageUrl = "https://cdn.abeta.dev/fresh/yuzu.png",
                priceText = "\$1.99",
                originalPriceText = "\$2.49",
                discountRibbon = "20% OFF",
                topEndBadge = "VEG",
                topEndVariant = TagVariant.SUCCESS,
                stepperFieldId = "cart_qty_yuzu_water",
                initialQty = 0,
            ),
            ExpressTileSpec(
                productId = "kettle_chips",
                title = "Sea Salt Kettle Chips",
                subtitle = "140 g · Olive oil",
                imageUrl = "https://cdn.abeta.dev/fresh/chips.png",
                priceText = "\$2.99",
                originalPriceText = "\$3.49",
                discountRibbon = "15% OFF",
                topEndBadge = "VEG",
                topEndVariant = TagVariant.SUCCESS,
                stepperFieldId = "cart_qty_kettle_chips",
                initialQty = 0,
            ),
            ExpressTileSpec(
                productId = "cold_pressed_oj",
                title = "Cold-Pressed Orange",
                subtitle = "250 ml · No sugar",
                imageUrl = "https://cdn.abeta.dev/fresh/juice-oj.png",
                priceText = "\$3.29",
                originalPriceText = "\$3.99",
                discountRibbon = "18% OFF",
                topEndBadge = "♥",
                topEndVariant = TagVariant.ERROR,
                stepperFieldId = "cart_qty_cold_pressed_oj",
                initialQty = 0,
            ),
        )

    fun deliveryInstructionsBlock(): SnippetEnvelope {
        val root =
            ComposedNode(
                nodeId = "fresh_delivery_card_root",
                kind = ComposedNodeKind.CARD,
                spacingDp = 10,
                layout = ui.cardLayout,
                tracking =
                    TrackingContract(
                        eventName = "node_impression",
                        properties =
                            mapOf(
                                "component_id" to "fresh_delivery_card_root",
                                "molecule" to "delivery_instructions_card",
                            ),
                    ),
                children = buildDeliveryCardChildren(),
            )
        return SnippetEnvelope(
            snippetId = "fresh_delivery_instructions",
            snippetType = SnippetTypes.COMPOSED_BLOCK,
            data = ui.encode(ComposedBlockSnippetData("fresh_deliv", "express_delivery_instructions", root)),
            tracking =
                TrackingContract(
                    eventName = "snippet_impression",
                    properties =
                        mapOf(
                            "snippet_id" to "fresh_delivery_instructions",
                            "molecule" to "delivery_instructions_card",
                        ),
                ),
        )
    }

    private fun buildDeliveryCardChildren(): List<ComposedNode> {
        val leaveSwitch =
            SwitchAtom(
                fieldId = "fresh_leave_at_door",
                label = "Leave at door",
                subtitle = "Zero-contact doorstep drop with photo confirmation",
                checked = true,
                onChange =
                    ui
                        .saveStateAction("save_fresh_leave_door", "fresh_leave_at_door", "toggled")
                        .copy(
                            tracking =
                                TrackingContract(
                                    eventName = "delivery_pref_toggled",
                                    properties = mapOf("field_id" to "fresh_leave_at_door"),
                                ),
                        ),
            )
        val noteField =
            TextFieldAtom(
                fieldId = "fresh_delivery_note",
                label = "Delivery instructions",
                placeholder = "Ring bell once or leave with lobby desk",
                initialValue = "Leave chilled bag by front porch",
            )
        val titleNode =
            ComposedNode(
                nodeId = "fresh_delivery_title",
                kind = ComposedNodeKind.TEXT,
                text = ui.text("Delivery Instructions", "title.medium"),
            )
        val tipLabelNode =
            ComposedNode(
                nodeId = "fresh_tip_label",
                kind = ComposedNodeKind.TEXT,
                text = ui.text("Tip your delivery partner", "label.large"),
            )
        val tipRowNode =
            ComposedNode(
                nodeId = "fresh_tip_chips_row",
                kind = ComposedNodeKind.FLOW_ROW,
                spacingDp = 8,
                children = buildTipChipNodes(),
            )
        return listOf(
            titleNode,
            ComposedNode("fresh_delivery_switch", ComposedNodeKind.SWITCH, switch = leaveSwitch),
            ComposedNode("fresh_delivery_note_field", ComposedNodeKind.TEXT_FIELD, textField = noteField),
            tipLabelNode,
            tipRowNode,
        )
    }

    private fun buildTipChipNodes(): List<ComposedNode> =
        listOf(
            Triple("tip_1", "\$1", false),
            Triple("tip_2", "\$2", true),
            Triple("tip_3", "\$3", false),
            Triple("tip_5", "\$5", false),
        ).map { (chipId, amount, selected) ->
            val tipToast = ui.toastAction("toast_fresh_$chipId", "Rider tip set to $amount — thank you!", "success")
            val tipAction =
                ui
                    .batchAction(
                        id = "select_fresh_$chipId",
                        actions = listOf(ui.saveStateAction("save_fresh_$chipId", "fresh_rider_tip", amount), tipToast),
                    ).copy(
                        tracking =
                            TrackingContract(
                                eventName = "rider_tip_selected",
                                properties = mapOf("amount" to amount, "surface" to "fresh-home"),
                            ),
                    )
            ComposedNode(
                nodeId = "fresh_$chipId",
                kind = ComposedNodeKind.CHIP,
                chip = ChipAtom(id = "fresh_chip_$chipId", label = amount, selected = selected, onClick = tipAction),
            )
        }

    private fun buildAvocadoVariantAction() =
        builders.buildVariantPickerSheetAction(
            ExpressVariantSheetSpec(
                actionId = "open_fresh_avocado_variants",
                dialogId = "fresh_variant_picker_sheet",
                productId = "fresh_avocados",
                title = "Organic Hass Avocados",
                subtitle = "8-min chilled delivery · Choose pack weight",
                imageUrl = "https://cdn.abeta.dev/fresh/avocados.png",
                priceText = "\$3.49",
                originalPriceText = "\$4.49",
                discountLabel = "20% OFF",
                selectedId = "500g",
                options =
                    listOf(
                        OptionItem(id = "250g", label = "250 g", subtitle = "1 pc · \$1.99"),
                        OptionItem(id = "500g", label = "500 g", subtitle = "2 pcs · \$3.49", badge = "POPULAR"),
                        OptionItem(id = "1kg", label = "1 kg", subtitle = "4 pcs · \$6.29", badge = "SAVE 15%"),
                    ),
            ),
        )

    private fun buildSourdoughVariantAction() =
        builders.buildVariantPickerSheetAction(
            ExpressVariantSheetSpec(
                actionId = "open_fresh_sourdough_variants",
                dialogId = "fresh_sourdough_variant_sheet",
                productId = "fresh_sourdough",
                title = "Artisan Sourdough Loaf",
                subtitle = "Stone-baked daily · Pick loaf cut",
                imageUrl = "https://cdn.abeta.dev/fresh/sourdough.png",
                priceText = "\$4.99",
                originalPriceText = "\$5.79",
                discountLabel = "14% OFF",
                selectedId = "450g_sliced",
                options =
                    listOf(
                        OptionItem("450g_sliced", "450 g · Sliced", "\$4.99", badge = "POPULAR"),
                        OptionItem("450g_whole", "450 g · Whole Boule", "\$4.99"),
                        OptionItem("900g_family", "900 g · Family Loaf", "\$8.49", badge = "SAVE 15%"),
                    ),
            ),
        )
}

private const val HERO_CORNER_RADIUS_DP = 12

private fun buildFreshHeroSlide(
    id: String,
    asset: String,
    desc: String,
): ImageAtom =
    ImageAtom(
        id = id,
        url = "https://cdn.abeta.dev/fresh/$asset",
        contentDescription = desc,
        scaleMode = ImageScaleMode.CROP,
        cornerRadius = CornerRadiusSpec(all = HERO_CORNER_RADIUS_DP),
    )
