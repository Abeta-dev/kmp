package dev.abeta.kmp.atoms.atoms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.atoms.tokens.resolve
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.AlignmentToken
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.tokens.ThemeTokens

private const val DEFAULT_INLINE_ICON_SIZE_DP = 16

/**
 * Pure helper mapping [AlignmentToken] to Compose [TextAlign].
 */
fun AlignmentToken?.toTextAlign(): TextAlign? =
    when (this) {
        AlignmentToken.START -> TextAlign.Start
        AlignmentToken.CENTER -> TextAlign.Center
        AlignmentToken.END -> TextAlign.End
        null -> null
    }

/**
 * Pure helper computing [TextDecoration] from [strikeThrough] and [underline] flags.
 */
fun resolveTextDecoration(
    strikeThrough: Boolean,
    underline: Boolean,
): TextDecoration =
    when {
        strikeThrough && underline -> {
            TextDecoration.combine(
                listOf(TextDecoration.LineThrough, TextDecoration.Underline),
            )
        }

        strikeThrough -> {
            TextDecoration.LineThrough
        }

        underline -> {
            TextDecoration.Underline
        }

        else -> {
            TextDecoration.None
        }
    }

/**
 * Stateless text UI atom rendered from a [TextAtom] contract.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiText(
    atom: TextAtom,
    modifier: Modifier = Modifier,
    onAction: (ActionRef) -> Unit = {},
) {
    val colors = ThemeTokens.colors
    val textColor = atom.color.resolve(fallback = colors.text.primary)
    val style = atom.typography.resolve()
    val combinedModifier = resolveTextModifier(atom = atom, modifier = modifier, onAction = onAction)
    val decoration =
        resolveTextDecoration(
            strikeThrough = atom.strikeThrough,
            underline = atom.underline,
        )
    val textAlign = atom.alignment.toTextAlign()
    val maxLines = atom.maxLines ?: Int.MAX_VALUE

    if (atom.prefixIcon == null && atom.suffixIcon == null) {
        UiDecoratedText(
            text = atom.text,
            color = textColor,
            style = style,
            textAlign = textAlign,
            decoration = decoration,
            maxLines = maxLines,
            modifier = combinedModifier,
        )
    } else {
        UiInlineIconText(
            atom = atom,
            color = textColor,
            style = style,
            textAlign = textAlign,
            decoration = decoration,
            maxLines = maxLines,
            modifier = combinedModifier,
            onAction = onAction,
        )
    }
}

@Composable
private fun resolveTextModifier(
    atom: TextAtom,
    modifier: Modifier,
    onAction: (ActionRef) -> Unit,
): Modifier {
    val clickAction = atom.onClick
    return modifier
        .applyLayoutConfig(atom.layout)
        .let { base ->
            if (clickAction != null) {
                base.clickable { onAction(clickAction) }
            } else {
                base
            }
        }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiInlineIconText(
    atom: TextAtom,
    color: Color,
    style: TextStyle,
    textAlign: TextAlign?,
    decoration: TextDecoration,
    maxLines: Int,
    modifier: Modifier = Modifier,
    onAction: (ActionRef) -> Unit = {},
) {
    val spacing = ThemeTokens.spacing
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        UiInlineIcon(
            icon = atom.prefixIcon,
            fallbackColor = atom.color,
            onAction = onAction,
        )
        UiDecoratedText(
            text = atom.text,
            color = color,
            style = style,
            textAlign = textAlign,
            decoration = decoration,
            maxLines = maxLines,
            modifier = Modifier.weight(1f, fill = false),
        )
        UiInlineIcon(
            icon = atom.suffixIcon,
            fallbackColor = atom.color,
            onAction = onAction,
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiInlineIcon(
    icon: IconAtom?,
    fallbackColor: ColorTokenRef?,
    onAction: (ActionRef) -> Unit,
) {
    icon?.let {
        UiIcon(
            atom =
                it.copy(
                    sizeDp = it.sizeDp ?: DEFAULT_INLINE_ICON_SIZE_DP,
                    color = it.color ?: fallbackColor,
                ),
            onAction = onAction,
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiDecoratedText(
    text: String,
    color: Color,
    style: TextStyle,
    textAlign: TextAlign?,
    decoration: TextDecoration,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style,
        textAlign = textAlign,
        textDecoration = decoration,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
