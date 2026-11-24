package dev.abeta.kmp.layout.engine

import dev.abeta.kmp.actions.handler.ActionDispatcher
import dev.abeta.kmp.actions.handler.ActionOutcome
import dev.abeta.kmp.analytics.AnalyticsEvent
import dev.abeta.kmp.analytics.AnalyticsTracker
import dev.abeta.kmp.analytics.AnalyticsTrackingPayload
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.SnippetDeltaPatch
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.TrackingContract
import dev.abeta.kmp.core.error.AppError
import dev.abeta.kmp.core.error.ValidationError
import dev.abeta.kmp.core.logging.AppLogger
import dev.abeta.kmp.core.logging.NoOpAppLogger
import dev.abeta.kmp.core.platform.ConnectivityStatus
import dev.abeta.kmp.core.platform.DeepLinkParser
import dev.abeta.kmp.core.platform.HapticStyle
import dev.abeta.kmp.core.platform.PlatformCapabilities
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.layout.platform.PlatformBridgeBundle
import dev.abeta.kmp.layout.platform.toHapticStyle
import dev.abeta.kmp.layout.repository.ScreenRepository
import dev.abeta.kmp.network.sse.SseReconnectPolicy
import dev.abeta.kmp.network.sse.SseTransport
import dev.abeta.kmp.state.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

/**
 * Immutable UI state emitted by [LayoutController] for rendering an SDUI screen.
 */
sealed interface LayoutUiState {
    /** Initial state before any screen load has been triggered. */
    data object Idle : LayoutUiState

    /** Screen fetch in progress. */
    data object Loading : LayoutUiState

    /** Active screen loaded and ready for interaction. */
    data class Ready(
        val page: PageResponse,
        val formValues: Map<String, String> = emptyMap(),
        val activeOverlay: ActionOutcome.OverlayRequested? = null,
        val activeToast: ActionOutcome.ToastRequested? = null,
        val validationErrors: Map<String, String> = emptyMap(),
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val pendingOfflineActions: Int = 0,
        val activeFeedback: ActionOutcome.FeedbackTriggered? = null,
        val feedbackSequence: Long = 0L,
        val isOffline: Boolean = false,
        val errorShakeSequence: Long = 0L,
        val errorShakeTargetId: String? = null,
        val isStreaming: Boolean = false,
    ) : LayoutUiState

    /** Screen fetch failed with a typed [AppError]. */
    data class Error(
        val error: AppError,
        val screenId: String? = null,
    ) : LayoutUiState
}

/**
 * Stateful SDUI screen orchestrator coordinating [ScreenRepository], [ActionDispatcher],
 * [SessionStore], [AnalyticsTracker], optional [OfflineActionCoordinator], and host [PlatformBridgeBundle].
 */
@Suppress("LongParameterList", "TooManyFunctions")
class LayoutController(
    private val screenRepository: ScreenRepository,
    private val actionDispatcher: ActionDispatcher,
    private val sessionStore: SessionStore,
    private val analyticsTracker: AnalyticsTracker,
    private val scope: CoroutineScope,
    private val navigationListener: (ActionOutcome.NavigationRequested) -> Unit = {},
    private val backListener: (ActionOutcome.BackRequested) -> Unit = {},
    private val clipboardListener: (ActionOutcome.ClipboardCopyRequested) -> Unit = {},
    private val logger: AppLogger = NoOpAppLogger(),
    private val offlineCoordinator: OfflineActionCoordinator? = null,
    private val platformBridges: PlatformBridgeBundle = PlatformBridgeBundle.createTestDefault(),
    private val sseTransport: SseTransport? = null,
) {
    private val _uiState = MutableStateFlow<LayoutUiState>(LayoutUiState.Idle)

    /** Observable SDUI screen UI state. */
    val uiState: StateFlow<LayoutUiState> = _uiState.asStateFlow()

    /** Active host OS capability snapshot. */
    val capabilities: PlatformCapabilities
        get() = platformBridges.capabilities

    /** Most recently requested or loaded screen identifier, if any. */
    val currentScreenId: String?
        get() = activeScreenId

    private val trackedImpressions = mutableSetOf<String>()
    private val trackedNodeImpressions = mutableSetOf<String>()
    private var activeScreenId: String? = null
    private var activeLoadJob: Job? = null
    private var pendingDeepLinkParams: Map<String, String> = emptyMap()
    private var pendingRestoredFormValues: Map<String, String> = emptyMap()
    private val streamController =
        LayoutStreamController(
            scope = scope,
            sseTransport = sseTransport,
            logger = logger,
            onPatch = ::applyDeltaPatch,
            onStreamingChanged = { streaming ->
                updateReady { current ->
                    if (current.isStreaming != streaming) current.copy(isStreaming = streaming) else current
                }
            },
        )
    private val bridgeOutcomeExecutor =
        PlatformBridgeOutcomeExecutor(
            bridges = platformBridges,
            navigationListener = navigationListener,
            clipboardListener = clipboardListener,
            showToast = { message, variant ->
                updateReady {
                    it.copy(activeToast = ActionOutcome.ToastRequested(message = message, variant = variant))
                }
            },
            dispatchFollowUp = ::dispatchAndApply,
        )

    /** Loads the SDUI screen identified by [screenId], returning the launched [Job]. */
    fun loadScreen(
        screenId: String,
        showLoader: Boolean = true,
    ): Job {
        stopStream()
        activeScreenId = screenId
        trackedImpressions.clear()
        trackedNodeImpressions.clear()
        if (showLoader || _uiState.value !is LayoutUiState.Ready) {
            _uiState.value = LayoutUiState.Loading
        }
        val job =
            scope.launch {
                when (val result = screenRepository.fetchScreen(screenId)) {
                    is AppResult.Failure -> {
                        logger.warn(TAG, "Failed to load screen '$screenId': ${result.error.message}")
                        _uiState.value = LayoutUiState.Error(error = result.error, screenId = screenId)
                    }

                    is AppResult.Success -> {
                        onPageLoaded(result.value)
                    }
                }
            }
        activeLoadJob = job
        return job
    }

    /** Retries loading the most recently requested screen, if any. */
    fun retry(): Job? = activeScreenId?.let { loadScreen(it, showLoader = true) }

    /** Parses a custom-scheme or HTTPS App/Universal link and navigates to its resolved screen. */
    fun handleDeepLink(uri: String): Job? {
        val parsed = DeepLinkParser.parse(uri) ?: return null
        pendingDeepLinkParams = parsed.params
        return loadScreen(parsed.screenId)
    }

    /**
     * Observes OS network reachability until cancelled, updating [LayoutUiState.Ready.isOffline]
     * and automatically flushing queued offline mutations upon connectivity recovery.
     */
    suspend fun observeConnectivity() {
        val monitor = platformBridges.connectivityMonitor
        monitor.start()
        var previouslyOffline = monitor.status.value == ConnectivityStatus.OFFLINE
        updateReady { it.copy(isOffline = previouslyOffline) }
        try {
            monitor.status.collect { status ->
                val nowOffline = status == ConnectivityStatus.OFFLINE
                updateReady { it.copy(isOffline = nowOffline) }
                if (previouslyOffline && !nowOffline) {
                    flushOfflineActions()
                }
                previouslyOffline = nowOffline
            }
        } finally {
            monitor.stop()
        }
    }

    /** Triggers the active page's `pull_to_refresh` action or performs a quiet screen reload. */
    fun refreshFromPull(): Job? {
        val ready = _uiState.value as? LayoutUiState.Ready
        val pullAction = ready?.page?.pullToRefresh
        val screenId = activeScreenId
        return when {
            ready == null || (pullAction == null && screenId == null) -> {
                null
            }

            pullAction == null -> {
                updateReady { it.copy(isRefreshing = true) }
                loadScreen(screenId = requireNotNull(screenId), showLoader = false)
            }

            else -> {
                launchAction(action = pullAction, isPullRefresh = true)
            }
        }
    }

    /** Dispatches the active page's cursor pagination `load_more_action`, if available. */
    fun loadMore(): Job? {
        val ready = _uiState.value as? LayoutUiState.Ready
        val loadMoreAction =
            ready
                ?.page
                ?.pagination
                ?.takeIf { it.hasMore && !ready.isLoadingMore }
                ?.loadMoreAction
        return loadMoreAction?.let { action ->
            updateReady { it.copy(isLoadingMore = true) }
            scope.launch {
                try {
                    dispatchAndApply(action)
                } finally {
                    updateReady { it.copy(isLoadingMore = false) }
                }
            }
        }
    }

    /** Flushes all queued offline API actions through [offlineCoordinator], if present. */
    fun flushOfflineActions(): Job? {
        val coordinator = offlineCoordinator ?: return null
        return scope.launch {
            when (val result = coordinator.flushAll()) {
                is AppResult.Success -> {
                    val report = result.value
                    updateReady { current ->
                        LayoutStateReducer.applyOfflineFlushed(
                            current = current,
                            pendingCount = coordinator.status.value.pendingCount,
                            succeededCount = report.succeeded,
                        )
                    }
                }

                is AppResult.Failure -> {
                    logger.warn(TAG, "Failed to flush offline actions: ${result.error.message}")
                    updateReady { it.copy(pendingOfflineActions = coordinator.status.value.pendingCount) }
                }
            }
        }
    }

    /** Updates a form field value in [SessionStore] and clears any prior validation error for [fieldId]. */
    fun onFormFieldChanged(
        fieldId: String,
        value: String,
    ) {
        sessionStore.put(fieldId, JsonPrimitive(value))
        updateReady { current ->
            current.copy(
                formValues = current.formValues + (fieldId to value),
                validationErrors = current.validationErrors - fieldId,
            )
        }
    }

    /** Returns a snapshot of active or pending restored form values for persistence. */
    fun currentFormValuesSnapshot(): Map<String, String> {
        val ready = _uiState.value as? LayoutUiState.Ready
        return ready?.formValues ?: pendingRestoredFormValues
    }

    /**
     * Restores persisted form field values into [SessionStore] and active UI state (or queues
     * them for the next loaded page), supporting Android process-death recovery.
     */
    fun restoreFormValues(values: Map<String, String>) {
        if (values.isEmpty()) return
        values.forEach { (fieldId, value) ->
            sessionStore.put(fieldId, JsonPrimitive(value))
        }
        pendingRestoredFormValues = pendingRestoredFormValues + values
        updateReady { current ->
            current.copy(formValues = current.formValues + values)
        }
    }

    /** Records a deduplicated impression for [snippet] when it becomes visible. */
    fun onSnippetImpression(snippet: SnippetEnvelope) {
        val tracking = snippet.tracking ?: return
        if (!trackedImpressions.add(snippet.snippetId)) {
            return
        }
        analyticsTracker.trackImpression(
            LayoutAnalyticsHelper.buildSnippetImpressionPayload(
                snippet = snippet,
                tracking = tracking,
                pageId = currentPageId(),
            ),
        )
    }

    /** Records a deduplicated impression for a composed node identified by [nodeKey]. */
    fun onNodeImpression(
        nodeKey: String,
        tracking: TrackingContract,
    ) {
        if (nodeKey.isBlank() || !trackedNodeImpressions.add(nodeKey)) {
            return
        }
        analyticsTracker.trackImpression(
            LayoutAnalyticsHelper.buildNodeImpressionPayload(
                nodeKey = nodeKey,
                tracking = tracking,
                pageId = currentPageId(),
            ),
        )
    }

    /** Dismisses the currently visible overlay dialog, if any. */
    fun dismissOverlay() {
        updateReady { current -> if (current.activeOverlay != null) current.copy(activeOverlay = null) else current }
    }

    /** Dismisses the currently visible toast message, if any. */
    fun dismissToast() {
        updateReady { current -> if (current.activeToast != null) current.copy(activeToast = null) else current }
    }

    /** Dismisses the currently active feedback overlay, if any. */
    fun dismissFeedback() {
        updateReady { current -> if (current.activeFeedback != null) current.copy(activeFeedback = null) else current }
    }

    /** Dispatches an [ActionRef] through [actionDispatcher] and applies resulting outcomes. */
    fun onAction(action: ActionRef): Job {
        val isPullRefresh = (_uiState.value as? LayoutUiState.Ready)?.page?.pullToRefresh == action
        return launchAction(action = action, isPullRefresh = isPullRefresh)
    }

    /** Applies an incremental [SnippetDeltaPatch] to the active [LayoutUiState.Ready] page. */
    fun applyDeltaPatch(patch: SnippetDeltaPatch) {
        updateReady { current -> current.copy(page = applySnippetDeltaPatch(current.page, patch)) }
    }

    /** Connects to an SSE stream at [path] via [sseTransport] and applies incoming [SnippetDeltaPatch] frames. */
    fun observeStream(
        path: String,
        headers: Map<String, String> = emptyMap(),
        lastEventId: String? = null,
        reconnectPolicy: SseReconnectPolicy = SseReconnectPolicy(),
    ): Job? = streamController.observe(path, headers, lastEventId, reconnectPolicy)

    /** Cancels the currently active SSE stream, if any, and resets [LayoutUiState.Ready.isStreaming]. */
    fun stopStream() = streamController.stop()

    private fun launchAction(
        action: ActionRef,
        isPullRefresh: Boolean,
    ): Job {
        if (isPullRefresh) {
            updateReady { it.copy(isRefreshing = true) }
        }
        return scope.launch {
            try {
                dispatchAndApply(action)
                if (isPullRefresh) {
                    activeLoadJob?.takeIf { it.isActive }?.join()
                }
            } finally {
                if (isPullRefresh) {
                    updateReady { it.copy(isRefreshing = false) }
                }
            }
        }
    }

    private suspend fun dispatchAndApply(action: ActionRef) {
        updateReady { current ->
            if (current.validationErrors.isNotEmpty()) current.copy(validationErrors = emptyMap()) else current
        }
        LayoutAnalyticsHelper
            .buildActionInteractionEvents(action = action, pageId = currentPageId())
            .forEach(analyticsTracker::track)
        val context =
            LayoutStateReducer.buildActionContext(
                ready = _uiState.value as? LayoutUiState.Ready,
                sessionState = sessionStore.state.value,
            )
        val results = actionDispatcher.dispatch(action, context)
        for (result in results) {
            applyDispatchResult(result)
        }
        maybeEnqueueOfflineCalls(action, results)
    }

    private suspend fun maybeEnqueueOfflineCalls(
        action: ActionRef,
        results: List<AppResult<ActionOutcome>>,
    ) {
        val coordinator = offlineCoordinator ?: return
        val failure =
            results
                .filterIsInstance<AppResult.Failure>()
                .firstOrNull { LayoutStateReducer.isOfflineOrTransientNetworkError(it.error, HTTP_SERVER_ERROR_MIN) }
        val payloads = if (failure != null) LayoutStateReducer.extractCallApiPayloads(action) else emptyList()
        if (failure == null || payloads.isEmpty()) return

        payloads.forEach { payload ->
            coordinator.enqueueOfflineCall(payload = payload, reason = failure.error.message)
        }
        val pendingCount = coordinator.status.value.pendingCount
        val hasCustomErrorAction = payloads.any { it.onError != null }
        updateReady { current ->
            LayoutStateReducer.applyOfflineEnqueued(current, pendingCount, hasCustomErrorAction)
        }
    }

    private suspend fun onPageLoaded(page: PageResponse) {
        activeScreenId = page.pageId
        val deepLinkParams = pendingDeepLinkParams
        pendingDeepLinkParams = emptyMap()
        val restoredValues = pendingRestoredFormValues
        pendingRestoredFormValues = emptyMap()
        val initialFormValues =
            restoredValues + LayoutFormSeeder.extractInitialFormValues(page, sessionStore) + deepLinkParams
        analyticsTracker.track(LayoutAnalyticsHelper.buildPageOpenEvent(page, deepLinkParams))
        page.tracking?.let { tracking ->
            analyticsTracker.trackImpression(
                AnalyticsTrackingPayload(
                    eventName = tracking.eventName,
                    properties = tracking.properties,
                ),
            )
        }
        _uiState.value =
            LayoutUiState.Ready(
                page = page,
                formValues = initialFormValues,
                pendingOfflineActions = offlineCoordinator?.status?.value?.pendingCount ?: 0,
                isOffline = platformBridges.connectivityMonitor.status.value == ConnectivityStatus.OFFLINE,
            )
        if (page.pageActions.isNotEmpty()) {
            val context =
                LayoutStateReducer.buildActionContext(
                    ready = _uiState.value as? LayoutUiState.Ready,
                    sessionState = sessionStore.state.value,
                )
            for (pageAction in page.pageActions) {
                val results = actionDispatcher.dispatch(pageAction, context)
                for (result in results) {
                    applyDispatchResult(result)
                }
            }
        }
        page.streamSpec?.let(streamController::observeFromSpec)
    }

    private suspend fun applyDispatchResult(result: AppResult<ActionOutcome>) {
        when (result) {
            is AppResult.Failure -> applyFailure(result.error)
            is AppResult.Success -> applyOutcome(result.value)
        }
    }

    private fun applyFailure(error: AppError) {
        logger.warn(TAG, "Action dispatch failure (${error.code}): ${error.message}")
        if (error is ValidationError) {
            platformBridges.hapticEngine.perform(HapticStyle.ERROR)
        }
        updateReady { current -> LayoutStateReducer.applyFailure(current, error) }
    }

    private suspend fun applyOutcome(outcome: ActionOutcome) {
        if (!bridgeOutcomeExecutor.executeIfBridgeOutcome(outcome)) {
            applyStandardOutcome(outcome)
        }
    }

    private suspend fun applyStandardOutcome(outcome: ActionOutcome) {
        when (outcome) {
            ActionOutcome.Handled -> Unit
            is ActionOutcome.NavigationRequested -> navigationListener(outcome)
            is ActionOutcome.BackRequested -> backListener(outcome)
            is ActionOutcome.TrackingRequested -> trackOutcome(outcome)
            is ActionOutcome.StateMutated -> applyStateMutation(outcome)
            is ActionOutcome.OverlayRequested -> applyOverlayRequested(outcome)
            is ActionOutcome.OverlayDismissed -> updateReady { LayoutStateReducer.applyOverlayDismissed(it, outcome) }
            is ActionOutcome.ToastRequested -> updateReady { it.copy(activeToast = outcome) }
            is ActionOutcome.RefreshRequested -> applyRefreshRequested(outcome)
            is ActionOutcome.SnippetsUpdated -> applySnippetsUpdated(outcome)
            is ActionOutcome.FeedbackTriggered -> applyFeedbackTriggered(outcome)
            is ActionOutcome.ApiCallResult -> updateReady { LayoutStateReducer.applyApiCallFailure(it, outcome) }
            else -> Unit
        }
    }

    private suspend fun applyOverlayRequested(outcome: ActionOutcome.OverlayRequested) {
        val initialSheetValues =
            if (outcome.snippets.isNotEmpty()) {
                LayoutFormSeeder.extractInitialFormValuesFromSnippets(outcome.snippets, sessionStore)
            } else {
                emptyMap()
            }
        updateReady { current ->
            current.copy(
                activeOverlay = outcome,
                formValues = initialSheetValues + current.formValues,
            )
        }
        val remoteSheetId = outcome.sheetScreenId?.trim()?.takeIf { it.isNotEmpty() }
        if (remoteSheetId != null && outcome.snippets.isEmpty()) {
            when (val result = screenRepository.fetchScreen(remoteSheetId)) {
                is AppResult.Success -> {
                    val fetchedSnippets = result.value.snippets
                    val seeded = LayoutFormSeeder.extractInitialFormValuesFromSnippets(fetchedSnippets, sessionStore)
                    updateReady { current ->
                        val active = current.activeOverlay
                        if (active != null && active.dialogId == outcome.dialogId) {
                            current.copy(
                                activeOverlay = active.copy(snippets = fetchedSnippets),
                                formValues = seeded + current.formValues,
                            )
                        } else {
                            current
                        }
                    }
                }

                is AppResult.Failure -> {
                    logger.warn(TAG, "Failed to load sheet screen '$remoteSheetId': ${result.error.message}")
                }
            }
        }
    }

    private fun currentPageId(): String? = (_uiState.value as? LayoutUiState.Ready)?.page?.pageId ?: activeScreenId

    private fun applySnippetsUpdated(outcome: ActionOutcome.SnippetsUpdated) {
        updateReady { current ->
            val nextSnippets = SnippetListMutator.mutate(current.page.snippets, outcome)
            current.copy(page = current.page.copy(snippets = nextSnippets))
        }
    }

    private fun applyFeedbackTriggered(outcome: ActionOutcome.FeedbackTriggered) {
        platformBridges.hapticEngine.perform(outcome.effect.toHapticStyle())
        updateReady { current -> LayoutStateReducer.applyFeedback(current, outcome) }
    }

    private fun trackOutcome(outcome: ActionOutcome.TrackingRequested) {
        analyticsTracker.track(
            AnalyticsEvent(
                name = outcome.eventName,
                properties = LayoutStateReducer.toAnalyticsProperties(outcome.properties),
                screenName = currentPageId(),
            ),
        )
    }

    private fun applyStateMutation(outcome: ActionOutcome.StateMutated) {
        sessionStore.putAll(outcome.savedEntries)
        sessionStore.remove(outcome.deletedKeys)
        updateReady { current -> LayoutStateReducer.applyStateMutation(current, outcome) }
    }

    private fun applyRefreshRequested(outcome: ActionOutcome.RefreshRequested) {
        val targetPage = outcome.pageId ?: activeScreenId ?: return
        loadScreen(screenId = targetPage, showLoader = outcome.showLoader)
    }

    private inline fun updateReady(transform: (LayoutUiState.Ready) -> LayoutUiState.Ready) {
        _uiState.update { current ->
            if (current is LayoutUiState.Ready) transform(current) else current
        }
    }

    private companion object {
        const val TAG = "LayoutController"
        const val HTTP_SERVER_ERROR_MIN = 500
    }
}
