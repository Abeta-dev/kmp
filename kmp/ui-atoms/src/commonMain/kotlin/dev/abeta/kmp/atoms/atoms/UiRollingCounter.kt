package dev.abeta.kmp.atoms.atoms

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import dev.abeta.kmp.tokens.ThemeTokens

private const val TABULAR_NUMBERS_FEATURE = "tnum"
private const val MIN_ROLL_DURATION_MS = 200
private val DIGIT_SAMPLES = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")

private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

/**
 * Pure helper determining whether a rolling digit slot should animate upward (`true`)
 * or downward (`false`) when transitioning from [previous] to [current].
 */
fun shouldRollDigitUp(
    previous: Char,
    current: Char,
): Boolean =
    if (previous.isAsciiDigit() && current.isAsciiDigit()) {
        current >= previous
    } else {
        true
    }

/**
 * Stateless rolling odometer text counter UI atom.
 *
 * Animates changing ASCII `'0'..'9'` digits vertically purely in [graphicsLayer] (zero
 * `AnimatedContent` subtree churn, zero layout shifts across tabular digit slots) while keeping
 * non-ASCII and symbol glyphs static without clipping, exposing a single merged accessibility
 * text node, and collapsing cleanly to static ellipsized text when
 * [dev.abeta.kmp.tokens.MotionTokens.reducedMotion] is enabled.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiRollingCounter(
    value: String,
    style: TextStyle = ThemeTokens.typography.titleMedium,
    color: Color = ThemeTokens.colors.text.primary,
    modifier: Modifier = Modifier,
) {
    val motion = ThemeTokens.motion
    val tabularStyle =
        if (style.fontFeatureSettings == null) {
            style.copy(fontFeatureSettings = TABULAR_NUMBERS_FEATURE)
        } else {
            style
        }

    if (motion.reducedMotion || value.isEmpty() || value.none { it.isAsciiDigit() }) {
        Text(
            text = value,
            style = tabularStyle,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier,
        )
        return
    }

    val digitSlotWidth = rememberTabularDigitWidth(tabularStyle)
    val rollDurationMs = motion.fastMs.coerceAtLeast(MIN_ROLL_DURATION_MS)

    Row(
        modifier =
            modifier
                .clipToBounds()
                .clearAndSetSemantics {
                    text = AnnotatedString(value)
                },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        value.forEachIndexed { index, ch ->
            // Key from the right so prefix width changes ($9.50 -> $10.00) keep trailing digits anchored.
            val slotKey = value.length - 1 - index
            val isAscii = ch.isAsciiDigit()
            key(slotKey, isAscii) {
                if (isAscii) {
                    RollingDigitSlot(
                        digit = ch,
                        durationMs = rollDurationMs,
                        slotWidth = digitSlotWidth,
                        style = tabularStyle,
                        color = color,
                    )
                } else {
                    Text(
                        text = ch.toString(),
                        style = tabularStyle,
                        color = color,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberTabularDigitWidth(style: TextStyle): Dp {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(style, density) {
        val maxWidthPx =
            DIGIT_SAMPLES.maxOf { sample ->
                textMeasurer.measure(text = sample, style = style, maxLines = 1, softWrap = false).size.width
            }
        with(density) { maxWidthPx.toDp() }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun RollingDigitSlot(
    digit: Char,
    durationMs: Int,
    slotWidth: Dp,
    style: TextStyle,
    color: Color,
) {
    var previousDigit by remember { mutableStateOf(digit) }
    var currentDigit by remember { mutableStateOf(digit) }
    var rollUp by remember { mutableStateOf(true) }
    val progress = remember { Animatable(1f) }

    LaunchedEffect(digit) {
        if (digit != currentDigit) {
            previousDigit = currentDigit
            rollUp = shouldRollDigitUp(previous = currentDigit, current = digit)
            currentDigit = digit
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = durationMs, easing = FastOutSlowInEasing),
            )
        }
    }

    Box(
        modifier = Modifier.width(slotWidth).clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = previousDigit.toString(),
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
            modifier =
                Modifier.graphicsLayer {
                    val p = progress.value
                    alpha = if (p < 1f) 1f - p else 0f
                    translationY = if (rollUp) -size.height * p else size.height * p
                },
        )
        Text(
            text = currentDigit.toString(),
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
            modifier =
                Modifier.graphicsLayer {
                    val p = progress.value
                    alpha = p
                    translationY = if (rollUp) size.height * (1f - p) else -size.height * (1f - p)
                },
        )
    }
}
