package dev.abeta.kmp.atoms.atoms

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
private val DIGIT_SAMPLES = listOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9")

/**
 * Pure helper determining whether a rolling digit slot should animate upward (`true`)
 * or downward (`false`) when transitioning from [previous] to [current].
 */
fun shouldRollDigitUp(
    previous: Char,
    current: Char,
): Boolean =
    if (previous.isDigit() && current.isDigit()) {
        current >= previous
    } else {
        true
    }

/**
 * Stateless rolling odometer text counter UI atom.
 *
 * Animates changing numeric digits vertically with slide-and-fade transitions while keeping
 * non-digit glyphs stable, exposing a single merged accessibility text node, and collapsing
 * cleanly to static text when [dev.abeta.kmp.tokens.MotionTokens.reducedMotion] is enabled.
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

    if (motion.reducedMotion || value.isEmpty()) {
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

    Row(
        modifier =
            modifier.clearAndSetSemantics {
                text = AnnotatedString(value)
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        value.forEachIndexed { index, ch ->
            // Key from the right so prefix width changes ($9.50 -> $10.00) keep trailing digits anchored.
            val slotKey = value.length - 1 - index
            key(slotKey, ch.isDigit()) {
                if (ch.isDigit()) {
                    RollingDigitSlot(
                        digit = ch,
                        durationMs = motion.fastMs,
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
    Box(
        modifier = Modifier.width(slotWidth),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = digit,
            transitionSpec = {
                val rollUp = shouldRollDigitUp(previous = initialState, current = targetState)
                val enter =
                    slideInVertically(animationSpec = tween(durationMillis = durationMs)) { height ->
                        if (rollUp) height else -height
                    } + fadeIn(animationSpec = tween(durationMillis = durationMs))
                val exit =
                    slideOutVertically(animationSpec = tween(durationMillis = durationMs)) { height ->
                        if (rollUp) -height else height
                    } + fadeOut(animationSpec = tween(durationMillis = durationMs))
                (enter togetherWith exit).using(SizeTransform(clip = true, sizeAnimationSpec = { _, _ -> snap() }))
            },
            label = "rolling_counter_digit",
        ) { currentDigit ->
            Text(
                text = currentDigit.toString(),
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
