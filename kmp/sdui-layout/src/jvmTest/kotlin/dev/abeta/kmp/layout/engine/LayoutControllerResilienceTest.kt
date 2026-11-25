package dev.abeta.kmp.layout.engine

import dev.abeta.kmp.actions.codec.BuiltInActionDecoders
import dev.abeta.kmp.actions.handler.ActionOutcome
import dev.abeta.kmp.actions.handler.ApiCallExecutor
import dev.abeta.kmp.actions.handler.BuiltInActionHandlers
import dev.abeta.kmp.actions.handler.DefaultActionDispatcher
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.analytics.RecordingAnalyticsTracker
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.core.error.OfflineError
import dev.abeta.kmp.core.error.UnexpectedError
import dev.abeta.kmp.core.identity.SequentialUuidGenerator
import dev.abeta.kmp.core.platform.ClipboardPort
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.storage.InMemoryKeyValueStore
import dev.abeta.kmp.core.time.MutableClock
import dev.abeta.kmp.layout.platform.PlatformBridgeBundle
import dev.abeta.kmp.layout.repository.ScreenRepository
import dev.abeta.kmp.state.session.InMemorySessionStore
import dev.abeta.kmp.state.sync.OfflineMutationQueue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LayoutControllerResilienceTest {
    @Test
    fun pullToRefreshFailurePreservesReadyStateAndIdenticalToastsIncrementSequence() =
        runTest(StandardTestDispatcher()) {
            var failLoad = false
            val repo =
                ScreenRepository { id ->
                    if (failLoad) {
                        AppResult.Failure(OfflineError("No network during refresh"))
                    } else {
                        AppResult.Success(PageResponse(pageId = id, title = "Home Live"))
                    }
                }
            val controller = buildController(scope = this, repository = repo)

            controller.loadScreen("home").join()
            assertEquals("Home Live", assertIs<LayoutUiState.Ready>(controller.uiState.value).page.title)

            failLoad = true
            controller.loadScreen("home", showLoader = false).join()
            val afterFailedRefresh = assertIs<LayoutUiState.Ready>(controller.uiState.value)
            assertFalse(afterFailedRefresh.isRefreshing)
            assertEquals("Home Live", afterFailedRefresh.page.title)
            assertEquals("No network during refresh", afterFailedRefresh.activeToast?.message)
            val firstSeq = afterFailedRefresh.toastSequence

            val repeatToast =
                ActionRef(
                    id = "toast_1",
                    type = ActionTypes.SHOW_TOAST,
                    payload = buildJsonObject { put("message", "No network during refresh") },
                )
            controller.onAction(repeatToast).join()
            val afterRepeat = assertIs<LayoutUiState.Ready>(controller.uiState.value)
            assertTrue(afterRepeat.toastSequence > firstSeq)
        }

    @Test
    fun rapidLoadScreenCancelsStaleLoadAndDeduplicatesSnippetsAndNonStringState() =
        runTest(StandardTestDispatcher()) {
            val slowGate = CompletableDeferred<Unit>()
            val repo =
                ScreenRepository { id ->
                    if (id == "slow") slowGate.await()
                    AppResult.Success(
                        PageResponse(
                            pageId = id,
                            title = "Screen $id",
                            snippets = listOf(SnippetEnvelope(snippetId = "s1", snippetType = SnippetTypes.DIVIDER)),
                        ),
                    )
                }
            val controller = buildController(scope = this, repository = repo)

            val slowJob = controller.loadScreen("slow")
            val fastJob = controller.loadScreen("fast")
            fastJob.join()
            slowGate.complete(Unit)
            slowJob.join()
            assertEquals("fast", assertIs<LayoutUiState.Ready>(controller.uiState.value).page.pageId)

            val replacementS1 =
                SnippetEnvelope(
                    snippetId = "s1",
                    snippetType = SnippetTypes.SPACER,
                    data = buildJsonObject { put("spacer", buildJsonObject { put("height_dp", 12) }) },
                )
            controller.onAction(appendSnippetsAction(listOf(replacementS1))).join()
            val deduped = assertIs<LayoutUiState.Ready>(controller.uiState.value)
            assertEquals(listOf("s1"), deduped.page.snippets.map { it.snippetId })
            assertEquals(
                SnippetTypes.SPACER,
                deduped.page.snippets
                    .single()
                    .snippetType,
            )

            controller
                .onAction(
                    ActionRef(
                        type = ActionTypes.SAVE_STATE,
                        payload =
                            buildJsonObject {
                                put("key", "qty_override")
                                put("value", 7)
                            },
                    ),
                ).join()
            assertEquals("7", assertIs<LayoutUiState.Ready>(controller.uiState.value).formValues["qty_override"])
        }

    @Test
    fun offlineQueueSkipsGetAndSucceededSiblingsAndClipboardFailureSkipsListener() =
        runTest(StandardTestDispatcher()) {
            val copies = mutableListOf<ActionOutcome.ClipboardCopyRequested>()
            val bridges = PlatformBridgeBundle.createTestDefault().copy(clipboardPort = failingClipboardPort())
            val executor = selectiveApiExecutor()
            val queue =
                OfflineMutationQueue(
                    backingStore = InMemoryKeyValueStore(),
                    clock = MutableClock(1_000L),
                    uuidGenerator = SequentialUuidGenerator(),
                )
            val coordinator = OfflineActionCoordinator(mutationQueue = queue, apiCallExecutor = executor)
            val controller =
                buildController(
                    scope = this,
                    repository =
                        ScreenRepository { id ->
                            AppResult.Success(PageResponse(pageId = id, title = "Home"))
                        },
                    apiExecutor = executor,
                    offlineCoordinator = coordinator,
                    platformBridges = bridges,
                    clipboardListener = copies::add,
                )

            controller.loadScreen("home").join()
            controller.onAction(callApiAction("/api/v1/read-only", "GET")).join()
            assertEquals(0, coordinator.status.value.pendingCount)

            val batch =
                batchAction(
                    callApiAction("/api/v1/ok", "POST"),
                    callApiAction("/api/v1/fail-post", "POST"),
                )
            controller.onAction(batch).join()
            assertEquals(1, coordinator.status.value.pendingCount)
            val queuedEndpoint =
                queue.queue.value
                    .single()
                    .payload.jsonObject["payload"]!!
                    .jsonObject["endpoint"]!!
                    .jsonPrimitive.content
            assertEquals("/api/v1/fail-post", queuedEndpoint)

            controller
                .onAction(
                    ActionRef(
                        type = ActionTypes.COPY_TO_CLIPBOARD,
                        payload = buildJsonObject { put("text", "secret") },
                    ),
                ).join()
            assertTrue(copies.isEmpty())
            val toast = assertIs<LayoutUiState.Ready>(controller.uiState.value).activeToast
            assertEquals("Clipboard locked", toast?.message)
        }

    @Suppress("LongParameterList")
    private fun buildController(
        scope: TestScope,
        repository: ScreenRepository,
        apiExecutor: ApiCallExecutor? = null,
        offlineCoordinator: OfflineActionCoordinator? = null,
        platformBridges: PlatformBridgeBundle = PlatformBridgeBundle.createTestDefault(),
        clipboardListener: (ActionOutcome.ClipboardCopyRequested) -> Unit = {},
    ): LayoutController {
        val registry =
            if (apiExecutor != null) {
                BuiltInActionHandlers.createDefaultRegistry(apiExecutor)
            } else {
                BuiltInActionHandlers.createDefaultRegistry()
            }
        val dispatcher =
            DefaultActionDispatcher(
                decoderRegistry = BuiltInActionDecoders.createDefaultRegistry(),
                actionRegistry = registry,
            )
        return LayoutController(
            screenRepository = repository,
            actionDispatcher = dispatcher,
            sessionStore = InMemorySessionStore(),
            analyticsTracker = RecordingAnalyticsTracker(),
            scope = scope,
            clipboardListener = clipboardListener,
            offlineCoordinator = offlineCoordinator,
            platformBridges = platformBridges,
        )
    }

    private fun failingClipboardPort(): ClipboardPort =
        object : ClipboardPort {
            override fun copy(
                text: String,
                label: String?,
            ): AppResult<Unit> = AppResult.Failure(UnexpectedError(message = "Clipboard locked"))

            override fun readText(): AppResult<String?> = AppResult.Success(null)
        }

    private fun selectiveApiExecutor(): ApiCallExecutor =
        ApiCallExecutor { payload, _ ->
            if (payload.endpoint == "/api/v1/ok") {
                AppResult.Success(
                    ActionOutcome.ApiCallResult(
                        endpoint = payload.endpoint,
                        method = payload.method,
                        isSuccess = true,
                        statusCode = 200,
                    ),
                )
            } else {
                AppResult.Failure(OfflineError("Offline"))
            }
        }

    private fun callApiAction(
        endpoint: String,
        method: String,
    ): ActionRef =
        ActionRef(
            type = ActionTypes.CALL_API,
            payload =
                buildJsonObject {
                    put("endpoint", endpoint)
                    put("method", method)
                },
        )

    private fun batchAction(vararg children: ActionRef): ActionRef =
        ActionRef(
            id = "batch_mixed",
            type = ActionTypes.BATCH,
            payload =
                buildJsonObject {
                    put(
                        "actions",
                        buildJsonArray {
                            children.forEach { add(SduiJson.encodeToJsonElement(ActionRef.serializer(), it)) }
                        },
                    )
                },
        )

    private fun appendSnippetsAction(snippets: List<SnippetEnvelope>): ActionRef =
        ActionRef(
            type = ActionTypes.UPDATE_SNIPPETS,
            payload =
                buildJsonObject {
                    put("operation", "append")
                    put(
                        "snippets",
                        buildJsonArray {
                            snippets.forEach { add(SduiJson.encodeToJsonElement(SnippetEnvelope.serializer(), it)) }
                        },
                    )
                },
        )
}
