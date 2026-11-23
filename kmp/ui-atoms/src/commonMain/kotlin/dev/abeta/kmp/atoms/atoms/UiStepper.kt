package dev.abeta.kmp.atoms.atoms

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.modifiers.LocalUiHapticFeedback
import dev.abeta.kmp.atoms.modifiers.UiHapticKind
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.StepperAtom
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.coroutines.delay

private const val STEPPER_ENTER_SCALE = 0.86f
private const val STEPPER_EXIT_SCALE = 0.86f
private const val STEPPER_BOUNCE_SCALE = 1.07f
private const val STEPPER_PRESS_SCALE = 0.94f
private const val STEPPER_ICON_SIZE_DP = 18
private const val ADD_LABEL = "ADD"
private const val ADD_CONTENT_DESCRIPTION = "Add item"
private const val DECREASE_CONTENT_DESCRIPTION = "Decrease quantity"
private const val INCREASE_CONTENT_DESCRIPTION = "Increase quantity"

/**
 * Pure helper resolving the effective clamped quantity from [atom] and optional [quantityOverride].
 */
fun resolveStepperQuantity(
    atom: StepperAtom,
    quantityOverride: Int? = null,
): Int {
    val safeMin = atom.minQuantity.coerceAtLeast(0)
    val safeMax = atom.maxQuantity.coerceAtLeast(safeMin)
    return (quantityOverride ?: atom.quantity).coerceIn(safeMin, safeMax)
}

/**
 * Pure helper returning the next quantity on increment, or `null` when disabled or at maximum.
 */
fun stepQuantityIncrement(
    atom: StepperAtom,
    currentQuantity: Int,
): Int? {
    val safeMin = atom.minQuantity.coerceAtLeast(0)
    val safeMax = atom.maxQuantity.coerceAtLeast(safeMin)
    return if (!atom.enabled || currentQuantity >= safeMax) {
        null
    } else {
        (currentQuantity + 1).coerceIn(safeMin, safeMax)
    }
}

/**
 * Pure helper returning the next quantity on decrement, or `null` when disabled or at minimum.
 */
fun stepQuantityDecrement(
    atom: StepperAtom,
    currentQuantity: Int,
): Int? {
    val safeMin = atom.minQuantity.coerceAtLeast(0)
    return if (!atom.enabled || currentQuantity <= safeMin) {
        null
    } else {
        (currentQuantity - 1).coerceAtLeast(safeMin)
    }
}

/**
 * Stateless animated quantity stepper UI atom rendered from a [StepperAtom] contract.
 *
 * Morphs cleanly between a brand `"ADD"` button when quantity is `0` and an animated `- qty +`
 * pill when quantity is greater than `0`, honoring [dev.abeta.kmp.tokens.MotionTokens.reducedMotion]
 * and [dev.abeta.kmp.tokens.SizingTokens.minTouchTarget].
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiStepper(
    atom: StepperAtom,
    modifier: Modifier = Modifier,
    quantityOverride: Int? = null,
    onQuantityChange: ((Int) -> Unit)? = null,
    onAction: (ActionRef) -> Unit = {},
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    val currentQuantity = resolveStepperQuantity(atom, quantityOverride)
    val labelText = atom.label?.takeIf { it.isNotBlank() }
    val rootModifier = modifier.applyLayoutConfig(atom.layout)

    if (labelText != null) {
        Row(
            modifier = rootModifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space12),
        ) {
            Text(
                text = labelText,
                style = typography.bodyMedium,
                color = if (atom.enabled) colors.text.primary else colors.text.disabled,
                modifier = Modifier.weight(1f, fill = false),
            )
            UiStepperControl(
                atom = atom,
                currentQuantity = currentQuantity,
                onQuantityChange = onQuantityChange,
                onAction = onAction,
            )
        }
    } else {
        Box(modifier = rootModifier) {
            UiStepperControl(
                atom = atom,
                currentQuantity = currentQuantity,
                onQuantityChange = onQuantityChange,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun rememberStepperBounceScaleState(currentQuantity: Int): State<Float> {
    val motion = ThemeTokens.motion
    var previousQuantity by remember { mutableIntStateOf(currentQuantity) }
    var bounceActive by remember { mutableStateOf(false) }

    LaunchedEffect(currentQuantity, motion.reducedMotion) {
        if (currentQuantity != previousQuantity) {
            previousQuantity = currentQuantity
            if (!motion.reducedMotion) {
                bounceActive = true
                delay(motion.fastMs.toLong())
                bounceActive = false
            }
        }
    }

    return animateFloatAsState(
        targetValue = if (bounceActive && !motion.reducedMotion) STEPPER_BOUNCE_SCALE else 1f,
        animationSpec =
            if (motion.reducedMotion) {
                snap()
            } else {
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
            },
        label = "stepper_bounce_scale",
    )
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiStepperControl(
    atom: StepperAtom,
    currentQuantity: Int,
    onQuantityChange: ((Int) -> Unit)?,
    onAction: (ActionRef) -> Unit,
) {
    val motion = ThemeTokens.motion
    val hapticFeedback = LocalUiHapticFeedback.current
    val bounceScaleState = rememberStepperBounceScaleState(currentQuantity)
    val nextIncrement = stepQuantityIncrement(atom, currentQuantity)
    val nextDecrement = stepQuantityDecrement(atom, currentQuantity)
    val onIncrementClick: () -> Unit = {
        nextIncrement?.let { next ->
            hapticFeedback(UiHapticKind.TICK)
            onQuantityChange?.invoke(next)
            atom.onIncrement?.let(onAction)
        }
    }
    val onDecrementClick: () -> Unit = {
        nextDecrement?.let { next ->
            hapticFeedback(UiHapticKind.TICK)
            onQuantityChange?.invoke(next)
            atom.onDecrement?.let(onAction)
        }
    }

    AnimatedContent(
        targetState = currentQuantity > 0,
        contentAlignment = Alignment.CenterEnd,
        modifier =
            Modifier.graphicsLayer {
                val scale = bounceScaleState.value
                scaleX = scale
                scaleY = scale
            },
        transitionSpec = {
            if (motion.reducedMotion) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                val springSpec = spring<Float>(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)
                val enter = fadeIn(tween(motion.fastMs)) + scaleIn(springSpec, STEPPER_ENTER_SCALE)
                val exit = fadeOut(tween(motion.fastMs)) + scaleOut(tween(motion.fastMs), STEPPER_EXIT_SCALE)
                (enter togetherWith exit).using(SizeTransform(clip = false, sizeAnimationSpec = { _, _ -> snap() }))
            }
        },
        label = "stepper_mode_morph",
    ) { hasQuantity ->
        if (hasQuantity) {
            UiStepperCounterPill(
                atom = atom,
                currentQuantity = currentQuantity,
                onDecrement = onDecrementClick,
                onIncrement = onIncrementClick,
            )
        } else {
            UiStepperAddButton(canIncrement = nextIncrement != null, onClick = onIncrementClick)
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiStepperAddButton(
    canIncrement: Boolean,
    onClick: () -> Unit,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val sizing = ThemeTokens.sizing
    val cornerRadius = ThemeTokens.cornerRadius
    val strokeWidth = ThemeTokens.strokeWidth
    val typography = ThemeTokens.typography
    val motion = ThemeTokens.motion

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScaleState =
        animateFloatAsState(
            targetValue = if (isPressed && canIncrement && !motion.reducedMotion) STEPPER_PRESS_SCALE else 1f,
            animationSpec = if (motion.reducedMotion) snap() else tween(durationMillis = motion.fastMs),
            label = "stepper_add_press_scale",
        )

    val shape = RoundedCornerShape(cornerRadius.full)
    val bgColor = if (canIncrement) colors.surface.primary else colors.button.disabledBackground
    val borderColor = if (canIncrement) colors.brand.primary else colors.border.subtle
    val textColor = if (canIncrement) colors.brand.primary else colors.button.disabledForeground

    Box(
        modifier =
            Modifier
                .graphicsLayer {
                    val scale = pressScaleState.value
                    scaleX = scale
                    scaleY = scale
                }.defaultMinSize(minWidth = sizing.minTouchTarget, minHeight = sizing.minTouchTarget)
                .clip(shape)
                .background(bgColor, shape)
                .border(strokeWidth.thin, borderColor, shape)
                .clickable(
                    enabled = canIncrement,
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick,
                ).semantics(mergeDescendants = true) {
                    role = Role.Button
                    contentDescription = ADD_CONTENT_DESCRIPTION
                }.padding(horizontal = spacing.space20, vertical = spacing.space8),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = ADD_LABEL,
            style = typography.labelLarge,
            color = textColor,
            maxLines = 1,
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiStepperCounterPill(
    atom: StepperAtom,
    currentQuantity: Int,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    val colors = ThemeTokens.colors
    val sizing = ThemeTokens.sizing
    val cornerRadius = ThemeTokens.cornerRadius
    val opacity = ThemeTokens.opacity

    val canDecrement = stepQuantityDecrement(atom, currentQuantity) != null
    val canIncrement = stepQuantityIncrement(atom, currentQuantity) != null
    val shape = RoundedCornerShape(cornerRadius.full)
    val containerColor = if (atom.enabled) colors.brand.primary else colors.button.disabledBackground
    val activeContentColor = if (atom.enabled) colors.brand.onPrimary else colors.button.disabledForeground
    val disabledContentColor = activeContentColor.copy(alpha = opacity.disabled)

    Row(
        modifier =
            Modifier
                .heightIn(min = sizing.minTouchTarget)
                .clip(shape)
                .background(containerColor, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UiStepperStepButton(
            iconName = "remove",
            contentDescription = DECREASE_CONTENT_DESCRIPTION,
            enabled = canDecrement,
            contentColor = if (canDecrement) activeContentColor else disabledContentColor,
            onClick = onDecrement,
        )
        UiStepperAnimatedDigit(currentQuantity = currentQuantity, contentColor = activeContentColor)
        UiStepperStepButton(
            iconName = "add",
            contentDescription = INCREASE_CONTENT_DESCRIPTION,
            enabled = canIncrement,
            contentColor = if (canIncrement) activeContentColor else disabledContentColor,
            onClick = onIncrement,
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiStepperAnimatedDigit(
    currentQuantity: Int,
    contentColor: Color,
) {
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    val motion = ThemeTokens.motion

    Box(
        modifier = Modifier.widthIn(min = spacing.space24),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = currentQuantity,
            transitionSpec = {
                if (motion.reducedMotion) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    val slideUp = targetState > initialState
                    val enter =
                        slideInVertically(tween(motion.fastMs)) { fullHeight ->
                            if (slideUp) fullHeight else -fullHeight
                        } + fadeIn(tween(motion.fastMs))
                    val exit =
                        slideOutVertically(tween(motion.fastMs)) { fullHeight ->
                            if (slideUp) -fullHeight else fullHeight
                        } + fadeOut(tween(motion.fastMs))
                    (enter togetherWith exit).using(SizeTransform(clip = false))
                }
            },
            label = "stepper_quantity_digit",
        ) { qty ->
            Text(
                text = qty.toString(),
                style = typography.labelLarge,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiStepperStepButton(
    iconName: String,
    contentDescription: String,
    enabled: Boolean,
    contentColor: Color,
    onClick: () -> Unit,
) {
    val sizing = ThemeTokens.sizing
    val motion = ThemeTokens.motion
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScaleState =
        animateFloatAsState(
            targetValue = if (isPressed && enabled && !motion.reducedMotion) STEPPER_PRESS_SCALE else 1f,
            animationSpec = if (motion.reducedMotion) snap() else tween(durationMillis = motion.fastMs),
            label = "stepper_step_press_scale",
        )
    val vector = LocalIconResolver.current?.resolve(iconName) ?: UiBuiltinIcons.resolve(iconName)

    Box(
        modifier =
            Modifier
                .graphicsLayer {
                    val scale = pressScaleState.value
                    scaleX = scale
                    scaleY = scale
                }.defaultMinSize(minWidth = sizing.minTouchTarget, minHeight = sizing.minTouchTarget)
                .clickable(
                    enabled = enabled,
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    onClick = onClick,
                ).semantics(mergeDescendants = true) {
                    role = Role.Button
                    this.contentDescription = contentDescription
                },
        contentAlignment = Alignment.Center,
    ) {
        if (vector != null) {
            Icon(
                imageVector = vector,
                contentDescription = null,
                modifier = Modifier.size(STEPPER_ICON_SIZE_DP.dp),
                tint = contentColor,
            )
        } else {
            UiIcon(
                atom = IconAtom(name = iconName, sizeDp = STEPPER_ICON_SIZE_DP),
            )
        }
    }
}
