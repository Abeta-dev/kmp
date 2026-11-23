package dev.abeta.kmp.storybook

import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.BottomBarSpec
import dev.abeta.kmp.contracts.ButtonSize
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.CardSnippetData
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.ComposedBlockSnippetData
import dev.abeta.kmp.contracts.ComposedNode
import dev.abeta.kmp.contracts.ComposedNodeKind
import dev.abeta.kmp.contracts.CornerRadiusSpec
import dev.abeta.kmp.contracts.FormSnippetData
import dev.abeta.kmp.contracts.ImageAtom
import dev.abeta.kmp.contracts.ImageScaleMode
import dev.abeta.kmp.contracts.KeyboardKind
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.StepperAtom
import dev.abeta.kmp.contracts.SwitchAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TextFieldAtom
import dev.abeta.kmp.contracts.TypographyTokenRef
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val FRESH_OK = "status.success_container"

private data class FreshStageCopy(
    val headline: String,
    val badge: String,
    val etaSeconds: Int,
)

/**
 * Multi-step Abeta Fresh workflow sub-screens (item detail, express checkout review, and
 * stage-cycling live rider tracker #FRH-8042) for Storybook.
 */
@Suppress("MagicNumber", "TooManyFunctions")
internal object SuperAppDemoFreshWorkflows {
    private val ui = SuperAppDemoBuilder
    private val nudges = SuperAppDemoNudgeBuilders

    fun freshItemDetail(): PageResponse {
        val toCart = ui.navAction("fresh_detail_to_cart", "fresh-cart")
        val share =
            ui.shareAction(
                "share_fresh_avo",
                "Hass Avocados · Abeta Fresh",
                "Organic Hass Avocados delivered in 8 mins for \$3.49!",
                "https://abeta.dev/fresh/hass-avocados",
            )
        val guaranteeRows =
            listOf(
                "8-Minute Cold-Chain Guarantee" to "Packed at 4°C · Instant replacement if bruised",
                "Single-Origin Traceability" to "Harvested 14h ago · Valley Orchard Lot #VO-219",
                "Nutrition per 100 g" to "160 kcal · 15 g healthy fats · 7 g fiber · 485 mg potassium",
            ).mapIndexed { i, (k, v) -> ui.actionRow("fresh_nut_$i", k, v, "check" to "status.success") }
        val originMetric =
            ui.metric("fresh_origin_metric", "Orchard Origin", "14h Harvest", "ORGANIC", "4°C chilled dispatch")
        val guaranteeAcc =
            ui.accordion("fresh_guarantee_acc", "Freshness & Nutrition Facts", "Guarantee & macros", guaranteeRows)
        val bottomHelper = ui.text("8-min express hub · Free delivery above \$15", "label.small", "text.secondary")
        return PageResponse(
            pageId = "fresh-item-detail",
            title = "Hass Avocados (2 pcs)",
            toolbar = ui.toolbar("Hass Avocados", "Abeta Fresh · Ready to Eat", "back_fresh_detail"),
            snippets =
                listOf(
                    buildFreshDetailHero(toCart, share),
                    buildFreshQuantityBlock(toCart),
                    originMetric,
                    guaranteeAcc,
                ),
            bottomBar =
                BottomBarSpec(
                    primaryButton = ui.button("btn_fresh_detail_cart", "Add & Review Cart", toCart, fullWidth = true),
                    helperText = bottomHelper,
                ),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    private fun buildFreshDetailHero(
        toCart: ActionRef,
        share: ActionRef,
    ): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = "fresh_detail_hero",
            snippetType = SnippetTypes.CARD,
            data =
                ui.encode(
                    CardSnippetData(
                        title = ui.text("Organic Hass Avocados · 2 pcs", "headline.small"),
                        subtitle = ui.text("\$3.49 · 4.9 ★ (640 ratings)", "title.medium", "text.brand"),
                        description =
                            ui.text(
                                "Creamy ripe avocados hand-picked from Valley Orchard for 8-min delivery.",
                                "body.medium",
                                "text.secondary",
                            ),
                        media =
                            ImageAtom(
                                url = "https://cdn.abeta.dev/catalog/fresh_avocados.png",
                                contentDescription = "Hass Avocados",
                                heightDp = 156,
                                scaleMode = ImageScaleMode.CROP,
                                cornerRadius = CornerRadiusSpec(all = 12),
                                placeholderColor = ColorTokenRef(FRESH_OK),
                            ),
                        badge = TagAtom(label = "8 MINS", bgColor = ColorTokenRef(FRESH_OK)),
                        primaryButton = ui.button("btn_fresh_hero_cart", "Go to Cart", toCart),
                        secondaryButton = ui.button("btn_fresh_hero_share", "Share", share, ButtonVariant.OUTLINE),
                        layout = ui.cardLayout,
                    ),
                ),
        )

    private fun buildFreshQuantityBlock(toCart: ActionRef): SnippetEnvelope {
        val row =
            ComposedNode(
                nodeId = "fresh_qty_row",
                kind = ComposedNodeKind.ROW,
                mainAxisArrangement = "space_between",
                verticalAlignment = "center",
                layout = ui.fullWidthLayout,
                children =
                    listOf(
                        ComposedNode(
                            nodeId = "fresh_qty_stepper",
                            kind = ComposedNodeKind.STEPPER,
                            stepper = StepperAtom("cart_qty_fresh_avocados", "ADD", quantity = 1, maxQuantity = 10),
                        ),
                        ComposedNode(
                            nodeId = "fresh_qty_price",
                            kind = ComposedNodeKind.ROLLING_COUNTER,
                            counterValue = "\$3.49",
                            counterTypography = TypographyTokenRef("title.medium"),
                            counterColor = ColorTokenRef("brand.primary"),
                        ),
                        ComposedNode(
                            nodeId = "fresh_qty_cta",
                            kind = ComposedNodeKind.BUTTON,
                            button = ui.button("btn_fresh_qty_cart", "View Cart", toCart, size = ButtonSize.SMALL),
                        ),
                    ),
            )
        val root =
            ComposedNode(
                nodeId = "fresh_qty_card",
                kind = ComposedNodeKind.CARD,
                layout = ui.cardLayout,
                children = listOf(row),
            )
        return SnippetEnvelope(
            snippetId = "fresh_qty_composed",
            snippetType = SnippetTypes.COMPOSED_BLOCK,
            data = ui.encode(ComposedBlockSnippetData("fresh_qty_block", "item_stepper_bar", root)),
        )
    }

    fun freshCart(
        slot: String = "express",
        promo: String = "",
    ): PageResponse {
        val activeSlot = if (slot == "scheduled") "scheduled" else "express"
        val promoApplied = promo.equals("FRESH20", ignoreCase = true)
        val slots = listOf("express" to "8-min Express (Free)", "scheduled" to "Scheduled 6 PM")
        val payAction =
            ui.apiCallAction(
                "fresh_pay_express",
                "/api/v1/fresh/orders/express",
                buildJsonObject {
                    put("vertical", "abeta-fresh")
                    put("slot", activeSlot)
                    put("promo", if (promoApplied) "FRESH20" else "")
                },
            )
        val payLabel = if (promoApplied) "Pay & Place Express Order · \$7.42" else "Pay & Place Express Order · \$8.78"
        val slotChips = ui.chipGroup("fresh_slot_chips", "fresh_slots", "fresh-cart", slots, activeSlot, "slot")
        val helper = ui.text("Includes \$2.00 rider tip · Cold-chain guaranteed", "label.small", "text.secondary")
        val pageActions =
            if (promoApplied) {
                listOf(ui.toastAction("fresh_promo_toast", "Promo FRESH20 applied — saved \$1.36!", "success"))
            } else {
                emptyList()
            }
        return PageResponse(
            pageId = "fresh-cart",
            title = "Express Checkout Review",
            toolbar = ui.toolbar("Express Cart", "Abeta Fresh · 2 items ready", "back_fresh_cart"),
            snippets =
                listOf(
                    ui.header("fresh_cart_hdr", "Delivery Slot & Basket", "Hass Avocados + A2 Whole Milk", "8 MINS"),
                    slotChips,
                    buildFreshPromoSummaryCard(activeSlot, promoApplied),
                    buildFreshCheckoutForm(payAction),
                ),
            bottomBar =
                BottomBarSpec(
                    primaryButton = ui.button("btn_fresh_bottom_pay", payLabel, payAction, fullWidth = true),
                    helperText = helper,
                ),
            pageActions = pageActions,
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    private fun buildFreshPromoSummaryCard(
        activeSlot: String,
        promoApplied: Boolean,
    ): SnippetEnvelope {
        val discountLine =
            if (promoApplied) {
                "Subtotal \$6.78 · Promo FRESH20 -\$1.36 · Tip \$2.00 · Total \$7.42"
            } else {
                "Subtotal \$6.78 · Delivery FREE · Tip \$2.00 · Apply FRESH20 for 20% off"
            }
        val promoAction =
            if (promoApplied) {
                ui.toastAction("fresh_promo_done", "FRESH20 (-20%) is already active!", "info")
            } else {
                ui.navAction("apply_fresh20", "fresh-cart?slot=$activeSlot&promo=FRESH20")
            }
        val title = if (promoApplied) "Promo FRESH20 Active (-20%)" else "Promo Code · FRESH20"
        val badgeText = if (promoApplied) "SAVED \$1.36" else "20% OFF"
        val btnVariant = if (promoApplied) ButtonVariant.OUTLINE else ButtonVariant.PRIMARY
        return SnippetEnvelope(
            snippetId = "fresh_promo_card",
            snippetType = SnippetTypes.CARD,
            data =
                ui.encode(
                    CardSnippetData(
                        title = ui.text(title, "title.medium"),
                        subtitle = ui.text(discountLine, "body.small", "text.secondary"),
                        badge = TagAtom(label = badgeText, bgColor = ColorTokenRef(FRESH_OK)),
                        primaryButton =
                            ui.button(
                                "btn_apply_fresh20",
                                if (promoApplied) "FRESH20 Applied" else "Apply FRESH20",
                                promoAction,
                                variant = btnVariant,
                                size = ButtonSize.SMALL,
                            ),
                        layout = ui.cardLayout,
                    ),
                ),
        )
    }

    private fun buildFreshCheckoutForm(payAction: ActionRef): SnippetEnvelope {
        val desc = ui.text("Customize contactless drop and tip for Aarav K.", "body.small", "text.secondary")
        return SnippetEnvelope(
            snippetId = "fresh_checkout_form",
            snippetType = SnippetTypes.FORM,
            data =
                ui.encode(
                    FormSnippetData(
                        formId = "fresh_delivery_prefs",
                        title = ui.text("Drop Preferences & Rider Tip", "title.medium"),
                        description = desc,
                        fields =
                            listOf(
                                TextFieldAtom(
                                    fieldId = "fresh_rider_tip",
                                    label = "Rider Tip (\$ USD)",
                                    placeholder = "2.00",
                                    initialValue = "2.00",
                                    helperText = "100% goes directly to your delivery partner",
                                    keyboardType = KeyboardKind.NUMBER,
                                ),
                                TextFieldAtom(
                                    fieldId = "fresh_drop_note",
                                    label = "Drop Instructions",
                                    placeholder = "Leave by front porch bench",
                                    initialValue = "Leave chilled bag by front porch",
                                ),
                            ),
                        switches =
                            listOf(
                                SwitchAtom(
                                    fieldId = "fresh_contactless",
                                    label = "Contactless Doorstep Drop",
                                    subtitle = "No-bell drop with instant photo confirmation",
                                    checked = true,
                                ),
                                SwitchAtom(
                                    fieldId = "fresh_cold_pouch",
                                    label = "Chilled Insulated Pouch",
                                    subtitle = "Maintain 4°C for A2 milk & produce",
                                    checked = true,
                                ),
                            ),
                        submitButton = ui.button("btn_fresh_form_pay", "Pay & Place Express Order", payAction),
                        layout = ui.cardLayout,
                    ),
                ),
        )
    }

    fun freshOrderTrack(stage: Int = 3): PageResponse {
        val current = stage.coerceIn(1, 4)
        val next = nudges.nextStage(current)
        val stageMeta =
            when (current) {
                1 -> FreshStageCopy("Order #FRH-8042 confirmed at Express Hub", "STAGE 1/4 · 8M", 480)
                2 -> FreshStageCopy("Chilled produce & A2 milk packed at 4°C", "STAGE 2/4 · 6M", 360)
                3 -> FreshStageCopy("Aarav K. is 300m away with your basket", "STAGE 3/4 · 3M", 180)
                else -> FreshStageCopy("Delivered to your doorstep · Photo verified", "DELIVERED", 0)
            }
        val stepSpecs =
            listOf(
                Triple("f_confirmed", "Confirmed", "Now"),
                Triple("f_packed", "Chilled Pack", "2m"),
                Triple("f_route", "On the way", "300m"),
                Triple("f_done", "Doorstep", "Done"),
            )
        val rateAction =
            ui.batchAction(
                "fresh_rate_5star",
                listOf(
                    ui.celebrationAction("fresh_rate_fx", "Thanks for 5★!", "Your 5-star review boosted Aarav K."),
                    ui.toastAction("fresh_rate_toast", "Rated delivery 5★ — thank you!", "success"),
                ),
            )
        val tracker =
            nudges.orderTracker(
                snippetId = "fresh_live_tracker",
                trackerId = "fresh_order_8042",
                orderCode = "#FRH-8042",
                headline = stageMeta.headline,
                etaBadge = stageMeta.badge,
                etaSeconds = stageMeta.etaSeconds,
                riderName = "Aarav K.",
                riderCaption = "4.95 ★ · Cold-chain EV scooter · Stage $current of 4",
                steps = nudges.stageSteps(current, stepSpecs),
                primaryLabel = "Advance Delivery Stage",
                primaryAction = ui.navAction("fresh_advance_stage", "fresh-order-track?stage=$next"),
                secondaryLabel = "Rate Delivery (5★)",
                secondaryAction = rateAction,
            )
        val shopRow =
            ui.actionRow(
                snippetId = "fresh_shop_again_row",
                title = "Back to Abeta Fresh Hub",
                subtitle = "Chilled doorstep drop enabled · Tap to browse more items",
                icon = "check" to "status.success",
                tag = "STAGE $current/4" to FRESH_OK,
                onClick = ui.navAction("fresh_shop_again", "fresh-home"),
            )
        return PageResponse(
            pageId = "fresh-order-track",
            title = "Track Order #FRH-8042",
            toolbar = ui.toolbar("Order #FRH-8042", "Abeta Fresh · Stage $current of 4", "back_fresh_track"),
            snippets = listOf(tracker, shopRow),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }
}
