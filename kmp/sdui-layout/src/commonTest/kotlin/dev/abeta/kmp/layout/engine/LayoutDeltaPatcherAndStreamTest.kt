package dev.abeta.kmp.layout.engine

import dev.abeta.kmp.actions.codec.BuiltInActionDecoders
import dev.abeta.kmp.actions.handler.ActionOutcome
import dev.abeta.kmp.actions.handler.BuiltInActionHandlers
import dev.abeta.kmp.actions.handler.DefaultActionDispatcher
import dev.abeta.kmp.analytics.RecordingAnalyticsTracker
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetDeltaOp
import dev.abeta.kmp.contracts.SnippetDeltaPatch
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.SseStreamSpec
import dev.abeta.kmp.core.error.NotFoundError
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.layout.repository.ScreenRepository
import dev.abeta.kmp.network.sse.SseEvent
import dev.abeta.kmp.network.sse.SseReconnectPolicy
import dev.abeta.kmp.network.sse.SseTransport
import dev.abeta.kmp.state.session.InMemorySessionStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LayoutDeltaPatcherAndStreamTest {
    @Test
    fun appendTextSupportsExplicitNestedDotPathAndDefaultSmartFallback() {
        val page = sampleAppendPage()
        val patches =
            listOf(
                SnippetDeltaPatch(SnippetDeltaOp.APPEND_TEXT, "reply_1", "body.text", ", world"),
                SnippetDeltaPatch(SnippetDeltaOp.APPEND_TEXT, "reply_1", textChunk = "!"),
                SnippetDeltaPatch(SnippetDeltaOp.APPEND_TEXT, "msg_1", textChunk = " -> Done"),
                SnippetDeltaPatch(SnippetDeltaOp.APPEND_TEXT, "hdr_1", textChunk = " Stream"),
                SnippetDeltaPatch(SnippetDeltaOp.APPEND_TEXT, "reply_1", "meta.author.name", "Abeta"),
            )
        val result = patches.fold(page, ::applySnippetDeltaPatch)

        assertEquals(
            "Hello",
            page.snippets[0]
                .data["body"]!!
                .jsonObject["text"]!!
                .jsonPrimitive.content,
        )
        assertEquals(
            "Hello, world!",
            result.snippets[0]
                .data["body"]!!
                .jsonObject["text"]!!
                .jsonPrimitive.content,
        )
        assertEquals(
            "Abeta",
            result.snippets[0]
                .data["meta"]!!
                .jsonObject["author"]!!
                .jsonObject["name"]!!
                .jsonPrimitive.content,
        )
        assertEquals(
            "Step 1 -> Done",
            result.snippets[1]
                .data["message"]!!
                .jsonObject["text"]!!
                .jsonPrimitive.content,
        )
        assertEquals(
            "Live Stream",
            result.snippets[2]
                .data["title"]!!
                .jsonObject["text"]!!
                .jsonPrimitive.content,
        )
    }

    @Test
    fun patchSnippetDataDeepMergesNestedObjectsAndReplacesPrimitives() {
        val page =
            PageResponse(
                pageId = "order",
                snippets =
                    listOf(
                        SnippetEnvelope(
                            snippetId = "tracker_1",
                            snippetType = SnippetTypes.CARD,
                            data =
                                buildJsonObject {
                                    putJsonObject("title") {
                                        put("text", "Preparing")
                                        put("color", "primary")
                                    }
                                    put("eta_minutes", 12)
                                    put("badge", "NEW")
                                },
                        ),
                    ),
            )

        val updated =
            applySnippetDeltaPatch(
                page,
                SnippetDeltaPatch(
                    op = SnippetDeltaOp.PATCH_SNIPPET_DATA,
                    snippetId = "tracker_1",
                    dataPatch =
                        buildJsonObject {
                            putJsonObject("title") { put("text", "Out for delivery") }
                            put("eta_minutes", 4)
                            put("courier", "Alex")
                        },
                ),
            )

        val data = updated.snippets.single().data
        assertEquals("Out for delivery", data["title"]!!.jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("primary", data["title"]!!.jsonObject["color"]!!.jsonPrimitive.content)
        assertEquals("4", data["eta_minutes"]!!.jsonPrimitive.content)
        assertEquals("NEW", data["badge"]!!.jsonPrimitive.content)
        assertEquals("Alex", data["courier"]!!.jsonPrimitive.content)

        // RFC 7396 merge patch removes keys set to JsonNull.
        val afterRemoval =
            applySnippetDeltaPatch(
                updated,
                SnippetDeltaPatch(
                    op = SnippetDeltaOp.PATCH_SNIPPET_DATA,
                    snippetId = "tracker_1",
                    dataPatch = buildJsonObject { put("badge", JsonNull) },
                ),
            )
        assertFalse("badge" in afterRemoval.snippets.single().data)
    }

    @Test
    fun patchesRecurseIntoContainersSupportDescriptionFallbackAndArrayIndexPaths() {
        val page = sampleNestedTabsPage()
        val afterDescAppend =
            applySnippetDeltaPatch(
                page,
                SnippetDeltaPatch(SnippetDeltaOp.APPEND_TEXT, "nested_card", textChunk = " update"),
            )
        val afterArrayAppend =
            applySnippetDeltaPatch(
                afterDescAppend,
                SnippetDeltaPatch(SnippetDeltaOp.APPEND_TEXT, "nested_card", "items.0.label", " done"),
            )
        val tabSnippets =
            afterArrayAppend.snippets
                .single()
                .data["tabs"]!!
                .jsonArray[0]
                .jsonObject["snippets"]!!
                .jsonArray
        val patchedCardData = tabSnippets[0].jsonObject["data"]!!.jsonObject
        assertEquals("Initial update", patchedCardData["description"]!!.jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals(
            "Step 1 done",
            patchedCardData["items"]!!
                .jsonArray[0]
                .jsonObject["label"]!!
                .jsonPrimitive.content,
        )

        val afterNestedRemove =
            applySnippetDeltaPatch(
                afterArrayAppend,
                SnippetDeltaPatch(SnippetDeltaOp.REMOVE_SNIPPET, snippetId = "nested_card"),
            )
        val remainingSnippets =
            afterNestedRemove.snippets
                .single()
                .data["tabs"]!!
                .jsonArray[0]
                .jsonObject["snippets"]!!
                .jsonArray
        assertTrue(remainingSnippets.isEmpty())
    }

    private fun sampleNestedTabsPage(): PageResponse {
        val nestedCard =
            SnippetEnvelope(
                snippetId = "nested_card",
                snippetType = SnippetTypes.CARD,
                data =
                    buildJsonObject {
                        putJsonObject("description") { put("text", "Initial") }
                        put("items", buildJsonArray { add(buildJsonObject { put("label", "Step 1") }) })
                    },
            )
        val encodedCard = SduiJson.encodeToJsonElement(SnippetEnvelope.serializer(), nestedCard)
        val tabEntry =
            buildJsonObject {
                put("id", "t1")
                put("title", "Tab 1")
                put("snippets", buildJsonArray { add(encodedCard) })
            }
        return PageResponse(
            pageId = "nested_page",
            snippets =
                listOf(
                    SnippetEnvelope(
                        snippetId = "tabs_1",
                        snippetType = SnippetTypes.TABS,
                        data = buildJsonObject { put("tabs", buildJsonArray { add(tabEntry) }) },
                    ),
                ),
        )
    }

    @Test
    fun replaceInsertRemoveAndUpdatePageMetaApplyDeterministically() {
        val s1 = SnippetEnvelope(snippetId = "s1", snippetType = SnippetTypes.CARD, data = JsonObject(emptyMap()))
        val s2 = SnippetEnvelope(snippetId = "s2", snippetType = SnippetTypes.CARD, data = JsonObject(emptyMap()))
        val s3 = SnippetEnvelope("s3", snippetType = SnippetTypes.BANNER, data = buildJsonObject { put("k", "v3") })
        val initial = PageResponse(pageId = "feed", title = "Feed", pageVersion = "v1", snippets = listOf(s1, s2))

        val afterInsert =
            applySnippetDeltaPatch(
                initial,
                SnippetDeltaPatch(op = SnippetDeltaOp.INSERT_SNIPPET, snippet = s3, insertIndex = 1),
            )
        assertEquals(listOf("s1", "s3", "s2"), afterInsert.snippets.map { it.snippetId })

        val s3Updated = s3.copy(data = buildJsonObject { put("k", "v3-updated") })
        val afterDedup =
            applySnippetDeltaPatch(afterInsert, SnippetDeltaPatch(SnippetDeltaOp.INSERT_SNIPPET, snippet = s3Updated))
        assertEquals(listOf("s1", "s3", "s2"), afterDedup.snippets.map { it.snippetId })
        assertEquals(
            "v3-updated",
            afterDedup.snippets[1]
                .data["k"]!!
                .jsonPrimitive.content,
        )

        val s2Replacement = SnippetEnvelope("s2_new", snippetType = SnippetTypes.HEADER)
        val afterReplace =
            applySnippetDeltaPatch(
                afterDedup,
                SnippetDeltaPatch(SnippetDeltaOp.REPLACE_SNIPPET, snippetId = "s2", snippet = s2Replacement),
            )
        assertEquals(listOf("s1", "s3", "s2_new"), afterReplace.snippets.map { it.snippetId })

        val afterRemove =
            applySnippetDeltaPatch(afterReplace, SnippetDeltaPatch(SnippetDeltaOp.REMOVE_SNIPPET, snippetId = "s1"))
        assertEquals(listOf("s3", "s2_new"), afterRemove.snippets.map { it.snippetId })

        val afterMeta =
            applySnippetDeltaPatch(
                afterRemove,
                SnippetDeltaPatch(SnippetDeltaOp.UPDATE_PAGE_META, title = "Live Feed", pageVersion = "v2"),
            )
        assertEquals("Live Feed", afterMeta.title)
        assertEquals("v2", afterMeta.pageVersion)
    }

    @Test
    fun layoutControllerObserveStreamAppliesTokenChunksAndTransitionsIsStreaming() =
        runTest(StandardTestDispatcher()) {
            val streamEvents = MutableSharedFlow<AppResult<SseEvent>>(extraBufferCapacity = 16)
            val sseTransport = FakeSseTransport(streamEvents)
            val controller = buildController(this, mapOf("ai-chat" to streamingAssistantPage()), sseTransport)
            controller.loadScreen("ai-chat").join()

            assertFalse(assertIs<LayoutUiState.Ready>(controller.uiState.value).isStreaming)
            val streamJob = assertNotNull(controller.observeStream("/api/v1/stream/ai-chat"))
            runCurrent()
            assertTrue(assertIs<LayoutUiState.Ready>(controller.uiState.value).isStreaming)

            streamEvents.emit(AppResult.Success(SseEvent(data = "   ")))
            streamEvents.emit(AppResult.Success(SseEvent(data = "{not-valid-json")))
            streamEvents.emit(encodeAppend("ai_msg", "Abeta "))
            runCurrent()
            val midPage = assertIs<LayoutUiState.Ready>(controller.uiState.value).page
            assertEquals("Abeta ", snippetBodyText(midPage, "ai_msg"))

            streamEvents.emit(encodeAppend("ai_msg", "KMP "))
            streamEvents.emit(encodeAppend("ai_msg", "Streams!", done = true))
            streamEvents.emit(encodeAppend("ai_msg", " IGNORED"))
            advanceUntilIdle()

            assertFalse(streamJob.isActive)
            val finalReady = assertIs<LayoutUiState.Ready>(controller.uiState.value)
            assertFalse(finalReady.isStreaming)
            assertEquals("Abeta KMP Streams!", snippetBodyText(finalReady.page, "ai_msg"))
        }

    @Test
    fun layoutControllerAutoConnectsFromPageStreamSpecAndCancelsOnLoadScreen() =
        runTest(StandardTestDispatcher()) {
            val streamEvents = MutableSharedFlow<AppResult<SseEvent>>(extraBufferCapacity = 8)
            val sseTransport = FakeSseTransport(streamEvents)
            val pages =
                mapOf(
                    "chat" to autoConnectChatPage(),
                    "home" to PageResponse(pageId = "home", title = "Home"),
                )
            val controller = buildController(this, pages, sseTransport)

            controller.loadScreen("chat").join()
            runCurrent()

            assertEquals("/api/v1/streams/chat", sseTransport.lastPath)
            assertEquals(mapOf("X-Channel" to "support"), sseTransport.lastHeaders)
            assertEquals(2_500L, sseTransport.lastPolicy?.initialDelayMillis)
            assertTrue(assertIs<LayoutUiState.Ready>(controller.uiState.value).isStreaming)

            streamEvents.emit(encodeEvent(SnippetDeltaPatch(SnippetDeltaOp.APPEND_TEXT, "reply", textChunk = " there")))
            runCurrent()
            val livePage = assertIs<LayoutUiState.Ready>(controller.uiState.value).page
            assertEquals("Hi there", snippetBodyText(livePage, "reply"))

            controller.loadScreen("home").join()
            runCurrent()
            assertTrue(sseTransport.completedOrCancelled)
            val homeReady = assertIs<LayoutUiState.Ready>(controller.uiState.value)
            assertEquals("home", homeReady.page.pageId)
            assertFalse(homeReady.isStreaming)
        }

    private fun sampleAppendPage(): PageResponse =
        PageResponse(
            pageId = "assistant",
            title = "AI Assistant",
            snippets =
                listOf(
                    SnippetEnvelope(
                        snippetId = "reply_1",
                        snippetType = SnippetTypes.BANNER,
                        data =
                            buildJsonObject {
                                putJsonObject("title") { put("text", "Summary") }
                                putJsonObject("body") { put("text", "Hello") }
                            },
                    ),
                    SnippetEnvelope(
                        snippetId = "msg_1",
                        snippetType = SnippetTypes.EMPTY_STATE,
                        data =
                            buildJsonObject {
                                putJsonObject("title") { put("text", "Status") }
                                putJsonObject("message") { put("text", "Step 1") }
                            },
                    ),
                    SnippetEnvelope(
                        snippetId = "hdr_1",
                        snippetType = SnippetTypes.HEADER,
                        data = buildJsonObject { putJsonObject("title") { put("text", "Live") } },
                    ),
                ),
        )

    private fun streamingAssistantPage(): PageResponse =
        PageResponse(
            pageId = "ai-chat",
            title = "Assistant",
            snippets =
                listOf(
                    SnippetEnvelope(
                        snippetId = "ai_msg",
                        snippetType = SnippetTypes.BANNER,
                        data = buildJsonObject { putJsonObject("body") { put("text", "") } },
                    ),
                ),
        )

    private fun autoConnectChatPage(): PageResponse =
        PageResponse(
            pageId = "chat",
            title = "Live Chat",
            streamSpec =
                SseStreamSpec(
                    path = "/api/v1/streams/chat",
                    autoConnect = true,
                    headers = mapOf("X-Channel" to "support"),
                    retryMs = 2_500L,
                ),
            snippets =
                listOf(
                    SnippetEnvelope(
                        snippetId = "reply",
                        snippetType = SnippetTypes.BANNER,
                        data = buildJsonObject { putJsonObject("body") { put("text", "Hi") } },
                    ),
                ),
        )

    private fun buildController(
        scope: TestScope,
        pages: Map<String, PageResponse>,
        sseTransport: SseTransport?,
    ): LayoutController {
        val repository =
            ScreenRepository { id ->
                pages[id]?.let { AppResult.Success(it) }
                    ?: AppResult.Failure(NotFoundError(resource = id, message = "Unknown screen '$id'"))
            }
        val dispatcher =
            DefaultActionDispatcher(
                decoderRegistry = BuiltInActionDecoders.createDefaultRegistry(),
                actionRegistry =
                    BuiltInActionHandlers.createDefaultRegistry { payload, _ ->
                        AppResult.Success(
                            ActionOutcome.ApiCallResult(
                                endpoint = payload.endpoint,
                                method = payload.method,
                                isSuccess = true,
                                statusCode = 200,
                            ),
                        )
                    },
            )
        return LayoutController(
            screenRepository = repository,
            actionDispatcher = dispatcher,
            sessionStore = InMemorySessionStore(),
            analyticsTracker = RecordingAnalyticsTracker(),
            scope = scope,
            sseTransport = sseTransport,
        )
    }

    private fun encodeAppend(
        snippetId: String,
        chunk: String,
        done: Boolean = false,
    ): AppResult<SseEvent> =
        encodeEvent(
            SnippetDeltaPatch(
                op = SnippetDeltaOp.APPEND_TEXT,
                snippetId = snippetId,
                fieldPath = "body.text",
                textChunk = chunk,
                done = done,
            ),
        )

    private fun encodeEvent(patch: SnippetDeltaPatch): AppResult<SseEvent> =
        AppResult.Success(
            SseEvent(data = SduiJson.encodeToString(SnippetDeltaPatch.serializer(), patch)),
        )

    private fun snippetBodyText(
        page: PageResponse,
        snippetId: String,
    ): String {
        val snippet = page.snippets.first { it.snippetId == snippetId }
        val body = snippet.data["body"] as JsonObject
        return (body["text"] as JsonPrimitive).content
    }

    private class FakeSseTransport(
        private val events: Flow<AppResult<SseEvent>>,
    ) : SseTransport {
        var lastPath: String? = null
        var lastHeaders: Map<String, String> = emptyMap()
        var lastPolicy: SseReconnectPolicy? = null
        var completedOrCancelled: Boolean = false

        override fun connect(
            path: String,
            headers: Map<String, String>,
            queryParams: Map<String, String>,
            lastEventId: String?,
            reconnectPolicy: SseReconnectPolicy,
        ): Flow<AppResult<SseEvent>> {
            lastPath = path
            lastHeaders = headers
            lastPolicy = reconnectPolicy
            return flow { events.collect { emit(it) } }.onCompletion { completedOrCancelled = true }
        }
    }
}
