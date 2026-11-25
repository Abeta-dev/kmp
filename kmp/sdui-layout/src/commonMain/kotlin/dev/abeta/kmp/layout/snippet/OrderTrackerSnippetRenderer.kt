package dev.abeta.kmp.layout.snippet

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.atoms.UiAvatar
import dev.abeta.kmp.atoms.atoms.UiButton
import dev.abeta.kmp.atoms.atoms.UiCountdown
import dev.abeta.kmp.atoms.atoms.UiIcon
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.atoms.atoms.UiText
import dev.abeta.kmp.atoms.modifiers.applyMotionSpec
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.AvatarAtom
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.MotionLoopEffect
import dev.abeta.kmp.contracts.MotionSpec
import dev.abeta.kmp.contracts.OrderTrackerSnippetData
import dev.abeta.kmp.contracts.OrderTrackerStep
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private const val STEP_DOT_SIZE_DP = 26
private const val STEP_ACTIVE_SCALE = 1.12f
private const val STEP_ICON_SIZE_DP = 14
private const val STEP_INNER_DOT_SIZE_DP = 8
private const val CONNECTOR_HEIGHT_DP = 4
private const val RIDER_AVATAR_SIZE_DP = 36
private const val ONE = 1f

/**
 * Pure helper computing normalized `[0f, 1f]` timeline completion progress across [steps].
 */
internal fun resolveOrderTrackerProgress(steps: List<OrderTrackerStep>): Float =
    when {
        steps.isEmpty() -> {
            0f
        }

        steps.size == 1 -> {
            val only = steps.first()
            if (only.completed || only.active) ONE else 0f
        }

        else -> {
            val targetIndex = maxOf(steps.indexOfLast { it.active }, steps.indexOfLast { it.completed })
            if (targetIndex < 0) 0f else (targetIndex.toFloat() / (steps.size - 1).toFloat()).coerceIn(0f, ONE)
        }
    }

internal class OrderTrackerSnippetRenderer(
    json: Json,
) : SnippetRenderer<OrderTrackerSnippetData> {
    override val snippetType: String = SnippetTypes.ORDER_TRACKER
    private val decoder = kotlinxSnippetDecoder<OrderTrackerSnippetData>(json)

    override fun decode(data: JsonObject): AppResult<OrderTrackerSnippetData> = decoder(data)

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    override fun Render(
        model: OrderTrackerSnippetData,
        envelope: SnippetEnvelope,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        val spacing = ThemeTokens.spacing

        UiSurfaceCard(
            modifier = Modifier.fillMaxWidth().testTag("order_tracker_${model.trackerId}"),
            layout = model.layout,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.space12),
            ) {
                OrderTrackerHeader(model = model, onAction = onAction)
                if (model.steps.isNotEmpty()) {
                    OrderTrackerTimeline(steps = model.steps, onAction = onAction)
                }
                model.riderName?.takeIf { it.isNotBlank() }?.let { rider ->
                    OrderTrackerRiderCard(
                        trackerId = model.trackerId,
                        riderName = rider,
                        riderCaption = model.riderCaption,
                    )
                }
                OrderTrackerActions(model = model, onAction = onAction)
            }
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun OrderTrackerHeader(
        model: OrderTrackerSnippetData,
        onAction: (ActionRef) -> Unit,
    ) {
        val spacing = ThemeTokens.spacing
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space12),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.space4),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                ) {
                    if (model.orderCode.isNotBlank()) {
                        UiTag(atom = TagAtom(label = model.orderCode, variant = TagVariant.NEUTRAL))
                    }
                    model.etaBadge?.let { badge ->
                        UiTag(atom = badge, onAction = onAction)
                    }
                }
                UiText(atom = model.headline, onAction = onAction)
            }
            model.etaCountdown?.let { countdown ->
                UiCountdown(atom = countdown, onAction = onAction)
            }
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun OrderTrackerTimeline(
        steps: List<OrderTrackerStep>,
        onAction: (ActionRef) -> Unit,
    ) {
        val colors = ThemeTokens.colors
        val spacing = ThemeTokens.spacing
        val cornerRadius = ThemeTokens.cornerRadius
        val motion = ThemeTokens.motion
        val progress = resolveOrderTrackerProgress(steps)
        val animatedProgressState =
            animateFloatAsState(
                targetValue = progress,
                animationSpec = if (motion.reducedMotion) snap() else tween(durationMillis = motion.normalMs),
                label = "order_tracker_progress",
            )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.space8),
        ) {
            LinearProgressIndicator(
                progress = { animatedProgressState.value },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(CONNECTOR_HEIGHT_DP.dp)
                        .clip(RoundedCornerShape(cornerRadius.full)),
                color = colors.status.success,
                trackColor = colors.border.subtle,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.space4),
            ) {
                steps.forEach { step ->
                    OrderTrackerStepItem(
                        step = step,
                        modifier = Modifier.weight(1f),
                        onAction = onAction,
                    )
                }
            }
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun OrderTrackerStepItem(
        step: OrderTrackerStep,
        modifier: Modifier = Modifier,
        onAction: (ActionRef) -> Unit,
    ) {
        val colors = ThemeTokens.colors
        val spacing = ThemeTokens.spacing
        val typography = ThemeTokens.typography
        val motion = ThemeTokens.motion
        val dotScaleState =
            animateFloatAsState(
                targetValue = if (step.active && !motion.reducedMotion) STEP_ACTIVE_SCALE else ONE,
                animationSpec =
                    if (motion.reducedMotion) {
                        snap()
                    } else {
                        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                    },
                label = "order_step_scale_${step.id}",
            )

        Column(
            modifier = modifier.testTag("order_step_${step.id}"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            OrderTrackerStepDot(
                step = step,
                scaleState = dotScaleState,
                onAction = onAction,
            )
            Text(
                text = step.label,
                style = typography.labelSmall,
                fontWeight = if (step.active || step.completed) FontWeight.SemiBold else FontWeight.Normal,
                color = if (step.active || step.completed) colors.text.primary else colors.text.tertiary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            step.caption?.takeIf { it.isNotBlank() }?.let { caption ->
                Text(
                    text = caption,
                    style = typography.caption,
                    color = colors.text.secondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun OrderTrackerStepDot(
        step: OrderTrackerStep,
        scaleState: State<Float>,
        onAction: (ActionRef) -> Unit,
    ) {
        val colors = ThemeTokens.colors
        val strokeWidth = ThemeTokens.strokeWidth
        val outerModifier =
            Modifier
                .size(STEP_DOT_SIZE_DP.dp)
                .graphicsLayer {
                    val scale = scaleState.value
                    scaleX = scale
                    scaleY = scale
                }.let { base ->
                    if (step.active && !step.completed) {
                        base.applyMotionSpec(
                            motion = MotionSpec(loopEffect = MotionLoopEffect.GLOW_RING),
                            motionKey = "step_glow_${step.id}",
                        )
                    } else {
                        base
                    }
                }
        val innerModifier = Modifier.size(STEP_DOT_SIZE_DP.dp).clip(CircleShape)

        Box(modifier = outerModifier, contentAlignment = Alignment.Center) {
            when {
                step.completed -> {
                    CompletedStepDot(step = step, modifier = innerModifier)
                }

                step.active -> {
                    ActiveStepDot(step = step, modifier = innerModifier, onAction = onAction)
                }

                else -> {
                    Box(
                        modifier =
                            innerModifier
                                .background(colors.surface.secondary, CircleShape)
                                .border(strokeWidth.thin, colors.border.default, CircleShape),
                    )
                }
            }
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun CompletedStepDot(
        step: OrderTrackerStep,
        modifier: Modifier,
    ) {
        Box(
            modifier = modifier.background(ThemeTokens.colors.status.success, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            UiIcon(
                atom =
                    IconAtom(
                        name = "check",
                        sizeDp = STEP_ICON_SIZE_DP,
                        color = ColorTokenRef(token = "text.inverse"),
                        contentDescription = "${step.label} completed",
                    ),
            )
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun ActiveStepDot(
        step: OrderTrackerStep,
        modifier: Modifier,
        onAction: (ActionRef) -> Unit,
    ) {
        val colors = ThemeTokens.colors
        Box(
            modifier = modifier.background(colors.brand.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            val icon = step.icon
            if (icon != null) {
                UiIcon(
                    atom =
                        icon.copy(
                            sizeDp = icon.sizeDp ?: STEP_ICON_SIZE_DP,
                            color = icon.color ?: ColorTokenRef(token = "icon.on_brand"),
                        ),
                    onAction = onAction,
                )
            } else {
                Box(
                    modifier =
                        Modifier
                            .size(STEP_INNER_DOT_SIZE_DP.dp)
                            .clip(CircleShape)
                            .background(colors.brand.onPrimary, CircleShape),
                )
            }
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun OrderTrackerRiderCard(
        trackerId: String,
        riderName: String,
        riderCaption: String?,
    ) {
        val colors = ThemeTokens.colors
        val spacing = ThemeTokens.spacing
        val typography = ThemeTokens.typography
        val shape = RoundedCornerShape(ThemeTokens.cornerRadius.md)

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("order_tracker_rider_$trackerId")
                    .clip(shape)
                    .background(colors.surface.secondary, shape)
                    .padding(horizontal = spacing.space12, vertical = spacing.space8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space12),
        ) {
            UiAvatar(atom = AvatarAtom(contentDescription = riderName, sizeDp = RIDER_AVATAR_SIZE_DP))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.space2),
            ) {
                Text(
                    text = riderName,
                    style = typography.titleSmall,
                    color = colors.text.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                riderCaption?.takeIf { it.isNotBlank() }?.let { caption ->
                    Text(
                        text = caption,
                        style = typography.bodySmall,
                        color = colors.text.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun OrderTrackerActions(
        model: OrderTrackerSnippetData,
        onAction: (ActionRef) -> Unit,
    ) {
        val primary = model.primaryAction
        val secondary = model.secondaryAction
        if (primary == null && secondary == null) return

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ThemeTokens.spacing.space8),
        ) {
            secondary?.let { btn ->
                UiButton(atom = btn, modifier = Modifier.weight(1f), onAction = onAction)
            }
            primary?.let { btn ->
                UiButton(atom = btn, modifier = Modifier.weight(1f), onAction = onAction)
            }
        }
    }
}
