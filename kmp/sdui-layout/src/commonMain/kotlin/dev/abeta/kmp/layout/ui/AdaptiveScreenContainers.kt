package dev.abeta.kmp.layout.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ScreenLayoutMode
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.layout.engine.LayoutUiState
import dev.abeta.kmp.layout.snippet.LocalFormValidationErrors
import dev.abeta.kmp.layout.snippet.LocalMotionStaggerIndex
import dev.abeta.kmp.layout.snippet.SnippetRegistry
import dev.abeta.kmp.tokens.AdaptiveLayoutMetrics
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.coroutines.delay

private const val MIN_TOAST_DURATION_MS = 1000L
private const val SPLIT_PANE_SIDEBAR_WIDTH_DP = 380
private const val SIDEBAR_FLOATING_CART_BOTTOM_SPACER_DP = 96

internal data class AdaptiveViewportPlan(
    val maxWidth: Dp,
    val primarySnippets: List<SnippetEnvelope>,
    val secondarySnippets: List<SnippetEnvelope>,
) {
    val isSplitPaneActive: Boolean
        get() = secondarySnippets.isNotEmpty()
}

internal fun resolveAdaptiveViewportPlan(
    state: LayoutUiState.Ready,
    metrics: AdaptiveLayoutMetrics,
): AdaptiveViewportPlan {
    val adaptiveSpec = state.page.adaptiveLayout
    val resolvedMaxWidth = adaptiveSpec?.maxWidthDp?.dp ?: metrics.maxContentWidth
    val splitPaneRequested =
        adaptiveSpec?.splitPaneEnabled == true ||
            adaptiveSpec?.layoutMode == ScreenLayoutMode.SPLIT_PANE
    val secondaryIds = adaptiveSpec?.secondarySnippetIds?.toSet().orEmpty()
    val canSplit = metrics.useTwoPane && splitPaneRequested && secondaryIds.isNotEmpty()
    val secondarySnippets =
        if (canSplit) state.page.snippets.filter { it.snippetId in secondaryIds } else emptyList()
    val primarySnippets =
        if (secondarySnippets.isNotEmpty()) {
            state.page.snippets.filterNot { it.snippetId in secondaryIds }
        } else {
            state.page.snippets
        }
    return AdaptiveViewportPlan(
        maxWidth = resolvedMaxWidth,
        primarySnippets = primarySnippets,
        secondarySnippets = secondarySnippets,
    )
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
internal fun AdaptiveReadyViewport(
    state: LayoutUiState.Ready,
    plan: AdaptiveViewportPlan,
    snippetRegistry: SnippetRegistry,
    onAction: (ActionRef) -> Unit,
    onFormValueChange: (String, String) -> Unit,
    onDismissToast: () -> Unit,
    onSnippetImpression: (SnippetEnvelope) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = ThemeTokens.spacing
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(modifier = Modifier.widthIn(max = plan.maxWidth).fillMaxSize()) {
            if (plan.isSplitPaneActive) {
                SplitPaneBody(
                    state = state,
                    primarySnippets = plan.primarySnippets,
                    secondarySnippets = plan.secondarySnippets,
                    snippetRegistry = snippetRegistry,
                    onAction = onAction,
                    onFormValueChange = onFormValueChange,
                    onSnippetImpression = onSnippetImpression,
                    onLoadMore = onLoadMore,
                )
            } else {
                PrimaryFeedContainer(
                    state = state,
                    snippets = plan.primarySnippets,
                    snippetRegistry = snippetRegistry,
                    onAction = onAction,
                    onFormValueChange = onFormValueChange,
                    onSnippetImpression = onSnippetImpression,
                    onLoadMore = onLoadMore,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            FloatingBottomOverlayColumn(
                state = state,
                onAction = onAction,
                onDismissToast = onDismissToast,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(spacing.space16),
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
internal fun OfflineStatusBanner(pendingActions: Int) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    val message =
        if (pendingActions > 0) {
            "Offline · $pendingActions pending change(s) will sync automatically"
        } else {
            "Offline mode · Showing cached screen"
        }
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite }
                .testTag("sdui_offline_banner"),
        color = colors.status.warningSubtle,
    ) {
        Text(
            text = message,
            style = typography.labelMedium,
            color = colors.status.warning,
            modifier = Modifier.padding(horizontal = spacing.space16, vertical = spacing.space8),
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun FloatingBottomOverlayColumn(
    state: LayoutUiState.Ready,
    onAction: (ActionRef) -> Unit,
    onDismissToast: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = ThemeTokens.spacing
    val overlayModifier =
        if (state.page.bottomBar == null) {
            modifier.navigationBarsPadding()
        } else {
            modifier
        }
    Column(
        modifier = overlayModifier,
        verticalArrangement = Arrangement.spacedBy(spacing.space8),
    ) {
        state.activeToast?.let { toast ->
            LaunchedEffect(state.toastSequence, toast) {
                delay(toast.durationMs.coerceAtLeast(MIN_TOAST_DURATION_MS))
                onDismissToast()
            }
            ToastBanner(
                toast = toast,
                onDismiss = onDismissToast,
            )
        }
        state.page.floatingCart?.let { floatingCart ->
            FloatingCartDockHost(
                floatingCart = floatingCart,
                state = state,
                onAction = onAction,
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun SplitPaneBody(
    state: LayoutUiState.Ready,
    primarySnippets: List<SnippetEnvelope>,
    secondarySnippets: List<SnippetEnvelope>,
    snippetRegistry: SnippetRegistry,
    onAction: (ActionRef) -> Unit,
    onFormValueChange: (String, String) -> Unit,
    onSnippetImpression: (SnippetEnvelope) -> Unit,
    onLoadMore: () -> Unit,
) {
    val spacing = ThemeTokens.spacing
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(spacing.space16),
    ) {
        PrimaryFeedContainer(
            state = state,
            snippets = primarySnippets,
            snippetRegistry = snippetRegistry,
            onAction = onAction,
            onFormValueChange = onFormValueChange,
            onSnippetImpression = onSnippetImpression,
            onLoadMore = onLoadMore,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        CompositionLocalProvider(LocalFormValidationErrors provides state.validationErrors) {
            Column(
                modifier =
                    Modifier
                        .width(SPLIT_PANE_SIDEBAR_WIDTH_DP.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = spacing.space8, horizontal = spacing.space8),
                verticalArrangement = Arrangement.spacedBy(spacing.space8),
            ) {
                secondarySnippets.forEachIndexed { idx, envelope ->
                    LaunchedEffect(envelope.snippetId) {
                        onSnippetImpression(envelope)
                    }
                    CompositionLocalProvider(LocalMotionStaggerIndex provides idx) {
                        snippetRegistry.Render(
                            envelope = envelope,
                            formValues = state.formValues,
                            onFormValueChange = onFormValueChange,
                            onAction = onAction,
                            onUnknownSnippet = { unknown -> UnknownSnippetPlaceholder(unknown) },
                            onDecodeError = { failed, err -> DecodeErrorPlaceholder(failed, err) },
                        )
                    }
                }
                if (state.page.floatingCart != null) {
                    Box(modifier = Modifier.padding(bottom = SIDEBAR_FLOATING_CART_BOTTOM_SPACER_DP.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun PrimaryFeedContainer(
    state: LayoutUiState.Ready,
    snippets: List<SnippetEnvelope>,
    snippetRegistry: SnippetRegistry,
    onAction: (ActionRef) -> Unit,
    onFormValueChange: (String, String) -> Unit,
    onSnippetImpression: (SnippetEnvelope) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullAction = state.page.pullToRefresh
    if (pullAction != null) {
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { onAction(pullAction) },
            modifier = modifier,
        ) {
            SnippetLazyList(
                state = state,
                snippets = snippets,
                snippetRegistry = snippetRegistry,
                onAction = onAction,
                onFormValueChange = onFormValueChange,
                onSnippetImpression = onSnippetImpression,
                onLoadMore = onLoadMore,
            )
        }
    } else {
        Box(modifier = modifier) {
            SnippetLazyList(
                state = state,
                snippets = snippets,
                snippetRegistry = snippetRegistry,
                onAction = onAction,
                onFormValueChange = onFormValueChange,
                onSnippetImpression = onSnippetImpression,
                onLoadMore = onLoadMore,
            )
        }
    }
}
