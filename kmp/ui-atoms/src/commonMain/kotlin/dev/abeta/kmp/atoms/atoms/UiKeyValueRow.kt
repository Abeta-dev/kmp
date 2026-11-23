package dev.abeta.kmp.atoms.atoms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.abeta.kmp.atoms.modifiers.LocalUiHapticFeedback
import dev.abeta.kmp.atoms.modifiers.UiHapticKind
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.atoms.modifiers.pressBounce
import dev.abeta.kmp.atoms.tokens.resolve
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.DividerAtom
import dev.abeta.kmp.contracts.KeyValueRowAtom
import dev.abeta.kmp.tokens.ThemeTokens

private const val TABULAR_NUMBERS_FEATURE = "tnum"

/**
 * Stateless receipt / bill / specification key-value row UI atom rendered from a [KeyValueRowAtom] contract.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiKeyValueRow(
    atom: KeyValueRowAtom,
    modifier: Modifier = Modifier,
    onAction: (ActionRef) -> Unit = {},
) {
    val spacing = ThemeTokens.spacing
    val haptic = LocalUiHapticFeedback.current
    val clickAction = atom.onClick
    val rowModifier =
        Modifier
            .fillMaxWidth()
            .let { base ->
                if (clickAction != null) {
                    base
                        .pressBounce()
                        .clickable(role = Role.Button) {
                            haptic(UiHapticKind.CLICK)
                            onAction(clickAction)
                        }
                } else {
                    base
                }
            }.padding(vertical = spacing.space8)

    Column(
        modifier =
            modifier
                .applyLayoutConfig(atom.layout)
                .testTag("kv_row_${atom.id ?: "default"}"),
    ) {
        UiKeyValueContentRow(atom = atom, modifier = rowModifier)
        if (atom.showDivider) {
            UiDivider(atom = DividerAtom())
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiKeyValueContentRow(
    atom: KeyValueRowAtom,
    modifier: Modifier,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    val labelStyle =
        if (atom.emphasized) {
            typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
        } else {
            typography.bodyMedium
        }
    val valueStyle =
        if (atom.emphasized) {
            typography.titleSmall.copy(
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = TABULAR_NUMBERS_FEATURE,
            )
        } else {
            typography.bodyMedium.copy(
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = TABULAR_NUMBERS_FEATURE,
            )
        }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = spacing.space12),
            verticalArrangement = Arrangement.spacedBy(spacing.space2),
        ) {
            Text(
                text = atom.label,
                style = labelStyle,
                color = if (atom.emphasized) colors.text.primary else colors.text.secondary,
            )
            atom.sublabel?.takeIf { it.isNotBlank() }?.let { sub ->
                Text(
                    text = sub,
                    style = typography.caption,
                    color = colors.text.tertiary,
                )
            }
        }
        Text(
            text = atom.value,
            style = valueStyle,
            color = atom.valueColor.resolve(fallback = colors.text.primary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
