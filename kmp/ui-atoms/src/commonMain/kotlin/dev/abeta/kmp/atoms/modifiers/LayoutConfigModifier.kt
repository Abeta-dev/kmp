package dev.abeta.kmp.atoms.modifiers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.tokens.resolveColor
import dev.abeta.kmp.atoms.tokens.resolveCornerRadii
import dev.abeta.kmp.atoms.tokens.resolveStrokeWidthDp
import dev.abeta.kmp.contracts.LayoutConfig
import dev.abeta.kmp.contracts.SpacingValues
import dev.abeta.kmp.tokens.ThemeTokens

/**
 * Converts a [SpacingValues] wire contract into Compose [PaddingValues].
 */
fun SpacingValues.toPaddingValues(): PaddingValues {
    val base = all ?: 0
    val horizontalFallback = horizontal ?: base
    val verticalFallback = vertical ?: base
    return PaddingValues(
        start = (start ?: horizontalFallback).coerceAtLeast(0).dp,
        top = (top ?: verticalFallback).coerceAtLeast(0).dp,
        end = (end ?: horizontalFallback).coerceAtLeast(0).dp,
        bottom = (bottom ?: verticalFallback).coerceAtLeast(0).dp,
    )
}

/**
 * Applies SDUI [LayoutConfig] (width fill, outer margin, shape clipping, background color,
 * border stroke, and inner padding) in standard box-model order.
 */
@Composable
fun Modifier.applyLayoutConfig(config: LayoutConfig?): Modifier {
    if (config == null) return this

    val colors = ThemeTokens.colors
    val cornerTokens = ThemeTokens.cornerRadius
    val strokeTokens = ThemeTokens.strokeWidth

    var result = this
    if (config.fillMaxWidth) {
        result = result.fillMaxWidth()
    }
    config.margin?.let { margin ->
        result = result.padding(margin.toPaddingValues())
    }

    val shape = resolveCornerRadii(config.cornerRadius, cornerTokens)?.toRoundedCornerShape()
    if (shape != null) {
        result = result.clip(shape)
    }

    config.bgColor?.let { bgRef ->
        val bgColor = resolveColor(bgRef, colors, Color.Unspecified)
        if (bgColor != Color.Unspecified) {
            result = result.background(bgColor, shape ?: RectangleShape)
        }
    }

    config.border?.let { borderSpec ->
        val borderColor = resolveColor(borderSpec.color, colors, colors.border.default)
        val borderWidth =
            resolveStrokeWidthDp(
                width = borderSpec.widthDp,
                widthToken = borderSpec.widthToken,
                tokens = strokeTokens,
                fallback = strokeTokens.thin,
            )
        result = result.border(borderWidth, borderColor, shape ?: RectangleShape)
    }

    config.padding?.let { innerPadding ->
        result = result.padding(innerPadding.toPaddingValues())
    }

    return result
}
