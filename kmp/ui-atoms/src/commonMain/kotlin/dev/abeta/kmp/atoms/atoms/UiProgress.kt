package dev.abeta.kmp.atoms.atoms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.atoms.tokens.resolve
import dev.abeta.kmp.contracts.ProgressAtom
import dev.abeta.kmp.contracts.ProgressKind
import dev.abeta.kmp.tokens.ThemeTokens

private const val CIRCULAR_INDICATOR_SIZE_DP = 28

/**
 * Pure helper clamping an optional progress fraction into `[0f, 1f]`.
 */
fun clampProgress(progress: Float?): Float? = progress?.coerceIn(0f, 1f)

/**
 * Stateless linear or circular progress indicator UI atom rendered from a [ProgressAtom] contract.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiProgress(
    atom: ProgressAtom,
    modifier: Modifier = Modifier,
) {
    val colors = ThemeTokens.colors
    val indicatorColor = atom.color.resolve(fallback = colors.surface.brand)
    val trackColor = atom.trackColor.resolve(fallback = colors.border.subtle)
    val clampedProgress = clampProgress(atom.progress)
    val layoutModifier = modifier.applyLayoutConfig(atom.layout)
    val labelText = atom.label?.takeIf { it.isNotBlank() }

    when (atom.kind) {
        ProgressKind.LINEAR -> {
            UiLinearProgress(
                progress = clampedProgress,
                labelText = labelText,
                indicatorColor = indicatorColor,
                trackColor = trackColor,
                modifier = layoutModifier,
            )
        }

        ProgressKind.CIRCULAR -> {
            UiCircularProgress(
                progress = clampedProgress,
                labelText = labelText,
                indicatorColor = indicatorColor,
                trackColor = trackColor,
                modifier = layoutModifier,
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiLinearProgress(
    progress: Float?,
    labelText: String?,
    indicatorColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    val spacing = ThemeTokens.spacing
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        if (labelText != null) {
            UiProgressLabel(text = labelText)
        }
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = indicatorColor,
                trackColor = trackColor,
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = indicatorColor,
                trackColor = trackColor,
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiCircularProgress(
    progress: Float?,
    labelText: String?,
    indicatorColor: Color,
    trackColor: Color,
    modifier: Modifier = Modifier,
) {
    val spacing = ThemeTokens.spacing
    val strokeWidth = ThemeTokens.strokeWidth
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space8),
    ) {
        if (progress != null) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(CIRCULAR_INDICATOR_SIZE_DP.dp),
                color = indicatorColor,
                trackColor = trackColor,
                strokeWidth = strokeWidth.thick,
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(CIRCULAR_INDICATOR_SIZE_DP.dp),
                color = indicatorColor,
                trackColor = trackColor,
                strokeWidth = strokeWidth.thick,
            )
        }
        if (labelText != null) {
            UiProgressLabel(text = labelText)
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiProgressLabel(text: String) {
    val colors = ThemeTokens.colors
    val typography = ThemeTokens.typography
    Text(
        text = text,
        style = typography.labelSmall,
        color = colors.text.secondary,
    )
}
