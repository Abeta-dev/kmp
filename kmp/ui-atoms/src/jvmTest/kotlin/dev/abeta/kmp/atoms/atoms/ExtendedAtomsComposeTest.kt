package dev.abeta.kmp.atoms.atoms

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.abeta.kmp.atoms.modifiers.LocalUiHapticFeedback
import dev.abeta.kmp.atoms.modifiers.UiHapticKind
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.AlertBannerAtom
import dev.abeta.kmp.contracts.BadgeAtom
import dev.abeta.kmp.contracts.ChipAtom
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.KeyValueRowAtom
import dev.abeta.kmp.contracts.MotionLoopEffect
import dev.abeta.kmp.contracts.MotionSpec
import dev.abeta.kmp.contracts.OptionItem
import dev.abeta.kmp.contracts.OtpInputAtom
import dev.abeta.kmp.contracts.PriceAtom
import dev.abeta.kmp.contracts.QrCodeAtom
import dev.abeta.kmp.contracts.RadioGroupAtom
import dev.abeta.kmp.contracts.RatingAtom
import dev.abeta.kmp.contracts.SegmentedControlAtom
import dev.abeta.kmp.contracts.SelectAtom
import dev.abeta.kmp.contracts.StatTileAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.tokens.DesignTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ExtendedAtomsComposeTest {
    @Test
    fun radioGroupSelectsOptionAndEmitsActionAndHaptic() =
        runComposeUiTest {
            val selectedIds = mutableListOf<String>()
            val capturedActions = mutableListOf<ActionRef>()
            val capturedHaptics = mutableListOf<UiHapticKind>()
            val changeAction = ActionRef(id = "delivery_changed", type = "save_state")

            setContent {
                DesignTheme(darkTheme = false, reducedMotion = true) {
                    CompositionLocalProvider(LocalUiHapticFeedback provides { capturedHaptics.add(it) }) {
                        var selected by remember { mutableStateOf("std") }
                        UiRadioGroup(
                            atom =
                                RadioGroupAtom(
                                    fieldId = "shipping",
                                    label = "Shipping Method",
                                    selectedId = selected,
                                    options =
                                        listOf(
                                            OptionItem(id = "std", label = "Standard", subtitle = "2-3 days"),
                                            OptionItem(id = "exp", label = "Express", badge = "FAST"),
                                        ),
                                    onChange = changeAction,
                                ),
                            selectedIdOverride = selected,
                            onSelect = {
                                selected = it
                                selectedIds.add(it)
                            },
                            onAction = capturedActions::add,
                        )
                    }
                }
            }

            onNodeWithText("Shipping Method").assertIsDisplayed()
            onNodeWithText("FAST").assertIsDisplayed()
            onNodeWithTag("radio_shipping_std").assertIsSelected()
            onNodeWithTag("radio_shipping_exp").assertIsNotSelected().performClick()

            onNodeWithTag("radio_shipping_exp").assertIsSelected()
            assertEquals(listOf("exp"), selectedIds)
            assertEquals(listOf(changeAction), capturedActions)
            assertEquals(listOf(UiHapticKind.CLICK), capturedHaptics)
        }

    @Test
    fun selectOpensDropdownUpdatesSelectionAndShowsHelperText() =
        runComposeUiTest {
            val selections = mutableListOf<String>()
            val capturedActions = mutableListOf<ActionRef>()
            val changeAction = ActionRef(id = "plan_selected", type = "save_state")

            setContent {
                DesignTheme(darkTheme = false, reducedMotion = false) {
                    var selected by remember { mutableStateOf<String?>(null) }
                    UiSelect(
                        atom =
                            SelectAtom(
                                fieldId = "plan",
                                label = "Billing Plan",
                                placeholder = "Choose a plan",
                                helperText = "Switch anytime",
                                options =
                                    listOf(
                                        OptionItem(id = "starter", label = "Starter", subtitle = "Free forever"),
                                        OptionItem(id = "pro", label = "Pro", badge = "POPULAR"),
                                    ),
                                onChange = changeAction,
                            ),
                        selectedIdOverride = selected,
                        onSelect = {
                            selected = it
                            selections.add(it)
                        },
                        onAction = capturedActions::add,
                    )
                }
            }

            onNodeWithText("Billing Plan").assertIsDisplayed()
            onNodeWithText("Choose a plan").assertIsDisplayed()
            onNodeWithText("Switch anytime").assertIsDisplayed()

            onNodeWithTag("select_plan").performClick()
            onNodeWithTag("select_option_plan_pro").performClick()

            onNodeWithText("Pro").assertIsDisplayed()
            assertEquals(listOf("pro"), selections)
            assertEquals(listOf(changeAction), capturedActions)
        }

    @Test
    fun otpInputFiltersNonDigitsMasksPinAndFiresOnCompleteOnce() =
        runComposeUiTest {
            val updates = mutableListOf<String>()
            val capturedActions = mutableListOf<ActionRef>()
            val completeAction = ActionRef(id = "verify_otp", type = "call_api")

            setContent {
                DesignTheme(darkTheme = false, reducedMotion = false) {
                    var code by remember { mutableStateOf("") }
                    UiOtpInput(
                        atom =
                            OtpInputAtom(
                                fieldId = "pin",
                                label = "Security PIN",
                                length = 4,
                                masked = true,
                                helperText = "Enter 4 digits",
                                onComplete = completeAction,
                            ),
                        valueOverride = code,
                        onValueChange = {
                            code = it
                            updates.add(it)
                        },
                        onAction = capturedActions::add,
                    )
                }
            }

            onNodeWithText("Security PIN").assertIsDisplayed()
            onNodeWithText("Enter 4 digits").assertIsDisplayed()
            onNodeWithTag("otp_pin").performTextInput("12a34")

            assertEquals(listOf("1234"), updates)
            assertEquals(listOf(completeAction), capturedActions)
        }

    @Test
    fun chipTogglesSelectionAndInvokesRemoveCallbacks() =
        runComposeUiTest {
            var removeCount = 0
            val capturedActions = mutableListOf<ActionRef>()
            val clickAction = ActionRef(id = "chip_click", type = "track_event")
            val removeAction = ActionRef(id = "chip_remove", type = "track_event")

            setContent {
                DesignTheme(darkTheme = false, reducedMotion = true) {
                    var selected by remember { mutableStateOf(false) }
                    UiChip(
                        atom =
                            ChipAtom(
                                id = "organic",
                                label = "Organic",
                                removable = true,
                                leadingIcon = IconAtom(name = "star"),
                                onClick = clickAction,
                                onRemove = removeAction,
                            ),
                        selectedOverride = selected,
                        onClick = { selected = !selected },
                        onRemove = { removeCount += 1 },
                        onAction = capturedActions::add,
                    )
                }
            }

            onNodeWithTag("chip_organic").assertIsNotSelected().performClick()
            onNodeWithTag("chip_organic").assertIsSelected()
            onNodeWithContentDescription("Remove Organic").performClick()

            assertEquals(1, removeCount)
            assertEquals(listOf(clickAction, removeAction), capturedActions)
        }

    @Test
    fun segmentedControlSlidesSelectionAndEmitsAction() =
        runComposeUiTest {
            val selectedIds = mutableListOf<String>()
            val capturedActions = mutableListOf<ActionRef>()
            val changeAction = ActionRef(id = "tab_changed", type = "save_state")

            setContent {
                DesignTheme(darkTheme = false, reducedMotion = false) {
                    var active by remember { mutableStateOf("delivery") }
                    UiSegmentedControl(
                        atom =
                            SegmentedControlAtom(
                                fieldId = "mode",
                                selectedId = active,
                                options =
                                    listOf(
                                        OptionItem(id = "delivery", label = "Delivery"),
                                        OptionItem(id = "pickup", label = "Pickup"),
                                    ),
                                onChange = changeAction,
                            ),
                        selectedIdOverride = active,
                        onSelect = {
                            active = it
                            selectedIds.add(it)
                        },
                        onAction = capturedActions::add,
                    )
                }
            }

            onNodeWithTag("segment_mode_delivery").assertIsSelected()
            onNodeWithTag("segment_mode_pickup").assertIsNotSelected().performClick()
            onNodeWithTag("segment_mode_pickup").assertIsSelected()
            assertEquals(listOf("pickup"), selectedIds)
            assertEquals(listOf(changeAction), capturedActions)
        }

    @Test
    fun ratingDisplaysFormattedScoreAndSupportsInteractiveTap() =
        runComposeUiTest {
            val ratings = mutableListOf<Float>()
            val capturedActions = mutableListOf<ActionRef>()
            val rateAction = ActionRef(id = "submit_rating", type = "call_api")

            setContent {
                DesignTheme(darkTheme = false, reducedMotion = true) {
                    var score by remember { mutableFloatStateOf(4.5f) }
                    UiRating(
                        atom =
                            RatingAtom(
                                id = "product_rate",
                                fieldId = "rating_field",
                                value = score,
                                maxStars = 5,
                                interactive = true,
                                reviewCount = 124,
                                label = "Score",
                                onRate = rateAction,
                            ),
                        ratingOverride = score,
                        onRate = {
                            score = it
                            ratings.add(it)
                        },
                        onAction = capturedActions::add,
                    )
                }
            }

            onNodeWithTag("rating_product_rate").assertIsDisplayed()
            onNodeWithText("Score").assertIsDisplayed()
            onNodeWithText("4.5 (124)").assertIsDisplayed()

            onNodeWithContentDescription("Rate 5 of 5").performClick()
            onNodeWithText("5.0 (124)").assertIsDisplayed()
            assertEquals(listOf(5f), ratings)
            assertEquals(listOf(rateAction), capturedActions)
        }

    @Test
    fun priceAndBadgeRenderTabularValuesCapAndDotMode() =
        runComposeUiTest {
            setContent {
                DesignTheme(darkTheme = false, reducedMotion = false) {
                    Column {
                        UiPrice(
                            atom =
                                PriceAtom(
                                    id = "berry_price",
                                    amountText = "$12.99",
                                    originalAmountText = "$16.00",
                                    discountLabel = "19% OFF",
                                    unitSuffix = "/ pack",
                                ),
                        )
                        UiBadge(atom = BadgeAtom(id = "inbox_count", count = 145, maxCount = 99))
                        UiBadge(
                            atom =
                                BadgeAtom(
                                    id = "live_dot",
                                    dotOnly = true,
                                    label = "Online",
                                    variant = TagVariant.SUCCESS,
                                    motion = MotionSpec(loopEffect = MotionLoopEffect.PULSE),
                                ),
                        )
                    }
                }
            }

            onNodeWithTag("price_berry_price").assertIsDisplayed()
            onNodeWithText("$12.99").assertIsDisplayed()
            onNodeWithText("/ pack").assertIsDisplayed()
            onNodeWithText("$16.00").assertIsDisplayed()
            onNodeWithText("19% OFF").assertIsDisplayed()
            onNodeWithTag("badge_inbox_count").assertIsDisplayed()
            onNodeWithText("99+").assertIsDisplayed()
            onNodeWithTag("badge_live_dot").assertExists()
        }

    @Test
    fun alertBannerRendersContentAndDispatchesActionAndDismiss() =
        runComposeUiTest {
            var dismissed = 0
            val capturedActions = mutableListOf<ActionRef>()
            val bannerCta = ActionRef(id = "banner_cta", type = "navigate")
            val bannerDismiss = ActionRef(id = "banner_dismiss", type = "track_event")

            setContent {
                DesignTheme(darkTheme = false, reducedMotion = true) {
                    UiAlertBanner(
                        atom =
                            AlertBannerAtom(
                                id = "promo_alert",
                                title = "Delivery delayed",
                                message = "Heavy rain near your area",
                                variant = TagVariant.WARNING,
                                actionLabel = "Track Rider",
                                onAction = bannerCta,
                                dismissible = true,
                                onDismiss = bannerDismiss,
                            ),
                        onDismiss = { dismissed += 1 },
                        onAction = capturedActions::add,
                    )
                }
            }

            onNodeWithTag("alert_banner_promo_alert").assertIsDisplayed()
            onNodeWithText("Delivery delayed").assertIsDisplayed()
            onNodeWithText("Heavy rain near your area").assertIsDisplayed()
            onNodeWithText("Track Rider").performClick()
            onNodeWithContentDescription("Dismiss Delivery delayed").performClick()

            assertEquals(1, dismissed)
            assertEquals(listOf(bannerCta, bannerDismiss), capturedActions)
        }

    @Test
    fun statTileAndKeyValueRowRenderMetricsAndDispatchActions() =
        runComposeUiTest {
            val capturedActions = mutableListOf<ActionRef>()
            val statAction = ActionRef(id = "open_stats", type = "navigate")
            val kvAction = ActionRef(id = "open_breakdown", type = "open_dialog")

            setContent {
                DesignTheme(darkTheme = true, reducedMotion = true) {
                    Column {
                        UiStatTile(
                            atom =
                                StatTileAtom(
                                    id = "revenue",
                                    label = "Revenue",
                                    value = "$18.4K",
                                    deltaLabel = "+9.2%",
                                    deltaPositive = true,
                                    caption = "Last 24h",
                                    icon = IconAtom(name = "star"),
                                    onClick = statAction,
                                ),
                            onAction = capturedActions::add,
                        )
                        UiKeyValueRow(
                            atom =
                                KeyValueRowAtom(
                                    id = "total",
                                    label = "Grand Total",
                                    sublabel = "Tax included",
                                    value = "$42.10",
                                    valueColor = ColorTokenRef(token = "text.success"),
                                    emphasized = true,
                                    showDivider = true,
                                    onClick = kvAction,
                                ),
                            onAction = capturedActions::add,
                        )
                    }
                }
            }

            onNodeWithTag("stat_tile_revenue").assertIsDisplayed().performClick()
            onNodeWithText("Revenue").assertIsDisplayed()
            onNodeWithText("$18.4K").assertIsDisplayed()
            onNodeWithText("▲ +9.2%").assertIsDisplayed()
            onNodeWithText("Last 24h").assertIsDisplayed()
            onNodeWithTag("kv_row_total").assertIsDisplayed()
            onNodeWithText("Grand Total").assertIsDisplayed().performClick()
            onNodeWithText("Tax included").assertIsDisplayed()
            onNodeWithText("$42.10").assertIsDisplayed()
            assertEquals(listOf(statAction, kvAction), capturedActions)
        }

    @Test
    fun qrCodeRendersMatrixFrameLabelCaptionAndDispatchesClick() =
        runComposeUiTest {
            val capturedActions = mutableListOf<ActionRef>()
            val qrAction = ActionRef(id = "expand_qr", type = "open_dialog")

            setContent {
                DesignTheme(darkTheme = true, reducedMotion = true) {
                    UiQrCode(
                        atom =
                            QrCodeAtom(
                                id = "pass",
                                payload = "abeta://ticket/8841",
                                label = "Boarding Pass",
                                caption = "Gate B4",
                                sizeDp = 120,
                                onClick = qrAction,
                            ),
                        onAction = capturedActions::add,
                    )
                }
            }

            onNodeWithTag("qr_code_pass").assertIsDisplayed().performClick()
            onNodeWithText("Boarding Pass").assertIsDisplayed()
            onNodeWithText("Gate B4").assertIsDisplayed()
            assertEquals(listOf(qrAction), capturedActions)
        }
}
