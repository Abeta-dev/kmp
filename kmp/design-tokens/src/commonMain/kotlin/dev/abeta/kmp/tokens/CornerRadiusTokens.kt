package dev.abeta.kmp.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Corner radius scale tokens for surfaces, buttons, tags, and inputs.
 */
@Immutable
data class CornerRadiusTokens(
    val none: Dp,
    val xs: Dp,
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,
    val full: Dp,
)

/**
 * Default corner radius tokens.
 */
@Suppress("MagicNumber")
val DefaultCornerRadiusTokens =
    CornerRadiusTokens(
        none = 0.dp,
        xs = 4.dp,
        sm = 8.dp,
        md = 12.dp,
        lg = 16.dp,
        xl = 24.dp,
        full = 999.dp,
    )

/**
 * Stroke and divider thickness tokens.
 */
@Immutable
data class StrokeWidthTokens(
    val hairline: Dp,
    val thin: Dp,
    val medium: Dp,
    val thick: Dp,
)

/**
 * Default stroke width tokens.
 */
@Suppress("MagicNumber")
val DefaultStrokeWidthTokens =
    StrokeWidthTokens(
        hairline = 0.5.dp,
        thin = 1.dp,
        medium = 1.5.dp,
        thick = 2.dp,
    )
