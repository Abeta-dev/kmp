package dev.abeta.kmp.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Semantic background color roles.
 */
@Immutable
data class BackgroundColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val inverse: Color,
)

/**
 * Semantic surface color roles for cards, sheets, and elevated containers.
 */
@Immutable
data class SurfaceColors(
    val primary: Color,
    val secondary: Color,
    val elevated: Color,
    val inverse: Color,
    val brand: Color,
    val brandSubtle: Color,
)

/**
 * Semantic brand and accent color roles aligned with Go BFF `brand.*` and `accent.*` tokens.
 */
@Immutable
data class BrandColors(
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val onSecondary: Color,
    val container: Color,
    val onContainer: Color,
    val accent: Color,
    val onAccent: Color,
)

/**
 * Semantic border and divider color roles.
 */
@Immutable
data class BorderColors(
    val subtle: Color,
    val default: Color,
    val strong: Color,
    val focus: Color,
    val error: Color,
    val divider: Color,
)

/**
 * Semantic typography color roles.
 */
@Immutable
data class TextColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val disabled: Color,
    val inverse: Color,
    val brand: Color,
    val onBrand: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color,
)

/**
 * Semantic iconography color roles.
 */
@Immutable
data class IconColors(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val disabled: Color,
    val inverse: Color,
    val brand: Color,
    val onBrand: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color,
)

/**
 * Semantic interactive button color roles.
 */
@Immutable
data class ButtonColors(
    val primaryBackground: Color,
    val primaryForeground: Color,
    val secondaryBackground: Color,
    val secondaryForeground: Color,
    val outlineBorder: Color,
    val ghostForeground: Color,
    val destructiveBackground: Color,
    val destructiveForeground: Color,
    val disabledBackground: Color,
    val disabledForeground: Color,
)

/**
 * Semantic form input and field control color roles.
 */
@Immutable
data class InputColors(
    val background: Color,
    val foreground: Color,
    val placeholder: Color,
    val border: Color,
    val focusBorder: Color,
    val errorBorder: Color,
    val disabledBackground: Color,
)

/**
 * Semantic tag and badge color roles.
 */
@Immutable
data class TagColors(
    val neutralBackground: Color,
    val neutralForeground: Color,
    val brandBackground: Color,
    val brandForeground: Color,
    val successBackground: Color,
    val successForeground: Color,
    val warningBackground: Color,
    val warningForeground: Color,
    val errorBackground: Color,
    val errorForeground: Color,
    val infoBackground: Color,
    val infoForeground: Color,
)

/**
 * Semantic status indicator color roles.
 */
@Immutable
data class StatusColors(
    val success: Color,
    val successSubtle: Color,
    val warning: Color,
    val warningSubtle: Color,
    val error: Color,
    val errorSubtle: Color,
    val info: Color,
    val infoSubtle: Color,
)

/**
 * Semantic modal scrim and overlay color roles.
 */
@Immutable
data class OverlayColors(
    val scrim: Color,
    val tooltipBackground: Color,
    val tooltipForeground: Color,
)

/**
 * Colorblind-accessible Okabe-Ito 5-series chart palette.
 */
@Immutable
data class ChartColors(
    val series1: Color,
    val series2: Color,
    val series3: Color,
    val series4: Color,
    val series5: Color,
)

/**
 * Complete semantic color token contract across all UI roles.
 */
@Immutable
data class ColorTokens(
    val isDark: Boolean,
    val background: BackgroundColors,
    val surface: SurfaceColors,
    val brand: BrandColors,
    val border: BorderColors,
    val text: TextColors,
    val icon: IconColors,
    val button: ButtonColors,
    val input: InputColors,
    val tag: TagColors,
    val status: StatusColors,
    val overlay: OverlayColors,
    val chart: ChartColors,
)

/**
 * Default light-mode semantic color tokens (Indigo primary).
 */
val LightColorTokens: ColorTokens = createIndigoLightColorTokens()

/**
 * Default dark-mode semantic color tokens (Indigo primary).
 */
val DarkColorTokens: ColorTokens = createIndigoDarkColorTokens()
