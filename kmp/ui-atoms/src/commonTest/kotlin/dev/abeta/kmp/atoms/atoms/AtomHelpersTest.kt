package dev.abeta.kmp.atoms.atoms

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.modifiers.isLoopEffectActive
import dev.abeta.kmp.atoms.modifiers.resolveMotionDelayMs
import dev.abeta.kmp.atoms.modifiers.resolveMotionDurationMs
import dev.abeta.kmp.contracts.AlignmentToken
import dev.abeta.kmp.contracts.AvatarAtom
import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.ButtonSize
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.contracts.CheckboxAtom
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.DividerAtom
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.ImageAtom
import dev.abeta.kmp.contracts.ImageScaleMode
import dev.abeta.kmp.contracts.KeyboardKind
import dev.abeta.kmp.contracts.MotionDurationToken
import dev.abeta.kmp.contracts.MotionLoopEffect
import dev.abeta.kmp.contracts.MotionSpec
import dev.abeta.kmp.contracts.MotionTransition
import dev.abeta.kmp.contracts.SkeletonAtom
import dev.abeta.kmp.contracts.SkeletonKind
import dev.abeta.kmp.contracts.SliderAtom
import dev.abeta.kmp.contracts.SpacerAtom
import dev.abeta.kmp.contracts.StepperAtom
import dev.abeta.kmp.contracts.SwitchAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TextFieldAtom
import dev.abeta.kmp.tokens.DefaultCornerRadiusTokens
import dev.abeta.kmp.tokens.DefaultMotionTokens
import dev.abeta.kmp.tokens.DefaultSpacingTokens
import dev.abeta.kmp.tokens.LightColorTokens
import dev.abeta.kmp.tokens.ReducedMotionTokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AtomHelpersTest {
    @Test
    fun buttonStyleResolvesVariantsSizesAndDisabledState() {
        val primaryLarge =
            resolveButtonStyle(
                variant = ButtonVariant.PRIMARY,
                size = ButtonSize.LARGE,
                enabled = true,
                colors = LightColorTokens,
                spacing = DefaultSpacingTokens,
            )
        assertEquals(LightColorTokens.button.primaryBackground, primaryLarge.containerColor)
        assertEquals(LightColorTokens.button.primaryForeground, primaryLarge.contentColor)
        assertEquals(52.dp, primaryLarge.minHeight)

        val disabledPrimary =
            resolveButtonStyle(
                variant = ButtonVariant.PRIMARY,
                size = ButtonSize.SMALL,
                enabled = false,
                colors = LightColorTokens,
                spacing = DefaultSpacingTokens,
            )
        assertEquals(LightColorTokens.button.disabledBackground, disabledPrimary.containerColor)
        assertEquals(LightColorTokens.button.disabledForeground, disabledPrimary.contentColor)
        assertEquals(36.dp, disabledPrimary.minHeight)

        val outlineEnabled =
            resolveButtonStyle(
                variant = ButtonVariant.OUTLINE,
                size = ButtonSize.MEDIUM,
                enabled = true,
                colors = LightColorTokens,
                spacing = DefaultSpacingTokens,
            )
        assertEquals(Color.Transparent, outlineEnabled.containerColor)
        assertEquals(LightColorTokens.button.outlineBorder, outlineEnabled.borderColor)
    }

    @Test
    fun tagColorsRespectVariantAndExplicitOverrides() {
        val successTag =
            resolveTagColors(
                variant = TagVariant.SUCCESS,
                bgColor = null,
                textColor = null,
                colors = LightColorTokens,
            )
        assertEquals(LightColorTokens.tag.successBackground, successTag.background)
        assertEquals(LightColorTokens.tag.successForeground, successTag.foreground)

        val customTag =
            resolveTagColors(
                variant = TagVariant.ERROR,
                bgColor = ColorTokenRef(hex = "#112233"),
                textColor = ColorTokenRef(token = "text.inverse"),
                colors = LightColorTokens,
            )
        assertEquals(Color(0xFF112233), customTag.background)
        assertEquals(LightColorTokens.text.inverse, customTag.foreground)
    }

    @Test
    fun textFieldProgressImageAndTextHelpersBehaveDeterministically() {
        assertEquals("abc", enforceMaxLength("abcdef", 3))
        assertEquals("abcdef", enforceMaxLength("abcdef", null))
        assertEquals("", enforceMaxLength("abcdef", 0))

        assertEquals(KeyboardType.Phone, resolveKeyboardType(KeyboardKind.PHONE))
        assertEquals(KeyboardType.Password, resolveKeyboardType(KeyboardKind.PASSWORD))
        assertEquals(ContentScale.Crop, resolveContentScale(ImageScaleMode.CROP))
        assertEquals(ContentScale.Fit, resolveContentScale(ImageScaleMode.FIT))

        assertEquals(0f, clampProgress(-0.2f))
        assertEquals(1f, clampProgress(1.5f))
        assertEquals(0.45f, clampProgress(0.45f))
        assertNull(clampProgress(null))

        assertEquals(TextAlign.Center, AlignmentToken.CENTER.toTextAlign())
        assertEquals(TextDecoration.Underline, resolveTextDecoration(strikeThrough = false, underline = true))
        assertNotNull(UiBuiltinIcons.resolve("arrow_back"))
        assertNull(UiBuiltinIcons.resolve("unknown_icon_xyz"))
    }

    @Test
    fun alignedAtomContractsExposeExpectedProperties() {
        val icon = IconAtom(name = "sparkles", sizeDp = 24, color = ColorTokenRef(token = "brand.primary"))
        assertEquals(24, icon.sizeDp)
        assertEquals("brand.primary", icon.color?.token)

        val tag = TagAtom(label = "LIVE", icon = icon)
        assertEquals("LIVE", tag.label)
        assertEquals("sparkles", tag.icon?.name)

        val button = ButtonAtom(label = "Submit", subLabel = "Step 1")
        assertEquals("Submit", button.label)
        assertEquals("Step 1", button.subLabel)

        val image = ImageAtom(url = "https://cdn.abeta.dev/a.png", widthDp = 120, heightDp = 80)
        assertEquals(120, image.widthDp)
        assertEquals(80, image.heightDp)

        val field = TextFieldAtom(fieldId = "email", initialValue = "a@b.com", keyboardType = KeyboardKind.EMAIL)
        assertEquals("email", field.fieldId)
        assertEquals("a@b.com", field.initialValue)
        assertEquals(KeyboardKind.EMAIL, field.keyboardType)

        val divider = DividerAtom(thicknessDp = 1f, insetStartDp = 16)
        assertEquals(1f, divider.thicknessDp)
        assertEquals(16, divider.insetStartDp)

        val spacer = SpacerAtom(widthDp = 8, heightDp = 12)
        assertEquals(8, spacer.widthDp)
        assertEquals(12, spacer.heightDp)
    }

    @Test
    fun switchCheckboxSliderAvatarAndSkeletonHelpersBehaveDeterministically() {
        assertEquals(
            "Alerts",
            formatSwitchContentDescription(SwitchAtom(fieldId = "a", label = "Alerts")),
        )
        assertEquals(
            "Alerts, Real-time push",
            formatSwitchContentDescription(
                SwitchAtom(fieldId = "a", label = "Alerts", subtitle = "Real-time push"),
            ),
        )

        assertEquals(
            "Accept Terms, required",
            formatCheckboxContentDescription(
                CheckboxAtom(fieldId = "tos", label = "Accept Terms", required = true),
            ),
        )

        assertEquals("42 km", formatSliderValue(42f, "km"))
        assertEquals("12.5 %", formatSliderValue(12.5f, "%"))
        assertEquals("-0.5", formatSliderValue(-0.5f, null))
        assertEquals(10f..11f, resolveSliderRange(10f, 5f))
        assertEquals(0f..100f, resolveSliderRange(0f, 100f))

        assertEquals("AL", resolveAvatarInitials(" al ", "Ada Lovelace"))
        assertEquals("DK", resolveAvatarInitials(null, "Donald Knuth"))
        assertEquals("AD", resolveAvatarInitials(null, "Ada"))
        assertEquals("?", resolveAvatarInitials(null, null))

        assertEquals(16, resolveSkeletonHeightDp(SkeletonAtom(kind = SkeletonKind.TEXT)))
        assertEquals(80, resolveSkeletonHeightDp(SkeletonAtom(kind = SkeletonKind.RECTANGLE)))
        assertEquals(48, resolveSkeletonHeightDp(SkeletonAtom(kind = SkeletonKind.CIRCLE)))
        assertEquals(64, resolveSkeletonHeightDp(SkeletonAtom(kind = SkeletonKind.CIRCLE, widthDp = 64)))
        assertEquals(32, resolveSkeletonHeightDp(SkeletonAtom(kind = SkeletonKind.RECTANGLE, heightDp = 32)))
        assertNotNull(resolveSkeletonShape(SkeletonKind.CIRCLE, DefaultCornerRadiusTokens))

        val avatar = AvatarAtom(initials = "JD", sizeDp = 44)
        assertEquals("JD", avatar.initials)
        val slider = SliderAtom(fieldId = "vol", value = 50f)
        assertEquals(50f, slider.value)
    }

    @Test
    fun stepperHelpersRespectBoundsClampAndDisabledState() {
        val defaultStepper = StepperAtom(fieldId = "qty", quantity = 0, minQuantity = 0, maxQuantity = 3)
        assertEquals(0, resolveStepperQuantity(defaultStepper))
        assertEquals(2, resolveStepperQuantity(defaultStepper, quantityOverride = 2))
        assertEquals(3, resolveStepperQuantity(defaultStepper, quantityOverride = 99))
        assertEquals(0, resolveStepperQuantity(defaultStepper, quantityOverride = -5))

        assertEquals(1, stepQuantityIncrement(defaultStepper, 0))
        assertEquals(3, stepQuantityIncrement(defaultStepper, 2))
        assertNull(stepQuantityIncrement(defaultStepper, 3))
        assertEquals(0, stepQuantityDecrement(defaultStepper, 1))
        assertNull(stepQuantityDecrement(defaultStepper, 0))

        val minOneStepper = StepperAtom(fieldId = "pack", quantity = 1, minQuantity = 1, maxQuantity = 2)
        assertEquals(1, resolveStepperQuantity(minOneStepper, quantityOverride = 0))
        assertNull(stepQuantityDecrement(minOneStepper, 1))
        assertEquals(2, stepQuantityIncrement(minOneStepper, 1))

        val disabledStepper = defaultStepper.copy(enabled = false)
        assertNull(stepQuantityIncrement(disabledStepper, 0))
        assertNull(stepQuantityDecrement(disabledStepper, 2))
    }

    @Test
    fun motionDurationAndDelayRespectTokensAndReducedMotion() {
        val fastSpec =
            MotionSpec(
                enter = MotionTransition.SCALE_IN,
                durationToken = MotionDurationToken.FAST,
                staggerMs = 60,
            )
        assertEquals(DefaultMotionTokens.fastMs, resolveMotionDurationMs(fastSpec, DefaultMotionTokens))
        assertEquals(0, resolveMotionDurationMs(fastSpec, ReducedMotionTokens))

        val explicitSpec = MotionSpec(durationMs = 320, delayMs = 90, loopEffect = MotionLoopEffect.PULSE)
        assertEquals(320, resolveMotionDurationMs(explicitSpec, DefaultMotionTokens))
        assertEquals(90, resolveMotionDelayMs(explicitSpec, itemIndex = 4, tokens = DefaultMotionTokens))
        assertEquals(180, resolveMotionDelayMs(fastSpec, itemIndex = 3, tokens = DefaultMotionTokens))
        assertEquals(0, resolveMotionDelayMs(fastSpec, itemIndex = 3, tokens = ReducedMotionTokens))
    }

    @Test
    fun countdownFormatAndUrgencyHelpersBehaveDeterministically() {
        assertEquals("02:05", formatCountdownSeconds(seconds = 125))
        assertEquals("00:02:05", formatCountdownSeconds(seconds = 125, showHours = true))
        assertEquals("01:01:05", formatCountdownSeconds(seconds = 3665))
        assertEquals("00:00", formatCountdownSeconds(seconds = 0))
        assertEquals("ENDED", formatCountdownSeconds(seconds = -10, expiredText = "ENDED"))

        assertTrue(isCountdownUrgent(remainingSeconds = 60, urgentThresholdSeconds = 60))
        assertTrue(isCountdownUrgent(remainingSeconds = 1, urgentThresholdSeconds = 60))
        assertFalse(isCountdownUrgent(remainingSeconds = 61, urgentThresholdSeconds = 60))
        assertFalse(isCountdownUrgent(remainingSeconds = 0, urgentThresholdSeconds = 60))
    }

    @Test
    fun rollingCounterAndMotionLoopHelpersBehaveDeterministically() {
        assertTrue(shouldRollDigitUp(previous = '2', current = '3'))
        assertFalse(shouldRollDigitUp(previous = '5', current = '4'))
        assertTrue(shouldRollDigitUp(previous = '9', current = '9'))
        assertTrue(shouldRollDigitUp(previous = ':', current = '1'))

        assertFalse(isLoopEffectActive(MotionLoopEffect.NONE, reducedMotion = false))
        val activeEffects =
            listOf(
                MotionLoopEffect.PULSE,
                MotionLoopEffect.SHIMMER,
                MotionLoopEffect.WIGGLE,
                MotionLoopEffect.FLOAT,
                MotionLoopEffect.HEARTBEAT,
                MotionLoopEffect.GLOW_RING,
                MotionLoopEffect.MARQUEE,
            )
        activeEffects.forEach { effect ->
            assertTrue(isLoopEffectActive(effect, reducedMotion = false), "Expected $effect active")
            assertFalse(isLoopEffectActive(effect, reducedMotion = true), "Expected $effect disabled under reduced")
        }
    }
}
