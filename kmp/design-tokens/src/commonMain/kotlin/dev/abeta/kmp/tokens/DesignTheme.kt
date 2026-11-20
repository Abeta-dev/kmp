package dev.abeta.kmp.tokens

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * [ProvidableCompositionLocal] providing the active [ColorTokens].
 */
val LocalColorTokens: ProvidableCompositionLocal<ColorTokens> =
    staticCompositionLocalOf { LightColorTokens }

/**
 * [ProvidableCompositionLocal] providing the active [TypographyTokens].
 */
val LocalTypographyTokens: ProvidableCompositionLocal<TypographyTokens> =
    staticCompositionLocalOf { DefaultTypographyTokens }

/**
 * [ProvidableCompositionLocal] providing the active [SpacingTokens].
 */
val LocalSpacingTokens: ProvidableCompositionLocal<SpacingTokens> =
    staticCompositionLocalOf { DefaultSpacingTokens }

/**
 * [ProvidableCompositionLocal] providing the active [CornerRadiusTokens].
 */
val LocalCornerRadiusTokens: ProvidableCompositionLocal<CornerRadiusTokens> =
    staticCompositionLocalOf { DefaultCornerRadiusTokens }

/**
 * [ProvidableCompositionLocal] providing the active [StrokeWidthTokens].
 */
val LocalStrokeWidthTokens: ProvidableCompositionLocal<StrokeWidthTokens> =
    staticCompositionLocalOf { DefaultStrokeWidthTokens }

/**
 * [ProvidableCompositionLocal] providing the active [ElevationTokens].
 */
val LocalElevationTokens: ProvidableCompositionLocal<ElevationTokens> =
    staticCompositionLocalOf { DefaultElevationTokens }

/**
 * [ProvidableCompositionLocal] providing the active [MotionTokens].
 */
val LocalMotionTokens: ProvidableCompositionLocal<MotionTokens> =
    staticCompositionLocalOf { DefaultMotionTokens }

/**
 * [ProvidableCompositionLocal] providing the active [OpacityTokens].
 */
val LocalOpacityTokens: ProvidableCompositionLocal<OpacityTokens> =
    staticCompositionLocalOf { DefaultOpacityTokens }

/**
 * [ProvidableCompositionLocal] providing the active [SizingTokens].
 */
val LocalSizingTokens: ProvidableCompositionLocal<SizingTokens> =
    staticCompositionLocalOf { DefaultSizingTokens }

/**
 * Bundled design-system token configuration passed into [DesignTheme].
 */
data class DesignTokenBundle(
    val colors: ColorTokens = LightColorTokens,
    val typography: TypographyTokens = DefaultTypographyTokens,
    val spacing: SpacingTokens = DefaultSpacingTokens,
    val cornerRadius: CornerRadiusTokens = DefaultCornerRadiusTokens,
    val strokeWidth: StrokeWidthTokens = DefaultStrokeWidthTokens,
    val elevation: ElevationTokens = DefaultElevationTokens,
    val motion: MotionTokens = DefaultMotionTokens,
    val opacity: OpacityTokens = DefaultOpacityTokens,
    val sizing: SizingTokens = DefaultSizingTokens,
)

/**
 * Ambient accessor for all design tokens within a [DesignTheme].
 */
object ThemeTokens {
    val colors: ColorTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalColorTokens.current

    val typography: TypographyTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalTypographyTokens.current

    val spacing: SpacingTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalSpacingTokens.current

    val cornerRadius: CornerRadiusTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalCornerRadiusTokens.current

    val strokeWidth: StrokeWidthTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalStrokeWidthTokens.current

    val elevation: ElevationTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalElevationTokens.current

    val motion: MotionTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalMotionTokens.current

    val opacity: OpacityTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalOpacityTokens.current

    val sizing: SizingTokens
        @Composable
        @ReadOnlyComposable
        get() = LocalSizingTokens.current

    val adaptiveMetrics: AdaptiveLayoutMetrics
        @Composable
        @ReadOnlyComposable
        get() = LocalAdaptiveLayoutMetrics.current
}

/**
 * Root theme composable providing semantic design tokens and bridging them cleanly into Material 3.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun DesignTheme(
    preset: ThemePreset = ThemePreset.INDIGO,
    darkTheme: Boolean = isSystemInDarkTheme(),
    reducedMotion: Boolean = false,
    tokens: DesignTokenBundle =
        DesignTokenBundle(
            colors = resolveColorTokens(preset = preset, darkTheme = darkTheme),
            motion = if (reducedMotion) ReducedMotionTokens else DefaultMotionTokens,
        ),
    content: @Composable () -> Unit,
) {
    val colorScheme = tokens.colors.toMaterialColorScheme()
    val materialTypography = tokens.typography.toMaterialTypography()

    CompositionLocalProvider(
        LocalColorTokens provides tokens.colors,
        LocalTypographyTokens provides tokens.typography,
        LocalSpacingTokens provides tokens.spacing,
        LocalCornerRadiusTokens provides tokens.cornerRadius,
        LocalStrokeWidthTokens provides tokens.strokeWidth,
        LocalElevationTokens provides tokens.elevation,
        LocalMotionTokens provides tokens.motion,
        LocalOpacityTokens provides tokens.opacity,
        LocalSizingTokens provides tokens.sizing,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = materialTypography,
            content = content,
        )
    }
}

private fun ColorTokens.toMaterialColorScheme(): ColorScheme =
    if (isDark) {
        darkColorScheme(
            primary = brand.primary,
            onPrimary = brand.onPrimary,
            primaryContainer = brand.container,
            onPrimaryContainer = brand.onContainer,
            secondary = brand.secondary,
            onSecondary = brand.onSecondary,
            background = background.primary,
            onBackground = text.primary,
            surface = surface.primary,
            onSurface = text.primary,
            surfaceVariant = surface.secondary,
            onSurfaceVariant = text.secondary,
            outline = border.default,
            outlineVariant = border.subtle,
            error = status.error,
            onError = text.onBrand,
            errorContainer = status.errorSubtle,
            onErrorContainer = text.error,
        )
    } else {
        lightColorScheme(
            primary = brand.primary,
            onPrimary = brand.onPrimary,
            primaryContainer = brand.container,
            onPrimaryContainer = brand.onContainer,
            secondary = brand.secondary,
            onSecondary = brand.onSecondary,
            background = background.primary,
            onBackground = text.primary,
            surface = surface.primary,
            onSurface = text.primary,
            surfaceVariant = surface.secondary,
            onSurfaceVariant = text.secondary,
            outline = border.default,
            outlineVariant = border.subtle,
            error = status.error,
            onError = text.onBrand,
            errorContainer = status.errorSubtle,
            onErrorContainer = text.error,
        )
    }

private fun TypographyTokens.toMaterialTypography(): Typography =
    Typography(
        displayLarge = displayLarge,
        displayMedium = displayMedium,
        displaySmall = displaySmall,
        headlineLarge = headlineLarge,
        headlineMedium = headlineMedium,
        headlineSmall = headlineSmall,
        titleLarge = titleLarge,
        titleMedium = titleMedium,
        titleSmall = titleSmall,
        bodyLarge = bodyLarge,
        bodyMedium = bodyMedium,
        bodySmall = bodySmall,
        labelLarge = labelLarge,
        labelMedium = labelMedium,
        labelSmall = labelSmall,
    )
