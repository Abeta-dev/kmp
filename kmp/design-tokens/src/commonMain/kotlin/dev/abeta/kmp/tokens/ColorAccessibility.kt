package dev.abeta.kmp.tokens

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Pure WCAG 2.1 luminance and contrast ratio calculator for validating accessible token pairings.
 */
object ColorAccessibility {
    private const val LINEAR_THRESHOLD = 0.03928
    private const val LINEAR_DIVISOR = 12.92
    private const val GAMMA_OFFSET = 0.055
    private const val GAMMA_DIVISOR = 1.055
    private const val GAMMA_EXPONENT = 2.4
    private const val RED_WEIGHT = 0.2126
    private const val GREEN_WEIGHT = 0.7152
    private const val BLUE_WEIGHT = 0.0722
    private const val CONTRAST_OFFSET = 0.05

    const val WCAG_AA_NORMAL_TEXT: Double = 4.5
    const val WCAG_AA_LARGE_TEXT: Double = 3.0
    const val WCAG_AAA_NORMAL_TEXT: Double = 7.0

    /** Computes WCAG relative luminance in `[0.0, 1.0]` for an sRGB [Color]. */
    fun relativeLuminance(color: Color): Double {
        val r = linearizeChannel(color.red.toDouble())
        val g = linearizeChannel(color.green.toDouble())
        val b = linearizeChannel(color.blue.toDouble())
        return RED_WEIGHT * r + GREEN_WEIGHT * g + BLUE_WEIGHT * b
    }

    /** Computes contrast ratio in `[1.0, 21.0]` between [foreground] and [background]. */
    fun contrastRatio(
        foreground: Color,
        background: Color,
    ): Double {
        val l1 = relativeLuminance(foreground)
        val l2 = relativeLuminance(background)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + CONTRAST_OFFSET) / (darker + CONTRAST_OFFSET)
    }

    /** Returns `true` when [foreground] over [background] meets WCAG 2.1 AA minimum contrast. */
    fun meetsWcagAA(
        foreground: Color,
        background: Color,
        largeText: Boolean = false,
    ): Boolean {
        val threshold = if (largeText) WCAG_AA_LARGE_TEXT else WCAG_AA_NORMAL_TEXT
        return contrastRatio(foreground, background) >= threshold
    }

    private fun linearizeChannel(channel: Double): Double =
        if (channel <= LINEAR_THRESHOLD) {
            channel / LINEAR_DIVISOR
        } else {
            ((channel + GAMMA_OFFSET) / GAMMA_DIVISOR).pow(GAMMA_EXPONENT)
        }
}
