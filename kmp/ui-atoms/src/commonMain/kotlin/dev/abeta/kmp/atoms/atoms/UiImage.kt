package dev.abeta.kmp.atoms.atoms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.atoms.tokens.resolve
import dev.abeta.kmp.atoms.tokens.resolveCornerRadii
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ImageAtom
import dev.abeta.kmp.contracts.ImageScaleMode
import dev.abeta.kmp.tokens.ThemeTokens

/**
 * Pure mapper from [ImageScaleMode] wire enum to Compose [ContentScale].
 */
fun resolveContentScale(mode: ImageScaleMode): ContentScale =
    when (mode) {
        ImageScaleMode.FIT -> ContentScale.Fit
        ImageScaleMode.FILL_BOUNDS -> ContentScale.FillBounds
        ImageScaleMode.CROP -> ContentScale.Crop
        ImageScaleMode.INSIDE -> ContentScale.Inside
    }

/**
 * Stateless image UI atom rendered from an [ImageAtom] contract.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiImage(
    atom: ImageAtom,
    modifier: Modifier = Modifier,
    onAction: (ActionRef) -> Unit = {},
) {
    val colors = ThemeTokens.colors
    val typography = ThemeTokens.typography
    val cornerTokens = ThemeTokens.cornerRadius
    val placeholder = atom.placeholderColor.resolve(fallback = colors.surface.secondary)
    val clickAction = atom.onClick

    var sizedModifier = modifier.applyLayoutConfig(atom.layout)
    atom.widthDp?.takeIf { it > 0 }?.let { w ->
        sizedModifier = sizedModifier.width(w.dp)
    }
    atom.heightDp?.takeIf { it > 0 }?.let { h ->
        sizedModifier = sizedModifier.height(h.dp)
    }
    atom.aspectRatio?.takeIf { it > 0f }?.let { ratio ->
        sizedModifier = sizedModifier.aspectRatio(ratio)
    }
    resolveCornerRadii(atom.cornerRadius, cornerTokens)?.toRoundedCornerShape()?.let { shape ->
        sizedModifier = sizedModifier.clip(shape)
    }
    if (clickAction != null) {
        sizedModifier = sizedModifier.clickable { onAction(clickAction) }
    }

    val renderer = LocalImageRenderer.current
    if (renderer != null) {
        renderer.render(atom = atom, modifier = sizedModifier)
    } else {
        Box(
            modifier = sizedModifier.background(placeholder),
            contentAlignment = Alignment.Center,
        ) {
            val label = atom.contentDescription?.takeIf { it.isNotBlank() }
            if (label != null) {
                Text(
                    text = label,
                    style = typography.labelSmall,
                    color = colors.text.tertiary,
                    maxLines = 1,
                )
            }
        }
    }
}
