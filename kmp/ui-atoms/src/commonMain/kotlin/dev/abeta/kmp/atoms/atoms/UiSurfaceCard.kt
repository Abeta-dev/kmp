package dev.abeta.kmp.atoms.atoms

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.contracts.LayoutConfig
import dev.abeta.kmp.tokens.ThemeTokens

private const val CARD_PRESS_SCALE = 0.97f

/**
 * Stateless themed surface card container atom applying default elevated styling
 * when no explicit [LayoutConfig] overrides are supplied.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiSurfaceCard(
    modifier: Modifier = Modifier,
    layout: LayoutConfig? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val cornerRadius = ThemeTokens.cornerRadius
    val strokeWidth = ThemeTokens.strokeWidth

    val interactionSource = if (onClick != null) remember { MutableInteractionSource() } else null
    val scaledModifier = applyCardPressScale(modifier = modifier, interactionSource = interactionSource)
    val defaultShape = RoundedCornerShape(cornerRadius.md)
    val indication = LocalIndication.current
    val clickableIfNeeded: Modifier.() -> Modifier = {
        if (onClick != null && interactionSource != null) {
            clickable(
                interactionSource = interactionSource,
                indication = indication,
                onClick = onClick,
            )
        } else {
            this
        }
    }
    val interactiveModifier =
        when {
            layout == null -> {
                scaledModifier
                    .clip(defaultShape)
                    .background(colors.surface.elevated, defaultShape)
                    .border(strokeWidth.thin, colors.border.subtle, defaultShape)
                    .clickableIfNeeded()
                    .padding(spacing.space16)
            }

            layout.bgColor == null && layout.cornerRadius == null -> {
                scaledModifier
                    .clip(defaultShape)
                    .background(colors.surface.elevated, defaultShape)
                    .clickableIfNeeded()
                    .applyLayoutConfig(layout)
            }

            else -> {
                scaledModifier
                    .applyLayoutConfig(layout)
                    .clickableIfNeeded()
            }
        }

    Column(modifier = interactiveModifier, content = content)
}

@Composable
private fun applyCardPressScale(
    modifier: Modifier,
    interactionSource: MutableInteractionSource?,
): Modifier {
    if (interactionSource == null) return modifier
    val motion = ThemeTokens.motion
    val isPressed = interactionSource.collectIsPressedAsState().value
    val pressScaleState =
        animateFloatAsState(
            targetValue = if (isPressed && !motion.reducedMotion) CARD_PRESS_SCALE else 1f,
            animationSpec = if (motion.reducedMotion) snap() else tween(durationMillis = motion.fastMs),
            label = "surface_card_press_scale",
        )
    return modifier.graphicsLayer {
        val scale = pressScaleState.value
        scaleX = scale
        scaleY = scale
    }
}
