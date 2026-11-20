package dev.abeta.kmp.atoms.tokens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.CornerRadiusSpec
import dev.abeta.kmp.contracts.TypographyTokenRef
import dev.abeta.kmp.tokens.ColorTokens
import dev.abeta.kmp.tokens.CornerRadiusTokens
import dev.abeta.kmp.tokens.DesignPalette
import dev.abeta.kmp.tokens.StrokeWidthTokens
import dev.abeta.kmp.tokens.ThemeTokens
import dev.abeta.kmp.tokens.TypographyTokens

private const val HEX_RADIX = 16
private const val HEX_SHORT_RGB_LEN = 3
private const val HEX_SHORT_ARGB_LEN = 4
private const val HEX_RRGGBB_LEN = 6
private const val HEX_AARRGGBB_LEN = 8
private const val FULL_ALPHA_MASK = 0xFF000000L
private const val FULL_BYTE_ALPHA = 0xFF
private const val NIBBLE_DUPLICATION_FACTOR = 0x11

private val ColorTokenRegistry: Map<String, (ColorTokens) -> Color> =
    mapOf(
        // Background (Go BFF `bg.*` + legacy `background.*`)
        "bg.primary" to { it.background.primary },
        "bg.secondary" to { it.background.secondary },
        "bg.tertiary" to { it.background.tertiary },
        "bg.inverse" to { it.background.inverse },
        "background.primary" to { it.background.primary },
        "background.secondary" to { it.background.secondary },
        "background.tertiary" to { it.background.tertiary },
        "background.inverse" to { it.background.inverse },
        // Surface
        "surface.primary" to { it.surface.primary },
        "surface.secondary" to { it.surface.secondary },
        "surface.elevated" to { it.surface.elevated },
        "surface.inverse" to { it.surface.inverse },
        "surface.brand" to { it.surface.brand },
        "surface.brand_subtle" to { it.surface.brandSubtle },
        // Brand & Accent (Go BFF)
        "brand.primary" to { it.brand.primary },
        "brand.secondary" to { it.brand.secondary },
        "brand.container" to { it.brand.container },
        "accent.primary" to { it.brand.accent },
        // Border & Divider
        "border.subtle" to { it.border.subtle },
        "border.default" to { it.border.default },
        "border.strong" to { it.border.strong },
        "border.focus" to { it.border.focus },
        "border.error" to { it.border.error },
        "divider.default" to { it.border.divider },
        // Text
        "text.primary" to { it.text.primary },
        "text.secondary" to { it.text.secondary },
        "text.tertiary" to { it.text.tertiary },
        "text.disabled" to { it.text.disabled },
        "text.inverse" to { it.text.inverse },
        "text.brand" to { it.text.brand },
        "text.on_brand" to { it.text.onBrand },
        "text.success" to { it.text.success },
        "text.warning" to { it.text.warning },
        "text.error" to { it.text.error },
        "text.info" to { it.text.info },
        // Icon
        "icon.primary" to { it.icon.primary },
        "icon.secondary" to { it.icon.secondary },
        "icon.tertiary" to { it.icon.tertiary },
        "icon.disabled" to { it.icon.disabled },
        "icon.inverse" to { it.icon.inverse },
        "icon.brand" to { it.icon.brand },
        "icon.on_brand" to { it.icon.onBrand },
        "icon.success" to { it.icon.success },
        "icon.warning" to { it.icon.warning },
        "icon.error" to { it.icon.error },
        "icon.info" to { it.icon.info },
        // Button
        "button.primary_bg" to { it.button.primaryBackground },
        "button.primary_fg" to { it.button.primaryForeground },
        "button.secondary_bg" to { it.button.secondaryBackground },
        "button.secondary_fg" to { it.button.secondaryForeground },
        "button.outline_border" to { it.button.outlineBorder },
        "button.ghost_fg" to { it.button.ghostForeground },
        "button.destructive_bg" to { it.button.destructiveBackground },
        "button.destructive_fg" to { it.button.destructiveForeground },
        "button.disabled_bg" to { it.button.disabledBackground },
        "button.disabled_fg" to { it.button.disabledForeground },
        // Tag
        "tag.neutral_bg" to { it.tag.neutralBackground },
        "tag.neutral_fg" to { it.tag.neutralForeground },
        "tag.brand_bg" to { it.tag.brandBackground },
        "tag.brand_fg" to { it.tag.brandForeground },
        "tag.success_bg" to { it.tag.successBackground },
        "tag.success_fg" to { it.tag.successForeground },
        "tag.warning_bg" to { it.tag.warningBackground },
        "tag.warning_fg" to { it.tag.warningForeground },
        "tag.error_bg" to { it.tag.errorBackground },
        "tag.error_fg" to { it.tag.errorForeground },
        "tag.info_bg" to { it.tag.infoBackground },
        "tag.info_fg" to { it.tag.infoForeground },
        // Status (Go BFF `*_container` + legacy `*_subtle`)
        "status.success" to { it.status.success },
        "status.success_container" to { it.status.successSubtle },
        "status.success_subtle" to { it.status.successSubtle },
        "status.warning" to { it.status.warning },
        "status.warning_container" to { it.status.warningSubtle },
        "status.warning_subtle" to { it.status.warningSubtle },
        "status.error" to { it.status.error },
        "status.error_container" to { it.status.errorSubtle },
        "status.error_subtle" to { it.status.errorSubtle },
        "status.info" to { it.status.info },
        "status.info_container" to { it.status.infoSubtle },
        "status.info_subtle" to { it.status.infoSubtle },
        // Utility
        "transparent" to { DesignPalette.Transparent },
    )

private val CornerRadiusTokenRegistry: Map<String, (CornerRadiusTokens) -> Dp> =
    mapOf(
        "none" to { it.none },
        "xs" to { it.xs },
        "sm" to { it.sm },
        "md" to { it.md },
        "lg" to { it.lg },
        "xl" to { it.xl },
        "full" to { it.full },
        "radius.none" to { it.none },
        "radius.xs" to { it.xs },
        "radius.sm" to { it.sm },
        "radius.md" to { it.md },
        "radius.lg" to { it.lg },
        "radius.xl" to { it.xl },
        "radius.full" to { it.full },
    )

private val StrokeWidthTokenRegistry: Map<String, (StrokeWidthTokens) -> Dp> =
    mapOf(
        "hairline" to { it.hairline },
        "thin" to { it.thin },
        "medium" to { it.medium },
        "thick" to { it.thick },
    )

private val TypographyTokenRegistry: Map<String, (TypographyTokens) -> TextStyle> =
    mapOf(
        "display.large" to { it.displayLarge },
        "display.medium" to { it.displayMedium },
        "display.small" to { it.displaySmall },
        "headline.large" to { it.headlineLarge },
        "headline.medium" to { it.headlineMedium },
        "headline.small" to { it.headlineSmall },
        "title.large" to { it.titleLarge },
        "title.medium" to { it.titleMedium },
        "title.small" to { it.titleSmall },
        "body.large" to { it.bodyLarge },
        "body.medium" to { it.bodyMedium },
        "body.small" to { it.bodySmall },
        "label.large" to { it.labelLarge },
        "label.medium" to { it.labelMedium },
        "label.small" to { it.labelSmall },
        "caption" to { it.caption },
        "overline" to { it.overline },
        // Legacy underscore aliases
        "display_large" to { it.displayLarge },
        "display_medium" to { it.displayMedium },
        "display_small" to { it.displaySmall },
        "headline_large" to { it.headlineLarge },
        "headline_medium" to { it.headlineMedium },
        "headline_small" to { it.headlineSmall },
        "title_large" to { it.titleLarge },
        "title_medium" to { it.titleMedium },
        "title_small" to { it.titleSmall },
        "body_large" to { it.bodyLarge },
        "body_medium" to { it.bodyMedium },
        "body_small" to { it.bodySmall },
        "label_large" to { it.labelLarge },
        "label_medium" to { it.labelMedium },
        "label_small" to { it.labelSmall },
    )

private val FontWeightRegistry: Map<String, FontWeight> =
    mapOf(
        "thin" to FontWeight.Thin,
        "extra_light" to FontWeight.ExtraLight,
        "extralight" to FontWeight.ExtraLight,
        "light" to FontWeight.Light,
        "regular" to FontWeight.Normal,
        "normal" to FontWeight.Normal,
        "medium" to FontWeight.Medium,
        "semi_bold" to FontWeight.SemiBold,
        "semibold" to FontWeight.SemiBold,
        "bold" to FontWeight.Bold,
        "extra_bold" to FontWeight.ExtraBold,
        "extrabold" to FontWeight.ExtraBold,
        "black" to FontWeight.Black,
    )

/**
 * Resolved corner radius dimensions in [Dp].
 */
data class ResolvedCornerRadii(
    val topStart: Dp,
    val topEnd: Dp,
    val bottomStart: Dp,
    val bottomEnd: Dp,
) {
    /** Converts these radii into a Compose [RoundedCornerShape]. */
    fun toRoundedCornerShape(): RoundedCornerShape =
        RoundedCornerShape(
            topStart = topStart,
            topEnd = topEnd,
            bottomEnd = bottomEnd,
            bottomStart = bottomStart,
        )
}

/**
 * Parses a hex color string (`#RGB`, `#ARGB`, `#RRGGBB`, `#AARRGGBB`, with or without `#`)
 * into a Compose [Color], or returns `null` if the format is invalid.
 */
fun parseHexColorOrNull(hex: String): Color? {
    val normalized = hex.trim().removePrefix("#")
    return when (normalized.length) {
        HEX_SHORT_RGB_LEN -> {
            parseShortHex(alphaChar = null, rgbChars = normalized)
        }

        HEX_SHORT_ARGB_LEN -> {
            parseShortHex(alphaChar = normalized[0], rgbChars = normalized.substring(1))
        }

        HEX_RRGGBB_LEN -> {
            normalized.toLongOrNull(HEX_RADIX)?.let { rgb -> Color(FULL_ALPHA_MASK or rgb) }
        }

        HEX_AARRGGBB_LEN -> {
            normalized.toLongOrNull(HEX_RADIX)?.let(::Color)
        }

        else -> {
            null
        }
    }
}

private fun parseShortHex(
    alphaChar: Char?,
    rgbChars: String,
): Color? {
    val rgbNibbles = rgbChars.mapNotNull { it.digitToIntOrNull(HEX_RADIX) }
    val alphaByte =
        if (alphaChar == null) {
            FULL_BYTE_ALPHA
        } else {
            alphaChar.digitToIntOrNull(HEX_RADIX)?.times(NIBBLE_DUPLICATION_FACTOR)
        }
    return if (rgbNibbles.size == HEX_SHORT_RGB_LEN && alphaByte != null) {
        Color(
            red = rgbNibbles[0] * NIBBLE_DUPLICATION_FACTOR,
            green = rgbNibbles[1] * NIBBLE_DUPLICATION_FACTOR,
            blue = rgbNibbles[2] * NIBBLE_DUPLICATION_FACTOR,
            alpha = alphaByte,
        )
    } else {
        null
    }
}

/**
 * Looks up a semantic color token path (e.g. `"text.primary"`) inside [colors].
 */
fun resolveColorToken(
    token: String,
    colors: ColorTokens,
): Color? = ColorTokenRegistry[token.trim().lowercase()]?.invoke(colors)

/**
 * Pure resolver for [ColorTokenRef]: prefers semantic token lookup, falls back to [ColorTokenRef.fallbackHex]
 * and [ColorTokenRef.hex], applies optional [ColorTokenRef.alpha] override, and returns [fallback] if unresolved.
 */
fun resolveColor(
    ref: ColorTokenRef?,
    colors: ColorTokens,
    fallback: Color,
): Color {
    val base =
        ref?.let {
            it.token?.let { token -> resolveColorToken(token, colors) }
                ?: it.fallbackHex?.let(::parseHexColorOrNull)
                ?: it.hex?.let(::parseHexColorOrNull)
        }
    val alphaOverride = ref?.alpha
    return when {
        base == null -> fallback
        alphaOverride != null -> base.copy(alpha = alphaOverride.coerceIn(0f, 1f))
        else -> base
    }
}

/**
 * Composable convenience resolver for [ColorTokenRef] against [ThemeTokens.colors].
 */
@Composable
@ReadOnlyComposable
fun ColorTokenRef?.resolve(fallback: Color = Color.Unspecified): Color =
    resolveColor(ref = this, colors = ThemeTokens.colors, fallback = fallback)

/**
 * Pure resolver for [TypographyTokenRef] against [typography], applying optional weight and letter spacing overrides.
 */
fun resolveTypography(
    ref: TypographyTokenRef?,
    typography: TypographyTokens,
    fallback: TextStyle = typography.bodyMedium,
): TextStyle {
    if (ref == null) return fallback
    val base = TypographyTokenRegistry[ref.token.trim().lowercase()]?.invoke(typography) ?: fallback
    val resolvedWeight =
        ref.fontWeight
            ?.trim()
            ?.lowercase()
            ?.let { FontWeightRegistry[it] }
    val resolvedLetterSpacing = ref.letterSpacing?.sp
    return if (resolvedWeight != null || resolvedLetterSpacing != null) {
        base.copy(
            fontWeight = resolvedWeight ?: base.fontWeight,
            letterSpacing = resolvedLetterSpacing ?: base.letterSpacing,
        )
    } else {
        base
    }
}

/**
 * Composable convenience resolver for [TypographyTokenRef] against [ThemeTokens.typography].
 */
@Composable
@ReadOnlyComposable
fun TypographyTokenRef?.resolve(fallback: TextStyle = ThemeTokens.typography.bodyMedium): TextStyle =
    resolveTypography(ref = this, typography = ThemeTokens.typography, fallback = fallback)

/**
 * Pure resolver for [CornerRadiusSpec] into [ResolvedCornerRadii].
 */
fun resolveCornerRadii(
    spec: CornerRadiusSpec?,
    tokens: CornerRadiusTokens,
): ResolvedCornerRadii? {
    if (spec == null) return null
    val tokenRadius =
        spec.token
            ?.trim()
            ?.lowercase()
            ?.let { CornerRadiusTokenRegistry[it]?.invoke(tokens) }
    val base = spec.all?.coerceAtLeast(0)?.dp ?: tokenRadius ?: tokens.none
    return ResolvedCornerRadii(
        topStart = spec.topStart?.coerceAtLeast(0)?.dp ?: base,
        topEnd = spec.topEnd?.coerceAtLeast(0)?.dp ?: base,
        bottomStart = spec.bottomStart?.coerceAtLeast(0)?.dp ?: base,
        bottomEnd = spec.bottomEnd?.coerceAtLeast(0)?.dp ?: base,
    )
}

/**
 * Pure resolver for stroke width values and semantic stroke tokens.
 */
fun resolveStrokeWidthDp(
    width: Float?,
    widthToken: String?,
    tokens: StrokeWidthTokens,
    fallback: Dp = tokens.thin,
): Dp {
    if (width != null && width > 0f) return width.dp
    val tokenStroke = widthToken?.trim()?.lowercase()?.let { StrokeWidthTokenRegistry[it]?.invoke(tokens) }
    return tokenStroke ?: fallback
}
