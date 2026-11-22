package dev.abeta.kmp.storybook

import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.ButtonSize
import dev.abeta.kmp.contracts.ChipGroupSnippetData
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.ComposedBlockSnippetData
import dev.abeta.kmp.contracts.ComposedNode
import dev.abeta.kmp.contracts.ComposedNodeKind
import dev.abeta.kmp.contracts.CountdownAtom
import dev.abeta.kmp.contracts.FormSnippetData
import dev.abeta.kmp.contracts.MotionLoopEffect
import dev.abeta.kmp.contracts.MotionSpec
import dev.abeta.kmp.contracts.ProgressAtom
import dev.abeta.kmp.contracts.ProgressKind
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.StepperAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TextFieldAtom
import dev.abeta.kmp.contracts.TypographyTokenRef

private const val MOQ_RATE_LOCK_SEC = 420
private const val MOQ_PROGRESS = 0.80f
private const val MOQ_MIN_CARTONS = 10
private const val MOQ_MAX_CARTONS = 50
private const val MOQ_UNIT_CENTS = 4500
private const val COMPOSED_SPACING_DP = 10
private const val MIN_GSTIN_LEN = 6

/**
 * Abeta Trade B2B RFQ fixtures, molded MOQ `composed_block` builder, and validator
 * for [EndToEndJourneysStory].
 */
internal object EndToEndTradeFixtures {
    const val TRADE_CARTONS_FIELD = "e2e_trade_cartons"
    const val TRADE_COMPANY_FIELD = "buyer_company"
    const val TRADE_GSTIN_FIELD = "gstin"
    const val TRADE_CITY_FIELD = "delivery_city"
    const val TRADE_SUBMIT_RFQ_ACTION = "e2e_trade_submit_rfq"
    const val TRADE_DEFAULT_CARTONS = MOQ_MIN_CARTONS

    fun buildTradeVerifiedChipsEnvelope(): SnippetEnvelope {
        val chips =
            ChipGroupSnippetData(
                groupId = "e2e_trade_badges",
                title = SnippetsSamples.txt("Supplier Trust & Compliance Filters", "label.large", "text.secondary"),
                multiSelect = true,
                chips =
                    listOf(
                        EndToEndJourneyFixtures.filterChip(
                            id = "gst_verified",
                            label = "GST Verified",
                            selected = true,
                            iconName = "check",
                        ),
                        EndToEndJourneyFixtures.filterChip("iso_9001", "ISO 9001", selected = true),
                        EndToEndJourneyFixtures.filterChip("escrow", "Escrow Protected", badge = "SAFE"),
                        EndToEndJourneyFixtures.filterChip("pan_india", "Pan-India Dispatch"),
                    ),
            )
        return SnippetEnvelope(
            snippetId = "e2e_trade_chips_env",
            snippetType = SnippetTypes.CHIP_GROUP,
            data = SnippetsSamples.encode(chips),
        )
    }

    fun buildTradeMoqComposedBlock(cartons: Int): SnippetEnvelope {
        val safeCartons = cartons.coerceIn(MOQ_MIN_CARTONS, MOQ_MAX_CARTONS)
        val block =
            ComposedBlockSnippetData(
                blockId = "e2e_trade_moq_block",
                recipeName = "b2b_bulk_moq_tier_v1",
                root =
                    ComposedNode(
                        nodeId = "e2e_moq_card",
                        kind = ComposedNodeKind.CARD,
                        spacingDp = COMPOSED_SPACING_DP,
                        layout = SnippetsSamples.cardLayout,
                        children =
                            listOf(
                                buildMoqHeaderRow(),
                                ComposedNode(
                                    kind = ComposedNodeKind.TEXT,
                                    text =
                                        SnippetsSamples.txt(
                                            "Cold-Pressed Olive Oil (12 × 1L Export Carton)",
                                            "title.medium",
                                        ),
                                ),
                                ComposedNode(
                                    kind = ComposedNodeKind.PROGRESS,
                                    progress =
                                        ProgressAtom(
                                            kind = ProgressKind.LINEAR,
                                            progress = MOQ_PROGRESS,
                                            label = "80% toward Tier-2 volume rebate (25+ cartons)",
                                        ),
                                ),
                                buildMoqFooterRow(safeCartons),
                            ),
                    ),
            )
        return SnippetEnvelope(
            snippetId = "e2e_trade_moq_env",
            snippetType = SnippetTypes.COMPOSED_BLOCK,
            data = SnippetsSamples.encode(block),
        )
    }

    fun buildTradeRfqFormEnvelope(): SnippetEnvelope {
        val form =
            FormSnippetData(
                formId = "e2e_trade_rfq_form",
                title = SnippetsSamples.txt("Submit GST-Verified RFQ", "title.medium"),
                description =
                    SnippetsSamples.txt(
                        "Validated fields dispatch directly to 3 GST-verified Abeta Trade wholesalers.",
                        "body.small",
                        "text.secondary",
                    ),
                fields =
                    listOf(
                        TextFieldAtom(
                            fieldId = TRADE_COMPANY_FIELD,
                            label = "Buyer Company Name",
                            placeholder = "Abeta Retail Hub Pvt Ltd",
                            required = true,
                        ),
                        TextFieldAtom(
                            fieldId = TRADE_GSTIN_FIELD,
                            label = "GSTIN / Tax ID",
                            placeholder = "29AABCA1234F1Z5",
                            required = true,
                        ),
                        TextFieldAtom(
                            fieldId = TRADE_CITY_FIELD,
                            label = "Delivery Hub City",
                            placeholder = "Bengaluru",
                            required = true,
                        ),
                    ),
                submitButton =
                    ButtonAtom(
                        label = "Submit Verified RFQ",
                        size = ButtonSize.MEDIUM,
                        fullWidth = true,
                        onClick = ActionRef(id = TRADE_SUBMIT_RFQ_ACTION, type = ActionTypes.CALL_API),
                    ),
                layout = SnippetsSamples.cardLayout,
            )
        return SnippetEnvelope(
            snippetId = "e2e_trade_rfq_env",
            snippetType = SnippetTypes.FORM,
            data = SnippetsSamples.encode(form),
        )
    }

    fun validateTradeRfq(formValues: Map<String, String>): Map<String, String> =
        buildMap {
            if (formValues[TRADE_COMPANY_FIELD].orEmpty().isBlank()) {
                put(TRADE_COMPANY_FIELD, "Company name is required for B2B RFQ.")
            }
            if (formValues[TRADE_GSTIN_FIELD].orEmpty().trim().length < MIN_GSTIN_LEN) {
                put(TRADE_GSTIN_FIELD, "Enter a valid GSTIN (at least $MIN_GSTIN_LEN characters).")
            }
            if (formValues[TRADE_CITY_FIELD].orEmpty().isBlank()) {
                put(TRADE_CITY_FIELD, "Delivery hub city is required.")
            }
        }

    private fun buildMoqHeaderRow(): ComposedNode =
        ComposedNode(
            kind = ComposedNodeKind.ROW,
            mainAxisArrangement = "space_between",
            children =
                listOf(
                    ComposedNode(
                        kind = ComposedNodeKind.TAG,
                        tag =
                            TagAtom(
                                label = "GST VERIFIED · MOQ $MOQ_MIN_CARTONS",
                                variant = TagVariant.SUCCESS,
                                motion = MotionSpec(loopEffect = MotionLoopEffect.SHIMMER),
                            ),
                    ),
                    ComposedNode(
                        kind = ComposedNodeKind.COUNTDOWN,
                        countdown =
                            CountdownAtom(
                                countdownId = "e2e_moq_rate_lock",
                                remainingSeconds = MOQ_RATE_LOCK_SEC,
                                label = "Rate Lock",
                            ),
                    ),
                ),
        )

    private fun buildMoqFooterRow(safeCartons: Int): ComposedNode {
        val totalFormatted = AnimationNudgesFixtures.formatUsdCents(safeCartons * MOQ_UNIT_CENTS)
        return ComposedNode(
            kind = ComposedNodeKind.ROW,
            mainAxisArrangement = "space_between",
            children =
                listOf(
                    ComposedNode(
                        kind = ComposedNodeKind.ROLLING_COUNTER,
                        counterValue = totalFormatted,
                        counterTypography = TypographyTokenRef("headline.small"),
                        counterColor = ColorTokenRef("text.brand"),
                    ),
                    ComposedNode(
                        kind = ComposedNodeKind.STEPPER,
                        stepper =
                            StepperAtom(
                                fieldId = TRADE_CARTONS_FIELD,
                                label = "Cartons",
                                quantity = safeCartons,
                                minQuantity = MOQ_MIN_CARTONS,
                                maxQuantity = MOQ_MAX_CARTONS,
                            ),
                    ),
                ),
        )
    }
}
