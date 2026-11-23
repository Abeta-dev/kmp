package dev.abeta.kmp.storybook

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.actions.codec.BuiltInActionDecoders
import dev.abeta.kmp.actions.handler.BuiltInActionHandlers
import dev.abeta.kmp.actions.handler.DefaultActionDispatcher
import dev.abeta.kmp.analytics.LoggerAnalyticsTracker
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.core.logging.NoOpAppLogger
import dev.abeta.kmp.layout.engine.LayoutController
import dev.abeta.kmp.layout.snippet.BuiltInSnippetModule
import dev.abeta.kmp.layout.snippet.SnippetRegistry
import dev.abeta.kmp.layout.ui.SduiScreen
import dev.abeta.kmp.state.session.InMemorySessionStore
import dev.abeta.kmp.tokens.DesignTheme
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.coroutines.CoroutineScope

private const val PHONE_FRAME_WIDTH_DP = 412
private const val TABLET_FRAME_WIDTH_DP = 768
private const val WATCH_FRAME_WIDTH_DP = 280

private enum class DemoViewportMode(
    val label: String,
    val maxWidthDp: Int?,
) {
    PHONE("Phone", PHONE_FRAME_WIDTH_DP),
    TABLET("Tablet", TABLET_FRAME_WIDTH_DP),
    DESKTOP("Full", null),
}

private class SuperAppDemoHolder(
    val snippetRegistry: SnippetRegistry,
    val controller: LayoutController,
    private val backStack: ArrayDeque<String>,
    private val onScreenChanged: (String) -> Unit,
) {
    val canGoBack: Boolean
        get() = backStack.size > 1

    fun openScreen(
        route: String,
        clearStack: Boolean = false,
    ) {
        val clean = route.trim().removePrefix("/screens/").removePrefix("/")
        if (clearStack) backStack.clear()
        if (backStack.lastOrNull() != clean) {
            backStack.addLast(clean)
        }
        onScreenChanged(clean)
        controller.loadScreen(clean)
    }

    fun goBack(steps: Int = 1) {
        if (backStack.size <= 1) return
        repeat(steps.coerceAtLeast(1)) {
            if (backStack.size > 1) backStack.removeLast()
        }
        val destination = backStack.lastOrNull() ?: SuperAppDemoCatalog.DEFAULT_SCREEN_ID
        onScreenChanged(destination)
        controller.loadScreen(destination)
    }
}

/**
 * Full-viewport live interactive 6-in-1 Super-App Showcase running natively in WasmJS Browser,
 * JVM Desktop, macOS, and iOS with zero nested scrolling and instant offline BFF parity.
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun SuperAppDemosStory(
    initialScreenId: String = SuperAppDemoCatalog.DEFAULT_SCREEN_ID,
    darkTheme: Boolean = false,
    reducedMotion: Boolean = false,
    onAction: (ActionRef) -> Unit = {},
) {
    val spacing = ThemeTokens.spacing
    val scope = rememberCoroutineScope()
    val startScreen = remember(initialScreenId) { SuperAppDemoCatalog.resolveInitialScreen(initialScreenId) }
    var activeScreen by remember(startScreen) { mutableStateOf(startScreen) }
    var viewportMode by remember { mutableStateOf(DemoViewportMode.PHONE) }

    val holder =
        remember(scope) {
            createSuperAppDemoHolder(scope = scope) { updated -> activeScreen = updated }
        }
    LaunchedEffect(holder, startScreen) {
        holder.openScreen(startScreen, clearStack = true)
    }

    val uiState by holder.controller.uiState.collectAsState()
    val activeApp = SuperAppDemoCatalog.appForScreen(activeScreen)
    val effectiveMaxWidth: Dp? =
        if (activeApp.isWatch) {
            WATCH_FRAME_WIDTH_DP.dp
        } else {
            viewportMode.maxWidthDp?.dp
        }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(spacing.space12),
    ) {
        SuperAppDemoControlBar(
            activeApp = activeApp,
            activeScreen = activeScreen,
            viewportMode = viewportMode,
            canGoBack = holder.canGoBack,
            onSelectApp = { app -> holder.openScreen(app.entryScreen, clearStack = true) },
            onSelectViewport = { viewportMode = it },
            onGoBack = { holder.goBack() },
            onReset = { holder.openScreen(activeApp.entryScreen, clearStack = true) },
        )

        SuperAppDemoViewport(
            activeApp = activeApp,
            effectiveMaxWidth = effectiveMaxWidth,
            uiState = uiState,
            holder = holder,
            darkTheme = darkTheme,
            reducedMotion = reducedMotion,
            onAction = onAction,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun SuperAppDemoViewport(
    activeApp: DemoVerticalApp,
    effectiveMaxWidth: Dp?,
    uiState: dev.abeta.kmp.layout.engine.LayoutUiState,
    holder: SuperAppDemoHolder,
    darkTheme: Boolean,
    reducedMotion: Boolean,
    onAction: (ActionRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = ThemeTokens.spacing
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(ThemeTokens.cornerRadius.md))
                .background(ThemeTokens.colors.background.secondary)
                .padding(spacing.space12),
        contentAlignment = Alignment.TopCenter,
    ) {
        val frameModifier =
            if (effectiveMaxWidth != null) {
                Modifier.fillMaxHeight().widthIn(max = effectiveMaxWidth).fillMaxWidth()
            } else {
                Modifier.fillMaxSize()
            }
        val frameShape = RoundedCornerShape(ThemeTokens.cornerRadius.lg)
        Box(
            modifier =
                frameModifier
                    .clip(frameShape)
                    .border(ThemeTokens.strokeWidth.thin, ThemeTokens.colors.border.default, frameShape),
        ) {
            DesignTheme(
                preset = activeApp.preset,
                darkTheme = darkTheme,
                reducedMotion = reducedMotion,
            ) {
                SduiScreen(
                    state = uiState,
                    snippetRegistry = holder.snippetRegistry,
                    onAction = { action ->
                        onAction(action)
                        holder.controller.onAction(action)
                    },
                    onFormValueChange = holder.controller::onFormFieldChanged,
                    onRetry = { holder.controller.retry() },
                    onDismissOverlay = holder.controller::dismissOverlay,
                    onDismissToast = holder.controller::dismissToast,
                    onDismissFeedback = holder.controller::dismissFeedback,
                    onSnippetImpression = holder.controller::onSnippetImpression,
                    onLoadMore = { holder.controller.loadMore() },
                    isWatch = activeApp.isWatch,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun SuperAppDemoControlBar(
    activeApp: DemoVerticalApp,
    activeScreen: String,
    viewportMode: DemoViewportMode,
    canGoBack: Boolean,
    onSelectApp: (DemoVerticalApp) -> Unit,
    onSelectViewport: (DemoViewportMode) -> Unit,
    onGoBack: () -> Unit,
    onReset: () -> Unit,
) {
    val spacing = ThemeTokens.spacing
    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space8)) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SuperAppDemoCatalog.apps.forEach { app ->
                    FilterChip(
                        selected = app.id == activeApp.id,
                        onClick = { onSelectApp(app) },
                        label = { Text(app.name) },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(spacing.space8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UiTag(TagAtom(label = activeApp.subtitle, variant = TagVariant.BRAND))
                UiTag(TagAtom(label = "/screens/$activeScreen", variant = TagVariant.NEUTRAL))
                DemoViewportMode.entries.forEach { mode ->
                    FilterChip(
                        selected = viewportMode == mode && !activeApp.isWatch,
                        enabled = !activeApp.isWatch,
                        onClick = { onSelectViewport(mode) },
                        label = { Text(mode.label) },
                    )
                }
                if (canGoBack) {
                    OutlinedButton(onClick = onGoBack) { Text("Back") }
                }
                OutlinedButton(onClick = onReset) { Text("Reset") }
            }
        }
    }
}

private fun createSuperAppDemoHolder(
    scope: CoroutineScope,
    onScreenChanged: (String) -> Unit,
): SuperAppDemoHolder {
    val repository = StorybookDemoScreenRepository()
    val apiExecutor = StorybookDemoApiExecutor()
    val dispatcher =
        DefaultActionDispatcher(
            decoderRegistry = BuiltInActionDecoders.createDefaultRegistry(),
            actionRegistry = BuiltInActionHandlers.createDefaultRegistry(apiExecutor = apiExecutor),
        )
    val backStack = ArrayDeque<String>()
    var holderRef: SuperAppDemoHolder? = null
    val controller =
        LayoutController(
            screenRepository = repository,
            actionDispatcher = dispatcher,
            sessionStore = InMemorySessionStore(),
            analyticsTracker = LoggerAnalyticsTracker(NoOpAppLogger()),
            scope = scope,
            navigationListener = { nav ->
                holderRef?.openScreen(nav.route, clearStack = nav.clearBackStack)
            },
            backListener = { back ->
                holderRef?.goBack(back.steps)
            },
        )
    return SuperAppDemoHolder(
        snippetRegistry = BuiltInSnippetModule.createDefaultRegistry(),
        controller = controller,
        backStack = backStack,
        onScreenChanged = onScreenChanged,
    ).also { holderRef = it }
}
