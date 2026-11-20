package dev.abeta.kmp.sample

import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.contracts.AccordionSnippetData
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ActionRowSnippetData
import dev.abeta.kmp.contracts.BorderSpec
import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.CheckboxAtom
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.CornerRadiusSpec
import dev.abeta.kmp.contracts.FormSnippetData
import dev.abeta.kmp.contracts.GridSnippetData
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.ImageAtom
import dev.abeta.kmp.contracts.ImageScaleMode
import dev.abeta.kmp.contracts.LayoutConfig
import dev.abeta.kmp.contracts.MotionDurationToken
import dev.abeta.kmp.contracts.MotionSpec
import dev.abeta.kmp.contracts.MotionTransition
import dev.abeta.kmp.contracts.ProductCardSnippetData
import dev.abeta.kmp.contracts.ResponsiveColumnsSpec
import dev.abeta.kmp.contracts.SliderAtom
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.SpacingValues
import dev.abeta.kmp.contracts.StepperAtom
import dev.abeta.kmp.contracts.SwitchAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TrackingContract
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val PRODUCT_IMAGE_HEIGHT_DP = 110
private const val PRODUCT_STAGGER_MS = 60
private const val GRID_COLUMNS = 2
private const val TOAST_DEFAULT_MS = 2000L
private const val BOUNCE_MS = 320L

internal data class FreshProductSpec(
    val id: String,
    val title: String,
    val subtitle: String,
    val price: String,
    val originalPrice: String,
    val unitPriceCents: Int,
    val badge: String,
    val badgeColor: String,
    val rating: String,
    val initialQty: Int,
)

internal val storeCompactCardLayout =
    LayoutConfig(
        padding = SpacingValues(all = 12),
        bgColor = ColorTokenRef("surface.primary"),
        cornerRadius = CornerRadiusSpec(all = 12),
        border = BorderSpec(widthDp = 1f, color = ColorTokenRef("border.subtle")),
        fillMaxWidth = true,
    )

internal object SampleStoreCatalog {
    fun buildProductGrid(
        tabId: String,
        gridId: String,
        products: List<FreshProductSpec>,
    ): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = gridId,
            snippetParentId = "store_category_tabs",
            snippetType = SnippetTypes.GRID,
            data =
                SampleScreens.encode(
                    GridSnippetData(
                        columns = GRID_COLUMNS,
                        columnsByWidth = ResponsiveColumnsSpec(compact = 2, medium = 3, expanded = 4),
                        spacingDp = 12,
                        items = products.mapIndexed { index, spec -> buildProductCard(gridId, index, spec) },
                        layout =
                            LayoutConfig(
                                padding = SpacingValues(horizontal = 8, vertical = 8),
                                fillMaxWidth = true,
                            ),
                    ),
                ),
            tracking =
                TrackingContract(
                    eventName = "category_grid_viewed",
                    properties = mapOf("tab_id" to tabId),
                ),
        )

    fun buildOffersAccordion(): SnippetEnvelope {
        val promiseRow = buildColdChainRow()
        val offersForm = buildOffersFormSnippet()
        return SnippetEnvelope(
            snippetId = "store_offers_accordion",
            snippetType = SnippetTypes.ACCORDION,
            data =
                SampleScreens.encode(
                    AccordionSnippetData(
                        title = SampleScreens.text("Offers & Delivery Promise", "title.medium"),
                        subtitle =
                            SampleScreens.text(
                                "Apply FRESH20 coupon, rider tip & view cold-chain guarantee",
                                "body.small",
                                "text.secondary",
                            ),
                        initiallyExpanded = true,
                        snippets = listOf(promiseRow, offersForm),
                        layout = SampleScreens.cardLayout,
                    ),
                ),
        )
    }

    @Suppress("MagicNumber")
    fun fruitsAndVegProducts(): List<FreshProductSpec> =
        listOf(
            FreshProductSpec(
                id = "avocados",
                title = "Fresh Avocados",
                subtitle = "2 pcs · Ready to eat",
                price = "\$3.49",
                originalPrice = "\$4.49",
                unitPriceCents = 349,
                badge = "BESTSELLER",
                badgeColor = "status.success_container",
                rating = "4.9 ★",
                initialQty = 1,
            ),
            FreshProductSpec(
                id = "strawberries",
                title = "Organic Strawberries",
                subtitle = "250 g · Sweet harvest",
                price = "\$4.99",
                originalPrice = "\$5.99",
                unitPriceCents = 499,
                badge = "ORGANIC",
                badgeColor = "brand.container",
                rating = "4.8 ★",
                initialQty = 0,
            ),
            FreshProductSpec(
                id = "cherry_tomatoes",
                title = "Vine Cherry Tomatoes",
                subtitle = "300 g · Greenhouse",
                price = "\$2.79",
                originalPrice = "\$3.29",
                unitPriceCents = 279,
                badge = "LOCAL",
                badgeColor = "status.info_container",
                rating = "4.7 ★",
                initialQty = 0,
            ),
            FreshProductSpec(
                id = "baby_spinach",
                title = "Crisp Baby Spinach",
                subtitle = "200 g · Triple washed",
                price = "\$2.49",
                originalPrice = "\$2.99",
                unitPriceCents = 249,
                badge = "WASHED",
                badgeColor = "status.success_container",
                rating = "4.8 ★",
                initialQty = 0,
            ),
        )

    @Suppress("MagicNumber")
    fun dairyAndBreadProducts(): List<FreshProductSpec> =
        listOf(
            FreshProductSpec(
                id = "sourdough",
                title = "Artisan Sourdough",
                subtitle = "450 g · Stone-baked",
                price = "\$4.99",
                originalPrice = "\$5.79",
                unitPriceCents = 499,
                badge = "BAKED TODAY",
                badgeColor = "status.warning_container",
                rating = "4.9 ★",
                initialQty = 1,
            ),
            FreshProductSpec(
                id = "whole_milk",
                title = "A2 Farm Whole Milk",
                subtitle = "1 L · Chilled glass",
                price = "\$3.29",
                originalPrice = "\$3.79",
                unitPriceCents = 329,
                badge = "CHILLED",
                badgeColor = "status.info_container",
                rating = "4.9 ★",
                initialQty = 0,
            ),
            FreshProductSpec(
                id = "greek_yogurt",
                title = "Greek Strained Yogurt",
                subtitle = "400 g · 18g protein",
                price = "\$3.99",
                originalPrice = "\$4.49",
                unitPriceCents = 399,
                badge = "HIGH PROTEIN",
                badgeColor = "brand.container",
                rating = "4.8 ★",
                initialQty = 0,
            ),
            FreshProductSpec(
                id = "cultured_butter",
                title = "Sea Salt Cultured Butter",
                subtitle = "200 g · Grass-fed",
                price = "\$4.29",
                originalPrice = "\$4.99",
                unitPriceCents = 429,
                badge = "FARMSTEAD",
                badgeColor = "status.success_container",
                rating = "4.9 ★",
                initialQty = 0,
            ),
        )

    @Suppress("MagicNumber")
    fun snacksAndDrinksProducts(): List<FreshProductSpec> =
        listOf(
            FreshProductSpec(
                id = "cold_brew",
                title = "Oat Milk Cold Brew",
                subtitle = "250 ml · Single origin",
                price = "\$3.79",
                originalPrice = "\$4.29",
                unitPriceCents = 379,
                badge = "CHILLED",
                badgeColor = "status.info_container",
                rating = "4.8 ★",
                initialQty = 0,
            ),
            FreshProductSpec(
                id = "sparkling_yuzu",
                title = "Sparkling Yuzu Water",
                subtitle = "330 ml · Zero sugar",
                price = "\$1.99",
                originalPrice = "\$2.49",
                unitPriceCents = 199,
                badge = "ZERO SUGAR",
                badgeColor = "status.success_container",
                rating = "4.7 ★",
                initialQty = 0,
            ),
            FreshProductSpec(
                id = "sea_salt_chips",
                title = "Kettle Sea Salt Chips",
                subtitle = "140 g · Olive oil",
                price = "\$2.99",
                originalPrice = "\$3.49",
                unitPriceCents = 299,
                badge = "CRUNCHY",
                badgeColor = "status.warning_container",
                rating = "4.8 ★",
                initialQty = 0,
            ),
            FreshProductSpec(
                id = "roasted_almonds",
                title = "Roasted Tamari Almonds",
                subtitle = "180 g · Small batch",
                price = "\$5.49",
                originalPrice = "\$6.49",
                unitPriceCents = 549,
                badge = "ROASTED",
                badgeColor = "brand.container",
                rating = "4.9 ★",
                initialQty = 0,
            ),
        )
}

private fun buildProductCard(
    parentId: String,
    index: Int,
    spec: FreshProductSpec,
): SnippetEnvelope {
    val fieldId = "cart_qty_${spec.id}"
    return SnippetEnvelope(
        snippetId = "prod_${spec.id}",
        snippetParentId = parentId,
        snippetType = SnippetTypes.PRODUCT_CARD,
        data =
            SampleScreens.encode(
                ProductCardSnippetData(
                    productId = spec.id,
                    title = SampleScreens.text(spec.title, "title.small"),
                    subtitle = SampleScreens.text(spec.subtitle, "body.small", "text.secondary"),
                    price = SampleScreens.text(spec.price, "label.large"),
                    originalPrice = SampleScreens.text(spec.originalPrice, "body.small", "text.tertiary"),
                    unitPriceCents = spec.unitPriceCents,
                    badge = TagAtom(label = spec.badge, bgColor = ColorTokenRef(spec.badgeColor)),
                    image =
                        ImageAtom(
                            url = "https://cdn.abeta.dev/fresh/${spec.id}.png",
                            contentDescription = spec.title,
                            heightDp = PRODUCT_IMAGE_HEIGHT_DP,
                            scaleMode = ImageScaleMode.CROP,
                            cornerRadius = CornerRadiusSpec(all = 8),
                        ),
                    stepper =
                        StepperAtom(
                            fieldId = fieldId,
                            label = "ADD",
                            quantity = spec.initialQty,
                            maxQuantity = 10,
                            onIncrement = buildProductIncrementAction(spec, fieldId),
                            onDecrement = buildProductDecrementAction(spec.id, fieldId),
                        ),
                    rating = SampleScreens.text(spec.rating, "label.small", "status.success"),
                    onClick =
                        ActionRef(
                            id = "preview_${spec.id}",
                            type = ActionTypes.SHOW_TOAST,
                            payload =
                                buildJsonObject {
                                    put("message", "${spec.title} (${spec.price}) · Delivered chilled in 8 mins")
                                    put("duration_ms", TOAST_DEFAULT_MS)
                                    put("variant", "info")
                                },
                        ),
                    layout = storeCompactCardLayout,
                ),
            ),
        motion =
            MotionSpec(
                enter = MotionTransition.SCALE_IN,
                durationToken = MotionDurationToken.FAST,
                staggerMs = PRODUCT_STAGGER_MS,
                delayMs = index * PRODUCT_STAGGER_MS,
            ),
    )
}

private fun buildProductIncrementAction(
    spec: FreshProductSpec,
    fieldId: String,
): ActionRef {
    val bouncePayload =
        buildJsonObject {
            put("effect", "cart_bounce")
            put("target_id", "floating_cart_dock")
            put("duration_ms", BOUNCE_MS)
        }
    val trackPayload =
        buildJsonObject {
            put("event_name", "cart_item_incremented")
            put(
                "properties",
                buildJsonObject {
                    put("product_id", spec.id)
                    put("field_id", fieldId)
                    put("unit_price_cents", spec.unitPriceCents)
                },
            )
        }
    val actionsJson =
        buildJsonArray {
            add(
                SampleScreens.encode(
                    ActionRef(
                        id = "bounce_${spec.id}",
                        type = ActionTypes.TRIGGER_FEEDBACK,
                        payload = bouncePayload,
                    ),
                ),
            )
            add(
                SampleScreens.encode(
                    ActionRef(
                        id = "track_inc_${spec.id}",
                        type = ActionTypes.TRACK_EVENT,
                        payload = trackPayload,
                    ),
                ),
            )
        }
    return ActionRef(
        id = "inc_${spec.id}",
        type = ActionTypes.BATCH,
        payload =
            buildJsonObject {
                put("actions", actionsJson)
                put("stop_on_error", true)
            },
    )
}

private fun buildProductDecrementAction(
    productId: String,
    fieldId: String,
): ActionRef =
    ActionRef(
        id = "dec_$productId",
        type = ActionTypes.TRACK_EVENT,
        payload =
            buildJsonObject {
                put("event_name", "cart_item_decremented")
                put(
                    "properties",
                    buildJsonObject {
                        put("product_id", productId)
                        put("field_id", fieldId)
                    },
                )
            },
    )

private fun buildColdChainRow(): SnippetEnvelope =
    SnippetEnvelope(
        snippetId = "store_delivery_promise",
        snippetParentId = "store_offers_accordion",
        snippetType = SnippetTypes.ACTION_ROW,
        data =
            SampleScreens.encode(
                ActionRowSnippetData(
                    title = SampleScreens.text("8-Min Cold-Chain Guarantee", "title.small"),
                    subtitle =
                        SampleScreens.text(
                            "Chilled dispatch from Abeta Fresh Hub · Seal verified",
                            "body.small",
                            "text.secondary",
                        ),
                    leadingIcon =
                        IconAtom(
                            name = "verified",
                            sizeDp = 22,
                            color = ColorTokenRef("status.success"),
                        ),
                    trailingTag =
                        TagAtom(
                            label = "GUARANTEED",
                            bgColor = ColorTokenRef("status.success_container"),
                        ),
                    layout = SampleScreens.cardLayout,
                ),
            ),
    )

@Suppress("MagicNumber")
private fun buildOffersFormSnippet(): SnippetEnvelope =
    SnippetEnvelope(
        snippetId = "store_offers_form",
        snippetParentId = "store_offers_accordion",
        snippetType = SnippetTypes.FORM,
        data =
            SampleScreens.encode(
                FormSnippetData(
                    formId = "fresh_order_prefs",
                    title = SampleScreens.text("Savings & Rider Tip", "title.small"),
                    switches =
                        listOf(
                            SwitchAtom(
                                fieldId = "apply_fresh_coupon",
                                label = "Apply FRESH20 Instant Coupon",
                                subtitle = "Saves \$2.00 on express baskets over \$8",
                                checked = true,
                            ),
                        ),
                    checkboxes =
                        listOf(
                            CheckboxAtom(
                                fieldId = "leave_at_door",
                                label = "Contactless doorstep handoff",
                                subtitle = "Photo confirmation on arrival",
                                checked = true,
                            ),
                        ),
                    sliders =
                        listOf(
                            SliderAtom(
                                fieldId = "rider_tip_dollars",
                                label = "Express Rider Tip",
                                value = 2f,
                                minValue = 0f,
                                maxValue = 10f,
                                steps = 5,
                                unitLabel = "$",
                            ),
                        ),
                    submitButton = buildOffersSubmitButton(),
                    layout = SampleScreens.cardLayout,
                ),
            ),
    )

private fun buildOffersSubmitButton(): ButtonAtom =
    ButtonAtom(
        id = "btn_apply_offers",
        label = "Apply Offers",
        variant = ButtonVariant.SECONDARY,
        onClick =
            ActionRef(
                id = "apply_store_offers",
                type = ActionTypes.SAVE_STATE,
                payload =
                    buildJsonObject {
                        put("key", "fresh_offers_saved")
                        put("value", true)
                        put("scope", "session")
                    },
                secondaryActions =
                    listOf(
                        ActionRef(
                            id = "toast_offers_applied",
                            type = ActionTypes.SHOW_TOAST,
                            payload =
                                buildJsonObject {
                                    put("message", "FRESH20 coupon & rider tip updated")
                                    put("duration_ms", TOAST_DEFAULT_MS)
                                    put("variant", "success")
                                },
                        ),
                    ),
            ),
    )
