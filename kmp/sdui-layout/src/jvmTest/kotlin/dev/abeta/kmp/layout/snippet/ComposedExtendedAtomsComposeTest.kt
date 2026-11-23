package dev.abeta.kmp.layout.snippet

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.AlertBannerAtom
import dev.abeta.kmp.contracts.BadgeAtom
import dev.abeta.kmp.contracts.ChipAtom
import dev.abeta.kmp.contracts.ComposedBlockSnippetData
import dev.abeta.kmp.contracts.ComposedNode
import dev.abeta.kmp.contracts.ComposedNodeKind
import dev.abeta.kmp.contracts.KeyValueRowAtom
import dev.abeta.kmp.contracts.OptionItem
import dev.abeta.kmp.contracts.OtpInputAtom
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.PriceAtom
import dev.abeta.kmp.contracts.QrCodeAtom
import dev.abeta.kmp.contracts.RadioGroupAtom
import dev.abeta.kmp.contracts.RatingAtom
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SegmentedControlAtom
import dev.abeta.kmp.contracts.SelectAtom
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.StatTileAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.layout.engine.LayoutFormSeeder
import dev.abeta.kmp.layout.engine.LayoutUiState
import dev.abeta.kmp.layout.ui.SduiScreen
import dev.abeta.kmp.state.session.InMemorySessionStore
import dev.abeta.kmp.tokens.DesignTheme
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ComposedExtendedAtomsComposeTest {
    @Test
    fun layoutFormSeederExtractsInitialValuesFromExtendedComposedControls() {
        val page = buildComposedPage(buildInteractiveComposedChildren())
        val seeded = LayoutFormSeeder.extractInitialFormValues(page, InMemorySessionStore())

        assertEquals("fast", seeded["delivery_tier"])
        assertEquals("usd", seeded["currency"])
        assertEquals("12", seeded["verify_otp"])
        assertEquals("monthly", seeded["cycle"])
        assertEquals("4.0", seeded["service_rating"])
    }

    @Test
    fun composedBlockRendersInteractiveExtendedControlsAndSyncsFormValues() =
        runComposeUiTest {
            val capturedActions = mutableListOf<ActionRef>()
            val observedFormUpdates = mutableMapOf<String, String>()
            val page = buildComposedPage(buildInteractiveComposedChildren())
            val seeded = LayoutFormSeeder.extractInitialFormValues(page, InMemorySessionStore())

            setContent {
                val registry = remember { BuiltInSnippetModule.createDefaultRegistry() }
                val formValues = remember { mutableStateMapOf<String, String>().apply { putAll(seeded) } }
                DesignTheme(darkTheme = false, reducedMotion = true) {
                    SduiScreen(
                        state = LayoutUiState.Ready(page = page, formValues = formValues.toMap()),
                        snippetRegistry = registry,
                        onAction = capturedActions::add,
                        onFormValueChange = { fieldId, value ->
                            formValues[fieldId] = value
                            observedFormUpdates[fieldId] = value
                        },
                    )
                }
            }

            onNodeWithTag("composed_block_extended_showcase").assertIsDisplayed()
            onNodeWithTag("radio_delivery_tier_fast").assertIsSelected()
            onNodeWithTag("radio_delivery_tier_eco").performScrollTo().performClick()
            assertEquals("eco", observedFormUpdates["delivery_tier"])

            onNodeWithTag("select_currency").performScrollTo().performClick()
            onNodeWithTag("select_option_currency_eur").performClick()
            assertEquals("eur", observedFormUpdates["currency"])

            onNodeWithTag("otp_verify_otp").performScrollTo().performTextInput("34")
            assertEquals("1234", observedFormUpdates["verify_otp"])

            onNodeWithTag("segment_cycle_yearly").performScrollTo().performClick()
            assertEquals("yearly", observedFormUpdates["cycle"])

            onNodeWithContentDescription("Rate 5 of 5").performScrollTo().performClick()
            assertEquals("5.0", observedFormUpdates["service_rating"])
        }

    @Test
    fun composedBlockRendersDisplayAndDismissibleExtendedAtoms() =
        runComposeUiTest {
            val capturedActions = mutableListOf<ActionRef>()
            val page = buildComposedPage(buildDisplayComposedChildren())

            setContent {
                val registry = remember { BuiltInSnippetModule.createDefaultRegistry() }
                DesignTheme(darkTheme = false, reducedMotion = true) {
                    SduiScreen(
                        state = LayoutUiState.Ready(page = page),
                        snippetRegistry = registry,
                        onAction = capturedActions::add,
                        onFormValueChange = { _, _ -> },
                    )
                }
            }

            onNodeWithTag("chip_promo").performScrollTo().assertIsDisplayed()
            onNodeWithTag("price_sku").performScrollTo().assertIsDisplayed()
            onNodeWithTag("badge_alerts").performScrollTo().assertIsDisplayed()
            onNodeWithTag("stat_tile_orders").performScrollTo().assertIsDisplayed()
            onNodeWithTag("kv_row_subtotal").performScrollTo().assertIsDisplayed()
            onNodeWithTag("qr_code_pickup").performScrollTo().assertIsDisplayed()

            onNodeWithTag("alert_banner_promo_notice").performScrollTo().assertIsDisplayed()
            onNodeWithTag("alert_banner_dismiss_promo_notice").performClick()
            onNodeWithTag("alert_banner_promo_notice").assertDoesNotExist()
            assertEquals("banner_dismissed", capturedActions.last().id)
        }

    private fun buildComposedPage(children: List<ComposedNode>): PageResponse {
        val block =
            ComposedBlockSnippetData(
                blockId = "extended_showcase",
                recipeName = "extended_atoms_v1",
                root =
                    ComposedNode(
                        nodeId = "root_col",
                        kind = ComposedNodeKind.COLUMN,
                        spacingDp = 12,
                        children = children,
                    ),
            )
        val envelope =
            SnippetEnvelope(
                snippetId = "cb_extended",
                snippetType = SnippetTypes.COMPOSED_BLOCK,
                data = SduiJson.encodeToJsonElement(ComposedBlockSnippetData.serializer(), block) as JsonObject,
            )
        return PageResponse(pageId = "extended_composed", snippets = listOf(envelope))
    }

    private fun buildInteractiveComposedChildren(): List<ComposedNode> =
        listOf(
            ComposedNode(
                kind = ComposedNodeKind.RADIO_GROUP,
                radioGroup =
                    RadioGroupAtom(
                        fieldId = "delivery_tier",
                        label = "Tier",
                        selectedId = "fast",
                        options =
                            listOf(
                                OptionItem(id = "fast", label = "Express"),
                                OptionItem(id = "eco", label = "Eco"),
                            ),
                    ),
            ),
            ComposedNode(
                kind = ComposedNodeKind.SELECT,
                select =
                    SelectAtom(
                        fieldId = "currency",
                        label = "Currency",
                        selectedId = "usd",
                        options =
                            listOf(
                                OptionItem(id = "usd", label = "USD"),
                                OptionItem(id = "eur", label = "EUR"),
                            ),
                    ),
            ),
            ComposedNode(
                kind = ComposedNodeKind.OTP_INPUT,
                otpInput =
                    OtpInputAtom(
                        fieldId = "verify_otp",
                        length = 4,
                        initialValue = "12",
                        onComplete = ActionRef(id = "otp_complete", type = "track_event"),
                    ),
            ),
            ComposedNode(
                kind = ComposedNodeKind.SEGMENTED_CONTROL,
                segmentedControl =
                    SegmentedControlAtom(
                        fieldId = "cycle",
                        selectedId = "monthly",
                        options =
                            listOf(
                                OptionItem(id = "monthly", label = "Monthly"),
                                OptionItem(id = "yearly", label = "Yearly"),
                            ),
                    ),
            ),
            ComposedNode(
                kind = ComposedNodeKind.RATING,
                rating = RatingAtom(id = "svc", fieldId = "service_rating", value = 4f, interactive = true),
            ),
        )

    private fun buildDisplayComposedChildren(): List<ComposedNode> =
        listOf(
            ComposedNode(
                kind = ComposedNodeKind.CHIP,
                chip = ChipAtom(id = "promo", label = "Free Delivery", selected = true),
            ),
            ComposedNode(
                kind = ComposedNodeKind.PRICE,
                price = PriceAtom(id = "sku", amountText = "$19.00", discountLabel = "15% OFF"),
            ),
            ComposedNode(
                kind = ComposedNodeKind.BADGE,
                badge = BadgeAtom(id = "alerts", count = 12, variant = TagVariant.BRAND),
            ),
            ComposedNode(
                kind = ComposedNodeKind.ALERT_BANNER,
                alertBanner =
                    AlertBannerAtom(
                        id = "promo_notice",
                        title = "Member perk active",
                        variant = TagVariant.INFO,
                        dismissible = true,
                        onDismiss = ActionRef(id = "banner_dismissed", type = "track_event"),
                    ),
            ),
            ComposedNode(
                kind = ComposedNodeKind.STAT_TILE,
                statTile =
                    StatTileAtom(
                        id = "orders",
                        label = "Orders",
                        value = "94",
                        deltaLabel = "+8%",
                        deltaPositive = true,
                    ),
            ),
            ComposedNode(
                kind = ComposedNodeKind.KEY_VALUE_ROW,
                keyValueRow =
                    KeyValueRowAtom(
                        id = "subtotal",
                        label = "Subtotal",
                        value = "$19.00",
                        showDivider = true,
                    ),
            ),
            ComposedNode(
                kind = ComposedNodeKind.QR_CODE,
                qrCode = QrCodeAtom(id = "pickup", payload = "abeta://pickup/94", sizeDp = 96),
            ),
        )
}
