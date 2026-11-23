package dev.abeta.kmp.atoms.atoms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.contracts.PriceAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.tokens.ThemeTokens

private const val TABULAR_NUMBERS_FEATURE = "tnum"

/**
 * Stateless commerce price UI atom rendered from a [PriceAtom] contract.
 *
 * Displays tabular sale price with optional unit suffix, strikethrough original MRP,
 * and semantic discount pill.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiPrice(
    atom: PriceAtom,
    modifier: Modifier = Modifier,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography

    Row(
        modifier =
            modifier
                .applyLayoutConfig(atom.layout)
                .testTag("price_${atom.id ?: "default"}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space8),
    ) {
        UiPriceAmountPair(atom = atom)
        atom.originalAmountText?.takeIf { it.isNotBlank() }?.let { originalPrice ->
            Text(
                text = originalPrice,
                style = typography.bodySmall.copy(fontFeatureSettings = TABULAR_NUMBERS_FEATURE),
                color = colors.text.tertiary,
                textDecoration = TextDecoration.LineThrough,
                maxLines = 1,
            )
        }
        atom.discountLabel?.takeIf { it.isNotBlank() }?.let { discount ->
            UiTag(atom = TagAtom(label = discount, variant = TagVariant.SUCCESS))
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiPriceAmountPair(atom: PriceAtom) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    val priceStyle =
        if (atom.emphasized) {
            typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = TABULAR_NUMBERS_FEATURE,
            )
        } else {
            typography.titleSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontFeatureSettings = TABULAR_NUMBERS_FEATURE,
            )
        }

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(spacing.space2),
    ) {
        Text(
            text = atom.amountText,
            style = priceStyle,
            color = colors.text.primary,
            maxLines = 1,
        )
        atom.unitSuffix?.takeIf { it.isNotBlank() }?.let { unit ->
            Text(
                text = unit,
                style = typography.bodySmall,
                color = colors.text.secondary,
                maxLines = 1,
            )
        }
    }
}
