package dev.abeta.kmp.atoms.atoms

import androidx.compose.ui.graphics.Color
import dev.abeta.kmp.atoms.tokens.resolveColor
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.tokens.ColorTokens

/**
 * Resolved background and foreground colors for a tag atom.
 */
data class ResolvedTagColors(
    val background: Color,
    val foreground: Color,
)

/**
 * Pure resolver computing background and text/icon colors for a tag atom from its [variant]
 * and optional explicit [bgColor] / [textColor] overrides.
 */
fun resolveTagColors(
    variant: TagVariant?,
    bgColor: ColorTokenRef?,
    textColor: ColorTokenRef?,
    colors: ColorTokens,
): ResolvedTagColors {
    val (defaultBg, defaultFg) =
        when (variant ?: TagVariant.NEUTRAL) {
            TagVariant.NEUTRAL -> colors.tag.neutralBackground to colors.tag.neutralForeground
            TagVariant.BRAND -> colors.tag.brandBackground to colors.tag.brandForeground
            TagVariant.SUCCESS -> colors.tag.successBackground to colors.tag.successForeground
            TagVariant.WARNING -> colors.tag.warningBackground to colors.tag.warningForeground
            TagVariant.ERROR -> colors.tag.errorBackground to colors.tag.errorForeground
            TagVariant.INFO -> colors.tag.infoBackground to colors.tag.infoForeground
        }
    return ResolvedTagColors(
        background = resolveColor(bgColor, colors, defaultBg),
        foreground = resolveColor(textColor, colors, defaultFg),
    )
}
