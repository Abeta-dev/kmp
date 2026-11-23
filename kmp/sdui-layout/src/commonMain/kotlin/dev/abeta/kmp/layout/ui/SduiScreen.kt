package dev.abeta.kmp.layout.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.atoms.UiButton
import dev.abeta.kmp.atoms.atoms.UiCelebrationOverlay
import dev.abeta.kmp.atoms.atoms.UiDivider
import dev.abeta.kmp.atoms.atoms.UiIcon
import dev.abeta.kmp.atoms.atoms.UiProgress
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiText
import dev.abeta.kmp.atoms.modifiers.LocalEnteredMotionKeys
import dev.abeta.kmp.atoms.modifiers.LocalUiHapticFeedback
import dev.abeta.kmp.atoms.modifiers.UiHapticKind
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.atoms.modifiers.applyMotionSpec
import dev.abeta.kmp.atoms.modifiers.rememberComposeHapticFallback
import dev.abeta.kmp.atoms.modifiers.shakeOnError
import dev.abeta.kmp.atoms.tokens.resolve
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.BottomBarSpec
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.DividerAtom
import dev.abeta.kmp.contracts.FeedbackEffect
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.ProgressAtom
import dev.abeta.kmp.contracts.ProgressKind
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.contracts.ToolbarSpec
import dev.abeta.kmp.core.error.AppError
import dev.abeta.kmp.layout.engine.LayoutUiState
import dev.abeta.kmp.layout.snippet.LocalFormValidationErrors
import dev.abeta.kmp.layout.snippet.LocalMotionStaggerIndex
import dev.abeta.kmp.layout.snippet.SnippetRegistry
import dev.abeta.kmp.tokens.ProvideAdaptiveLayoutMetrics
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.coroutines.delay

private const val MIN_FEEDBACK_DURATION_MS = 800L
private const val TOOLBAR_ICON_SPACING_DP = 12
private const val CONTENT_BOTTOM_EXTRA_DP = 16
private const val FLOATING_CART_BOTTOM_SPACER_DP = 96
private const val FLOATING_CART_NUDGE_BOTTOM_SPACER_DP = 148
private const val ERROR_ICON_SIZE_DP = 32

/**
 * Stateless Compose Multiplatform SDUI screen renderer consuming [LayoutUiState] and
 * delegating every snippet to [snippetRegistry].
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
fun SduiScreen(
    state: LayoutUiState,
    snippetRegistry: SnippetRegistry,
    onAction: (ActionRef) -> Unit,
    onFormValueChange: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onDismissOverlay: () -> Unit = {},
    onDismissToast: () -> Unit = {},
    onDismissFeedback: () -> Unit = {},
    onSnippetImpression: (SnippetEnvelope) -> Unit = {},
    onLoadMore: () -> Unit = {},
    isWatch: Boolean = false,
    onHaptic: ((UiHapticKind) -> Unit)? = null,
) {
    val colors = ThemeTokens.colors
    val hapticCallback = onHaptic ?: rememberComposeHapticFallback()

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        ProvideAdaptiveLayoutMetrics(
            maxWidth = maxWidth,
            maxHeight = maxHeight,
            isWatch = isWatch,
        ) {
            CompositionLocalProvider(LocalUiHapticFeedback provides hapticCallback) {
                when (state) {
                    LayoutUiState.Idle -> {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(colors.background.primary),
                        )
                    }

                    LayoutUiState.Loading -> {
                        SkeletonScreenPlaceholder(modifier = Modifier.fillMaxSize())
                    }

                    is LayoutUiState.Error -> {
                        ErrorScreenContent(
                            error = state.error,
                            onRetry = onRetry,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    is LayoutUiState.Ready -> {
                        ReadyScreenContent(
                            state = state,
                            snippetRegistry = snippetRegistry,
                            onAction = onAction,
                            onFormValueChange = onFormValueChange,
                            onDismissOverlay = onDismissOverlay,
                            onDismissToast = onDismissToast,
                            onDismissFeedback = onDismissFeedback,
                            onSnippetImpression = onSnippetImpression,
                            onLoadMore = onLoadMore,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun ErrorScreenContent(
    error: AppError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.background.primary)
                .padding(spacing.space24),
        contentAlignment = Alignment.Center,
    ) {
        UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(spacing.space12),
            ) {
                UiIcon(
                    atom =
                        IconAtom(
                            name = "error",
                            sizeDp = ERROR_ICON_SIZE_DP,
                            color = ColorTokenRef(token = "status.error"),
                            contentDescription = "Error",
                        ),
                )
                UiText(
                    atom =
                        TextAtom(
                            text = "Unable to load screen",
                            color = ColorTokenRef(token = "text.primary"),
                        ),
                )
                Text(
                    text = error.message,
                    style = typography.bodyMedium,
                    color = colors.text.secondary,
                )
                TextButton(onClick = onRetry) {
                    Text(
                        text = "Retry",
                        style = typography.labelLarge,
                        color = colors.text.brand,
                    )
                }
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun ReadyScreenContent(
    state: LayoutUiState.Ready,
    snippetRegistry: SnippetRegistry,
    onAction: (ActionRef) -> Unit,
    onFormValueChange: (String, String) -> Unit,
    onDismissOverlay: () -> Unit,
    onDismissToast: () -> Unit,
    onDismissFeedback: () -> Unit,
    onSnippetImpression: (SnippetEnvelope) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ThemeTokens.colors

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.background.primary,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            state.page.toolbar?.let { toolbar ->
                SduiToolbar(toolbar = toolbar, onAction = onAction)
            }
        },
        bottomBar = {
            state.page.bottomBar?.let { bottomBar ->
                SduiBottomBar(bottomBar = bottomBar, onAction = onAction)
            }
        },
    ) { innerPadding ->
        ReadyScreenBody(
            state = state,
            snippetRegistry = snippetRegistry,
            onAction = onAction,
            onFormValueChange = onFormValueChange,
            onDismissToast = onDismissToast,
            onSnippetImpression = onSnippetImpression,
            onLoadMore = onLoadMore,
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
    }

    state.activeOverlay?.let { overlay ->
        OverlayHost(
            overlay = overlay,
            onAction = onAction,
            onDismiss = onDismissOverlay,
        )
    }

    val feedback = state.activeFeedback
    if (feedback != null && feedback.effect == FeedbackEffect.CELEBRATION) {
        LaunchedEffect(state.feedbackSequence, feedback) {
            delay(feedback.durationMs.coerceAtLeast(MIN_FEEDBACK_DURATION_MS))
            onDismissFeedback()
        }
        UiCelebrationOverlay(
            visible = true,
            title = feedback.title ?: "Order Confirmed!",
            subtitle = feedback.subtitle,
            onDismiss = onDismissFeedback,
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun ReadyScreenBody(
    state: LayoutUiState.Ready,
    snippetRegistry: SnippetRegistry,
    onAction: (ActionRef) -> Unit,
    onFormValueChange: (String, String) -> Unit,
    onDismissToast: () -> Unit,
    onSnippetImpression: (SnippetEnvelope) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val plan = resolveAdaptiveViewportPlan(state = state, metrics = ThemeTokens.adaptiveMetrics)
    Column(modifier = modifier.imePadding()) {
        if (state.isOffline) {
            OfflineStatusBanner(pendingActions = state.pendingOfflineActions)
        }
        AdaptiveReadyViewport(
            state = state,
            plan = plan,
            snippetRegistry = snippetRegistry,
            onAction = onAction,
            onFormValueChange = onFormValueChange,
            onDismissToast = onDismissToast,
            onSnippetImpression = onSnippetImpression,
            onLoadMore = onLoadMore,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
internal fun SnippetLazyList(
    state: LayoutUiState.Ready,
    snippets: List<SnippetEnvelope>,
    snippetRegistry: SnippetRegistry,
    onAction: (ActionRef) -> Unit,
    onFormValueChange: (String, String) -> Unit,
    onSnippetImpression: (SnippetEnvelope) -> Unit,
    onLoadMore: () -> Unit,
) {
    val spacing = ThemeTokens.spacing
    val reducedMotion = ThemeTokens.motion.reducedMotion
    val enteredMotionKeys = remember(state.page.pageId) { mutableSetOf<Any>() }
    val listState = remember(state.page.pageId) { LazyListState() }
    val cart = state.page.floatingCart
    val bottomExtraDp =
        when {
            cart == null -> CONTENT_BOTTOM_EXTRA_DP
            cart.freeDeliveryThresholdCents != null -> FLOATING_CART_NUDGE_BOTTOM_SPACER_DP
            else -> FLOATING_CART_BOTTOM_SPACER_DP
        }

    CompositionLocalProvider(
        LocalFormValidationErrors provides state.validationErrors,
        LocalEnteredMotionKeys provides enteredMotionKeys,
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = spacing.space8, horizontal = 0.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            itemsIndexed(
                items = snippets,
                key = { _, item -> item.snippetId },
                contentType = { _, item -> item.snippetType },
            ) { index, envelope ->
                val baseModifier = if (reducedMotion) Modifier else Modifier.animateItem()
                val shakeMatches =
                    state.errorShakeTargetId == null || state.errorShakeTargetId == envelope.snippetId
                SnippetListItemSlot(
                    index = index,
                    envelope = envelope,
                    formValues = state.formValues,
                    snippetRegistry = snippetRegistry,
                    onFormValueChange = onFormValueChange,
                    onAction = onAction,
                    onSnippetImpression = onSnippetImpression,
                    modifier = baseModifier.shakeOnError(state.errorShakeSequence, enabled = shakeMatches),
                )
            }
            if (state.page.pagination?.hasMore == true) {
                item(key = "__pagination_load_more__") {
                    PaginationLoadMoreItem(isLoadingMore = state.isLoadingMore, onLoadMore = onLoadMore)
                }
            }
            item(key = "__bottom_spacer__") {
                Box(modifier = Modifier.padding(bottom = bottomExtraDp.dp))
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun SnippetListItemSlot(
    index: Int,
    envelope: SnippetEnvelope,
    formValues: Map<String, String>,
    snippetRegistry: SnippetRegistry,
    onFormValueChange: (String, String) -> Unit,
    onAction: (ActionRef) -> Unit,
    onSnippetImpression: (SnippetEnvelope) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        LaunchedEffect(envelope.snippetId) {
            onSnippetImpression(envelope)
        }
        CompositionLocalProvider(LocalMotionStaggerIndex provides index) {
            snippetRegistry.Render(
                envelope = envelope,
                formValues = formValues,
                onFormValueChange = onFormValueChange,
                onAction = onAction,
                onUnknownSnippet = { unknown -> UnknownSnippetPlaceholder(unknown) },
                onDecodeError = { failed, err -> DecodeErrorPlaceholder(failed, err) },
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun PaginationLoadMoreItem(
    isLoadingMore: Boolean,
    onLoadMore: () -> Unit,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    val cornerRadius = ThemeTokens.cornerRadius
    val strokeWidth = ThemeTokens.strokeWidth

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.space16, vertical = spacing.space8)
                .testTag("sdui_pagination_load_more"),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoadingMore) {
            UiProgress(
                atom =
                    ProgressAtom(
                        kind = ProgressKind.CIRCULAR,
                        label = "Loading more...",
                    ),
            )
        } else {
            OutlinedButton(
                onClick = onLoadMore,
                shape = RoundedCornerShape(cornerRadius.md),
                border = BorderStroke(strokeWidth.thin, colors.button.outlineBorder),
            ) {
                Text(
                    text = "Load more",
                    style = typography.labelLarge,
                    color = colors.text.brand,
                )
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun SduiToolbar(
    toolbar: ToolbarSpec,
    onAction: (ActionRef) -> Unit,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val bgColor = toolbar.bgColor.resolve(fallback = colors.surface.primary)

    Surface(color = bgColor) {
        Column(modifier = Modifier.fillMaxWidth().statusBarsPadding()) {
            val rowModifier =
                if (toolbar.layout != null) {
                    Modifier.fillMaxWidth().applyLayoutConfig(toolbar.layout)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.space16, vertical = spacing.space12)
                }
            Row(
                modifier = rowModifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(TOOLBAR_ICON_SPACING_DP.dp),
            ) {
                toolbar.navigationIcon?.let { navIcon ->
                    UiIcon(atom = navIcon, onAction = onAction)
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(spacing.space2),
                ) {
                    toolbar.title?.let { title ->
                        UiText(atom = title, onAction = onAction)
                    }
                    toolbar.subtitle?.let { subtitle ->
                        UiText(atom = subtitle, onAction = onAction)
                    }
                }
                toolbar.trailingActions.forEach { actionIcon ->
                    UiIcon(atom = actionIcon, onAction = onAction)
                }
            }
            UiDivider(atom = DividerAtom())
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun SduiBottomBar(
    bottomBar: BottomBarSpec,
    onAction: (ActionRef) -> Unit,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing

    Surface(
        modifier = Modifier.applyMotionSpec(motion = bottomBar.motion, motionKey = "sdui_bottom_bar"),
        color = colors.surface.primary,
    ) {
        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            UiDivider(atom = DividerAtom())
            val contentModifier =
                if (bottomBar.layout != null) {
                    Modifier.fillMaxWidth().applyLayoutConfig(bottomBar.layout)
                } else {
                    Modifier.fillMaxWidth().padding(spacing.space12)
                }
            Column(
                modifier = contentModifier,
                verticalArrangement = Arrangement.spacedBy(spacing.space8),
            ) {
                bottomBar.helperText?.let { helper ->
                    UiText(atom = helper, onAction = onAction)
                }
                if (bottomBar.secondaryButton != null || bottomBar.primaryButton != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                    ) {
                        bottomBar.secondaryButton?.let { secondary ->
                            UiButton(
                                atom = secondary,
                                modifier = Modifier.weight(1f),
                                onAction = onAction,
                            )
                        }
                        bottomBar.primaryButton?.let { primary ->
                            UiButton(
                                atom = primary,
                                modifier = Modifier.weight(1f),
                                onAction = onAction,
                            )
                        }
                    }
                }
            }
        }
    }
}
