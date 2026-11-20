package dev.abeta.kmp.atoms.atoms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxColors
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.CheckboxAtom
import dev.abeta.kmp.tokens.ColorTokens
import dev.abeta.kmp.tokens.ThemeTokens

/**
 * Pure helper computing accessible content description for a [CheckboxAtom].
 */
fun formatCheckboxContentDescription(atom: CheckboxAtom): String =
    buildString {
        append(atom.label)
        if (atom.required) {
            append(", required")
        }
        atom.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
            append(", ")
            append(sub)
        }
    }

/**
 * Stateless checkbox selection UI atom rendered from a [CheckboxAtom] contract.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiCheckbox(
    atom: CheckboxAtom,
    modifier: Modifier = Modifier,
    checkedOverride: Boolean? = null,
    onCheckedChange: (Boolean) -> Unit = {},
    onAction: (ActionRef) -> Unit = {},
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val sizing = ThemeTokens.sizing
    val isChecked = checkedOverride ?: atom.checked
    val description = formatCheckboxContentDescription(atom)

    Row(
        modifier =
            modifier
                .applyLayoutConfig(atom.layout)
                .heightIn(min = sizing.minTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space8),
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = { updated ->
                onCheckedChange(updated)
                atom.onChange?.let(onAction)
            },
            modifier =
                Modifier
                    .defaultMinSize(minWidth = sizing.minTouchTarget, minHeight = sizing.minTouchTarget)
                    .semantics { contentDescription = description },
            enabled = atom.enabled,
            colors = resolveCheckboxColors(colors),
        )
        UiCheckboxLabels(
            atom = atom,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiCheckboxLabels(
    atom: CheckboxAtom,
    modifier: Modifier = Modifier,
) {
    val colors = ThemeTokens.colors
    val typography = ThemeTokens.typography
    val spacing = ThemeTokens.spacing
    val primaryColor = if (atom.enabled) colors.text.primary else colors.text.disabled
    val secondaryColor = if (atom.enabled) colors.text.secondary else colors.text.disabled

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.space2),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            Text(
                text = atom.label,
                style = typography.bodyLarge,
                color = primaryColor,
            )
            if (atom.required) {
                Text(
                    text = "*",
                    style = typography.bodyLarge,
                    color = colors.text.error,
                )
            }
        }
        atom.subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
            Text(
                text = sub,
                style = typography.bodySmall,
                color = secondaryColor,
            )
        }
    }
}

@Composable
private fun resolveCheckboxColors(colors: ColorTokens): CheckboxColors =
    CheckboxDefaults.colors(
        checkedColor = colors.brand.primary,
        uncheckedColor = colors.border.strong,
        checkmarkColor = colors.brand.onPrimary,
        disabledCheckedColor = colors.button.disabledBackground,
        disabledUncheckedColor = colors.border.subtle,
        disabledIndeterminateColor = colors.button.disabledBackground,
    )
