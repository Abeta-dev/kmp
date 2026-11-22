package dev.abeta.kmp.sample

import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.BottomBarSpec
import dev.abeta.kmp.contracts.ButtonSize
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.CardSnippetData
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.CornerRadiusSpec
import dev.abeta.kmp.contracts.GridSnippetData
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.ImageAtom
import dev.abeta.kmp.contracts.ImageScaleMode
import dev.abeta.kmp.contracts.MotionDurationToken
import dev.abeta.kmp.contracts.MotionSpec
import dev.abeta.kmp.contracts.MotionTransition
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.ProductCardSnippetData
import dev.abeta.kmp.contracts.ResponsiveColumnsSpec
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.StepperAtom
import dev.abeta.kmp.contracts.TagAtom
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val OK_BG = "status.success_container"
private const val INFO_BG = "status.info_container"
private const val WARN_BG = "status.warning_container"
private const val BRAND_BG = "brand.container"
private const val MART_DETAIL = "mart-product-detail"
private const val BITES_REST = "bites-restaurant"

private data class CommerceSku(
    val id: String,
    val title: String,
    val subtitle: String,
    val cents: Int,
    val badge: Pair<String, String>,
    val qty: Int = 0,
    val route: String? = null,
)

/**
 * Offline fallback SDUI pages for commerce verticals: Abeta Fresh, Abeta Bites, and Abeta Mart.
 */
@Suppress("MagicNumber", "TooManyFunctions")
internal object SampleVerticalsCommerce {
    private val ui = SampleScreens

    private val freshSkus =
        listOf(
            CommerceSku("fresh_avocados", "Hass Avocados", "2 pcs · Ready", 349, "8 MINS" to OK_BG, 1),
            CommerceSku("fresh_milk", "A2 Whole Milk", "1 L · Chilled", 329, "CHILLED" to INFO_BG, 1),
            CommerceSku("fresh_sourdough", "Sourdough Loaf", "450 g · Baked", 499, "BAKERY" to WARN_BG),
            CommerceSku("fresh_berries", "Strawberries", "250 g · Farm", 449, "ORGANIC" to BRAND_BG),
            CommerceSku("fresh_cold_brew", "Oat Cold Brew", "250 ml · Zero", 379, "DRINKS" to INFO_BG),
            CommerceSku("fresh_almonds", "Sea Salt Almonds", "180 g · Roast", 549, "SNACKS" to WARN_BG),
        )

    private val bitesRestaurants =
        listOf(
            ListingCardSpec(
                "rest_saffron",
                "Saffron Hearth Kitchen",
                "North Indian · Biryani · Kebabs",
                "4.8 ★ · 24 mins · \$14 for two · Free Delivery",
                "TOP RATED" to OK_BG,
                "View Menu",
                BITES_REST,
            ),
            ListingCardSpec(
                "rest_cedar",
                "Cedar Crust Pizzeria",
                "Wood-Fired Pizza · Fresh Pasta",
                "4.7 ★ · 20 mins · \$18 for two · Flat \$3 OFF",
                "FAST 20M" to WARN_BG,
                "View Menu",
                BITES_REST,
            ),
            ListingCardSpec(
                "rest_bamboo",
                "Bamboo Noodle Foundry",
                "Pan-Asian · Hand-Pulled Ramen",
                "4.6 ★ · 28 mins · Pure Veg Options",
                "PURE VEG+" to INFO_BG,
                "View Menu",
                BITES_REST,
            ),
        )

    private val bitesDishes =
        listOf(
            CommerceSku("dish_biryani", "Smoked Veg Biryani", "Mint raita & salan", 950, "CHEF" to OK_BG, 1),
            CommerceSku("dish_paneer", "Charred Paneer Tikka", "Tandoor · 6 pcs", 825, "PURE VEG" to INFO_BG, 1),
            CommerceSku("dish_naan", "Garlic Herb Naan", "2 buttered breads", 350, "HOT" to WARN_BG),
            CommerceSku("dish_lassi", "Cardamom Mango Lassi", "Chilled · 350 ml", 425, "CHILLED" to BRAND_BG),
        )

    private val martSkus =
        listOf(
            CommerceSku("mart_anc", "Pulse ANC Headset", "44 dB ANC · 42h", 14900, "-30%" to WARN_BG, 1, MART_DETAIL),
            CommerceSku("mart_lamp", "Nordic Desk Lamp", "CRI 97+ · Dimmer", 5900, "PICK" to BRAND_BG, 0, MART_DETAIL),
            CommerceSku("mart_kb", "Loom Mechanical 75%", "Gasket · Tri-mode", 11900, "NEW" to INFO_BG, 0, MART_DETAIL),
            CommerceSku("mart_hoodie", "Merino Hoodie", "Breathable weave", 8400, "FASHION" to OK_BG, 0, MART_DETAIL),
        )

    fun freshHome(): PageResponse {
        val expressOrder =
            ui.apiCallAction(
                "fresh_express_checkout",
                "/api/v1/fresh/orders/express",
                buildJsonObject { put("vertical", "abeta-fresh") },
            )
        val freeCta =
            ui.button(
                "btn_fresh_free",
                "FREE DELIVERY",
                ui.toastAction("fresh_free", "Free 8-min express delivery unlocked!", "success"),
                ButtonVariant.OUTLINE,
                ButtonSize.SMALL,
            )
        val chips = listOf("produce" to "Produce", "dairy" to "Dairy", "snacks" to "Snacks", "drinks" to "Drinks")
        val search = ui.searchBarEnvelope("fresh_search_bar", "fresh_search", "Search produce & dairy...", "fresh-home")
        val banner =
            ui.bannerEnvelope(
                "fresh_eta_banner",
                "8-Minute Express Grocery",
                "Chilled produce, dairy & bakery dispatched directly from Abeta Fresh Hub.",
                "8 MINS",
                freeCta,
            )
        return PageResponse(
            pageId = "fresh-home",
            title = "Abeta Fresh",
            toolbar = ui.verticalToolbar("Abeta Fresh", "Express Hub · Delivery in 8 mins", "back_fresh"),
            snippets =
                listOf(
                    search,
                    SampleNudgeAndTrackerPrimitives.expressPerksMarquee("fresh_perks_marquee", "fresh_express_perks"),
                    ui.chipGroupEnvelope("fresh_category_chips", "fresh_categories", "fresh-home", chips),
                    SampleNudgeAndTrackerPrimitives.flashDropNudgeBar("fresh_flash_nudge", "fresh_flash_drop"),
                    banner,
                    SampleComposedBlockRecipes.loyaltyStampComposedBlock("fresh_loyalty_block", "fresh_loyalty_stamps"),
                    buildProductGrid("fresh_grid", "Express Essentials", freshSkus),
                ),
            floatingCart =
                SampleNudgeAndTrackerPrimitives.expressFloatingCart(
                    label = "2 items · \$6.78",
                    ctaLabel = "Express Checkout",
                    action = expressOrder,
                ),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    fun bitesHome(): PageResponse {
        val collections =
            listOf(
                Triple("col_biryani", "Chef's Biryani Vault", "Slow-cooked aromatic bowls · 20% OFF"),
                Triple("col_pizza", "Wood-Fired Crusts", "Sourdough pies baked in 90 seconds"),
                Triple("col_bowls", "Protein Power Bowls", "Macro-balanced bowls under 550 kcal"),
            )
        val filters =
            listOf(
                "all" to "All",
                "pure_veg" to "Pure Veg",
                "rating_4" to "Rating 4.0+",
                "fast" to "Fast Delivery",
                "biryani" to "Biryani",
                "pizza" to "Pizza",
            )
        val trackIcon =
            IconAtom(
                name = "notifications",
                sizeDp = 22,
                color = ColorTokenRef("brand.primary"),
                contentDescription = "Track active order",
                onClick = ui.navAction("nav_bites_track", "bites-order-track"),
            )
        val search = ui.searchBarEnvelope("bites_search_bar", "bites_search", "Search biryani, pizza...", "bites-home")
        val carousel = ui.carouselEnvelope("bites_collections", "Featured Cravings", collections, BITES_REST, "CURATED")
        return PageResponse(
            pageId = "bites-home",
            title = "Abeta Bites",
            toolbar = ui.verticalToolbar("Abeta Bites", "Hot meals · Live tracking", "back_bites", listOf(trackIcon)),
            snippets =
                listOf(
                    search,
                    ui.chipGroupEnvelope("bites_filter_chips", "bites_filters", "bites-home", filters),
                    carousel,
                ) + bitesRestaurants.map(ui::listingCardEnvelope),
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
        val header = ui.headerEnvelope("bites_rest_header", "Recommended Menu", "Cooked fresh · Eco bowls", "24 MINS")
        return PageResponse(
            pageId = BITES_REST,
            title = "Saffron Hearth Kitchen",
            toolbar = ui.verticalToolbar("Saffron Hearth Kitchen", "4.8 ★ · 24 mins · North Indian", "back_bites_rest"),
            snippets = listOf(header, buildProductGrid("bites_menu_grid", "Signature Dishes", bitesDishes)),
            floatingCart = ui.floatingCart("2 items · \$17.75", "24 MINS", "Place Meal Order", orderAction),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    fun bitesOrderTrack(): PageResponse {
        val dropInfo =
            ui.actionRowEnvelope(
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
            toolbar = ui.verticalToolbar("Order #BTS-4091", "Arriving in 11 mins · Abeta Bites", "back_bites_track"),
            snippets = listOf(SampleNudgeAndTrackerPrimitives.bitesOrderTrackerEnvelope(), dropInfo),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    fun martHome(): PageResponse {
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
        val search = ui.searchBarEnvelope("mart_search_bar", "mart_search", "Search tech, home & audio...", "mart-home")
        val carousel = ui.carouselEnvelope("mart_lightning_carousel", "Lightning Deals", deals, MART_DETAIL, "DEAL")
        return PageResponse(
            pageId = "mart-home",
            title = "Abeta Mart",
            toolbar = ui.verticalToolbar("Abeta Mart", "Multi-category · Same-day dispatch", "back_mart"),
            snippets =
                listOf(
                    search,
                    SampleNudgeAndTrackerPrimitives.martPerksMarquee(),
                    ui.chipGroupEnvelope("mart_category_chips", "mart_categories", "mart-home", cats),
                    carousel,
                    buildProductGrid("mart_products_grid", "Trending on Abeta Mart", martSkus),
                ),
            floatingCart = ui.floatingCart("1 item · \$149.00", "SAME DAY", "Checkout Now", checkout),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

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
            ).mapIndexed { i, (k, v) -> ui.actionRowEnvelope("mart_spec_$i", k, v, "check" to "status.success") }
        val reviews =
            listOf(
                "Rohan S. · Verified Buyer" to "Crisp vocal staging and effortless multi-device handoff.",
                "Claire D. · Verified Buyer" to "Featherweight headband even on 10-hour flights.",
            ).mapIndexed { i, (author, body) ->
                ui.actionRowEnvelope("mart_rev_$i", author, body, "star" to "status.warning", "5.0 ★" to OK_BG)
            }
        val specsAcc = ui.accordionEnvelope("mart_specs_accordion", "Specifications", "Drivers & battery", specs)
        val revAcc = ui.accordionEnvelope("mart_reviews_acc", "Customer Reviews", "4.9 ★ (1,284 reviews)", reviews)
        return PageResponse(
            pageId = MART_DETAIL,
            title = "Abeta Pulse ANC Headphones",
            toolbar = ui.verticalToolbar("Product Detail", "Abeta Mart · In Stock", "back_mart_detail"),
            snippets = listOf(buildMartHeroCard(buyNow, share), specsAcc, revAcc),
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
                                heightDp = 172,
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

    private fun buildProductGrid(
        gridId: String,
        title: String,
        items: List<CommerceSku>,
    ): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = gridId,
            snippetType = SnippetTypes.GRID,
            data =
                ui.encode(
                    GridSnippetData(
                        title = ui.text(title, "title.medium"),
                        columns = 2,
                        columnsByWidth = ResponsiveColumnsSpec(compact = 2, medium = 3, expanded = 4),
                        spacingDp = 12,
                        items = items.mapIndexed { idx, sku -> buildProductCard(gridId, idx, sku) },
                        layout = ui.horizontalPaddingLayout,
                    ),
                ),
        )

    private fun buildProductCard(
        parentId: String,
        index: Int,
        sku: CommerceSku,
    ): SnippetEnvelope {
        val price = ui.formatUsd(sku.cents)
        val onClick =
            sku.route?.let { ui.navAction("open_${sku.id}", it) }
                ?: ui.toastAction("peek_${sku.id}", "${sku.title} ($price)")
        val image =
            ImageAtom(
                url = "https://cdn.abeta.dev/catalog/${sku.id}.png",
                contentDescription = sku.title,
                heightDp = 100,
                scaleMode = ImageScaleMode.CROP,
                cornerRadius = CornerRadiusSpec(all = 8),
                placeholderColor = ColorTokenRef(sku.badge.second),
            )
        val motion =
            MotionSpec(
                enter = MotionTransition.SCALE_IN,
                durationToken = MotionDurationToken.FAST,
                staggerMs = 50,
                delayMs = index * 50,
            )
        return SnippetEnvelope(
            snippetId = "prod_${sku.id}",
            snippetParentId = parentId,
            snippetType = SnippetTypes.PRODUCT_CARD,
            data =
                ui.encode(
                    ProductCardSnippetData(
                        productId = sku.id,
                        title = ui.text(sku.title, "title.small"),
                        subtitle = ui.text(sku.subtitle, "body.small", "text.secondary"),
                        price = ui.text(price, "label.large"),
                        originalPrice = ui.text(ui.formatUsd(sku.cents + 80), "body.small", "text.tertiary"),
                        unitPriceCents = sku.cents,
                        badge = TagAtom(label = sku.badge.first, bgColor = ColorTokenRef(sku.badge.second)),
                        image = image,
                        stepper = StepperAtom("cart_qty_${sku.id}", "ADD", sku.qty, maxQuantity = 10),
                        rating = ui.text("4.8 ★", "label.small", "status.success"),
                        onClick = onClick,
                        layout = storeCompactCardLayout,
                    ),
                ),
            motion = motion,
        )
    }
}
