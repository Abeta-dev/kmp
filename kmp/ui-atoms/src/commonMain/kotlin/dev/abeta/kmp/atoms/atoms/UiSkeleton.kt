package dev.abeta.kmp.atoms.atoms

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.contracts.SkeletonAtom
import dev.abeta.kmp.contracts.SkeletonKind
import dev.abeta.kmp.tokens.CornerRadiusTokens
import dev.abeta.kmp.tokens.ThemeTokens

private const val DEFAULT_TEXT_HEIGHT_DP = 16
private const val DEFAULT_RECTANGLE_HEIGHT_DP = 80
private const val DEFAULT_CIRCLE_SIZE_DP = 48
private const val SHIMMER_MIN_ALPHA = 0.35f
private const val SHIMMER_MAX_ALPHA = 0.85f
private const val SHIMMER_CYCLE_MULTIPLIER = 2

/**
 * Pure helper resolving default height in dp for a [SkeletonAtom] when [SkeletonAtom.heightDp] is absent.
 */
fun resolveSkeletonHeightDp(atom: SkeletonAtom): Int =
    atom.heightDp?.takeIf { it > 0 }
        ?: when (atom.kind) {
            SkeletonKind.TEXT -> DEFAULT_TEXT_HEIGHT_DP
            SkeletonKind.RECTANGLE -> DEFAULT_RECTANGLE_HEIGHT_DP
            SkeletonKind.CIRCLE -> atom.widthDp?.takeIf { it > 0 } ?: DEFAULT_CIRCLE_SIZE_DP
        }

/**
 * Pure helper resolving clip [Shape] for a [SkeletonKind].
 */
fun resolveSkeletonShape(
    kind: SkeletonKind,
    cornerRadius: CornerRadiusTokens,
): Shape =
    when (kind) {
        SkeletonKind.TEXT -> RoundedCornerShape(cornerRadius.xs)
        SkeletonKind.RECTANGLE -> RoundedCornerShape(cornerRadius.md)
        SkeletonKind.CIRCLE -> CircleShape
    }

/**
 * Stateless shimmer placeholder UI atom rendered from a [SkeletonAtom] contract.
 *
 * Honors [dev.abeta.kmp.tokens.MotionTokens.reducedMotion] by rendering a static muted fill.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiSkeleton(
    atom: SkeletonAtom,
    modifier: Modifier = Modifier,
) {
    val colors = ThemeTokens.colors
    val cornerRadius = ThemeTokens.cornerRadius
    val motion = ThemeTokens.motion
    val opacity = ThemeTokens.opacity

    val shimmerAlphaState =
        if (motion.reducedMotion) {
            null
        } else {
            rememberShimmerAlphaState(durationMs = motion.slowMs * SHIMMER_CYCLE_MULTIPLIER)
        }

    val shape = resolveSkeletonShape(atom.kind, cornerRadius)
    val sizedModifier =
        resolveSkeletonSizeModifier(
            atom = atom,
            baseModifier = modifier.applyLayoutConfig(atom.layout),
        )
    val alphaModifier =
        if (shimmerAlphaState != null) {
            sizedModifier.graphicsLayer { alpha = shimmerAlphaState.value }
        } else {
            sizedModifier
        }
    val fillColor =
        if (shimmerAlphaState != null) {
            colors.background.tertiary
        } else {
            colors.background.tertiary.copy(alpha = opacity.muted)
        }

    Box(
        modifier =
            alphaModifier
                .clip(shape)
                .background(fillColor, shape),
    )
}

@Composable
private fun rememberShimmerAlphaState(durationMs: Int): State<Float> {
    val transition = rememberInfiniteTransition(label = "skeleton_shimmer")
    return transition.animateFloat(
        initialValue = SHIMMER_MIN_ALPHA,
        targetValue = SHIMMER_MAX_ALPHA,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = durationMs.coerceAtLeast(1), easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "skeleton_alpha",
    )
}

private fun resolveSkeletonSizeModifier(
    atom: SkeletonAtom,
    baseModifier: Modifier,
): Modifier {
    val heightDp = resolveSkeletonHeightDp(atom)
    return if (atom.kind == SkeletonKind.CIRCLE) {
        val diameter = atom.widthDp?.takeIf { it > 0 } ?: heightDp
        baseModifier.size(diameter.dp)
    } else {
        val widthDp = atom.widthDp?.takeIf { it > 0 }
        val withWidth =
            if (widthDp != null) {
                baseModifier.width(widthDp.dp)
            } else {
                baseModifier.fillMaxWidth()
            }
        withWidth.height(heightDp.dp)
    }
}
