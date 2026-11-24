package dev.abeta.kmp.layout.analytics

import dev.abeta.kmp.actions.codec.ActionDecoderRegistry
import dev.abeta.kmp.actions.codec.BuiltInActionDecoders
import dev.abeta.kmp.actions.handler.ActionOutcome
import dev.abeta.kmp.actions.handler.BuiltInActionHandlers
import dev.abeta.kmp.actions.handler.DefaultActionDispatcher
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.analytics.AnalyticsTracker
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.AnalyticsBatchRequest
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.TrackingContract
import dev.abeta.kmp.core.error.NetworkError
import dev.abeta.kmp.core.identity.SequentialUuidGenerator
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.storage.InMemoryKeyValueStore
import dev.abeta.kmp.core.time.MutableClock
import dev.abeta.kmp.layout.di.createSduiKoinModule
import dev.abeta.kmp.layout.engine.LayoutController
import dev.abeta.kmp.layout.platform.PlatformBridgeBundle
import dev.abeta.kmp.layout.repository.ScreenRepository
import dev.abeta.kmp.network.client.HttpMethodType
import dev.abeta.kmp.network.client.HttpRequestBody
import dev.abeta.kmp.network.client.HttpTransport
import dev.abeta.kmp.state.session.InMemorySessionStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.koin.dsl.koinApplication
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteBffAnalyticsTrackerTest {
    private class RecordingHttpTransport(
        var failNextRequest: Boolean = false,
    ) : HttpTransport {
        val paths = mutableListOf<String>()
        val methods = mutableListOf<HttpMethodType>()
        val encodedBodies = mutableListOf<String>()

        override suspend fun <T> request(
            method: HttpMethodType,
            path: String,
            deserializer: DeserializationStrategy<T>,
            headers: Map<String, String>,
            queryParams: Map<String, String>,
            body: HttpRequestBody?,
        ): AppResult<T> {
            methods.add(method)
            paths.add(path)
            val serializedBody =
                when (body) {
                    is HttpRequestBody.Serialized<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        val typed = body as HttpRequestBody.Serialized<Any?>
                        SduiJson.encodeToString(typed.serializer, typed.value)
                    }

                    is HttpRequestBody.JsonValue -> {
                        SduiJson.encodeToString(JsonElement.serializer(), body.element)
                    }

                    null -> {
                        ""
                    }
                }
            encodedBodies.add(serializedBody)
            if (failNextRequest) {
                failNextRequest = false
                return AppResult.Failure(NetworkError(statusCode = 503, message = "Analytics service unavailable"))
            }
            val rawResponse =
                """
                {
                  "success": true,
                  "data": {
                    "accepted": 4,
                    "dropped": 0,
                    "server_timestamp_epoch_ms": 1764000005000
                  }
                }
                """.trimIndent()
            return AppResult.Success(SduiJson.decodeFromString(deserializer, rawResponse))
        }
    }

    @Test
    fun batchesPageOpenSnippetNodeImpressionsAndInteractionClicksToBffEndpoint() =
        runTest(StandardTestDispatcher()) {
            val transport = RecordingHttpTransport()
            val tracker =
                RemoteBffAnalyticsTracker(
                    httpTransport = transport,
                    backingStore = InMemoryKeyValueStore(),
                    sessionId = "sess-express-1",
                    platform = "jvm",
                    appVersion = "1.5.0",
                    clock = MutableClock(1_764_000_000_000L),
                    uuidGenerator = SequentialUuidGenerator(prefix = "evt-"),
                    batchSize = 20,
                )
            tracker.identify(userId = "usr-42", traits = mapOf("tier" to "plus"))

            val snippet =
                SnippetEnvelope(
                    snippetId = "hero_deals",
                    snippetType = SnippetTypes.SPACER,
                    tracking = TrackingContract(eventName = "snippet_impression", properties = mapOf("slot" to "top")),
                )
            val controller = buildExpressController(tracker = tracker, snippet = snippet, scope = this)

            controller.loadScreen("express-home").join()
            controller.onSnippetImpression(snippet)
            controller.onNodeImpression(
                nodeKey = "express_card_avocado",
                tracking = TrackingContract(eventName = "node_impression", properties = mapOf("sku" to "avocado")),
            )
            controller.onAction(buildTrackedToastAction()).join()

            val flushed = assertIs<AppResult.Success<Int>>(tracker.flush())
            assertEquals(6, flushed.value)
            assertTrue(tracker.queuedEvents.value.isEmpty())
            assertEquals(listOf(HttpMethodType.POST), transport.methods)
            assertEquals(listOf("/api/v1/analytics/events"), transport.paths)

            val batch = SduiJson.decodeFromString(AnalyticsBatchRequest.serializer(), transport.encodedBodies.single())
            assertExpectedBatchEvents(batch)
        }

    private fun buildExpressController(
        tracker: RemoteBffAnalyticsTracker,
        snippet: SnippetEnvelope,
        scope: CoroutineScope,
    ): LayoutController {
        val page =
            PageResponse(
                pageId = "express-home",
                title = "Abeta Express Home",
                snippets = listOf(snippet),
            )
        val dispatcher =
            DefaultActionDispatcher(
                decoderRegistry = BuiltInActionDecoders.registerDefaults(ActionDecoderRegistry.builder()).build(),
                actionRegistry = BuiltInActionHandlers.createDefaultRegistry(),
            )
        return LayoutController(
            screenRepository = ScreenRepository { AppResult.Success(page) },
            actionDispatcher = dispatcher,
            sessionStore = InMemorySessionStore(),
            analyticsTracker = tracker,
            scope = scope,
        )
    }

    private fun buildTrackedToastAction(): ActionRef =
        ActionRef(
            id = "tap_add_avocado",
            type = ActionTypes.SHOW_TOAST,
            payload = buildJsonObject { put("message", "Added") },
            tracking =
                TrackingContract(
                    eventName = "express_add_tapped",
                    properties = mapOf("sku" to "avocado"),
                ),
        )

    private fun assertExpectedBatchEvents(batch: AnalyticsBatchRequest) {
        assertEquals("sess-express-1", batch.sessionId)
        assertEquals("usr-42", batch.userId)
        assertEquals("jvm", batch.platform)
        assertEquals("1.5.0", batch.appVersion)
        assertEquals(6, batch.events.size)

        val pageOpen = assertNotNull(batch.events.firstOrNull { it.eventName == "page_open" })
        assertEquals("page_open", pageOpen.category)
        assertEquals("express-home", pageOpen.pageId)
        assertEquals("1", pageOpen.properties["snippet_count"])

        val snippetImp = assertNotNull(batch.events.firstOrNull { it.eventName == "snippet_impression" })
        assertEquals("impression", snippetImp.category)
        assertEquals("hero_deals", snippetImp.snippetId)
        assertEquals("express-home", snippetImp.pageId)

        val nodeImp = assertNotNull(batch.events.firstOrNull { it.eventName == "node_impression" })
        assertEquals("impression", nodeImp.category)
        assertEquals("express_card_avocado", nodeImp.componentId)
        assertEquals("express-home", nodeImp.pageId)

        val clickEvent = assertNotNull(batch.events.firstOrNull { it.eventName == "interaction_click" })
        assertEquals("interaction", clickEvent.category)
        assertEquals("tap_add_avocado", clickEvent.actionId)
        assertEquals(ActionTypes.SHOW_TOAST, clickEvent.actionType)
        assertEquals("avocado", clickEvent.properties["sku"])

        val customClick = assertNotNull(batch.events.firstOrNull { it.eventName == "express_add_tapped" })
        assertEquals("interaction", customClick.category)
        assertEquals("tap_add_avocado", customClick.actionId)
    }

    @Test
    fun retainsQueuedEventsOnTransportFailureAndFlushesOnRetry() =
        runTest(StandardTestDispatcher()) {
            val transport = RecordingHttpTransport(failNextRequest = true)
            val tracker =
                RemoteBffAnalyticsTracker(
                    httpTransport = transport,
                    backingStore = InMemoryKeyValueStore(),
                    baseUrl = "https://bff.abeta.dev",
                    clock = MutableClock(1_764_000_000_000L),
                    uuidGenerator = SequentialUuidGenerator(),
                )

            tracker.trackScreen("express-catalog", mapOf("tab" to "fresh"))
            val failed = assertIs<AppResult.Failure>(tracker.flush())
            assertIs<NetworkError>(failed.error)
            assertEquals(1, tracker.queuedEvents.value.size)
            assertEquals("https://bff.abeta.dev/api/v1/analytics/events", transport.paths.first())

            val recovered = assertIs<AppResult.Success<Int>>(tracker.flush())
            assertEquals(1, recovered.value)
            assertTrue(tracker.queuedEvents.value.isEmpty())
        }

    @Test
    fun koinModuleWiresRemoteBffAnalyticsTrackerWhenEnabled() =
        runTest {
            var postedPath: String? = null
            val mockEngine =
                MockEngine { request ->
                    postedPath = request.url.encodedPath
                    respond(
                        content = """{"success":true,"data":{"accepted":1,"dropped":0}}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            val app =
                koinApplication {
                    modules(
                        createSduiKoinModule(
                            baseUrl = "https://bff.abeta.dev",
                            scope = this@runTest,
                            httpEngine = mockEngine,
                            keyValueStore = InMemoryKeyValueStore(),
                            platformBridges = PlatformBridgeBundle.createTestDefault(),
                            enableRemoteAnalytics = true,
                        ),
                    )
                }
            try {
                val tracker = assertIs<RemoteBffAnalyticsTracker>(app.koin.get<AnalyticsTracker>())
                tracker.trackScreen("express-home")
                val flushed = assertIs<AppResult.Success<Int>>(tracker.flush())
                assertEquals(1, flushed.value)
                assertEquals("/api/v1/analytics/events", postedPath)
            } finally {
                app.close()
            }
        }
}
