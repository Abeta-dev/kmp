@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.layout.snippet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.atoms.UiButton
import dev.abeta.kmp.atoms.atoms.UiCountdown
import dev.abeta.kmp.atoms.atoms.UiIcon
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.atoms.atoms.UiText
import dev.abeta.kmp.atoms.modifiers.LocalUiHapticFeedback
import dev.abeta.kmp.atoms.modifiers.UiHapticKind
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.atoms.modifiers.pressBounce
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.MarqueeItem
import dev.abeta.kmp.contracts.MarqueeStripSnippetData
import dev.abeta.kmp.contracts.NudgeBarSnippetData
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private const val DEFAULT_MARQUEE_VELOCITY_DP = 48
private const val MIN_MARQUEE_VELOCITY_DP = 12
private const val MAX_MARQUEE_VELOCITY_DP = 240
private const val MIN_MARQUEE_CYCLE_MS = 1800
private const val FALLBACK_MARQUEE_CYCLE_MS = 8000
private const val MILLIS_PER_SECOND = 1000f
private const val CARET_WIDTH_DP = 14
private const val CARET_HEIGHT_DP = 7
private const val DISMISS_CIRCLE_SIZE_DP = 28
private const val DISMISS_GLYPH = "×"
private const val MARQUEE_ICON_SIZE_DP = 16
private const val MARQUEE_COPY_COUNT = 3
private const val MAX_FRAME_DELTA_SEC = 0.05f
private const val NANOS_PER_SECOND = 1_000_000_000f

internal fun resolveMarqueeVelocityDpPerSec(velocityDpPerSec: Int): Int =
    if (velocityDpPerSec <= 0) {
        DEFAULT_MARQUEE_VELOCITY_DP
    } else {
        velocityDpPerSec.coerceIn(MIN_MARQUEE_VELOCITY_DP, MAX_MARQUEE_VELOCITY_DP)
    }

internal fun resolveMarqueeCycleDurationMs(
    contentWidthPx: Int,
    density: Float,
    velocityDpPerSec: Int,
): Int {
    if (contentWidthPx <= 0 || density <= 0f) {
        return FALLBACK_MARQUEE_CYCLE_MS
    }
    val safeVelocityPxPerSec = resolveMarqueeVelocityDpPerSec(velocityDpPerSec) * density
    val millis = ((contentWidthPx / safeVelocityPxPerSec) * MILLIS_PER_SECOND).toInt()
    return millis.coerceAtLeast(MIN_MARQUEE_CYCLE_MS)
}

internal class NudgeBarSnippetRenderer(
    json: Json,
) : SnippetRenderer<NudgeBarSnippetData> {
    override val snippetType: String = SnippetTypes.NUDGE_BAR
    private val decoder = kotlinxSnippetDecoder<NudgeBarSnippetData>(json)

    override fun decode(data: JsonObject): AppResult<NudgeBarSnippetData> = decoder(data)

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    override fun Render(
        model: NudgeBarSnippetData,
        envelope: SnippetEnvelope,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        val motion = ThemeTokens.motion
        var dismissed by remember(envelope.snippetId, model.nudgeId) { mutableStateOf(false) }
        val handleDismiss: () -> Unit = {
            dismissed = true
            model.onDismiss?.let(onAction)
        }

        if (motion.reducedMotion) {
            if (!dismissed) {
                NudgeBarCard(model = model, onDismiss = handleDismiss, onAction = onAction)
            }
        } else {
            AnimatedVisibility(visible = !dismissed, exit = shrinkVertically() + fadeOut()) {
                NudgeBarCard(model = model, onDismiss = handleDismiss, onAction = onAction)
            }
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun NudgeBarCard(
        model: NudgeBarSnippetData,
        onDismiss: () -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        val colors = ThemeTokens.colors
        val spacing = ThemeTokens.spacing
        val motion = ThemeTokens.motion
        val edge = model.pointerEdge?.trim()?.lowercase()
        val caretColor = colors.brand.primary

        Column(modifier = Modifier.fillMaxWidth()) {
            if (edge == "top") {
                NudgePointerCaret(pointUp = true, color = caretColor)
            }
            UiSurfaceCard(
                modifier = Modifier.fillMaxWidth().testTag("nudge_bar_${model.nudgeId}"),
                layout = model.layout,
                onClick = model.onClick?.let { action -> { onAction(action) } },
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(spacing.space8),
                ) {
                    NudgeHeaderRow(model = model, onDismiss = onDismiss, onAction = onAction)
                    model.progress?.let { rawProgress ->
                        val animatedProgressState =
                            animateFloatAsState(
                                targetValue = rawProgress.coerceIn(0f, 1f),
                                animationSpec =
                                    if (motion.reducedMotion) {
                                        snap()
                                    } else {
                                        tween(durationMillis = motion.normalMs, easing = FastOutSlowInEasing)
                                    },
                                label = "nudge_progress_${model.nudgeId}",
                            )
                        LinearProgressIndicator(
                            progress = { animatedProgressState.value },
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.surface.brand,
                            trackColor = colors.border.subtle,
                        )
                    }
                    model.cta?.let { cta ->
                        UiButton(atom = cta, onAction = onAction)
                    }
                }
            }
            if (edge == "bottom") {
                NudgePointerCaret(pointUp = false, color = caretColor)
            }
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun NudgeHeaderRow(
        model: NudgeBarSnippetData,
        onDismiss: () -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        val spacing = ThemeTokens.spacing
        val hasMetaRow = model.badge != null || model.countdown != null || model.icon != null
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            if (hasMetaRow) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                ) {
                    model.icon?.let { icon ->
                        UiIcon(atom = icon, onAction = onAction)
                    }
                    model.badge?.let { badge ->
                        UiTag(atom = badge, onAction = onAction)
                    }
                    Box(modifier = Modifier.weight(1f))
                    model.countdown?.let { countdown ->
                        UiCountdown(atom = countdown, onAction = onAction)
                    }
                    if (model.dismissible) {
                        NudgeDismissButton(nudgeId = model.nudgeId, onDismiss = onDismiss)
                    }
                }
                UiText(atom = model.title, onAction = onAction)
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                ) {
                    UiText(
                        atom = model.title,
                        modifier = Modifier.weight(1f),
                        onAction = onAction,
                    )
                    if (model.dismissible) {
                        NudgeDismissButton(nudgeId = model.nudgeId, onDismiss = onDismiss)
                    }
                }
            }
            model.subtitle?.let { subtitle ->
                UiText(atom = subtitle, onAction = onAction)
            }
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun NudgeDismissButton(
        nudgeId: String,
        onDismiss: () -> Unit,
    ) {
        val colors = ThemeTokens.colors
        val sizing = ThemeTokens.sizing
        val haptic = LocalUiHapticFeedback.current
        Box(
            modifier =
                Modifier
                    .defaultMinSize(minWidth = sizing.minTouchTarget, minHeight = sizing.minTouchTarget)
                    .clickable(role = Role.Button) {
                        haptic(UiHapticKind.CLICK)
                        onDismiss()
                    }.semantics { contentDescription = "Dismiss nudge" }
                    .testTag("nudge_dismiss_$nudgeId"),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(DISMISS_CIRCLE_SIZE_DP.dp)
                        .clip(CircleShape)
                        .background(colors.surface.secondary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = DISMISS_GLYPH,
                    style = ThemeTokens.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.text.secondary,
                )
            }
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun NudgePointerCaret(
        pointUp: Boolean,
        color: Color,
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ThemeTokens.spacing.space24),
            contentAlignment = Alignment.CenterStart,
        ) {
            Canvas(modifier = Modifier.size(width = CARET_WIDTH_DP.dp, height = CARET_HEIGHT_DP.dp)) {
                val path =
                    Path().apply {
                        if (pointUp) {
                            moveTo(size.width / 2f, 0f)
                            lineTo(size.width, size.height)
                            lineTo(0f, size.height)
                        } else {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width / 2f, size.height)
                        }
                        close()
                    }
                drawPath(path = path, color = color)
            }
        }
    }
}

internal class MarqueeStripSnippetRenderer(
    json: Json,
) : SnippetRenderer<MarqueeStripSnippetData> {
    override val snippetType: String = SnippetTypes.MARQUEE_STRIP
    private val decoder = kotlinxSnippetDecoder<MarqueeStripSnippetData>(json)

    override fun decode(data: JsonObject): AppResult<MarqueeStripSnippetData> = decoder(data)

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    override fun Render(
        model: MarqueeStripSnippetData,
        envelope: SnippetEnvelope,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        if (model.items.isEmpty()) return

        val colors = ThemeTokens.colors
        val spacing = ThemeTokens.spacing
        val cornerRadius = ThemeTokens.cornerRadius
        val motion = ThemeTokens.motion
        val shape = RoundedCornerShape(cornerRadius.md)
        val containerModifier =
            if (model.layout != null) {
                Modifier.fillMaxWidth().applyLayoutConfig(model.layout)
            } else {
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(colors.surface.brandSubtle, shape)
                    .padding(horizontal = spacing.space12, vertical = spacing.space8)
            }

        Box(
            modifier = containerModifier.clipToBounds().testTag("marquee_strip_${model.stripId}"),
        ) {
            if (motion.reducedMotion) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.space12),
                ) {
                    MarqueeItemsContent(
                        items = model.items,
                        separator = model.separator,
                        includeTestTags = true,
                        onAction = onAction,
                    )
                }
            } else {
                AnimatedMarqueeTrack(model = model, onAction = onAction)
            }
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun AnimatedMarqueeTrack(
        model: MarqueeStripSnippetData,
        onAction: (ActionRef) -> Unit,
    ) {
        val spacing = ThemeTokens.spacing
        val density = LocalDensity.current.density
        val velocityPxPerSec = resolveMarqueeVelocityDpPerSec(model.velocityDpPerSec) * density
        var cycleWidthPx by remember(model.stripId, model.items) { mutableIntStateOf(0) }
        val offsetPxState = remember(model.stripId) { mutableFloatStateOf(0f) }

        LaunchedEffect(model.stripId, velocityPxPerSec) {
            var lastFrameNanos = 0L
            while (true) {
                withInfiniteAnimationFrameNanos { frameNanos ->
                    if (lastFrameNanos != 0L) {
                        val dtSec = ((frameNanos - lastFrameNanos) / NANOS_PER_SECOND).coerceIn(0f, MAX_FRAME_DELTA_SEC)
                        val width = cycleWidthPx
                        if (width > 0) {
                            val next = offsetPxState.floatValue + (velocityPxPerSec * dtSec)
                            offsetPxState.floatValue = if (next >= width) next % width else next
                        }
                    }
                    lastFrameNanos = frameNanos
                }
            }
        }

        Row(
            modifier =
                Modifier
                    .wrapContentWidth(unbounded = true, align = Alignment.Start)
                    .graphicsLayer {
                        val widthPx = cycleWidthPx
                        translationX = if (widthPx > 0) -offsetPxState.floatValue else 0f
                    },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(MARQUEE_COPY_COUNT) { copyIndex ->
                val primaryCopy = copyIndex == 0
                val copyModifier =
                    if (primaryCopy) {
                        Modifier.onSizeChanged { size -> cycleWidthPx = size.width }.padding(end = spacing.space16)
                    } else {
                        Modifier.padding(end = spacing.space16)
                    }
                Row(
                    modifier = copyModifier,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.space12),
                ) {
                    MarqueeItemsContent(
                        items = model.items,
                        separator = model.separator,
                        includeTestTags = primaryCopy,
                        onAction = onAction,
                    )
                }
            }
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun MarqueeItemsContent(
        items: List<MarqueeItem>,
        separator: String,
        includeTestTags: Boolean,
        onAction: (ActionRef) -> Unit,
    ) {
        val colors = ThemeTokens.colors
        val spacing = ThemeTokens.spacing
        val typography = ThemeTokens.typography
        val haptic = LocalUiHapticFeedback.current
        val sep = separator.takeIf { it.isNotBlank() }

        items.forEachIndexed { index, item ->
            val clickAction = item.onClick
            val itemModifier =
                Modifier
                    .let { if (includeTestTags) it.testTag("marquee_item_${item.id}") else it }
                    .let { base ->
                        if (clickAction != null) {
                            base.pressBounce().clickable(role = Role.Button) {
                                haptic(UiHapticKind.CLICK)
                                onAction(clickAction)
                            }
                        } else {
                            base
                        }
                    }

            Row(
                modifier = itemModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.space4),
            ) {
                item.icon?.let { icon ->
                    UiIcon(atom = icon.copy(sizeDp = icon.sizeDp ?: MARQUEE_ICON_SIZE_DP), onAction = onAction)
                }
                item.badge?.let { badge ->
                    UiTag(atom = badge, onAction = onAction)
                }
                Text(
                    text = item.text,
                    style = typography.labelLarge,
                    color = colors.text.primary,
                    maxLines = 1,
                )
            }
            if (sep != null && index < items.lastIndex) {
                Text(
                    text = sep,
                    style = typography.labelMedium,
                    color = colors.text.tertiary,
                    maxLines = 1,
                )
            }
        }
    }
}
