package dev.abeta.kmp.tokens

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesignTokensTest {
    @Test
    fun `all theme presets pass WCAG AA text and brand button contrast`() {
        for (preset in ThemePreset.entries) {
            for (darkTheme in listOf(false, true)) {
                val colors = resolveColorTokens(preset = preset, darkTheme = darkTheme)
                assertEquals(darkTheme, colors.isDark)

                assertTrue(
                    ColorAccessibility.meetsWcagAA(colors.text.primary, colors.background.primary),
                    "Primary text must pass WCAG AA on background for $preset dark=$darkTheme",
                )
                assertTrue(
                    ColorAccessibility.meetsWcagAA(colors.text.primary, colors.surface.primary),
                    "Primary text must pass WCAG AA on surface for $preset dark=$darkTheme",
                )
                assertTrue(
                    ColorAccessibility.meetsWcagAA(colors.text.secondary, colors.surface.primary),
                    "Secondary text must pass WCAG AA on surface for $preset dark=$darkTheme",
                )
                assertTrue(
                    ColorAccessibility.meetsWcagAA(colors.button.primaryForeground, colors.button.primaryBackground),
                    "Primary CTA must pass WCAG AA contrast for $preset dark=$darkTheme",
                )
            }
        }
    }

    @Test
    fun `typography spacing motion opacity and sizing scales match contract bounds`() {
        val typography = DefaultTypographyTokens
        assertTrue(typography.displayLarge.fontSize > typography.displayMedium.fontSize)
        assertTrue(typography.displayMedium.fontSize > typography.displaySmall.fontSize)
        assertTrue(typography.headlineLarge.fontSize > typography.bodySmall.fontSize)
        assertTrue(typography.bodyMedium.fontSize > typography.overline.fontSize)

        val spacing = DefaultSpacingTokens
        assertEquals(spacing.space0, spacing.none)
        assertEquals(spacing.space12, spacing.md)
        assertEquals(spacing.space16, spacing.lg)
        assertEquals(spacing.space48, spacing.xxxl)

        assertEquals(0, ReducedMotionTokens.resolveDurationMs(250))
        assertEquals(250, DefaultMotionTokens.resolveDurationMs(250))
        assertTrue(DefaultSizingTokens.minTouchTarget >= DefaultSizingTokens.buttonMd)
        assertTrue(DefaultOpacityTokens.disabled < DefaultOpacityTokens.overlayScrim)
        assertTrue(DefaultElevationTokens.xl > DefaultElevationTokens.none)
    }

    @Test
    fun `adaptive layout metrics classify watch phone tablet and wide desktop breakpoints`() {
        val watch = resolveAdaptiveLayoutMetrics(widthDp = 220, heightDp = 260, isWatch = true)
        assertEquals(DeviceFormFactor.WATCH, watch.formFactor)
        assertEquals(WindowWidthClass.COMPACT, watch.widthClass)
        assertEquals(1, watch.recommendedGridColumns)

        val phone = resolveAdaptiveLayoutMetrics(widthDp = 390, heightDp = 844)
        assertEquals(DeviceFormFactor.PHONE, phone.formFactor)
        assertEquals(WindowWidthClass.COMPACT, phone.widthClass)
        assertEquals(2, phone.recommendedGridColumns)

        val tablet = resolveAdaptiveLayoutMetrics(widthDp = 768, heightDp = 1024)
        assertEquals(DeviceFormFactor.TABLET, tablet.formFactor)
        assertEquals(WindowWidthClass.MEDIUM, tablet.widthClass)
        assertEquals(3, tablet.recommendedGridColumns)

        val narrowNonWatch = resolveAdaptiveLayoutMetrics(widthDp = 240, heightDp = 640, isWatch = false)
        assertEquals(DeviceFormFactor.PHONE, narrowNonWatch.formFactor)
        assertEquals(WindowWidthClass.COMPACT, narrowNonWatch.widthClass)
        assertEquals(2, narrowNonWatch.recommendedGridColumns)

        assertEquals(390, resolveViewportDimensionDp(Dp.Unspecified, fallbackDp = 390))
        assertEquals(844, resolveViewportDimensionDp(Dp.Infinity, fallbackDp = 844))
        assertEquals(320, resolveViewportDimensionDp(320.dp, fallbackDp = 390))

        val desktop = resolveAdaptiveLayoutMetrics(widthDp = 1280, heightDp = 800)
        assertEquals(DeviceFormFactor.DESKTOP_WIDE, desktop.formFactor)
        assertEquals(WindowWidthClass.EXPANDED, desktop.widthClass)
        assertEquals(4, desktop.recommendedGridColumns)
        assertTrue(desktop.useTwoPane)
    }
}
