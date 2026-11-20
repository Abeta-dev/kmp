package dev.abeta.kmp.storybook

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.atoms.UiDivider
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.atoms.atoms.UiText
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.DividerAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.contracts.TypographyTokenRef
import dev.abeta.kmp.tokens.ColorAccessibility
import dev.abeta.kmp.tokens.ThemePreset
import dev.abeta.kmp.tokens.ThemeTokens
import kotlin.math.roundToInt

private const val SWATCH_MIN_WIDTH_DP = 170
private const val COLOR_BAR_HEIGHT_DP = 44
private const val SPACING_BAR_HEIGHT_DP = 14
private const val RADIUS_BOX_SIZE_DP = 52
private const val ELEVATION_BOX_SIZE_DP = 64
private const val CHART_BAR_HEIGHT_DP = 28
private const val CONTRAST_SCALE_FACTOR = 10.0

/**
 * Interactive design-token specimen story displaying active theme swatches, WCAG contrast badges,
 * all 17 typography steps, spacing, corner radii, elevation, sizing, and chart palette.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun DesignTokensStory(
    preset: ThemePreset,
    darkTheme: Boolean,
    reducedMotion: Boolean,
) {
    val spacing = ThemeTokens.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.space16)) {
        TokenHeaderCard(preset = preset, darkTheme = darkTheme, reducedMotion = reducedMotion)
        SemanticColorsSection()
        ChartPaletteSection()
        TypographyScaleSection()
        SpacingAndRadiusSection()
        ElevationAndSizingSection()
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun TokenHeaderCard(
    preset: ThemePreset,
    darkTheme: Boolean,
    reducedMotion: Boolean,
) {
    val spacing = ThemeTokens.spacing
    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        UiText(
            TextAtom(
                text = "Active Theme: ${preset.displayName} (${if (darkTheme) "Dark" else "Light"})",
                typography = TypographyTokenRef("title.large"),
            ),
        )
        Row(
            modifier = Modifier.padding(top = spacing.space8),
            horizontalArrangement = Arrangement.spacedBy(spacing.space8),
        ) {
            UiTag(TagAtom(label = preset.name, variant = TagVariant.BRAND))
            UiTag(TagAtom(label = if (darkTheme) "Dark Surface" else "Light Surface", variant = TagVariant.NEUTRAL))
            val motionTag =
                if (reducedMotion) {
                    "Reduced Motion (0ms)"
                } else {
                    "Full Motion (${ThemeTokens.motion.normalMs}ms)"
                }
            UiTag(
                TagAtom(
                    label = motionTag,
                    variant = if (reducedMotion) TagVariant.WARNING else TagVariant.SUCCESS,
                ),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun SemanticColorsSection() {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val pairs =
        listOf(
            Triple("background.primary", colors.background.primary, colors.text.primary),
            Triple("surface.primary", colors.surface.primary, colors.text.primary),
            Triple("surface.elevated", colors.surface.elevated, colors.text.primary),
            Triple("brand.primary", colors.brand.primary, colors.brand.onPrimary),
            Triple("brand.container", colors.brand.container, colors.brand.onContainer),
            Triple("brand.secondary", colors.brand.secondary, colors.brand.onSecondary),
            Triple("status.success", colors.status.successSubtle, colors.text.success),
            Triple("status.warning", colors.status.warningSubtle, colors.text.warning),
            Triple("status.error", colors.status.errorSubtle, colors.text.error),
            Triple("status.info", colors.status.infoSubtle, colors.text.info),
        )

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        UiText(
            TextAtom(
                text = "Semantic Colors & WCAG AA Contrast",
                typography = TypographyTokenRef("title.large"),
            ),
        )
        UiDivider(DividerAtom(), modifier = Modifier.padding(vertical = spacing.space8))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.space12),
            verticalArrangement = Arrangement.spacedBy(spacing.space12),
        ) {
            pairs.forEach { (token, bg, fg) ->
                ColorContrastTile(token = token, background = bg, foreground = fg)
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun ColorContrastTile(
    token: String,
    background: Color,
    foreground: Color,
) {
    val tokens = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val shape = RoundedCornerShape(ThemeTokens.cornerRadius.sm)
    val ratio = ColorAccessibility.contrastRatio(foreground, background)
    val roundedRatio = (ratio * CONTRAST_SCALE_FACTOR).roundToInt() / CONTRAST_SCALE_FACTOR
    val passesAa = ColorAccessibility.meetsWcagAA(foreground, background)

    Column(
        modifier =
            Modifier
                .width(SWATCH_MIN_WIDTH_DP.dp)
                .clip(shape)
                .border(ThemeTokens.strokeWidth.thin, tokens.border.subtle, shape)
                .padding(spacing.space8),
        verticalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(COLOR_BAR_HEIGHT_DP.dp)
                    .clip(RoundedCornerShape(ThemeTokens.cornerRadius.xs))
                    .background(background)
                    .border(ThemeTokens.strokeWidth.hairline, tokens.border.default, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "Aa", style = ThemeTokens.typography.labelLarge, color = foreground)
        }
        Text(text = token, style = ThemeTokens.typography.labelSmall, color = tokens.text.primary)
        Text(
            text = "${if (passesAa) "AA Pass" else "Large"} · $roundedRatio:1",
            style = ThemeTokens.typography.caption,
            color = if (passesAa) tokens.text.success else tokens.text.warning,
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun ChartPaletteSection() {
    val chart = ThemeTokens.colors.chart
    val spacing = ThemeTokens.spacing
    val series =
        listOf(
            "series1 (Cobalt)" to chart.series1,
            "series2 (Amber)" to chart.series2,
            "series3 (Teal)" to chart.series3,
            "series4 (Vermillion)" to chart.series4,
            "series5 (Sky)" to chart.series5,
        )

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        UiText(
            TextAtom(
                text = "Data Visualization (Okabe-Ito Palette)",
                typography = TypographyTokenRef("title.large"),
            ),
        )
        UiDivider(DividerAtom(), modifier = Modifier.padding(vertical = spacing.space8))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.space8),
        ) {
            series.forEach { (label, color) ->
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.space4)) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(CHART_BAR_HEIGHT_DP.dp)
                                .clip(RoundedCornerShape(ThemeTokens.cornerRadius.xs))
                                .background(color),
                    )
                    Text(
                        text = label,
                        style = ThemeTokens.typography.caption,
                        color = ThemeTokens.colors.text.secondary,
                    )
                }
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun TypographyScaleSection() {
    val typo = ThemeTokens.typography
    val spacing = ThemeTokens.spacing
    val entries: List<Pair<String, TextStyle>> =
        listOf(
            "display.large (36sp)" to typo.displayLarge,
            "display.medium (32sp)" to typo.displayMedium,
            "display.small (28sp)" to typo.displaySmall,
            "headline.large (24sp)" to typo.headlineLarge,
            "headline.medium (22sp)" to typo.headlineMedium,
            "headline.small (20sp)" to typo.headlineSmall,
            "title.large (18sp)" to typo.titleLarge,
            "title.medium (16sp)" to typo.titleMedium,
            "title.small (14sp)" to typo.titleSmall,
            "body.large (16sp)" to typo.bodyLarge,
            "body.medium (14sp)" to typo.bodyMedium,
            "body.small (12sp)" to typo.bodySmall,
            "label.large (14sp)" to typo.labelLarge,
            "label.medium (12sp)" to typo.labelMedium,
            "label.small (11sp)" to typo.labelSmall,
            "caption (12sp)" to typo.caption,
            "overline (10sp)" to typo.overline,
        )

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        UiText(
            TextAtom(
                text = "Typography Scale (17 BFF Tokens)",
                typography = TypographyTokenRef("title.large"),
            ),
        )
        UiDivider(DividerAtom(), modifier = Modifier.padding(vertical = spacing.space8))
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space8)) {
            entries.forEach { (name, style) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = name,
                        style = typo.labelSmall,
                        color = ThemeTokens.colors.text.tertiary,
                        modifier = Modifier.width(SWATCH_MIN_WIDTH_DP.dp),
                    )
                    Text(
                        text = "The quick brown fox jumps over the lazy dog",
                        style = style,
                        color = ThemeTokens.colors.text.primary,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun SpacingAndRadiusSection() {
    val spacing = ThemeTokens.spacing
    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        UiText(TextAtom(text = "Spacing & Corner Radius Scales", typography = TypographyTokenRef("title.large")))
        UiDivider(DividerAtom(), modifier = Modifier.padding(vertical = spacing.space8))
        SpacingBarsList()
        UiDivider(DividerAtom(), modifier = Modifier.padding(vertical = spacing.space12))
        RadiusSwatchGrid()
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun SpacingBarsList() {
    val spacing = ThemeTokens.spacing
    val colors = ThemeTokens.colors
    val spacingItems: List<Pair<String, Dp>> =
        listOf(
            "xxs (2dp)" to spacing.xxs,
            "xs (4dp)" to spacing.xs,
            "sm (8dp)" to spacing.sm,
            "md (12dp)" to spacing.md,
            "lg (16dp)" to spacing.lg,
            "xl (24dp)" to spacing.xl,
            "xxl (32dp)" to spacing.xxl,
            "xxxl (48dp)" to spacing.xxxl,
        )
    Column(verticalArrangement = Arrangement.spacedBy(spacing.space4)) {
        spacingItems.forEach { (label, dp) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.space8),
            ) {
                Text(
                    text = label,
                    style = ThemeTokens.typography.caption,
                    color = colors.text.secondary,
                    modifier = Modifier.width(SWATCH_MIN_WIDTH_DP.dp),
                )
                Box(
                    modifier =
                        Modifier
                            .width(dp.coerceAtLeast(2.dp))
                            .height(SPACING_BAR_HEIGHT_DP.dp)
                            .clip(RoundedCornerShape(ThemeTokens.cornerRadius.xs))
                            .background(colors.brand.primary),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun RadiusSwatchGrid() {
    val spacing = ThemeTokens.spacing
    val radii = ThemeTokens.cornerRadius
    val colors = ThemeTokens.colors
    val radiusItems: List<Pair<String, Dp>> =
        listOf(
            "none" to radii.none,
            "xs" to radii.xs,
            "sm" to radii.sm,
            "md" to radii.md,
            "lg" to radii.lg,
            "xl" to radii.xl,
            "full" to radii.full,
        )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(spacing.space12),
        verticalArrangement = Arrangement.spacedBy(spacing.space12),
    ) {
        radiusItems.forEach { (label, radius) ->
            val shape = RoundedCornerShape(radius)
            Box(
                modifier =
                    Modifier
                        .size(RADIUS_BOX_SIZE_DP.dp)
                        .clip(shape)
                        .background(colors.brand.container)
                        .border(ThemeTokens.strokeWidth.medium, colors.brand.primary, shape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = ThemeTokens.typography.labelSmall,
                    color = colors.brand.onContainer,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun ElevationAndSizingSection() {
    val elevation = ThemeTokens.elevation
    val sizing = ThemeTokens.sizing
    val opacity = ThemeTokens.opacity
    val spacing = ThemeTokens.spacing
    val colors = ThemeTokens.colors

    val elevations =
        listOf(
            "none (0dp)" to elevation.none,
            "xs (1dp)" to elevation.xs,
            "sm (2dp)" to elevation.sm,
            "md (4dp)" to elevation.md,
            "lg (8dp)" to elevation.lg,
            "xl (16dp)" to elevation.xl,
        )

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        UiText(TextAtom(text = "Elevation, Sizing & Opacity Tokens", typography = TypographyTokenRef("title.large")))
        UiDivider(DividerAtom(), modifier = Modifier.padding(vertical = spacing.space8))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.space12),
            verticalArrangement = Arrangement.spacedBy(spacing.space12),
        ) {
            elevations.forEach { (label, dp) ->
                Column(
                    modifier =
                        Modifier
                            .size(ELEVATION_BOX_SIZE_DP.dp)
                            .clip(RoundedCornerShape(ThemeTokens.cornerRadius.sm))
                            .background(colors.surface.secondary)
                            .border(
                                ThemeTokens.strokeWidth.thin,
                                colors.border.default,
                                RoundedCornerShape(ThemeTokens.cornerRadius.sm),
                            ).padding(spacing.space4),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(text = label, style = ThemeTokens.typography.caption, color = colors.text.primary)
                    Text(
                        text = "${dp.value.toInt()}dp",
                        style = ThemeTokens.typography.labelSmall,
                        color = colors.text.brand,
                    )
                }
            }
        }
        UiDivider(DividerAtom(), modifier = Modifier.padding(vertical = spacing.space8))
        UiText(
            TextAtom(
                text =
                    "Min Touch Target: ${sizing.minTouchTarget.value.toInt()}dp · " +
                        "ButtonHeights: ${sizing.buttonSm.value.toInt()}/${sizing.buttonMd.value.toInt()}/" +
                        "${sizing.buttonLg.value.toInt()}dp · " +
                        "Opacity (disabled/muted/scrim): ${opacity.disabled}/${opacity.muted}/${opacity.overlayScrim}",
                typography = TypographyTokenRef("body.small"),
                color = ColorTokenRef("text.secondary"),
            ),
        )
    }
}
