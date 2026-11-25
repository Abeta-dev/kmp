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
import dev.abeta.kmp.core.platform.ConnectivityMonitor
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
        val toastSequence: Long = 0L,
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
@Suppress("LongParameterList", "TooManyFunctions", "LargeClass")
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
    private val screenScopedJobs = MutableStateFlow<Set<Job>>(emptySet())
    private var activeScreenId: String? = null
    private var activeLoadJob: Job? = null
    private var screenGeneration: Long = 0L
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
                    it.withToast(ActionOutcome.ToastRequested(message = message, variant = variant))
                }
            },
            dispatchFollowUp = { followUp -> dispatchAndApply(followUp, screenGeneration) },
        )

    /** Loads the SDUI screen identified by [screenId], returning the launched [Job]. */
    fun loadScreen(
        screenId: String,
        showLoader: Boolean = true,
    ): Job = loadScreenInternal(screenId = screenId, showLoader = showLoader, preserveJob = null)

    private fun loadScreenInternal(
        screenId: String,
        showLoader: Boolean,
        preserveJob: Job?,
    ): Job {
        val generation = ++screenGeneration
        activeLoadJob?.cancel()
        cancelScreenScopedJobs(skipJob = preserveJob)
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
                        if (generation != screenGeneration) return@launch
                        logger.warn(TAG, "Failed to load screen '$screenId': ${result.error.message}")
                        handleLoadScreenFailure(
                            screenId = screenId,
                            error = result.error,
                            showLoader = showLoader,
                            generation = generation,
                        )
                    }

                    is AppResult.Success -> {
                        if (generation != screenGeneration) return@launch
                        onPageLoaded(result.value, generation)
                    }
                }
            }
        activeLoadJob = job
        return job
    }

    private fun handleLoadScreenFailure(
        screenId: String,
        error: AppError,
        showLoader: Boolean,
        generation: Long,
    ) {
        if (!showLoader && _uiState.value is LayoutUiState.Ready) {
            updateReadyIfGeneration(generation) { current ->
                current
                    .copy(isRefreshing = false)
                    .withToast(ActionOutcome.ToastRequested(message = error.message, variant = "error"))
            }
        } else if (generation == screenGeneration) {
            _uiState.value = LayoutUiState.Error(error = error, screenId = screenId)
        }
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
        ConnectivityMonitorLifecycle.acquire(monitor)
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
            withContext(NonCancellable) {
                ConnectivityMonitorLifecycle.release(monitor)
            }
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
        val generation = screenGeneration
        var actionToDispatch: ActionRef? = null
        _uiState.update { current ->
            actionToDispatch = null
            val ready = current as? LayoutUiState.Ready
            val candidate =
                ready
                    ?.page
                    ?.pagination
                    ?.takeIf { it.hasMore && !ready.isLoadingMore }
                    ?.loadMoreAction
            if (ready != null && candidate != null) {
                actionToDispatch = candidate
                ready.copy(isLoadingMore = true)
            } else {
                current
            }
        }
        val action = actionToDispatch ?: return null
        return trackScreenScopedJob(
            scope.launch {
                try {
                    dispatchAndApply(action, generation)
                } finally {
                    updateReadyIfGeneration(generation) { it.copy(isLoadingMore = false) }
                }
            },
        )
    }

    /** Flushes all queued offline API actions through [offlineCoordinator], if present. */
    fun flushOfflineActions(): Job? {
        val coordinator = offlineCoordinator ?: return null
        return scope.launch {
            val result =
                coordinator.flushAll(
                    onFollowUpAction = { followUp -> dispatchAndApply(followUp, screenGeneration) },
                )
            when (result) {
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
        val generation = screenGeneration
        if (isPullRefresh) {
            updateReadyIfGeneration(generation) { it.copy(isRefreshing = true) }
        }
        val job =
            scope.launch {
                try {
                    dispatchAndApply(action, generation)
                    if (isPullRefresh) {
                        activeLoadJob?.takeIf { it.isActive }?.join()
                    }
                } finally {
                    if (isPullRefresh) {
                        updateReady { it.copy(isRefreshing = false) }
                    }
                }
            }
        return if (isPullRefresh) job else trackScreenScopedJob(job)
    }

    private suspend fun dispatchAndApply(
        action: ActionRef,
        generation: Long,
    ) {
        if (generation != screenGeneration) return
        updateReadyIfGeneration(generation) { current ->
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
        if (generation != screenGeneration) return
        for (result in results) {
            applyDispatchResult(result, generation)
        }
        maybeEnqueueOfflineCalls(action, results, generation)
    }

    private suspend fun maybeEnqueueOfflineCalls(
        action: ActionRef,
        results: List<AppResult<ActionOutcome>>,
        generation: Long,
    ) {
        val coordinator = offlineCoordinator ?: return
        val transientFailures =
            results
                .filterIsInstance<AppResult.Failure>()
                .filter { LayoutStateReducer.isOfflineOrTransientNetworkError(it.error, HTTP_SERVER_ERROR_MIN) }
        val failedPayloads =
            if (transientFailures.isEmpty()) {
                emptyList()
            } else {
                OfflinePayloadSelector.selectFailedNonGetCallApiPayloads(
                    action = action,
                    results = results,
                    transientFailureCount = transientFailures.size,
                )
            }
        if (failedPayloads.isEmpty()) return

        val primaryReason = transientFailures.first().error.message
        failedPayloads.forEach { payload ->
            coordinator.enqueueOfflineCall(payload = payload, reason = primaryReason)
        }
        val pendingCount = coordinator.status.value.pendingCount
        val hasCustomErrorAction = failedPayloads.any { it.onError != null }
        updateReadyIfGeneration(generation) { current ->
            LayoutStateReducer.applyOfflineEnqueued(current, pendingCount, hasCustomErrorAction)
        }
    }

    private suspend fun onPageLoaded(
        page: PageResponse,
        generation: Long,
    ) {
        if (generation != screenGeneration) return
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
        if (generation != screenGeneration) return
        _uiState.value =
            LayoutUiState.Ready(
                page = page,
                formValues = initialFormValues,
                pendingOfflineActions = offlineCoordinator?.status?.value?.pendingCount ?: 0,
                isOffline = platformBridges.connectivityMonitor.status.value == ConnectivityStatus.OFFLINE,
            )
        dispatchPageActions(page, generation)
        if (generation == screenGeneration) {
            page.streamSpec?.let(streamController::observeFromSpec)
        }
    }

    private suspend fun dispatchPageActions(
        page: PageResponse,
        generation: Long,
    ) {
        if (page.pageActions.isEmpty()) return
        val context =
            LayoutStateReducer.buildActionContext(
                ready = _uiState.value as? LayoutUiState.Ready,
                sessionState = sessionStore.state.value,
            )
        for (pageAction in page.pageActions) {
            if (generation != screenGeneration) break
            val results = actionDispatcher.dispatch(pageAction, context)
            if (generation == screenGeneration) {
                for (result in results) {
                    applyDispatchResult(result, generation)
                }
            }
        }
    }

    private suspend fun applyDispatchResult(
        result: AppResult<ActionOutcome>,
        generation: Long,
    ) {
        when (result) {
            is AppResult.Failure -> applyFailure(result.error, generation)
            is AppResult.Success -> applyOutcome(result.value, generation)
        }
    }

    private fun applyFailure(
        error: AppError,
        generation: Long,
    ) {
        logger.warn(TAG, "Action dispatch failure (${error.code}): ${error.message}")
        if (error is ValidationError) {
            platformBridges.hapticEngine.perform(HapticStyle.ERROR)
        }
        updateReadyIfGeneration(generation) { current -> LayoutStateReducer.applyFailure(current, error) }
    }

    private suspend fun applyOutcome(
        outcome: ActionOutcome,
        generation: Long,
    ) {
        if (!bridgeOutcomeExecutor.executeIfBridgeOutcome(outcome)) {
            applyStandardOutcome(outcome, generation)
        }
    }

    private suspend fun applyStandardOutcome(
        outcome: ActionOutcome,
        generation: Long,
    ) {
        when (outcome) {
            ActionOutcome.Handled -> {
                // Already handled synchronously by the action handler.
            }

            is ActionOutcome.NavigationRequested -> {
                stopStream()
                navigationListener(outcome)
            }

            is ActionOutcome.BackRequested -> {
                stopStream()
                backListener(outcome)
            }

            is ActionOutcome.TrackingRequested -> {
                trackOutcome(outcome)
            }

            is ActionOutcome.StateMutated -> {
                applyStateMutation(outcome, generation)
            }

            is ActionOutcome.OverlayRequested -> {
                applyOverlayRequested(outcome, generation)
            }

            is ActionOutcome.OverlayDismissed -> {
                updateReadyIfGeneration(generation) { LayoutStateReducer.applyOverlayDismissed(it, outcome) }
            }

            is ActionOutcome.ToastRequested -> {
                updateReadyIfGeneration(generation) { it.withToast(outcome) }
            }

            is ActionOutcome.RefreshRequested -> {
                applyRefreshRequested(outcome)
            }

            is ActionOutcome.SnippetsUpdated -> {
                applySnippetsUpdated(outcome, generation)
            }

            is ActionOutcome.FeedbackTriggered -> {
                applyFeedbackTriggered(outcome, generation)
            }

            is ActionOutcome.ApiCallResult -> {
                updateReadyIfGeneration(generation) { LayoutStateReducer.applyApiCallFailure(it, outcome) }
            }

            else -> {
                // Platform bridge outcomes are handled by PlatformBridgeOutcomeExecutor.
            }
        }
    }

    private suspend fun applyOverlayRequested(
        outcome: ActionOutcome.OverlayRequested,
        generation: Long,
    ) {
        val initialSheetValues =
            if (outcome.snippets.isNotEmpty()) {
                LayoutFormSeeder.extractInitialFormValuesFromSnippets(outcome.snippets, sessionStore)
            } else {
                emptyMap()
            }
        updateReadyIfGeneration(generation) { current ->
            current.copy(
                activeOverlay = outcome,
                formValues = initialSheetValues + current.formValues,
            )
        }
        val remoteSheetId = outcome.sheetScreenId?.trim()?.takeIf { it.isNotEmpty() }
        if (remoteSheetId != null && outcome.snippets.isEmpty()) {
            when (val result = screenRepository.fetchScreen(remoteSheetId)) {
                is AppResult.Success -> {
                    if (generation != screenGeneration) return
                    val fetchedSnippets = result.value.snippets
                    val seeded = LayoutFormSeeder.extractInitialFormValuesFromSnippets(fetchedSnippets, sessionStore)
                    updateReadyIfGeneration(generation) { current ->
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

    private fun applySnippetsUpdated(
        outcome: ActionOutcome.SnippetsUpdated,
        generation: Long,
    ) {
        updateReadyIfGeneration(generation) { current ->
            val nextSnippets = SnippetListMutator.mutate(current.page.snippets, outcome)
            current.copy(page = current.page.copy(snippets = nextSnippets))
        }
    }

    private fun applyFeedbackTriggered(
        outcome: ActionOutcome.FeedbackTriggered,
        generation: Long,
    ) {
        platformBridges.hapticEngine.perform(outcome.effect.toHapticStyle())
        updateReadyIfGeneration(generation) { current -> LayoutStateReducer.applyFeedback(current, outcome) }
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

    private fun applyStateMutation(
        outcome: ActionOutcome.StateMutated,
        generation: Long,
    ) {
        sessionStore.putAll(outcome.savedEntries)
        sessionStore.remove(outcome.deletedKeys)
        updateReadyIfGeneration(generation) { current -> LayoutStateReducer.applyStateMutation(current, outcome) }
    }

    private suspend fun applyRefreshRequested(outcome: ActionOutcome.RefreshRequested) {
        val targetPage = outcome.pageId ?: activeScreenId ?: return
        loadScreenInternal(
            screenId = targetPage,
            showLoader = outcome.showLoader,
            preserveJob = currentCoroutineContext()[Job],
        )
    }

    private fun trackScreenScopedJob(job: Job): Job {
        screenScopedJobs.update { it + job }
        job.invokeOnCompletion { screenScopedJobs.update { current -> current - job } }
        return job
    }

    private fun cancelScreenScopedJobs(skipJob: Job? = null) {
        screenScopedJobs.value.forEach { job ->
            if (job !== skipJob) {
                job.cancel()
            }
        }
    }

    private inline fun updateReady(transform: (LayoutUiState.Ready) -> LayoutUiState.Ready) {
        _uiState.update { current ->
            if (current is LayoutUiState.Ready) transform(current) else current
        }
    }

    private inline fun updateReadyIfGeneration(
        generation: Long,
        transform: (LayoutUiState.Ready) -> LayoutUiState.Ready,
    ) {
        if (generation != screenGeneration) return
        _uiState.update { current ->
            if (current is LayoutUiState.Ready && generation == screenGeneration) transform(current) else current
        }
    }

    private companion object {
        const val TAG = "LayoutController"
        const val HTTP_SERVER_ERROR_MIN = 500
    }
}

internal fun LayoutUiState.Ready.withToast(toast: ActionOutcome.ToastRequested): LayoutUiState.Ready =
    copy(
        activeToast = toast,
        toastSequence = toastSequence + 1L,
    )

private object ConnectivityMonitorLifecycle {
    private val mutex = Mutex()
    private val activeObservers = mutableMapOf<ConnectivityMonitor, Int>()

    suspend fun acquire(monitor: ConnectivityMonitor) {
        val shouldStart =
            mutex.withLock {
                val nextCount = (activeObservers[monitor] ?: 0) + 1
                activeObservers[monitor] = nextCount
                nextCount == 1
            }
        if (shouldStart) {
            monitor.start()
        }
    }

    suspend fun release(monitor: ConnectivityMonitor) {
        val shouldStop =
            mutex.withLock {
                val current = activeObservers[monitor] ?: 0
                if (current <= 1) {
                    activeObservers.remove(monitor)
                    current == 1
                } else {
                    activeObservers[monitor] = current - 1
                    false
                }
            }
        if (shouldStop) {
            monitor.stop()
        }
    }
}
