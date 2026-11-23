package dev.abeta.kmp.storybook

import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.AvatarAtom
import dev.abeta.kmp.contracts.ButtonSize
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.CardSnippetData
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.ComposedBlockSnippetData
import dev.abeta.kmp.contracts.ComposedNode
import dev.abeta.kmp.contracts.ComposedNodeKind
import dev.abeta.kmp.contracts.DividerAtom
import dev.abeta.kmp.contracts.FormSnippetData
import dev.abeta.kmp.contracts.GridSnippetData
import dev.abeta.kmp.contracts.KeyboardKind
import dev.abeta.kmp.contracts.MotionLoopEffect
import dev.abeta.kmp.contracts.MotionSpec
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.ResponsiveColumnsSpec
import dev.abeta.kmp.contracts.SliderAtom
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.SwitchAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TextFieldAtom
import dev.abeta.kmp.contracts.ValidationRuleContract
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val DWF_OK = "status.success_container"
private const val DWF_INFO = "status.info_container"
private const val DWF_WARN = "status.warning_container"
private const val DWF_BRAND = "brand.container"
private const val DWF_EMAIL_REGEX = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$"
private const val DWF_PHONE_REGEX = "^[+]?[0-9\\-\\s]{7,16}$"

private data class TradeQuoteSpec(
    val id: String,
    val supplier: String,
    val unitPrice: String,
    val summary: String,
    val badge: Pair<String, String>,
)

/**
 * Multi-step discovery & B2B workflow sub-screens (event checkout, digital QR pass, Spark chat
 * & preferences, factory quote comparison, and B2B PO tracker) for Storybook.
 */
@Suppress("MagicNumber", "TooManyFunctions")
internal object SuperAppDemoDiscoveryWorkflows {
    private val ui = SuperAppDemoBuilder
    private val nudges = SuperAppDemoNudgeBuilders

    fun pulseCheckout(tier: String = "arena_gold"): PageResponse {
        val tierLabel =
            when (tier) {
                "tier_vip" -> "Front Row VIP · 2 Seats · \$190"
                "tier_balcony" -> "Balcony Acoustic · 2 Seats · \$60"
                else -> "Arena Gold · 2 Seats · \$110"
            }
        val validations =
            listOf(
                ValidationRuleContract("pulse_email", "required", message = "Ticket email is required"),
                ValidationRuleContract("pulse_email", "regex", DWF_EMAIL_REGEX, "Enter a valid email address"),
                ValidationRuleContract("pulse_phone", "required", message = "Mobile phone is required"),
                ValidationRuleContract("pulse_phone", "regex", DWF_PHONE_REGEX, "Enter a valid phone number"),
            )
        val confirmAction =
            ui.apiCallAction(
                "pulse_confirm_checkout",
                "/api/v1/pulse/checkout",
                buildJsonObject {
                    put("event_id", "neon_horizon_symphony")
                    put("tier", tier)
                },
                validations,
            )
        return PageResponse(
            pageId = "pulse-checkout",
            title = "Ticket Checkout · Abeta Pulse",
            toolbar = ui.toolbar("Event Checkout", "Neon Horizon · Harbor Dome", "back_pulse_checkout"),
            snippets =
                listOf(
                    ui.header("pulse_chk_hdr", tierLabel, "Sat, 8:00 PM · Gate 4B · Instant QR", "RESERVED"),
                    buildPulseCheckoutForm(confirmAction),
                ),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    private fun buildPulseCheckoutForm(confirmAction: ActionRef): SnippetEnvelope {
        val desc = ui.text("QR pass is delivered instantly after confirmation.", "body.small", "text.secondary")
        return SnippetEnvelope(
            snippetId = "pulse_checkout_form",
            snippetType = SnippetTypes.FORM,
            data =
                ui.encode(
                    FormSnippetData(
                        formId = "pulse_attendee_form",
                        title = ui.text("Attendee Details & Add-Ons", "title.medium"),
                        description = desc,
                        fields =
                            listOf(
                                TextFieldAtom(
                                    fieldId = "pulse_email",
                                    label = "Attendee Email",
                                    placeholder = "aanya@abeta.dev",
                                    initialValue = "aanya@abeta.dev",
                                    required = true,
                                    regex = DWF_EMAIL_REGEX,
                                    keyboardType = KeyboardKind.EMAIL,
                                ),
                                TextFieldAtom(
                                    fieldId = "pulse_phone",
                                    label = "Mobile Phone (SMS Gate Pass)",
                                    placeholder = "+1 415 555 0192",
                                    initialValue = "+14155550192",
                                    required = true,
                                    regex = DWF_PHONE_REGEX,
                                    keyboardType = KeyboardKind.PHONE,
                                ),
                            ),
                        switches =
                            listOf(
                                SwitchAtom(
                                    fieldId = "pulse_vip_lounge",
                                    label = "VIP Lounge Access (+\$25)",
                                    subtitle = "Priority entry & pre-show acoustic lounge",
                                    checked = true,
                                ),
                                SwitchAtom(
                                    fieldId = "pulse_snack_combo",
                                    label = "Snack & Craft Drink Combo (+\$16)",
                                    subtitle = "Express pickup at Harbor Dome concourse",
                                    checked = false,
                                ),
                            ),
                        submitButton = ui.button("btn_pulse_confirm", "Confirm & Issue QR Pass", confirmAction),
                        layout = ui.cardLayout,
                    ),
                ),
        )
    }

    fun pulseTicketPass(): PageResponse {
        val sharePass =
            ui.shareAction(
                "share_pulse_qr",
                "Abeta Pulse QR Pass #PLS-7712",
                "Neon Horizon Live Symphony · Gate 4B · Row C Seat 12–13",
                "https://abeta.dev/pulse/pass/PLS-7712",
            )
        val addCalendar =
            ui.toastAction("pulse_add_calendar", "Added Neon Horizon (Sat 8 PM · Gate 4B) to calendar!", "success")
        val directionsRow =
            ui.actionRow(
                snippetId = "pulse_gate_row",
                title = "Harbor Dome · Gate 4B Fast-Track",
                subtitle = "Doors open 7:00 PM · Show QR token PLS-7712 at scanner",
                icon = "check" to "status.success",
                tag = "ACTIVE PASS" to DWF_OK,
                onClick = ui.toastAction("pulse_gate_info", "Gate 4B is on the West Waterfront concourse"),
            )
        val moreRow =
            ui.actionRow(
                snippetId = "pulse_browse_more",
                title = "Browse More Events on Abeta Pulse",
                subtitle = "Concerts, IMAX 70mm movies & late-night comedy",
                icon = "star" to "brand.primary",
                tag = "EXPLORE" to DWF_BRAND,
                onClick = ui.navAction("pulse_back_home", "pulse-home"),
            )
        return PageResponse(
            pageId = "pulse-ticket-pass",
            title = "Digital QR Pass #PLS-7712",
            toolbar = ui.toolbar("Digital QR Pass", "Abeta Pulse · #PLS-7712", "back_pulse_pass"),
            snippets = listOf(buildPulseQrPassBlock(sharePass, addCalendar), directionsRow, moreRow),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    private fun buildPulseQrPassBlock(
        sharePass: ActionRef,
        addCalendar: ActionRef,
    ): SnippetEnvelope {
        val titleText = ui.text("Neon Horizon Live", "title.large")
        val titleNode = ComposedNode("qr_title", ComposedNodeKind.TEXT, text = titleText)
        val subText = ui.text("Sat, 8:00 PM · Harbor Dome Arena", "body.small", "text.secondary")
        val subNode = ComposedNode("qr_sub", ComposedNodeKind.TEXT, text = subText)
        val root =
            ComposedNode(
                nodeId = "pulse_qr_card",
                kind = ComposedNodeKind.CARD,
                spacingDp = 10,
                layout = ui.cardLayout,
                children =
                    listOf(
                        buildQrHeaderRow(),
                        titleNode,
                        subNode,
                        ComposedNode("qr_div", ComposedNodeKind.DIVIDER, divider = DividerAtom()),
                    ) + SuperAppDemoExtendedBlocks.buildPulseQrGateDetailsNodes() +
                        listOf(
                            SuperAppDemoExtendedBlocks.buildPulseQrCenterBox(),
                            buildQrActionsRow(sharePass, addCalendar),
                        ),
            )
        return SnippetEnvelope(
            snippetId = "pulse_qr_pass_block",
            snippetType = SnippetTypes.COMPOSED_BLOCK,
            data = ui.encode(ComposedBlockSnippetData("pulse_qr_pass", "digital_qr_event_pass", root)),
        )
    }

    private fun buildQrHeaderRow(): ComposedNode {
        val pulseMotion = MotionSpec(loopEffect = MotionLoopEffect.PULSE)
        val liveTag = TagAtom(label = "DIGITAL QR PASS", variant = TagVariant.SUCCESS, motion = pulseMotion)
        val codeTag = TagAtom(label = "#PLS-7712", variant = TagVariant.BRAND)
        return ComposedNode(
            nodeId = "qr_hdr_row",
            kind = ComposedNodeKind.ROW,
            mainAxisArrangement = "space_between",
            verticalAlignment = "center",
            layout = ui.fullWidthLayout,
            children =
                listOf(
                    ComposedNode(nodeId = "qr_live_tag", kind = ComposedNodeKind.TAG, tag = liveTag),
                    ComposedNode(nodeId = "qr_code_tag", kind = ComposedNodeKind.TAG, tag = codeTag),
                ),
        )
    }

    private fun buildQrActionsRow(
        sharePass: ActionRef,
        addCalendar: ActionRef,
    ): ComposedNode {
        val shareBtn = ui.button("btn_qr_share", "Share Pass", sharePass, size = ButtonSize.SMALL)
        val calBtn = ui.button("btn_qr_cal", "Add to Calendar", addCalendar, ButtonVariant.OUTLINE, ButtonSize.SMALL)
        return ComposedNode(
            nodeId = "qr_actions_row",
            kind = ComposedNodeKind.FLOW_ROW,
            spacingDp = 8,
            layout = ui.fullWidthLayout,
            children =
                listOf(
                    ComposedNode("qr_share_btn", ComposedNodeKind.BUTTON, button = shareBtn),
                    ComposedNode("qr_cal_btn", ComposedNodeKind.BUTTON, button = calBtn),
                ),
        )
    }

    fun sparkChat(): PageResponse {
        val messages =
            listOf(
                buildChatMessageCard(
                    "chat_msg_1",
                    "Maya Chen",
                    "MC",
                    "10m ago",
                    "Hey! Loved your trail running photos. Favorite pour-over spot near Harbor Park?",
                    DWF_BRAND,
                ),
                buildChatMessageCard(
                    "chat_msg_2",
                    "You",
                    "YO",
                    "6m ago",
                    "Ember Roasters on 4th has an incredible single-origin pour-over!",
                    DWF_INFO,
                ),
                buildChatMessageCard(
                    "chat_msg_3",
                    "Maya Chen",
                    "MC",
                    "2m ago",
                    "No way — I was sketching there Sunday! Coffee this Saturday morning?",
                    DWF_OK,
                ),
            )
        val icebreakers =
            ui.actionChipGroup(
                snippetId = "spark_icebreaker_chips",
                groupId = "spark_icebreakers",
                chips =
                    listOf(
                        Triple(
                            "ice_sat",
                            "Sat 10 AM works!",
                            ui.toastAction("send_ice_sat", "Sent: \"Sat 10 AM works!\"", "success"),
                        ),
                        Triple(
                            "ice_sketch",
                            "Bring your sketchbook!",
                            ui.toastAction("send_ice_sketch", "Sent: \"Bring your sketchbook!\"", "success"),
                        ),
                        Triple(
                            "ice_ceramics",
                            "Tell me about ceramics",
                            ui.toastAction("send_ice_ceramics", "Sent: \"Tell me about ceramics\"", "success"),
                        ),
                    ),
            )
        return PageResponse(
            pageId = "spark-chat",
            title = "Chat with Maya Chen",
            toolbar = ui.toolbar("Maya Chen", "Online now · 2.4 km · Verified Spark", "back_spark_chat"),
            snippets = messages + listOf(icebreakers, buildSparkReplyForm()),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    @Suppress("LongParameterList")
    private fun buildChatMessageCard(
        snippetId: String,
        sender: String,
        initials: String,
        timestamp: String,
        body: String,
        badgeColor: String,
    ): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = snippetId,
            snippetType = SnippetTypes.CARD,
            data =
                ui.encode(
                    CardSnippetData(
                        title = ui.text(sender, "title.small"),
                        subtitle = ui.text(body, "body.medium", "text.primary"),
                        badge = TagAtom(label = timestamp, bgColor = ColorTokenRef(badgeColor)),
                        avatar = AvatarAtom(initials = initials, sizeDp = 36),
                        layout = ui.cardLayout,
                    ),
                ),
        )

    private fun buildSparkReplyForm(): SnippetEnvelope {
        val validations =
            listOf(ValidationRuleContract("spark_reply_text", "required", message = "Type a message to send"))
        val sendAction =
            ui.apiCallAction(
                "spark_send_chat",
                "/api/v1/spark/chat",
                buildJsonObject { put("match_id", "match_maya") },
                validations,
            )
        return SnippetEnvelope(
            snippetId = "spark_reply_form_snippet",
            snippetType = SnippetTypes.FORM,
            data =
                ui.encode(
                    FormSnippetData(
                        formId = "spark_reply_form",
                        title = ui.text("Send a Message", "title.small"),
                        fields =
                            listOf(
                                TextFieldAtom(
                                    fieldId = "spark_reply_text",
                                    label = "Reply to Maya Chen",
                                    placeholder = "Write your message...",
                                    initialValue = "Saturday at 10 AM at Ember Roasters sounds great!",
                                    required = true,
                                ),
                            ),
                        submitButton = ui.button("btn_spark_send", "Send Reply", sendAction),
                        layout = ui.cardLayout,
                    ),
                ),
        )
    }

    fun sparkPreferences(): PageResponse =
        PageResponse(
            pageId = "spark-preferences",
            title = "Spark Preferences",
            toolbar = ui.toolbar("Discovery Preferences", "Abeta Spark · Match Filters", "back_spark_prefs"),
            snippets = listOf(buildSparkPreferencesForm()),
            adaptiveLayout = ui.centeredAdaptive,
        )

    private fun buildSparkPreferencesForm(): SnippetEnvelope {
        val saveAction = ui.toastAction("spark_save_prefs", "Spark discovery preferences saved!", "success")
        val backBtn =
            ui.button(
                "btn_spark_back_deck",
                "Back to Deck",
                ui.navAction("spark_prefs_deck", "spark-deck"),
                ButtonVariant.OUTLINE,
            )
        val desc = ui.text("Tune distance radius and verification filters.", "body.small", "text.secondary")
        return SnippetEnvelope(
            snippetId = "spark_prefs_form_snippet",
            snippetType = SnippetTypes.FORM,
            data =
                ui.encode(
                    FormSnippetData(
                        formId = "spark_preferences_form",
                        title = ui.text("Discovery Settings", "title.medium"),
                        description = desc,
                        sliders =
                            listOf(
                                SliderAtom(
                                    fieldId = "spark_max_distance_km",
                                    label = "Maximum Distance",
                                    value = 25f,
                                    minValue = 5f,
                                    maxValue = 100f,
                                    steps = 19,
                                    showValue = true,
                                    unitLabel = "km",
                                ),
                            ),
                        switches =
                            listOf(
                                SwitchAtom(
                                    fieldId = "spark_verified_only",
                                    label = "Verified Profiles Only",
                                    subtitle = "Only show photo-verified Abeta Spark members",
                                    checked = true,
                                ),
                                SwitchAtom(
                                    fieldId = "spark_recently_active",
                                    label = "Recently Active Priority",
                                    subtitle = "Surface people active in the past 24 hours",
                                    checked = true,
                                ),
                            ),
                        submitButton = ui.button("btn_spark_save_prefs", "Save Preferences", saveAction),
                        cancelButton = backBtn,
                        layout = ui.cardLayout,
                    ),
                ),
        )
    }

    fun tradeQuotes(): PageResponse {
        val quotes =
            listOf(
                TradeQuoteSpec(
                    id = "quote_apex",
                    supplier = "Apex Corrugated",
                    unitPrice = "\$0.68/u · \$340 total",
                    summary = "4-day dispatch · GST Verified · ISO 9001 5-ply kraft",
                    badge = "BEST VALUE" to DWF_OK,
                ),
                TradeQuoteSpec(
                    id = "quote_nova",
                    supplier = "Nova Packaging",
                    unitPrice = "\$0.72/u · \$360 total",
                    summary = "3-day express air · FSC certified · Custom logo print",
                    badge = "FASTEST" to DWF_INFO,
                ),
                TradeQuoteSpec(
                    id = "quote_ecobox",
                    supplier = "EcoBox Mill",
                    unitPrice = "\$0.65/u · \$325 total",
                    summary = "6-day freight · 100% recycled kraft · MOQ 500",
                    badge = "LOWEST \$" to DWF_WARN,
                ),
            )
        val grid =
            SnippetEnvelope(
                snippetId = "trade_quotes_grid",
                snippetType = SnippetTypes.GRID,
                data =
                    ui.encode(
                        GridSnippetData(
                            title = ui.text("Compare 3 Live Factory Quotes (500 Units)", "title.medium"),
                            columns = 1,
                            columnsByWidth = ResponsiveColumnsSpec(compact = 1, medium = 2, expanded = 3),
                            spacingDp = 12,
                            items = quotes.map(::buildTradeQuoteCard),
                            layout = ui.horizontalLayout,
                        ),
                    ),
            )
        return PageResponse(
            pageId = "trade-quotes",
            title = "Live Factory Quotes",
            toolbar = ui.toolbar("Factory Quotes", "RFQ #RFQ-9042 · 3 Live Bids", "back_trade_quotes"),
            snippets =
                listOf(
                    ui.header("trade_quotes_hdr", "RFQ #RFQ-9042 Quotes", "500× 5-Ply Shipping Cartons", "3 BIDS"),
                    SuperAppDemoExtendedBlocks.buildTradeQuotesTelemetryBlock(),
                    grid,
                ),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    private fun buildTradeQuoteCard(spec: TradeQuoteSpec): SnippetEnvelope {
        val acceptPo =
            ui.apiCallAction(
                "accept_${spec.id}",
                "/api/v1/trade/po",
                buildJsonObject {
                    put("quote_id", spec.id)
                    put("supplier", spec.supplier)
                },
            )
        val cta = ui.button("btn_po_${spec.id}", "Accept Quote & Issue PO", acceptPo, size = ButtonSize.SMALL)
        return SnippetEnvelope(
            snippetId = spec.id,
            snippetParentId = "trade_quotes_grid",
            snippetType = SnippetTypes.CARD,
            data =
                ui.encode(
                    CardSnippetData(
                        title = ui.text(spec.supplier, "title.medium"),
                        subtitle = ui.text(spec.unitPrice, "label.large", "text.brand"),
                        description = ui.text(spec.summary, "body.small", "text.secondary"),
                        badge = TagAtom(label = spec.badge.first, bgColor = ColorTokenRef(spec.badge.second)),
                        primaryButton = cta,
                        layout = ui.compactCardLayout,
                    ),
                ),
        )
    }

    fun tradePoTrack(stage: Int = 2): PageResponse {
        val current = stage.coerceIn(1, 4)
        val next = nudges.nextStage(current)
        val (headline, badge, etaSeconds) =
            when (current) {
                1 -> Triple("PO #TRD-9024 issued to Apex Corrugated", "STAGE 1/4", 86400)
                2 -> Triple("500 cartons passed ISO 9001 factory QC", "STAGE 2/4", 43200)
                3 -> Triple("Freight cleared port customs · E-Way Bill active", "STAGE 3/4", 14400)
                else -> Triple("Delivered to Warehouse Dock B · GST invoice filed", "SETTLED", 0)
            }
        val stepSpecs =
            listOf(
                Triple("t_po", "PO Issued", "Day 1"),
                Triple("t_qc", "Factory QC", "Day 2"),
                Triple("t_customs", "Customs", "Day 3"),
                Triple("t_dock", "Warehouse", "Day 4"),
            )
        val sharePo =
            ui.shareAction(
                "share_trade_po",
                "Abeta Trade PO #TRD-9024",
                "B2B Freight & Customs milestone Stage $current of 4 · Apex Corrugated",
                "https://abeta.dev/trade/po/TRD-9024",
            )
        val tracker =
            nudges.orderTracker(
                snippetId = "trade_po_tracker",
                trackerId = "trade_po_9024",
                orderCode = "#TRD-9024",
                headline = headline,
                etaBadge = badge,
                etaSeconds = etaSeconds,
                riderName = "Apex Freight Logistics",
                riderCaption = "Container #AX-412 · GST Invoice Ready · Stage $current/4",
                steps = nudges.stageSteps(current, stepSpecs),
                primaryLabel = "Advance Milestone",
                primaryAction = ui.navAction("trade_advance_po", "trade-po-track?stage=$next"),
                secondaryLabel = "Share PO Docs",
                secondaryAction = sharePo,
            )
        val homeRow =
            ui.actionRow(
                snippetId = "trade_back_home_row",
                title = "Back to Abeta Trade Wholesale",
                subtitle = "Download GST invoice or request another bulk RFQ",
                icon = "check" to "status.success",
                tag = "STAGE $current/4" to DWF_OK,
                onClick = ui.navAction("trade_nav_home", "trade-home"),
            )
        return PageResponse(
            pageId = "trade-po-track",
            title = "PO Tracker #TRD-9024",
            toolbar = ui.toolbar("PO #TRD-9024", "Abeta Trade · Stage $current of 4", "back_trade_po"),
            snippets = listOf(tracker, homeRow),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }
}
