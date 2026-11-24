package dev.abeta.kmp.atoms.atoms

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.modifiers.LocalUiHapticFeedback
import dev.abeta.kmp.atoms.modifiers.UiHapticKind
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.atoms.modifiers.applyMotionSpec
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.tokens.ThemeTokens

private const val SUB_LABEL_ALPHA = 0.85f
private const val BUTTON_PRESS_SCALE = 0.97f

private data class ButtonRenderSpec(
    val style: ResolvedButtonStyle,
    val shape: Shape,
    val contentPadding: PaddingValues,
    val interactionSource: MutableInteractionSource,
)

private fun resolveButtonClickAction(atom: ButtonAtom): ActionRef? {
    val clickAction = atom.onClick ?: return null
    return if (atom.tracking != null && clickAction.tracking == null) {
        clickAction.copy(tracking = atom.tracking)
    } else {
        clickAction
    }
}

/**
 * Stateless interactive button UI atom rendered from a [ButtonAtom] contract.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiButton(
    atom: ButtonAtom,
    modifier: Modifier = Modifier,
    onAction: (ActionRef) -> Unit = {},
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val cornerRadius = ThemeTokens.cornerRadius
    val motion = ThemeTokens.motion
    val haptic = LocalUiHapticFeedback.current
    val style = resolveButtonStyle(atom.variant, atom.size, atom.enabled, colors, spacing)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isInteractive = atom.enabled && !atom.loading
    val pressScaleState =
        animateFloatAsState(
            targetValue = if (isPressed && isInteractive && !motion.reducedMotion) BUTTON_PRESS_SCALE else 1f,
            animationSpec = if (motion.reducedMotion) snap() else tween(durationMillis = motion.fastMs),
            label = "button_press_scale",
        )
    val spec =
        ButtonRenderSpec(
            style = style,
            shape = RoundedCornerShape(cornerRadius.md),
            contentPadding = PaddingValues(horizontal = style.horizontalPadding, vertical = style.verticalPadding),
            interactionSource = interactionSource,
        )
    val buttonModifier =
        modifier
            .applyMotionSpec(atom.motion)
            .graphicsLayer {
                val scale = pressScaleState.value
                scaleX = scale
                scaleY = scale
            }.applyLayoutConfig(atom.layout)
            .let { if (atom.fullWidth) it.fillMaxWidth() else it }
            .heightIn(min = style.minHeight)
    val onClickLambda: () -> Unit = {
        if (isInteractive) {
            haptic(UiHapticKind.CLICK)
            resolveButtonClickAction(atom)?.let(onAction)
        }
    }

    UiButtonContainer(
        atom = atom,
        spec = spec,
        modifier = buttonModifier,
        onClick = onClickLambda,
        content = { UiButtonBody(atom = atom, style = style, onAction = onAction) },
    )
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiButtonContainer(
    atom: ButtonAtom,
    spec: ButtonRenderSpec,
    modifier: Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    when (atom.variant) {
        ButtonVariant.OUTLINE -> {
            UiOutlineButton(
                atom = atom,
                spec = spec,
                modifier = modifier,
                onClick = onClick,
                content = content,
            )
        }

        ButtonVariant.GHOST -> {
            UiGhostButton(
                enabled = atom.enabled,
                spec = spec,
                modifier = modifier,
                onClick = onClick,
                content = content,
            )
        }

        ButtonVariant.PRIMARY,
        ButtonVariant.SECONDARY,
        ButtonVariant.DESTRUCTIVE,
        -> {
            UiPrimaryButton(
                enabled = atom.enabled,
                spec = spec,
                modifier = modifier,
                onClick = onClick,
                content = content,
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiOutlineButton(
    atom: ButtonAtom,
    spec: ButtonRenderSpec,
    modifier: Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val colors = ThemeTokens.colors
    val strokeWidth = ThemeTokens.strokeWidth
    val style = spec.style
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = atom.enabled,
        shape = spec.shape,
        border = BorderStroke(strokeWidth.thin, style.borderColor ?: colors.button.outlineBorder),
        colors =
            ButtonDefaults.outlinedButtonColors(
                containerColor = style.containerColor,
                contentColor = style.contentColor,
                disabledContainerColor = style.containerColor,
                disabledContentColor = style.contentColor,
            ),
        contentPadding = spec.contentPadding,
        interactionSource = spec.interactionSource,
        content = { content() },
    )
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiGhostButton(
    enabled: Boolean,
    spec: ButtonRenderSpec,
    modifier: Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val style = spec.style
    TextButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = spec.shape,
        colors =
            ButtonDefaults.textButtonColors(
                containerColor = style.containerColor,
                contentColor = style.contentColor,
                disabledContainerColor = style.containerColor,
                disabledContentColor = style.contentColor,
            ),
        contentPadding = spec.contentPadding,
        interactionSource = spec.interactionSource,
        content = { content() },
    )
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiPrimaryButton(
    enabled: Boolean,
    spec: ButtonRenderSpec,
    modifier: Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val style = spec.style
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = spec.shape,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = style.containerColor,
                contentColor = style.contentColor,
                disabledContainerColor = style.containerColor,
                disabledContentColor = style.contentColor,
            ),
        contentPadding = spec.contentPadding,
        interactionSource = spec.interactionSource,
        content = { content() },
    )
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiButtonBody(
    atom: ButtonAtom,
    style: ResolvedButtonStyle,
    onAction: (ActionRef) -> Unit,
) {
    val spacing = ThemeTokens.spacing
    val strokeWidth = ThemeTokens.strokeWidth

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space8),
    ) {
        if (atom.loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(style.iconSize.dp),
                color = style.contentColor,
                strokeWidth = strokeWidth.medium,
            )
        } else {
            atom.leadingIcon?.let { icon ->
                UiIcon(
                    atom = icon.copy(sizeDp = icon.sizeDp ?: style.iconSize),
                    onAction = onAction,
                )
            }
        }
        UiButtonLabels(atom = atom, style = style)
        if (!atom.loading) {
            atom.trailingIcon?.let { icon ->
                UiIcon(
                    atom = icon.copy(sizeDp = icon.sizeDp ?: style.iconSize),
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiButtonLabels(
    atom: ButtonAtom,
    style: ResolvedButtonStyle,
) {
    val typography = ThemeTokens.typography
    val subLabel = atom.subLabel
    if (subLabel.isNullOrBlank()) {
        Text(
            text = atom.label,
            style = typography.labelLarge,
            color = style.contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = atom.label,
                style = typography.labelLarge,
                color = style.contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subLabel,
                style = typography.labelSmall,
                color = style.contentColor.copy(alpha = SUB_LABEL_ALPHA),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
