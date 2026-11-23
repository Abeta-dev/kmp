package dev.abeta.kmp.atoms.atoms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.abeta.kmp.atoms.modifiers.LocalUiHapticFeedback
import dev.abeta.kmp.atoms.modifiers.UiHapticKind
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.StatTileAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.tokens.ThemeTokens

private const val STAT_ICON_SIZE_DP = 18
private const val CAPTION_MAX_LINES = 2
private const val TREND_UP_PREFIX = "▲ "
private const val TREND_DOWN_PREFIX = "▼ "
private const val TABULAR_NUMBERS_FEATURE = "tnum"

/**
 * Pure helper formatting a stat delta string with a directional trend arrow when [deltaPositive] is specified.
 */
fun formatStatDelta(
    deltaLabel: String,
    deltaPositive: Boolean?,
): String =
    when (deltaPositive) {
        true -> "$TREND_UP_PREFIX$deltaLabel"
        false -> "$TREND_DOWN_PREFIX$deltaLabel"
        null -> deltaLabel
    }

/**
 * Stateless compact KPI / metric summary tile UI atom rendered from a [StatTileAtom] contract.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiStatTile(
    atom: StatTileAtom,
    modifier: Modifier = Modifier,
    onAction: (ActionRef) -> Unit = {},
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    val haptic = LocalUiHapticFeedback.current
    val clickAction = atom.onClick

    UiSurfaceCard(
        modifier = modifier.testTag("stat_tile_${atom.id ?: "default"}"),
        layout = atom.layout,
        onClick =
            clickAction?.let { action ->
                {
                    haptic(UiHapticKind.CLICK)
                    onAction(action)
                }
            },
    ) {
        UiStatTileHeader(atom = atom)
        Spacer(modifier = Modifier.height(spacing.space8))
        UiStatTileMetricRow(atom = atom)
        atom.caption?.takeIf { it.isNotBlank() }?.let { captionText ->
            Spacer(modifier = Modifier.height(spacing.space4))
            Text(
                text = captionText,
                style = typography.caption,
                color = colors.text.tertiary,
                maxLines = CAPTION_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiStatTileHeader(atom: StatTileAtom) {
    val colors = ThemeTokens.colors
    val typography = ThemeTokens.typography
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = atom.label,
            style = typography.labelMedium,
            color = colors.text.secondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        atom.icon?.let { icon ->
            UiIcon(atom = icon.copy(sizeDp = icon.sizeDp ?: STAT_ICON_SIZE_DP))
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiStatTileMetricRow(atom: StatTileAtom) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space8),
    ) {
        Text(
            text = atom.value,
            style =
                typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontFeatureSettings = TABULAR_NUMBERS_FEATURE,
                ),
            color = colors.text.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        atom.deltaLabel?.takeIf { it.isNotBlank() }?.let { delta ->
            val deltaVariant =
                when (atom.deltaPositive) {
                    true -> TagVariant.SUCCESS
                    false -> TagVariant.ERROR
                    null -> TagVariant.NEUTRAL
                }
            UiTag(
                atom =
                    TagAtom(
                        label = formatStatDelta(delta, atom.deltaPositive),
                        variant = deltaVariant,
                    ),
            )
        }
    }
}
