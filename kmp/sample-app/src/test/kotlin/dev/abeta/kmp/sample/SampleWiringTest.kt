package dev.abeta.kmp.sample

import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ActionRowSnippetData
import dev.abeta.kmp.contracts.ComposedBlockSnippetData
import dev.abeta.kmp.contracts.FeedbackEffect
import dev.abeta.kmp.contracts.FormSnippetData
import dev.abeta.kmp.contracts.MarqueeStripSnippetData
import dev.abeta.kmp.contracts.MetricCardSnippetData
import dev.abeta.kmp.contracts.MotionLoopEffect
import dev.abeta.kmp.contracts.NudgeBarSnippetData
import dev.abeta.kmp.contracts.OrderTrackerSnippetData
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.security.BiometricResult
import dev.abeta.kmp.layout.engine.LayoutUiState
import dev.abeta.kmp.layout.platform.PlatformBridgeBundle
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

    @Test
    fun allSevenVerticalShowcaseAppsAndDetailScreensLoadOffline() =
        runTest {
            assertEquals(7, SampleContainer.VERTICAL_APPS.size)
            assertEquals(
                listOf("fresh-home", "bites-home", "pulse-home", "mart-home", "spark-deck", "trade-home", "pay-home"),
                SampleContainer.VERTICAL_SCREENS,
            )
            val allVerticalScreens =
                listOf(
                    "fresh-home",
                    "bites-home",
                    "bites-restaurant",
                    "bites-order-track",
                    "pulse-home",
                    "pulse-event-detail",
                    "mart-home",
                    "mart-product-detail",
                    "spark-deck",
                    "spark-matches",
                    "trade-home",
                    "trade-supplier",
                    "pay-home",
                    "pay-transfer",
                    "pay-cards",
                    "pay-receipt",
                )
            val container = SampleFactory.create(scope = this, bffBaseUrl = null)
            try {
                for (screenId in allVerticalScreens) {
                    container.navigateTo(screenId).join()
                    val ready = assertIs<LayoutUiState.Ready>(container.controller.uiState.value)
                    assertEquals(screenId, ready.page.pageId)
                    assertTrue(ready.page.snippets.isNotEmpty(), "Expected snippets on '$screenId'")
                    assertNotNull(SampleShowcaseApps.findAppForScreen(screenId))
                }
            } finally {
                container.close()
            }
        }

    @Test
    fun runVerticalsHeadlessVerificationAndCliFlagsSucceed() =
        runTest {
            val cli =
                parseCliOptions(
                    arrayOf(
                        "--headless",
                        "--verticals",
                        "--capture-verticals=/tmp/verticals",
                        "--bff-url=http://127.0.0.1:8080",
                    ),
                )
            assertTrue(cli.headless)
            assertTrue(cli.verticals)
            assertEquals("/tmp/verticals", cli.captureVerticalsDir)
            assertEquals("http://127.0.0.1:8080", cli.bffUrl)

            val container = SampleFactory.create(scope = this, bffBaseUrl = null)
            try {
                val summary = container.runVerticalsHeadlessVerification()
                assertEquals(7, summary.loadedVerticals.size)
                assertTrue(summary.loadedVerticals.values.all { it > 0 })
                assertTrue(summary.expressOrderCelebrationTriggered)
                assertTrue(summary.swipeCelebrationTriggered)
                assertTrue(summary.bookingCelebrationTriggered)
                assertTrue(summary.rfqSubmitted)
                assertTrue(summary.payTransferCelebrationTriggered)

                container.navigateTo("bites-restaurant").join()
                val bitesOrder =
                    ActionRef(
                        id = "test_bites_order",
                        type = ActionTypes.CALL_API,
                        payload = buildJsonObject { put("endpoint", "/api/v1/bites/orders") },
                    )
                container.controller.onAction(bitesOrder).join()
                assertEquals("bites-order-track", container.currentScreenId.value)

                container.navigateTo("mart-product-detail").join()
                val martOrder =
                    ActionRef(
                        id = "test_mart_order",
                        type = ActionTypes.CALL_API,
                        payload = buildJsonObject { put("endpoint", "/api/v1/mart/orders") },
                    )
                container.controller.onAction(martOrder).join()
                val martReady = assertIs<LayoutUiState.Ready>(container.controller.uiState.value)
                assertEquals(FeedbackEffect.CELEBRATION, martReady.activeFeedback?.effect)
            } finally {
                container.close()
            }
        }

    @Test
    fun offlineScreenRepositoryStripsQueryStringsGracefully() =
        runTest {
            val repo = OfflineScreenRepository()
            val freshResult =
                assertIs<AppResult.Success<PageResponse>>(repo.fetchScreen("fresh-home?q=avocados&filter=produce"))
            assertEquals("fresh-home", freshResult.value.pageId)

            val bitesResult =
                assertIs<AppResult.Success<PageResponse>>(repo.fetchScreen("/screens/bites-home?filter=biryani"))
            assertEquals("bites-home", bitesResult.value.pageId)

            val tradeResult = assertIs<AppResult.Success<PageResponse>>(repo.fetchScreen("trade-home?q=cartons"))
            assertEquals("trade-home", tradeResult.value.pageId)

            val payResult = assertIs<AppResult.Success<PageResponse>>(repo.fetchScreen("pay-home?filter=bills"))
            assertEquals("pay-home", payResult.value.pageId)
        }

    @Test
    fun wave6ExpressCommercePrimitivesPopulateFreshAndStoreHome() =
        runTest {
            val pages = SampleScreens.allPages()
            for (screenId in listOf("fresh-home", "store-home")) {
                val page = assertNotNull(pages[screenId], "Missing screen $screenId")
                val types = page.snippets.map { it.snippetType }.toSet()
                assertTrue(SnippetTypes.MARQUEE_STRIP in types, "Expected marquee_strip on $screenId")
                assertTrue(SnippetTypes.NUDGE_BAR in types, "Expected nudge_bar on $screenId")
                assertTrue(SnippetTypes.COMPOSED_BLOCK in types, "Expected composed_block on $screenId")

                val marquee =
                    SduiJson.decodeFromJsonElement(
                        MarqueeStripSnippetData.serializer(),
                        page.snippets.first { it.snippetType == SnippetTypes.MARQUEE_STRIP }.data,
                    )
                assertEquals(3, marquee.items.size)

                val nudge =
                    SduiJson.decodeFromJsonElement(
                        NudgeBarSnippetData.serializer(),
                        page.snippets.first { it.snippetType == SnippetTypes.NUDGE_BAR }.data,
                    )
                assertEquals(420, nudge.countdown?.remainingSeconds)
                assertEquals(MotionLoopEffect.WIGGLE, nudge.badge?.motion?.loopEffect)

                val composed =
                    SduiJson.decodeFromJsonElement(
                        ComposedBlockSnippetData.serializer(),
                        page.snippets.first { it.snippetType == SnippetTypes.COMPOSED_BLOCK }.data,
                    )
                assertEquals("loyalty_stamp_card", composed.recipeName)

                val cart = assertNotNull(page.floatingCart)
                assertEquals(1500, cart.freeDeliveryThresholdCents)
                assertEquals("Add", cart.nudgePrefix)
                assertEquals("FREE 8-min delivery unlocked!", cart.unlockedLabel)
            }
        }

    @Test
    fun wave6OrderTrackerNudgesAndMoldedBlocksPopulateVerticalScreens() =
        runTest {
            val pages = SampleScreens.allPages()
            val bitesTrack = assertNotNull(pages["bites-order-track"])
            val trackerEnvelope = bitesTrack.snippets.first { it.snippetType == SnippetTypes.ORDER_TRACKER }
            val tracker = SduiJson.decodeFromJsonElement(OrderTrackerSnippetData.serializer(), trackerEnvelope.data)
            assertEquals("#BTS-4091", tracker.orderCode)
            assertEquals(660, tracker.etaCountdown?.remainingSeconds)
            assertEquals(4, tracker.steps.size)
            assertTrue(tracker.steps.any { it.label == "Rider En Route" && it.active })
            assertNotNull(tracker.riderName)
            assertNotNull(tracker.primaryAction)
            assertNotNull(tracker.secondaryAction)

            val martTypes = assertNotNull(pages["mart-home"]).snippets.map { it.snippetType }.toSet()
            assertTrue(SnippetTypes.MARQUEE_STRIP in martTypes)

            val pulsePage = assertNotNull(pages["pulse-home"])
            val pulseNudge =
                SduiJson.decodeFromJsonElement(
                    NudgeBarSnippetData.serializer(),
                    pulsePage.snippets.first { it.snippetType == SnippetTypes.NUDGE_BAR }.data,
                )
            assertNotNull(pulseNudge.countdown)

            val sparkPage = assertNotNull(pages["spark-deck"])
            val sparkNudge =
                SduiJson.decodeFromJsonElement(
                    NudgeBarSnippetData.serializer(),
                    sparkPage.snippets.first { it.snippetType == SnippetTypes.NUDGE_BAR }.data,
                )
            assertEquals(MotionLoopEffect.HEARTBEAT, sparkNudge.badge?.motion?.loopEffect)

            val tradePage = assertNotNull(pages["trade-home"])
            val tradeBlock =
                SduiJson.decodeFromJsonElement(
                    ComposedBlockSnippetData.serializer(),
                    tradePage.snippets.first { it.snippetType == SnippetTypes.COMPOSED_BLOCK }.data,
                )
            assertEquals("moq_bulk_savings_card", tradeBlock.recipeName)
        }

    @Test
    fun abetaPayHomeAndCardsScreensVerifyContracts() =
        runTest {
            val pages = SampleScreens.allPages()
            val payHome = assertNotNull(pages["pay-home"])
            val payHomeTypes = payHome.snippets.map { it.snippetType }.toSet()
            assertTrue(SnippetTypes.MARQUEE_STRIP in payHomeTypes)
            assertTrue(SnippetTypes.NUDGE_BAR in payHomeTypes)
            assertTrue(SnippetTypes.COMPOSED_BLOCK in payHomeTypes)
            assertTrue(SnippetTypes.CAROUSEL in payHomeTypes)

            val balance =
                SduiJson.decodeFromJsonElement(
                    MetricCardSnippetData.serializer(),
                    payHome.snippets.first { it.snippetType == SnippetTypes.METRIC_CARD }.data,
                )
            assertEquals("\$2,480.50", balance.value.text)

            val cashbackNudge =
                SduiJson.decodeFromJsonElement(
                    NudgeBarSnippetData.serializer(),
                    payHome.snippets.first { it.snippetType == SnippetTypes.NUDGE_BAR }.data,
                )
            assertEquals(480, cashbackNudge.countdown?.remainingSeconds)

            val metalCard =
                SduiJson.decodeFromJsonElement(
                    ComposedBlockSnippetData.serializer(),
                    payHome.snippets.first { it.snippetType == SnippetTypes.COMPOSED_BLOCK }.data,
                )
            assertEquals("metal_virtual_card", metalCard.recipeName)

            val payCards = assertNotNull(pages["pay-cards"])
            val cardControls =
                SduiJson.decodeFromJsonElement(
                    FormSnippetData.serializer(),
                    payCards.snippets.first { it.snippetType == SnippetTypes.FORM }.data,
                )
            assertEquals(
                listOf("card_freeze", "card_international", "card_contactless"),
                cardControls.switches.map { it.fieldId },
            )
            assertEquals(1, cardControls.sliders.size)
            assertEquals("card_spend_limit", cardControls.sliders.first().fieldId)
        }

    @Test
    fun abetaPayTransferBiometricAndReceiptFlowWorkEndToEnd() =
        runTest {
            val pages = SampleScreens.allPages()
            val payReceipt = assertNotNull(pages["pay-receipt"])
            val settlement =
                SduiJson.decodeFromJsonElement(
                    OrderTrackerSnippetData.serializer(),
                    payReceipt.snippets.first { it.snippetType == SnippetTypes.ORDER_TRACKER }.data,
                )
            assertEquals("#PAY-3081", settlement.orderCode)
            assertEquals(4, settlement.steps.size)
            assertEquals(ActionTypes.SHARE, settlement.primaryAction?.onClick?.type)

            val payTransfer = assertNotNull(pages["pay-transfer"])
            val bioRow =
                SduiJson.decodeFromJsonElement(
                    ActionRowSnippetData.serializer(),
                    payTransfer.snippets.first { it.snippetId == "pay_biometric_auth_row" }.data,
                )
            val bioAction = assertNotNull(bioRow.onClick)
            assertEquals(ActionTypes.BIOMETRIC_AUTH, bioAction.type)

            val bridges = PlatformBridgeBundle.createTestDefault(biometricResult = BiometricResult.Authenticated)
            val container = SampleFactory.create(scope = this, bffBaseUrl = null, platformBridges = bridges)
            try {
                container.navigateTo("pay-transfer").join()
                container.controller.onAction(bioAction).join()
                assertEquals("pay-receipt", container.currentScreenId.value)
            } finally {
                container.close()
            }
        }
}
