package dev.abeta.kmp.sample

import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.layout.engine.LayoutUiState
import dev.abeta.kmp.layout.repository.CachedScreenRepository
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SampleWiringTest {
    private fun readGoldenScreenJson(fileName: String): String {
        val candidates =
            listOf(
                File("../bff/testdata/golden/$fileName"),
                File("bff/testdata/golden/$fileName"),
                File("../../bff/testdata/golden/$fileName"),
            )
        val file =
            candidates.firstOrNull { it.exists() }
                ?: error("Could not locate golden file '$fileName' in ${candidates.map { it.absolutePath }}")
        return file.readText()
    }

    private fun createMockBffEngine(
        homeJson: String,
        formJson: String,
        containersJson: String,
        storeHomeJson: String,
        isOutage: () -> Boolean,
    ): MockEngine =
        MockEngine { request ->
            val path = request.url.encodedPath
            val cursor = request.url.parameters["cursor"]
            val (status, responseBody) =
                when {
                    path == "/api/v1/screens/home" -> {
                        HttpStatusCode.OK to """{"success":true,"data":$homeJson}"""
                    }

                    path == "/api/v1/screens/form-demo" -> {
                        HttpStatusCode.OK to """{"success":true,"data":$formJson}"""
                    }

                    path == "/api/v1/screens/store-home" -> {
                        HttpStatusCode.OK to """{"success":true,"data":$storeHomeJson}"""
                    }

                    path == "/api/v1/screens/watch-status" -> {
                        val watchJson =
                            SduiJson.encodeToString(PageResponse.serializer(), SampleWatchScreen.watchStatus())
                        HttpStatusCode.OK to """{"success":true,"data":$watchJson}"""
                    }

                    path == "/api/v1/screens/containers" && cursor == "cursor_page_2" -> {
                        HttpStatusCode.OK to
                            """{"success":true,"data":{"page_id":"containers","snippets":[]}}"""
                    }

                    path == "/api/v1/screens/containers" -> {
                        HttpStatusCode.OK to """{"success":true,"data":$containersJson}"""
                    }

                    path == "/api/v1/orders/express" -> {
                        HttpStatusCode.OK to
                            """
                            {"success":true,"data":{"action_id":"place_express_order","status":"confirmed","follow_up_actions":[{"id":"express_order_celebration","type":"trigger_feedback","payload":{"effect":"celebration","title":"Order Confirmed!","subtitle":"Abeta Fresh rider arriving in 8 minutes","duration_ms":2400}},{"id":"express_order_toast","type":"show_toast","payload":{"message":"Express order confirmed!","duration_ms":2500,"variant":"success"}}]}}
                            """.trimIndent()
                    }

                    path == "/api/v1/actions/execute" && isOutage() -> {
                        HttpStatusCode.ServiceUnavailable to
                            """{"success":false,"error":{"code":"UNAVAILABLE","message":"Offline"}}"""
                    }

                    path == "/api/v1/actions/execute" -> {
                        HttpStatusCode.OK to
                            """{"success":true,"data":{"action_id":"submit_workspace_form","status":"ok"}}"""
                    }

                    else -> {
                        HttpStatusCode.NotFound to
                            """{"success":false,"error":{"code":"NOT_FOUND","message":"Unknown: $path"}}"""
                    }
                }
            respond(
                content = responseBody,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

    @Test
    fun remoteBffWiringLoadsHomeFormAndContainersWrappedInEnvelope() =
        runTest {
            var simulateOutage = false
            val mockEngine =
                createMockBffEngine(
                    homeJson = readGoldenScreenJson("home_screen.json"),
                    formJson = readGoldenScreenJson("form_screen.json"),
                    containersJson = readGoldenScreenJson("containers_screen.json"),
                    storeHomeJson = readGoldenScreenJson("store_home.json"),
                    isOutage = { simulateOutage },
                )
            val container = SampleFactory.create(this, "https://bff.abeta.dev", httpEngine = mockEngine)

            try {
                assertIs<CachedScreenRepository>(container.screenRepository)
                val summary = container.runHeadlessVerification()
                assertRemoteSummary(summary)

                container.navigateTo("containers").join()
                val loadMoreJob = container.controller.loadMore()
                assertNotNull(loadMoreJob).join()
                val afterLoadMore = assertIs<LayoutUiState.Ready>(container.controller.uiState.value)
                assertEquals("Loaded next page of containers", afterLoadMore.activeToast?.message)

                simulateOutage = true
                val offlineRef =
                    ActionRef(
                        id = "offline_action",
                        type = ActionTypes.CALL_API,
                        payload =
                            buildJsonObject {
                                put("endpoint", "/api/v1/actions/execute")
                                put("method", "POST")
                                put("body", buildJsonObject { put("queued", true) })
                            },
                    )
                container.controller.onAction(offlineRef).join()
                val queuedState = assertIs<LayoutUiState.Ready>(container.controller.uiState.value)
                assertEquals(1, queuedState.pendingOfflineActions)

                simulateOutage = false
                assertNotNull(container.flushOfflineActions()).join()
                val flushedState = assertIs<LayoutUiState.Ready>(container.controller.uiState.value)
                assertEquals(0, flushedState.pendingOfflineActions)
                assertEquals("Synced 1 offline action(s)", flushedState.activeToast?.message)
            } finally {
                container.close()
            }
        }

    private fun assertRemoteSummary(summary: HeadlessVerificationSummary) {
        assertEquals("home", summary.loadedPageId)
        assertEquals(8, summary.snippetCount)
        assertEquals("form-demo", summary.formSubmittedPageId)
        assertEquals("containers", summary.containersPageId)
        assertTrue(SnippetTypes.CAROUSEL in summary.containersSnippetTypes)
        assertTrue(SnippetTypes.GRID in summary.containersSnippetTypes)
        assertTrue(SnippetTypes.TABS in summary.containersSnippetTypes)
        assertTrue(SnippetTypes.ACCORDION in summary.containersSnippetTypes)
        assertTrue(SnippetTypes.FORM in summary.containersSnippetTypes)
        assertEquals("store-home", summary.storePageId)
        assertEquals("watch-status", summary.watchPageId)
        assertTrue(summary.celebrationTriggered)
        assertEquals("Workspace setup saved!", summary.toastMessage)
        assertTrue("screen_viewed" in summary.recordedEvents)
        assertTrue("workspace_form_submitted" in summary.recordedEvents)
        assertTrue("page_loaded" in summary.recordedImpressions)
        assertTrue("snippet_impression" in summary.recordedImpressions)
        assertTrue("full_name" in summary.sessionKeys)
        assertTrue("work_email" in summary.sessionKeys)
        assertTrue("compact_mode" in summary.sessionKeys)
        assertTrue("confirm_schema" in summary.sessionKeys)
        assertTrue("grid_density" in summary.sessionKeys)
    }

    @Test
    fun offlineSampleWiringAndCliParsingWorkEndToEnd() =
        runTest {
            val parsed = parseCliOptions(arrayOf("--headless", "--bff-url", "http://127.0.0.1:8080"))
            assertTrue(parsed.headless)
            assertEquals("http://127.0.0.1:8080", parsed.bffUrl)

            val container = SampleFactory.create(scope = this, bffBaseUrl = null)
            try {
                val summary = container.runHeadlessVerification()
                assertEquals("home", summary.loadedPageId)
                assertEquals("containers", summary.containersPageId)
                assertEquals("store-home", summary.storePageId)
                assertEquals("watch-status", summary.watchPageId)
                assertTrue(summary.celebrationTriggered)
                assertEquals("Workspace setup saved!", summary.toastMessage)
                assertTrue(SnippetTypes.CAROUSEL in summary.containersSnippetTypes)
                assertTrue(SnippetTypes.GRID in summary.containersSnippetTypes)
                assertTrue(SnippetTypes.TABS in summary.containersSnippetTypes)
                assertTrue(SnippetTypes.ACCORDION in summary.containersSnippetTypes)

                container.navigateTo("catalog").join()
                assertEquals("catalog", assertIs<LayoutUiState.Ready>(container.controller.uiState.value).page.pageId)

                container.navigateTo("profile").join()
                assertEquals("profile", assertIs<LayoutUiState.Ready>(container.controller.uiState.value).page.pageId)

                container.navigateTo("watch-status").join()
                val watchState = assertIs<LayoutUiState.Ready>(container.controller.uiState.value)
                assertEquals("watch-status", watchState.page.pageId)
                assertEquals(280, watchState.page.adaptiveLayout?.maxWidthDp)

                container.navigateTo("containers").join()
                val containersState = assertIs<LayoutUiState.Ready>(container.controller.uiState.value)
                assertEquals("containers", containersState.page.pageId)
                assertTrue(containersState.page.pagination?.hasMore == true)
                assertNotNull(containersState.page.pullToRefresh)
                assertNotNull(container.controller.loadMore()).join()
            } finally {
                container.close()
            }
        }

    @Test
    fun allDefaultScreensLoadIntoReadyState() =
        runTest {
            assertEquals(7, SampleContainer.DEFAULT_SCREENS.size)
            val container = SampleFactory.create(scope = this, bffBaseUrl = null)
            try {
                for (screenId in SampleContainer.DEFAULT_SCREENS) {
                    container.navigateTo(screenId).join()
                    val ready = assertIs<LayoutUiState.Ready>(container.controller.uiState.value)
                    assertEquals(screenId, ready.page.pageId)
                    assertTrue(ready.page.snippets.isNotEmpty(), "Expected snippets on screen '$screenId'")
                }
            } finally {
                container.close()
            }
        }
}
