package dev.abeta.kmp.storybook

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.atoms.UiIcon
import dev.abeta.kmp.atoms.atoms.UiImageRenderer
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.ImageAtom
import dev.abeta.kmp.tokens.ThemeTokens

private const val DEMO_PLACEHOLDER_HOST = "cdn.abeta.dev"
private const val ARTWORK_ICON_SIZE_DP = 24
private const val ARTWORK_BADGE_SIZE_DP = 44
private const val ARTWORK_START_ALPHA = 0.22f
private const val ARTWORK_END_ALPHA = 0.08f
private const val ARTWORK_BADGE_ALPHA = 0.85f

private data class DemoArtworkPalette(
    val start: Color,
    val end: Color,
    val iconName: String,
)

/**
 * Storybook [UiImageRenderer] that renders crisp zero-network themed Compose artwork cards for
 * offline demo URLs (`cdn.abeta.dev`) while delegating real remote URLs to [remoteFallback].
 */
internal class StorybookDemoImageRenderer(
    private val remoteFallback: UiImageRenderer,
) : UiImageRenderer {
    @Composable
    override fun render(
        atom: ImageAtom,
        modifier: Modifier,
    ) {
        if (atom.url.contains(DEMO_PLACEHOLDER_HOST, ignoreCase = true)) {
            DemoArtworkPlaceholder(atom = atom, modifier = modifier)
        } else {
            remoteFallback.render(atom = atom, modifier = modifier)
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun DemoArtworkPlaceholder(
    atom: ImageAtom,
    modifier: Modifier,
) {
    val palette = resolveArtworkPalette(atom.url)
    val colors = ThemeTokens.colors
    val typography = ThemeTokens.typography
    val spacing = ThemeTokens.spacing
    val label =
        atom.contentDescription
            ?.takeIf { it.isNotBlank() }
            ?: atom.url
                .substringAfterLast('/')
                .substringBefore('.')
                .replace('_', ' ')

    Box(
        modifier =
            modifier.background(
                Brush.linearGradient(
                    colors =
                        listOf(
                            palette.start.copy(alpha = ARTWORK_START_ALPHA),
                            palette.end.copy(alpha = ARTWORK_END_ALPHA),
                        ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(spacing.space8),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(ARTWORK_BADGE_SIZE_DP.dp)
                        .clip(CircleShape)
                        .background(colors.surface.primary.copy(alpha = ARTWORK_BADGE_ALPHA)),
                contentAlignment = Alignment.Center,
            ) {
                UiIcon(
                    atom =
                        IconAtom(
                            name = palette.iconName,
                            sizeDp = ARTWORK_ICON_SIZE_DP,
                            color = ColorTokenRef("brand.primary"),
                        ),
                )
            }
            Text(
                text = label,
                style = typography.labelSmall,
                color = colors.text.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val PRODUCE_KEYWORDS = listOf("avocado", "produce", "berr", "harvest")
private val DRINK_KEYWORDS = listOf("milk", "dairy", "brew", "drink", "yuzu", "juice")
private val BAKERY_KEYWORDS = listOf("sourdough", "bakery", "almond", "biryani", "chips")

@Composable
private fun resolveArtworkPalette(url: String): DemoArtworkPalette {
    val colors = ThemeTokens.colors
    val slug = url.lowercase()
    return when {
        "pay" in slug -> DemoArtworkPalette(colors.brand.primary, colors.status.warning, "star")
        "spark" in slug -> DemoArtworkPalette(colors.brand.primary, colors.status.warning, "person")
        PRODUCE_KEYWORDS.any { it in slug } -> DemoArtworkPalette(colors.status.success, colors.brand.primary, "star")
        DRINK_KEYWORDS.any { it in slug } -> DemoArtworkPalette(colors.status.info, colors.brand.primary, "info")
        BAKERY_KEYWORDS.any { it in slug } -> DemoArtworkPalette(colors.status.warning, colors.brand.primary, "check")
        else -> DemoArtworkPalette(colors.brand.primary, colors.status.info, "shopping_cart")
    }
}
