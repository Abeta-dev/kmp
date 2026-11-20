package dev.abeta.kmp.atoms.tokens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.abeta.kmp.atoms.atoms.resolveKeyboardType
import dev.abeta.kmp.atoms.modifiers.toPaddingValues
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.CornerRadiusSpec
import dev.abeta.kmp.contracts.KeyboardKind
import dev.abeta.kmp.contracts.SpacingValues
import dev.abeta.kmp.contracts.TypographyTokenRef
import dev.abeta.kmp.tokens.DarkColorTokens
import dev.abeta.kmp.tokens.DefaultCornerRadiusTokens
import dev.abeta.kmp.tokens.DefaultStrokeWidthTokens
import dev.abeta.kmp.tokens.DefaultTypographyTokens
import dev.abeta.kmp.tokens.LightColorTokens
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TokenResolversTest {
    @Test
    fun parseHexColorOrNullSupportsAllValidFormats() {
        assertEquals(Color(0xFF4F46E5), parseHexColorOrNull("#4F46E5"))
        assertEquals(Color(0xFF4F46E5), parseHexColorOrNull("  4f46e5  "))
        assertEquals(Color(0x804F46E5), parseHexColorOrNull("#804F46E5"))
        assertEquals(Color(0xFFFF00AA), parseHexColorOrNull("#F0A"))
        assertEquals(Color(0x88FF00AA), parseHexColorOrNull("#8F0A"))
    }

    @Test
    fun parseHexColorOrNullRejectsInvalidFormats() {
        assertNull(parseHexColorOrNull(""))
        assertNull(parseHexColorOrNull("#"))
        assertNull(parseHexColorOrNull("#12"))
        assertNull(parseHexColorOrNull("#12345"))
        assertNull(parseHexColorOrNull("#1234567"))
        assertNull(parseHexColorOrNull("#GGGGGG"))
    }

    @Test
    fun resolveColorSupportsGoBffTokensFallbackHexAndAlpha() {
        val tokenAndHex = ColorTokenRef(token = "text.primary", hex = "#FF0000")
        assertEquals(
            LightColorTokens.text.primary,
            resolveColor(tokenAndHex, LightColorTokens, Color.Gray),
        )
        assertEquals(
            DarkColorTokens.text.primary,
            resolveColor(tokenAndHex, DarkColorTokens, Color.Gray),
        )

        val unknownTokenWithFallbackHex =
            ColorTokenRef(
                token = "nonexistent.role",
                fallbackHex = "#10B981",
                hex = "#FF0000",
            )
        assertEquals(
            Color(0xFF10B981),
            resolveColor(unknownTokenWithFallbackHex, LightColorTokens, Color.Gray),
        )

        val unknownTokenWithHex = ColorTokenRef(token = "nonexistent.role", hex = "#10B981")
        assertEquals(
            Color(0xFF10B981),
            resolveColor(unknownTokenWithHex, LightColorTokens, Color.Gray),
        )

        val alphaOverride = ColorTokenRef(token = "brand.primary", alpha = 0.5f)
        assertEquals(
            LightColorTokens.surface.brand.copy(alpha = 0.5f),
            resolveColor(alphaOverride, LightColorTokens, Color.Gray),
        )

        val bffTokens =
            listOf(
                "bg.primary" to LightColorTokens.background.primary,
                "bg.secondary" to LightColorTokens.background.secondary,
                "bg.tertiary" to LightColorTokens.background.tertiary,
                "surface.inverse" to LightColorTokens.surface.inverse,
                "brand.primary" to LightColorTokens.brand.primary,
                "brand.secondary" to LightColorTokens.brand.secondary,
                "brand.container" to LightColorTokens.brand.container,
                "accent.primary" to LightColorTokens.brand.accent,
                "status.success_container" to LightColorTokens.status.successSubtle,
                "status.warning_container" to LightColorTokens.status.warningSubtle,
                "status.error_container" to LightColorTokens.status.errorSubtle,
                "status.info_container" to LightColorTokens.status.infoSubtle,
                "divider.default" to LightColorTokens.border.divider,
            )
        bffTokens.forEach { (token, expected) ->
            assertEquals(expected, resolveColorToken(token, LightColorTokens))
        }

        val invalidRef = ColorTokenRef(token = "missing", hex = "not-a-hex")
        assertEquals(Color.Gray, resolveColor(invalidRef, LightColorTokens, Color.Gray))
        assertEquals(Color.Gray, resolveColor(null, LightColorTokens, Color.Gray))
    }

    @Test
    fun resolveTypographyMapsAllGoBffScaleTokensAndOverrides() {
        assertEquals(
            DefaultTypographyTokens.displayLarge,
            resolveTypography(TypographyTokenRef("display.large"), DefaultTypographyTokens),
        )
        assertEquals(
            DefaultTypographyTokens.titleLarge,
            resolveTypography(TypographyTokenRef("title.large"), DefaultTypographyTokens),
        )
        assertEquals(
            DefaultTypographyTokens.bodyMedium,
            resolveTypography(TypographyTokenRef("body.medium"), DefaultTypographyTokens),
        )
        assertEquals(
            DefaultTypographyTokens.labelSmall,
            resolveTypography(TypographyTokenRef("label.small"), DefaultTypographyTokens),
        )
        assertEquals(
            DefaultTypographyTokens.bodyMedium,
            resolveTypography(null, DefaultTypographyTokens),
        )

        val allBffTokens =
            listOf(
                "display.large",
                "display.medium",
                "display.small",
                "headline.large",
                "headline.medium",
                "headline.small",
                "title.large",
                "title.medium",
                "title.small",
                "body.large",
                "body.medium",
                "body.small",
                "label.large",
                "label.medium",
                "label.small",
                "caption",
                "overline",
            )
        allBffTokens.forEach { token ->
            val resolved = resolveTypography(TypographyTokenRef(token = token), DefaultTypographyTokens)
            assertNotNull(resolved)
        }

        val overridden =
            resolveTypography(
                TypographyTokenRef(token = "body.medium", fontWeight = "bold", letterSpacing = 0.75),
                DefaultTypographyTokens,
            )
        assertEquals(FontWeight.Bold, overridden.fontWeight)
        assertEquals(0.75.sp, overridden.letterSpacing)
        assertEquals(DefaultTypographyTokens.bodyMedium.fontSize, overridden.fontSize)
    }

    @Test
    fun resolveCornerRadiiAndStrokeWidthTokens() {
        val tokenSpec = CornerRadiusSpec(token = "radius.lg")
        val resolvedToken = resolveCornerRadii(tokenSpec, DefaultCornerRadiusTokens)
        assertNotNull(resolvedToken)
        assertEquals(DefaultCornerRadiusTokens.lg, resolvedToken.topStart)
        assertEquals(DefaultCornerRadiusTokens.lg, resolvedToken.bottomEnd)

        val partialOverride = CornerRadiusSpec(token = "sm", topStart = 20, bottomEnd = 0)
        val resolvedPartial = resolveCornerRadii(partialOverride, DefaultCornerRadiusTokens)
        assertNotNull(resolvedPartial)
        assertEquals(20.dp, resolvedPartial.topStart)
        assertEquals(DefaultCornerRadiusTokens.sm, resolvedPartial.topEnd)
        assertEquals(DefaultCornerRadiusTokens.sm, resolvedPartial.bottomStart)
        assertEquals(0.dp, resolvedPartial.bottomEnd)

        assertEquals(
            DefaultStrokeWidthTokens.medium,
            resolveStrokeWidthDp(null, "medium", DefaultStrokeWidthTokens),
        )
        assertEquals(
            3.5f.dp,
            resolveStrokeWidthDp(3.5f, "medium", DefaultStrokeWidthTokens),
        )
    }

    @Test
    fun spacingValuesResolvesAxisAndSidePrecedence() {
        val spacing = SpacingValues(all = 8, horizontal = 16, top = 24)
        val padding = spacing.toPaddingValues()
        assertEquals(16.dp, padding.calculateLeftPadding(LayoutDirection.Ltr))
        assertEquals(16.dp, padding.calculateRightPadding(LayoutDirection.Ltr))
        assertEquals(24.dp, padding.calculateTopPadding())
        assertEquals(8.dp, padding.calculateBottomPadding())
        assertEquals(
            KeyboardType.Email,
            resolveKeyboardType(KeyboardKind.EMAIL),
        )
    }
}
