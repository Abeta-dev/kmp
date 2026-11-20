package dev.abeta.kmp.atoms.atoms

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.tokens.ColorTokens
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val BURST_CANVAS_SIZE_DP = 140
private const val CARD_MAX_WIDTH_DP = 320
private const val BURST_DURATION_MS = 900
private const val ENTER_SCALE_INITIAL = 0.88f
private const val EXIT_SCALE_TARGET = 0.92f
private const val RADIUS_DIVISOR = 2f
private const val PARTICLE_COUNT = 12
private const val PARTICLE_BASE_RADIUS_DP = 3.5f
private const val PARTICLE_ALT_RADIUS_DP = 5.5f
private const val PARTICLE_ALT_STEP = 2
private const val PARTICLE_MIN_DISTANCE_RATIO = 0.24f
private const val PARTICLE_DISTANCE_SPAN = 0.66f
private const val PARTICLE_ALT_DISTANCE_BOOST = 0.08f
private const val PARTICLE_SHRINK_FACTOR = 0.35f
private const val HALO_START_RATIO = 0.34f
private const val HALO_SPAN_RATIO = 0.56f
private const val HALO_ALPHA_MAX = 0.45f
private const val HALO_STROKE_DP = 3f
private const val CHECK_BADGE_RATIO = 0.32f
private const val CHECK_HALO_SCALE = 1.28f
private const val CHECK_STROKE_DP = 4f
private const val CHECK_START_X = -0.34f
private const val CHECK_START_Y = 0.04f
private const val CHECK_MID_X = -0.08f
private const val CHECK_MID_Y = 0.32f
private const val CHECK_END_X = 0.40f
private const val CHECK_END_Y = -0.26f
private const val FULL_CIRCLE_DEGREES = 360f
private const val STRAIGHT_ANGLE_DEGREES = 180.0
private const val DISMISS_DESCRIPTION = "Dismiss celebration"

/**
 * Stateless celebratory burst and spring checkmark overlay rendered inside a [DesignTheme].
 *
 * Animates expanding brand, success, and accent confetti particles smoothly across frames and
 * collapses transient burst motion cleanly when [dev.abeta.kmp.tokens.MotionTokens.reducedMotion]
 * is enabled.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiCelebrationOverlay(
    visible: Boolean,
    title: String?,
    subtitle: String?,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = ThemeTokens.colors
    val motion = ThemeTokens.motion

    val enterTransition =
        if (motion.reducedMotion) {
            EnterTransition.None
        } else {
            fadeIn(tween(motion.fastMs)) +
                scaleIn(
                    initialScale = ENTER_SCALE_INITIAL,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                )
        }
    val exitTransition =
        if (motion.reducedMotion) {
            ExitTransition.None
        } else {
            fadeOut(tween(motion.fastMs)) + scaleOut(targetScale = EXIT_SCALE_TARGET)
        }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = enterTransition,
        exit = exitTransition,
        label = "celebration_overlay",
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(colors.overlay.scrim)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ).semantics {
                        role = Role.Button
                        contentDescription = DISMISS_DESCRIPTION
                    },
            contentAlignment = Alignment.Center,
        ) {
            CelebrationCard(
                visible = visible,
                title = title,
                subtitle = subtitle,
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun CelebrationCard(
    visible: Boolean,
    title: String?,
    subtitle: String?,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val cornerRadius = ThemeTokens.cornerRadius
    val strokeWidth = ThemeTokens.strokeWidth
    val typography = ThemeTokens.typography
    val shape = RoundedCornerShape(cornerRadius.lg)

    Column(
        modifier =
            Modifier
                .padding(horizontal = spacing.space24)
                .widthIn(max = CARD_MAX_WIDTH_DP.dp)
                .clip(shape)
                .background(colors.surface.elevated, shape)
                .border(strokeWidth.thin, colors.border.subtle, shape)
                .padding(horizontal = spacing.space24, vertical = spacing.space24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.space12),
    ) {
        CelebrationBurstCanvas(visible = visible)
        title?.takeIf { it.isNotBlank() }?.let { heading ->
            Text(
                text = heading,
                style = typography.titleLarge,
                color = colors.text.primary,
                textAlign = TextAlign.Center,
            )
        }
        subtitle?.takeIf { it.isNotBlank() }?.let { detail ->
            Text(
                text = detail,
                style = typography.bodyMedium,
                color = colors.text.secondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun CelebrationBurstCanvas(visible: Boolean) {
    val colors = ThemeTokens.colors
    val motion = ThemeTokens.motion
    val burstProgress = remember { Animatable(0f) }
    val checkScale = remember { Animatable(0f) }

    LaunchedEffect(visible, motion.reducedMotion) {
        if (!visible) return@LaunchedEffect
        if (motion.reducedMotion) {
            burstProgress.snapTo(1f)
            checkScale.snapTo(1f)
        } else {
            burstProgress.snapTo(0f)
            checkScale.snapTo(0f)
            launch {
                burstProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = BURST_DURATION_MS, easing = FastOutSlowInEasing),
                )
            }
            launch {
                checkScale.animateTo(
                    targetValue = 1f,
                    animationSpec =
                        spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                )
            }
        }
    }

    val palette =
        remember(colors) {
            listOf(
                colors.brand.primary,
                colors.brand.accent,
                colors.status.success,
                colors.status.warning,
                colors.brand.secondary,
            )
        }

    Canvas(modifier = Modifier.size(BURST_CANVAS_SIZE_DP.dp)) {
        val maxRadius = size.minDimension / RADIUS_DIVISOR
        if (!motion.reducedMotion && burstProgress.value < 1f) {
            drawBurstParticles(
                progress = burstProgress.value,
                palette = palette,
                maxRadius = maxRadius,
            )
        }
        drawCheckBadge(
            checkScale = checkScale.value,
            colors = colors,
            maxRadius = maxRadius,
        )
    }
}

private fun DrawScope.drawBurstParticles(
    progress: Float,
    palette: List<Color>,
    maxRadius: Float,
) {
    val fadeAlpha = (1f - progress).coerceIn(0f, 1f)
    val haloRadius = maxRadius * (HALO_START_RATIO + HALO_SPAN_RATIO * progress)
    drawCircle(
        color = palette.first().copy(alpha = fadeAlpha * HALO_ALPHA_MAX),
        radius = haloRadius,
        center = center,
        style = Stroke(width = HALO_STROKE_DP.dp.toPx()),
    )

    for (index in 0 until PARTICLE_COUNT) {
        val angleDegrees = (index.toFloat() / PARTICLE_COUNT) * FULL_CIRCLE_DEGREES
        val angleRadians = angleDegrees * (PI / STRAIGHT_ANGLE_DEGREES)
        val extraBoost = if (index % PARTICLE_ALT_STEP == 0) PARTICLE_ALT_DISTANCE_BOOST * progress else 0f
        val distance = maxRadius * (PARTICLE_MIN_DISTANCE_RATIO + PARTICLE_DISTANCE_SPAN * progress + extraBoost)
        val baseRadius =
            if (index % PARTICLE_ALT_STEP == 0) PARTICLE_ALT_RADIUS_DP else PARTICLE_BASE_RADIUS_DP
        val particleRadius = baseRadius.dp.toPx() * (1f - progress * PARTICLE_SHRINK_FACTOR)
        val offset =
            Offset(
                x = center.x + cos(angleRadians).toFloat() * distance,
                y = center.y + sin(angleRadians).toFloat() * distance,
            )
        drawCircle(
            color = palette[index % palette.size].copy(alpha = fadeAlpha),
            radius = particleRadius,
            center = offset,
        )
    }
}

private fun DrawScope.drawCheckBadge(
    checkScale: Float,
    colors: ColorTokens,
    maxRadius: Float,
) {
    val badgeRadius = (maxRadius * CHECK_BADGE_RATIO * checkScale).coerceAtLeast(0f)
    if (badgeRadius <= 0f) return

    drawCircle(
        color = colors.status.successSubtle,
        radius = badgeRadius * CHECK_HALO_SCALE,
        center = center,
    )
    drawCircle(
        color = colors.status.success,
        radius = badgeRadius,
        center = center,
    )

    val checkPath =
        Path().apply {
            moveTo(center.x + badgeRadius * CHECK_START_X, center.y + badgeRadius * CHECK_START_Y)
            lineTo(center.x + badgeRadius * CHECK_MID_X, center.y + badgeRadius * CHECK_MID_Y)
            lineTo(center.x + badgeRadius * CHECK_END_X, center.y + badgeRadius * CHECK_END_Y)
        }
    drawPath(
        path = checkPath,
        color = colors.icon.inverse,
        style =
            Stroke(
                width = CHECK_STROKE_DP.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
    )
}
