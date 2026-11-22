package dev.abeta.kmp.atoms.modifiers

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import dev.abeta.kmp.contracts.MotionDurationToken
import dev.abeta.kmp.contracts.MotionLoopEffect
import dev.abeta.kmp.contracts.MotionSpec
import dev.abeta.kmp.contracts.MotionTransition
import dev.abeta.kmp.tokens.MotionTokens
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.coroutines.delay

private const val MAX_STAGGER_DELAY_MS = 600
private const val SLIDE_DISTANCE_PX = 36f
private const val SCALE_INITIAL = 0.90f
private const val SPRING_SCALE_INITIAL = 0.84f
private const val SHAKE_STEP_MS = 45
private val SHAKE_OFFSETS_PX = listOf(-16f, 16f, -11f, 11f, -6f, 6f, 0f)
private const val ONE = 1f
private const val ZERO = 0f

/**
 * Pure helper resolving animation duration in milliseconds for [motion], collapsing to zero
 * when [MotionTokens.reducedMotion] is enabled.
 */
fun resolveMotionDurationMs(
    motion: MotionSpec?,
    tokens: MotionTokens,
): Int =
    when {
        tokens.reducedMotion -> {
            tokens.instantMs
        }

        motion?.durationMs != null -> {
            motion.durationMs!!.coerceAtLeast(0)
        }

        else -> {
            when (motion?.durationToken) {
                MotionDurationToken.INSTANT -> tokens.instantMs
                MotionDurationToken.FAST -> tokens.fastMs
                MotionDurationToken.NORMAL -> tokens.normalMs
                MotionDurationToken.SLOW -> tokens.slowMs
                null -> tokens.normalMs
            }
        }
    }

/**
 * Pure helper resolving enter stagger delay in milliseconds for [motion] at [itemIndex].
 */
fun resolveMotionDelayMs(
    motion: MotionSpec?,
    itemIndex: Int,
    tokens: MotionTokens,
): Int =
    if (tokens.reducedMotion || motion == null) {
        0
    } else if (motion.delayMs > 0) {
        motion.delayMs.coerceAtMost(MAX_STAGGER_DELAY_MS)
    } else {
        (motion.staggerMs.coerceAtLeast(0) * itemIndex.coerceAtLeast(0))
            .coerceAtMost(MAX_STAGGER_DELAY_MS)
    }

/**
 * Maps a declarative [MotionTransition] preset and duration to a Compose [EnterTransition].
 */
fun resolveEnterTransition(
    transition: MotionTransition,
    durationMs: Int,
): EnterTransition =
    when (transition) {
        MotionTransition.NONE -> {
            EnterTransition.None
        }

        MotionTransition.FADE_IN -> {
            fadeIn(animationSpec = tween(durationMillis = durationMs))
        }

        MotionTransition.SLIDE_UP -> {
            slideInVertically(animationSpec = tween(durationMillis = durationMs)) { it / 2 } +
                fadeIn(animationSpec = tween(durationMillis = durationMs))
        }

        MotionTransition.SLIDE_DOWN -> {
            slideInVertically(animationSpec = tween(durationMillis = durationMs)) { -it / 2 } +
                fadeIn(animationSpec = tween(durationMillis = durationMs))
        }

        MotionTransition.SCALE_IN -> {
            scaleIn(animationSpec = tween(durationMillis = durationMs), initialScale = SCALE_INITIAL) +
                fadeIn(animationSpec = tween(durationMillis = durationMs))
        }

        MotionTransition.SPRING_BOUNCE -> {
            scaleIn(
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                initialScale = SPRING_SCALE_INITIAL,
            ) + fadeIn(animationSpec = tween(durationMillis = durationMs))
        }
    }

/**
 * Maps a declarative [MotionTransition] preset and duration to a Compose [ExitTransition].
 */
fun resolveExitTransition(
    transition: MotionTransition,
    durationMs: Int,
): ExitTransition =
    when (transition) {
        MotionTransition.NONE -> {
            ExitTransition.None
        }

        MotionTransition.FADE_IN -> {
            fadeOut(animationSpec = tween(durationMillis = durationMs))
        }

        MotionTransition.SLIDE_UP -> {
            slideOutVertically(animationSpec = tween(durationMillis = durationMs)) { -it / 2 } +
                fadeOut(animationSpec = tween(durationMillis = durationMs))
        }

        MotionTransition.SLIDE_DOWN -> {
            slideOutVertically(animationSpec = tween(durationMillis = durationMs)) { it / 2 } +
                fadeOut(animationSpec = tween(durationMillis = durationMs))
        }

        MotionTransition.SCALE_IN,
        MotionTransition.SPRING_BOUNCE,
        -> {
            scaleOut(animationSpec = tween(durationMillis = durationMs), targetScale = SCALE_INITIAL) +
                fadeOut(animationSpec = tween(durationMillis = durationMs))
        }
    }

/**
 * Applies SDUI declarative enter transitions and all 7 ambient loop animations from [motion] while
 * keeping the element mounted in semantics and collapsing to zero motion under
 * [dev.abeta.kmp.tokens.MotionTokens.reducedMotion].
 */
@Composable
fun Modifier.applyMotionSpec(
    motion: MotionSpec?,
    itemIndex: Int = 0,
    motionKey: Any? = Unit,
): Modifier {
    val motionTokens = ThemeTokens.motion
    val isInactive =
        motion == null ||
            (motion.enter == MotionTransition.NONE && motion.loopEffect == MotionLoopEffect.NONE)
    if (motionTokens.reducedMotion || isInactive) {
        return this
    }
    val spec = checkNotNull(motion)
    val durationMs = resolveMotionDurationMs(spec, motionTokens)
    val delayMs = resolveMotionDelayMs(spec, itemIndex, motionTokens)
    val enterProgress = rememberEnterProgress(spec, delayMs, durationMs, motionKey)
    val loopState = rememberLoopTransformState(spec.loopEffect)
    val glowColor = ThemeTokens.colors.brand.primary

    return this
        .applyMotionGraphicsLayer(
            enter = spec.enter,
            progress = enterProgress,
            loop = loopState,
        ).applyLoopOverlays(
            loopEffect = spec.loopEffect,
            shimmerPhase = loopState.shimmerPhase,
            glowProgress = loopState.glowProgress,
            glowColor = glowColor,
        )
}

@Composable
private fun rememberEnterProgress(
    motion: MotionSpec,
    delayMs: Int,
    durationMs: Int,
    motionKey: Any?,
): Float {
    if (motion.enter == MotionTransition.NONE) return ONE
    val animatable = remember(motionKey, motion.enter) { Animatable(ZERO) }
    LaunchedEffect(motionKey, motion.enter, delayMs, durationMs) {
        if (delayMs > 0) {
            delay(delayMs.toLong())
        }
        if (motion.enter == MotionTransition.SPRING_BOUNCE) {
            animatable.animateTo(
                targetValue = ONE,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
            )
        } else {
            animatable.animateTo(
                targetValue = ONE,
                animationSpec = tween(durationMillis = durationMs),
            )
        }
    }
    return animatable.value
}

private fun Modifier.applyMotionGraphicsLayer(
    enter: MotionTransition,
    progress: Float,
    loop: LoopTransformState,
): Modifier =
    graphicsLayer {
        val enterScale =
            when (enter) {
                MotionTransition.SCALE_IN -> SCALE_INITIAL + (ONE - SCALE_INITIAL) * progress
                MotionTransition.SPRING_BOUNCE -> SPRING_SCALE_INITIAL + (ONE - SPRING_SCALE_INITIAL) * progress
                else -> ONE
            }
        val enterTranslationY =
            when (enter) {
                MotionTransition.SLIDE_UP -> (ONE - progress) * SLIDE_DISTANCE_PX
                MotionTransition.SLIDE_DOWN -> -(ONE - progress) * SLIDE_DISTANCE_PX
                else -> ZERO
            }
        val combinedScale = enterScale * loop.scale
        scaleX = combinedScale
        scaleY = combinedScale
        rotationZ = loop.rotationZ
        translationX = loop.translationX
        translationY = enterTranslationY + loop.translationY
        alpha = (progress * loop.alpha).coerceIn(ZERO, ONE)
    }

/**
 * Triggers a short horizontal error-shake animation whenever [shakeSequence] increments above `0`,
 * collapsing to zero displacement when [ThemeTokens.motion.reducedMotion] is active.
 */
@Composable
fun Modifier.shakeOnError(
    shakeSequence: Long,
    enabled: Boolean = true,
): Modifier {
    val motion = ThemeTokens.motion
    if (!enabled || motion.reducedMotion || shakeSequence <= 0L) {
        return this
    }
    val offsetX = remember { Animatable(ZERO) }
    LaunchedEffect(shakeSequence) {
        offsetX.snapTo(ZERO)
        SHAKE_OFFSETS_PX.forEach { target ->
            offsetX.animateTo(
                targetValue = target,
                animationSpec = tween(durationMillis = SHAKE_STEP_MS, easing = LinearEasing),
            )
        }
    }
    return graphicsLayer {
        translationX = offsetX.value
    }
}
