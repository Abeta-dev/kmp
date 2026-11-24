package dev.abeta.kmp.atoms.atoms

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.modifiers.LocalUiHapticFeedback
import dev.abeta.kmp.atoms.modifiers.UiHapticKind
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.atoms.tokens.resolveCornerRadii
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ImageAtom
import dev.abeta.kmp.contracts.MediaPagerAtom
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

private const val MIN_PAGER_HEIGHT_DP = 64
private const val MAX_PAGER_HEIGHT_DP = 640
private const val MIN_AUTO_ADVANCE_MS = 1500
private const val INDICATOR_DOT_SIZE_DP = 6
private const val INDICATOR_ACTIVE_WIDTH_DP = 18
private const val INDICATOR_SPACING_DP = 6
private const val INDICATOR_INACTIVE_ALPHA = 0.50f
private const val INDICATOR_PILL_BG_ALPHA = 0.35f
private const val INDICATOR_HALF_DIVISOR = 2f

/**
 * Pure helper returning the number of indicator dots to render for a media pager.
 * Returns `0` when fewer than two items are present.
 */
fun resolvePagerIndicatorCount(itemCount: Int): Int = if (itemCount > 1) itemCount else 0

/**
 * Stateless swipeable horizontal media pager UI atom rendered from a [MediaPagerAtom] contract.
 *
 * Smoothly renders each [ImageAtom] via [UiImage], clips to [MediaPagerAtom.cornerRadius]
 * (defaulting to [dev.abeta.kmp.tokens.CornerRadiusTokens.md]), dispatches per-item or fallback
 * click actions with tactile feedback, renders draw-phase animated pill dot indicators when
 * multiple items exist, and honors [dev.abeta.kmp.tokens.MotionTokens.reducedMotion] for
 * auto-advance.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun UiMediaPager(
    atom: MediaPagerAtom,
    modifier: Modifier = Modifier,
    onAction: (ActionRef) -> Unit = {},
) {
    if (atom.items.isEmpty()) return

    val cornerTokens = ThemeTokens.cornerRadius
    val spacing = ThemeTokens.spacing
    val pagerKey = atom.id ?: "default"
    val resolvedHeight = atom.heightDp.coerceIn(MIN_PAGER_HEIGHT_DP, MAX_PAGER_HEIGHT_DP).dp
    val pagerShape =
        resolveCornerRadii(atom.cornerRadius, cornerTokens)?.toRoundedCornerShape()
            ?: RoundedCornerShape(cornerTokens.md)
    val pagerState = rememberPagerState(pageCount = { atom.items.size })
    val indicatorCount = if (atom.showIndicators) resolvePagerIndicatorCount(atom.items.size) else 0

    UiMediaPagerAutoAdvanceEffect(
        pagerState = pagerState,
        pageCount = atom.items.size,
        autoAdvanceMs = atom.autoAdvanceMs,
    )

    Box(
        modifier =
            modifier
                .applyLayoutConfig(atom.layout)
                .fillMaxWidth()
                .height(resolvedHeight)
                .clip(pagerShape)
                .testTag("media_pager_$pagerKey"),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val item = atom.items[page]
            UiMediaPagerSlide(
                pagerKey = pagerKey,
                page = page,
                item = item,
                fallbackAction = atom.onPageClick,
                onAction = onAction,
            )
        }

        if (indicatorCount > 0) {
            UiMediaPagerIndicators(
                pagerKey = pagerKey,
                pagerState = pagerState,
                count = indicatorCount,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = spacing.space8),
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiMediaPagerAutoAdvanceEffect(
    pagerState: PagerState,
    pageCount: Int,
    autoAdvanceMs: Int?,
) {
    val reducedMotion = ThemeTokens.motion.reducedMotion
    val intervalMs = autoAdvanceMs?.takeIf { it >= MIN_AUTO_ADVANCE_MS } ?: return
    if (reducedMotion || pageCount <= 1) {
        return
    }
    LaunchedEffect(pagerState, intervalMs, pageCount) {
        while (isActive) {
            delay(intervalMs.toLong())
            if (!pagerState.isScrollInProgress) {
                val nextPage = (pagerState.currentPage + 1) % pageCount
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiMediaPagerSlide(
    pagerKey: String,
    page: Int,
    item: ImageAtom,
    fallbackAction: ActionRef?,
    onAction: (ActionRef) -> Unit,
) {
    val haptic = LocalUiHapticFeedback.current
    val pageAction = item.onClick ?: fallbackAction
    val slideImage = item.copy(onClick = null, widthDp = null, heightDp = null)
    val slideModifier =
        Modifier
            .fillMaxSize()
            .testTag("media_pager_${pagerKey}_page_$page")
            .let { base ->
                if (pageAction != null) {
                    base.clickable(role = Role.Button) {
                        haptic(UiHapticKind.CLICK)
                        onAction(pageAction)
                    }
                } else {
                    base
                }
            }

    Box(
        modifier = slideModifier,
        contentAlignment = Alignment.Center,
    ) {
        UiImage(
            atom = slideImage,
            modifier = Modifier.fillMaxSize(),
            onAction = onAction,
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun UiMediaPagerIndicators(
    pagerKey: String,
    pagerState: PagerState,
    count: Int,
    modifier: Modifier = Modifier,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val cornerRadius = ThemeTokens.cornerRadius
    val pillShape = RoundedCornerShape(cornerRadius.full)
    val activeColor = colors.text.inverse
    val inactiveColor = colors.text.inverse.copy(alpha = INDICATOR_INACTIVE_ALPHA)
    val scrimColor = colors.surface.inverse.copy(alpha = INDICATOR_PILL_BG_ALPHA)
    val totalWidthDp =
        (
            INDICATOR_ACTIVE_WIDTH_DP +
                (count - 1) * INDICATOR_DOT_SIZE_DP +
                (count - 1) * INDICATOR_SPACING_DP
        ).dp

    Box(
        modifier =
            modifier
                .testTag("media_pager_indicators_$pagerKey")
                .clip(pillShape)
                .background(scrimColor, pillShape)
                .semantics {
                    contentDescription = "Page ${pagerState.currentPage + 1} of $count"
                }.padding(horizontal = spacing.space8, vertical = spacing.space4),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier.size(width = totalWidthDp, height = INDICATOR_DOT_SIZE_DP.dp),
        ) {
            val dotDiameterPx = INDICATOR_DOT_SIZE_DP.dp.toPx()
            val activeWidthPx = INDICATOR_ACTIVE_WIDTH_DP.dp.toPx()
            val gapPx = INDICATOR_SPACING_DP.dp.toPx()
            val maxIndex = (count - 1).toFloat()
            val progress =
                (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                    .coerceIn(0f, maxIndex)
            val corner =
                CornerRadius(
                    x = dotDiameterPx / INDICATOR_HALF_DIVISOR,
                    y = dotDiameterPx / INDICATOR_HALF_DIVISOR,
                )
            var cursorX = 0f
            for (index in 0 until count) {
                val distance = abs(progress - index).coerceIn(0f, 1f)
                val activeFactor = 1f - distance
                val width = dotDiameterPx + (activeWidthPx - dotDiameterPx) * activeFactor
                drawRoundRect(
                    color = lerp(inactiveColor, activeColor, activeFactor),
                    topLeft = Offset(x = cursorX, y = 0f),
                    size = Size(width = width, height = dotDiameterPx),
                    cornerRadius = corner,
                )
                cursorX += width + gapPx
            }
        }
    }
}
