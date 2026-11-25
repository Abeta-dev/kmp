package dev.abeta.kmp.layout.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.atoms.UiButton
import dev.abeta.kmp.atoms.atoms.UiIcon
import dev.abeta.kmp.atoms.atoms.UiRollingCounter
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.atoms.modifiers.applyMotionSpec
import dev.abeta.kmp.atoms.modifiers.resolveEnterTransition
import dev.abeta.kmp.atoms.modifiers.resolveExitTransition
import dev.abeta.kmp.atoms.modifiers.resolveMotionDurationMs
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.FeedbackEffect
import dev.abeta.kmp.contracts.FloatingCartSpec
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.MotionTransition
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.layout.engine.LayoutUiState
import dev.abeta.kmp.layout.engine.computeFloatingCartSummary
import dev.abeta.kmp.layout.engine.extractProductCards
import dev.abeta.kmp.layout.engine.formatUsdCents
import dev.abeta.kmp.tokens.ThemeTokens

private const val CART_DOCK_BOUNCE_PEAK = 1.06f
private const val UNLOCKED_POP_START_SCALE = 0.92f
private const val NUDGE_PROGRESS_HEIGHT_DP = 5
private const val NUDGE_ICON_SIZE_DP = 16
private const val DEFAULT_NUDGE_PREFIX = "Add"
private const val DEFAULT_UNLOCKED_LABEL = "FREE delivery unlocked!"
private const val ONE = 1f

/**
 * Pure computed threshold progress state for the free-delivery nudge bar inside [FloatingCartDockHost].
 */
internal data class FreeDeliveryNudgeState(
    val progress: Float,
    val remainingCents: Long,
    val unlocked: Boolean,
    val message: String,
)

internal fun computeFreeDeliveryNudge(
    floatingCart: FloatingCartSpec,
    totalCents: Int,
): FreeDeliveryNudgeState? = computeFreeDeliveryNudge(floatingCart = floatingCart, totalCents = totalCents.toLong())

internal fun computeFreeDeliveryNudge(
    floatingCart: FloatingCartSpec,
    totalCents: Long,
): FreeDeliveryNudgeState? {
    val threshold = floatingCart.freeDeliveryThresholdCents?.takeIf { it > 0 }?.toLong() ?: return null
    val safeTotal = totalCents.coerceAtLeast(0L)
    val progress = (safeTotal.toDouble() / threshold.toDouble()).toFloat().coerceIn(0f, ONE)
    val remainingCents = (threshold - safeTotal).coerceAtLeast(0L)
    val unlocked = safeTotal >= threshold
    val prefix = floatingCart.nudgePrefix?.trim()?.takeIf { it.isNotEmpty() }
    val message =
        when {
            unlocked -> {
                floatingCart.unlockedLabel?.takeIf { it.isNotBlank() } ?: DEFAULT_UNLOCKED_LABEL
            }

            prefix != null && prefix.contains("$") -> {
                prefix
            }

            else -> {
                val lead = prefix ?: DEFAULT_NUDGE_PREFIX
                "$lead ${formatUsdCents(remainingCents)} more for FREE delivery"
            }
        }
    return FreeDeliveryNudgeState(
        progress = progress,
        remainingCents = remainingCents,
        unlocked = unlocked,
        message = message,
    )
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
internal fun FloatingCartDockHost(
    floatingCart: FloatingCartSpec,
    state: LayoutUiState.Ready,
    onAction: (ActionRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionTokens = ThemeTokens.motion
    val productCards = remember(state.page) { extractProductCards(state.page) }
    val summary =
        remember(productCards, state.formValues) {
            computeFloatingCartSummary(productCards, state.formValues)
        }
    val visible = !summary.hasProductCards || summary.itemCount > 0
    val resolvedLabel =
        if (summary.hasProductCards) summary.formattedLabel() else floatingCart.label
    val nudge =
        remember(floatingCart, summary.totalCents) {
            computeFreeDeliveryNudge(floatingCart = floatingCart, totalCents = summary.totalCents)
        }
    val durationMs = resolveMotionDurationMs(floatingCart.motion, motionTokens)
    val bounceScaleState =
        rememberCartBounceScaleState(
            feedbackSequence = state.feedbackSequence,
            effect = state.activeFeedback?.effect,
            itemCount = summary.itemCount,
        )

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val scale = bounceScaleState.value
                    scaleX = scale
                    scaleY = scale
                },
    ) {
        if (motionTokens.reducedMotion) {
            if (visible) {
                FloatingCartDockContent(
                    floatingCart = floatingCart,
                    label = resolvedLabel,
                    nudge = nudge,
                    onAction = onAction,
                )
            }
        } else {
            val enterPreset = floatingCart.motion?.enter ?: MotionTransition.SLIDE_UP
            val exitPreset = floatingCart.motion?.exit ?: MotionTransition.SLIDE_DOWN
            AnimatedVisibility(
                visible = visible,
                enter = resolveEnterTransition(enterPreset, durationMs),
                exit = resolveExitTransition(exitPreset, durationMs),
            ) {
                FloatingCartDockContent(
                    floatingCart = floatingCart,
                    label = resolvedLabel,
                    nudge = nudge,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun rememberCartBounceScaleState(
    feedbackSequence: Long,
    effect: FeedbackEffect?,
    itemCount: Int,
): State<Float> {
    val motionTokens = ThemeTokens.motion
    val bounceScale = remember { Animatable(ONE) }
    var previousItemCount by remember { mutableIntStateOf(itemCount) }
    var lastFeedbackSeq by remember { mutableStateOf(feedbackSequence) }
    var bounceTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(itemCount) {
        if (itemCount > previousItemCount) {
            bounceTrigger += 1
        }
        previousItemCount = itemCount
    }

    LaunchedEffect(feedbackSequence, effect) {
        if (feedbackSequence != lastFeedbackSeq) {
            lastFeedbackSeq = feedbackSequence
            if (effect == FeedbackEffect.CART_BOUNCE) {
                bounceTrigger += 1
            }
        } else if (feedbackSequence > 0L && effect == FeedbackEffect.CART_BOUNCE && bounceTrigger == 0) {
            bounceTrigger += 1
        }
    }

    LaunchedEffect(bounceTrigger, motionTokens.reducedMotion) {
        if (!motionTokens.reducedMotion && bounceTrigger > 0) {
            bounceScale.animateTo(
                targetValue = CART_DOCK_BOUNCE_PEAK,
                animationSpec = tween(durationMillis = motionTokens.fastMs),
            )
            bounceScale.animateTo(
                targetValue = ONE,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
            )
        }
    }
    return bounceScale.asState()
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun FloatingCartDockContent(
    floatingCart: FloatingCartSpec,
    label: String,
    nudge: FreeDeliveryNudgeState?,
    onAction: (ActionRef) -> Unit,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    val cornerRadius = ThemeTokens.cornerRadius
    val elevation = ThemeTokens.elevation
    val strokeWidth = ThemeTokens.strokeWidth
    val shape = RoundedCornerShape(cornerRadius.lg)
    // AnimatedVisibility already drives enter/exit transitions; only apply ambient loop effects here.
    val loopOnlyMotion = floatingCart.motion?.copy(enter = MotionTransition.NONE)

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("floating_cart_dock")
                .applyMotionSpec(motion = loopOnlyMotion, motionKey = "floating_cart_surface"),
        shape = shape,
        color = colors.surface.elevated,
        shadowElevation = elevation.lg,
        border = BorderStroke(strokeWidth.thin, colors.border.subtle),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (nudge != null) {
                FreeDeliveryNudgeStrip(nudge = nudge)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.space16, vertical = spacing.space12),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.space12),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.space4),
                ) {
                    floatingCart.etaBadge?.let { eta ->
                        UiTag(atom = eta, onAction = onAction)
                    }
                    UiRollingCounter(
                        value = label,
                        style = typography.titleSmall,
                        color = colors.text.primary,
                    )
                }
                UiButton(
                    atom = floatingCart.cta,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun FreeDeliveryNudgeStrip(nudge: FreeDeliveryNudgeState) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val cornerRadius = ThemeTokens.cornerRadius
    val motion = ThemeTokens.motion

    val animatedProgressState =
        animateFloatAsState(
            targetValue = nudge.progress,
            animationSpec = if (motion.reducedMotion) snap() else tween(durationMillis = motion.normalMs),
            label = "free_delivery_nudge_progress",
        )
    val unlockScaleState = rememberUnlockBounceScaleState(unlocked = nudge.unlocked)
    val stripBg = if (nudge.unlocked) colors.status.successSubtle else colors.surface.brandSubtle
    val barColor = if (nudge.unlocked) colors.status.success else colors.brand.primary

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("floating_cart_nudge")
                .background(stripBg)
                .padding(horizontal = spacing.space16, vertical = spacing.space8)
                .graphicsLayer {
                    val scale = unlockScaleState.value
                    scaleX = scale
                    scaleY = scale
                },
        verticalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        FreeDeliveryNudgeHeader(nudge = nudge)
        LinearProgressIndicator(
            progress = { animatedProgressState.value },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(NUDGE_PROGRESS_HEIGHT_DP.dp)
                    .clip(RoundedCornerShape(cornerRadius.full)),
            color = barColor,
            trackColor = colors.border.subtle,
        )
    }
}

@Composable
private fun rememberUnlockBounceScaleState(unlocked: Boolean): State<Float> {
    val motion = ThemeTokens.motion
    val unlockScale = remember { Animatable(ONE) }
    LaunchedEffect(unlocked, motion.reducedMotion) {
        if (unlocked && !motion.reducedMotion) {
            unlockScale.snapTo(UNLOCKED_POP_START_SCALE)
            unlockScale.animateTo(
                targetValue = ONE,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
            )
        } else {
            unlockScale.snapTo(ONE)
        }
    }
    return unlockScale.asState()
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun FreeDeliveryNudgeHeader(nudge: FreeDeliveryNudgeState) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space8),
    ) {
        if (nudge.unlocked) {
            UiTag(atom = TagAtom(label = "FREE", variant = TagVariant.SUCCESS))
            Text(
                text = nudge.message,
                style = typography.labelLarge,
                color = colors.text.success,
                modifier = Modifier.weight(1f),
            )
        } else {
            UiIcon(
                atom =
                    IconAtom(
                        name = "star",
                        sizeDp = NUDGE_ICON_SIZE_DP,
                        color = ColorTokenRef(token = "icon.brand"),
                    ),
            )
            val amountText = formatUsdCents(nudge.remainingCents)
            val amountStart = nudge.message.indexOf(amountText)
            if (amountStart >= 0) {
                NudgeRemainingAmountRow(
                    message = nudge.message,
                    amountText = amountText,
                    amountStart = amountStart,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    text = nudge.message,
                    style = typography.labelMedium,
                    color = colors.text.brand,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun NudgeRemainingAmountRow(
    message: String,
    amountText: String,
    amountStart: Int,
    modifier: Modifier = Modifier,
) {
    val colors = ThemeTokens.colors
    val typography = ThemeTokens.typography
    val leadText = message.substring(0, amountStart)
    val trailText = message.substring(amountStart + amountText.length)
    Row(
        modifier = modifier.clearAndSetSemantics { text = AnnotatedString(message) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadText.isNotEmpty()) {
            Text(text = leadText, style = typography.labelMedium, color = colors.text.brand, maxLines = 1)
        }
        UiRollingCounter(value = amountText, style = typography.labelMedium, color = colors.text.brand)
        if (trailText.isNotEmpty()) {
            Text(
                text = trailText,
                style = typography.labelMedium,
                color = colors.text.brand,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}
