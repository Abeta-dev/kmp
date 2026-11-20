package dev.abeta.kmp.watch

import dev.abeta.kmp.actions.handler.ActionDispatcher
import dev.abeta.kmp.actions.handler.ActionOutcome
import dev.abeta.kmp.actions.model.ActionContext
import dev.abeta.kmp.analytics.AnalyticsEvent
import dev.abeta.kmp.analytics.AnalyticsTracker
import dev.abeta.kmp.analytics.LoggerAnalyticsTracker
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.FeedbackEffect
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.core.logging.NoOpAppLogger
import dev.abeta.kmp.core.platform.ConnectivityMonitor
import dev.abeta.kmp.core.platform.ConnectivityStatus
import dev.abeta.kmp.core.platform.ExternalLauncherPort
import dev.abeta.kmp.core.platform.HapticEngine
import dev.abeta.kmp.core.platform.HapticStyle
import dev.abeta.kmp.core.platform.MutableConnectivityMonitor
import dev.abeta.kmp.core.platform.NoOpHapticEngine
import dev.abeta.kmp.core.platform.RecordingExternalLauncherPort
import dev.abeta.kmp.core.platform.RecordingSharePort
import dev.abeta.kmp.core.platform.SharePort
import dev.abeta.kmp.core.platform.ShareRequest
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.state.session.InMemorySessionStore
import dev.abeta.kmp.state.session.SessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Headless companion state controller for watchOS WatchKit & Android Wear OS hosts.
 * Coordinates [WatchPageSource], [ActionDispatcher], [SessionStore], [HapticEngine], and OS bridges
 * without Compose UI or UIKit dependencies.
 */
@Suppress("LongParameterList", "TooManyFunctions")
class WatchCompanionController(
    private val pageSource: WatchPageSource,
    private val actionDispatcher: ActionDispatcher,
    private val scope: CoroutineScope,
    private val mapper: WatchScreenMapper = WatchScreenMapper(),
    private val sessionStore: SessionStore = InMemorySessionStore(),
    private val analyticsTracker: AnalyticsTracker = LoggerAnalyticsTracker(NoOpAppLogger()),
    private val hapticEngine: HapticEngine = NoOpHapticEngine,
    private val externalLauncher: ExternalLauncherPort = RecordingExternalLauncherPort(),
    private val sharePort: SharePort = RecordingSharePort(),
    private val connectivityMonitor: ConnectivityMonitor = MutableConnectivityMonitor(),
) {
    private val _uiState = MutableStateFlow<WatchUiState>(WatchUiState.Idle)

    /** Observable state stream rendered natively in WatchKit SwiftUI or Wear OS Compose Tiles. */
    val uiState: StateFlow<WatchUiState> = _uiState.asStateFlow()

    private var activeScreenId: String = DEFAULT_WATCH_SCREEN

    /** Loads and maps the SDUI screen [screenId] for wearable presentation. */
    fun loadScreen(screenId: String = DEFAULT_WATCH_SCREEN): Job {
        activeScreenId = screenId
        _uiState.value = WatchUiState.Loading
        return scope.launch {
            when (val result = pageSource.fetchPage(screenId)) {
                is AppResult.Failure -> {
                    hapticEngine.perform(HapticStyle.ERROR)
                    _uiState.value = WatchUiState.Error(error = result.error, screenId = screenId)
                }

                is AppResult.Success -> {
                    handleLoadedPage(result.value)
                }
            }
        }
    }

    /** Reloads the currently active watch screen. */
    fun refresh(): Job = loadScreen(activeScreenId)

    /** Dispatches a user tap [action] on the watch and routes resulting haptics/outcomes. */
    fun onAction(action: ActionRef): Job =
        scope.launch {
            val context =
                ActionContext(
                    sessionState = sessionStore.state.value,
                    metadata = mapOf("surface" to "watch", "page_id" to activeScreenId),
                )
            val results = actionDispatcher.dispatch(action, context)
            for (result in results) {
                applyDispatchResult(result)
            }
        }

    /** Clears transient toast status on [WatchUiState.Ready]. */
    fun dismissToast() {
        updateReady { it.copy(activeToast = null) }
    }

    /** Observes OS connectivity transitions until cancelled, updating [WatchGlancePage.isOffline]. */
    suspend fun observeConnectivity() {
        connectivityMonitor.start()
        updateReady { ready ->
            ready.copy(
                page = ready.page.copy(isOffline = !connectivityMonitor.isOnline()),
            )
        }
        try {
            connectivityMonitor.status.collect { status ->
                val nowOffline = status == ConnectivityStatus.OFFLINE
                updateReady { ready -> ready.copy(page = ready.page.copy(isOffline = nowOffline)) }
            }
        } finally {
            connectivityMonitor.stop()
        }
    }

    private suspend fun handleLoadedPage(page: PageResponse) {
        val mapped = mapper.map(page = page, isOffline = !connectivityMonitor.isOnline())
        _uiState.value = WatchUiState.Ready(page = mapped)
        val context = ActionContext(sessionState = sessionStore.state.value)
        for (pageAction in page.pageActions) {
            val results = actionDispatcher.dispatch(pageAction, context)
            for (result in results) {
                applyDispatchResult(result)
            }
        }
    }

    private suspend fun applyDispatchResult(result: AppResult<ActionOutcome>) {
        when (result) {
            is AppResult.Failure -> {
                triggerHaptic(HapticStyle.ERROR)
                updateReady { it.copy(activeToast = result.error.message) }
            }

            is AppResult.Success -> {
                applyOutcome(result.value)
            }
        }
    }

    private suspend fun applyOutcome(outcome: ActionOutcome) {
        if (!applyFeedbackOrBridgeOutcome(outcome)) {
            applyNavigationOrStateOutcome(outcome)
        }
    }

    private suspend fun applyFeedbackOrBridgeOutcome(outcome: ActionOutcome): Boolean =
        when (outcome) {
            is ActionOutcome.FeedbackTriggered -> {
                triggerHaptic(outcome.effect.toHapticStyle())
                val toast = outcome.title ?: outcome.subtitle
                if (toast != null) updateReady { it.copy(activeToast = toast) }
                true
            }

            is ActionOutcome.OpenUrlRequested -> {
                externalLauncher.openUrl(outcome.url)
                true
            }

            is ActionOutcome.ShareRequested -> {
                sharePort.share(ShareRequest(text = outcome.text, title = outcome.title, url = outcome.url))
                true
            }

            is ActionOutcome.ApiCallResult -> {
                triggerHaptic(if (outcome.isSuccess) HapticStyle.SUCCESS else HapticStyle.ERROR)
                true
            }

            else -> {
                false
            }
        }

    private fun applyNavigationOrStateOutcome(outcome: ActionOutcome) {
        when (outcome) {
            is ActionOutcome.NavigationRequested -> {
                val nextId =
                    outcome.route
                        .trim()
                        .removePrefix("/screens/")
                        .removePrefix("/")
                if (nextId.isNotEmpty()) loadScreen(nextId)
            }

            is ActionOutcome.ToastRequested -> {
                updateReady { it.copy(activeToast = outcome.message) }
            }

            is ActionOutcome.StateMutated -> {
                sessionStore.putAll(outcome.savedEntries)
                sessionStore.remove(outcome.deletedKeys)
            }

            is ActionOutcome.TrackingRequested -> {
                analyticsTracker.track(
                    AnalyticsEvent(
                        name = outcome.eventName,
                        properties = outcome.properties.mapValues { (_, v) -> v.toString() },
                    ),
                )
            }

            is ActionOutcome.RefreshRequested -> {
                refreshPage(outcome.pageId ?: activeScreenId)
            }

            else -> {}
        }
    }

    private fun refreshPage(screenId: String) {
        loadScreen(screenId)
    }

    private fun triggerHaptic(style: HapticStyle) {
        hapticEngine.perform(style)
        updateReady { it.copy(lastHaptic = style) }
    }

    private inline fun updateReady(transform: (WatchUiState.Ready) -> WatchUiState.Ready) {
        _uiState.update { current -> if (current is WatchUiState.Ready) transform(current) else current }
    }

    private companion object {
        const val DEFAULT_WATCH_SCREEN = "watch-status"

        fun FeedbackEffect.toHapticStyle(): HapticStyle =
            when (this) {
                FeedbackEffect.CELEBRATION -> HapticStyle.SUCCESS
                FeedbackEffect.CART_BOUNCE -> HapticStyle.CLICK
                FeedbackEffect.SHAKE_ERROR -> HapticStyle.ERROR
                FeedbackEffect.HAPTIC_TICK -> HapticStyle.TICK
            }
    }
}
