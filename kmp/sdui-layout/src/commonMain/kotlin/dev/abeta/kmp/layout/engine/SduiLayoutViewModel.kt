package dev.abeta.kmp.layout.engine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.savedState
import androidx.savedstate.write
import dev.abeta.kmp.actions.handler.ActionDispatcher
import dev.abeta.kmp.actions.handler.ActionOutcome
import dev.abeta.kmp.analytics.AnalyticsTracker
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.TrackingContract
import dev.abeta.kmp.core.logging.AppLogger
import dev.abeta.kmp.core.logging.NoOpAppLogger
import dev.abeta.kmp.core.platform.PlatformCapabilities
import dev.abeta.kmp.layout.platform.PlatformBridgeBundle
import dev.abeta.kmp.layout.repository.ScreenRepository
import dev.abeta.kmp.state.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Process-death persistence snapshot of the active SDUI screen identifier and form values.
 */
data class SduiSavedSnapshot(
    val screenId: String? = null,
    val formValues: Map<String, String> = emptyMap(),
)

/**
 * Official Google AndroidX KMP [ViewModel] owning a [LayoutController] bound to [viewModelScope].
 *
 * Preserves active screen, cart, and form state across Android configuration changes, cancels
 * in-flight coroutines automatically in [onCleared], and provides [SavedState] snapshot helpers
 * ([saveStateTo], [createSavedState], [restoreStateFrom]) for process-death resilience.
 */
@Suppress("TooManyFunctions")
class SduiLayoutViewModel : ViewModel {
    private val onClearedCallback: () -> Unit

    /** Underlying [LayoutController] bound to this [ViewModel]'s [viewModelScope]. */
    val controller: LayoutController

    /** Creates a [SduiLayoutViewModel] backed by the default lifecycle [viewModelScope]. */
    constructor(
        controllerFactory: (CoroutineScope) -> LayoutController,
        onClearedCallback: () -> Unit = {},
    ) : super() {
        this.onClearedCallback = onClearedCallback
        this.controller = controllerFactory(viewModelScope)
    }

    /** Creates a [SduiLayoutViewModel] backed by an explicit [viewModelScope] (e.g. in tests). */
    constructor(
        viewModelScope: CoroutineScope,
        controllerFactory: (CoroutineScope) -> LayoutController,
        onClearedCallback: () -> Unit = {},
    ) : super(viewModelScope) {
        this.onClearedCallback = onClearedCallback
        this.controller = controllerFactory(this.viewModelScope)
    }

    /** Observable SDUI screen UI state. */
    val uiState: StateFlow<LayoutUiState>
        get() = controller.uiState

    /** Active host OS capability snapshot. */
    val capabilities: PlatformCapabilities
        get() = controller.capabilities

    /** Most recently requested or loaded screen identifier, if any. */
    val lastLoadedScreenId: String?
        get() = controller.currentScreenId

    /** Loads the SDUI screen identified by [screenId]. */
    fun loadScreen(
        screenId: String,
        showLoader: Boolean = true,
    ): Job = controller.loadScreen(screenId = screenId, showLoader = showLoader)

    /** Retries loading the most recently requested screen, if any. */
    fun retry(): Job? = controller.retry()

    /** Parses a custom-scheme or HTTPS App/Universal link and loads the resolved screen. */
    fun handleDeepLink(uri: String): Job? = controller.handleDeepLink(uri)

    /** Observes OS network reachability and flushes queued offline mutations upon reconnect. */
    suspend fun observeConnectivity() = controller.observeConnectivity()

    /** Triggers the active page's `pull_to_refresh` action or performs a quiet screen reload. */
    fun refreshFromPull(): Job? = controller.refreshFromPull()

    /** Dispatches the active page's cursor pagination `load_more_action`, if available. */
    fun loadMore(): Job? = controller.loadMore()

    /** Flushes all queued offline API actions, if an [OfflineActionCoordinator] is configured. */
    fun flushOfflineActions(): Job? = controller.flushOfflineActions()

    /** Updates a form field value and clears any prior validation error for [fieldId]. */
    fun onFormFieldChanged(
        fieldId: String,
        value: String,
    ) = controller.onFormFieldChanged(fieldId = fieldId, value = value)

    /** Records a deduplicated impression for [snippet]. */
    fun onSnippetImpression(snippet: SnippetEnvelope) = controller.onSnippetImpression(snippet)

    /** Records a deduplicated impression for a composed node identified by [nodeKey]. */
    fun onNodeImpression(
        nodeKey: String,
        tracking: TrackingContract,
    ) = controller.onNodeImpression(nodeKey = nodeKey, tracking = tracking)

    /** Dismisses the currently visible overlay dialog or bottom sheet, if any. */
    fun dismissOverlay() = controller.dismissOverlay()

    /** Dismisses the currently visible toast message, if any. */
    fun dismissToast() = controller.dismissToast()

    /** Dismisses the currently active feedback overlay, if any. */
    fun dismissFeedback() = controller.dismissFeedback()

    /** Dispatches an [ActionRef] through the underlying [LayoutController]. */
    fun onAction(action: ActionRef): Job = controller.onAction(action)

    /** Captures the current screen identifier and form values as a [SduiSavedSnapshot]. */
    fun snapshotState(): SduiSavedSnapshot =
        SduiSavedSnapshot(
            screenId = controller.currentScreenId,
            formValues = controller.currentFormValuesSnapshot(),
        )

    /** Writes the current screen identifier and form values into [outState]. */
    fun saveStateTo(outState: SavedState) {
        val snapshot = snapshotState()
        outState.write {
            val screenId = snapshot.screenId?.takeIf { it.isNotBlank() }
            if (screenId != null) {
                putString(KEY_SCREEN_ID, screenId)
            } else {
                remove(KEY_SCREEN_ID)
            }
            if (snapshot.formValues.isNotEmpty()) {
                putString(KEY_FORM_VALUES, SduiJson.encodeToString(STRING_MAP_SERIALIZER, snapshot.formValues))
            } else {
                remove(KEY_FORM_VALUES)
            }
        }
    }

    /** Creates a new [SavedState] populated with the current SDUI snapshot. */
    fun createSavedState(): SavedState = savedState().also(::saveStateTo)

    /**
     * Restores persisted form values and (when [autoReload] is `true` and a `screenId` was saved)
     * reloads the saved screen on [viewModelScope]. Returns the decoded [SduiSavedSnapshot].
     */
    fun restoreStateFrom(
        savedState: SavedState,
        autoReload: Boolean = true,
    ): SduiSavedSnapshot {
        val snapshot = readSnapshot(savedState)
        if (snapshot.formValues.isNotEmpty()) {
            controller.restoreFormValues(snapshot.formValues)
        }
        val targetScreenId = snapshot.screenId?.takeIf { it.isNotBlank() }
        if (autoReload && targetScreenId != null) {
            controller.loadScreen(targetScreenId)
        }
        return snapshot
    }

    override fun onCleared() {
        super.onCleared()
        onClearedCallback()
    }

    companion object {
        const val KEY_SCREEN_ID: String = "abeta_sdui_screen_id"
        const val KEY_FORM_VALUES: String = "abeta_sdui_form_values"

        private val STRING_MAP_SERIALIZER = MapSerializer(String.serializer(), String.serializer())

        /** Decodes a [SduiSavedSnapshot] from [savedState] without mutating any controller. */
        fun readSnapshot(savedState: SavedState): SduiSavedSnapshot =
            savedState.read {
                val screenId = getStringOrNull(KEY_SCREEN_ID)?.takeIf { it.isNotBlank() }
                val rawFormValues = getStringOrNull(KEY_FORM_VALUES)
                val formValues =
                    if (!rawFormValues.isNullOrBlank()) {
                        runCatching {
                            SduiJson.decodeFromString(STRING_MAP_SERIALIZER, rawFormValues)
                        }.getOrDefault(emptyMap())
                    } else {
                        emptyMap()
                    }
                SduiSavedSnapshot(screenId = screenId, formValues = formValues)
            }

        /** Convenience factory building a [SduiLayoutViewModel] directly from its dependencies. */
        @Suppress("LongParameterList")
        fun create(
            screenRepository: ScreenRepository,
            actionDispatcher: ActionDispatcher,
            sessionStore: SessionStore,
            analyticsTracker: AnalyticsTracker,
            navigationListener: (ActionOutcome.NavigationRequested) -> Unit = {},
            backListener: (ActionOutcome.BackRequested) -> Unit = {},
            clipboardListener: (ActionOutcome.ClipboardCopyRequested) -> Unit = {},
            logger: AppLogger = NoOpAppLogger(),
            offlineCoordinator: OfflineActionCoordinator? = null,
            platformBridges: PlatformBridgeBundle = PlatformBridgeBundle.createTestDefault(),
            onClearedCallback: () -> Unit = {},
            viewModelScope: CoroutineScope? = null,
        ): SduiLayoutViewModel {
            val factory: (CoroutineScope) -> LayoutController = { boundScope ->
                LayoutController(
                    screenRepository = screenRepository,
                    actionDispatcher = actionDispatcher,
                    sessionStore = sessionStore,
                    analyticsTracker = analyticsTracker,
                    scope = boundScope,
                    navigationListener = navigationListener,
                    backListener = backListener,
                    clipboardListener = clipboardListener,
                    logger = logger,
                    offlineCoordinator = offlineCoordinator,
                    platformBridges = platformBridges,
                )
            }
            return if (viewModelScope != null) {
                SduiLayoutViewModel(
                    viewModelScope = viewModelScope,
                    controllerFactory = factory,
                    onClearedCallback = onClearedCallback,
                )
            } else {
                SduiLayoutViewModel(
                    controllerFactory = factory,
                    onClearedCallback = onClearedCallback,
                )
            }
        }
    }
}
