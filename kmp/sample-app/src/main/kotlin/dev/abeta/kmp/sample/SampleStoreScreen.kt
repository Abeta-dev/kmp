package dev.abeta.kmp.sample

import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.AdaptivePageSpec
import dev.abeta.kmp.contracts.BannerSnippetData
import dev.abeta.kmp.contracts.BorderSpec
import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.ButtonSize
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.CardSnippetData
import dev.abeta.kmp.contracts.CarouselSnippetData
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.CornerRadiusSpec
import dev.abeta.kmp.contracts.FloatingCartSpec
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.LayoutConfig
import dev.abeta.kmp.contracts.MotionDurationToken
import dev.abeta.kmp.contracts.MotionLoopEffect
import dev.abeta.kmp.contracts.MotionSpec
import dev.abeta.kmp.contracts.MotionTransition
import dev.abeta.kmp.contracts.OverlayPresentation
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.ScreenLayoutMode
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.SpacingValues
import dev.abeta.kmp.contracts.TabItemSpec
import dev.abeta.kmp.contracts.TabsSnippetData
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.ToolbarSpec
import dev.abeta.kmp.contracts.TrackingContract
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val PROMO_STAGGER_MS = 80
private const val CAROUSEL_ITEM_WIDTH_DP = 240
private const val TOAST_FAST_MS = 1800L
private const val DELIVERY_TOAST_MS = 2200L

internal object SampleStoreScreen {
    fun storeHome(): PageResponse {
        val checkoutAction = buildCheckoutSheetAction()
        return PageResponse(
            pageId = "store-home",
            title = "Abeta Fresh",
            toolbar = buildStoreToolbar(checkoutAction),
            snippets =
                listOf(
                    SampleNudgeAndTrackerPrimitives.expressPerksMarquee("store_perks_marquee", "store_express_perks"),
                    SampleNudgeAndTrackerPrimitives.flashDropNudgeBar("store_flash_nudge", "store_flash_drop"),
                    buildEtaBanner(),
                    buildPromoCarousel(),
                    SampleComposedBlockRecipes.loyaltyStampComposedBlock("store_loyalty_block", "store_loyalty_stamps"),
                    buildCategoryTabs(),
                    SampleStoreCatalog.buildOffersAccordion(),
                ),
            pullToRefresh =
                ActionRef(
                    id = "pull_refresh_store_home",
                    type = ActionTypes.REFRESH_PAGE,
                    payload =
                        buildJsonObject {
                            put("page_id", "store-home")
                            put("show_loader", true)
                        },
                ),
            pageActions =
                listOf(
                    ActionRef(
                        id = "track_store_home_view",
                        type = ActionTypes.TRACK_EVENT,
                        payload =
                            buildJsonObject {
                                put("event_name", "screen_viewed")
                                put(
                                    "properties",
                                    buildJsonObject {
                                        put("screen_id", "store-home")
                                        put("brand", "Abeta Fresh")
                                    },
                                )
                            },
                    ),
                ),
            floatingCart = buildFloatingCart(checkoutAction),
            adaptiveLayout =
                AdaptivePageSpec(
                    layoutMode = ScreenLayoutMode.CENTERED,
                    maxWidthDp = 1120,
                    splitPaneEnabled = true,
                    secondarySnippetIds = listOf("store_offers_accordion"),
                ),
            tracking =
                TrackingContract(
                    eventName = "page_loaded",
                    properties = mapOf("page_id" to "store-home"),
                ),
        )
    }
}

private fun buildStoreToolbar(checkoutAction: ActionRef): ToolbarSpec =
    ToolbarSpec(
        title = SampleScreens.text("Abeta Fresh", "title.large"),
        subtitle = SampleScreens.text("Home · Express 8 mins", "body.small", "text.secondary"),
        navigationIcon =
            IconAtom(
                name = "arrow_back",
                sizeDp = 22,
                color = ColorTokenRef("text.primary"),
                contentDescription = "Navigate back",
                onClick = SampleScreens.backAction("back_from_store_home"),
            ),
        trailingActions =
            listOf(
                IconAtom(
                    name = "shopping_cart",
                    sizeDp = 22,
                    color = ColorTokenRef("brand.primary"),
                    contentDescription = "Open cart checkout",
                    onClick = checkoutAction,
                ),
            ),
        bgColor = ColorTokenRef("surface.primary"),
    )

private fun buildEtaBanner(): SnippetEnvelope =
    SnippetEnvelope(
        snippetId = "store_eta_banner",
        snippetType = SnippetTypes.BANNER,
        data = SampleScreens.encode(buildEtaBannerData()),
        motion =
            MotionSpec(
                enter = MotionTransition.SLIDE_DOWN,
                durationToken = MotionDurationToken.NORMAL,
                loopEffect = MotionLoopEffect.PULSE,
            ),
    )

private fun buildEtaBannerData(): BannerSnippetData {
    val freeDeliveryToast =
        ActionRef(
            id = "toast_free_delivery",
            type = ActionTypes.SHOW_TOAST,
            payload =
                buildJsonObject {
                    put("message", "FREE DELIVERY unlocked on your Abeta Fresh express order!")
                    put("duration_ms", DELIVERY_TOAST_MS)
                    put("variant", "success")
                },
        )
    return BannerSnippetData(
        title = SampleScreens.text("8-Min Express Delivery", "title.medium"),
        body =
            SampleScreens.text(
                "Chilled produce, bakery & pantry staples delivered fresh from Abeta Fresh Hub.",
                "body.small",
                "text.secondary",
            ),
        icon = IconAtom(name = "bolt", sizeDp = 24, color = ColorTokenRef("brand.primary")),
        tag =
            TagAtom(
                label = "8 MINS",
                bgColor = ColorTokenRef("status.success_container"),
                motion = MotionSpec(loopEffect = MotionLoopEffect.PULSE),
            ),
        cta =
            ButtonAtom(
                id = "btn_free_delivery_pill",
                label = "FREE DELIVERY",
                variant = ButtonVariant.OUTLINE,
                size = ButtonSize.SMALL,
                onClick = freeDeliveryToast,
                motion = MotionSpec(enter = MotionTransition.FADE_IN, durationToken = MotionDurationToken.FAST),
            ),
        layout =
            LayoutConfig(
                padding = SpacingValues(all = 16),
                margin = SpacingValues(horizontal = 16, vertical = 4),
                bgColor = ColorTokenRef("brand.container"),
                cornerRadius = CornerRadiusSpec(all = 12),
                border = BorderSpec(widthDp = 1f, color = ColorTokenRef("border.subtle")),
                fillMaxWidth = true,
            ),
    )
}

private fun buildPromoCarousel(): SnippetEnvelope {
    val items =
        listOf(
            Triple("promo_harvest", "Morning Harvest · 25% OFF", "FRESH" to "status.success_container"),
            Triple("promo_bakery", "Warm Bakery & Dairy", "CHILLED" to "status.info_container"),
            Triple("promo_hydration", "Cold-Pressed Hydration", "COMBO" to "status.warning_container"),
        ).mapIndexed { index, (id, title, badgePair) ->
            buildPromoCard(id = id, title = title, badge = badgePair, index = index)
        }
    return SnippetEnvelope(
        snippetId = "store_promo_carousel",
        snippetType = SnippetTypes.CAROUSEL,
        data =
            SampleScreens.encode(
                CarouselSnippetData(
                    title = SampleScreens.text("Flash Express Deals", "title.medium"),
                    items = items,
                    itemWidthDp = CAROUSEL_ITEM_WIDTH_DP,
                    spacingDp = 12,
                    layout = LayoutConfig(padding = SpacingValues(horizontal = 16, vertical = 8), fillMaxWidth = true),
                ),
            ),
    )
}

private fun buildPromoCard(
    id: String,
    title: String,
    badge: Pair<String, String>,
    index: Int,
): SnippetEnvelope {
    val subtitle =
        when (id) {
            "promo_harvest" -> "Farm-fresh avocados, berries & leafy greens"
            "promo_bakery" -> "Artisan sourdough & chilled A2 milk in 8 mins"
            else -> "Zero-sugar yuzu spritzers & oat cold brew"
        }
    return SnippetEnvelope(
        snippetId = id,
        snippetParentId = "store_promo_carousel",
        snippetType = SnippetTypes.CARD,
        data =
            SampleScreens.encode(
                CardSnippetData(
                    title = SampleScreens.text(title, "title.medium"),
                    subtitle = SampleScreens.text(subtitle, "body.small", "text.secondary"),
                    badge = TagAtom(label = badge.first, bgColor = ColorTokenRef(badge.second)),
                    onClick =
                        ActionRef(
                            id = "toast_$id",
                            type = ActionTypes.SHOW_TOAST,
                            payload =
                                buildJsonObject {
                                    put("message", "$title deal highlighted")
                                    put("duration_ms", TOAST_FAST_MS)
                                    put("variant", "info")
                                },
                        ),
                    layout = storeCompactCardLayout,
                ),
            ),
        motion =
            MotionSpec(
                enter = MotionTransition.SLIDE_UP,
                durationToken = MotionDurationToken.NORMAL,
                staggerMs = PROMO_STAGGER_MS,
                delayMs = index * PROMO_STAGGER_MS,
            ),
    )
}

private fun buildCategoryTabs(): SnippetEnvelope {
    val tabs =
        listOf(
            TabItemSpec(
                id = "fruits_veg",
                title = "Fruits & Veg",
                badge = TagAtom(label = "FRESH", bgColor = ColorTokenRef("status.success_container")),
                snippets =
                    listOf(
                        SampleStoreCatalog.buildProductGrid(
                            "fruits_veg",
                            "grid_fruits_veg",
                            SampleStoreCatalog.fruitsAndVegProducts(),
                        ),
                    ),
            ),
            TabItemSpec(
                id = "dairy_bread",
                title = "Dairy & Bread",
                badge = TagAtom(label = "COLD", bgColor = ColorTokenRef("status.info_container")),
                snippets =
                    listOf(
                        SampleStoreCatalog.buildProductGrid(
                            "dairy_bread",
                            "grid_dairy_bread",
                            SampleStoreCatalog.dairyAndBreadProducts(),
                        ),
                    ),
            ),
            TabItemSpec(
                id = "snacks_drinks",
                title = "Snacks & Drinks",
                snippets =
                    listOf(
                        SampleStoreCatalog.buildProductGrid(
                            "snacks_drinks",
                            "grid_snacks_drinks",
                            SampleStoreCatalog.snacksAndDrinksProducts(),
                        ),
                    ),
            ),
        )
    return SnippetEnvelope(
        snippetId = "store_category_tabs",
        snippetType = SnippetTypes.TABS,
        data =
            SampleScreens.encode(
                TabsSnippetData(
                    tabs = tabs,
                    selectedIndex = 0,
                    layout = LayoutConfig(padding = SpacingValues(horizontal = 12, vertical = 4), fillMaxWidth = true),
                ),
            ),
    )
}

private fun buildFloatingCart(checkoutAction: ActionRef): FloatingCartSpec =
    FloatingCartSpec(
        label = "2 items · \$8.48",
        etaBadge =
            TagAtom(
                label = "8 MINS",
                bgColor = ColorTokenRef("status.success_container"),
                motion = MotionSpec(loopEffect = MotionLoopEffect.PULSE),
            ),
        cta =
            ButtonAtom(
                id = "btn_floating_checkout",
                label = "Place Express Order",
                variant = ButtonVariant.PRIMARY,
                onClick = checkoutAction,
                motion = MotionSpec(enter = MotionTransition.SPRING_BOUNCE, durationToken = MotionDurationToken.FAST),
            ),
        motion =
            MotionSpec(
                enter = MotionTransition.SLIDE_UP,
                exit = MotionTransition.SLIDE_DOWN,
                durationToken = MotionDurationToken.NORMAL,
            ),
        freeDeliveryThresholdCents = FREE_DELIVERY_THRESHOLD_CENTS,
        nudgePrefix = FREE_DELIVERY_NUDGE_PREFIX,
        unlockedLabel = FREE_DELIVERY_UNLOCKED_LABEL,
    )

private fun buildCheckoutSheetAction(): ActionRef {
    val dismissCheckout =
        ActionRef(
            id = "dismiss_express_checkout",
            type = ActionTypes.DISMISS_OVERLAY,
            payload = buildJsonObject { put("overlay_id", "express_checkout_sheet") },
        )
    val placeOrder =
        ActionRef(
            id = "place_express_order",
            type = ActionTypes.CALL_API,
            payload =
                buildJsonObject {
                    put("endpoint", "/api/v1/orders/express")
                    put("method", "POST")
                    put(
                        "body",
                        buildJsonObject {
                            put("store_brand", "Abeta Fresh")
                            put("delivery_eta_mins", 8)
                            put("coupon_code", "FRESH20")
                        },
                    )
                },
            secondaryActions = listOf(dismissCheckout),
        )
    val cancelCheckout =
        ActionRef(
            id = "cancel_express_checkout",
            type = ActionTypes.DISMISS_OVERLAY,
            payload = buildJsonObject { put("overlay_id", "express_checkout_sheet") },
        )
    return ActionRef(
        id = "open_express_checkout",
        type = ActionTypes.OPEN_DIALOG,
        payload =
            buildJsonObject {
                put("dialog_id", "express_checkout_sheet")
                put("title", "Place Express Order")
                put("message", "Abeta Fresh · 8-Min Express Delivery to Home · Free delivery applied.")
                put("presentation", OverlayPresentation.BOTTOM_SHEET.name.lowercase())
                put("dismissible", true)
                put("positive_action", SampleScreens.encode(placeOrder))
                put("negative_action", SampleScreens.encode(cancelCheckout))
            },
    )
}
