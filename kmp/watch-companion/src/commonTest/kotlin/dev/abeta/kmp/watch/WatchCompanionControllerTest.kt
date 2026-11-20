package dev.abeta.kmp.watch

import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.analytics.RecordingAnalyticsTracker
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.core.platform.ConnectivityStatus
import dev.abeta.kmp.core.platform.HapticStyle
import dev.abeta.kmp.core.platform.MutableConnectivityMonitor
import dev.abeta.kmp.core.platform.RecordingExternalLauncherPort
import dev.abeta.kmp.core.platform.RecordingHapticEngine
import dev.abeta.kmp.core.platform.RecordingSharePort
import dev.abeta.kmp.core.storage.InMemoryKeyValueStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WatchCompanionControllerTest {
    @Test
    fun loadsWatchStatusFromMockBffAndTriggersOneTapReorderHaptics() =
        runTest(StandardTestDispatcher()) {
            val haptics = RecordingHapticEngine()
            val launcher = RecordingExternalLauncherPort()
            val sharePort = RecordingSharePort()
            val monitor = MutableConnectivityMonitor(ConnectivityStatus.ONLINE)

            val mockEngine = buildMockWatchEngine()
            val app =
                koinApplication {
                    modules(
                        createWatchCompanionModule(
                            baseUrl = "https://bff.abeta.dev",
                            scope = this@runTest,
                            httpEngine = mockEngine,
                            keyValueStore = InMemoryKeyValueStore(),
                            hapticEngine = haptics,
                            externalLauncher = launcher,
                            sharePort = sharePort,
                            connectivityMonitor = monitor,
                        ),
                    )
                }

            try {
                val controller = app.koin.get<WatchCompanionController>()
                controller.loadScreen("watch-status").join()
                advanceUntilIdle()

                val ready = assertIs<WatchUiState.Ready>(controller.uiState.value)
                assertEquals("Abeta Watch", ready.page.title)
                assertEquals(
                    "8 mins",
                    ready.page.metrics
                        .single()
                        .value,
                )
                assertEquals(
                    0.78f,
                    ready.page.metrics
                        .single()
                        .progressFraction,
                )
                verifyCardAndMetricActions(
                    controller = controller,
                    ready = ready,
                    haptics = haptics,
                    launcher = launcher,
                    sharePort = sharePort,
                    scope = this@runTest,
                )

                val connJob = launch { controller.observeConnectivity() }
                try {
                    monitor.setStatus(ConnectivityStatus.OFFLINE)
                    advanceUntilIdle()
                    assertTrue(assertIs<WatchUiState.Ready>(controller.uiState.value).page.isOffline)
                } finally {
                    connJob.cancel()
                }
            } finally {
                app.close()
            }
        }

    private suspend fun verifyCardAndMetricActions(
        controller: WatchCompanionController,
        ready: WatchUiState.Ready,
        haptics: RecordingHapticEngine,
        launcher: RecordingExternalLauncherPort,
        sharePort: RecordingSharePort,
        scope: kotlinx.coroutines.test.TestScope,
    ) {
        val reorderAction =
            assertNotNull(
                ready.page.cards
                    .single()
                    .primaryAction,
            )
        controller.onAction(reorderAction).join()
        scope.advanceUntilIdle()
        assertTrue(HapticStyle.TICK in haptics.performed)
        assertTrue(HapticStyle.SUCCESS in haptics.performed)

        val shareAction =
            assertNotNull(
                ready.page.cards
                    .single()
                    .secondaryAction,
            )
        controller.onAction(shareAction).join()
        assertEquals(1, sharePort.requests.size)

        val supportAction =
            assertNotNull(
                ready.page.metrics
                    .single()
                    .onClick,
            )
        controller.onAction(supportAction).join()
        assertEquals(listOf("https://abeta.dev/docs"), launcher.openedUrls)
    }

    @Test
    fun starterPreviewSourceAndTrackingWorkOffline() =
        runTest(StandardTestDispatcher()) {
            val haptics = RecordingHapticEngine()
            val tracker = RecordingAnalyticsTracker()
            val controller =
                WatchCompanionController(
                    pageSource = WatchOfflineStarterDefaults.createStarterPageSource(),
                    actionDispatcher =
                        dev.abeta.kmp.actions.handler.DefaultActionDispatcher(
                            decoderRegistry =
                                dev.abeta.kmp.actions.codec.BuiltInActionDecoders
                                    .createDefaultRegistry(),
                            actionRegistry =
                                dev.abeta.kmp.actions.handler.BuiltInActionHandlers.createDefaultRegistry(
                                    offlineWatchStarterApiExecutor(),
                                ),
                        ),
                    scope = this,
                    analyticsTracker = tracker,
                    hapticEngine = haptics,
                )
            controller.loadScreen().join()
            val ready = assertIs<WatchUiState.Ready>(controller.uiState.value)
            assertEquals("watch-status", ready.page.pageId)
            assertEquals(
                "1-Tap Reorder",
                ready.page.cards
                    .single()
                    .primaryActionLabel,
            )

            controller
                .onAction(
                    ActionRef(
                        type = ActionTypes.TRACK_EVENT,
                        payload = buildJsonObject { put("event_name", "watch_tile_tap") },
                    ),
                ).join()
            assertEquals("watch_tile_tap", tracker.events.single().name)
        }

    private fun buildMockWatchEngine(): MockEngine =
        MockEngine { request ->
            val (code, body) =
                when (request.url.encodedPath) {
                    "/api/v1/screens/watch-status" -> {
                        HttpStatusCode.OK to """{"success":true,"data":$WATCH_PAGE_JSON}"""
                    }

                    "/api/v1/orders/express" -> {
                        HttpStatusCode.OK to """{"success":true,"data":{"follow_up_actions":[]}}"""
                    }

                    else -> {
                        HttpStatusCode.NotFound to """{"success":false,"error":{"message":"Not found"}}"""
                    }
                }
            respond(
                content = body,
                status = code,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

    private companion object {
        val WATCH_PAGE_JSON =
            """
            {
              "page_id": "watch-status",
              "title": "Abeta Watch",
              "snippets": [
                {
                  "snippet_id": "watch_eta_metric",
                  "snippet_type": "metric_card",
                  "data": {
                    "label": { "text": "Express SLA" },
                    "value": { "text": "8 mins" },
                    "progress": { "kind": "linear", "progress": 0.78, "label": "Arriving in 8 mins" },
                    "on_click": { "id": "help", "type": "open_url", "payload": { "url": "https://abeta.dev/docs" } }
                  }
                },
                {
                  "snippet_id": "watch_active_order",
                  "snippet_type": "card_v1",
                  "data": {
                    "title": { "text": "Rider En Route · 8 min" },
                    "subtitle": { "text": "Abeta Fresh · Bag #AF-409" },
                    "badge": { "label": "LIVE" },
                    "primary_button": {
                      "label": "1-Tap Reorder",
                      "on_click": {
                        "id": "watch_reorder",
                        "type": "call_api",
                        "payload": { "endpoint": "/api/v1/orders/express", "method": "POST" },
                        "secondary_actions": [
                          { "id": "tick", "type": "trigger_feedback", "payload": { "effect": "haptic_tick" } }
                        ]
                      }
                    },
                    "secondary_button": {
                      "label": "Share ETA",
                      "on_click": {
                        "id": "share",
                        "type": "share",
                        "payload": { "title": "Abeta Fresh", "text": "Arriving in 8 mins!" }
                      }
                    }
                  }
                }
              ]
            }
            """.trimIndent()
    }
}
