package dev.abeta.kmp.layout.snippet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.modifiers.LocalUiHapticFeedback
import dev.abeta.kmp.atoms.modifiers.UiHapticKind
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.SwipeCardItem
import dev.abeta.kmp.contracts.SwipeDeckSnippetData
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.tokens.MotionTokens
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.math.abs
import kotlin.math.max

private const val MAX_VISIBLE_STACK_CARDS = 3
private const val HORIZONTAL_SWIPE_THRESHOLD_DP = 110
private const val VERTICAL_SWIPE_THRESHOLD_DP = 96
private const val HORIZONTAL_EXIT_OFFSET_DP = 520
private const val VERTICAL_EXIT_OFFSET_DP = 480
private const val STAMP_ACTIVATION_DP = 20
private const val STACK_BOTTOM_PEEK_DP = 24
private const val PEEK_SCALE_STEP = 0.045f
private const val PEEK_OFFSET_Y_DP = 12f
private const val PEEK_ALPHA_STEP = 0.10f
private const val MIN_PEEK_ALPHA = 0.72f
private const val ROTATION_DIVISOR = 24f
private const val MAX_ROTATION_DEG = 14f

internal enum class SwipeDirection {
    LIKE,
    NOPE,
    SUPER_LIKE,
}

internal fun resolveSwipeDirection(
    offsetX: Float,
    offsetY: Float,
    horizontalThresholdPx: Float,
    verticalThresholdPx: Float,
): SwipeDirection? =
    when {
        offsetX >= horizontalThresholdPx && abs(offsetX) >= abs(offsetY) -> SwipeDirection.LIKE
        offsetX <= -horizontalThresholdPx && abs(offsetX) >= abs(offsetY) -> SwipeDirection.NOPE
        offsetY <= -verticalThresholdPx && abs(offsetY) > abs(offsetX) -> SwipeDirection.SUPER_LIKE
        offsetX >= horizontalThresholdPx -> SwipeDirection.LIKE
        offsetX <= -horizontalThresholdPx -> SwipeDirection.NOPE
        offsetY <= -verticalThresholdPx -> SwipeDirection.SUPER_LIKE
        else -> null
    }

internal fun resolveSwipeAction(
    card: SwipeCardItem,
    deck: SwipeDeckSnippetData,
    direction: SwipeDirection,
): ActionRef? =
    when (direction) {
        SwipeDirection.LIKE -> card.onLike ?: deck.onLike
        SwipeDirection.NOPE -> card.onNope ?: deck.onNope
        SwipeDirection.SUPER_LIKE -> card.onSuperLike ?: deck.onSuperLike
    }

private fun SwipeThresholds.exitTargetFor(
    direction: SwipeDirection,
    startY: Float,
): Offset =
    when (direction) {
        SwipeDirection.LIKE -> Offset(horizontalExitPx, startY)
        SwipeDirection.NOPE -> Offset(-horizontalExitPx, startY)
        SwipeDirection.SUPER_LIKE -> Offset(0f, -verticalExitPx)
    }

private fun snapBackOffset(
    scope: CoroutineScope,
    animatedOffset: Animatable<Offset, *>,
    fromOffset: Offset,
    reducedMotion: Boolean,
) {
    scope.launch {
        animatedOffset.snapTo(fromOffset)
        if (reducedMotion) {
            animatedOffset.snapTo(Offset.Zero)
        } else {
            animatedOffset.animateTo(
                targetValue = Offset.Zero,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
            )
        }
    }
}

private class SwipeDeckState(
    private val model: SwipeDeckSnippetData,
    private val thresholds: SwipeThresholds,
    private val motion: MotionTokens,
    private val scope: CoroutineScope,
    private val haptic: (UiHapticKind) -> Unit,
    private val onAction: (ActionRef) -> Unit,
) {
    var currentIndex by mutableIntStateOf(0)
    var swipedHistory by mutableStateOf<List<Int>>(emptyList())
    var isAnimatingExit by mutableStateOf(false)
    val topCard: SwipeCardItem?
        get() = model.cards.getOrNull(currentIndex)

    fun swipe(
        direction: SwipeDirection,
        startOffset: Offset?,
        animatedOffset: Animatable<Offset, *>,
    ) {
        val activeCard = topCard ?: return
        if (isAnimatingExit) return
        haptic(if (direction == SwipeDirection.NOPE) UiHapticKind.WARNING else UiHapticKind.SUCCESS)
        val action = resolveSwipeAction(activeCard, model, direction)
        val advance: () -> Unit = {
            swipedHistory = swipedHistory + currentIndex
            currentIndex += 1
            isAnimatingExit = false
            action?.let(onAction)
        }
        if (motion.reducedMotion) {
            advance()
        } else {
            isAnimatingExit = true
            val start = startOffset ?: animatedOffset.value
            val target = thresholds.exitTargetFor(direction, start.y)
            scope.launch {
                animatedOffset.snapTo(start)
                animatedOffset.animateTo(target, tween(durationMillis = motion.fastMs))
                advance()
            }
        }
    }

    fun handleDragRelease(
        finalOffset: Offset,
        cancelled: Boolean,
        animatedOffset: Animatable<Offset, *>,
    ) {
        val direction =
            if (cancelled) {
                null
            } else {
                resolveSwipeDirection(finalOffset.x, finalOffset.y, thresholds.horizontalPx, thresholds.verticalPx)
            }
        if (direction != null) {
            swipe(direction, finalOffset, animatedOffset)
        } else {
            snapBackOffset(scope, animatedOffset, finalOffset, motion.reducedMotion)
        }
    }

    fun undo() {
        val previousIndex = swipedHistory.lastOrNull() ?: return
        if (isAnimatingExit) return
        haptic(UiHapticKind.CLICK)
        swipedHistory = swipedHistory.dropLast(1)
        currentIndex = previousIndex
        model.onUndo?.let(onAction)
    }

    fun reset() {
        if (isAnimatingExit) return
        haptic(UiHapticKind.CLICK)
        currentIndex = 0
        swipedHistory = emptyList()
    }
}

internal class SwipeDeckSnippetRenderer(
    json: Json,
) : SnippetRenderer<SwipeDeckSnippetData> {
    override val snippetType: String = SnippetTypes.SWIPE_DECK
    private val decoder = kotlinxSnippetDecoder<SwipeDeckSnippetData>(json)

    override fun decode(data: JsonObject): AppResult<SwipeDeckSnippetData> = decoder(data)

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    override fun Render(
        model: SwipeDeckSnippetData,
        envelope: SnippetEnvelope,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        val spacing = ThemeTokens.spacing
        val motion = ThemeTokens.motion
        val haptic = LocalUiHapticFeedback.current
        val coroutineScope = rememberCoroutineScope()
        val thresholds = rememberSwipeThresholds()
        val cardsKey = remember(model.cards) { model.cards.map { it.cardId } }
        val state =
            remember(envelope.snippetId, model.deckId, cardsKey, motion) {
                SwipeDeckState(
                    model = model,
                    thresholds = thresholds,
                    motion = motion,
                    scope = coroutineScope,
                    haptic = haptic,
                    onAction = onAction,
                )
            }
        val topCard = state.topCard
        val animatedOffset = remember(topCard?.cardId) { Animatable(Offset.Zero, Offset.VectorConverter) }
        val callbacks =
            SwipeDeckCallbacks(
                onSwipe = { direction, startOffset -> state.swipe(direction, startOffset, animatedOffset) },
                onUndo = state::undo,
                onReset = state::reset,
                onAction = onAction,
            )

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .applyLayoutConfig(model.layout)
                    .testTag("swipe_deck_${model.deckId}"),
            verticalArrangement = Arrangement.spacedBy(spacing.space16),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (topCard != null) {
                SwipeCardStack(model, state, animatedOffset, thresholds, onAction)
            } else {
                SwipeDeckEmptyCard(model, state.swipedHistory.isNotEmpty(), callbacks.onReset, onAction)
            }
            if (model.cards.isNotEmpty()) {
                SwipeActionBar(
                    canUndo = state.swipedHistory.isNotEmpty() && !state.isAnimatingExit,
                    canSwipe = topCard != null && !state.isAnimatingExit,
                    callbacks = callbacks,
                )
            }
        }
    }

    @Composable
    private fun rememberSwipeThresholds(): SwipeThresholds {
        val density = LocalDensity.current
        return remember(density) {
            with(density) {
                SwipeThresholds(
                    horizontalPx = HORIZONTAL_SWIPE_THRESHOLD_DP.dp.toPx(),
                    verticalPx = VERTICAL_SWIPE_THRESHOLD_DP.dp.toPx(),
                    horizontalExitPx = HORIZONTAL_EXIT_OFFSET_DP.dp.toPx(),
                    verticalExitPx = VERTICAL_EXIT_OFFSET_DP.dp.toPx(),
                    stampActivationPx = STAMP_ACTIVATION_DP.dp.toPx(),
                )
            }
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun SwipeCardStack(
        model: SwipeDeckSnippetData,
        state: SwipeDeckState,
        animatedOffset: Animatable<Offset, *>,
        thresholds: SwipeThresholds,
        onAction: (ActionRef) -> Unit,
    ) {
        val reducedMotion = ThemeTokens.motion.reducedMotion
        val density = LocalDensity.current.density
        val coroutineScope = rememberCoroutineScope()
        val topCard = model.cards[state.currentIndex]
        var isDragging by remember(topCard.cardId) { mutableStateOf(false) }
        var dragOffset by remember(topCard.cardId) { mutableStateOf(Offset.Zero) }
        val activeOffset = if (isDragging) dragOffset else animatedOffset.value
        val dragProgress =
            max(
                abs(activeOffset.x) / thresholds.horizontalPx,
                max(0f, -activeOffset.y) / thresholds.verticalPx,
            ).coerceIn(0f, 1f)
        val endExclusive = minOf(state.currentIndex + MAX_VISIBLE_STACK_CARDS, model.cards.size)
        val visibleCards = model.cards.subList(state.currentIndex, endExclusive)

        Box(
            modifier = Modifier.fillMaxWidth().padding(bottom = STACK_BOTTOM_PEEK_DP.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            for (depth in visibleCards.lastIndex downTo 0) {
                val card = visibleCards[depth]
                val isTop = depth == 0
                val layerModifier =
                    if (isTop) {
                        topCardModifier(
                            cardId = card.cardId,
                            activeOffset = activeOffset,
                            reducedMotion = reducedMotion,
                            isAnimatingExit = state.isAnimatingExit,
                            onDragStart = {
                                isDragging = true
                                dragOffset = animatedOffset.value
                                coroutineScope.launch { animatedOffset.stop() }
                            },
                            onDragDelta = { delta -> dragOffset += delta },
                            onDragEnd = { cancelled ->
                                val finalOffset = dragOffset
                                isDragging = false
                                dragOffset = Offset.Zero
                                state.handleDragRelease(finalOffset, cancelled, animatedOffset)
                            },
                        )
                    } else {
                        peekCardModifier(depth, dragProgress, reducedMotion, density)
                    }

                Box(modifier = layerModifier) {
                    SwipeCardContent(card, isTop && !state.isAnimatingExit, onAction)
                    if (isTop) {
                        SwipeStampOverlays(model, activeOffset, thresholds, reducedMotion)
                    }
                }
            }
        }
    }

    private fun peekCardModifier(
        depth: Int,
        dragProgress: Float,
        reducedMotion: Boolean,
        density: Float,
    ): Modifier {
        val effectiveDepth = if (reducedMotion) depth.toFloat() else (depth - dragProgress).coerceAtLeast(0f)
        val scale = 1f - (effectiveDepth * PEEK_SCALE_STEP)
        val peekTranslateY = effectiveDepth * PEEK_OFFSET_Y_DP * density
        val layerAlpha = (1f - (effectiveDepth * PEEK_ALPHA_STEP)).coerceIn(MIN_PEEK_ALPHA, 1f)
        return Modifier.fillMaxWidth().graphicsLayer {
            scaleX = scale
            scaleY = scale
            translationY = peekTranslateY
            alpha = layerAlpha
        }
    }

    @Suppress("LongParameterList")
    private fun topCardModifier(
        cardId: String,
        activeOffset: Offset,
        reducedMotion: Boolean,
        isAnimatingExit: Boolean,
        onDragStart: () -> Unit,
        onDragDelta: (Offset) -> Unit,
        onDragEnd: (Boolean) -> Unit,
    ): Modifier =
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = activeOffset.x
                translationY = activeOffset.y
                rotationZ =
                    if (reducedMotion) {
                        0f
                    } else {
                        (activeOffset.x / ROTATION_DIVISOR).coerceIn(-MAX_ROTATION_DEG, MAX_ROTATION_DEG)
                    }
            }.pointerInput(cardId, isAnimatingExit) {
                detectDragGestures(
                    onDragStart = { if (!isAnimatingExit) onDragStart() },
                    onDrag = { change, dragAmount ->
                        if (!isAnimatingExit) {
                            change.consume()
                            onDragDelta(dragAmount)
                        }
                    },
                    onDragCancel = { onDragEnd(true) },
                    onDragEnd = { onDragEnd(false) },
                )
            }
}
