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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.atoms.modifiers.toPaddingValues
import dev.abeta.kmp.contracts.LayoutConfig
import dev.abeta.kmp.contracts.SpacingValues
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
    val interactionSource = if (onClick != null) remember { MutableInteractionSource() } else null
    val scaledModifier = applyCardPressScale(modifier = modifier, interactionSource = interactionSource)
    val interactiveModifier =
        resolveCardSurfaceModifier(
            baseModifier = scaledModifier,
            layout = layout,
            interactionSource = interactionSource,
            onClick = onClick,
        )

    Column(modifier = interactiveModifier, content = content)
}

@Composable
private fun resolveCardSurfaceModifier(
    baseModifier: Modifier,
    layout: LayoutConfig?,
    interactionSource: MutableInteractionSource?,
    onClick: (() -> Unit)?,
): Modifier {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val defaultShape = RoundedCornerShape(ThemeTokens.cornerRadius.md)
    val strokeWidth = ThemeTokens.strokeWidth
    val indication = LocalIndication.current
    val clickableModifier: Modifier.() -> Modifier = {
        if (onClick != null && interactionSource != null) {
            clickable(interactionSource = interactionSource, indication = indication, onClick = onClick)
        } else {
            this
        }
    }
    return when {
        layout == null -> {
            baseModifier
                .clip(defaultShape)
                .background(colors.surface.elevated, defaultShape)
                .border(strokeWidth.thin, colors.border.subtle, defaultShape)
                .clickableModifier()
                .padding(spacing.space16)
        }

        layout.bgColor == null && layout.cornerRadius == null -> {
            val outer =
                baseModifier
                    .let { if (layout.fillMaxWidth) it.fillMaxWidth() else it }
                    .let { applyOptionalPadding(it, layout.margin) }
            val surface =
                outer
                    .clip(defaultShape)
                    .background(colors.surface.elevated, defaultShape)
                    .applyLayoutConfig(layout.copy(fillMaxWidth = false, margin = null, padding = null))
                    .clickableModifier()
            applyOptionalPadding(surface, layout.padding)
        }

        else -> {
            val surface = baseModifier.applyLayoutConfig(layout.copy(padding = null)).clickableModifier()
            applyOptionalPadding(surface, layout.padding)
        }
    }
}

private fun applyOptionalPadding(
    modifier: Modifier,
    spacing: SpacingValues?,
): Modifier = if (spacing != null) modifier.padding(spacing.toPaddingValues()) else modifier

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
