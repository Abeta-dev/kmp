package dev.abeta.kmp.storybook

import dev.abeta.kmp.contracts.BorderSpec
import dev.abeta.kmp.contracts.ButtonSize
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.CardSnippetData
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.ComposedBlockSnippetData
import dev.abeta.kmp.contracts.ComposedNode
import dev.abeta.kmp.contracts.ComposedNodeKind
import dev.abeta.kmp.contracts.CornerRadiusSpec
import dev.abeta.kmp.contracts.FormSnippetData
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.KeyboardKind
import dev.abeta.kmp.contracts.LayoutConfig
import dev.abeta.kmp.contracts.MarqueeItem
import dev.abeta.kmp.contracts.MotionLoopEffect
import dev.abeta.kmp.contracts.MotionSpec
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.SliderAtom
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.SpacingValues
import dev.abeta.kmp.contracts.SwitchAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TextFieldAtom
import dev.abeta.kmp.contracts.TypographyTokenRef
import dev.abeta.kmp.contracts.ValidationRuleContract
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val PAY_OK = "status.success_container"
private const val PAY_INFO = "status.info_container"
private const val PAY_WARN = "status.warning_container"
private const val PAY_BRAND = "brand.container"
private const val PAY_AMOUNT_REGEX = "^\\d+(\\.\\d{1,2})?$"

/**
 * Multiplatform fintech & wallet vertical screens for the 7th Super-App demo:
 * Abeta Pay (Wallet Dashboard, Instant P2P Send Money, Smart Card Controls, and Settlement Tracker).
 */
@Suppress("MagicNumber", "TooManyFunctions")
internal object SuperAppDemoPayPages {
    private val ui = SuperAppDemoBuilder
    private val nudges = SuperAppDemoNudgeBuilders

    private val metalCardLayout =
        LayoutConfig(
            padding = SpacingValues(all = 16),
            margin = SpacingValues(horizontal = 16, vertical = 4),
            bgColor = ColorTokenRef("brand.container"),
            cornerRadius = CornerRadiusSpec(all = 16),
            border = BorderSpec(widthDp = 1.5f, color = ColorTokenRef("brand.primary")),
            fillMaxWidth = true,
        )

    fun payHome(): PageResponse {
        val receiptIcon =
            IconAtom(
                name = "notifications",
                sizeDp = 22,
                color = ColorTokenRef("brand.primary"),
                contentDescription = "Last Receipt",
                onClick = ui.navAction("nav_pay_receipt_tb", "pay-receipt?stage=2"),
            )
        val bills =
            listOf(
                Triple("bill_fiber", "Metro Fiber & Power", "Auto-pay due in 3d · \$64.00"),
                Triple("bill_cloud", "Abeta Cloud Vault", "Monthly plan · \$19.00"),
                Triple("bill_transit", "Harbor Transit Pass", "Top-up \$25 · 5% back"),
            )
        val balanceCard =
            ui.metric("pay_balance_metric", "Available Wallet Balance", "\$2,480.50", "+5.0% APY", "Instant P2P")
        val billCarousel =
            ui.carousel("pay_bills_carousel", "Bill Pay & Utilities", bills, "pay-transfer?amount=50", "ZERO FEE")
        return PageResponse(
            pageId = "pay-home",
            title = "Abeta Pay",
            toolbar = ui.toolbar("Abeta Pay", "Fintech & Wallet · Instant Rail", "back_pay", listOf(receiptIcon)),
            snippets =
                listOf(
                    buildPayMarquee(),
                    buildAbetaMetalCardBlock(),
                    buildPayQuickActionsBlock(),
                    balanceCard,
                    buildPayCashbackNudge(),
                    billCarousel,
                ) + buildRecentTransactions(),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    private fun buildPayMarquee(): SnippetEnvelope {
        val cbToast = ui.toastAction("pay_cb_info", "5% cashback credits instantly to Abeta Pay!", "success")
        return nudges.marquee(
            snippetId = "pay_fx_marquee",
            stripId = "pay_live_fx",
            items =
                listOf(
                    MarqueeItem(
                        "fx_usd_eur",
                        "USD/EUR 0.92 · 0% FX markup",
                        IconAtom(name = "star", sizeDp = 16, color = ColorTokenRef("brand.primary")),
                        TagAtom(label = "FX LIVE", bgColor = ColorTokenRef(PAY_BRAND)),
                    ),
                    MarqueeItem(
                        "fx_cashback",
                        "5% instant cashback on Abeta apps",
                        IconAtom(name = "check", sizeDp = 16, color = ColorTokenRef("status.success")),
                        TagAtom(label = "5% BACK", bgColor = ColorTokenRef(PAY_OK)),
                        cbToast,
                    ),
                    MarqueeItem(
                        "fx_instant",
                        "Zero-fee P2P settles in <2 sec",
                        IconAtom(name = "info", sizeDp = 16, color = ColorTokenRef("status.info")),
                        TagAtom(label = "INSTANT", bgColor = ColorTokenRef(PAY_INFO)),
                    ),
                ),
        )
    }

    private fun buildAbetaMetalCardBlock(): SnippetEnvelope {
        val root =
            ComposedNode(
                nodeId = "metal_card_root",
                kind = ComposedNodeKind.CARD,
                spacingDp = 10,
                layout = metalCardLayout,
                children = listOf(metalCardTopRow(), metalCardNumberRow(), metalCardFooterRow()),
            )
        return SnippetEnvelope(
            snippetId = "pay_metal_card_block",
            snippetType = SnippetTypes.COMPOSED_BLOCK,
            data = ui.encode(ComposedBlockSnippetData("abeta_metal_card", "metal_virtual_card", root)),
        )
    }

    private fun metalCardTopRow(): ComposedNode {
        val brandText = ui.text("ABETA METAL · VIRTUAL", "label.large", "text.brand")
        val pulseMotion = MotionSpec(loopEffect = MotionLoopEffect.PULSE)
        val statusTag = TagAtom(label = "ACTIVE", variant = TagVariant.SUCCESS, motion = pulseMotion)
        return ComposedNode(
            nodeId = "metal_top_row",
            kind = ComposedNodeKind.ROW,
            mainAxisArrangement = "space_between",
            verticalAlignment = "center",
            layout = ui.fullWidthLayout,
            children =
                listOf(
                    ComposedNode("metal_brand", ComposedNodeKind.TEXT, weight = 1f, text = brandText),
                    ComposedNode("metal_status", ComposedNodeKind.TAG, tag = statusTag),
                ),
        )
    }

    private fun metalCardNumberRow(): ComposedNode {
        val panText = ui.text("•••• 4829 · ACTIVE", "title.large")
        return ComposedNode(
            nodeId = "metal_num_row",
            kind = ComposedNodeKind.ROW,
            mainAxisArrangement = "space_between",
            verticalAlignment = "center",
            layout = ui.fullWidthLayout,
            children =
                listOf(
                    ComposedNode("metal_pan", ComposedNodeKind.TEXT, weight = 1f, text = panText),
                    ComposedNode(
                        "metal_bal",
                        ComposedNodeKind.ROLLING_COUNTER,
                        counterValue = "\$2,480.50",
                        counterTypography = TypographyTokenRef("title.medium"),
                        counterColor = ColorTokenRef("brand.primary"),
                    ),
                ),
        )
    }

    private fun metalCardFooterRow(): ComposedNode {
        val expText = ui.text("EXP 09/29 · NFC READY", "body.small", "text.secondary")
        val manageBtn =
            ui.button(
                "btn_metal_ctrl",
                "Manage Card",
                ui.navAction("nav_pay_cards", "pay-cards"),
                ButtonVariant.OUTLINE,
                ButtonSize.SMALL,
            )
        return ComposedNode(
            nodeId = "metal_ftr_row",
            kind = ComposedNodeKind.ROW,
            mainAxisArrangement = "space_between",
            verticalAlignment = "center",
            spacingDp = 8,
            layout = ui.fullWidthLayout,
            children =
                listOf(
                    ComposedNode("metal_exp", ComposedNodeKind.TEXT, weight = 1f, text = expText),
                    ComposedNode("metal_ctrl_btn", ComposedNodeKind.BUTTON, button = manageBtn),
                ),
        )
    }

    private fun buildPayCashbackNudge(): SnippetEnvelope =
        nudges.nudgeBar(
            snippetId = "pay_cashback_nudge",
            nudgeId = "pay_zero_fee_cb",
            title = "Zero-Fee 5% Cashback Window",
            subtitle = "Send P2P or pay bills with Abeta Metal to earn instant rewards",
            badgeLabel = "5% BACK",
            badgeEffect = MotionLoopEffect.GLOW_RING,
            ctaLabel = "Send & Earn",
            ctaAction = ui.navAction("nav_pay_nudge_send", "pay-transfer"),
            countdownSeconds = 900,
            countdownLabel = "Boost ends",
            progress = 0.72f,
        )

    private fun buildPayQuickActionsBlock(): SnippetEnvelope {
        val sendNav = ui.navAction("qa_nav_send", "pay-transfer")
        val sendBtn = ui.button("btn_qa_send", "Send Money", sendNav, size = ButtonSize.SMALL)
        val cardsBtn =
            ui.button(
                "btn_qa_cards",
                "Card Controls",
                ui.navAction("qa_nav_cards", "pay-cards"),
                ButtonVariant.OUTLINE,
                ButtonSize.SMALL,
            )
        val rcptBtn =
            ui.button(
                "btn_qa_receipt",
                "Last Receipt",
                ui.navAction("qa_nav_receipt", "pay-receipt?stage=2"),
                ButtonVariant.OUTLINE,
                ButtonSize.SMALL,
            )
        val actionsRow =
            ComposedNode(
                nodeId = "pay_quick_row",
                kind = ComposedNodeKind.FLOW_ROW,
                spacingDp = 8,
                layout = ui.fullWidthLayout,
                children =
                    listOf(
                        ComposedNode("qa_send", ComposedNodeKind.BUTTON, button = sendBtn),
                        ComposedNode("qa_cards", ComposedNodeKind.BUTTON, button = cardsBtn),
                        ComposedNode("qa_receipt", ComposedNodeKind.BUTTON, button = rcptBtn),
                    ),
            )
        val qaTitle = ui.text("Quick Actions", "title.small")
        val root =
            ComposedNode(
                nodeId = "pay_qa_card",
                kind = ComposedNodeKind.CARD,
                spacingDp = 8,
                layout = ui.cardLayout,
                children = listOf(ComposedNode("pay_qa_title", ComposedNodeKind.TEXT, text = qaTitle), actionsRow),
            )
        return SnippetEnvelope(
            snippetId = "pay_quick_actions_block",
            snippetType = SnippetTypes.COMPOSED_BLOCK,
            data = ui.encode(ComposedBlockSnippetData("pay_quick_actions", "wallet_quick_actions", root)),
        )
    }

    private fun buildRecentTransactions(): List<SnippetEnvelope> =
        listOf(
            ui.actionRow(
                "tx_maya",
                "Maya Chen · Instant P2P",
                "Today 19:42 · Rail #PAY-3081 · Zero fee",
                "person" to "brand.primary",
                "-\$85.00" to PAY_INFO,
                ui.navAction("open_tx_maya", "pay-receipt?stage=4"),
            ),
            ui.actionRow(
                "tx_fresh",
                "Abeta Fresh · 5% Cashback",
                "Today 16:10 · Express Grocery Reward",
                "check" to "status.success",
                "+\$1.80" to PAY_OK,
                ui.toastAction("tx_fresh_info", "Cashback credited from Abeta Fresh #FRH-8042"),
            ),
            ui.actionRow(
                "tx_roasters",
                "Harbor Coffee Roasters",
                "Yesterday · Contactless Abeta Metal •••• 4829",
                "star" to "status.warning",
                "-\$6.50" to PAY_WARN,
                ui.toastAction("tx_coffee_info", "Contactless tap settled · \$0.32 cashback earned"),
            ),
        )

    fun payTransfer(amount: String = ""): PageResponse {
        val cleanAmount = amount.trim().takeIf { it.isNotEmpty() }
        val selectedAmount = cleanAmount ?: "50"
        val quickAmounts = listOf("25" to "\$25", "50" to "\$50", "100" to "\$100", "250" to "\$250")
        val chipRow =
            ui.chipGroup("pay_amt_chips", "pay_quick_amounts", "pay-transfer", quickAmounts, selectedAmount, "amount")
        val pageActions =
            if (cleanAmount != null) {
                listOf(ui.saveStateAction("sync_pay_amt_$cleanAmount", "pay_amount", cleanAmount))
            } else {
                emptyList()
            }
        return PageResponse(
            pageId = "pay-transfer",
            title = "Instant P2P Send Money",
            toolbar = ui.toolbar("Send Money", "Abeta Pay · Available \$2,480.50", "back_pay_transfer"),
            snippets =
                listOf(
                    ui.header("pay_tx_hdr", "Instant P2P Transfer", "Zero fee · Biometric rail", "INSTANT"),
                    chipRow,
                    buildBiometricSecurityCard(),
                    buildPayTransferForm(selectedAmount),
                ),
            pageActions = pageActions,
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    private fun buildBiometricSecurityCard(): SnippetEnvelope {
        val okToast = ui.toastAction("pay_bio_ok", "Biometric verified · Ready for instant transfer!", "success")
        val failToast = ui.toastAction("pay_bio_fallback", "Device passcode fallback enabled for transfer", "warning")
        val bioAction =
            ui.biometricAuthAction(
                id = "pay_biometric_verify",
                title = "Abeta Pay Biometric Check",
                reason = "Verify Face ID / Touch ID to authorize instant P2P transfer",
                onAuthenticated = okToast,
                onFailed = failToast,
            )
        val bioBtn =
            ui.button("btn_pay_bio", "Verify with Biometrics", bioAction, ButtonVariant.OUTLINE, ButtonSize.SMALL)
        val sub =
            ui.text("Confirm identity via OS biometric bridge before sending funds.", "body.small", "text.secondary")
        return SnippetEnvelope(
            snippetId = "pay_biometric_card",
            snippetType = SnippetTypes.CARD,
            data =
                ui.encode(
                    CardSnippetData(
                        title = ui.text("Biometric Verification", "title.small"),
                        subtitle = sub,
                        badge = TagAtom(label = "BIOMETRIC", bgColor = ColorTokenRef(PAY_BRAND)),
                        primaryButton = bioBtn,
                        layout = ui.cardLayout,
                    ),
                ),
        )
    }

    private fun buildPayTransferForm(defaultAmount: String): SnippetEnvelope {
        val validations =
            listOf(
                ValidationRuleContract("pay_recipient", "required", message = "Recipient handle or email is required"),
                ValidationRuleContract("pay_amount", "required", message = "Transfer amount is required"),
                ValidationRuleContract("pay_amount", "regex", PAY_AMOUNT_REGEX, "Enter a valid USD amount"),
            )
        val sendAction =
            ui.apiCallAction(
                "pay_send_instant",
                "/api/v1/pay/transfer",
                buildJsonObject { put("rail", "abeta_instant") },
                validations,
            )
        return SnippetEnvelope(
            snippetId = "pay_transfer_form_snippet",
            snippetType = SnippetTypes.FORM,
            data =
                ui.encode(
                    FormSnippetData(
                        formId = "pay_transfer_form",
                        title = ui.text("Transfer Details", "title.medium"),
                        fields =
                            listOf(
                                TextFieldAtom(
                                    fieldId = "pay_recipient",
                                    label = "Recipient (@handle or email)",
                                    placeholder = "@maya.chen",
                                    initialValue = "@maya.chen",
                                    required = true,
                                ),
                                TextFieldAtom(
                                    fieldId = "pay_amount",
                                    label = "Amount (\$ USD)",
                                    placeholder = "50",
                                    initialValue = defaultAmount,
                                    required = true,
                                    regex = PAY_AMOUNT_REGEX,
                                    keyboardType = KeyboardKind.NUMBER,
                                ),
                                TextFieldAtom(
                                    fieldId = "pay_note",
                                    label = "Note (Optional)",
                                    placeholder = "Dinner split & coffee",
                                    initialValue = "Weekend coffee & lunch split",
                                ),
                            ),
                        submitButton = ui.button("btn_pay_send_instant", "Send Money Instantly", sendAction),
                        layout = ui.cardLayout,
                    ),
                ),
        )
    }

    fun payCards(): PageResponse =
        PageResponse(
            pageId = "pay-cards",
            title = "Smart Card Controls",
            toolbar = ui.toolbar("Card Controls", "Abeta Metal · •••• 4829", "back_pay_cards"),
            snippets = listOf(buildAbetaMetalCardBlock(), buildPayCardControlsForm()),
            adaptiveLayout = ui.centeredAdaptive,
        )

    private fun buildPayCardControlsForm(): SnippetEnvelope {
        val revealCvv = ui.toastAction("pay_reveal_cvv", "Dynamic CVV: 842 (auto-rotates in 5 mins)", "info")
        val saveControls = ui.toastAction("pay_save_controls", "Abeta Metal card controls saved!", "success")
        val desc = ui.text("Instant real-time rules for Abeta Metal •••• 4829.", "body.small", "text.secondary")
        val cvvBtn = ui.button("btn_pay_reveal_cvv", "Reveal CVV", revealCvv, ButtonVariant.OUTLINE)
        return SnippetEnvelope(
            snippetId = "pay_cards_form_snippet",
            snippetType = SnippetTypes.FORM,
            data =
                ui.encode(
                    FormSnippetData(
                        formId = "pay_card_controls_form",
                        title = ui.text("Smart Card Controls", "title.medium"),
                        description = desc,
                        switches =
                            listOf(
                                SwitchAtom(
                                    fieldId = "pay_freeze_card",
                                    label = "Freeze Virtual Card",
                                    subtitle = "Instantly pause all new online & tap transactions",
                                    checked = false,
                                ),
                                SwitchAtom(
                                    fieldId = "pay_international",
                                    label = "International Payments",
                                    subtitle = "Zero-markup FX across 140+ currencies",
                                    checked = true,
                                ),
                                SwitchAtom(
                                    fieldId = "pay_contactless",
                                    label = "Contactless Tap-to-Pay",
                                    subtitle = "NFC Apple/Google Wallet & express transit",
                                    checked = true,
                                ),
                            ),
                        sliders =
                            listOf(
                                SliderAtom(
                                    fieldId = "pay_monthly_limit",
                                    label = "Monthly Spend Limit",
                                    value = 3500f,
                                    minValue = 500f,
                                    maxValue = 10000f,
                                    steps = 19,
                                    showValue = true,
                                    unitLabel = "USD",
                                ),
                            ),
                        submitButton = ui.button("btn_pay_save_cards", "Save Card Controls", saveControls),
                        cancelButton = cvvBtn,
                        layout = ui.cardLayout,
                    ),
                ),
        )
    }

    fun payReceipt(stage: Int = 2): PageResponse {
        val current = stage.coerceIn(1, 4)
        val next = nudges.nextStage(current)
        val (headline, badge, etaSeconds) =
            when (current) {
                1 -> Triple("Transfer #PAY-3081 initiated to @maya.chen", "STAGE 1/4", 12)
                2 -> Triple("Biometric signature verified · Dispatching rail", "STAGE 2/4", 6)
                3 -> Triple("Instant settlement rail cleared · \$0.00 fee", "STAGE 3/4", 2)
                else -> Triple("Settled in @maya.chen's wallet · 5% reward queued", "SETTLED", 0)
            }
        val stepSpecs =
            listOf(
                Triple("p_init", "Initiated", "19:42"),
                Triple("p_bio", "Biometric Verified", "<1s"),
                Triple("p_rail", "Rail Cleared", "1.2s"),
                Triple("p_settled", "Settled", "Done"),
            )
        val shareReceipt =
            ui.shareAction(
                "pay_share_receipt",
                "Abeta Pay Receipt #PAY-3081",
                "Instant P2P Settlement #PAY-3081 to @maya.chen · Stage $current/4",
                "https://abeta.dev/pay/receipt/PAY-3081",
            )
        val tracker =
            nudges.orderTracker(
                snippetId = "pay_settlement_tracker",
                trackerId = "pay_receipt_3081",
                orderCode = "#PAY-3081",
                headline = headline,
                etaBadge = badge,
                etaSeconds = etaSeconds,
                riderName = "Abeta Instant Clearing Rail",
                riderCaption = "Recipient: @maya.chen · Zero fee · Stage $current of 4",
                steps = nudges.stageSteps(current, stepSpecs),
                primaryLabel = "Advance Settlement",
                primaryAction = ui.navAction("pay_advance_stage", "pay-receipt?stage=$next"),
                secondaryLabel = "Share Receipt",
                secondaryAction = shareReceipt,
            )
        return PageResponse(
            pageId = "pay-receipt",
            title = "Settlement Receipt #PAY-3081",
            toolbar = ui.toolbar("Receipt #PAY-3081", "Abeta Pay · Stage $current of 4", "back_pay_receipt"),
            snippets = listOf(tracker, buildPayReceiptSummaryCard(current)),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    private fun buildPayReceiptSummaryCard(currentStage: Int): SnippetEnvelope {
        val sendAgainNav = ui.navAction("pay_again_nav", "pay-transfer")
        val sendAgain = ui.button("btn_pay_send_again", "Send Again", sendAgainNav, size = ButtonSize.SMALL)
        val walletHome =
            ui.button(
                "btn_pay_wallet",
                "Wallet Home",
                ui.navAction("pay_wallet_nav", "pay-home"),
                ButtonVariant.OUTLINE,
                ButtonSize.SMALL,
            )
        val sub = ui.text("Funded via Abeta Metal •••• 4829 · Instant Rail", "body.small", "text.secondary")
        return SnippetEnvelope(
            snippetId = "pay_receipt_summary",
            snippetType = SnippetTypes.CARD,
            data =
                ui.encode(
                    CardSnippetData(
                        title = ui.text("Receipt #PAY-3081 · Zero Fee", "title.medium"),
                        subtitle = sub,
                        badge = TagAtom(label = "STAGE $currentStage/4", bgColor = ColorTokenRef(PAY_OK)),
                        primaryButton = sendAgain,
                        secondaryButton = walletHome,
                        layout = ui.cardLayout,
                    ),
                ),
        )
    }
}
