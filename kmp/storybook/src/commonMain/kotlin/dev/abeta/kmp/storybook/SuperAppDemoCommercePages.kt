package dev.abeta.kmp.storybook

import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.BottomBarSpec
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.CardSnippetData
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.ComposedNode
import dev.abeta.kmp.contracts.ComposedNodeKind
import dev.abeta.kmp.contracts.CornerRadiusSpec
import dev.abeta.kmp.contracts.CountdownAtom
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.ImageAtom
import dev.abeta.kmp.contracts.ImageScaleMode
import dev.abeta.kmp.contracts.MarqueeItem
import dev.abeta.kmp.contracts.MotionLoopEffect
import dev.abeta.kmp.contracts.OrderTrackerStep
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val OK_BG = "status.success_container"
private const val INFO_BG = "status.info_container"
private const val WARN_BG = "status.warning_container"
private const val BRAND_BG = "brand.container"
private const val MART_DETAIL = "mart-product-detail"

/**
 * Multiplatform commerce vertical demo screens for Storybook:
 * Abeta Fresh (8-Min Grocery), Abeta Bites (Food Delivery), and Abeta Mart (Marketplace).
 */
@Suppress("MagicNumber", "TooManyFunctions")
internal object SuperAppDemoCommercePages {
    private val ui = SuperAppDemoBuilder
    private val nudges = SuperAppDemoNudgeBuilders

    private val freshSkus =
        listOf(
            DemoSkuSpec(
                id = "fresh_avocados",
                title = "Hass Avocados",
                subtitle = "2 pcs · Ready",
                cents = 349,
                badge = "8 MINS" to OK_BG,
                qty = 1,
                category = "produce",
            ),
            DemoSkuSpec(
                id = "fresh_milk",
                title = "A2 Whole Milk",
                subtitle = "1 L · Chilled",
                cents = 329,
                badge = "CHILLED" to INFO_BG,
                qty = 1,
                category = "dairy",
            ),
            DemoSkuSpec(
                id = "fresh_sourdough",
                title = "Sourdough Loaf",
                subtitle = "450 g · Baked",
                cents = 499,
                badge = "BAKERY" to WARN_BG,
                category = "snacks",
            ),
            DemoSkuSpec(
                id = "fresh_berries",
                title = "Strawberries",
                subtitle = "250 g · Farm",
                cents = 449,
                badge = "ORGANIC" to BRAND_BG,
                category = "produce",
            ),
            DemoSkuSpec(
                id = "fresh_cold_brew",
                title = "Oat Cold Brew",
                subtitle = "250 ml · Zero",
                cents = 379,
                badge = "DRINKS" to INFO_BG,
                category = "drinks",
            ),
            DemoSkuSpec(
                id = "fresh_almonds",
                title = "Sea Salt Almonds",
                subtitle = "180 g · Roast",
                cents = 549,
                badge = "SNACKS" to WARN_BG,
                category = "snacks",
            ),
        )

    private val bitesRestaurants =
        listOf(
            DemoListingSpec(
                "rest_saffron",
                "Saffron Hearth Kitchen",
                "North Indian · Biryani · Kebabs",
                "4.8 ★ · 24 mins · \$14 for two · Free Delivery",
                "TOP RATED" to OK_BG,
                "View Menu",
                "bites-restaurant",
                "biryani",
            ),
            DemoListingSpec(
                "rest_cedar",
                "Cedar Crust Pizzeria",
                "Wood-Fired Pizza · Fresh Pasta",
                "4.7 ★ · 20 mins · \$18 for two · Flat \$3 OFF",
                "FAST 20M" to WARN_BG,
                "View Menu",
                "bites-restaurant",
                "pizza",
            ),
            DemoListingSpec(
                "rest_bamboo",
                "Bamboo Noodle Foundry",
                "Pan-Asian · Hand-Pulled Ramen",
                "4.6 ★ · 28 mins · Pure Veg Options",
                "PURE VEG+" to INFO_BG,
                "View Menu",
                "bites-restaurant",
                "pure_veg",
            ),
        )

    private val bitesDishes =
        listOf(
            DemoSkuSpec("dish_biryani", "Smoked Veg Biryani", "Mint raita & salan", 950, "CHEF" to OK_BG, 1),
            DemoSkuSpec("dish_paneer", "Charred Paneer Tikka", "Tandoor · 6 pcs", 825, "PURE VEG" to INFO_BG, 1),
            DemoSkuSpec("dish_naan", "Garlic Herb Naan", "2 buttered breads", 350, "HOT" to WARN_BG),
            DemoSkuSpec("dish_lassi", "Cardamom Mango Lassi", "Chilled · 350 ml", 425, "CHILLED" to BRAND_BG),
        )

    private val martSkus =
        listOf(
            DemoSkuSpec(
                id = "mart_anc",
                title = "Pulse ANC Headset",
                subtitle = "44 dB ANC · 42h",
                cents = 14900,
                badge = "-30%" to WARN_BG,
                qty = 1,
                route = MART_DETAIL,
                category = "audio",
            ),
            DemoSkuSpec(
                id = "mart_lamp",
                title = "Nordic Desk Lamp",
                subtitle = "CRI 97+ · Dimmer",
                cents = 5900,
                badge = "PICK" to BRAND_BG,
                route = MART_DETAIL,
                category = "home",
            ),
            DemoSkuSpec(
                id = "mart_kb",
                title = "Loom Mechanical 75%",
                subtitle = "Gasket · Tri-mode",
                cents = 11900,
                badge = "NEW" to INFO_BG,
                route = MART_DETAIL,
                category = "electronics",
            ),
            DemoSkuSpec(
                id = "mart_hoodie",
                title = "Merino Hoodie",
                subtitle = "Breathable weave",
                cents = 8400,
                badge = "FASHION" to OK_BG,
                route = MART_DETAIL,
                category = "fashion",
            ),
        )

    fun freshHome(
        query: String = "",
        filter: String = "all",
    ): PageResponse {
        val visibleSkus = filterSkus(freshSkus, query, filter)
        val checkout =
            ui.apiCallAction(
                "fresh_express_checkout",
                "/api/v1/fresh/orders/express",
                buildJsonObject { put("vertical", "abeta-fresh") },
            )
        val trackIcon =
            IconAtom(
                name = "notifications",
                sizeDp = 22,
                color = ColorTokenRef("brand.primary"),
                contentDescription = "Track Fresh order",
                onClick = ui.navAction("nav_fresh_track", "fresh-order-track"),
            )
        val chips =
            listOf(
                "all" to "All",
                "produce" to "Produce",
                "dairy" to "Dairy",
                "snacks" to "Snacks",
                "drinks" to "Drinks",
            )
        val search = ui.searchBar("fresh_search_bar", "fresh_search", "Search avocados, milk...", "fresh-home", query)
        return PageResponse(
            pageId = "fresh-home",
            title = "Abeta Fresh",
            toolbar = ui.toolbar("Abeta Fresh", "Express Hub · Delivery in 8 mins", "back_fresh", listOf(trackIcon)),
            snippets =
                listOf(
                    search,
                    buildFreshPerksMarquee(),
                    ui.chipGroup("fresh_category_chips", "fresh_categories", "fresh-home", chips, filter),
                    buildFreshFlashNudge(),
                    buildFreshLoyaltyCard(),
                    ui.productGrid("fresh_grid", "Express Essentials (${visibleSkus.size})", visibleSkus),
                ),
            floatingCart =
                nudges.floatingCart(
                    label = "2 items · \$6.78",
                    eta = "8 MINS",
                    ctaLabel = "Express Checkout",
                    action = checkout,
                    freeDeliveryThresholdCents = 1500,
                    unlockedLabel = "FREE 8-min delivery unlocked!",
                ),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    private fun buildFreshPerksMarquee(): SnippetEnvelope =
        nudges.marquee(
            snippetId = "fresh_perks_marquee",
            stripId = "fresh_live_perks",
            items =
                listOf(
                    MarqueeItem(
                        id = "mq_dispatch",
                        text = "8-min express dispatch",
                        icon = IconAtom(name = "star", sizeDp = 16, color = ColorTokenRef("brand.primary")),
                        badge = TagAtom(label = "EXPRESS", bgColor = ColorTokenRef(OK_BG)),
                        onClick = ui.toastAction("tap_mq_dispatch", "Chilled express dispatch in 8 mins"),
                    ),
                    MarqueeItem(
                        id = "mq_free",
                        text = "Free delivery above \$15",
                        icon = IconAtom(name = "check", sizeDp = 16, color = ColorTokenRef("status.success")),
                        badge = TagAtom(label = "FREE", bgColor = ColorTokenRef(BRAND_BG)),
                        onClick = ui.toastAction("tap_mq_free", "Add \$15+ for free 8-min delivery!", "success"),
                    ),
                    MarqueeItem(
                        id = "mq_zero",
                        text = "Zero surge pricing 24/7",
                        icon = IconAtom(name = "info", sizeDp = 16, color = ColorTokenRef("status.info")),
                        badge = TagAtom(label = "LIVE", bgColor = ColorTokenRef(INFO_BG)),
                    ),
                ),
        )

    private fun buildFreshFlashNudge(): SnippetEnvelope =
        nudges.nudgeBar(
            snippetId = "fresh_flash_nudge",
            nudgeId = "fresh_flash_drop",
            title = "Flash Drop · Extra 20% off fresh produce",
            subtitle = "Chilled express slot reserved — add items to unlock FREE delivery",
            badgeLabel = "FLASH DROP",
            badgeEffect = MotionLoopEffect.WIGGLE,
            ctaLabel = "Claim Drop",
            ctaAction = ui.toastAction("fresh_claim", "Flash Drop 20% applied to basket!", "success"),
            countdownSeconds = 420,
            countdownLabel = "Ends in",
            progress = 0.68f,
        )

    private fun buildFreshLoyaltyCard(): SnippetEnvelope =
        nudges.moldedCardBlock(
            snippetId = "fresh_loyalty_block",
            blockId = "fresh_loyalty_stamps",
            recipeName = "loyalty_stamp_card",
            badgeLabel = "3 / 5 STAMPS",
            badgeVariant = TagVariant.BRAND,
            trailingNode =
                ComposedNode(
                    nodeId = "fresh_bonus_cd",
                    kind = ComposedNodeKind.COUNTDOWN,
                    countdown = CountdownAtom("fresh_reward_cd", 840, label = "2× stamps"),
                ),
            title = "Abeta Fresh Club · Free Express Basket",
            subtitle = "Molded via composed_block — 2 more orders unlock a free basket.",
            progress = 0.60f,
            progressLabel = "3 of 5 stamps",
            footerCounter = "3 / 5 stamps",
            ctaLabel = "Redeem Perks",
            ctaAction = ui.toastAction("fresh_redeem", "2 more orders unlock your free basket!", "success"),
        )

    fun freshOrderTrack(): PageResponse {
        val tracker =
            nudges.orderTracker(
                snippetId = "fresh_live_tracker",
                trackerId = "fresh_order_8042",
                orderCode = "#FRH-8042",
                headline = "Aarav K. is 300m away with your chilled basket",
                etaBadge = "6 MINS",
                etaSeconds = 360,
                riderName = "Aarav K.",
                riderCaption = "4.95 ★ · Cold-chain EV scooter · 6 items",
                steps =
                    listOf(
                        OrderTrackerStep("f_confirmed", "Confirmed", "Now", completed = true),
                        OrderTrackerStep("f_packed", "Chilled Pack", "2m", completed = true),
                        OrderTrackerStep("f_route", "On the way", "300m", active = true),
                        OrderTrackerStep("f_done", "Doorstep", "6m"),
                    ),
                primaryLabel = "Call Rider",
                primaryAction = ui.toastAction("fresh_call", "Connecting masked call to Aarav K."),
                secondaryLabel = "Shop Again",
                secondaryAction = ui.navAction("fresh_shop_again", "fresh-home"),
            )
        val dropRow =
            ui.actionRow(
                snippetId = "fresh_drop_row",
                title = "Chilled Doorstep Drop Enabled",
                subtitle = "No-bell drop with photo confirmation on delivery",
                icon = "check" to "status.success",
                tag = "ACTIVE" to OK_BG,
                onClick = ui.toastAction("fresh_drop_ok", "Doorstep drop preferences saved", "success"),
            )
        return PageResponse(
            pageId = "fresh-order-track",
            title = "Track Order #FRH-8042",
            toolbar = ui.toolbar("Order #FRH-8042", "Abeta Fresh · Arriving in 6 mins", "back_fresh_track"),
            snippets = listOf(tracker, dropRow),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    fun bitesHome(
        query: String = "",
        filter: String = "all",
    ): PageResponse {
        val visible = filterListings(bitesRestaurants, query, filter)
        val collections =
            listOf(
                Triple("col_biryani", "Chef's Biryani Vault", "Slow-cooked aromatic bowls · 20% OFF"),
                Triple("col_pizza", "Wood-Fired Crusts", "Sourdough pies baked in 90 seconds"),
                Triple("col_bowls", "Protein Power Bowls", "Macro-balanced bowls under 550 kcal"),
            )
        val filters = listOf("all" to "All", "biryani" to "Biryani", "pizza" to "Pizza", "pure_veg" to "Pure Veg")
        val trackIcon =
            IconAtom(
                name = "notifications",
                sizeDp = 22,
                color = ColorTokenRef("brand.primary"),
                contentDescription = "Track Bites order",
                onClick = ui.navAction("nav_bites_track", "bites-order-track"),
            )
        val search = ui.searchBar("bites_search_bar", "bites_search", "Search biryani, pizza...", "bites-home", query)
        return PageResponse(
            pageId = "bites-home",
            title = "Abeta Bites",
            toolbar = ui.toolbar("Abeta Bites", "Hot meals · Live 4-stage tracking", "back_bites", listOf(trackIcon)),
            snippets =
                listOf(
                    search,
                    ui.chipGroup("bites_filter_chips", "bites_filters", "bites-home", filters, filter),
                    ui.carousel("bites_collections", "Featured Cravings", collections, "bites-restaurant", "CURATED"),
                ) + visible.map(ui::listingCard),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    fun bitesRestaurant(): PageResponse {
        val orderAction =
            ui.apiCallAction(
                "bites_place_order",
                "/api/v1/bites/orders",
                buildJsonObject { put("restaurant_id", "saffron_hearth") },
            )
        return PageResponse(
            pageId = "bites-restaurant",
            title = "Saffron Hearth Kitchen",
            toolbar = ui.toolbar("Saffron Hearth Kitchen", "4.8 ★ · 24 mins · North Indian", "back_bites_rest"),
            snippets =
                listOf(
                    ui.header("bites_rest_header", "Recommended Menu", "Cooked fresh · Eco bowls", "24 MINS"),
                    ui.productGrid("bites_menu_grid", "Signature Dishes", bitesDishes),
                ),
            floatingCart =
                nudges.floatingCart(
                    label = "2 items · \$17.75",
                    eta = "24 MINS",
                    ctaLabel = "Place Meal Order",
                    action = orderAction,
                    freeDeliveryThresholdCents = 2000,
                    unlockedLabel = "FREE priority kitchen delivery!",
                ),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    fun bitesOrderTrack(): PageResponse {
        val tracker =
            nudges.orderTracker(
                snippetId = "bites_live_order_tracker",
                trackerId = "bites_order_4091",
                orderCode = "#BTS-4091",
                headline = "Karan M. is on the way from Saffron Hearth",
                etaBadge = "11 MINS",
                etaSeconds = 660,
                riderName = "Karan M.",
                riderCaption = "4.9 ★ · 1.2 km away on e-bike · Thermal bag",
                steps =
                    listOf(
                        OrderTrackerStep("b_conf", "Confirmed", "19:14", completed = true),
                        OrderTrackerStep("b_prep", "Preparing", "19:21", completed = true),
                        OrderTrackerStep("b_route", "En Route", "1.2 km", active = true),
                        OrderTrackerStep("b_done", "Delivered", "11m"),
                    ),
                primaryLabel = "Call Rider",
                primaryAction = ui.toastAction("bites_call", "Connecting masked call to Karan M."),
                secondaryLabel = "Back to Bites",
                secondaryAction = ui.navAction("bites_home_nav", "bites-home"),
            )
        val dropInfo =
            ui.actionRow(
                snippetId = "bites_contactless_row",
                title = "Contactless Doorstep Drop",
                subtitle = "Photo proof uploaded automatically when Karan M. arrives",
                icon = "check" to "status.success",
                tag = "ACTIVE" to OK_BG,
                onClick = ui.toastAction("bites_drop_pref", "Contactless drop instructions confirmed", "success"),
            )
        return PageResponse(
            pageId = "bites-order-track",
            title = "Track Order #BTS-4091",
            toolbar = ui.toolbar("Order #BTS-4091", "Arriving in 11 mins · Abeta Bites", "back_bites_track"),
            snippets = listOf(tracker, dropInfo),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    fun martHome(
        query: String = "",
        filter: String = "all",
    ): PageResponse {
        val visibleSkus = filterSkus(martSkus, query, filter)
        val deals =
            listOf(
                Triple("deal_anc", "Abeta Pulse ANC · 30% OFF", "44 dB ANC · 42h battery · \$149"),
                Triple("deal_lamp", "Nordic Desk Lamp Pro", "Adaptive glare-free LED · \$59"),
                Triple("deal_kb", "Loom Mechanical 75%", "Hot-swap tactile switches · \$119"),
            )
        val cats =
            listOf(
                "all" to "All",
                "electronics" to "Electronics",
                "audio" to "Audio",
                "home" to "Home",
                "fashion" to "Fashion",
            )
        val checkout =
            ui.apiCallAction(
                "mart_cart_order",
                "/api/v1/mart/orders",
                buildJsonObject { put("vertical", "abeta-mart") },
            )
        return PageResponse(
            pageId = "mart-home",
            title = "Abeta Mart",
            toolbar = ui.toolbar("Abeta Mart", "Multi-category · Same-day dispatch", "back_mart"),
            snippets =
                listOf(
                    ui.searchBar("mart_search_bar", "mart_search", "Search tech, audio & home...", "mart-home", query),
                    buildMartPerksMarquee(),
                    ui.chipGroup("mart_category_chips", "mart_categories", "mart-home", cats, filter),
                    ui.carousel("mart_lightning_carousel", "Lightning Deals", deals, MART_DETAIL, "DEAL"),
                    ui.productGrid("mart_products_grid", "Trending on Abeta Mart (${visibleSkus.size})", visibleSkus),
                ),
            floatingCart = nudges.floatingCart("1 item · \$149.00", "SAME DAY", "Checkout Now", checkout),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    private fun buildMartPerksMarquee(): SnippetEnvelope =
        nudges.marquee(
            snippetId = "mart_perks_marquee",
            stripId = "mart_live_perks",
            items =
                listOf(
                    MarqueeItem(
                        "m_fast",
                        "Same-day metro dispatch",
                        IconAtom(name = "star", sizeDp = 16, color = ColorTokenRef("brand.primary")),
                        TagAtom(label = "FAST", bgColor = ColorTokenRef(BRAND_BG)),
                    ),
                    MarqueeItem(
                        "m_ret",
                        "30-day instant returns",
                        IconAtom(name = "check", sizeDp = 16, color = ColorTokenRef("status.success")),
                        TagAtom(label = "PROTECTED", bgColor = ColorTokenRef(OK_BG)),
                    ),
                    MarqueeItem(
                        "m_cash",
                        "10% instant bank cashback",
                        IconAtom(name = "info", sizeDp = 16, color = ColorTokenRef("status.warning")),
                        TagAtom(label = "DEAL", bgColor = ColorTokenRef(WARN_BG)),
                        ui.toastAction("mart_cash", "10% bank cashback applies at checkout!", "success"),
                    ),
                ),
        )

    fun martProductDetail(): PageResponse {
        val buyNow =
            ui.apiCallAction("mart_buy_now", "/api/v1/mart/orders", buildJsonObject { put("product_id", "mart_anc") })
        val share =
            ui.shareAction(
                "share_mart",
                "Abeta Pulse ANC Headphones",
                "44 dB ANC headphones for \$149 on Abeta Mart!",
                "https://abeta.dev/mart/pulse-anc",
            )
        val specs =
            listOf(
                "Noise Cancellation" to "Hybrid 44 dB adaptive ANC + Transparency mode",
                "Battery & Charging" to "42 hours playback · 10 min USB-C gives 5 hours",
                "Connectivity" to "Bluetooth 5.4 Multipoint + lossless USB-C audio",
            ).mapIndexed { i, (k, v) -> ui.actionRow("mart_spec_$i", k, v, "check" to "status.success") }
        val reviews =
            listOf(
                "Rohan S. · Verified Buyer" to "Crisp vocal staging and effortless multi-device handoff.",
                "Claire D. · Verified Buyer" to "Featherweight headband even on 10-hour flights.",
            ).mapIndexed { i, (author, body) ->
                ui.actionRow("mart_rev_$i", author, body, "star" to "status.warning", "5.0 ★" to OK_BG)
            }
        return PageResponse(
            pageId = MART_DETAIL,
            title = "Abeta Pulse ANC Headphones",
            toolbar = ui.toolbar("Product Detail", "Abeta Mart · In Stock", "back_mart_detail"),
            snippets =
                listOf(
                    buildMartHeroCard(buyNow, share),
                    ui.accordion("mart_specs_accordion", "Specifications", "Drivers & battery", specs),
                    ui.accordion("mart_reviews_acc", "Customer Reviews", "4.9 ★ (1,284 reviews)", reviews),
                ),
            bottomBar =
                BottomBarSpec(
                    primaryButton = ui.button("btn_mart_bottom_buy", "Buy Now · \$149.00", buyNow, fullWidth = true),
                    helperText = ui.text("Free same-day delivery · 30-day returns", "label.small", "text.secondary"),
                ),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    private fun buildMartHeroCard(
        buyNow: ActionRef,
        share: ActionRef,
    ): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = "mart_detail_hero",
            snippetType = SnippetTypes.CARD,
            data =
                ui.encode(
                    CardSnippetData(
                        title = ui.text("Abeta Pulse ANC Headphones", "headline.small"),
                        subtitle = ui.text("4.9 ★ (1,284 reviews) · \$149.00", "title.medium", "text.brand"),
                        description =
                            ui.text(
                                "Studio 40mm drivers with adaptive ANC & same-day dispatch.",
                                "body.medium",
                                "text.secondary",
                            ),
                        media =
                            ImageAtom(
                                url = "https://cdn.abeta.dev/mart/pulse-anc.png",
                                contentDescription = "Abeta Pulse ANC Headphones",
                                heightDp = 160,
                                scaleMode = ImageScaleMode.CROP,
                                cornerRadius = CornerRadiusSpec(all = 12),
                                placeholderColor = ColorTokenRef(BRAND_BG),
                            ),
                        badge = TagAtom(label = "LIGHTNING DEAL", bgColor = ColorTokenRef(WARN_BG)),
                        primaryButton = ui.button("btn_mart_hero_buy", "Buy Now · \$149", buyNow),
                        secondaryButton = ui.button("btn_mart_hero_share", "Share", share, ButtonVariant.OUTLINE),
                        layout = ui.cardLayout,
                    ),
                ),
        )

    private fun filterSkus(
        skus: List<DemoSkuSpec>,
        query: String,
        filter: String,
    ): List<DemoSkuSpec> {
        val q = query.trim().lowercase()
        val f = filter.trim().lowercase()
        return skus
            .filter { sku ->
                val matchesFilter = f.isEmpty() || f == "all" || sku.category == f
                val matchesQuery =
                    q.isEmpty() || sku.title.lowercase().contains(q) || sku.subtitle.lowercase().contains(q)
                matchesFilter && matchesQuery
            }.ifEmpty { skus }
    }

    private fun filterListings(
        items: List<DemoListingSpec>,
        query: String,
        filter: String,
    ): List<DemoListingSpec> {
        val q = query.trim().lowercase()
        val f = filter.trim().lowercase()
        return items
            .filter { item ->
                val matchesFilter = f.isEmpty() || f == "all" || item.filterTag == f
                val matchesQuery =
                    q.isEmpty() || item.title.lowercase().contains(q) || item.subtitle.lowercase().contains(q)
                matchesFilter && matchesQuery
            }.ifEmpty { items }
    }
}
