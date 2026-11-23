package dev.abeta.kmp.storybook

import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.AdaptivePageSpec
import dev.abeta.kmp.contracts.BottomBarSpec
import dev.abeta.kmp.contracts.ButtonSize
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.CardSnippetData
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.ComposedNode
import dev.abeta.kmp.contracts.ComposedNodeKind
import dev.abeta.kmp.contracts.CornerRadiusSpec
import dev.abeta.kmp.contracts.FormSnippetData
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.ImageAtom
import dev.abeta.kmp.contracts.ImageScaleMode
import dev.abeta.kmp.contracts.KeyboardKind
import dev.abeta.kmp.contracts.MetricCardSnippetData
import dev.abeta.kmp.contracts.MotionLoopEffect
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.ProgressAtom
import dev.abeta.kmp.contracts.ProgressKind
import dev.abeta.kmp.contracts.ScreenLayoutMode
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.SwipeCardItem
import dev.abeta.kmp.contracts.SwipeDeckSnippetData
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TextFieldAtom
import dev.abeta.kmp.contracts.TypographyTokenRef
import dev.abeta.kmp.contracts.ValidationRuleContract
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val DISC_OK = "status.success_container"
private const val DISC_INFO = "status.info_container"
private const val DISC_WARN = "status.warning_container"
private const val DISC_BRAND = "brand.container"
private const val EMAIL_REGEX = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"
private const val PULSE_DETAIL = "pulse-event-detail"

/**
 * Multiplatform discovery, B2B & wearable vertical demo screens for Storybook:
 * Abeta Pulse (Events), Abeta Spark (Swipe Discovery), Abeta Trade (B2B RFQ), and Abeta Watch.
 */
@Suppress("MagicNumber", "TooManyFunctions")
internal object SuperAppDemoDiscoveryPages {
    private val ui = SuperAppDemoBuilder
    private val nudges = SuperAppDemoNudgeBuilders

    private val pulseEvents =
        listOf(
            DemoListingSpec(
                "ev_symphony",
                "Neon Horizon Live Symphony",
                "Concerts · Sat, 8:00 PM · Harbor Dome",
                "From \$30 · Instant QR · Selling fast",
                "CONCERTS" to DISC_BRAND,
                "Book Tickets",
                PULSE_DETAIL,
                "concerts",
            ),
            DemoListingSpec(
                "ev_cinema",
                "Orbital Drift — IMAX 70mm",
                "Movies · Today, 7:15 PM · Starlight",
                "From \$16 · Dolby Atmos · Recliners",
                "IMAX" to DISC_INFO,
                "Select Seats",
                PULSE_DETAIL,
                "movies",
            ),
            DemoListingSpec(
                "ev_comedy",
                "Late-Night Stand-Up Showcase",
                "Comedy & Dining · Fri, 9:00 PM · Lantern",
                "From \$22 · Includes 1 craft beverage",
                "COMEDY" to DISC_WARN,
                "Reserve Table",
                PULSE_DETAIL,
                "comedy",
            ),
        )

    private val tradeSuppliers =
        listOf(
            DemoListingSpec(
                "sup_apex",
                "Apex Corrugated Packaging",
                "Packaging · 5-ply kraft shipping cartons",
                "\$0.68/unit · MOQ 500 · 4-day dispatch",
                "GST VERIFIED" to DISC_OK,
                "Request Quote",
                "trade-supplier",
                "packaging",
            ),
            DemoListingSpec(
                "sup_nova",
                "Nova Circuit Components",
                "Electronics · IoT telemetry modules",
                "\$11.40/unit · MOQ 250 · ISO 9001",
                "MOQ 250" to DISC_INFO,
                "View Catalog",
                "trade-supplier",
                "electronics",
            ),
            DemoListingSpec(
                "sup_veridian",
                "Veridian Loom Textiles",
                "Textiles · 320 GSM organic cotton rolls",
                "\$4.90/meter · MOQ 300m · Export mill",
                "EXPORT MILL" to DISC_WARN,
                "Request Quote",
                "trade-supplier",
                "textiles",
            ),
        )

    fun pulseHome(
        query: String = "",
        filter: String = "all",
    ): PageResponse {
        val visible = filterListings(pulseEvents, query, filter)
        val cats = listOf("all" to "All", "concerts" to "Concerts", "movies" to "Movies", "comedy" to "Comedy")
        val spotNav = ui.navAction("nav_spot", PULSE_DETAIL)
        val spotBtn = ui.button("btn_spot", "Book Seats", spotNav, size = ButtonSize.SMALL)
        val passIcon =
            IconAtom(
                name = "star",
                sizeDp = 22,
                color = ColorTokenRef("brand.primary"),
                contentDescription = "Open QR pass",
                onClick = ui.navAction("nav_pulse_pass", "pulse-ticket-pass"),
            )
        val urgencyNudge =
            nudges.nudgeBar(
                snippetId = "pulse_early_bird_nudge",
                nudgeId = "pulse_early_bird",
                title = "Early-Bird Arena Gold · 18 seats left",
                subtitle = "Neon Horizon Live Symphony · Instant QR pass",
                badgeLabel = "SELLING FAST",
                badgeEffect = MotionLoopEffect.WIGGLE,
                ctaLabel = "Reserve Now",
                ctaAction = ui.navAction("nav_pulse_nudge", PULSE_DETAIL),
                countdownSeconds = 540,
                countdownLabel = "Tier closes",
                progress = 0.82f,
            )
        val search =
            ui.searchBar(
                snippetId = "pulse_search_bar",
                searchId = "pulse_search",
                placeholder = "Search concerts & movies...",
                routeBase = "pulse-home",
                query = query,
            )
        return PageResponse(
            pageId = "pulse-home",
            title = "Abeta Pulse",
            toolbar = ui.toolbar("Abeta Pulse", "Metro City · Events, Movies & Dining", "back_pulse", listOf(passIcon)),
            snippets =
                listOf(
                    search,
                    ui.chipGroup("pulse_category_chips", "pulse_categories", "pulse-home", cats, filter),
                    urgencyNudge,
                    ui.banner("pulse_spotlight", "Neon Horizon Live", "Harbor Dome · Sat 8 PM.", "SPOTLIGHT", spotBtn),
                ) + visible.map(ui::listingCard),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    fun pulseEventDetail(): PageResponse {
        val share = ui.shareAction("share_pulse", "Neon Horizon", "Join me on Abeta Pulse!", "https://abeta.dev/pulse")
        val tiers =
            listOf(
                Triple("tier_vip", "Front Row VIP · \$95", "Lounge access, signed poster & soundcheck"),
                Triple("arena_gold", "Arena Gold · \$55", "Center acoustic sweet spot + fast-track"),
                Triple("tier_balcony", "Balcony Acoustic · \$30", "Panoramic stage view & instant QR pass"),
            ).map { (tierId, title, desc) -> buildSeatTierCard(tierId, title, desc) }
        val shareBtn = ui.button("btn_pulse_share", "Share", share, ButtonVariant.OUTLINE, ButtonSize.SMALL)
        val selectGold = ui.navAction("nav_pulse_gold", "pulse-checkout?tier=arena_gold")
        val bookBtn = ui.button("btn_select_gold", "Select Arena Gold · \$110", selectGold, fullWidth = true)
        return PageResponse(
            pageId = PULSE_DETAIL,
            title = "Neon Horizon Live Symphony",
            toolbar = ui.toolbar("Event Tickets", "Abeta Pulse · Instant QR Pass", "back_pulse_detail"),
            snippets =
                listOf(
                    ui.header("pulse_hdr", "Neon Horizon", "Sat 8 PM · Harbor Dome", "SELLING FAST", shareBtn),
                ) + tiers,
            bottomBar =
                BottomBarSpec(
                    primaryButton = bookBtn,
                    helperText = ui.text("Instant QR passes · Refundable up to 24h", "label.small", "text.secondary"),
                ),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    private fun buildSeatTierCard(
        tierId: String,
        title: String,
        desc: String,
    ): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = "pulse_$tierId",
            snippetType = SnippetTypes.CARD,
            data =
                ui.encode(
                    CardSnippetData(
                        title = ui.text(title, "title.medium"),
                        description = ui.text(desc, "body.small", "text.secondary"),
                        primaryButton =
                            ui.button(
                                id = "btn_book_$tierId",
                                label = "Book Tickets",
                                onClick = ui.navAction("checkout_$tierId", "pulse-checkout?tier=$tierId"),
                                size = ButtonSize.SMALL,
                            ),
                        layout = ui.cardLayout,
                    ),
                ),
        )

    fun sparkDeck(filter: String = "nearby"): PageResponse {
        val navMatches = ui.navAction("nav_matches", "spark-matches")
        val navPrefs = ui.navAction("nav_spark_prefs", "spark-preferences")
        val matchesBtn = ui.button("btn_matches", "Matches (3)", navMatches, ButtonVariant.OUTLINE, ButtonSize.SMALL)
        val prefsIcon =
            IconAtom(
                name = "settings",
                sizeDp = 22,
                color = ColorTokenRef("brand.primary"),
                contentDescription = "Preferences",
                onClick = navPrefs,
            )
        val prefsRow =
            ui.actionRow(
                snippetId = "spark_prefs_quick_row",
                title = "Preferences",
                subtitle = "Max distance 25 km · Verified profiles only",
                icon = "settings" to "brand.primary",
                tag = "FILTERS" to DISC_BRAND,
                onClick = navPrefs,
            )
        val filters = listOf("nearby" to "Nearby", "new" to "New", "verified" to "Verified")
        val boostNudge =
            nudges.nudgeBar(
                snippetId = "spark_boost_nudge",
                nudgeId = "spark_prime_boost",
                title = "Prime Evening Boost · 3× Visibility",
                subtitle = "12 active profiles nearby — drag cards or tap action controls",
                badgeLabel = "3× BOOST",
                badgeEffect = MotionLoopEffect.HEARTBEAT,
                ctaLabel = "Boost Deck",
                ctaAction = ui.toastAction("toast_spark_boost", "3× Prime Boost active for 30 mins!", "success"),
            )
        return PageResponse(
            pageId = "spark-deck",
            title = "Abeta Spark",
            toolbar = ui.toolbar("Abeta Spark", "Curated Discovery · 12 nearby", "back_spark", listOf(prefsIcon)),
            snippets =
                listOf(
                    ui.header("spark_hdr", "Discover Nearby", "Swipe right or up to match", "4 NEW", matchesBtn),
                    prefsRow,
                    boostNudge,
                    ui.chipGroup("spark_filter_chips", "spark_filters", "spark-deck", filters, filter),
                    buildSparkDeckEnvelope(),
                ),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    fun sparkMatches(): PageResponse {
        val rows =
            listOf(
                Triple("match_maya", "Maya Chen · Matched 10m ago", "Asked: Best pour-over spot near the park?"),
                Triple("match_arjun", "Arjun Rao · Super-Liked You", "Shared a curated synth & vinyl playlist"),
                Triple("match_elena", "Elena Morales · Matched yesterday", "Recommended a rooftop jazz night"),
            ).map { (id, title, subtitle) ->
                val openChat = ui.navAction("chat_$id", "spark-chat")
                ui.actionRow(id, title, subtitle, "person" to "brand.primary", "CHAT" to DISC_OK, openChat)
            }
        val backBtn = ui.button("btn_return_deck", "Back to Deck", ui.navAction("back_to_spark", "spark-deck"))
        val backCard =
            SnippetEnvelope(
                snippetId = "spark_back_deck_card",
                snippetType = SnippetTypes.CARD,
                data =
                    ui.encode(
                        CardSnippetData(
                            title = ui.text("Keep Discovering", "title.medium"),
                            description =
                                ui.text(
                                    "Tap any match to open live chat or return to deck.",
                                    "body.small",
                                    "text.secondary",
                                ),
                            primaryButton = backBtn,
                            layout = ui.cardLayout,
                        ),
                    ),
            )
        val header = ui.header("spark_matches_header", "Mutual Sparks", "Tap a match to chat live", "3 ACTIVE")
        return PageResponse(
            pageId = "spark-matches",
            title = "Your Matches",
            toolbar = ui.toolbar("Your Matches", "Abeta Spark · 3 Mutual Connections", "back_spark_matches"),
            snippets = listOf(header) + rows + backCard,
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    fun tradeHome(
        query: String = "",
        filter: String = "all",
    ): PageResponse {
        val visible = filterListings(tradeSuppliers, query, filter)
        val filters =
            listOf(
                "all" to "All",
                "packaging" to "Packaging",
                "electronics" to "Electronics",
                "textiles" to "Textiles",
            )
        val poIcon =
            IconAtom(
                name = "notifications",
                sizeDp = 22,
                color = ColorTokenRef("brand.primary"),
                contentDescription = "Track Trade PO",
                onClick = ui.navAction("nav_trade_po", "trade-po-track?stage=2"),
            )
        val search =
            ui.searchBar(
                snippetId = "trade_search_bar",
                searchId = "trade_search",
                placeholder = "Search packaging, circuits...",
                routeBase = "trade-home",
                query = query,
            )
        return PageResponse(
            pageId = "trade-home",
            title = "Abeta Trade",
            toolbar = ui.toolbar("Abeta Trade", "Verified B2B Wholesale & Direct RFQ", "back_trade", listOf(poIcon)),
            snippets =
                listOf(
                    search,
                    ui.chipGroup("trade_filter_chips", "trade_filters", "trade-home", filters, filter),
                    buildTradeMoqBlock(),
                ) + visible.map(ui::listingCard),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    private fun buildTradeMoqBlock(): SnippetEnvelope =
        nudges.moldedCardBlock(
            snippetId = "trade_moq_savings_block",
            blockId = "trade_moq_savings",
            recipeName = "moq_bulk_savings_card",
            badgeLabel = "BULK TIER · -18%",
            badgeVariant = TagVariant.SUCCESS,
            trailingNode =
                ComposedNode(
                    nodeId = "trade_moq_price",
                    kind = ComposedNodeKind.ROLLING_COUNTER,
                    counterValue = "\$0.68 / unit",
                    counterTypography = TypographyTokenRef("title.small"),
                    counterColor = ColorTokenRef("brand.primary"),
                ),
            title = "Wholesale MOQ Bulk Savings · 500+ Units",
            subtitle = "Molded via composed_block — direct factory pricing & GST credit.",
            progress = 0.75f,
            progressLabel = "375 / 500 MOQ committed",
            footerCounter = "GST VERIFIED",
            ctaLabel = "Compare Quotes",
            ctaAction = ui.navAction("nav_trade_quotes", "trade-quotes"),
        )

    fun tradeSupplier(): PageResponse =
        PageResponse(
            pageId = "trade-supplier",
            title = "Apex Corrugated Packaging",
            toolbar = ui.toolbar("Apex Corrugated", "GST Verified · Direct Manufacturer", "back_trade_sup"),
            snippets =
                listOf(
                    ui.header("trade_sup_header", "Apex Corrugated", "ISO 9001 & FSC Mill", "GST VERIFIED"),
                    ui.metric(
                        "trade_moq_metric",
                        "Wholesale Bulk Tier",
                        "\$0.68 / unit",
                        "MOQ 500",
                        "98.6% on-time dispatch · 4 day lead time",
                    ),
                    buildTradeRfqForm(),
                ),
            adaptiveLayout = ui.centeredAdaptive,
        )

    private fun buildTradeRfqForm(): SnippetEnvelope {
        val validations =
            listOf(
                ValidationRuleContract("rfq_quantity", "required", message = "Order quantity is required"),
                ValidationRuleContract("rfq_quantity", "regex", "^\\d+$", "Enter a valid numeric unit quantity"),
                ValidationRuleContract("rfq_company", "required", message = "Company name is required"),
                ValidationRuleContract("rfq_email", "required", message = "Business email is required"),
                ValidationRuleContract("rfq_email", "regex", EMAIL_REGEX, "Enter a valid business email"),
            )
        val submit =
            ui.apiCallAction(
                "trade_submit_rfq",
                "/api/v1/trade/rfq",
                buildJsonObject { put("supplier_id", "apex_corrugated") },
                validations,
            )
        val desc = ui.text("Submit RFQ to compare 3 live factory quotes instantly.", "body.small", "text.secondary")
        return SnippetEnvelope(
            snippetId = "trade_rfq_form_snippet",
            snippetType = SnippetTypes.FORM,
            data =
                ui.encode(
                    FormSnippetData(
                        formId = "trade_rfq_form",
                        title = ui.text("Request Direct Wholesale Quote (RFQ)", "title.medium"),
                        description = desc,
                        fields = buildTradeRfqFields(),
                        submitButton = ui.button("btn_submit_rfq", "Submit RFQ & View Quotes", submit),
                        layout = ui.cardLayout,
                    ),
                ),
        )
    }

    private fun buildTradeRfqFields(): List<TextFieldAtom> =
        listOf(
            TextFieldAtom(
                fieldId = "rfq_quantity",
                label = "Order Quantity (Units)",
                placeholder = "500",
                initialValue = "500",
                helperText = "MOQ: 500 units",
                required = true,
                regex = "^\\d+$",
                keyboardType = KeyboardKind.NUMBER,
            ),
            TextFieldAtom(
                fieldId = "rfq_company",
                label = "Company Name",
                placeholder = "Northstar Retail",
                initialValue = "Northstar Retail",
                required = true,
            ),
            TextFieldAtom(
                fieldId = "rfq_email",
                label = "Business Email",
                placeholder = "procurement@northstar.example",
                initialValue = "procurement@northstar.example",
                required = true,
                regex = EMAIL_REGEX,
                keyboardType = KeyboardKind.EMAIL,
            ),
        )

    fun watchStatus(): PageResponse {
        val reorder =
            ui.apiCallAction(
                "watch_express_reorder",
                "/api/v1/orders/express",
                buildJsonObject { put("source", "watch_companion") },
            )
        val share =
            ui.shareAction(
                "watch_share_eta",
                "Abeta Fresh Delivery",
                "My express order arrives in 8 minutes!",
                "https://abeta.dev",
            )
        val progress =
            ProgressAtom(
                kind = ProgressKind.LINEAR,
                progress = 0.78f,
                label = "Arriving in 8 mins",
                color = ColorTokenRef("brand.primary"),
            )
        val etaMetric =
            SnippetEnvelope(
                snippetId = "watch_eta_metric",
                snippetType = SnippetTypes.METRIC_CARD,
                data =
                    ui.encode(
                        MetricCardSnippetData(
                            label = ui.text("Express SLA", "label.small", "text.secondary"),
                            value = ui.text("8 mins", "title.medium", "brand.primary"),
                            progress = progress,
                            layout = ui.cardLayout,
                        ),
                    ),
            )
        val primary = ui.button("watch_reorder_btn", "1-Tap Reorder", reorder, size = ButtonSize.SMALL)
        val secondary = ui.button("watch_share_btn", "Share ETA", share, ButtonVariant.OUTLINE, ButtonSize.SMALL)
        val orderCard =
            SnippetEnvelope(
                snippetId = "watch_active_order",
                snippetType = SnippetTypes.CARD,
                data =
                    ui.encode(
                        CardSnippetData(
                            title = ui.text("Rider En Route · 8 min", "title.small"),
                            subtitle = ui.text("Abeta Fresh · Bag #AF-409", "body.small", "text.secondary"),
                            badge = TagAtom(label = "LIVE", variant = TagVariant.SUCCESS),
                            primaryButton = primary,
                            secondaryButton = secondary,
                            layout = ui.cardLayout,
                        ),
                    ),
            )
        return PageResponse(
            pageId = "watch-status",
            title = "Abeta Watch",
            adaptiveLayout = AdaptivePageSpec(layoutMode = ScreenLayoutMode.SINGLE_COLUMN, maxWidthDp = 280),
            snippets = listOf(etaMetric, orderCard),
        )
    }

    private fun buildSparkDeckEnvelope(): SnippetEnvelope {
        val cards =
            listOf(
                swipeCard(
                    "spark_maya",
                    "Maya Chen, 28",
                    "Product Designer · Studio K",
                    "Trail runner, espresso nerd, and weekend ceramicist.",
                    "2.4 km",
                    "VERIFIED" to DISC_OK,
                    listOf("Design", "Coffee", "Hiking"),
                ),
                swipeCard(
                    "spark_arjun",
                    "Arjun Rao, 30",
                    "Acoustic Architect · Soundcraft",
                    "Building modular synths and exploring indie vinyl shops.",
                    "3.1 km",
                    "ACTIVE" to DISC_INFO,
                    listOf("Vinyl", "Synths", "Cycling"),
                ),
                swipeCard(
                    "spark_elena",
                    "Elena Morales, 27",
                    "Culinary Photographer",
                    "Always hunting neighborhood tacos and rooftop jazz.",
                    "4.8 km",
                    "VERIFIED" to DISC_WARN,
                    listOf("Photo", "Jazz", "Food"),
                ),
                swipeCard(
                    "spark_noah",
                    "Noah Park, 29",
                    "Marine Robotics Engineer",
                    "Sailing on Sundays, simmering ramen from scratch.",
                    "5.5 km",
                    "NEW" to DISC_BRAND,
                    listOf("Sailing", "Ramen", "Robotics"),
                ),
            )
        val emptyMsg = ui.text("Reset the deck or message your mutual matches.", "body.small", "text.secondary")
        return SnippetEnvelope(
            snippetId = "spark_swipe_deck",
            snippetType = SnippetTypes.SWIPE_DECK,
            data =
                ui.encode(
                    SwipeDeckSnippetData(
                        deckId = "spark_discovery_deck",
                        cards = cards,
                        emptyTitle = ui.text("You've seen everyone nearby", "title.medium"),
                        emptyMessage = emptyMsg,
                        onLike = sparkSwipeAction("deck", "like"),
                        onNope = sparkSwipeAction("deck", "nope"),
                        onSuperLike = sparkSwipeAction("deck", "super_like"),
                        onUndo = ui.toastAction("spark_undo", "Rewound previous discovery card"),
                        layout = ui.horizontalLayout,
                    ),
                ),
        )
    }

    @Suppress("LongParameterList")
    private fun swipeCard(
        id: String,
        nameAge: String,
        role: String,
        bio: String,
        distance: String,
        badge: Pair<String, String>,
        tags: List<String>,
    ): SwipeCardItem =
        SwipeCardItem(
            cardId = id,
            title = ui.text(nameAge, "title.large"),
            subtitle = ui.text(role, "label.large", "text.brand"),
            bio = ui.text(bio, "body.medium", "text.secondary"),
            badge = TagAtom(label = badge.first, bgColor = ColorTokenRef(badge.second)),
            distanceText = ui.text(distance, "label.medium", "text.secondary"),
            image =
                ImageAtom(
                    url = "https://cdn.abeta.dev/spark/$id.png",
                    contentDescription = nameAge,
                    heightDp = 190,
                    scaleMode = ImageScaleMode.CROP,
                    cornerRadius = CornerRadiusSpec(all = 12),
                    placeholderColor = ColorTokenRef(badge.second),
                ),
            tags = tags.map { TagAtom(label = it, bgColor = ColorTokenRef("surface.secondary")) },
            onLike = sparkSwipeAction(id, "like"),
            onNope = sparkSwipeAction(id, "nope"),
            onSuperLike = sparkSwipeAction(id, "super_like"),
        )

    private fun sparkSwipeAction(
        profileId: String,
        action: String,
    ): ActionRef =
        ui.apiCallAction(
            "swipe_${profileId}_$action",
            "/api/v1/spark/swipe",
            buildJsonObject {
                put("profile_id", profileId)
                put("action", action)
            },
        )

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
