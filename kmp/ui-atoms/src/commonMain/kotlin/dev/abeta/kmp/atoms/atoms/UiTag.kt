package dev.abeta.kmp.atoms.atoms

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.atoms.modifiers.applyMotionSpec
import dev.abeta.kmp.atoms.tokens.resolveColor
import dev.abeta.kmp.atoms.tokens.resolveStrokeWidthDp
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.tokens.ThemeTokens

private const val DEFAULT_TAG_ICON_SIZE_DP = 14

/**
 * Stateless pill/badge tag UI atom rendered from a [TagAtom] contract.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiTag(
    atom: TagAtom,
    modifier: Modifier = Modifier,
    onAction: (ActionRef) -> Unit = {},
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    val cornerRadius = ThemeTokens.cornerRadius
    val strokeWidth = ThemeTokens.strokeWidth

    val resolved =
        resolveTagColors(
            variant = atom.variant,
            bgColor = atom.bgColor,
            textColor = atom.textColor,
            colors = colors,
        )
    val pillShape = RoundedCornerShape(cornerRadius.full)
    val clickAction = atom.onClick

    var combinedModifier =
        modifier
            .applyMotionSpec(atom.motion)
            .applyLayoutConfig(atom.layout)
            .clip(pillShape)
            .background(resolved.background, pillShape)

    atom.border?.let { borderSpec ->
        val borderColor = resolveColor(borderSpec.color, colors, colors.border.default)
        val borderWidth =
            resolveStrokeWidthDp(
                width = borderSpec.widthDp,
                widthToken = borderSpec.widthToken,
                tokens = strokeWidth,
                fallback = strokeWidth.thin,
            )
        combinedModifier = combinedModifier.border(borderWidth, borderColor, pillShape)
    }

    if (clickAction != null) {
        combinedModifier = combinedModifier.clickable { onAction(clickAction) }
    }
    combinedModifier = combinedModifier.padding(horizontal = spacing.space8, vertical = spacing.space4)

    Row(
        modifier = combinedModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        atom.icon?.let { icon ->
            UiIcon(
                atom =
                    icon.copy(
                        sizeDp = icon.sizeDp ?: DEFAULT_TAG_ICON_SIZE_DP,
                        color = icon.color ?: atom.textColor,
                    ),
                onAction = onAction,
            )
        }
        Text(
            text = atom.label,
            color = resolved.foreground,
            style = typography.labelSmall,
            maxLines = 1,
        )
    }
}
