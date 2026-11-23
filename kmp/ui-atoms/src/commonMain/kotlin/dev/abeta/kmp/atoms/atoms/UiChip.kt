package dev.abeta.kmp.atoms.atoms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.modifiers.LocalUiHapticFeedback
import dev.abeta.kmp.atoms.modifiers.UiHapticKind
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.atoms.modifiers.pressBounce
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ChipAtom
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.tokens.ColorTokens
import dev.abeta.kmp.tokens.ThemeTokens

private const val CHIP_MIN_HEIGHT_DP = 36
private const val CHIP_LEADING_ICON_SIZE_DP = 16
private const val CHIP_REMOVE_TOUCH_DP = 24
private const val CHIP_REMOVE_ICON_SIZE_DP = 14
private const val REMOVE_BG_ALPHA = 0.12f

private data class ResolvedChipColors(
    val background: Color,
    val border: Color,
    val content: Color,
    val iconColorToken: String,
)

/**
 * Stateless standalone interactive pill chip UI atom rendered from a [ChipAtom] contract.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
fun UiChip(
    atom: ChipAtom,
    modifier: Modifier = Modifier,
    selectedOverride: Boolean? = null,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    onAction: (ActionRef) -> Unit = {},
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    val haptic = LocalUiHapticFeedback.current

    val isSelected = selectedOverride ?: atom.selected
    val palette = resolveChipColors(selected = isSelected, enabled = atom.enabled, colors = colors)
    val chipModifier =
        resolveChipModifier(
            atom = atom,
            modifier = modifier,
            palette = palette,
            isSelected = isSelected,
            onClick = onClick,
            onAction = onAction,
        )

    Row(
        modifier = chipModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space8),
    ) {
        atom.leadingIcon?.let { icon ->
            UiIcon(
                atom =
                    icon.copy(
                        sizeDp = icon.sizeDp ?: CHIP_LEADING_ICON_SIZE_DP,
                        color = icon.color ?: ColorTokenRef(token = palette.iconColorToken),
                    ),
            )
        }
        Text(
            text = atom.label,
            style = typography.labelLarge,
            color = palette.content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (atom.removable) {
            UiChipRemoveButton(
                atom = atom,
                palette = palette,
                onRemove = {
                    haptic(UiHapticKind.TICK)
                    onRemove?.invoke()
                    atom.onRemove?.let(onAction)
                },
            )
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun resolveChipModifier(
    atom: ChipAtom,
    modifier: Modifier,
    palette: ResolvedChipColors,
    isSelected: Boolean,
    onClick: (() -> Unit)?,
    onAction: (ActionRef) -> Unit,
): Modifier {
    val spacing = ThemeTokens.spacing
    val cornerRadius = ThemeTokens.cornerRadius
    val strokeWidth = ThemeTokens.strokeWidth
    val haptic = LocalUiHapticFeedback.current
    val pillShape = RoundedCornerShape(cornerRadius.full)
    val hasClickHandler = onClick != null || atom.onClick != null

    return modifier
        .applyLayoutConfig(atom.layout)
        .testTag("chip_${atom.id}")
        .heightIn(min = CHIP_MIN_HEIGHT_DP.dp)
        .pressBounce(enabled = atom.enabled && hasClickHandler)
        .clip(pillShape)
        .background(palette.background, pillShape)
        .border(strokeWidth.thin, palette.border, pillShape)
        .semantics { selected = isSelected }
        .let { base ->
            if (hasClickHandler) {
                base.clickable(enabled = atom.enabled, role = Role.Button) {
                    haptic(UiHapticKind.CLICK)
                    onClick?.invoke()
                    atom.onClick?.let(onAction)
                }
            } else {
                base
            }
        }.padding(
            start = spacing.space12,
            end = if (atom.removable) spacing.space8 else spacing.space12,
            top = spacing.space4,
            bottom = spacing.space4,
        )
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiChipRemoveButton(
    atom: ChipAtom,
    palette: ResolvedChipColors,
    onRemove: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(CHIP_REMOVE_TOUCH_DP.dp)
                .testTag("chip_remove_${atom.id}")
                .clip(CircleShape)
                .background(palette.content.copy(alpha = REMOVE_BG_ALPHA), CircleShape)
                .semantics { contentDescription = "Remove ${atom.label}" }
                .clickable(
                    enabled = atom.enabled,
                    role = Role.Button,
                    onClick = onRemove,
                ),
        contentAlignment = Alignment.Center,
    ) {
        UiIcon(
            atom =
                IconAtom(
                    name = "close",
                    sizeDp = CHIP_REMOVE_ICON_SIZE_DP,
                    color = ColorTokenRef(token = palette.iconColorToken),
                ),
        )
    }
}

private fun resolveChipColors(
    selected: Boolean,
    enabled: Boolean,
    colors: ColorTokens,
): ResolvedChipColors =
    when {
        !enabled -> {
            ResolvedChipColors(
                background = colors.input.disabledBackground,
                border = colors.border.subtle,
                content = colors.text.disabled,
                iconColorToken = "icon.disabled",
            )
        }

        selected -> {
            ResolvedChipColors(
                background = colors.brand.container,
                border = colors.brand.primary,
                content = colors.brand.onContainer,
                iconColorToken = "icon.brand",
            )
        }

        else -> {
            ResolvedChipColors(
                background = colors.surface.elevated,
                border = colors.border.default,
                content = colors.text.primary,
                iconColorToken = "icon.secondary",
            )
        }
    }
