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
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.contracts.MotionDurationToken
import dev.abeta.kmp.contracts.MotionLoopEffect
import dev.abeta.kmp.contracts.MotionSpec
import dev.abeta.kmp.contracts.MotionTransition
import dev.abeta.kmp.tokens.MotionTokens
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.coroutines.delay

private const val MAX_STAGGER_DELAY_MS = 600
private val SLIDE_DISTANCE_DP = 16.dp
private const val SCALE_INITIAL = 0.90f
private const val SPRING_SCALE_INITIAL = 0.84f
private const val SHAKE_STEP_MS = 45
private val SHAKE_OFFSETS_DP = listOf((-8).dp, 8.dp, (-5.5f).dp, 5.5f.dp, (-3).dp, 3.dp, 0.dp)
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
 * Optional per-screen registry of `motionKey` values whose enter transition has already played,
 * preventing `LazyColumn` scroll recycling from replaying entrance slides/scales repeatedly.
 */
val LocalEnteredMotionKeys: ProvidableCompositionLocal<MutableSet<Any>?> =
    staticCompositionLocalOf { null }

/**
 * Applies SDUI declarative enter transitions and all 7 ambient loop animations from [motion] while
 * keeping the element mounted in semantics, deferring animation reads into [graphicsLayer] and
 * draw-phase overlays for zero per-frame recompositions, and collapsing to zero motion under
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
    val enterProgressState = rememberEnterProgressState(spec, delayMs, durationMs, motionKey)
    val loopState = rememberLoopTransformState(spec.loopEffect)
    val glowColor = ThemeTokens.colors.brand.primary
    val slideDistancePx = with(LocalDensity.current) { SLIDE_DISTANCE_DP.toPx() }

    return this
        .applyMotionGraphicsLayer(
            enter = spec.enter,
            progressState = enterProgressState,
            loop = loopState,
            slideDistancePx = slideDistancePx,
        ).applyLoopOverlays(
            loopEffect = spec.loopEffect,
            shimmerPhase = loopState.shimmerPhase,
            glowProgress = loopState.glowProgress,
            glowColor = glowColor,
        )
}

@Composable
private fun rememberEnterProgressState(
    motion: MotionSpec,
    delayMs: Int,
    durationMs: Int,
    motionKey: Any?,
): State<Float>? {
    if (motion.enter == MotionTransition.NONE) return null
    val enteredKeys = LocalEnteredMotionKeys.current
    val trackableKey = motionKey?.takeIf { it !== Unit }
    val alreadyEntered = trackableKey != null && enteredKeys?.contains(trackableKey) == true
    val animatable =
        remember(motionKey, motion.enter) {
            Animatable(if (alreadyEntered) ONE else ZERO)
        }
    LaunchedEffect(motionKey, motion.enter, delayMs, durationMs) {
        if (alreadyEntered) {
            animatable.snapTo(ONE)
            return@LaunchedEffect
        }
        if (trackableKey != null) {
            enteredKeys?.add(trackableKey)
        }
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
    return animatable.asState()
}

private fun LoopTransformState.hasGraphicsLayerState(): Boolean =
    scale != null || alpha != null || rotationZ != null || translationX != null || translationY != null

private fun resolveEnterScale(
    enter: MotionTransition,
    progress: Float,
): Float =
    when (enter) {
        MotionTransition.SCALE_IN -> SCALE_INITIAL + (ONE - SCALE_INITIAL) * progress
        MotionTransition.SPRING_BOUNCE -> SPRING_SCALE_INITIAL + (ONE - SPRING_SCALE_INITIAL) * progress
        else -> ONE
    }

private fun resolveEnterTranslationY(
    enter: MotionTransition,
    progress: Float,
    slideDistancePx: Float,
): Float =
    when (enter) {
        MotionTransition.SLIDE_UP -> (ONE - progress) * slideDistancePx
        MotionTransition.SLIDE_DOWN -> -(ONE - progress) * slideDistancePx
        else -> ZERO
    }

private fun Modifier.applyMotionGraphicsLayer(
    enter: MotionTransition,
    progressState: State<Float>?,
    loop: LoopTransformState,
    slideDistancePx: Float,
): Modifier {
    if (progressState == null && !loop.hasGraphicsLayerState()) return this

    return graphicsLayer {
        val progress = progressState?.value ?: ONE
        val combinedScale = resolveEnterScale(enter, progress) * (loop.scale?.value ?: ONE)
        scaleX = combinedScale
        scaleY = combinedScale
        rotationZ = loop.rotationZ?.value ?: ZERO
        translationX = loop.translationX?.value ?: ZERO
        translationY = resolveEnterTranslationY(enter, progress, slideDistancePx) + (loop.translationY?.value ?: ZERO)
        alpha = (progress * (loop.alpha?.value ?: ONE)).coerceIn(ZERO, ONE)
    }
}

/**
 * Triggers a short horizontal error-shake animation whenever [shakeSequence] increments above the
 * sequence observed at initial composition, collapsing to zero displacement when
 * [ThemeTokens.motion.reducedMotion] is active or when a recycled item enters composition after a
 * prior shake.
 */
@Composable
fun Modifier.shakeOnError(
    shakeSequence: Long,
    enabled: Boolean = true,
): Modifier {
    val motion = ThemeTokens.motion
    val initialSequence = remember { shakeSequence }
    if (!enabled || motion.reducedMotion || shakeSequence <= initialSequence) {
        return this
    }
    val density = LocalDensity.current
    val shakeOffsetsPx =
        remember(density) {
            with(density) { SHAKE_OFFSETS_DP.map { it.toPx() } }
        }
    val offsetX = remember { Animatable(ZERO) }
    LaunchedEffect(shakeSequence) {
        offsetX.snapTo(ZERO)
        shakeOffsetsPx.forEach { target ->
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
