package dev.abeta.kmp.atoms.atoms

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.modifiers.LocalUiHapticFeedback
import dev.abeta.kmp.atoms.modifiers.UiHapticKind
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.AvatarAtom
import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.CheckboxAtom
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.MotionDurationToken
import dev.abeta.kmp.contracts.MotionLoopEffect
import dev.abeta.kmp.contracts.MotionSpec
import dev.abeta.kmp.contracts.MotionTransition
import dev.abeta.kmp.contracts.SkeletonAtom
import dev.abeta.kmp.contracts.SkeletonKind
import dev.abeta.kmp.contracts.SliderAtom
import dev.abeta.kmp.contracts.StepperAtom
import dev.abeta.kmp.contracts.SwitchAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TextFieldAtom
import dev.abeta.kmp.tokens.DesignTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class UiAtomsComposeTest {
    @Test
    fun clickingUiButtonEmitsActionRef() =
        runComposeUiTest {
            val capturedActions = mutableListOf<ActionRef>()
            val capturedHaptics = mutableListOf<UiHapticKind>()
            val expectedAction = ActionRef(id = "submit_btn", type = "navigate")

            setContent {
                DesignTheme(darkTheme = false, reducedMotion = true) {
                    CompositionLocalProvider(LocalUiHapticFeedback provides { capturedHaptics.add(it) }) {
                        UiButton(
                            atom = ButtonAtom(label = "Submit", onClick = expectedAction),
                            onAction = { capturedActions.add(it) },
                        )
                    }
                }
            }

            onNodeWithText("Submit").assertIsDisplayed().performClick()
            assertEquals(listOf(expectedAction), capturedActions)
            assertEquals(listOf(UiHapticKind.CLICK), capturedHaptics)
        }

    @Test
    fun clickableUiIconEnforcesAccessibleTouchTargetAndRoleWhileDecorativeIconKeepsExactSize() =
        runComposeUiTest {
            val capturedActions = mutableListOf<ActionRef>()
            val capturedHaptics = mutableListOf<UiHapticKind>()
            val refreshAction = ActionRef(id = "refresh_icon", type = "refresh_page")

            setContent {
                DesignTheme(darkTheme = false, reducedMotion = true) {
                    CompositionLocalProvider(LocalUiHapticFeedback provides { capturedHaptics.add(it) }) {
                        Column {
                            UiIcon(
                                atom =
                                    IconAtom(
                                        name = "refresh",
                                        sizeDp = 18,
                                        contentDescription = "Refresh screen",
                                        onClick = refreshAction,
                                    ),
                                onAction = { capturedActions.add(it) },
                            )
                            UiIcon(
                                atom =
                                    IconAtom(
                                        name = "check",
                                        sizeDp = 20,
                                        contentDescription = "Status verified",
                                    ),
                            )
                        }
                    }
                }
            }

            onNodeWithContentDescription("Refresh screen")
                .assertIsDisplayed()
                .assertWidthIsAtLeast(48.dp)
                .assertHeightIsAtLeast(48.dp)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
                .performClick()
            assertEquals(listOf(refreshAction), capturedActions)
            assertEquals(listOf(UiHapticKind.CLICK), capturedHaptics)

            onNodeWithContentDescription("Status verified")
                .assertIsDisplayed()
                .assertWidthIsEqualTo(20.dp)
                .assertHeightIsEqualTo(20.dp)
        }

    @Test
    fun togglingUiSwitchUpdatesStateAndEmitsAction() =
        runComposeUiTest {
            val checkedValues = mutableListOf<Boolean>()
            val capturedActions = mutableListOf<ActionRef>()
            val changeAction = ActionRef(id = "toggle_alerts", type = "save_state")

            setContent {
                DesignTheme(darkTheme = false, reducedMotion = true) {
                    var checked by remember { mutableStateOf(false) }
                    UiSwitch(
                        atom =
                            SwitchAtom(
                                fieldId = "alerts",
                                label = "Notifications",
                                subtitle = "Order alerts",
                                checked = checked,
                                onChange = changeAction,
                            ),
                        checkedOverride = checked,
                        onCheckedChange = {
                            checked = it
                            checkedValues.add(it)
                        },
                        onAction = { capturedActions.add(it) },
                    )
                }
            }

            onNodeWithText("Notifications").assertIsDisplayed()
            onNodeWithText("Order alerts").assertIsDisplayed()
            onNodeWithContentDescription("Notifications, Order alerts")
                .assertIsOff()
                .performClick()
                .assertIsOn()

            assertEquals(listOf(true), checkedValues)
            assertEquals(listOf(changeAction), capturedActions)
        }

    @Test
    fun togglingUiCheckboxShowsRequiredIndicatorAndEmitsAction() =
        runComposeUiTest {
            val checkedValues = mutableListOf<Boolean>()
            val capturedActions = mutableListOf<ActionRef>()
            val changeAction = ActionRef(id = "toggle_tos", type = "save_state")

            setContent {
                DesignTheme(darkTheme = false, reducedMotion = true) {
                    var checked by remember { mutableStateOf(false) }
                    UiCheckbox(
                        atom =
                            CheckboxAtom(
                                fieldId = "tos",
                                label = "Accept Terms",
                                checked = checked,
                                required = true,
                                onChange = changeAction,
                            ),
                        checkedOverride = checked,
                        onCheckedChange = {
                            checked = it
                            checkedValues.add(it)
                        },
                        onAction = { capturedActions.add(it) },
                    )
                }
            }

            onNodeWithText("Accept Terms").assertIsDisplayed()
            onNodeWithText("*").assertIsDisplayed()
            onNodeWithContentDescription("Accept Terms, required")
                .assertIsOff()
                .performClick()
                .assertIsOn()

            assertEquals(listOf(true), checkedValues)
            assertEquals(listOf(changeAction), capturedActions)
        }

    @Test
    fun adjustingUiSliderUpdatesValueAndEmitsAction() =
        runComposeUiTest {
            val sliderUpdates = mutableListOf<Float>()
            val capturedActions = mutableListOf<ActionRef>()
            val changeAction = ActionRef(id = "radius_changed", type = "save_state")

            setContent {
                DesignTheme(darkTheme = false, reducedMotion = true) {
                    var radius by remember { mutableFloatStateOf(20f) }
                    UiSlider(
                        atom =
                            SliderAtom(
                                fieldId = "radius",
                                label = "Search Radius",
                                value = radius,
                                minValue = 0f,
                                maxValue = 100f,
                                unitLabel = "km",
                                onChange = changeAction,
                            ),
                        valueOverride = radius,
                        onValueChange = {
                            radius = it
                            sliderUpdates.add(it)
                        },
                        onAction = { capturedActions.add(it) },
                    )
                }
            }

            onNodeWithText("Search Radius").assertIsDisplayed()
            onNodeWithText("20 km").assertIsDisplayed()
            onNodeWithContentDescription("Search Radius")
                .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                    setProgress(65f)
                }

            onNodeWithText("65 km").assertIsDisplayed()
            assertEquals(listOf(65f), sliderUpdates)
            assertEquals(listOf(changeAction), capturedActions)
        }

    @Test
    fun renderingUiAvatarInitialsAndReducedMotionSkeleton() =
        runComposeUiTest {
            val capturedActions = mutableListOf<ActionRef>()
            val avatarClick = ActionRef(id = "open_profile", type = "navigate")

            setContent {
                DesignTheme(darkTheme = false, reducedMotion = true) {
                    Column {
                        UiAvatar(
                            atom =
                                AvatarAtom(
                                    initials = "al",
                                    contentDescription = "Ada Lovelace",
                                    sizeDp = 48,
                                    onClick = avatarClick,
                                ),
                            onAction = { capturedActions.add(it) },
                        )
                        UiSkeleton(
                            atom = SkeletonAtom(kind = SkeletonKind.TEXT, widthDp = 120, heightDp = 16),
                        )
                    }
                }
            }

            onNodeWithText("AL").assertIsDisplayed()
            onNodeWithContentDescription("Ada Lovelace").performClick()
            assertEquals(listOf(avatarClick), capturedActions)
        }

    @Test
    fun enteringTextInUiTextFieldEmitsValueAndAction() =
        runComposeUiTest {
            val valueUpdates = mutableListOf<String>()
            val capturedActions = mutableListOf<ActionRef>()
            val changeAction = ActionRef(id = "email_changed", type = "save_state")

            setContent {
                DesignTheme(darkTheme = false, reducedMotion = true) {
                    var value by remember { mutableStateOf("") }
                    UiTextField(
                        atom =
                            TextFieldAtom(
                                fieldId = "work_email",
                                label = "Work Email",
                                onValueChange = changeAction,
                            ),
                        valueOverride = value,
                        onValueChange = {
                            value = it
                            valueUpdates.add(it)
                        },
                        onAction = { capturedActions.add(it) },
                    )
                }
            }

            onNodeWithText("Work Email").assertIsDisplayed()
            onNode(hasSetTextAction()).performTextInput("ada@abeta.dev")

            onNodeWithText("ada@abeta.dev").assertIsDisplayed()
            assertEquals(listOf("ada@abeta.dev"), valueUpdates)
            assertEquals(listOf(changeAction), capturedActions)
        }

    @Test
    fun stepperMorphsBetweenAddAndCounterWhileHonoringBoundsAndActions() =
        runComposeUiTest {
            val quantityUpdates = mutableListOf<Int>()
            val capturedActions = mutableListOf<ActionRef>()
            val incrementAction = ActionRef(id = "cart_inc", type = "call_api")
            val decrementAction = ActionRef(id = "cart_dec", type = "call_api")

            setContent {
                DesignTheme(darkTheme = false, reducedMotion = false) {
                    var qty by remember { mutableIntStateOf(0) }
                    UiStepper(
                        atom =
                            StepperAtom(
                                fieldId = "avocado_qty",
                                label = "Quantity",
                                quantity = qty,
                                minQuantity = 0,
                                maxQuantity = 2,
                                onIncrement = incrementAction,
                                onDecrement = decrementAction,
                            ),
                        quantityOverride = qty,
                        onQuantityChange = {
                            qty = it
                            quantityUpdates.add(it)
                        },
                        onAction = { capturedActions.add(it) },
                    )
                }
            }

            onNodeWithText("Quantity").assertIsDisplayed()
            onNodeWithText("ADD").assertIsDisplayed()
            onNodeWithContentDescription("Add item").performClick()

            onNodeWithText("1").assertIsDisplayed()
            onNodeWithText("ADD").assertDoesNotExist()
            onNodeWithContentDescription("Increase quantity").performClick()

            onNodeWithText("2").assertIsDisplayed()
            onNodeWithContentDescription("Increase quantity").performClick()
            onNodeWithText("2").assertIsDisplayed()

            onNodeWithContentDescription("Decrease quantity").performClick()
            onNodeWithText("1").assertIsDisplayed()

            onNodeWithContentDescription("Decrease quantity").performClick()
            onNodeWithText("ADD").assertIsDisplayed()

            assertEquals(listOf(1, 2, 1, 0), quantityUpdates)
            assertEquals(
                listOf(incrementAction, incrementAction, decrementAction, decrementAction),
                capturedActions,
            )
        }

    @Test
    fun celebrationOverlayRendersTitleSubtitleAndDismissesCleanly() =
        runComposeUiTest {
            var dismissedCount = 0

            setContent {
                DesignTheme(darkTheme = false, reducedMotion = false) {
                    var visible by remember { mutableStateOf(false) }
                    Column {
                        UiSurfaceCard(onClick = { visible = true }) {
                            Text("Place Order")
                        }
                        UiCelebrationOverlay(
                            visible = visible,
                            title = "Order Confirmed!",
                            subtitle = "Delivering in 10 mins",
                            onDismiss = {
                                dismissedCount += 1
                                visible = false
                            },
                        )
                    }
                }
            }

            onNodeWithText("Order Confirmed!").assertDoesNotExist()
            onNodeWithText("Place Order").performClick()

            onNodeWithText("Order Confirmed!").assertIsDisplayed()
            onNodeWithText("Delivering in 10 mins").assertIsDisplayed()

            onNodeWithContentDescription("Dismiss celebration").performClick()
            onNodeWithText("Order Confirmed!").assertDoesNotExist()
            assertTrue(dismissedCount == 1)
        }

    @Test
    fun celebrationOverlayCollapsesMotionUnderReducedMotion() =
        runComposeUiTest {
            setContent {
                DesignTheme(darkTheme = true, reducedMotion = true) {
                    UiCelebrationOverlay(
                        visible = true,
                        title = "Saved!",
                        subtitle = "Preferences updated",
                    )
                }
            }

            onNodeWithText("Saved!").assertIsDisplayed()
            onNodeWithText("Preferences updated").assertIsDisplayed()
        }

    @Test
    fun motionAwareTagAndButtonRenderReliablyWithReducedAndFullMotion() =
        runComposeUiTest {
            var clicks = 0
            val pulseTag =
                TagAtom(
                    label = "8 MINS",
                    motion = MotionSpec(loopEffect = MotionLoopEffect.PULSE),
                )
            val springButton =
                ButtonAtom(
                    label = "Order Now",
                    onClick = ActionRef(id = "order_now", type = "track_event"),
                    motion =
                        MotionSpec(
                            enter = MotionTransition.SPRING_BOUNCE,
                            durationToken = MotionDurationToken.FAST,
                        ),
                )

            setContent {
                DesignTheme(darkTheme = false, reducedMotion = false) {
                    Column {
                        UiTag(atom = pulseTag)
                        UiButton(atom = springButton, onAction = { clicks++ })
                    }
                }
            }

            onNodeWithText("8 MINS").assertExists()
            onNodeWithText("Order Now").performClick()
            assertEquals(1, clicks)
        }
}
