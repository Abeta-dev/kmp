package dev.abeta.kmp.atoms.atoms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonColors
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import dev.abeta.kmp.atoms.modifiers.LocalUiHapticFeedback
import dev.abeta.kmp.atoms.modifiers.UiHapticKind
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.atoms.modifiers.pressBounce
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.OptionItem
import dev.abeta.kmp.contracts.RadioGroupAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.tokens.ColorTokens
import dev.abeta.kmp.tokens.ThemeTokens

/**
 * Stateless single-choice radio group UI atom rendered from a [RadioGroupAtom] contract.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiRadioGroup(
    atom: RadioGroupAtom,
    modifier: Modifier = Modifier,
    selectedIdOverride: String? = null,
    onSelect: (String) -> Unit = {},
    onAction: (ActionRef) -> Unit = {},
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    val haptic = LocalUiHapticFeedback.current
    val activeId = selectedIdOverride ?: atom.selectedId

    Column(
        modifier =
            modifier
                .applyLayoutConfig(atom.layout)
                .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(spacing.space8),
    ) {
        atom.label?.takeIf { it.isNotBlank() }?.let { groupLabel ->
            Text(
                text = groupLabel,
                style = typography.labelLarge,
                color = if (atom.enabled) colors.text.primary else colors.text.disabled,
            )
        }
        atom.options.forEach { option ->
            val isSelected = option.id == activeId
            val isOptionEnabled = atom.enabled && option.enabled
            UiRadioOptionRow(
                fieldId = atom.fieldId,
                option = option,
                selected = isSelected,
                enabled = isOptionEnabled,
                onClick = {
                    haptic(UiHapticKind.CLICK)
                    onSelect(option.id)
                    atom.onChange?.let(onAction)
                },
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiRadioOptionRow(
    fieldId: String,
    option: OptionItem,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val cornerRadius = ThemeTokens.cornerRadius
    val strokeWidth = ThemeTokens.strokeWidth
    val sizing = ThemeTokens.sizing
    val shape = RoundedCornerShape(cornerRadius.md)
    val backgroundColor =
        if (!enabled) {
            colors.input.disabledBackground
        } else if (selected) {
            colors.surface.brandSubtle
        } else {
            colors.surface.elevated
        }
    val borderColor =
        if (!enabled) {
            colors.border.subtle
        } else if (selected) {
            colors.brand.primary
        } else {
            colors.border.default
        }
    val borderWidth = if (selected && enabled) strokeWidth.medium else strokeWidth.thin

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = sizing.minTouchTarget)
                .testTag("radio_${fieldId}_${option.id}")
                .pressBounce(enabled = enabled)
                .clip(shape)
                .background(backgroundColor, shape)
                .border(borderWidth, borderColor, shape)
                .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .padding(horizontal = spacing.space12, vertical = spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space8),
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
            colors = resolveRadioButtonColors(colors),
        )
        UiRadioOptionLabels(option = option, enabled = enabled, modifier = Modifier.weight(1f))
        option.badge?.takeIf { it.isNotBlank() }?.let { badgeLabel ->
            UiTag(atom = TagAtom(label = badgeLabel, variant = if (selected) TagVariant.BRAND else TagVariant.NEUTRAL))
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiRadioOptionLabels(
    option: OptionItem,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = ThemeTokens.colors
    val typography = ThemeTokens.typography
    val spacing = ThemeTokens.spacing
    val primaryColor = if (enabled) colors.text.primary else colors.text.disabled
    val secondaryColor = if (enabled) colors.text.secondary else colors.text.disabled

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.space2),
    ) {
        Text(
            text = option.label,
            style = typography.bodyLarge,
            color = primaryColor,
        )
        option.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
            Text(
                text = subtitle,
                style = typography.bodySmall,
                color = secondaryColor,
            )
        }
    }
}

@Composable
private fun resolveRadioButtonColors(colors: ColorTokens): RadioButtonColors =
    RadioButtonDefaults.colors(
        selectedColor = colors.brand.primary,
        unselectedColor = colors.border.strong,
        disabledSelectedColor = colors.button.disabledBackground,
        disabledUnselectedColor = colors.border.subtle,
    )
