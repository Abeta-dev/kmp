package dev.abeta.kmp.atoms.atoms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.SwitchAtom
import dev.abeta.kmp.tokens.ColorTokens
import dev.abeta.kmp.tokens.ThemeTokens

/**
 * Pure helper computing accessible content description for a [SwitchAtom].
 */
fun formatSwitchContentDescription(atom: SwitchAtom): String {
    val subtitle = atom.subtitle?.takeIf { it.isNotBlank() }
    return if (subtitle != null) "${atom.label}, $subtitle" else atom.label
}

/**
 * Stateless toggle switch UI atom rendered from a [SwitchAtom] contract.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiSwitch(
    atom: SwitchAtom,
    modifier: Modifier = Modifier,
    checkedOverride: Boolean? = null,
    onCheckedChange: (Boolean) -> Unit = {},
    onAction: (ActionRef) -> Unit = {},
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val sizing = ThemeTokens.sizing
    val isChecked = checkedOverride ?: atom.checked
    val description = formatSwitchContentDescription(atom)

    Row(
        modifier =
            modifier
                .applyLayoutConfig(atom.layout)
                .heightIn(min = sizing.minTouchTarget),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space12),
    ) {
        UiSwitchLabels(
            label = atom.label,
            subtitle = atom.subtitle,
            enabled = atom.enabled,
            modifier = Modifier.weight(1f),
        )
        Switch(
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
            colors = resolveSwitchColors(colors),
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiSwitchLabels(
    label: String,
    subtitle: String?,
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
            text = label,
            style = typography.bodyLarge,
            color = primaryColor,
        )
        subtitle?.takeIf { it.isNotBlank() }?.let { sub ->
            Text(
                text = sub,
                style = typography.bodySmall,
                color = secondaryColor,
            )
        }
    }
}

@Composable
private fun resolveSwitchColors(colors: ColorTokens): SwitchColors =
    SwitchDefaults.colors(
        checkedThumbColor = colors.brand.onPrimary,
        checkedTrackColor = colors.brand.primary,
        uncheckedThumbColor = colors.text.secondary,
        uncheckedTrackColor = colors.surface.secondary,
        uncheckedBorderColor = colors.border.default,
        disabledCheckedThumbColor = colors.button.disabledForeground,
        disabledCheckedTrackColor = colors.button.disabledBackground,
        disabledUncheckedThumbColor = colors.text.disabled,
        disabledUncheckedTrackColor = colors.input.disabledBackground,
        disabledUncheckedBorderColor = colors.border.subtle,
    )
