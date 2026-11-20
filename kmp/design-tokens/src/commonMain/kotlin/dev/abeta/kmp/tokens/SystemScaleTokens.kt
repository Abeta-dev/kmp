package dev.abeta.kmp.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Elevation depth tokens for surfaces, cards, popovers, and modal overlays.
 */
@Immutable
data class ElevationTokens(
    val none: Dp,
    val xs: Dp,
    val sm: Dp,
    val md: Dp,
    val lg: Dp,
    val xl: Dp,
)

/**
 * Default elevation scale tokens.
 */
@Suppress("MagicNumber")
val DefaultElevationTokens =
    ElevationTokens(
        none = 0.dp,
        xs = 1.dp,
        sm = 2.dp,
        md = 4.dp,
        lg = 8.dp,
        xl = 16.dp,
    )

/**
 * Animation duration tokens respecting WCAG reduced-motion accessibility preferences.
 */
@Immutable
data class MotionTokens(
    val instantMs: Int,
    val fastMs: Int,
    val normalMs: Int,
    val slowMs: Int,
    val reducedMotion: Boolean = false,
) {
    /** Returns [durationMs] when full motion is active or `0` when [reducedMotion] is enabled. */
    fun resolveDurationMs(durationMs: Int): Int = if (reducedMotion) instantMs else durationMs
}

/**
 * Default motion timing scale (milliseconds).
 */
@Suppress("MagicNumber")
val DefaultMotionTokens =
    MotionTokens(
        instantMs = 0,
        fastMs = 150,
        normalMs = 250,
        slowMs = 400,
        reducedMotion = false,
    )

/**
 * Reduced-motion token scale collapsing animations to zero duration for accessibility compliance.
 */
@Suppress("MagicNumber")
val ReducedMotionTokens =
    DefaultMotionTokens.copy(
        reducedMotion = true,
    )

/**
 * Semantic opacity tokens for disabled, muted, pressed, and scrim states.
 */
@Immutable
data class OpacityTokens(
    val disabled: Float,
    val muted: Float,
    val pressed: Float,
    val overlayScrim: Float,
)

/**
 * Default opacity tokens.
 */
@Suppress("MagicNumber")
val DefaultOpacityTokens =
    OpacityTokens(
        disabled = 0.38f,
        muted = 0.65f,
        pressed = 0.88f,
        overlayScrim = 0.45f,
    )

/**
 * Standardized control, icon, and WCAG minimum touch-target dimensions.
 */
@Immutable
data class SizingTokens(
    val minTouchTarget: Dp,
    val iconSm: Dp,
    val iconMd: Dp,
    val iconLg: Dp,
    val iconXl: Dp,
    val buttonSm: Dp,
    val buttonMd: Dp,
    val buttonLg: Dp,
    val inputMinHeight: Dp,
)

/**
 * Default sizing tokens honoring a 48dp minimum accessible touch target.
 */
@Suppress("MagicNumber")
val DefaultSizingTokens =
    SizingTokens(
        minTouchTarget = 48.dp,
        iconSm = 16.dp,
        iconMd = 20.dp,
        iconLg = 24.dp,
        iconXl = 32.dp,
        buttonSm = 36.dp,
        buttonMd = 44.dp,
        buttonLg = 52.dp,
        inputMinHeight = 48.dp,
    )
