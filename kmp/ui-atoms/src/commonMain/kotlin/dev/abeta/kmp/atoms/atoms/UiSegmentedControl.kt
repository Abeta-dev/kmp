package dev.abeta.kmp.atoms.atoms

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.modifiers.LocalUiHapticFeedback
import dev.abeta.kmp.atoms.modifiers.UiHapticKind
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.OptionItem
import dev.abeta.kmp.contracts.SegmentedControlAtom
import dev.abeta.kmp.tokens.ThemeTokens

private const val SEGMENT_MIN_HEIGHT_DP = 36
private const val HALF_DIVISOR = 2f

/**
 * Stateless pill-track segmented control UI atom rendered from a [SegmentedControlAtom] contract.
 *
 * Animates the active sliding thumb purely inside `drawBehind` (zero per-frame recompositions)
 * and snaps immediately under [dev.abeta.kmp.tokens.MotionTokens.reducedMotion].
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiSegmentedControl(
    atom: SegmentedControlAtom,
    modifier: Modifier = Modifier,
    selectedIdOverride: String? = null,
    onSelect: (String) -> Unit = {},
    onAction: (ActionRef) -> Unit = {},
) {
    if (atom.options.isEmpty()) return

    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val cornerRadius = ThemeTokens.cornerRadius
    val strokeWidth = ThemeTokens.strokeWidth
    val haptic = LocalUiHapticFeedback.current

    val activeId = selectedIdOverride ?: atom.selectedId ?: atom.options.first().id
    val selectedIndex = atom.options.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
    val animatedIndexState = rememberAnimatedSegmentIndex(selectedIndex)
    val trackShape = RoundedCornerShape(cornerRadius.full)
    val thumbColor = if (atom.enabled) colors.surface.elevated else colors.input.disabledBackground
    val thumbBorderColor = if (atom.enabled) colors.border.default else colors.border.subtle

    Box(
        modifier =
            modifier
                .applyLayoutConfig(atom.layout)
                .fillMaxWidth()
                .clip(trackShape)
                .background(colors.surface.secondary, trackShape)
                .border(strokeWidth.thin, colors.border.subtle, trackShape)
                .padding(spacing.space4),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .selectableGroup()
                    .drawSegmentedThumb(
                        optionCount = atom.options.size,
                        animatedIndexState = animatedIndexState,
                        thumbColor = thumbColor,
                        thumbBorderColor = thumbBorderColor,
                        borderStrokeWidth = strokeWidth.thin,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            atom.options.forEach { option ->
                UiSegmentCell(
                    fieldId = atom.fieldId,
                    option = option,
                    selected = option.id == activeId,
                    enabled = atom.enabled && option.enabled,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic(UiHapticKind.CLICK)
                        onSelect(option.id)
                        atom.onChange?.let(onAction)
                    },
                )
            }
        }
    }
}

@Composable
private fun rememberAnimatedSegmentIndex(selectedIndex: Int): State<Float> {
    val motion = ThemeTokens.motion
    return animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec =
            if (motion.reducedMotion) {
                snap()
            } else {
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                )
            },
        label = "segmented_control_thumb",
    )
}

private fun Modifier.drawSegmentedThumb(
    optionCount: Int,
    animatedIndexState: State<Float>,
    thumbColor: Color,
    thumbBorderColor: Color,
    borderStrokeWidth: Dp,
): Modifier =
    drawBehind {
        val safeCount = optionCount.coerceAtLeast(1)
        val slotWidth = size.width / safeCount
        val maxIndex = (safeCount - 1).toFloat()
        val offsetX = animatedIndexState.value.coerceIn(0f, maxIndex) * slotWidth
        val pillRadius = size.height / HALF_DIVISOR
        val corner = CornerRadius(pillRadius, pillRadius)
        val thumbSize = Size(width = slotWidth, height = size.height)
        val topLeft = Offset(x = offsetX, y = 0f)
        drawRoundRect(
            color = thumbColor,
            topLeft = topLeft,
            size = thumbSize,
            cornerRadius = corner,
        )
        drawRoundRect(
            color = thumbBorderColor,
            topLeft = topLeft,
            size = thumbSize,
            cornerRadius = corner,
            style = Stroke(width = borderStrokeWidth.toPx()),
        )
    }

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun UiSegmentCell(
    fieldId: String,
    option: OptionItem,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    val cornerRadius = ThemeTokens.cornerRadius
    val pillShape = RoundedCornerShape(cornerRadius.full)
    val textColor =
        when {
            !enabled -> colors.text.disabled
            selected -> colors.text.primary
            else -> colors.text.secondary
        }
    val textStyle =
        if (selected) {
            typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
        } else {
            typography.labelMedium
        }

    Box(
        modifier =
            modifier
                .heightIn(min = SEGMENT_MIN_HEIGHT_DP.dp)
                .testTag("segment_${fieldId}_${option.id}")
                .clip(pillShape)
                .selectable(
                    selected = selected,
                    enabled = enabled,
                    role = Role.Tab,
                    onClick = onClick,
                ).padding(horizontal = spacing.space8, vertical = spacing.space8),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = option.label,
            style = textStyle,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
