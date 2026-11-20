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
import androidx.compose.runtime.getValue
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
    val motion = ThemeTokens.motion

    val interactionSource = if (onClick != null) remember { MutableInteractionSource() } else null
    val isPressed = interactionSource?.collectIsPressedAsState()?.value == true
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && !motion.reducedMotion) CARD_PRESS_SCALE else 1f,
        animationSpec = if (motion.reducedMotion) snap() else tween(durationMillis = motion.fastMs),
        label = "surface_card_press_scale",
    )

    val scaledModifier =
        if (onClick != null) {
            modifier.graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
        } else {
            modifier
        }

    val defaultShape = RoundedCornerShape(cornerRadius.md)
    val baseModifier =
        if (layout == null) {
            scaledModifier
                .clip(defaultShape)
                .background(colors.surface.elevated, defaultShape)
                .border(strokeWidth.thin, colors.border.subtle, defaultShape)
                .padding(spacing.space16)
        } else {
            if (layout.bgColor == null && layout.cornerRadius == null) {
                scaledModifier
                    .clip(defaultShape)
                    .background(colors.surface.elevated, defaultShape)
                    .applyLayoutConfig(layout)
            } else {
                scaledModifier.applyLayoutConfig(layout)
            }
        }

    val interactiveModifier =
        if (onClick != null && interactionSource != null) {
            baseModifier.clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick,
            )
        } else {
            baseModifier
        }

    Column(
        modifier = interactiveModifier,
        content = content,
    )
}
