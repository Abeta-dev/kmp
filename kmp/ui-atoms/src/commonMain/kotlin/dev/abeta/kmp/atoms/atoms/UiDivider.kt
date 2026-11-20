package dev.abeta.kmp.atoms.atoms

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.atoms.tokens.resolve
import dev.abeta.kmp.contracts.DividerAtom
import dev.abeta.kmp.tokens.ThemeTokens

/**
 * Stateless horizontal divider UI atom rendered from a [DividerAtom] contract.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiDivider(
    atom: DividerAtom,
    modifier: Modifier = Modifier,
) {
    val colors = ThemeTokens.colors
    val strokeWidth = ThemeTokens.strokeWidth

    val resolvedThickness = atom.thicknessDp?.takeIf { it > 0f }?.dp ?: strokeWidth.thin
    val resolvedColor = atom.color.resolve(fallback = colors.border.subtle)
    val dividerModifier =
        modifier
            .applyLayoutConfig(atom.layout)
            .let { base ->
                val inset = atom.insetStartDp?.takeIf { it > 0 }
                if (inset != null) base.padding(start = inset.dp) else base
            }

    HorizontalDivider(
        modifier = dividerModifier,
        thickness = resolvedThickness,
        color = resolvedColor,
    )
}
