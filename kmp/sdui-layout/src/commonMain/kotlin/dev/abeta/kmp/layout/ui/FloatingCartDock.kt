package dev.abeta.kmp.layout.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import dev.abeta.kmp.atoms.atoms.UiButton
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.atoms.modifiers.applyMotionSpec
import dev.abeta.kmp.atoms.modifiers.resolveEnterTransition
import dev.abeta.kmp.atoms.modifiers.resolveExitTransition
import dev.abeta.kmp.atoms.modifiers.resolveMotionDurationMs
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.FeedbackEffect
import dev.abeta.kmp.contracts.FloatingCartSpec
import dev.abeta.kmp.contracts.MotionTransition
import dev.abeta.kmp.layout.engine.LayoutUiState
import dev.abeta.kmp.layout.engine.computeFloatingCartSummary
import dev.abeta.kmp.tokens.ThemeTokens

private const val CART_DOCK_BOUNCE_PEAK = 1.06f
private const val ONE = 1f

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
internal fun FloatingCartDockHost(
    floatingCart: FloatingCartSpec,
    state: LayoutUiState.Ready,
    onAction: (ActionRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionTokens = ThemeTokens.motion
    val summary =
        remember(state.page, state.formValues) {
            computeFloatingCartSummary(state.page, state.formValues)
        }
    val visible = !summary.hasProductCards || summary.itemCount > 0
    val resolvedLabel =
        if (summary.hasProductCards) summary.formattedLabel() else floatingCart.label
    val durationMs = resolveMotionDurationMs(floatingCart.motion, motionTokens)
    val bounceScale =
        rememberCartBounceScale(
            feedbackSequence = state.feedbackSequence,
            effect = state.activeFeedback?.effect,
        )

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = bounceScale
                    scaleY = bounceScale
                },
    ) {
        if (motionTokens.reducedMotion) {
            if (visible) {
                FloatingCartDockContent(
                    floatingCart = floatingCart,
                    label = resolvedLabel,
                    onAction = onAction,
                )
            }
        } else {
            val enterPreset = floatingCart.motion?.enter ?: MotionTransition.SLIDE_UP
            val exitPreset = floatingCart.motion?.exit ?: MotionTransition.SLIDE_DOWN
            AnimatedVisibility(
                visible = visible,
                enter = resolveEnterTransition(enterPreset, durationMs),
                exit = resolveExitTransition(exitPreset, durationMs),
            ) {
                FloatingCartDockContent(
                    floatingCart = floatingCart,
                    label = resolvedLabel,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun rememberCartBounceScale(
    feedbackSequence: Long,
    effect: FeedbackEffect?,
): Float {
    val motionTokens = ThemeTokens.motion
    val bounceScale = remember { Animatable(ONE) }
    LaunchedEffect(feedbackSequence, effect, motionTokens.reducedMotion) {
        if (!motionTokens.reducedMotion && effect == FeedbackEffect.CART_BOUNCE) {
            bounceScale.animateTo(
                targetValue = CART_DOCK_BOUNCE_PEAK,
                animationSpec = tween(durationMillis = motionTokens.fastMs),
            )
            bounceScale.animateTo(
                targetValue = ONE,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
            )
        }
    }
    return bounceScale.value
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun FloatingCartDockContent(
    floatingCart: FloatingCartSpec,
    label: String,
    onAction: (ActionRef) -> Unit,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    val cornerRadius = ThemeTokens.cornerRadius
    val elevation = ThemeTokens.elevation
    val strokeWidth = ThemeTokens.strokeWidth
    val shape = RoundedCornerShape(cornerRadius.lg)

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("floating_cart_dock")
                .applyMotionSpec(motion = floatingCart.motion, motionKey = "floating_cart_surface"),
        shape = shape,
        color = colors.surface.elevated,
        shadowElevation = elevation.lg,
        border = BorderStroke(strokeWidth.thin, colors.border.subtle),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.space16, vertical = spacing.space12),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space12),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.space4),
            ) {
                floatingCart.etaBadge?.let { eta ->
                    UiTag(atom = eta, onAction = onAction)
                }
                Text(
                    text = label,
                    style = typography.titleSmall,
                    color = colors.text.primary,
                )
            }
            UiButton(
                atom = floatingCart.cta,
                onAction = onAction,
            )
        }
    }
}
