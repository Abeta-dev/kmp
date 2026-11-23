package dev.abeta.kmp.storybook

import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.BottomBarSpec
import dev.abeta.kmp.contracts.ButtonSize
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.CardSnippetData
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.FormSnippetData
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.SwitchAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TextFieldAtom
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val WF_OK = "status.success_container"
private const val WF_INFO = "status.info_container"
private const val WF_WARN = "status.warning_container"
private const val WF_BRAND = "brand.container"

private data class CommerceStageCopy(
    val headline: String,
    val badge: String,
    val etaSeconds: Int,
)

/**
 * Multi-step Abeta Bites and Abeta Mart workflow sub-screens (cart review, checkout, and
 * stage-cycling live trackers #BTS-4091 & #MRT-5518) for Storybook.
 */
@Suppress("MagicNumber", "TooManyFunctions")
internal object SuperAppDemoCommerceWorkflows {
    private val ui = SuperAppDemoBuilder
    private val nudges = SuperAppDemoNudgeBuilders

    fun bitesCart(promo: String = ""): PageResponse {
        val promoApplied = promo.equals("BITES50", ignoreCase = true)
        val placeOrder =
            ui.apiCallAction(
                "bites_place_order",
                "/api/v1/bites/orders",
                buildJsonObject {
                    put("restaurant_id", "saffron_hearth")
                    put("promo", if (promoApplied) "BITES50" else "")
                },
            )
        val orderLabel = if (promoApplied) "Place Order · \$14.25" else "Place Order · \$17.75"
        val helper = ui.text("Priority thermal delivery · Zero plastic cutlery", "label.small", "text.secondary")
        val pageActions =
            if (promoApplied) {
                listOf(ui.toastAction("bites_promo_toast", "Promo BITES50 applied — saved \$3.50!", "success"))
            } else {
                emptyList()
            }
        return PageResponse(
            pageId = "bites-cart",
            title = "Order Review · Saffron Hearth",
            toolbar = ui.toolbar("Review Meal Order", "Saffron Hearth Kitchen · 24 mins", "back_bites_cart"),
            snippets =
                listOf(
                    ui.header("bites_cart_hdr", "Your Meal Bag", "1× Smoked Veg Biryani · 1× Paneer Tikka", "2 ITEMS"),
                    buildBitesPromoCard(promoApplied),
                    buildBitesCheckoutForm(placeOrder),
                ),
            bottomBar =
                BottomBarSpec(
                    primaryButton = ui.button("btn_bites_bottom_order", orderLabel, placeOrder, fullWidth = true),
                    helperText = helper,
                ),
            pageActions = pageActions,
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    private fun buildBitesPromoCard(promoApplied: Boolean): SnippetEnvelope {
        val summary =
            if (promoApplied) {
                "Items \$17.75 · Promo BITES50 -\$3.50 · Delivery FREE · Total \$14.25"
            } else {
                "Items \$17.75 · Apply BITES50 for instant \$3.50 off your meal"
            }
        val promoAction =
            if (promoApplied) {
                ui.toastAction("bites_promo_done", "BITES50 is already applied to your bag!", "info")
            } else {
                ui.navAction("apply_bites50", "bites-cart?promo=BITES50")
            }
        val title = if (promoApplied) "Promo BITES50 Active" else "Save with BITES50"
        val badgeText = if (promoApplied) "SAVED \$3.50" else "PROMO"
        val variant = if (promoApplied) ButtonVariant.OUTLINE else ButtonVariant.PRIMARY
        return SnippetEnvelope(
            snippetId = "bites_promo_card",
            snippetType = SnippetTypes.CARD,
            data =
                ui.encode(
                    CardSnippetData(
                        title = ui.text(title, "title.medium"),
                        subtitle = ui.text(summary, "body.small", "text.secondary"),
                        badge = TagAtom(label = badgeText, bgColor = ColorTokenRef(WF_WARN)),
                        primaryButton =
                            ui.button(
                                "btn_apply_bites50",
                                if (promoApplied) "BITES50 Applied" else "Apply BITES50",
                                promoAction,
                                variant = variant,
                                size = ButtonSize.SMALL,
                            ),
                        layout = ui.cardLayout,
                    ),
                ),
        )
    }

    private fun buildBitesCheckoutForm(placeOrder: ActionRef): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = "bites_checkout_form",
            snippetType = SnippetTypes.FORM,
            data =
                ui.encode(
                    FormSnippetData(
                        formId = "bites_drop_prefs",
                        title = ui.text("Kitchen & Delivery Preferences", "title.medium"),
                        fields =
                            listOf(
                                TextFieldAtom(
                                    fieldId = "bites_kitchen_note",
                                    label = "Cooking Instructions",
                                    placeholder = "Medium spice, extra mint raita",
                                    initialValue = "Medium spice, extra mint raita",
                                ),
                            ),
                        switches =
                            listOf(
                                SwitchAtom(
                                    fieldId = "bites_contactless",
                                    label = "Contactless Drop",
                                    subtitle = "Leave thermal bag at door with photo proof",
                                    checked = true,
                                ),
                                SwitchAtom(
                                    fieldId = "bites_skip_cutlery",
                                    label = "Skip Single-Use Cutlery",
                                    subtitle = "Eco-friendly bowls without plastic spoons",
                                    checked = true,
                                ),
                            ),
                        submitButton = ui.button("btn_bites_place_order", "Place Order", placeOrder),
                        layout = ui.cardLayout,
                    ),
                ),
        )

    fun bitesOrderTrack(stage: Int = 3): PageResponse {
        val current = stage.coerceIn(1, 4)
        val next = nudges.nextStage(current)
        val stageMeta =
            when (current) {
                1 -> CommerceStageCopy("Saffron Hearth accepted your order", "STAGE 1/4 · 22M", 1320)
                2 -> CommerceStageCopy("Chef is finishing Smoked Veg Biryani", "STAGE 2/4 · 16M", 960)
                3 -> CommerceStageCopy("Karan M. is on the way from Saffron Hearth", "STAGE 3/4 · 8M", 480)
                else -> CommerceStageCopy("Hot meal delivered — enjoy your dinner!", "DELIVERED", 0)
            }
        val stepSpecs =
            listOf(
                Triple("b_conf", "Confirmed", "19:14"),
                Triple("b_prep", "Preparing", "19:21"),
                Triple("b_route", "En Route", "1.2 km"),
                Triple("b_done", "Delivered", "Done"),
            )
        val tracker =
            nudges.orderTracker(
                snippetId = "bites_live_order_tracker",
                trackerId = "bites_order_4091",
                orderCode = "#BTS-4091",
                headline = stageMeta.headline,
                etaBadge = stageMeta.badge,
                etaSeconds = stageMeta.etaSeconds,
                riderName = "Karan M.",
                riderCaption = "4.9 ★ · Thermal e-bike · Stage $current of 4",
                steps = nudges.stageSteps(current, stepSpecs),
                primaryLabel = "Advance Order Stage",
                primaryAction = ui.navAction("bites_advance_stage", "bites-order-track?stage=$next"),
                secondaryLabel = "Rate Meal",
                secondaryAction = ui.toastAction("bites_rate_meal", "Thanks for rating Saffron Hearth 5★!", "success"),
            )
        val backRow =
            ui.actionRow(
                snippetId = "bites_contactless_row",
                title = "Contactless Drop Active · Order Again",
                subtitle = "Photo proof uploaded on delivery · Tap to return to Abeta Bites",
                icon = "check" to "status.success",
                tag = "STAGE $current/4" to WF_OK,
                onClick = ui.navAction("bites_home_nav", "bites-home"),
            )
        return PageResponse(
            pageId = "bites-order-track",
            title = "Track Order #BTS-4091",
            toolbar = ui.toolbar("Order #BTS-4091", "Abeta Bites · Stage $current of 4", "back_bites_track"),
            snippets = listOf(tracker, backRow),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    fun martCheckout(shipping: String = "same_day"): PageResponse {
        val activeShipping = if (shipping == "standard") "standard" else "same_day"
        val shipChips = listOf("same_day" to "Same-Day Express (Free)", "standard" to "Standard 2-Day (-\$5)")
        val placeOrder =
            ui.apiCallAction(
                "mart_place_order",
                "/api/v1/mart/orders",
                buildJsonObject {
                    put("product_id", "mart_anc")
                    put("shipping", activeShipping)
                },
            )
        val totalLabel = if (activeShipping == "standard") "\$129.10" else "\$134.10"
        val chipRow =
            ui.chipGroup("mart_ship_chips", "mart_shipping", "mart-checkout", shipChips, activeShipping, "shipping")
        val orderBtn = ui.button("btn_mart_place_order", "Place Mart Order · $totalLabel", placeOrder, fullWidth = true)
        val helper = ui.text("Includes 10% instant bank cashback · 30-day returns", "label.small", "text.secondary")
        return PageResponse(
            pageId = "mart-checkout",
            title = "Abeta Mart Checkout",
            toolbar = ui.toolbar("Mart Checkout", "Abeta Pulse ANC · Same-Day Hub", "back_mart_checkout"),
            snippets = listOf(buildMartAddressCard(), chipRow, buildMartCashbackCard(totalLabel, placeOrder)),
            bottomBar = BottomBarSpec(primaryButton = orderBtn, helperText = helper),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    private fun buildMartAddressCard(): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = "mart_saved_address",
            snippetType = SnippetTypes.CARD,
            data =
                ui.encode(
                    CardSnippetData(
                        title = ui.text("Saved Address · Home", "title.medium"),
                        subtitle = ui.text("420 Harbor View, Apt 8B, Metro City 94107", "body.small", "text.secondary"),
                        description =
                            ui.text("Recipient: Aanya Verma · +1 (415) 555-0192", "label.medium", "text.brand"),
                        badge = TagAtom(label = "VERIFIED", bgColor = ColorTokenRef(WF_INFO)),
                        secondaryButton =
                            ui.button(
                                "btn_mart_change_addr",
                                "Change Address",
                                ui.toastAction("mart_addr_toast", "Address book synced with Abeta profile"),
                                ButtonVariant.OUTLINE,
                                ButtonSize.SMALL,
                            ),
                        layout = ui.cardLayout,
                    ),
                ),
        )

    private fun buildMartCashbackCard(
        totalLabel: String,
        placeOrder: ActionRef,
    ): SnippetEnvelope {
        val summary = "Abeta Pulse ANC \$149.00 · Bank Cashback -\$14.90 · Net $totalLabel"
        val btn = ui.button("btn_mart_card_order", "Place Mart Order", placeOrder, size = ButtonSize.SMALL)
        return SnippetEnvelope(
            snippetId = "mart_cashback_card",
            snippetType = SnippetTypes.CARD,
            data =
                ui.encode(
                    CardSnippetData(
                        title = ui.text("10% Instant Bank Cashback", "title.medium"),
                        subtitle = ui.text(summary, "body.small", "text.secondary"),
                        badge = TagAtom(label = "SAVE \$14.90", bgColor = ColorTokenRef(WF_OK)),
                        primaryButton = btn,
                        layout = ui.cardLayout,
                    ),
                ),
        )
    }

    fun martOrderTrack(stage: Int = 2): PageResponse {
        val current = stage.coerceIn(1, 4)
        val next = nudges.nextStage(current)
        val stageMeta =
            when (current) {
                1 -> CommerceStageCopy("Order #MRT-5518 confirmed & warranty bound", "STAGE 1/4", 7200)
                2 -> CommerceStageCopy("Packed at Metro Fulfillment Hub · Seal #882", "STAGE 2/4", 5400)
                3 -> CommerceStageCopy("Courier van is 4 stops away from Harbor View", "STAGE 3/4", 1800)
                else -> CommerceStageCopy("Delivered · 30-day instant return window active", "DELIVERED", 0)
            }
        val stepSpecs =
            listOf(
                Triple("m_conf", "Confirmed", "14:05"),
                Triple("m_pack", "Hub Packed", "14:40"),
                Triple("m_van", "Out for Delivery", "4 stops"),
                Triple("m_done", "Delivered", "Done"),
            )
        val share =
            ui.shareAction(
                "share_mart_track",
                "Abeta Mart Shipment #MRT-5518",
                "Tracking Abeta Pulse ANC Headphones · Stage $current of 4",
                "https://abeta.dev/mart/track/5518",
            )
        val tracker =
            nudges.orderTracker(
                snippetId = "mart_live_tracker",
                trackerId = "mart_order_5518",
                orderCode = "#MRT-5518",
                headline = stageMeta.headline,
                etaBadge = stageMeta.badge,
                etaSeconds = stageMeta.etaSeconds,
                riderName = "Devon L. · Abeta Courier",
                riderCaption = "Same-day Metro Van · OTP-verified handoff · Stage $current/4",
                steps = nudges.stageSteps(current, stepSpecs),
                primaryLabel = "Advance Shipment",
                primaryAction = ui.navAction("mart_advance_stage", "mart-order-track?stage=$next"),
                secondaryLabel = "Share Tracking",
                secondaryAction = share,
            )
        val backRow =
            ui.actionRow(
                snippetId = "mart_continue_row",
                title = "Continue Shopping on Abeta Mart",
                subtitle = "10% cashback credited · 30-day hassle-free returns",
                icon = "star" to "brand.primary",
                tag = "STAGE $current/4" to WF_BRAND,
                onClick = ui.navAction("mart_home_nav", "mart-home"),
            )
        return PageResponse(
            pageId = "mart-order-track",
            title = "Track Shipment #MRT-5518",
            toolbar = ui.toolbar("Shipment #MRT-5518", "Abeta Mart · Stage $current of 4", "back_mart_track"),
            snippets = listOf(tracker, backRow),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }
}
