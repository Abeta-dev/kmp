package dev.abeta.kmp.layout.engine

import app.cash.turbine.test
import dev.abeta.kmp.actions.codec.ActionDecoderRegistry
import dev.abeta.kmp.actions.codec.BuiltInActionDecoders
import dev.abeta.kmp.actions.handler.ActionOutcome
import dev.abeta.kmp.actions.handler.ActionRegistry
import dev.abeta.kmp.actions.handler.ApiCallExecutor
import dev.abeta.kmp.actions.handler.BuiltInActionHandlers
import dev.abeta.kmp.actions.handler.DefaultActionDispatcher
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.analytics.AnalyticsCategory
import dev.abeta.kmp.analytics.RecordingAnalyticsTracker
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.FeedbackEffect
import dev.abeta.kmp.contracts.OverlayPresentation
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.TrackingContract
import dev.abeta.kmp.core.error.NotFoundError
import dev.abeta.kmp.core.error.OfflineError
import dev.abeta.kmp.core.identity.SequentialUuidGenerator
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.core.storage.InMemoryKeyValueStore
import dev.abeta.kmp.core.time.MutableClock
import dev.abeta.kmp.layout.platform.PlatformBridgeBundle
import dev.abeta.kmp.layout.repository.ScreenRepository
import dev.abeta.kmp.state.session.InMemorySessionStore
import dev.abeta.kmp.state.sync.OfflineMutationQueue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class LayoutControllerTest {
    private fun readGoldenPage(fileName: String): PageResponse {
        val candidates =
            listOf(
                File("../bff/testdata/golden/$fileName"),
                File("bff/testdata/golden/$fileName"),
                File("../../bff/testdata/golden/$fileName"),
            )
        val file =
            candidates.firstOrNull { it.exists() }
                ?: error("Could not locate golden file '$fileName' in ${candidates.map { it.absolutePath }}")
        return SduiJson.decodeFromString(PageResponse.serializer(), file.readText())
    }

    private fun createController(
        scope: TestScope,
        pages: Map<String, PageResponse> =
            mapOf(
                "home" to readGoldenPage("home_screen.json"),
                "form-demo" to readGoldenPage("form_screen.json"),
                "containers" to readGoldenPage("containers_screen.json"),
                "store-home" to readGoldenPage("store_home.json"),
            ),
        navigations: MutableList<ActionOutcome.NavigationRequested> = mutableListOf(),
        backs: MutableList<ActionOutcome.BackRequested> = mutableListOf(),
        copies: MutableList<ActionOutcome.ClipboardCopyRequested> = mutableListOf(),
        apiExecutor: ApiCallExecutor =
            ApiCallExecutor { payload, _ ->
                AppResult.Success(
                    ActionOutcome.ApiCallResult(
                        endpoint = payload.endpoint,
                        method = payload.method,
                        isSuccess = true,
                        statusCode = 200,
                    ),
                )
            },
        offlineCoordinator: OfflineActionCoordinator? = null,
        platformBridges: PlatformBridgeBundle = PlatformBridgeBundle.createTestDefault(),
    ): Triple<LayoutController, InMemorySessionStore, RecordingAnalyticsTracker> {
        val sessionStore = InMemorySessionStore()
        val tracker = RecordingAnalyticsTracker()
        val repo =
            ScreenRepository { id ->
                val page = pages[id]
                if (page != null) {
                    AppResult.Success(page)
                } else {
                    AppResult.Failure(NotFoundError(resource = id, message = "Screen '$id' not found"))
                }
            }
        val dispatcher =
            DefaultActionDispatcher(
                decoderRegistry = BuiltInActionDecoders.registerDefaults(ActionDecoderRegistry.builder()).build(),
                actionRegistry = BuiltInActionHandlers.createDefaultRegistry(apiExecutor),
            )
        val controller =
            LayoutController(
                screenRepository = repo,
                actionDispatcher = dispatcher,
                sessionStore = sessionStore,
                analyticsTracker = tracker,
                scope = scope,
                navigationListener = { navigations.add(it) },
                backListener = { backs.add(it) },
                clipboardListener = { copies.add(it) },
                offlineCoordinator = offlineCoordinator,
                platformBridges = platformBridges,
            )
        return Triple(controller, sessionStore, tracker)
    }

    @Test
    fun loadHomeScreenEmitsStatesAndDeduplicatesImpressions() =
        runTest(StandardTestDispatcher()) {
            val navigations = mutableListOf<ActionOutcome.NavigationRequested>()
            val copies = mutableListOf<ActionOutcome.ClipboardCopyRequested>()
            val (controller, _, tracker) =
                createController(
                    scope = this,
                    navigations = navigations,
                    copies = copies,
                )

            controller.uiState.test {
                assertIs<LayoutUiState.Idle>(awaitItem())
                controller.loadScreen("home")
                assertIs<LayoutUiState.Loading>(awaitItem())
                val ready = assertIs<LayoutUiState.Ready>(awaitItem())
                assertEquals("home", ready.page.pageId)
                assertEquals(8, ready.page.snippets.size)
                cancelAndIgnoreRemainingEvents()
            }

            assertHomeLoadAnalytics(tracker)

            // Snippet impression deduplication
            val headerSnippet = (controller.uiState.value as LayoutUiState.Ready).page.snippets.first()
            controller.onSnippetImpression(headerSnippet)
            controller.onSnippetImpression(headerSnippet)
            assertEquals(2, tracker.impressions.size)
            assertEquals("snippet_impression", tracker.impressions.last().eventName)

            // Navigation and clipboard secondary actions
            val navAction =
                ActionRef(
                    id = "nav_catalog",
                    type = ActionTypes.NAVIGATE,
                    payload = buildJsonObject { put("route", "/screens/catalog") },
                )
            controller.onAction(navAction).join()
            assertEquals("/screens/catalog", navigations.single().route)

            val copyAction =
                ActionRef(
                    id = "copy_url",
                    type = ActionTypes.COPY_TO_CLIPBOARD,
                    payload = buildJsonObject { put("text", "/api/v1/catalog/tokens") },
                    secondaryActions =
                        listOf(
                            ActionRef(
                                type = ActionTypes.SHOW_TOAST,
                                payload =
                                    buildJsonObject {
                                        put("message", "Copied!")
                                        put("variant", "success")
                                    },
                            ),
                        ),
                )
            controller.onAction(copyAction).join()
            assertEquals("/api/v1/catalog/tokens", copies.single().text)
            val readyAfterCopy = assertIs<LayoutUiState.Ready>(controller.uiState.value)
            assertEquals("Copied!", readyAfterCopy.activeToast?.message)
            assertEquals("success", readyAfterCopy.activeToast?.variant)
        }

    private fun assertHomeLoadAnalytics(tracker: RecordingAnalyticsTracker) {
        assertEquals(1, tracker.impressions.size)
        assertEquals("page_loaded", tracker.impressions.single().eventName)
        assertEquals(listOf("page_open", "screen_viewed"), tracker.events.map { it.name })
        val pageOpen = tracker.events.first()
        assertEquals(AnalyticsCategory.SCREEN, pageOpen.category)
        assertEquals("home", pageOpen.screenName)
        assertEquals("home", pageOpen.properties["page_id"])
        assertEquals("8", pageOpen.properties["snippet_count"])
    }

    @Test
    fun formValidationFailureThenValidSubmission() =
        runTest(StandardTestDispatcher()) {
            val (controller, sessionStore, tracker) = createController(scope = this)

            controller.loadScreen("form-demo").join()
            val formPage = assertIs<LayoutUiState.Ready>(controller.uiState.value).page
            val submitAction =
                SduiJson
                    .decodeFromJsonElement(
                        dev.abeta.kmp.contracts.FormSnippetData
                            .serializer(),
                        formPage.snippets.first { it.snippetType == SnippetTypes.FORM }.data,
                    ).submitButton!!
                    .onClick!!

            controller.onAction(submitAction).join()
            val invalidState = assertIs<LayoutUiState.Ready>(controller.uiState.value)
            assertEquals("Full name is required", invalidState.validationErrors["full_name"])
            assertEquals("Full name is required", invalidState.activeToast?.message)
            assertEquals("error", invalidState.activeToast?.variant)

            controller.onFormFieldChanged("full_name", "Alex Rivera")
            controller.onFormFieldChanged("work_email", "alex@abeta.dev")
            assertEquals(JsonPrimitive("Alex Rivera"), sessionStore.get("full_name"))
            assertEquals(JsonPrimitive("alex@abeta.dev"), sessionStore.get("work_email"))

            controller.onAction(submitAction).join()
            val validState = assertIs<LayoutUiState.Ready>(controller.uiState.value)
            assertTrue(validState.validationErrors.isEmpty())
            assertEquals("Workspace setup saved!", validState.activeToast?.message)
            assertEquals("success", validState.activeToast?.variant)
            assertTrue(tracker.events.any { it.name == "workspace_form_submitted" })
        }

    @Test
    fun dialogOpensAndDismissesCleanly() =
        runTest(StandardTestDispatcher()) {
            val (controller, _, _) = createController(scope = this)
            controller.loadScreen("form-demo").join()

            controller
                .onAction(
                    ActionRef(
                        type = ActionTypes.OPEN_DIALOG,
                        payload =
                            buildJsonObject {
                                put("dialog_id", "confirm_1")
                                put("title", "Confirm")
                                put("message", "Proceed?")
                            },
                    ),
                ).join()
            val dialogState = assertIs<LayoutUiState.Ready>(controller.uiState.value)
            assertEquals("confirm_1", assertNotNull(dialogState.activeOverlay).dialogId)

            controller
                .onAction(
                    ActionRef(
                        type = ActionTypes.DISMISS_OVERLAY,
                        payload = buildJsonObject { put("overlay_id", "confirm_1") },
                    ),
                ).join()
            assertNull(assertIs<LayoutUiState.Ready>(controller.uiState.value).activeOverlay)
        }

    @Test
    fun snippetMutationsSupportAppendReplaceAndRemove() =
        runTest(StandardTestDispatcher()) {
            val (controller, _, _) = createController(scope = this)
            controller.loadScreen("form-demo").join()

            val appended =
                SnippetEnvelope(
                    snippetId = "added_spacer",
                    snippetType = SnippetTypes.SPACER,
                    data = buildJsonObject { put("spacer", buildJsonObject { put("height_dp", 24) }) },
                )
            controller.onAction(updateSnippetsAction("append", null, listOf(appended))).join()
            val afterAppend = assertIs<LayoutUiState.Ready>(controller.uiState.value)
            assertEquals(3, afterAppend.page.snippets.size)
            assertEquals(
                "added_spacer",
                afterAppend.page.snippets
                    .last()
                    .snippetId,
            )

            val replaced =
                SnippetEnvelope(
                    snippetId = "added_spacer_replaced",
                    snippetType = SnippetTypes.DIVIDER,
                    data = buildJsonObject { },
                )
            controller.onAction(updateSnippetsAction("replace", "added_spacer", listOf(replaced))).join()
            val afterReplace = assertIs<LayoutUiState.Ready>(controller.uiState.value)
            assertEquals(3, afterReplace.page.snippets.size)
            assertEquals(
                "added_spacer_replaced",
                afterReplace.page.snippets
                    .last()
                    .snippetId,
            )

            controller.onAction(updateSnippetsAction("remove", "added_spacer_replaced", emptyList())).join()
            val afterRemove = assertIs<LayoutUiState.Ready>(controller.uiState.value)
            assertEquals(2, afterRemove.page.snippets.size)
        }

    @Test
    fun containersScreenSupportsLoadMorePullToRefreshAndInitialControlValues() =
        runTest(StandardTestDispatcher()) {
            val calledEndpoints = mutableListOf<String>()
            val (controller, _, _) =
                createController(
                    scope = this,
                    apiExecutor = { payload, _ ->
                        calledEndpoints.add(payload.endpoint)
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

            controller.loadScreen("containers").join()
            val ready = assertIs<LayoutUiState.Ready>(controller.uiState.value)
            assertEquals("containers", ready.page.pageId)
            assertEquals("true", ready.formValues["compact_mode"])
            assertEquals("false", ready.formValues["confirm_schema"])
            assertEquals("2.0", ready.formValues["grid_density"])

            controller.loadMore()!!.join()
            val afterLoadMore = assertIs<LayoutUiState.Ready>(controller.uiState.value)
            assertFalse(afterLoadMore.isLoadingMore)
            assertEquals(listOf("/api/v1/screens/containers?cursor=cursor_page_2"), calledEndpoints)
            assertEquals("Loaded next page of containers", afterLoadMore.activeToast?.message)

            controller.refreshFromPull()!!.join()
            val afterRefresh = assertIs<LayoutUiState.Ready>(controller.uiState.value)
            assertFalse(afterRefresh.isRefreshing)
            assertEquals("containers", afterRefresh.page.pageId)
        }

    @Test
    fun offlineActionFailureQueuesMutationAndFlushReplaysWhenOnline() =
        runTest(StandardTestDispatcher()) {
            var offline = true
            val replayed = mutableListOf<String>()
            val executor =
                ApiCallExecutor { payload, _ ->
                    if (offline) {
                        AppResult.Failure(OfflineError("No connection"))
                    } else {
                        replayed.add(payload.endpoint)
                        AppResult.Success(
                            ActionOutcome.ApiCallResult(
                                endpoint = payload.endpoint,
                                method = payload.method,
                                isSuccess = true,
                                statusCode = 200,
                            ),
                        )
                    }
                }
            val coordinator =
                OfflineActionCoordinator(
                    mutationQueue =
                        OfflineMutationQueue(
                            backingStore = InMemoryKeyValueStore(),
                            clock = MutableClock(1_000L),
                            uuidGenerator = SequentialUuidGenerator(),
                        ),
                    apiCallExecutor = executor,
                )
            val (controller, _, _) =
                createController(
                    scope = this,
                    apiExecutor = executor,
                    offlineCoordinator = coordinator,
                )

            controller.loadScreen("home").join()
            controller
                .onAction(
                    ActionRef(
                        id = "offline_save",
                        type = ActionTypes.CALL_API,
                        payload =
                            buildJsonObject {
                                put("endpoint", "/api/v1/actions/execute")
                                put("method", "POST")
                            },
                    ),
                ).join()

            val queuedState = assertIs<LayoutUiState.Ready>(controller.uiState.value)
            assertEquals(1, queuedState.pendingOfflineActions)

            offline = false
            controller.flushOfflineActions()!!.join()
            val flushedState = assertIs<LayoutUiState.Ready>(controller.uiState.value)
            assertEquals(0, flushedState.pendingOfflineActions)
            assertEquals(listOf("/api/v1/actions/execute"), replayed)
        }

    private fun updateSnippetsAction(
        operation: String,
        targetSnippetId: String?,
        snippets: List<SnippetEnvelope>,
    ): ActionRef =
        ActionRef(
            type = ActionTypes.UPDATE_SNIPPETS,
            payload =
                buildJsonObject {
                    put("operation", operation)
                    targetSnippetId?.let { put("target_snippet_id", it) }
                    if (snippets.isNotEmpty()) {
                        put(
                            "snippets",
                            buildJsonArray {
                                snippets.forEach {
                                    add(SduiJson.encodeToJsonElement(SnippetEnvelope.serializer(), it))
                                }
                            },
                        )
                    }
                },
        )

    @Test
    fun missingScreenEmitsErrorState() =
        runTest(StandardTestDispatcher()) {
            val (controller, _, _) = createController(scope = this)
            controller.loadScreen("does-not-exist").join()
            advanceUntilIdle()
            val errorState = assertIs<LayoutUiState.Error>(controller.uiState.value)
            assertIs<NotFoundError>(errorState.error)
            assertEquals("does-not-exist", errorState.screenId)
        }

    @Test
    fun storeHomeSeedsNestedProductQuantitiesAndHandlesFeedbackActions() =
        runTest(StandardTestDispatcher()) {
            val (controller, _, _) =
                createController(
                    scope = this,
                    apiExecutor = expressOrderCelebrationExecutor(),
                )

            controller.loadScreen("store-home").join()
            advanceUntilIdle()
            val ready = assertIs<LayoutUiState.Ready>(controller.uiState.value)
            assertEquals("1", ready.formValues["cart_qty_avocados"])
            assertEquals("1", ready.formValues["cart_qty_sourdough"])
            assertEquals("2.0", ready.formValues["rider_tip_dollars"])

            val bounceRef =
                ActionRef(
                    id = "bounce",
                    type = ActionTypes.TRIGGER_FEEDBACK,
                    payload =
                        buildJsonObject {
                            put("effect", "cart_bounce")
                            put("target_id", "floating_cart_dock")
                        },
                )
            controller.onAction(bounceRef).join()
            val afterBounce = assertIs<LayoutUiState.Ready>(controller.uiState.value)
            assertEquals(1L, afterBounce.feedbackSequence)
            assertEquals(FeedbackEffect.CART_BOUNCE, afterBounce.activeFeedback?.effect)

            val orderRef =
                ActionRef(
                    id = "place_express_order",
                    type = ActionTypes.CALL_API,
                    payload =
                        buildJsonObject {
                            put("endpoint", "/api/v1/orders/express")
                            put("method", "POST")
                        },
                )
            controller.onAction(orderRef).join()
            val afterOrder = assertIs<LayoutUiState.Ready>(controller.uiState.value)
            assertEquals(2L, afterOrder.feedbackSequence)
            assertEquals(FeedbackEffect.CELEBRATION, afterOrder.activeFeedback?.effect)
            assertEquals("Order Confirmed!", afterOrder.activeFeedback?.title)

            controller.dismissFeedback()
            assertNull(assertIs<LayoutUiState.Ready>(controller.uiState.value).activeFeedback)
        }

    @Test
    fun emitsAutomaticClickTrackingDeduplicatesNodeImpressionsAndLoadsRemoteSheetScreen() =
        runTest(StandardTestDispatcher()) {
            val (controller, _, tracker) =
                createController(
                    scope = this,
                    pages =
                        mapOf(
                            "home" to readGoldenPage("home_screen.json"),
                            "express-sheet" to sampleRemoteSheetPage(),
                        ),
                )

            controller.loadScreen("home").join()
            val nodeTracking = TrackingContract(eventName = "card_seen", properties = mapOf("slot" to "hero"))
            controller.onNodeImpression("hero_node_1", nodeTracking)
            controller.onNodeImpression("hero_node_1", nodeTracking)
            val nodeImpressions = tracker.impressions.filter { it.eventName == "card_seen" }
            assertEquals(1, nodeImpressions.size)
            assertEquals("hero_node_1", nodeImpressions.single().properties["node_id"])
            assertEquals("home", nodeImpressions.single().properties["page_id"])

            controller.onAction(sampleOpenRemoteSheetAction()).join()

            val ready = assertIs<LayoutUiState.Ready>(controller.uiState.value)
            val overlay = assertNotNull(ready.activeOverlay)
            assertEquals(OverlayPresentation.BOTTOM_SHEET, overlay.presentation)
            assertEquals(listOf("sheet_spacer"), overlay.snippets.map { it.snippetId })

            val autoClick = tracker.events.first { it.name == "interaction_click" }
            assertEquals("open_express_sheet", autoClick.properties["action_id"])
            assertEquals(ActionTypes.OPEN_DIALOG, autoClick.properties["action_type"])
            assertEquals("floating_cta", autoClick.properties["source"])
            assertTrue(tracker.events.any { it.name == "basket_sheet_tapped" })

            val beforeTrackCount = tracker.events.count { it.name == "interaction_click" }
            controller
                .onAction(
                    ActionRef(
                        id = "explicit_track",
                        type = ActionTypes.TRACK_EVENT,
                        payload = buildJsonObject { put("event_name", "custom_ping") },
                        tracking = TrackingContract(eventName = "custom_ping"),
                    ),
                ).join()
            assertEquals(beforeTrackCount, tracker.events.count { it.name == "interaction_click" })
            assertEquals(1, tracker.events.count { it.name == "custom_ping" })
        }

    private fun sampleRemoteSheetPage(): PageResponse =
        PageResponse(
            pageId = "express-sheet",
            title = "Express Sheet",
            snippets =
                listOf(
                    SnippetEnvelope(
                        snippetId = "sheet_spacer",
                        snippetType = SnippetTypes.SPACER,
                        data = buildJsonObject { put("spacer", buildJsonObject { put("height_dp", 16) }) },
                    ),
                ),
        )

    private fun sampleOpenRemoteSheetAction(): ActionRef =
        ActionRef(
            id = "open_express_sheet",
            type = ActionTypes.OPEN_DIALOG,
            payload =
                buildJsonObject {
                    put("dialog_id", "sheet_1")
                    put("title", "Quick Basket")
                    put("presentation", "bottom_sheet")
                    put("sheet_screen_id", "express-sheet")
                },
            tracking =
                TrackingContract(
                    eventName = "basket_sheet_tapped",
                    properties = mapOf("source" to "floating_cta"),
                ),
        )

    private fun expressOrderCelebrationExecutor(): ApiCallExecutor {
        val celebrationAction =
            ActionRef(
                id = "express_celebration",
                type = ActionTypes.TRIGGER_FEEDBACK,
                payload =
                    buildJsonObject {
                        put("effect", "celebration")
                        put("title", "Order Confirmed!")
                        put("subtitle", "Abeta Fresh rider arriving in 8 minutes")
                        put("duration_ms", 2400)
                    },
            )
        return ApiCallExecutor { payload, _ ->
            val followUps =
                if (payload.endpoint == "/api/v1/orders/express") {
                    listOf(celebrationAction)
                } else {
                    emptyList()
                }
            AppResult.Success(
                ActionOutcome.ApiCallResult(
                    endpoint = payload.endpoint,
                    method = payload.method,
                    isSuccess = true,
                    statusCode = 200,
                    followUpActions = followUps,
                ),
            )
        }
    }
}
