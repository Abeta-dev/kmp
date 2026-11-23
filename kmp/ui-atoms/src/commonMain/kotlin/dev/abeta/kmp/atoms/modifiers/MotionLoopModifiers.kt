@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.atoms.modifiers

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import dev.abeta.kmp.contracts.MotionLoopEffect
import dev.abeta.kmp.tokens.ThemeTokens

private const val ONE = 1f
private const val ZERO = 0f

private const val PULSE_MIN_ALPHA = 0.88f
private const val PULSE_MAX_SCALE = 1.032f
private const val PULSE_PERIOD_MS = 1500

private const val SHIMMER_PERIOD_MS = 1600
private const val SHIMMER_START_FRACTION = -0.4f
private const val SHIMMER_END_FRACTION = 1.4f
private const val SHIMMER_BAND_WIDTH = 0.35f
private const val SHIMMER_ALPHA = 0.16f
private val SHIMMER_COLORS = listOf(Color.Transparent, Color.White.copy(alpha = SHIMMER_ALPHA), Color.Transparent)

private const val WIGGLE_PERIOD_MS = 2600
private const val WIGGLE_PEAK_DEGREES = 3.5f
private const val WIGGLE_SECONDARY_DEGREES = 2.0f
private const val WIGGLE_KEYFRAME_1_MS = 160
private const val WIGGLE_KEYFRAME_2_MS = 340
private const val WIGGLE_KEYFRAME_3_MS = 520
private const val WIGGLE_KEYFRAME_4_MS = 700
private const val WIGGLE_KEYFRAME_5_MS = 880

private const val FLOAT_PERIOD_MS = 1800
private const val FLOAT_AMPLITUDE_PX = 4f

private const val HEARTBEAT_PERIOD_MS = 1600
private const val HEARTBEAT_PEAK_PRIMARY = 1.07f
private const val HEARTBEAT_PEAK_SECONDARY = 1.04f
private const val HEARTBEAT_BEAT_1_MS = 180
private const val HEARTBEAT_REST_1_MS = 360
private const val HEARTBEAT_BEAT_2_MS = 540
private const val HEARTBEAT_REST_2_MS = 760

private const val GLOW_RING_PERIOD_MS = 1500
private const val GLOW_MIN_STROKE_PX = 2f
private const val GLOW_MAX_STROKE_PX = 5f
private const val GLOW_OUTER_STROKE_MULTIPLIER = 1.8f
private const val GLOW_MIN_ALPHA = 0.22f
private const val GLOW_MAX_ALPHA = 0.65f
private const val GLOW_OUTER_ALPHA_FACTOR = 0.30f
private const val GLOW_MAX_CORNER_RADIUS_PX = 28f

private const val MARQUEE_SWAY_PERIOD_MS = 1800
private const val MARQUEE_SWAY_PX = 6f

private const val PRESS_BOUNCE_SCALE = 0.96f

internal data class LoopTransformState(
    val scale: State<Float>? = null,
    val alpha: State<Float>? = null,
    val rotationZ: State<Float>? = null,
    val translationX: State<Float>? = null,
    val translationY: State<Float>? = null,
    val shimmerPhase: State<Float>? = null,
    val glowProgress: State<Float>? = null,
)

/**
 * Pure helper returning `true` when [loopEffect] drives an active continuous animation
 * and [reducedMotion] is disabled.
 */
fun isLoopEffectActive(
    loopEffect: MotionLoopEffect,
    reducedMotion: Boolean,
): Boolean = !reducedMotion && loopEffect != MotionLoopEffect.NONE

/**
 * Adds tactile spring press-bounce scaling (`0.96f`) using non-consuming pointer observation so
 * child or chained `clickable` modifiers remain unaffected. Cancels press feedback automatically
 * once pointer movement exceeds touch slop (preventing scroll-drag jitter) and defers scale reads
 * into [graphicsLayer] for zero-recomposition frames. Collapses to a no-op under
 * [dev.abeta.kmp.tokens.MotionTokens.reducedMotion] or when [enabled] is `false`.
 */
@Composable
fun Modifier.pressBounce(enabled: Boolean = true): Modifier {
    if (!enabled || ThemeTokens.motion.reducedMotion) {
        return this
    }
    var isPressed by remember { mutableStateOf(false) }
    val scaleState =
        animateFloatAsState(
            targetValue = if (isPressed) PRESS_BOUNCE_SCALE else ONE,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            label = "press_bounce_scale",
        )
    return this
        .pointerInput(enabled) {
            val touchSlopSq = viewConfiguration.touchSlop * viewConfiguration.touchSlop
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val downPos = down.position
                isPressed = true
                try {
                    do {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        val activeChange = event.changes.firstOrNull { it.id == down.id }
                        if (activeChange != null && isPressed) {
                            val dx = activeChange.position.x - downPos.x
                            val dy = activeChange.position.y - downPos.y
                            if (dx * dx + dy * dy > touchSlopSq) {
                                isPressed = false
                            }
                        }
                    } while (event.changes.any { it.pressed })
                } finally {
                    isPressed = false
                }
            }
        }.graphicsLayer {
            val currentScale = scaleState.value
            scaleX = currentScale
            scaleY = currentScale
        }
}

@Composable
internal fun rememberLoopTransformState(loopEffect: MotionLoopEffect): LoopTransformState =
    when (loopEffect) {
        MotionLoopEffect.NONE -> LoopTransformState()
        MotionLoopEffect.PULSE -> rememberPulseLoop()
        MotionLoopEffect.WIGGLE -> LoopTransformState(rotationZ = rememberWiggleDegrees())
        MotionLoopEffect.HEARTBEAT -> LoopTransformState(scale = rememberHeartbeatScale())
        MotionLoopEffect.SHIMMER -> LoopTransformState(shimmerPhase = rememberLinearLoop(SHIMMER_SPEC))
        MotionLoopEffect.FLOAT -> LoopTransformState(translationY = rememberLinearLoop(FLOAT_SPEC))
        MotionLoopEffect.GLOW_RING -> LoopTransformState(glowProgress = rememberLinearLoop(GLOW_RING_SPEC))
        MotionLoopEffect.MARQUEE -> LoopTransformState(translationX = rememberLinearLoop(MARQUEE_SPEC))
    }

private data class RepeatingFloatSpec(
    val initialValue: Float,
    val targetValue: Float,
    val durationMs: Int,
    val easing: Easing,
    val repeatMode: RepeatMode,
    val label: String,
)

private val SHIMMER_SPEC =
    RepeatingFloatSpec(
        initialValue = SHIMMER_START_FRACTION,
        targetValue = SHIMMER_END_FRACTION,
        durationMs = SHIMMER_PERIOD_MS,
        easing = LinearEasing,
        repeatMode = RepeatMode.Restart,
        label = "shimmer_phase",
    )

private val FLOAT_SPEC =
    RepeatingFloatSpec(
        initialValue = -FLOAT_AMPLITUDE_PX,
        targetValue = FLOAT_AMPLITUDE_PX,
        durationMs = FLOAT_PERIOD_MS,
        easing = FastOutSlowInEasing,
        repeatMode = RepeatMode.Reverse,
        label = "float_offset_y",
    )

private val GLOW_RING_SPEC =
    RepeatingFloatSpec(
        initialValue = ZERO,
        targetValue = ONE,
        durationMs = GLOW_RING_PERIOD_MS,
        easing = FastOutSlowInEasing,
        repeatMode = RepeatMode.Reverse,
        label = "glow_ring_progress",
    )

private val MARQUEE_SPEC =
    RepeatingFloatSpec(
        initialValue = -MARQUEE_SWAY_PX,
        targetValue = MARQUEE_SWAY_PX,
        durationMs = MARQUEE_SWAY_PERIOD_MS,
        easing = FastOutSlowInEasing,
        repeatMode = RepeatMode.Reverse,
        label = "marquee_sway_x",
    )

internal fun Modifier.applyLoopOverlays(
    loopEffect: MotionLoopEffect,
    shimmerPhase: State<Float>?,
    glowProgress: State<Float>?,
    glowColor: Color,
): Modifier =
    when (loopEffect) {
        MotionLoopEffect.SHIMMER -> if (shimmerPhase != null) applyShimmerSweep(shimmerPhase) else this
        MotionLoopEffect.GLOW_RING -> if (glowProgress != null) applyGlowRingHalo(glowProgress, glowColor) else this
        else -> this
    }

@Composable
private fun rememberLinearLoop(spec: RepeatingFloatSpec): State<Float> {
    val transition = rememberInfiniteTransition(label = "motion_${spec.label}")
    return transition.animateFloat(
        initialValue = spec.initialValue,
        targetValue = spec.targetValue,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = spec.durationMs, easing = spec.easing),
                repeatMode = spec.repeatMode,
            ),
        label = spec.label,
    )
}

@Composable
private fun rememberPulseLoop(): LoopTransformState {
    val transition = rememberInfiniteTransition(label = "motion_pulse")
    val scale =
        transition.animateFloat(
            initialValue = ONE,
            targetValue = PULSE_MAX_SCALE,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = PULSE_PERIOD_MS, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "pulse_scale",
        )
    val alpha =
        transition.animateFloat(
            initialValue = PULSE_MIN_ALPHA,
            targetValue = ONE,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = PULSE_PERIOD_MS, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "pulse_alpha",
        )
    return LoopTransformState(scale = scale, alpha = alpha)
}

@Composable
private fun rememberWiggleDegrees(): State<Float> {
    val transition = rememberInfiniteTransition(label = "motion_wiggle")
    return transition.animateFloat(
        initialValue = ZERO,
        targetValue = ZERO,
        animationSpec =
            infiniteRepeatable(
                animation =
                    keyframes {
                        durationMillis = WIGGLE_PERIOD_MS
                        ZERO at 0 using FastOutSlowInEasing
                        WIGGLE_PEAK_DEGREES at WIGGLE_KEYFRAME_1_MS using FastOutSlowInEasing
                        -WIGGLE_PEAK_DEGREES at WIGGLE_KEYFRAME_2_MS using FastOutSlowInEasing
                        WIGGLE_SECONDARY_DEGREES at WIGGLE_KEYFRAME_3_MS using FastOutSlowInEasing
                        -WIGGLE_SECONDARY_DEGREES at WIGGLE_KEYFRAME_4_MS using FastOutSlowInEasing
                        ZERO at WIGGLE_KEYFRAME_5_MS using FastOutSlowInEasing
                        ZERO at WIGGLE_PERIOD_MS
                    },
                repeatMode = RepeatMode.Restart,
            ),
        label = "wiggle_degrees",
    )
}

@Composable
private fun rememberHeartbeatScale(): State<Float> {
    val transition = rememberInfiniteTransition(label = "motion_heartbeat")
    return transition.animateFloat(
        initialValue = ONE,
        targetValue = ONE,
        animationSpec =
            infiniteRepeatable(
                animation =
                    keyframes {
                        durationMillis = HEARTBEAT_PERIOD_MS
                        ONE at 0 using FastOutSlowInEasing
                        HEARTBEAT_PEAK_PRIMARY at HEARTBEAT_BEAT_1_MS using FastOutSlowInEasing
                        ONE at HEARTBEAT_REST_1_MS using FastOutSlowInEasing
                        HEARTBEAT_PEAK_SECONDARY at HEARTBEAT_BEAT_2_MS using FastOutSlowInEasing
                        ONE at HEARTBEAT_REST_2_MS using FastOutSlowInEasing
                        ONE at HEARTBEAT_PERIOD_MS
                    },
                repeatMode = RepeatMode.Restart,
            ),
        label = "heartbeat_scale",
    )
}

private fun Modifier.applyShimmerSweep(phaseState: State<Float>): Modifier =
    drawWithContent {
        drawContent()
        val width = size.width
        val height = size.height
        if (width > ZERO && height > ZERO) {
            val phase = phaseState.value
            val startX = width * (phase - SHIMMER_BAND_WIDTH)
            val endX = width * phase
            drawRect(
                brush =
                    Brush.linearGradient(
                        colors = SHIMMER_COLORS,
                        start = Offset(startX, ZERO),
                        end = Offset(endX, height),
                    ),
            )
        }
    }

private fun Modifier.applyGlowRingHalo(
    progressState: State<Float>,
    glowColor: Color,
): Modifier =
    drawWithContent {
        drawContent()
        val width = size.width
        val height = size.height
        if (width > ZERO && height > ZERO) {
            val progress = progressState.value
            val innerStroke = GLOW_MIN_STROKE_PX + (GLOW_MAX_STROKE_PX - GLOW_MIN_STROKE_PX) * progress
            val outerStroke = innerStroke * GLOW_OUTER_STROKE_MULTIPLIER
            val innerAlpha = GLOW_MIN_ALPHA + (GLOW_MAX_ALPHA - GLOW_MIN_ALPHA) * progress
            val cornerPx = minOf(minOf(width, height) / 2f, GLOW_MAX_CORNER_RADIUS_PX)
            val radius = CornerRadius(cornerPx, cornerPx)
            drawRoundRect(
                color = glowColor.copy(alpha = innerAlpha * GLOW_OUTER_ALPHA_FACTOR),
                cornerRadius = radius,
                style = Stroke(width = outerStroke),
            )
            drawRoundRect(
                color = glowColor.copy(alpha = innerAlpha),
                cornerRadius = radius,
                style = Stroke(width = innerStroke),
            )
        }
    }
