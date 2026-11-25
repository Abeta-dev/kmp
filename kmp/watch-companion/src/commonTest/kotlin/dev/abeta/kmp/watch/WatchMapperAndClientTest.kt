package dev.abeta.kmp.watch

import dev.abeta.kmp.actions.codec.BuiltInActionDecoders
import dev.abeta.kmp.actions.handler.ActionOutcome
import dev.abeta.kmp.actions.handler.ApiCallExecutor
import dev.abeta.kmp.actions.handler.BuiltInActionHandlers
import dev.abeta.kmp.actions.handler.DefaultActionDispatcher
import dev.abeta.kmp.actions.model.ActionContext
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.actions.model.CallApiPayload
import dev.abeta.kmp.contracts.AccordionSnippetData
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ActionRowSnippetData
import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.CardSnippetData
import dev.abeta.kmp.contracts.CarouselSnippetData
import dev.abeta.kmp.contracts.ComposedBlockSnippetData
import dev.abeta.kmp.contracts.ComposedNode
import dev.abeta.kmp.contracts.ComposedNodeKind
import dev.abeta.kmp.contracts.CountdownAtom
import dev.abeta.kmp.contracts.CustomSlotSnippetData
import dev.abeta.kmp.contracts.EmptyStateSnippetData
import dev.abeta.kmp.contracts.GraphCanvasSnippetData
import dev.abeta.kmp.contracts.GraphEdgeSpec
import dev.abeta.kmp.contracts.GraphNodeSpec
import dev.abeta.kmp.contracts.GridSnippetData
import dev.abeta.kmp.contracts.MarqueeItem
import dev.abeta.kmp.contracts.MarqueeStripSnippetData
import dev.abeta.kmp.contracts.MetricCardSnippetData
import dev.abeta.kmp.contracts.NudgeBarSnippetData
import dev.abeta.kmp.contracts.OrderTrackerSnippetData
import dev.abeta.kmp.contracts.OrderTrackerStep
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.StatTileAtom
import dev.abeta.kmp.contracts.TabItemSpec
import dev.abeta.kmp.contracts.TabsSnippetData
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.core.error.NotFoundError
import dev.abeta.kmp.core.error.OfflineError
import dev.abeta.kmp.core.error.UnexpectedError
import dev.abeta.kmp.core.platform.ExternalLauncherPort
import dev.abeta.kmp.core.platform.HapticStyle
import dev.abeta.kmp.core.platform.RecordingHapticEngine
import dev.abeta.kmp.core.platform.SharePort
import dev.abeta.kmp.core.platform.ShareRequest
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.storage.InMemoryKeyValueStore
import dev.abeta.kmp.network.client.ConditionalHttpResult
import dev.abeta.kmp.network.client.HttpClientFactory
import dev.abeta.kmp.network.client.HttpMethodType
import dev.abeta.kmp.network.client.HttpRequestBody
import dev.abeta.kmp.network.client.HttpTransport
import dev.abeta.kmp.network.client.KtorHttpTransport
import dev.abeta.kmp.network.config.NetworkConfig
import dev.abeta.kmp.network.config.RetryPolicy
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WatchMapperAndClientTest {
    @Test
    fun mapperTraversesContainersCustomSlotAndMapsAllWearableSnippets() {
        val mapped = WatchScreenMapper().map(RichWatchPageFixtures.build())
        assertEquals("rich-watch", mapped.pageId)
        assertEquals(2, mapped.metrics.size)
        assertTrue(mapped.metrics.any { it.id == "grid_metric" && it.value == "98%" })
        assertTrue(mapped.metrics.any { it.id == "stat_savings" && it.value == "\$14.50" })

        val cardById = mapped.cards.associateBy { it.id }
        assertNotNull(cardById["carousel_card"])
        assertNotNull(cardById["accordion_card"])
        assertNotNull(cardById["slot_fallback_card"])
        assertNull(cardById["tab_row"]?.primaryActionLabel)
        assertNotNull(cardById["tab_row"]?.primaryAction)
        assertMappedNewSnippets(cardById)
    }

    private fun assertMappedNewSnippets(cardById: Map<String, WatchActionCard>) {
        val tracker = assertNotNull(cardById["live_tracker"])
        assertEquals("Arriving soon", tracker.title)
        val trackerSub = assertNotNull(tracker.subtitle)
        assertTrue("Rider En Route" in trackerSub)
        assertTrue("9m" in trackerSub)
        assertEquals("Call", tracker.primaryActionLabel)
        assertEquals("Help", tracker.secondaryActionLabel)

        assertEquals("Claim", assertNotNull(cardById["flash_nudge"]).primaryActionLabel)
        assertTrue(assertNotNull(cardById["perks_marquee"]).subtitle?.contains("5% cashback") == true)
        assertEquals("Redeem", assertNotNull(cardById["composed_loyalty"]).primaryActionLabel)
        assertTrue(assertNotNull(cardById["graph_overview"]).subtitle?.contains("2 nodes · 1 edges") == true)
        assertEquals("Refresh", assertNotNull(cardById["empty_inbox"]).primaryActionLabel)
    }

    @Test
    fun remoteWatchPageSourceSupportsEtag304OfflineFallbackAndBffErrorCodes() =
        runTest {
            var requestCount = 0
            var failOffline = false
            val engine =
                buildEtagMockEngine(
                    onRequest = { requestCount += 1 },
                    isOffline = { failOffline },
                )
            val client =
                HttpClientFactory.create(
                    config = NetworkConfig(baseUrl = "https://bff.abeta.dev", retry = RetryPolicy(maxRetries = 0)),
                    json = SduiJson,
                    engine = engine,
                )
            try {
                val source =
                    RemoteWatchPageSource(
                        httpTransport = KtorHttpTransport(httpClient = client, json = SduiJson),
                        cacheStore = InMemoryKeyValueStore(),
                    )
                val first = assertIs<AppResult.Success<PageResponse>>(source.fetchPage("watch-status"))
                assertEquals("\"watch-v1\"", first.value.etag)

                val second = assertIs<AppResult.Success<PageResponse>>(source.fetchPage("watch-status"))
                assertEquals("Cached Watch", second.value.title)
                assertEquals(2, requestCount)

                failOffline = true
                val offline = assertIs<AppResult.Success<PageResponse>>(source.fetchPage("watch-status"))
                assertEquals("Cached Watch", offline.value.title)

                val notFound = assertIs<AppResult.Failure>(source.fetchPage("missing"))
                assertIs<NotFoundError>(notFound.error)
            } finally {
                client.close()
            }
        }

    @Test
    fun watchRemoteApiExecutorOmitsGetBodyAndDecodesJsonArray() =
        runTest {
            var observedMethod: HttpMethodType? = null
            var observedBody: HttpRequestBody? = null
            val fakeTransport =
                object : HttpTransport {
                    override suspend fun <T> request(
                        method: HttpMethodType,
                        path: String,
                        deserializer: DeserializationStrategy<T>,
                        headers: Map<String, String>,
                        queryParams: Map<String, String>,
                        body: HttpRequestBody?,
                    ): AppResult<T> {
                        observedMethod = method
                        observedBody = body
                        return AppResult.Success(SduiJson.decodeFromString(deserializer, """["ok","cached"]"""))
                    }

                    override suspend fun <T> requestConditional(
                        path: String,
                        deserializer: DeserializationStrategy<T>,
                        ifNoneMatch: String?,
                        headers: Map<String, String>,
                        queryParams: Map<String, String>,
                    ): AppResult<ConditionalHttpResult<T>> = AppResult.Failure(OfflineError())
                }
            val result =
                WatchRemoteApiCallExecutor(httpTransport = fakeTransport).execute(
                    payload =
                        CallApiPayload(
                            endpoint = "/api/v1/status",
                            method = "GET",
                            body = buildJsonObject { put("ignored", true) },
                        ),
                    context = ActionContext(),
                )
            val success = assertIs<AppResult.Success<ActionOutcome.ApiCallResult>>(result)
            assertEquals(HttpMethodType.GET, observedMethod)
            assertNull(observedBody)
            assertIs<JsonArray>(success.value.responseBody)
        }

    @Test
    fun staleLoadDoesNotOverwriteNewerScreen() =
        runTest(StandardTestDispatcher()) {
            val slowGate = CompletableDeferred<PageResponse>()
            val source =
                WatchPageSource { screenId ->
                    if (screenId == "slow-screen") {
                        AppResult.Success(slowGate.await())
                    } else {
                        AppResult.Success(PageResponse(pageId = "fast-screen", title = "Fast Screen"))
                    }
                }
            val controller =
                WatchCompanionController(
                    pageSource = source,
                    actionDispatcher = createWatchDispatcher(offlineWatchStarterApiExecutor()),
                    scope = this,
                )

            val slowJob = controller.loadScreen("slow-screen")
            val fastJob = controller.loadScreen("fast-screen")
            fastJob.join()
            slowGate.complete(PageResponse(pageId = "slow-screen", title = "Stale Slow Screen"))
            slowJob.join()
            advanceUntilIdle()

            val ready = assertIs<WatchUiState.Ready>(controller.uiState.value)
            assertEquals("fast-screen", ready.page.pageId)
            assertEquals("Fast Screen", ready.page.title)
        }

    @Test
    fun bridgeFailuresAndUnsuccessfulApiCallsSurfaceErrorToastAndFallbackRoute() =
        runTest(StandardTestDispatcher()) {
            val haptics = RecordingHapticEngine()
            val controller = createFailingBridgesController(scope = this, haptics = haptics)
            controller.loadScreen("watch-status").join()

            val openFail =
                ActionRef(
                    id = "open_fail",
                    type = ActionTypes.OPEN_URL,
                    payload = buildJsonObject { put("url", "https://abeta.dev") },
                )
            controller.onAction(openFail).join()
            assertEquals("Browser unavailable", assertIs<WatchUiState.Ready>(controller.uiState.value).activeToast)
            assertTrue(HapticStyle.ERROR in haptics.performed)

            controller.dismissToast()
            val cleared = assertIs<WatchUiState.Ready>(controller.uiState.value)
            assertNull(cleared.activeToast)
            assertNull(cleared.page.lastFeedbackMessage)

            val shareFail =
                ActionRef(
                    id = "share_fail",
                    type = ActionTypes.SHARE,
                    payload = buildJsonObject { put("text", "Hello") },
                )
            controller.onAction(shareFail).join()
            assertEquals("Share sheet blocked", assertIs<WatchUiState.Ready>(controller.uiState.value).activeToast)

            val apiFail =
                ActionRef(
                    id = "api_fail",
                    type = ActionTypes.CALL_API,
                    payload = buildJsonObject { put("endpoint", "/api/v1/orders/express") },
                )
            controller.onAction(apiFail).join()
            assertEquals("Order rejected", assertIs<WatchUiState.Ready>(controller.uiState.value).activeToast)

            val openFallback =
                ActionRef(
                    id = "open_fallback",
                    type = ActionTypes.OPEN_URL,
                    payload =
                        buildJsonObject {
                            put("url", "https://abeta.dev")
                            put("fallback_route", "/screens/fallback-screen")
                        },
                )
            controller.onAction(openFallback).join()
            advanceUntilIdle()
            assertEquals("fallback-screen", assertIs<WatchUiState.Ready>(controller.uiState.value).page.pageId)
        }

    @Test
    fun starterDefaultsHandleSpecialCharactersInScreenIdSafely() =
        runTest {
            val source = WatchOfflineStarterDefaults.createStarterPageSource()
            val trickyId = "watch\"quoted\\screen"
            val result = assertIs<AppResult.Success<PageResponse>>(source.fetchPage(trickyId))
            assertEquals(trickyId, result.value.pageId)
            assertEquals(2, result.value.snippets.size)
        }

    private fun createFailingBridgesController(
        scope: kotlinx.coroutines.CoroutineScope,
        haptics: RecordingHapticEngine,
    ): WatchCompanionController {
        val failingLauncher =
            object : ExternalLauncherPort {
                override fun canOpenUrl(url: String): Boolean = false

                override suspend fun openUrl(url: String): AppResult<Unit> =
                    AppResult.Failure(UnexpectedError(message = "Browser unavailable"))
            }
        val failingShare =
            object : SharePort {
                override suspend fun share(request: ShareRequest): AppResult<Unit> =
                    AppResult.Failure(UnexpectedError(message = "Share sheet blocked"))
            }
        val failingApi =
            ApiCallExecutor { payload, _ ->
                AppResult.Success(
                    ActionOutcome.ApiCallResult(
                        endpoint = payload.endpoint,
                        method = payload.method,
                        isSuccess = false,
                        errorMessage = "Order rejected",
                        followUpActions = emptyList(),
                    ),
                )
            }
        return WatchCompanionController(
            pageSource = WatchPageSource { id -> AppResult.Success(PageResponse(pageId = id, title = "Screen $id")) },
            actionDispatcher = createWatchDispatcher(failingApi),
            scope = scope,
            hapticEngine = haptics,
            externalLauncher = failingLauncher,
            sharePort = failingShare,
        )
    }

    private fun buildEtagMockEngine(
        onRequest: () -> Unit,
        isOffline: () -> Boolean,
    ): MockEngine =
        MockEngine { request ->
            onRequest()
            val jsonHeaders = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            when {
                request.url.encodedPath == "/api/v1/screens/missing" -> {
                    respond(
                        content = """{"success":false,"error":{"code":"NOT_FOUND","message":"Screen absent"}}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders,
                    )
                }

                isOffline() -> {
                    respond(
                        content = """{"success":false,"error":{"code":"UNAVAILABLE","message":"Down"}}""",
                        status = HttpStatusCode.ServiceUnavailable,
                        headers = jsonHeaders,
                    )
                }

                request.headers[HttpHeaders.IfNoneMatch] == "\"watch-v1\"" -> {
                    respond(
                        content = "",
                        status = HttpStatusCode.NotModified,
                        headers = headersOf(HttpHeaders.ETag, "\"watch-v1\""),
                    )
                }

                else -> {
                    respond(
                        content = """{"success":true,"data":{"page_id":"watch-status","title":"Cached Watch"}}""",
                        status = HttpStatusCode.OK,
                        headers =
                            headersOf(
                                HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                                HttpHeaders.ETag to listOf("\"watch-v1\""),
                            ),
                    )
                }
            }
        }

    private fun createWatchDispatcher(apiExecutor: ApiCallExecutor): DefaultActionDispatcher =
        DefaultActionDispatcher(
            decoderRegistry = BuiltInActionDecoders.createDefaultRegistry(),
            actionRegistry = BuiltInActionHandlers.createDefaultRegistry(apiExecutor),
        )
}

private object RichWatchPageFixtures {
    fun build(): PageResponse {
        val action = ActionRef(id = "tap", type = ActionTypes.SHOW_TOAST)
        val snippets =
            buildContainerSnippets(action) +
                listOf(
                    buildOrderTracker(action),
                    buildNudge(action),
                    buildMarquee(action),
                    buildComposedLoyalty(action),
                    buildGraphCanvas(),
                    buildEmptyState(action),
                )
        return PageResponse(pageId = "rich-watch", title = "Rich Watch", snippets = snippets)
    }

    private fun buildContainerSnippets(action: ActionRef): List<SnippetEnvelope> {
        val carouselCard = cardEnvelope("carousel_card", "Deal")
        val accordionCard = cardEnvelope("accordion_card", "Nested")
        val fallbackCard = cardEnvelope("slot_fallback_card", "Fallback")
        val metric =
            envelope(
                "grid_metric",
                SnippetTypes.METRIC_CARD,
                MetricCardSnippetData(label = TextAtom(text = "SLA"), value = TextAtom(text = "98%")),
            )
        val tabRow =
            envelope(
                "tab_row",
                SnippetTypes.ACTION_ROW,
                ActionRowSnippetData(title = TextAtom(text = "Row"), onClick = action),
            )
        return listOf(
            envelope("carousel_1", SnippetTypes.CAROUSEL, CarouselSnippetData(items = listOf(carouselCard))),
            envelope("grid_1", SnippetTypes.GRID, GridSnippetData(items = listOf(metric))),
            envelope(
                "tabs_1",
                SnippetTypes.TABS,
                TabsSnippetData(tabs = listOf(TabItemSpec(id = "t1", title = "Main", snippets = listOf(tabRow)))),
            ),
            envelope(
                "acc_1",
                SnippetTypes.ACCORDION,
                AccordionSnippetData(title = TextAtom(text = "More"), snippets = listOf(accordionCard)),
            ),
            envelope(
                "slot_1",
                SnippetTypes.CUSTOM_SLOT,
                CustomSlotSnippetData(slotId = "s1", slotType = "host_map", fallback = fallbackCard),
            ),
        )
    }

    private fun buildOrderTracker(action: ActionRef): SnippetEnvelope =
        envelope(
            "live_tracker",
            SnippetTypes.ORDER_TRACKER,
            OrderTrackerSnippetData(
                trackerId = "tr1",
                orderCode = "#BTS-4091",
                headline = TextAtom(text = "Arriving soon"),
                etaCountdown = CountdownAtom(countdownId = "cd1", remainingSeconds = 540),
                steps =
                    listOf(
                        OrderTrackerStep(id = "s1", label = "Packed", completed = true),
                        OrderTrackerStep(id = "s2", label = "Rider En Route", active = true),
                    ),
                primaryAction = ButtonAtom(label = "Call", onClick = action),
                secondaryAction = ButtonAtom(label = "Help", onClick = action),
            ),
        )

    private fun buildNudge(action: ActionRef): SnippetEnvelope =
        envelope(
            "flash_nudge",
            SnippetTypes.NUDGE_BAR,
            NudgeBarSnippetData(
                nudgeId = "n1",
                title = TextAtom(text = "Flash Drop"),
                subtitle = TextAtom(text = "Limited stock"),
                badge = TagAtom(label = "HOT"),
                cta = ButtonAtom(label = "Claim", onClick = action),
            ),
        )

    private fun buildMarquee(action: ActionRef): SnippetEnvelope =
        envelope(
            "perks_marquee",
            SnippetTypes.MARQUEE_STRIP,
            MarqueeStripSnippetData(
                stripId = "m1",
                items =
                    listOf(
                        MarqueeItem(id = "i1", text = "Free delivery", onClick = action),
                        MarqueeItem(id = "i2", text = "5% cashback"),
                    ),
            ),
        )

    private fun buildComposedLoyalty(action: ActionRef): SnippetEnvelope =
        envelope(
            "composed_loyalty",
            SnippetTypes.COMPOSED_BLOCK,
            ComposedBlockSnippetData(
                blockId = "b1",
                recipeName = "loyalty_card",
                root =
                    ComposedNode(
                        kind = ComposedNodeKind.COLUMN,
                        children =
                            listOf(
                                ComposedNode(
                                    nodeId = "stat_savings",
                                    kind = ComposedNodeKind.STAT_TILE,
                                    statTile = StatTileAtom(label = "Saved", value = "\$14.50"),
                                ),
                                ComposedNode(kind = ComposedNodeKind.TEXT, text = TextAtom(text = "6 of 8 Stamps")),
                                ComposedNode(
                                    kind = ComposedNodeKind.BUTTON,
                                    button = ButtonAtom(label = "Redeem", onClick = action),
                                ),
                            ),
                    ),
            ),
        )

    private fun buildGraphCanvas(): SnippetEnvelope =
        envelope(
            "graph_overview",
            SnippetTypes.GRAPH_CANVAS,
            GraphCanvasSnippetData(
                canvasId = "g1",
                title = TextAtom(text = "Team Map"),
                nodes =
                    listOf(
                        GraphNodeSpec(nodeId = "n1", label = "A", x = 0f, y = 0f),
                        GraphNodeSpec(nodeId = "n2", label = "B", x = 10f, y = 10f),
                    ),
                edges = listOf(GraphEdgeSpec(edgeId = "e1", fromNodeId = "n1", toNodeId = "n2")),
            ),
        )

    private fun buildEmptyState(action: ActionRef): SnippetEnvelope =
        envelope(
            "empty_inbox",
            SnippetTypes.EMPTY_STATE,
            EmptyStateSnippetData(
                title = TextAtom(text = "All Caught Up"),
                message = TextAtom(text = "No alerts"),
                actionButton = ButtonAtom(label = "Refresh", onClick = action),
            ),
        )

    private fun cardEnvelope(
        id: String,
        title: String,
    ): SnippetEnvelope = envelope(id, SnippetTypes.CARD, CardSnippetData(title = TextAtom(text = title)))

    private inline fun <reified T> envelope(
        id: String,
        type: String,
        payload: T,
    ): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = id,
            snippetType = type,
            data = SduiJson.encodeToJsonElement(kotlinx.serialization.serializer<T>(), payload) as JsonObject,
        )
}
