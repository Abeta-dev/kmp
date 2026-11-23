package dev.abeta.kmp.storybook

import dev.abeta.kmp.contracts.AlertBannerAtom
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.ComposedBlockSnippetData
import dev.abeta.kmp.contracts.ComposedNode
import dev.abeta.kmp.contracts.ComposedNodeKind
import dev.abeta.kmp.contracts.DividerAtom
import dev.abeta.kmp.contracts.KeyValueRowAtom
import dev.abeta.kmp.contracts.OptionItem
import dev.abeta.kmp.contracts.OtpInputAtom
import dev.abeta.kmp.contracts.QrCodeAtom
import dev.abeta.kmp.contracts.SegmentedControlAtom
import dev.abeta.kmp.contracts.SelectAtom
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.StatTileAtom
import dev.abeta.kmp.contracts.TagVariant

/**
 * Pure Kotlin Multiplatform `composed_block` builders showcasing extended atoms
 * (`UiQrCode`, `UiStatTile`, `UiKeyValueRow`, `UiSegmentedControl`, `UiSelect`,
 * `UiOtpInput`, and `UiAlertBanner`) across Abeta Pulse, Abeta Trade, and Abeta Pay.
 */
@Suppress("MagicNumber")
internal object SuperAppDemoExtendedBlocks {
    private val ui = SuperAppDemoBuilder

    fun buildPulseQrGateDetailsNodes(): List<ComposedNode> =
        listOf(
            kvNode("qr_kv_gate", KeyValueRowAtom(id = "p_gate", label = "Entry Gate", value = "Gate 4B Fast-Track")),
            kvNode(
                "qr_kv_seats",
                KeyValueRowAtom(
                    id = "p_seats",
                    label = "Reserved Seats",
                    value = "Row C · Seat 12–13",
                    valueColor = ColorTokenRef("brand.primary"),
                    emphasized = true,
                    showDivider = true,
                ),
            ),
        )

    fun buildPulseQrCenterBox(): ComposedNode {
        val qrAtom =
            QrCodeAtom(
                id = "pulse_pass_qr",
                payload = "ABETA-PULSE:PLS-7712:GATE4B:ROWC-12-13",
                label = "QR · PLS-7712 · ARENA GOLD (2x)",
                caption = "Present at Harbor Dome Gate 4B scanner",
                sizeDp = 156,
                onClick = ui.toastAction("pulse_qr_tap", "Pass PLS-7712 ready for NFC & optical scan!", "success"),
            )
        return ComposedNode(
            nodeId = "qr_code_box",
            kind = ComposedNodeKind.BOX,
            horizontalAlignment = "center",
            layout = ui.fullWidthLayout,
            children = listOf(ComposedNode("qr_matrix_node", ComposedNodeKind.QR_CODE, qrCode = qrAtom)),
        )
    }

    fun buildTradeQuotesTelemetryBlock(): SnippetEnvelope {
        val ok = ColorTokenRef("status.success")
        val kvRows =
            listOf(
                KeyValueRowAtom(id = "t_unit", label = "Best Unit Rate", value = "\$0.68 / u"),
                KeyValueRowAtom(id = "t_qty", label = "Batch Quantity", value = "500 units"),
                KeyValueRowAtom(
                    id = "t_gst",
                    label = "GST & Freight Docs",
                    value = "Included",
                    valueColor = ok,
                    showDivider = true,
                ),
                KeyValueRowAtom(
                    id = "t_total",
                    label = "Estimated PO Total",
                    value = "\$340.00",
                    valueColor = ColorTokenRef("brand.primary"),
                    emphasized = true,
                ),
            ).mapIndexed { i, kv -> kvNode("trade_kv_$i", kv) }
        val root =
            ComposedNode(
                nodeId = "trade_quotes_summary_card",
                kind = ComposedNodeKind.CARD,
                spacingDp = 8,
                layout = ui.cardLayout,
                children =
                    listOf(
                        buildTradeStatsRow(),
                        ComposedNode("trade_div", ComposedNodeKind.DIVIDER, divider = DividerAtom()),
                    ) + kvRows,
            )
        return SnippetEnvelope(
            snippetId = "trade_quotes_telemetry_block",
            snippetType = SnippetTypes.COMPOSED_BLOCK,
            data = ui.encode(ComposedBlockSnippetData("trade_quotes_telemetry", "b2b_quote_telemetry", root)),
        )
    }

    private fun buildTradeStatsRow(): ComposedNode {
        val savingsTile =
            StatTileAtom(
                id = "trade_bulk_savings",
                label = "Bulk Savings",
                value = "18%",
                deltaLabel = "+18% vs retail",
                deltaPositive = true,
                caption = "500-unit wholesale tier",
            )
        val slaTile =
            StatTileAtom(
                id = "trade_dispatch_sla",
                label = "Dispatch SLA",
                value = "4 Days",
                deltaLabel = "ISO 9001",
                deltaPositive = true,
                caption = "Apex Corrugated hub",
            )
        return ComposedNode(
            nodeId = "trade_stats_row",
            kind = ComposedNodeKind.ROW,
            spacingDp = 10,
            layout = ui.fullWidthLayout,
            children =
                listOf(
                    ComposedNode("trade_savings_node", ComposedNodeKind.STAT_TILE, weight = 1f, statTile = savingsTile),
                    ComposedNode("trade_sla_node", ComposedNodeKind.STAT_TILE, weight = 1f, statTile = slaTile),
                ),
        )
    }

    fun buildPayTelemetryRowBlock(): SnippetEnvelope {
        val cashbackTile =
            StatTileAtom(
                id = "pay_stat_cashback",
                label = "Monthly Cashback",
                value = "\$64.20",
                deltaLabel = "+14%",
                deltaPositive = true,
                caption = "5% instant rewards",
            )
        val transfersTile =
            StatTileAtom(
                id = "pay_stat_transfers",
                label = "Instant Transfers",
                value = "18",
                deltaLabel = "0% fee",
                deltaPositive = true,
                caption = "Cleared in <2 sec",
            )
        val root =
            ComposedNode(
                nodeId = "pay_stats_row",
                kind = ComposedNodeKind.ROW,
                spacingDp = 12,
                layout = ui.horizontalLayout,
                children =
                    listOf(
                        ComposedNode("pay_cb_tile", ComposedNodeKind.STAT_TILE, weight = 1f, statTile = cashbackTile),
                        ComposedNode("pay_tx_tile", ComposedNodeKind.STAT_TILE, weight = 1f, statTile = transfersTile),
                    ),
            )
        return SnippetEnvelope(
            snippetId = "pay_telemetry_row_block",
            snippetType = SnippetTypes.COMPOSED_BLOCK,
            data = ui.encode(ComposedBlockSnippetData("pay_telemetry_row", "wallet_stats_row", root)),
        )
    }

    fun buildPayReceiveQrBlock(): SnippetEnvelope {
        val receiveQr =
            QrCodeAtom(
                id = "pay_receive_qr",
                payload = "ABETA-PAY:RECEIVE:@abeta.user:USD",
                label = "Scan to Receive · @abeta.user",
                caption = "Zero-fee instant P2P wallet QR",
                sizeDp = 148,
                onClick = ui.toastAction("pay_qr_copy", "Handle @abeta.user copied!", "success"),
            )
        val root =
            ComposedNode(
                nodeId = "pay_qr_card",
                kind = ComposedNodeKind.CARD,
                horizontalAlignment = "center",
                spacingDp = 8,
                layout = ui.cardLayout,
                children = listOf(ComposedNode("pay_qr_node", ComposedNodeKind.QR_CODE, qrCode = receiveQr)),
            )
        return SnippetEnvelope(
            snippetId = "pay_receive_qr_block",
            snippetType = SnippetTypes.COMPOSED_BLOCK,
            data = ui.encode(ComposedBlockSnippetData("pay_receive_qr", "wallet_receive_qr", root)),
        )
    }

    fun buildPayRailAndPinBlock(): SnippetEnvelope {
        val railSegment =
            SegmentedControlAtom(
                fieldId = "pay_rail_segment",
                options = listOf(OptionItem("instant_p2p", "Instant P2P"), OptionItem("bank_rail", "Bank Rail")),
                selectedId = "instant_p2p",
                onChange = ui.toastAction("pay_rail_change", "Settlement rail updated"),
            )
        val fundingSelect =
            SelectAtom(
                fieldId = "pay_funding_source",
                label = "Funding Source",
                options =
                    listOf(
                        OptionItem("wallet", "Abeta Wallet · \$2,480.50", "Instant · \$0.00 fee", badge = "DEFAULT"),
                        OptionItem("metal", "Metal Card •••• 4829", "5% instant cashback"),
                        OptionItem("bank", "Linked Bank •••• 9104", "Zero-fee bank clearing"),
                    ),
                selectedId = "wallet",
                helperText = "Zero-fee transfers across all verified Abeta accounts",
                onChange = ui.toastAction("pay_source_change", "Funding source updated"),
            )
        val pinInput =
            OtpInputAtom(
                fieldId = "pay_security_pin",
                label = "4-Digit Security PIN",
                length = 4,
                initialValue = "4829",
                masked = true,
                helperText = "Masked PIN authorizes instant rail settlement",
                onComplete = ui.toastAction("pay_pin_ok", "4-Digit Security PIN verified!", "success"),
            )
        val railNode =
            ComposedNode(
                nodeId = "pay_rail_seg",
                kind = ComposedNodeKind.SEGMENTED_CONTROL,
                segmentedControl = railSegment,
            )
        val root =
            ComposedNode(
                nodeId = "pay_rail_pin_card",
                kind = ComposedNodeKind.CARD,
                spacingDp = 12,
                layout = ui.cardLayout,
                children =
                    listOf(
                        railNode,
                        ComposedNode("pay_src_sel", ComposedNodeKind.SELECT, select = fundingSelect),
                        ComposedNode("pay_pin_otp", ComposedNodeKind.OTP_INPUT, otpInput = pinInput),
                    ),
            )
        return SnippetEnvelope(
            snippetId = "pay_rail_pin_composed",
            snippetType = SnippetTypes.COMPOSED_BLOCK,
            data = ui.encode(ComposedBlockSnippetData("pay_rail_pin", "transfer_rail_funding_pin", root)),
        )
    }

    fun buildPayReceiptBreakdownBlock(): SnippetEnvelope {
        val ok = ColorTokenRef("status.success")
        val verifiedBanner =
            AlertBannerAtom(
                id = "pay_receipt_verified",
                title = "Instant Settlement Verified",
                message = "Clearing proof #PAY-3081 confirmed on Abeta Instant Rail with \$0.00 fee.",
                variant = TagVariant.SUCCESS,
            )
        val rows =
            listOf(
                KeyValueRowAtom(id = "p_amt", label = "Transfer Amount", value = "\$85.00"),
                KeyValueRowAtom(id = "p_fee", label = "Network Fee · \$0.00", value = "\$0.00", valueColor = ok),
                KeyValueRowAtom(
                    id = "p_cb",
                    label = "5% Cashback",
                    value = "+\$4.25",
                    valueColor = ok,
                    showDivider = true,
                ),
                KeyValueRowAtom(
                    id = "p_tot",
                    label = "Total Debited",
                    value = "\$85.00",
                    valueColor = ColorTokenRef("brand.primary"),
                    emphasized = true,
                ),
            ).mapIndexed { i, kv -> kvNode("pay_rcpt_kv_$i", kv) }
        val root =
            ComposedNode(
                nodeId = "pay_receipt_breakdown_card",
                kind = ComposedNodeKind.CARD,
                spacingDp = 8,
                layout = ui.cardLayout,
                children =
                    listOf(
                        ComposedNode("pay_rcpt_banner", ComposedNodeKind.ALERT_BANNER, alertBanner = verifiedBanner),
                    ) + rows,
            )
        return SnippetEnvelope(
            snippetId = "pay_receipt_breakdown_block",
            snippetType = SnippetTypes.COMPOSED_BLOCK,
            data = ui.encode(ComposedBlockSnippetData("pay_receipt_breakdown", "settlement_receipt_card", root)),
        )
    }

    private fun kvNode(
        nodeId: String,
        atom: KeyValueRowAtom,
    ): ComposedNode = ComposedNode(nodeId = nodeId, kind = ComposedNodeKind.KEY_VALUE_ROW, keyValueRow = atom)
}
