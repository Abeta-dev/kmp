package dev.abeta.kmp.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.abeta.kmp.actions.codec.BuiltInActionDecoders
import dev.abeta.kmp.actions.handler.ActionDispatcher
import dev.abeta.kmp.actions.handler.ApiCallExecutor
import dev.abeta.kmp.actions.handler.BuiltInActionHandlers
import dev.abeta.kmp.actions.handler.DefaultActionDispatcher
import dev.abeta.kmp.actions.model.ActionContext
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.analytics.AnalyticsTracker
import dev.abeta.kmp.analytics.CompositeAnalyticsTracker
import dev.abeta.kmp.analytics.LoggerAnalyticsTracker
import dev.abeta.kmp.analytics.RecordingAnalyticsTracker
import dev.abeta.kmp.atoms.atoms.LocalImageRenderer
import dev.abeta.kmp.atoms.atoms.UiDivider
import dev.abeta.kmp.atoms.atoms.UiImageRenderer
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.DividerAtom
import dev.abeta.kmp.contracts.FeedbackEffect
import dev.abeta.kmp.contracts.FormSnippetData
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.core.logging.AppLogger
import dev.abeta.kmp.core.logging.KermitAppLogger
import dev.abeta.kmp.core.logging.LogLevel
import dev.abeta.kmp.core.storage.InMemoryKeyValueStore
import dev.abeta.kmp.core.storage.KeyValueStore
import dev.abeta.kmp.layout.engine.LayoutController
import dev.abeta.kmp.layout.engine.LayoutUiState
import dev.abeta.kmp.layout.engine.OfflineActionCoordinator
import dev.abeta.kmp.layout.media.KtorImageBytesFetcher
import dev.abeta.kmp.layout.media.KtorImageRenderer
import dev.abeta.kmp.layout.platform.PlatformBridgeBundle
import dev.abeta.kmp.layout.platform.asUiHapticCallback
import dev.abeta.kmp.layout.repository.CachedScreenRepository
import dev.abeta.kmp.layout.repository.RemoteApiCallExecutor
import dev.abeta.kmp.layout.repository.RemoteScreenRepository
import dev.abeta.kmp.layout.repository.ScreenCachePolicy
import dev.abeta.kmp.layout.repository.ScreenRepository
import dev.abeta.kmp.layout.snippet.BuiltInSnippetModule
import dev.abeta.kmp.layout.snippet.SnippetRegistry
import dev.abeta.kmp.layout.ui.SduiScreen
import dev.abeta.kmp.network.client.HttpClientFactory
import dev.abeta.kmp.network.client.KtorHttpTransport
import dev.abeta.kmp.network.config.NetworkConfig
import dev.abeta.kmp.network.config.RetryPolicy
import dev.abeta.kmp.state.session.InMemorySessionStore
import dev.abeta.kmp.state.session.SessionStore
import dev.abeta.kmp.state.sync.OfflineMutationQueue
import dev.abeta.kmp.tokens.DesignTheme
import dev.abeta.kmp.tokens.ThemeTokens
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

private const val DEFAULT_BFF_BASE_URL = "http://localhost:8080"

/**
 * Structured summary emitted by [SampleContainer.runHeadlessVerification].
 */
data class HeadlessVerificationSummary(
    val loadedPageId: String,
    val snippetCount: Int,
    val formSubmittedPageId: String,
    val containersPageId: String,
    val containersSnippetTypes: Set<String>,
    val storePageId: String,
    val watchPageId: String,
    val celebrationTriggered: Boolean,
    val toastMessage: String?,
    val recordedEvents: List<String>,
    val recordedImpressions: List<String>,
    val sessionKeys: Set<String>,
)

/**
 * Dependency-injected container wiring `sdui-layout`, `action-engine`, `network`, `state`, `analytics`,
 * `design-tokens`, `sdui-contracts`, and `ui-atoms` without global singletons.
 */
@Suppress("LongParameterList")
class SampleContainer(
    val screenRepository: ScreenRepository,
    val sessionStore: SessionStore,
    val recordingTracker: RecordingAnalyticsTracker,
    val snippetRegistry: SnippetRegistry,
    val actionDispatcher: ActionDispatcher,
    val offlineCoordinator: OfflineActionCoordinator,
    val imageRenderer: UiImageRenderer,
    val controller: LayoutController,
    val platformBridges: PlatformBridgeBundle = PlatformBridgeBundle.createTestDefault(),
    private val httpClient: HttpClient? = null,
    private val backStack: ArrayDeque<String> = ArrayDeque(listOf(DEFAULT_SCREEN_ID)),
    private val currentScreenFlow: MutableStateFlow<String> = MutableStateFlow(DEFAULT_SCREEN_ID),
) {
    /** Currently selected sample screen ID. */
    val currentScreenId: StateFlow<String> = currentScreenFlow.asStateFlow()

    /** Ordered list of sample screen IDs available in the UI switcher. */
    val availableScreens: List<String> = DEFAULT_SCREENS

    /** Ordered list of six vertical showcase apps available in the Super-App launcher. */
    val verticalApps: List<ShowcaseAppSpec> = VERTICAL_APPS

    /** Loads the initial `"home"` screen. */
    fun loadInitialScreen(): Job = navigateTo(DEFAULT_SCREEN_ID, pushHistory = false)

    /** Navigates to [screenId], updating the local back stack and triggering [LayoutController.loadScreen]. */
    fun navigateTo(
        screenId: String,
        clearBackStack: Boolean = false,
        pushHistory: Boolean = true,
    ): Job {
        val cleanId = screenId.trim().removePrefix("/screens/").removePrefix("/")
        if (clearBackStack) backStack.clear()
        if (pushHistory && backStack.lastOrNull() != cleanId) {
            backStack.addLast(cleanId)
        } else if (backStack.isEmpty()) {
            backStack.addLast(cleanId)
        }
        currentScreenFlow.value = cleanId
        return controller.loadScreen(cleanId)
    }

    /** Pops [steps] entries from the sample back stack and loads the destination screen. */
    fun navigateBack(steps: Int = 1): Job? {
        if (backStack.size <= 1) return null
        repeat(steps.coerceAtLeast(1)) {
            if (backStack.size > 1) backStack.removeLast()
        }
        val destination = backStack.lastOrNull() ?: DEFAULT_SCREEN_ID
        currentScreenFlow.value = destination
        return controller.loadScreen(destination)
    }

    /** Flushes any queued offline mutations via [LayoutController.flushOfflineActions]. */
    fun flushOfflineActions(): Job? = controller.flushOfflineActions()

    /** Releases underlying Ktor [HttpClient] resources owned by this container. */
    fun close() {
        httpClient?.close()
    }

    /**
     * Executes an end-to-end headless verification sequence across `"home"`, `"form-demo"`,
     * and `"containers"` and returns a [HeadlessVerificationSummary].
     */
    suspend fun runHeadlessVerification(): HeadlessVerificationSummary {
        navigateTo("home", clearBackStack = true).join()
        val homeReady =
            controller.uiState.value as? LayoutUiState.Ready
                ?: error("Expected Ready state for 'home', got ${controller.uiState.value}")
        homeReady.page.snippets.forEach(controller::onSnippetImpression)
        val toastAction =
            ActionRef(
                id = "headless_toast",
                type = ActionTypes.SHOW_TOAST,
                payload = buildJsonObject { put("message", "Headless action check passed") },
            )
        controller.onAction(toastAction).join()

        navigateTo("form-demo").join()
        val formReady =
            controller.uiState.value as? LayoutUiState.Ready
                ?: error("Expected Ready state for 'form-demo', got ${controller.uiState.value}")
        controller.onFormFieldChanged("full_name", "Alex Rivera")
        controller.onFormFieldChanged("work_email", "alex@abeta.dev")
        val formEnvelope = formReady.page.snippets.first { it.snippetType == SnippetTypes.FORM }
        val formModel = SduiJson.decodeFromJsonElement(FormSnippetData.serializer(), formEnvelope.data)
        formModel.submitButton?.onClick?.let { controller.onAction(it).join() }
        val formToastMessage = (controller.uiState.value as? LayoutUiState.Ready)?.activeToast?.message

        navigateTo("containers").join()
        val containersReady =
            controller.uiState.value as? LayoutUiState.Ready
                ?: error("Expected Ready state for 'containers', got ${controller.uiState.value}")
        val containerTypes =
            containersReady.page.snippets
                .map { it.snippetType }
                .toSet()
        val controlsEnvelope = containersReady.page.snippets.first { it.snippetType == SnippetTypes.FORM }
        val controlsModel = SduiJson.decodeFromJsonElement(FormSnippetData.serializer(), controlsEnvelope.data)
        check(controlsModel.switches.isNotEmpty()) { "Expected switches in 'containers' form" }
        check(controlsModel.checkboxes.isNotEmpty()) { "Expected checkboxes in 'containers' form" }
        check(controlsModel.sliders.isNotEmpty()) { "Expected sliders in 'containers' form" }

        val (storePageId, celebrationTriggered) = verifyStoreHomeHeadless()
        val watchPageId = verifyWatchStatusHeadless()

        return HeadlessVerificationSummary(
            loadedPageId = homeReady.page.pageId,
            snippetCount = homeReady.page.snippets.size,
            formSubmittedPageId = formReady.page.pageId,
            containersPageId = containersReady.page.pageId,
            containersSnippetTypes = containerTypes,
            storePageId = storePageId,
            watchPageId = watchPageId,
            celebrationTriggered = celebrationTriggered,
            toastMessage = formToastMessage,
            recordedEvents = recordingTracker.events.map { it.name },
            recordedImpressions = recordingTracker.impressions.map { it.eventName },
            sessionKeys = sessionStore.state.value.keys,
        )
    }

    private suspend fun verifyWatchStatusHeadless(): String {
        navigateTo("watch-status").join()
        val watchReady =
            controller.uiState.value as? LayoutUiState.Ready
                ?: error("Expected Ready state for 'watch-status', got ${controller.uiState.value}")
        check(watchReady.page.snippets.isNotEmpty()) { "Expected snippets in 'watch-status'" }
        return watchReady.page.pageId
    }

    private suspend fun verifyStoreHomeHeadless(): Pair<String, Boolean> {
        navigateTo("store-home").join()
        val storeReady =
            controller.uiState.value as? LayoutUiState.Ready
                ?: error("Expected Ready state for 'store-home', got ${controller.uiState.value}")
        check(storeReady.page.floatingCart != null) { "Expected floatingCart in 'store-home'" }
        check(storeReady.formValues["cart_qty_avocados"] == "1") { "Expected seeded cart_qty_avocados in 'store-home'" }
        val orderAction =
            ActionRef(
                id = "headless_express_order",
                type = ActionTypes.CALL_API,
                payload =
                    buildJsonObject {
                        put("endpoint", "/api/v1/orders/express")
                        put("method", "POST")
                    },
            )
        controller.onAction(orderAction).join()
        val afterOrderReady =
            controller.uiState.value as? LayoutUiState.Ready
                ?: error("Expected Ready state after express order")
        val celebrationTriggered = afterOrderReady.activeFeedback?.effect == FeedbackEffect.CELEBRATION
        return storeReady.page.pageId to celebrationTriggered
    }

    /**
     * Executes an end-to-end headless verification sequence across all six vertical showcase entry
     * screens (`fresh-home`, `bites-home`, `pulse-home`, `mart-home`, `spark-deck`, `trade-home`)
     * and live vertical `CALL_API` endpoints.
     */
    suspend fun runVerticalsHeadlessVerification(): VerticalsVerificationSummary = verifyVerticalsHeadless(this)

    companion object {
        const val DEFAULT_SCREEN_ID: String = "home"
        val DEFAULT_SCREENS: List<String> =
            listOf("home", "store-home", "catalog", "profile", "form-demo", "containers", "watch-status")
        val VERTICAL_APPS: List<ShowcaseAppSpec> = SampleShowcaseApps.allApps
        val VERTICAL_SCREENS: List<String> =
            listOf("fresh-home", "bites-home", "pulse-home", "mart-home", "spark-deck", "trade-home")
    }
}

/**
 * Factory assembling a complete [SampleContainer] via constructor injection.
 */
object SampleFactory {
    /**
     * Builds a [SampleContainer]. When [bffBaseUrl] or [httpEngine] is non-null, remote
     * Ktor/BFF repositories are used; otherwise offline sample screens are served via [CachedScreenRepository].
     */
    fun create(
        scope: CoroutineScope,
        bffBaseUrl: String? = null,
        httpEngine: HttpClientEngine? = null,
        logger: AppLogger = KermitAppLogger(minLevel = LogLevel.INFO),
        keyValueStore: KeyValueStore = InMemoryKeyValueStore(),
        platformBridges: PlatformBridgeBundle = PlatformBridgeBundle.createTestDefault(),
    ): SampleContainer {
        val sessionStore = InMemorySessionStore()
        val recordingTracker = RecordingAnalyticsTracker()
        val analytics = CompositeAnalyticsTracker(listOf(LoggerAnalyticsTracker(logger), recordingTracker))
        val retryPolicy = if (bffBaseUrl == null && httpEngine == null) RetryPolicy(maxRetries = 0) else RetryPolicy()
        val httpClient =
            HttpClientFactory.create(
                config = NetworkConfig(baseUrl = bffBaseUrl ?: DEFAULT_BFF_BASE_URL, retry = retryPolicy),
                json = SduiJson,
                engine = httpEngine,
            )
        val (baseRepo, apiExecutor) = createBackendPorts(bffBaseUrl, httpEngine, httpClient)
        val cachedRepository =
            CachedScreenRepository(
                remote = baseRepo,
                store = keyValueStore,
                policy = ScreenCachePolicy.STALE_WHILE_REVALIDATE,
                backgroundScope = scope,
            )
        val offlineCoordinator =
            OfflineActionCoordinator(
                mutationQueue = OfflineMutationQueue(backingStore = keyValueStore),
                apiCallExecutor = apiExecutor,
                logger = logger,
                contextProvider = { ActionContext(sessionState = sessionStore.state.value) },
            )
        return buildContainer(
            scope = scope,
            cachedRepository = cachedRepository,
            apiExecutor = apiExecutor,
            sessionStore = sessionStore,
            recordingTracker = recordingTracker,
            analytics = analytics,
            offlineCoordinator = offlineCoordinator,
            httpClient = httpClient,
            logger = logger,
            platformBridges = platformBridges,
        )
    }

    @Suppress("LongParameterList")
    private fun buildContainer(
        scope: CoroutineScope,
        cachedRepository: CachedScreenRepository,
        apiExecutor: ApiCallExecutor,
        sessionStore: SessionStore,
        recordingTracker: RecordingAnalyticsTracker,
        analytics: AnalyticsTracker,
        offlineCoordinator: OfflineActionCoordinator,
        httpClient: HttpClient,
        logger: AppLogger,
        platformBridges: PlatformBridgeBundle,
    ): SampleContainer {
        val dispatcher =
            DefaultActionDispatcher(
                decoderRegistry = BuiltInActionDecoders.createDefaultRegistry(),
                actionRegistry = BuiltInActionHandlers.createDefaultRegistry(apiExecutor),
            )
        var containerRef: SampleContainer? = null
        val controller =
            LayoutController(
                screenRepository = cachedRepository,
                actionDispatcher = dispatcher,
                sessionStore = sessionStore,
                analyticsTracker = analytics,
                scope = scope,
                navigationListener = { nav -> containerRef?.navigateTo(nav.route, nav.clearBackStack) },
                backListener = { back -> containerRef?.navigateBack(steps = back.steps) },
                clipboardListener = { copy ->
                    runCatching {
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(copy.text), null)
                    }
                },
                logger = logger,
                offlineCoordinator = offlineCoordinator,
                platformBridges = platformBridges,
            )
        val container =
            SampleContainer(
                screenRepository = cachedRepository,
                sessionStore = sessionStore,
                recordingTracker = recordingTracker,
                snippetRegistry = BuiltInSnippetModule.createDefaultRegistry(),
                actionDispatcher = dispatcher,
                offlineCoordinator = offlineCoordinator,
                imageRenderer = KtorImageRenderer(fetcher = KtorImageBytesFetcher(httpClient = httpClient)),
                controller = controller,
                platformBridges = platformBridges,
                httpClient = httpClient,
            )
        containerRef = container
        return container
    }

    private fun createBackendPorts(
        bffBaseUrl: String?,
        httpEngine: HttpClientEngine?,
        httpClient: HttpClient,
    ): Pair<ScreenRepository, ApiCallExecutor> =
        if (bffBaseUrl == null && httpEngine == null) {
            OfflineScreenRepository() to OfflineApiCallExecutor()
        } else {
            val transport = KtorHttpTransport(httpClient = httpClient, json = SduiJson)
            RemoteScreenRepository(transport) to RemoteApiCallExecutor(transport)
        }
}

/**
 * Root Compose Desktop UI hosting the screen switcher, offline queue controls, theme toggle, and [SduiScreen].
 */
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
fun SampleRoot(
    container: SampleContainer,
    initialDarkTheme: Boolean = false,
    showTopBar: Boolean = true,
) {
    var darkTheme by remember(initialDarkTheme) { mutableStateOf(initialDarkTheme) }
    val uiState by container.controller.uiState.collectAsState()
    val activeScreen by container.currentScreenId.collectAsState()
    val offlineStatus by container.offlineCoordinator.status.collectAsState()
    val pendingOfflineCount = (uiState as? LayoutUiState.Ready)?.pendingOfflineActions ?: offlineStatus.pendingCount
    var launcherMode by remember { mutableStateOf(launcherModeForScreen(activeScreen)) }
    val activePreset = SampleShowcaseApps.themePresetForScreen(activeScreen)

    LaunchedEffect(activeScreen) {
        launcherMode = launcherModeForScreen(activeScreen)
    }

    LaunchedEffect(container.controller) {
        container.controller.observeConnectivity()
    }

    DesignTheme(preset = activePreset, darkTheme = darkTheme) {
        CompositionLocalProvider(LocalImageRenderer provides container.imageRenderer) {
            Column(modifier = Modifier.fillMaxSize().background(ThemeTokens.colors.background.primary)) {
                if (showTopBar) {
                    SampleTopBar(
                        verticalApps = container.verticalApps,
                        availableScreens = container.availableScreens,
                        activeScreen = activeScreen,
                        mode = launcherMode,
                        pendingOfflineCount = pendingOfflineCount,
                        darkTheme = darkTheme,
                        onSelectMode = { launcherMode = it },
                        onSelectScreen = { container.navigateTo(it) },
                        onFlushOffline = { container.flushOfflineActions() },
                        onToggleTheme = { darkTheme = !darkTheme },
                    )
                    UiDivider(atom = DividerAtom())
                }
                SduiScreen(
                    state = uiState,
                    snippetRegistry = container.snippetRegistry,
                    onAction = container.controller::onAction,
                    onFormValueChange = container.controller::onFormFieldChanged,
                    onRetry = { container.controller.retry() },
                    onDismissOverlay = container.controller::dismissOverlay,
                    onDismissToast = container.controller::dismissToast,
                    onDismissFeedback = container.controller::dismissFeedback,
                    onSnippetImpression = container.controller::onSnippetImpression,
                    onLoadMore = { container.controller.loadMore() },
                    isWatch = SampleShowcaseApps.normalizeBaseScreenId(activeScreen) == "watch-status",
                    onHaptic = container.platformBridges.hapticEngine.asUiHapticCallback(),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
            }
        }
    }
}
