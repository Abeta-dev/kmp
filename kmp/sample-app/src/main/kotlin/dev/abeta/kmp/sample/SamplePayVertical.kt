package dev.abeta.kmp.sample

import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.FormSnippetData
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.KeyboardKind
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.ProgressAtom
import dev.abeta.kmp.contracts.ProgressKind
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SliderAtom
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.SwitchAtom
import dev.abeta.kmp.contracts.TextFieldAtom
import dev.abeta.kmp.contracts.ValidationRuleContract
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private const val PAY_CELEBRATION_MS = 2400L
private const val PAY_RECIPIENT_REGEX = "^(@[a-zA-Z0-9._-]{2,}|[^@\\s]+@[^@\\s]+\\.[^@\\s]+)$"
private const val PAY_AMOUNT_REGEX = "^\\d+(\\.\\d{1,2})?$"

private val payBillPayItems =
    listOf(
        Triple("bill_power", "City Power & Grid", "Auto-pay due in 3 days · \$68.40 · 5% back"),
        Triple("bill_fiber", "HyperFiber Gigabit", "Monthly broadband · \$59.00 · Zero fee"),
        Triple("bill_mobile", "Abeta Wireless 5G", "Unlimited plan · \$45.00 · Instant top-up"),
    )

private val payTransferFields =
    listOf(
        TextFieldAtom(
            fieldId = "pay_recipient",
            label = "Recipient (@handle or email)",
            placeholder = "@maya.chen",
            initialValue = "@maya.chen",
            helperText = "Instant Abeta Pay handle or verified email",
            required = true,
            regex = PAY_RECIPIENT_REGEX,
            keyboardType = KeyboardKind.TEXT,
        ),
        TextFieldAtom(
            fieldId = "pay_amount",
            label = "Amount (USD)",
            placeholder = "125.00",
            initialValue = "125.00",
            helperText = "Zero fee up to \$2,500 per instant transfer",
            required = true,
            regex = PAY_AMOUNT_REGEX,
            keyboardType = KeyboardKind.NUMBER,
        ),
        TextFieldAtom(
            fieldId = "pay_note",
            label = "Memo (Optional)",
            placeholder = "Dinner split & coffee",
            initialValue = "Dinner split",
            keyboardType = KeyboardKind.TEXT,
        ),
    )

private val payCardSwitches: List<SwitchAtom> =
    listOf(
        SwitchAtom(
            fieldId = "card_freeze",
            label = "Freeze Card Temporarily",
            subtitle = "Instantly block all new card transactions & subscriptions",
            checked = false,
            onChange = SamplePayPrimitives.saveSessionFlag("save_card_freeze", "card_freeze", false),
        ),
        SwitchAtom(
            fieldId = "card_international",
            label = "International & FX Spend",
            subtitle = "Zero FX markup across 140+ global currencies",
            checked = true,
            onChange = SamplePayPrimitives.saveSessionFlag("save_card_intl", "card_international", true),
        ),
        SwitchAtom(
            fieldId = "card_contactless",
            label = "Contactless & Tap-to-Pay",
            subtitle = "Enable NFC tap-to-pay on phones & wearables",
            checked = true,
            onChange = SamplePayPrimitives.saveSessionFlag("save_card_nfc", "card_contactless", true),
        ),
    )

/**
 * Offline SDUI pages for the 7th Super-App vertical: **Abeta Pay**
 * (Fintech, Digital Wallet, Instant P2P Transfers & Smart Card Controls).
 */
@Suppress("MagicNumber")
internal object SamplePayVertical {
    private val ui = SampleScreens
    private val pay = SamplePayPrimitives

    fun payHome(): PageResponse {
        val cardsIcon =
            IconAtom(
                name = "settings",
                sizeDp = 22,
                color = ColorTokenRef("brand.primary"),
                contentDescription = "Open Smart Card Controls",
                onClick = ui.navAction("nav_pay_cards_toolbar", "pay-cards"),
            )
        val search = ui.searchBarEnvelope("pay_search_bar", "pay_search", "Search contacts & bills...", "pay-home")
        val progress =
            ProgressAtom(
                kind = ProgressKind.LINEAR,
                progress = 0.78f,
                label = "\$1,950 / \$2,500 monthly cashback cap",
                color = ColorTokenRef("status.success"),
            )
        val balance =
            ui.metricEnvelope(
                snippetId = "pay_balance_metric",
                label = "Available Wallet Balance",
                value = "\$2,480.50",
                delta = "+5.0% APY",
                caption = "FDIC-insured vault · Instant settlement · Auto-cashback enabled",
                progress = progress,
            )
        val bills =
            ui.carouselEnvelope(
                "pay_bill_carousel",
                "Bill Pay & Utilities",
                payBillPayItems,
                "pay-transfer",
                "BILL PAY",
            )
        val toolbar =
            ui.verticalToolbar("Abeta Pay", "Wallet & Smart Cards · Zero-Fee P2P", "back_pay", listOf(cardsIcon))
        return PageResponse(
            pageId = "pay-home",
            title = "Abeta Pay",
            toolbar = toolbar,
            snippets =
                listOf(
                    search,
                    pay.perksMarquee(),
                    pay.quickChips(),
                    balance,
                    pay.cashbackNudge(),
                    pay.metalVirtualCardBlock(),
                    buildQuickActionRow("pay_quick_send_row", "Quick Send P2P", "pay-transfer"),
                    buildQuickActionRow("pay_card_controls_row", "Smart Card Controls", "pay-cards"),
                    buildQuickActionRow("pay_recent_receipt_row", "Latest Settlement #PAY-3081", "pay-receipt"),
                    bills,
                ),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    fun payTransfer(): PageResponse {
        val header =
            ui.headerEnvelope(
                snippetId = "pay_transfer_header",
                title = "Instant P2P Transfer",
                subtitle = "Available balance: \$2,480.50 · Settles in < 2 seconds",
                tagLabel = "ZERO FEE",
            )
        val contacts =
            listOf("maya" to "@maya.chen", "arjun" to "@arjun.rao", "elena" to "@elena.m", "noah" to "@noah.park")
        val chips = ui.chipGroupEnvelope("pay_contact_chips", "pay_recent_contacts", "pay-transfer", contacts)
        val receiptRow =
            ui.actionRowEnvelope(
                snippetId = "pay_transfer_receipt_row",
                title = "View Latest Settlement #PAY-3081",
                subtitle = "Inspect 4-stage live clearing tracker & share receipt",
                icon = "check" to "status.success",
                tag = "RECEIPT" to PAY_OK_BG,
                onClick = ui.navAction("nav_pay_transfer_receipt", "pay-receipt"),
            )
        val toolbar = ui.verticalToolbar("Send Money", "Abeta Pay · Zero-Fee Instant P2P", "back_pay_transfer")
        return PageResponse(
            pageId = "pay-transfer",
            title = "Send Money",
            toolbar = toolbar,
            snippets = listOf(header, chips, buildPayTransferForm(), buildBiometricAuthorizeRow(), receiptRow),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    fun payCards(): PageResponse {
        val header =
            ui.headerEnvelope(
                snippetId = "pay_cards_header",
                title = "Abeta Metal Virtual Card",
                subtitle = "Instant real-time card security & spending controls",
                tagLabel = "ACTIVE · •••• 4092",
            )
        val spendProgress =
            ProgressAtom(
                kind = ProgressKind.LINEAR,
                progress = 0.284f,
                label = "\$1,420 / \$5,000 monthly spend limit",
                color = ColorTokenRef("brand.primary"),
            )
        val spendMetric =
            ui.metricEnvelope(
                snippetId = "pay_cards_spend_metric",
                label = "Monthly Card Spend",
                value = "\$1,420.00",
                delta = "28% OF LIMIT",
                caption = "Auto-resets on the 1st · 5% cashback unlocked",
                progress = spendProgress,
            )
        val toolbar = ui.verticalToolbar("Smart Card Controls", "Abeta Metal · •••• 4092", "back_pay_cards")
        return PageResponse(
            pageId = "pay-cards",
            title = "Smart Card Controls",
            toolbar = toolbar,
            snippets = listOf(header, spendMetric, buildCardControlsForm()),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    fun payReceipt(): PageResponse {
        val shareAct =
            ui.shareAction(
                id = "share_pay_receipt_3081",
                title = "Abeta Pay Receipt #PAY-3081",
                text = "Sent \$125.00 to @maya.chen via Abeta Pay (Zero Fee · Ref #PAY-3081)",
                url = "https://abeta.dev/pay/receipt/PAY-3081",
            )
        val shareRow =
            ui.actionRowEnvelope(
                snippetId = "pay_receipt_share_row",
                title = "Share Settlement Receipt #PAY-3081",
                subtitle = "Send verified zero-fee transfer confirmation via OS share sheet",
                icon = "info" to "brand.primary",
                tag = "SHARE" to PAY_BRAND_BG,
                onClick = shareAct,
            )
        val cashbackToast = ui.toastAction("toast_pay_cashback_vault", "+\$6.25 cashback credited to Vault", "success")
        val cashbackRow =
            ui.actionRowEnvelope(
                snippetId = "pay_receipt_cashback_row",
                title = "5% Instant Cashback Credited",
                subtitle = "+\$6.25 added to your Abeta Pay Vault · Zero FX & transfer fees",
                icon = "star" to "status.success",
                tag = "+\$6.25" to PAY_OK_BG,
                onClick = cashbackToast,
            )
        val celebration =
            ActionRef(
                id = "pay_receipt_celebration",
                type = ActionTypes.TRIGGER_FEEDBACK,
                payload =
                    buildJsonObject {
                        put("effect", "celebration")
                        put("title", "Transfer Sent!")
                        put("subtitle", "Instant settlement #PAY-3081 confirmed via Abeta Pay")
                        put("duration_ms", PAY_CELEBRATION_MS)
                    },
            )
        val toolbar = ui.verticalToolbar("Receipt #PAY-3081", "Abeta Pay · Instant Settlement", "back_pay_receipt")
        return PageResponse(
            pageId = "pay-receipt",
            title = "Receipt #PAY-3081",
            toolbar = toolbar,
            snippets = listOf(pay.settlementTracker(shareAct), shareRow, cashbackRow),
            pageActions = listOf(celebration),
            adaptiveLayout = ui.centeredAdaptive,
        )
    }

    private fun buildQuickActionRow(
        snippetId: String,
        title: String,
        route: String,
    ): SnippetEnvelope =
        when (snippetId) {
            "pay_quick_send_row" -> {
                ui.actionRowEnvelope(
                    snippetId = snippetId,
                    title = title,
                    subtitle = "Zero-fee instant transfer to @maya.chen, @arjun.rao or any handle",
                    icon = "arrow_forward" to "brand.primary",
                    tag = "INSTANT" to PAY_OK_BG,
                    onClick = ui.navAction("nav_pay_quick_send", route),
                )
            }

            "pay_card_controls_row" -> {
                ui.actionRowEnvelope(
                    snippetId = snippetId,
                    title = title,
                    subtitle = "Freeze card, international spend, contactless & monthly limit",
                    icon = "settings" to "brand.primary",
                    tag = "ACTIVE" to PAY_INFO_BG,
                    onClick = ui.navAction("nav_pay_card_controls", route),
                )
            }

            else -> {
                ui.actionRowEnvelope(
                    snippetId = snippetId,
                    title = title,
                    subtitle = "\$125.00 to @maya.chen · 4-stage live settlement tracker",
                    icon = "check" to "status.success",
                    tag = "SETTLED" to PAY_OK_BG,
                    onClick = ui.navAction("nav_pay_latest_receipt", route),
                )
            }
        }

    private fun buildPayTransferApiAction(validations: List<ValidationRuleContract> = emptyList()): ActionRef =
        ui.apiCallAction(
            id = "pay_execute_transfer",
            endpoint = "/api/v1/pay/transfer",
            body =
                buildJsonObject {
                    put("vertical", "abeta-pay")
                    put("recipient", "@maya.chen")
                    put("amount_cents", 12500)
                    put("currency", "USD")
                },
            validations = validations,
        )

    private fun buildPayTransferForm(): SnippetEnvelope {
        val validations =
            listOf(
                ValidationRuleContract("pay_recipient", "required", message = "Recipient handle or email is required"),
                ValidationRuleContract("pay_recipient", "regex", PAY_RECIPIENT_REGEX, "Enter a valid @handle or email"),
                ValidationRuleContract("pay_amount", "required", message = "Transfer amount is required"),
                ValidationRuleContract("pay_amount", "regex", PAY_AMOUNT_REGEX, "Enter a valid USD amount"),
            )
        val submitAction = buildPayTransferApiAction(validations)
        val submitBtn = ui.button("btn_pay_send_now", "Send \$125.00 Instantly", submitAction, fullWidth = true)
        val desc = ui.text("Validated client & server before instant settlement.", "body.small", "text.secondary")
        val data =
            FormSnippetData(
                formId = "pay_transfer_form",
                title = ui.text("Transfer Details", "title.medium"),
                description = desc,
                fields = payTransferFields,
                submitButton = submitBtn,
                layout = ui.cardLayout,
            )
        return SnippetEnvelope(
            snippetId = "pay_transfer_form_snippet",
            snippetType = SnippetTypes.FORM,
            data = ui.encode(data),
        )
    }

    private fun buildBiometricAuthorizeRow(): SnippetEnvelope {
        val onAuth = buildPayTransferApiAction()
        val onFailed =
            ui.toastAction("pay_biometric_fallback_toast", "Biometric unavailable — tap Send above", "warning")
        val bioAction =
            ActionRef(
                id = "pay_biometric_authorize",
                type = ActionTypes.BIOMETRIC_AUTH,
                payload =
                    buildJsonObject {
                        put("reason", "Authorize instant \$125.00 P2P transfer to @maya.chen")
                        put("title", "Abeta Pay Biometric Auth")
                        put("allow_device_credential", true)
                        put("on_authenticated", SduiJson.encodeToJsonElement(ActionRef.serializer(), onAuth))
                        put("on_failed", SduiJson.encodeToJsonElement(ActionRef.serializer(), onFailed))
                    },
            )
        return ui.actionRowEnvelope(
            snippetId = "pay_biometric_auth_row",
            title = "Authorize with Face ID / Touch ID",
            subtitle = "Confirm \$125.00 instant transfer via OS biometric prompt",
            icon = "person" to "brand.primary",
            tag = "BIOMETRIC" to PAY_BRAND_BG,
            onClick = bioAction,
        )
    }

    private fun buildCardControlsForm(): SnippetEnvelope {
        val saveLimit = pay.saveSessionLimit("save_card_limit", "card_spend_limit", 5000)
        val saveAll =
            ActionRef(
                id = "save_pay_card_controls",
                type = ActionTypes.SAVE_STATE,
                payload =
                    buildJsonObject {
                        put("key", "pay_card_controls_saved")
                        put("value", true)
                        put("scope", "session")
                    },
                secondaryActions =
                    listOf(ui.toastAction("toast_pay_cards_saved", "Smart Card Controls saved & synced!", "success")),
            )
        val desc = ui.text("Real-time switches & monthly spend cap for •••• 4092.", "body.small", "text.secondary")
        val slider =
            SliderAtom(
                fieldId = "card_spend_limit",
                label = "Monthly Spend Limit",
                value = 5000f,
                minValue = 500f,
                maxValue = 15000f,
                steps = 29,
                showValue = true,
                unitLabel = "USD",
                onChange = saveLimit,
            )
        val data =
            FormSnippetData(
                formId = "pay_card_controls_form",
                title = ui.text("Card Security & Spend Limits", "title.medium"),
                description = desc,
                switches = payCardSwitches,
                sliders = listOf(slider),
                submitButton = ui.button("btn_save_pay_cards", "Save Card Controls", saveAll, fullWidth = true),
                layout = ui.cardLayout,
            )
        return SnippetEnvelope(
            snippetId = "pay_cards_controls_form",
            snippetType = SnippetTypes.FORM,
            data = ui.encode(data),
        )
    }
}
