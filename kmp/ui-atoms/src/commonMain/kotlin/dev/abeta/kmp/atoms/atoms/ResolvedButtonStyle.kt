package dev.abeta.kmp.atoms.atoms

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.contracts.ButtonSize
import dev.abeta.kmp.contracts.ButtonVariant
import dev.abeta.kmp.tokens.ColorTokens
import dev.abeta.kmp.tokens.SpacingTokens

private const val SMALL_MIN_HEIGHT_DP = 36
private const val MEDIUM_MIN_HEIGHT_DP = 44
private const val LARGE_MIN_HEIGHT_DP = 52
private const val SMALL_ICON_DP = 16
private const val MEDIUM_ICON_DP = 18
private const val LARGE_ICON_DP = 20

/**
 * Pure resolved styling attributes for an [UiButton].
 */
data class ResolvedButtonStyle(
    val containerColor: Color,
    val contentColor: Color,
    val borderColor: Color?,
    val minHeight: Dp,
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val iconSize: Int,
)

/**
 * Pure resolver computing colors, sizing, and padding for a button atom.
 */
fun resolveButtonStyle(
    variant: ButtonVariant,
    size: ButtonSize,
    enabled: Boolean,
    colors: ColorTokens,
    spacing: SpacingTokens,
): ResolvedButtonStyle {
    val (container, content, border) =
        if (enabled) {
            resolveEnabledButtonColors(variant, colors)
        } else {
            resolveDisabledButtonColors(variant, colors)
        }
    return resolveButtonMetrics(
        size = size,
        containerColor = container,
        contentColor = content,
        borderColor = border,
        spacing = spacing,
    )
}

private fun resolveDisabledButtonColors(
    variant: ButtonVariant,
    colors: ColorTokens,
): Triple<Color, Color, Color?> =
    when (variant) {
        ButtonVariant.OUTLINE -> {
            Triple(
                Color.Transparent,
                colors.button.disabledForeground,
                colors.button.disabledBackground,
            )
        }

        ButtonVariant.GHOST -> {
            Triple(
                Color.Transparent,
                colors.button.disabledForeground,
                null,
            )
        }

        ButtonVariant.PRIMARY,
        ButtonVariant.SECONDARY,
        ButtonVariant.DESTRUCTIVE,
        -> {
            Triple(
                colors.button.disabledBackground,
                colors.button.disabledForeground,
                null,
            )
        }
    }

private fun resolveEnabledButtonColors(
    variant: ButtonVariant,
    colors: ColorTokens,
): Triple<Color, Color, Color?> =
    when (variant) {
        ButtonVariant.PRIMARY -> {
            Triple(
                colors.button.primaryBackground,
                colors.button.primaryForeground,
                null,
            )
        }

        ButtonVariant.SECONDARY -> {
            Triple(
                colors.button.secondaryBackground,
                colors.button.secondaryForeground,
                null,
            )
        }

        ButtonVariant.OUTLINE -> {
            Triple(
                Color.Transparent,
                colors.text.primary,
                colors.button.outlineBorder,
            )
        }

        ButtonVariant.GHOST -> {
            Triple(
                Color.Transparent,
                colors.button.ghostForeground,
                null,
            )
        }

        ButtonVariant.DESTRUCTIVE -> {
            Triple(
                colors.button.destructiveBackground,
                colors.button.destructiveForeground,
                null,
            )
        }
    }

private fun resolveButtonMetrics(
    size: ButtonSize,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color?,
    spacing: SpacingTokens,
): ResolvedButtonStyle =
    when (size) {
        ButtonSize.SMALL -> {
            ResolvedButtonStyle(
                containerColor = containerColor,
                contentColor = contentColor,
                borderColor = borderColor,
                minHeight = SMALL_MIN_HEIGHT_DP.dp,
                horizontalPadding = spacing.space12,
                verticalPadding = spacing.space8,
                iconSize = SMALL_ICON_DP,
            )
        }

        ButtonSize.MEDIUM -> {
            ResolvedButtonStyle(
                containerColor = containerColor,
                contentColor = contentColor,
                borderColor = borderColor,
                minHeight = MEDIUM_MIN_HEIGHT_DP.dp,
                horizontalPadding = spacing.space16,
                verticalPadding = spacing.space12,
                iconSize = MEDIUM_ICON_DP,
            )
        }

        ButtonSize.LARGE -> {
            ResolvedButtonStyle(
                containerColor = containerColor,
                contentColor = contentColor,
                borderColor = borderColor,
                minHeight = LARGE_MIN_HEIGHT_DP.dp,
                horizontalPadding = spacing.space24,
                verticalPadding = spacing.space12,
                iconSize = LARGE_ICON_DP,
            )
        }
    }
