package dev.abeta.kmp.atoms.atoms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.SliderAtom
import dev.abeta.kmp.tokens.ColorTokens
import dev.abeta.kmp.tokens.ThemeTokens
import kotlin.math.abs
import kotlin.math.roundToLong

private const val TENTHS_MULTIPLIER = 10.0
private const val TENTHS_DIVISOR = 10L

/**
 * Pure multiplatform helper formatting a slider numeric [value] with optional [unitLabel].
 */
fun formatSliderValue(
    value: Float,
    unitLabel: String? = null,
): String {
    val safeValue = if (value.isFinite()) value else 0f
    val scaledTenths = (safeValue.toDouble() * TENTHS_MULTIPLIER).roundToLong()
    val whole = scaledTenths / TENTHS_DIVISOR
    val frac = abs(scaledTenths % TENTHS_DIVISOR)
    val numeric =
        if (frac == 0L) {
            whole.toString()
        } else {
            val sign = if (scaledTenths < 0L && whole == 0L) "-" else ""
            "$sign$whole.$frac"
        }
    val unit = unitLabel?.trim()?.takeIf { it.isNotEmpty() }
    return if (unit != null) "$numeric $unit" else numeric
}

/**
 * Pure helper computing a safe closed [ClosedFloatingPointRange] for a [SliderAtom].
 */
fun resolveSliderRange(
    minValue: Float,
    maxValue: Float,
): ClosedFloatingPointRange<Float> {
    val safeMin = if (minValue.isFinite()) minValue else 0f
    val safeMax = if (maxValue.isFinite() && maxValue > safeMin) maxValue else safeMin + 1f
    return safeMin..safeMax
}

/**
 * Stateless range slider UI atom rendered from a [SliderAtom] contract.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiSlider(
    atom: SliderAtom,
    modifier: Modifier = Modifier,
    valueOverride: Float? = null,
    onValueChange: (Float) -> Unit = {},
    onAction: (ActionRef) -> Unit = {},
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val sizing = ThemeTokens.sizing

    val valueRange = resolveSliderRange(atom.minValue, atom.maxValue)
    val rawValue = valueOverride ?: atom.value
    val safeCurrent = if (rawValue.isFinite()) rawValue else valueRange.start
    val currentValue = safeCurrent.coerceIn(valueRange.start, valueRange.endInclusive)
    val formattedValue = formatSliderValue(currentValue, atom.unitLabel)
    val description = atom.label?.takeIf { it.isNotBlank() } ?: formattedValue

    Column(
        modifier = modifier.applyLayoutConfig(atom.layout),
        verticalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        if (!atom.label.isNullOrBlank() || atom.showValue) {
            UiSliderHeader(
                label = atom.label,
                formattedValue = formattedValue,
                showValue = atom.showValue,
                enabled = atom.enabled,
            )
        }
        Slider(
            value = currentValue,
            onValueChange = { updated ->
                onValueChange(updated)
            },
            onValueChangeFinished = {
                atom.onChange?.let(onAction)
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = sizing.minTouchTarget)
                    .semantics { contentDescription = description },
            enabled = atom.enabled,
            valueRange = valueRange,
            steps = atom.steps.coerceAtLeast(0),
            colors = resolveSliderColors(colors),
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiSliderHeader(
    label: String?,
    formattedValue: String,
    showValue: Boolean,
    enabled: Boolean,
) {
    val colors = ThemeTokens.colors
    val typography = ThemeTokens.typography
    val labelColor = if (enabled) colors.text.primary else colors.text.disabled
    val valueColor = if (enabled) colors.text.brand else colors.text.disabled

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        val labelText = label?.takeIf { it.isNotBlank() }
        if (labelText != null) {
            Text(
                text = labelText,
                style = typography.bodyMedium,
                color = labelColor,
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
        if (showValue) {
            Text(
                text = formattedValue,
                style = typography.labelLarge,
                color = valueColor,
            )
        }
    }
}

@Composable
private fun resolveSliderColors(colors: ColorTokens): SliderColors =
    SliderDefaults.colors(
        thumbColor = colors.brand.primary,
        activeTrackColor = colors.brand.primary,
        inactiveTrackColor = colors.border.subtle,
        disabledThumbColor = colors.text.disabled,
        disabledActiveTrackColor = colors.button.disabledBackground,
        disabledInactiveTrackColor = colors.border.subtle,
    )
