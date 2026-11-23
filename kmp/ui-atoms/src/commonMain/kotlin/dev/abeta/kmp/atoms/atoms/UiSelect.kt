package dev.abeta.kmp.atoms.atoms

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import dev.abeta.kmp.atoms.modifiers.LocalUiHapticFeedback
import dev.abeta.kmp.atoms.modifiers.UiHapticKind
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.atoms.modifiers.pressBounce
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.OptionItem
import dev.abeta.kmp.contracts.SelectAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.tokens.ColorTokens
import dev.abeta.kmp.tokens.ThemeTokens

private const val CHEVRON_ICON_SIZE_DP = 18
private const val CHECK_ICON_SIZE_DP = 16
private const val CHEVRON_EXPANDED_DEGREES = 180f
private const val CHEVRON_COLLAPSED_DEGREES = 0f

/**
 * Stateless dropdown select UI atom rendered from a [SelectAtom] contract.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiSelect(
    atom: SelectAtom,
    modifier: Modifier = Modifier,
    selectedIdOverride: String? = null,
    onSelect: (String) -> Unit = {},
    onAction: (ActionRef) -> Unit = {},
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    val motion = ThemeTokens.motion
    val haptic = LocalUiHapticFeedback.current

    var expanded by remember(atom.fieldId) { mutableStateOf(false) }
    val activeId = selectedIdOverride ?: atom.selectedId
    val selectedOption = atom.options.firstOrNull { it.id == activeId }
    val isError = !atom.errorText.isNullOrBlank()
    val supportingMessage = atom.errorText?.takeIf { it.isNotBlank() } ?: atom.helperText?.takeIf { it.isNotBlank() }
    val chevronRotationState =
        animateFloatAsState(
            targetValue = if (expanded) CHEVRON_EXPANDED_DEGREES else CHEVRON_COLLAPSED_DEGREES,
            animationSpec = if (motion.reducedMotion) snap() else tween(durationMillis = motion.fastMs),
            label = "select_chevron_rotation",
        )

    Column(
        modifier = modifier.applyLayoutConfig(atom.layout),
        verticalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        UiSelectLabel(label = atom.label, enabled = atom.enabled, expanded = expanded, isError = isError)
        Box(modifier = Modifier.fillMaxWidth()) {
            UiSelectTrigger(
                atom = atom,
                selectedOption = selectedOption,
                expanded = expanded,
                isError = isError,
                chevronRotationState = chevronRotationState,
                onClick = {
                    haptic(UiHapticKind.CLICK)
                    expanded = !expanded
                },
            )
            UiSelectDropdown(
                atom = atom,
                activeId = activeId,
                expanded = expanded,
                onDismiss = { expanded = false },
                onOptionSelected = { optionId ->
                    expanded = false
                    haptic(UiHapticKind.CLICK)
                    onSelect(optionId)
                    atom.onChange?.let(onAction)
                },
            )
        }
        UiSelectSupportingText(message = supportingMessage, isError = isError)
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiSelectLabel(
    label: String?,
    enabled: Boolean,
    expanded: Boolean,
    isError: Boolean,
) {
    val fieldLabel = label?.takeIf { it.isNotBlank() } ?: return
    val colors = ThemeTokens.colors
    val typography = ThemeTokens.typography
    val labelColor =
        when {
            !enabled -> colors.text.disabled
            isError -> colors.text.error
            expanded -> colors.text.brand
            else -> colors.text.secondary
        }
    Text(
        text = fieldLabel,
        style = typography.labelMedium,
        color = labelColor,
    )
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiSelectSupportingText(
    message: String?,
    isError: Boolean,
) {
    val text = message?.takeIf { it.isNotBlank() } ?: return
    val colors = ThemeTokens.colors
    val typography = ThemeTokens.typography
    Text(
        text = text,
        style = typography.labelSmall,
        color = if (isError) colors.text.error else colors.text.tertiary,
    )
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun UiSelectTrigger(
    atom: SelectAtom,
    selectedOption: OptionItem?,
    expanded: Boolean,
    isError: Boolean,
    chevronRotationState: State<Float>,
    onClick: () -> Unit,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    val cornerRadius = ThemeTokens.cornerRadius
    val strokeWidth = ThemeTokens.strokeWidth
    val sizing = ThemeTokens.sizing
    val shape = RoundedCornerShape(cornerRadius.sm)
    val bgColor = if (atom.enabled) colors.input.background else colors.input.disabledBackground
    val borderColor = resolveSelectBorderColor(atom.enabled, isError, expanded, colors)
    val borderWidth = if (expanded || isError) strokeWidth.medium else strokeWidth.thin
    val textColor = resolveSelectTextColor(atom.enabled, selectedOption != null, colors)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = sizing.inputMinHeight)
                .testTag("select_${atom.fieldId}")
                .pressBounce(enabled = atom.enabled)
                .clip(shape)
                .background(bgColor, shape)
                .border(borderWidth, borderColor, shape)
                .clickable(enabled = atom.enabled, role = Role.DropdownList, onClick = onClick)
                .padding(horizontal = spacing.space12, vertical = spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space8),
    ) {
        Text(
            text = selectedOption?.label ?: atom.placeholder,
            style = typography.bodyLarge,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        UiIcon(
            atom =
                IconAtom(
                    name = "chevron_down",
                    sizeDp = CHEVRON_ICON_SIZE_DP,
                    color = ColorTokenRef(token = if (atom.enabled) "icon.secondary" else "icon.disabled"),
                ),
            modifier = Modifier.graphicsLayer { rotationZ = chevronRotationState.value },
        )
    }
}

private fun resolveSelectBorderColor(
    enabled: Boolean,
    isError: Boolean,
    expanded: Boolean,
    colors: ColorTokens,
) = when {
    !enabled -> colors.border.subtle
    isError -> colors.input.errorBorder
    expanded -> colors.input.focusBorder
    else -> colors.input.border
}

private fun resolveSelectTextColor(
    enabled: Boolean,
    hasSelection: Boolean,
    colors: ColorTokens,
) = when {
    !enabled -> colors.text.disabled
    hasSelection -> colors.input.foreground
    else -> colors.input.placeholder
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiSelectDropdown(
    atom: SelectAtom,
    activeId: String?,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onOptionSelected: (String) -> Unit,
) {
    val colors = ThemeTokens.colors
    val typography = ThemeTokens.typography
    val spacing = ThemeTokens.spacing

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = colors.surface.elevated,
    ) {
        atom.options.forEach { option ->
            val isSelected = option.id == activeId
            val optionEnabled = atom.enabled && option.enabled
            DropdownMenuItem(
                modifier = Modifier.testTag("select_option_${atom.fieldId}_${option.id}"),
                enabled = optionEnabled,
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.space2)) {
                        Text(
                            text = option.label,
                            style = typography.bodyMedium,
                            color =
                                when {
                                    !optionEnabled -> colors.text.disabled
                                    isSelected -> colors.text.brand
                                    else -> colors.text.primary
                                },
                        )
                        option.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                            Text(
                                text = subtitle,
                                style = typography.caption,
                                color = if (optionEnabled) colors.text.secondary else colors.text.disabled,
                            )
                        }
                    }
                },
                trailingIcon = {
                    UiSelectOptionTrailing(option = option, selected = isSelected)
                },
                onClick = { onOptionSelected(option.id) },
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiSelectOptionTrailing(
    option: OptionItem,
    selected: Boolean,
) {
    val spacing = ThemeTokens.spacing
    val badge = option.badge?.takeIf { it.isNotBlank() }
    if (badge == null && !selected) return

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        if (badge != null) {
            UiTag(
                atom =
                    TagAtom(
                        label = badge,
                        variant = if (selected) TagVariant.BRAND else TagVariant.NEUTRAL,
                    ),
            )
        }
        if (selected) {
            UiIcon(
                atom =
                    IconAtom(
                        name = "check",
                        sizeDp = CHECK_ICON_SIZE_DP,
                        color = ColorTokenRef(token = "icon.brand"),
                    ),
            )
        }
    }
}
