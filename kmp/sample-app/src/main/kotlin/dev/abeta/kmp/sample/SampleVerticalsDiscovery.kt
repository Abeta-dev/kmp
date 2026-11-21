package dev.abeta.kmp.sample

import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.BottomBarSpec
import dev.abeta.kmp.contracts.ButtonSize
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.CardSnippetData
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.CornerRadiusSpec
import dev.abeta.kmp.contracts.FormSnippetData
import dev.abeta.kmp.contracts.ImageAtom
import dev.abeta.kmp.contracts.ImageScaleMode
import dev.abeta.kmp.contracts.KeyboardKind
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.SwipeCardItem
import dev.abeta.kmp.contracts.SwipeDeckSnippetData
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TextFieldAtom
import dev.abeta.kmp.contracts.ValidationRuleContract
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val DISC_OK = "status.success_container"
private const val DISC_INFO = "status.info_container"
private const val DISC_WARN = "status.warning_container"
private const val DISC_BRAND = "brand.container"
private const val EMAIL_REGEX = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"
private const val PULSE_DETAIL = "pulse-event-detail"
private const val TRADE_SUP = "trade-supplier"

/**
 * Offline fallback SDUI pages for discovery & B2B verticals: Abeta Pulse, Abeta Spark, and Abeta Trade.
 */
@Suppress("MagicNumber", "TooManyFunctions")
internal object SampleVerticalsDiscovery {
    private val ui = SampleScreens

    private val pulseEvents =
        listOf(
            ListingCardSpec(
                "ev_symphony",
                "Neon Horizon Live Symphony",
                "Concerts · Sat, 8:00 PM · Harbor Dome",
                "From \$30 · Instant QR · Selling fast",
                "CONCERTS" to DISC_BRAND,
                "Book Tickets",
                PULSE_DETAIL,
            ),
            ListingCardSpec(
                "ev_cinema",
                "Orbital Drift — IMAX 70mm",
                "Movies · Today, 7:15 PM · Starlight",
                "From \$16 · Dolby Atmos · Recliners",
                "IMAX" to DISC_INFO,
                "Select Seats",
                PULSE_DETAIL,
            ),
            ListingCardSpec(
                "ev_comedy",
                "Late-Night Stand-Up Showcase",
                "Comedy & Dining · Fri, 9:00 PM · Lantern",
                "From \$22 · Includes 1 craft beverage",
                "COMEDY" to DISC_WARN,
                "Reserve Table",
                PULSE_DETAIL,
            ),
        )

    private val tradeSuppliers =
        listOf(
            ListingCardSpec(
                "sup_apex",
                "Apex Corrugated Packaging",
                "Packaging · 5-ply kraft shipping cartons",
                "\$0.68/unit · MOQ 500 · 4-day dispatch",
                "GST VERIFIED" to DISC_OK,
                "Request Quote",
                TRADE_SUP,
            ),
            ListingCardSpec(
                "sup_nova",
                "Nova Circuit Components",
                "Electronics · IoT telemetry modules",
                "\$11.40/unit · MOQ 250 · ISO 9001",
                "MOQ 250" to DISC_INFO,
                "View Catalog",
                TRADE_SUP,
            ),
            ListingCardSpec(
                "sup_veridian",
                "Veridian Loom Textiles",
                "Textiles · 320 GSM organic cotton rolls",
                "\$4.90/meter · MOQ 300m · Export mill",
                "EXPORT MILL" to DISC_WARN,
                "Request Quote",
                TRADE_SUP,
            ),
        )

    private val rfqFields =
        listOf(
            TextFieldAtom(
                "rfq_quantity",
                "Order Quantity (Units)",
                "500",
                "500",
                "MOQ: 500 units",
                required = true,
                regex = "^\\d+$",
                keyboardType = KeyboardKind.NUMBER,
            ),
            TextFieldAtom("rfq_company", "Company Name", "Northstar Retail Pvt Ltd", required = true),
            TextFieldAtom(
                "rfq_email",
                "Business Email",
                "procurement@northstar.example",
                required = true,
                regex = EMAIL_REGEX,
                keyboardType = KeyboardKind.EMAIL,
            ),
        )

    fun pulseHome(): PageResponse {
        val spotNav = ui.navAction("nav_spot", PULSE_DETAIL)
        val spotBtn = ui.button("btn_spot", "Book Seats", spotNav, size = ButtonSize.SMALL)
        val cats =
            listOf("all" to "All", "movies" to "Movies", "concerts" to "Concerts", "comedy" to "Comedy") +
                ("dining" to "Dining")
        val search = ui.searchBarEnvelope("pulse_search_bar", "pulse_search", "Search concerts & movies", "pulse-home")
        val chips = ui.chipGroupEnvelope("pulse_category_chips", "pulse_categories", "pulse-home", cats)
        val banner =
            ui.bannerEnvelope("pulse_spotlight", "Neon Horizon Live", "Harbor Dome · Sat 8 PM.", "SPOTLIGHT", spotBtn)
        return PageResponse(
            pageId = "pulse-home",
            title = "Abeta Pulse",
            toolbar = ui.verticalToolbar("Abeta Pulse", "Metro City · Events, Movies & Dining", "back_pulse"),
            snippets = listOf(search, chips, banner) + pulseEvents.map(ui::listingCardEnvelope),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    fun pulseEventDetail(): PageResponse {
        val share = ui.shareAction("share_pulse", "Neon Horizon", "Join me on Abeta Pulse!", "https://abeta.dev/pulse")
        val tiers =
            listOf(
                Triple("tier_vip", "Front Row VIP · \$95", "Lounge access, signed poster & soundcheck"),
                Triple("tier_gold", "Arena Gold · \$55", "Center acoustic sweet spot + fast-track"),
                Triple("tier_balcony", "Balcony Acoustic · \$30", "Panoramic stage view & instant QR pass"),
            ).map { (tierId, title, desc) -> buildSeatTierCard(tierId, title, desc) }
        val shareBtn = ui.button("btn_pulse_share", "Share", share, ButtonVariant.OUTLINE, ButtonSize.SMALL)
        val header = ui.headerEnvelope("pulse_hdr", "Neon Horizon", "Sat 8 PM · Harbor Dome", "SELLING FAST", shareBtn)
        val shareRow =
            ui.actionRowEnvelope(
                "pulse_share_row",
                "Share Event",
                "Send venue link",
                "info" to "brand.primary",
                "SHARE" to DISC_BRAND,
                share,
            )
        val bookBtn = ui.button("btn_book_gold", "Book 2 Gold · \$110", pulseBookAction("arena_gold"), fullWidth = true)
        val helper = ui.text("Instant QR passes · Refundable up to 24h", "label.small", "text.secondary")
        return PageResponse(
            pageId = PULSE_DETAIL,
            title = "Neon Horizon Live Symphony",
            toolbar = ui.verticalToolbar("Event Tickets", "Abeta Pulse · Instant QR Pass", "back_pulse_detail"),
            snippets = listOf(header) + tiers + listOf(shareRow),
            bottomBar = BottomBarSpec(primaryButton = bookBtn, helperText = helper),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    fun sparkDeck(): PageResponse {
        val navMatches = ui.navAction("nav_matches", "spark-matches")
        val matchesBtn = ui.button("btn_matches", "Matches (3)", navMatches, ButtonVariant.OUTLINE, ButtonSize.SMALL)
        val filters = listOf("nearby" to "Nearby", "new" to "New", "verified" to "Verified")
        val header = ui.headerEnvelope("spark_hdr", "Discover Nearby", "Swipe right or up", "4 NEW", matchesBtn)
        val chips = ui.chipGroupEnvelope("spark_filter_chips", "spark_filters", "spark-deck", filters)
        return PageResponse(
            pageId = "spark-deck",
            title = "Abeta Spark",
            toolbar = ui.verticalToolbar("Abeta Spark", "Curated Discovery · 12 nearby", "back_spark"),
            snippets = listOf(header, chips, buildSparkDeckEnvelope()),
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
                val toast = ui.toastAction("msg_$id", "Opened chat with ${title.substringBefore(" ·")}", "success")
                ui.actionRowEnvelope(id, title, subtitle, "person" to "brand.primary", "SPARK" to DISC_OK, toast)
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
                            description = ui.text("Return to your Spark card stack.", "body.small", "text.secondary"),
                            primaryButton = backBtn,
                            layout = ui.cardLayout,
                        ),
                    ),
            )
        val header = ui.headerEnvelope("spark_matches_header", "Mutual Sparks", "Reply within 24h", "3 ACTIVE")
        return PageResponse(
            pageId = "spark-matches",
            title = "Your Matches",
            toolbar = ui.verticalToolbar("Your Matches", "Abeta Spark · 3 Mutual Connections", "back_spark_matches"),
            snippets = listOf(header) + rows + listOf(backCard),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    fun tradeHome(): PageResponse {
        val filters =
            listOf(
                "all" to "All",
                "gst" to "GST Verified",
                "electronics" to "Electronics",
                "packaging" to "Packaging",
                "textiles" to "Textiles",
            )
        val search = ui.searchBarEnvelope("trade_search_bar", "trade_search", "Search wholesale...", "trade-home")
        val chips = ui.chipGroupEnvelope("trade_filter_chips", "trade_filters", "trade-home", filters)
        return PageResponse(
            pageId = "trade-home",
            title = "Abeta Trade",
            toolbar = ui.verticalToolbar("Abeta Trade", "Verified B2B Wholesale & Direct RFQ", "back_trade"),
            snippets = listOf(search, chips) + tradeSuppliers.map(ui::listingCardEnvelope),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    fun tradeSupplier(): PageResponse {
        val header = ui.headerEnvelope("trade_sup_header", "Apex Corrugated", "ISO 9001 & FSC Mill", "GST VERIFIED")
        val metric =
            ui.metricEnvelope(
                "trade_moq_metric",
                "Wholesale Bulk Tier",
                "\$0.68 / unit",
                "MOQ 500",
                caption = "98.6% on-time dispatch · 4 business day lead time",
            )
        return PageResponse(
            pageId = TRADE_SUP,
            title = "Apex Corrugated Packaging",
            toolbar = ui.verticalToolbar("Apex Corrugated", "GST Verified · Direct Manufacturer", "back_trade_sup"),
            snippets = listOf(header, metric, buildTradeRfqForm()),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

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
        val desc = ui.text("Receive tiered bulk pricing & GST invoice within 2h.", "body.small", "text.secondary")
        return SnippetEnvelope(
            snippetId = "trade_rfq_form_snippet",
            snippetType = SnippetTypes.FORM,
            data =
                ui.encode(
                    FormSnippetData(
                        formId = "trade_rfq_form",
                        title = ui.text("Request Direct Wholesale Quote (RFQ)", "title.medium"),
                        description = desc,
                        fields = rfqFields,
                        submitButton = ui.button("btn_submit_rfq", "Submit RFQ Quote", submit),
                        layout = ui.cardLayout,
                    ),
                ),
        )
    }

    private fun buildSparkDeckEnvelope(): SnippetEnvelope {
        val cards =
            listOf(
                buildSwipeCard(
                    "spark_maya",
                    "Maya Chen, 28",
                    "Product Designer · Studio K",
                    "Trail runner, espresso nerd, and weekend ceramicist.",
                    "2.4 km",
                    "VERIFIED" to DISC_OK,
                    listOf("Design", "Coffee", "Hiking"),
                ),
                buildSwipeCard(
                    "spark_arjun",
                    "Arjun Rao, 30",
                    "Acoustic Architect · Soundcraft",
                    "Building modular synths and exploring indie vinyl shops.",
                    "3.1 km",
                    "ACTIVE" to DISC_INFO,
                    listOf("Vinyl", "Synths", "Cycling"),
                ),
                buildSwipeCard(
                    "spark_elena",
                    "Elena Morales, 27",
                    "Culinary Photographer",
                    "Always hunting neighborhood tacos and rooftop jazz.",
                    "4.8 km",
                    "VERIFIED" to DISC_WARN,
                    listOf("Photo", "Jazz", "Food"),
                ),
                buildSwipeCard(
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
                        layout = ui.horizontalPaddingLayout,
                    ),
                ),
        )
    }

    private fun buildSeatTierCard(
        tierId: String,
        title: String,
        desc: String,
    ): SnippetEnvelope {
        val btn = ui.button("btn_book_$tierId", "Book Tickets", pulseBookAction(tierId), size = ButtonSize.SMALL)
        return SnippetEnvelope(
            snippetId = "pulse_$tierId",
            snippetType = SnippetTypes.CARD,
            data =
                ui.encode(
                    CardSnippetData(
                        title = ui.text(title, "title.medium"),
                        description = ui.text(desc, "body.small", "text.secondary"),
                        primaryButton = btn,
                        layout = ui.cardLayout,
                    ),
                ),
        )
    }

    private fun pulseBookAction(tier: String): ActionRef =
        ui.apiCallAction(
            "book_$tier",
            "/api/v1/pulse/bookings",
            buildJsonObject {
                put("event_id", "neon_horizon_symphony")
                put("tier", tier)
                put("quantity", 2)
            },
        )

    @Suppress("LongParameterList")
    private fun buildSwipeCard(
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
                    heightDp = 220,
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
}
