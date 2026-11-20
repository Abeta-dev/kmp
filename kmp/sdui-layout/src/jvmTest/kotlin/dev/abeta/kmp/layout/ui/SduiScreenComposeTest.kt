package dev.abeta.kmp.layout.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.abeta.kmp.actions.codec.BuiltInActionDecoders
import dev.abeta.kmp.actions.handler.ActionOutcome
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.actions.model.OpenDialogPayload
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.FeedbackEffect
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.core.error.NetworkError
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.layout.engine.LayoutUiState
import dev.abeta.kmp.layout.snippet.BuiltInSnippetModule
import dev.abeta.kmp.tokens.DesignTheme
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SduiScreenComposeTest {
    private val verticalListMatcher: SemanticsMatcher =
        hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)

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

    @Test
    fun loadingStateRendersSkeletonPlaceholder() =
        runComposeUiTest {
            val registry = BuiltInSnippetModule.createDefaultRegistry()
            setContent {
                DesignTheme(darkTheme = false, reducedMotion = true) {
                    SduiScreen(
                        state = LayoutUiState.Loading,
                        snippetRegistry = registry,
                        onAction = {},
                        onFormValueChange = { _, _ -> },
                    )
                }
            }

            onNodeWithTag("sdui_loading_skeleton").assertIsDisplayed()
        }

    @Test
    fun errorStateDisplaysMessageAndInvokesRetryCallback() =
        runComposeUiTest {
            val registry = BuiltInSnippetModule.createDefaultRegistry()
            var retryClicks = 0

            setContent {
                DesignTheme(darkTheme = false, reducedMotion = true) {
                    SduiScreen(
                        state =
                            LayoutUiState.Error(
                                error = NetworkError(statusCode = 504, message = "Gateway timeout"),
                            ),
                        snippetRegistry = registry,
                        onAction = {},
                        onFormValueChange = { _, _ -> },
                        onRetry = { retryClicks++ },
                    )
                }
            }

            onNodeWithText("Unable to load screen").assertIsDisplayed()
            onNodeWithText("Gateway timeout").assertIsDisplayed()
            onNodeWithText("Retry").assertIsDisplayed().performClick()
            assertEquals(1, retryClicks)
        }

    @Test
    fun containersGoldenScreenRendersContainersAndInteractions() =
        runComposeUiTest {
            val page = readGoldenPage("containers_screen.json")
            val capturedActions = mutableListOf<ActionRef>()
            var loadMoreClicks = 0

            setContent {
                ContainersTestHost(
                    page = page,
                    onCapturedAction = capturedActions::add,
                    onLoadMore = { loadMoreClicks++ },
                )
            }

            verifyHeaderCarouselAndGrid()
            verifyTabsAndAccordion()
            verifyFormControlsAndLoadMore(page.snippets.size) {
                assertEquals(1, loadMoreClicks)
            }
            verifyBottomSheetOpenAndDismiss()

            assertTrue(capturedActions.any { it.type == ActionTypes.OPEN_DIALOG })
            assertTrue(capturedActions.any { it.type == ActionTypes.DISMISS_OVERLAY })
            assertTrue(capturedActions.any { it.type == ActionTypes.SAVE_STATE })
        }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun ContainersTestHost(
        page: PageResponse,
        onCapturedAction: (ActionRef) -> Unit,
        onLoadMore: () -> Unit,
    ) {
        val registry = remember { BuiltInSnippetModule.createDefaultRegistry() }
        val decoders = remember { BuiltInActionDecoders.createDefaultRegistry() }
        DesignTheme(darkTheme = false, reducedMotion = true) {
            val formValues = remember { mutableStateMapOf<String, String>() }
            var activeOverlay by remember { mutableStateOf<ActionOutcome.OverlayRequested?>(null) }

            SduiScreen(
                state =
                    LayoutUiState.Ready(
                        page = page,
                        formValues = formValues.toMap(),
                        activeOverlay = activeOverlay,
                    ),
                snippetRegistry = registry,
                onAction = { action ->
                    onCapturedAction(action)
                    when (action.type) {
                        ActionTypes.OPEN_DIALOG -> {
                            val decoded = decoders.decode(action.type, action.payload)
                            val payload = (decoded as? AppResult.Success)?.value as? OpenDialogPayload
                            if (payload != null) {
                                activeOverlay =
                                    ActionOutcome.OverlayRequested(
                                        dialogId = payload.dialogId,
                                        title = payload.title,
                                        message = payload.message,
                                        dismissible = payload.dismissible,
                                        positiveAction = payload.positiveAction,
                                        negativeAction = payload.negativeAction,
                                        presentation = payload.presentation,
                                    )
                            }
                        }

                        ActionTypes.DISMISS_OVERLAY -> {
                            activeOverlay = null
                        }
                    }
                },
                onFormValueChange = { fieldId, value -> formValues[fieldId] = value },
                onDismissOverlay = { activeOverlay = null },
                onLoadMore = onLoadMore,
            )
        }
    }

    private fun ComposeUiTest.verifyHeaderCarouselAndGrid() {
        onNodeWithText("Container Showcase").assertIsDisplayed()
        onNodeWithText("AC").assertIsDisplayed()
        onNodeWithText("SDUI Layout Containers").assertIsDisplayed()

        onNode(verticalListMatcher).performScrollToIndex(1)
        onNodeWithText("Featured Carousel").assertIsDisplayed()
        onNodeWithText("Carousel Card One").assertIsDisplayed()

        onNode(verticalListMatcher).performScrollToIndex(2)
        onNodeWithText("Telemetry Grid").assertIsDisplayed()
        onNodeWithText("Active Snippets").assertIsDisplayed()
        onNodeWithText("Atoms Ready").assertIsDisplayed()
    }

    private fun ComposeUiTest.verifyTabsAndAccordion() {
        onNode(verticalListMatcher).performScrollToIndex(3)
        onNodeWithText("Composable Containers").assertIsDisplayed()
        onNodeWithText("Skeletons").performClick()
        onNodeWithText("Skeleton Loading Preview").assertIsDisplayed()
        onNodeWithText("Overview").performClick()
        onNodeWithText("Composable Containers").assertIsDisplayed()

        onNode(verticalListMatcher).performScrollToIndex(4)
        onNodeWithText("Preview Bottom Sheet").assertIsDisplayed()
        onNodeWithText("Overlay & Sheet Triggers").performClick()
        onNodeWithText("Preview Bottom Sheet").assertDoesNotExist()
        onNodeWithText("Overlay & Sheet Triggers").performClick()
        onNodeWithText("Preview Bottom Sheet").assertIsDisplayed()
    }

    private fun ComposeUiTest.verifyFormControlsAndLoadMore(
        loadMoreIndex: Int,
        assertLoadMoreClicked: () -> Unit,
    ) {
        onNode(verticalListMatcher).performScrollToIndex(6)
        onNodeWithContentDescription("Compact Container Spacing, Reduces inner card padding to 8dp")
            .assertIsOn()
            .performClick()
            .assertIsOff()
        onNodeWithContentDescription(
            "Enable Strict Schema Mode, required, Reject unrecognized snippet envelopes in preview",
        ).assertIsOff()
            .performClick()
            .assertIsOn()
        onNodeWithText("2 cols").assertIsDisplayed()
        onNodeWithContentDescription("Grid Density")
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress ->
                setProgress(4f)
            }
        onNodeWithText("4 cols").assertIsDisplayed()

        onNode(verticalListMatcher).performScrollToIndex(loadMoreIndex)
        onNodeWithText("Load more").assertIsDisplayed().performClick()
        assertLoadMoreClicked()
    }

    private fun ComposeUiTest.verifyBottomSheetOpenAndDismiss() {
        onNode(verticalListMatcher).performScrollToIndex(0)
        onNodeWithText("Options").performClick()
        onNodeWithText("Container Layout Options").assertIsDisplayed()
        onNodeWithText("OK").performClick()
        onNodeWithText("Container Layout Options").assertDoesNotExist()
    }

    @Test
    fun storeHomeGoldenScreenSupportsLiveCartDockTabSwitchAndOrderCelebration() =
        runComposeUiTest {
            val page = readGoldenPage("store_home.json")

            setContent {
                StoreHomeTestHost(page = page)
            }

            onNodeWithText("Abeta Fresh").assertIsDisplayed()
            onNodeWithText("2 items · $8.48").assertIsDisplayed()

            onNode(verticalListMatcher).performScrollToIndex(2)
            onNodeWithText("Fresh Avocados").assertIsDisplayed()

            onAllNodesWithContentDescription("Increase quantity")[0].performClick()
            onNodeWithText("3 items · $11.97").assertIsDisplayed()

            onNodeWithText("Dairy & Bread").performClick()
            onNodeWithText("Artisan Sourdough").assertIsDisplayed()

            onNodeWithText("Place Express Order").performClick()
            onNodeWithText("Abeta Fresh · 8-Min Express Delivery to Home · Free delivery applied.").assertIsDisplayed()
            onNodeWithText("OK").performClick()

            onNodeWithText("Order Confirmed!").assertIsDisplayed()
            onNodeWithText("Abeta Fresh rider arriving in 8 minutes").assertIsDisplayed()
            onNodeWithContentDescription("Dismiss celebration").performClick()
            onNodeWithText("Order Confirmed!").assertDoesNotExist()
        }

    @Test
    fun offlineBannerRendersOnReadyScreenWhenOffline() =
        runComposeUiTest {
            val storePage = readGoldenPage("store_home.json")
            val registry = BuiltInSnippetModule.createDefaultRegistry()

            setContent {
                DesignTheme(darkTheme = false, reducedMotion = true) {
                    SduiScreen(
                        state =
                            LayoutUiState.Ready(
                                page = storePage,
                                isOffline = true,
                                pendingOfflineActions = 2,
                            ),
                        snippetRegistry = registry,
                        onAction = {},
                        onFormValueChange = { _, _ -> },
                    )
                }
            }

            onNodeWithTag("sdui_offline_banner")
                .assertIsDisplayed()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            onNodeWithText("Offline · 2 pending change(s) will sync automatically").assertIsDisplayed()
            onNodeWithText("Offers & Delivery Promise").assertIsDisplayed()
        }

    @Test
    fun toastBannerExposesPoliteLiveRegionSemantics() =
        runComposeUiTest {
            val storePage = readGoldenPage("store_home.json")
            val registry = BuiltInSnippetModule.createDefaultRegistry()

            setContent {
                DesignTheme(darkTheme = false, reducedMotion = true) {
                    SduiScreen(
                        state =
                            LayoutUiState.Ready(
                                page = storePage,
                                activeToast =
                                    ActionOutcome.ToastRequested(
                                        message = "Saved to cart",
                                        variant = "success",
                                    ),
                            ),
                        snippetRegistry = registry,
                        onAction = {},
                        onFormValueChange = { _, _ -> },
                    )
                }
            }

            onNode(
                SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite),
            ).assertIsDisplayed()
            onNodeWithText("Saved to cart").assertIsDisplayed()
        }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun StoreHomeTestHost(page: PageResponse) {
        val registry = remember { BuiltInSnippetModule.createDefaultRegistry() }
        val decoders = remember { BuiltInActionDecoders.createDefaultRegistry() }
        DesignTheme(darkTheme = false, reducedMotion = true) {
            val formValues = remember { mutableStateMapOf<String, String>() }
            var activeOverlay by remember { mutableStateOf<ActionOutcome.OverlayRequested?>(null) }
            var activeFeedback by remember { mutableStateOf<ActionOutcome.FeedbackTriggered?>(null) }
            var feedbackSequence by remember { mutableStateOf(0L) }

            SduiScreen(
                state =
                    LayoutUiState.Ready(
                        page = page,
                        formValues = formValues.toMap(),
                        activeOverlay = activeOverlay,
                        activeFeedback = activeFeedback,
                        feedbackSequence = feedbackSequence,
                    ),
                snippetRegistry = registry,
                onAction = { action ->
                    when (action.type) {
                        ActionTypes.OPEN_DIALOG -> {
                            decodeOpenOverlay(decoders, action)?.let { activeOverlay = it }
                        }

                        ActionTypes.DISMISS_OVERLAY -> {
                            activeOverlay = null
                        }

                        ActionTypes.CALL_API -> {
                            activeOverlay = null
                            feedbackSequence += 1L
                            activeFeedback =
                                ActionOutcome.FeedbackTriggered(
                                    effect = FeedbackEffect.CELEBRATION,
                                    title = "Order Confirmed!",
                                    subtitle = "Abeta Fresh rider arriving in 8 minutes",
                                )
                        }

                        ActionTypes.BATCH -> {
                            feedbackSequence += 1L
                            activeFeedback =
                                ActionOutcome.FeedbackTriggered(
                                    effect = FeedbackEffect.CART_BOUNCE,
                                    targetId = "floating_cart_dock",
                                )
                        }
                    }
                },
                onFormValueChange = { fieldId, value -> formValues[fieldId] = value },
                onDismissOverlay = { activeOverlay = null },
                onDismissFeedback = { activeFeedback = null },
            )
        }
    }

    private fun decodeOpenOverlay(
        decoders: dev.abeta.kmp.actions.codec.ActionDecoderRegistry,
        action: ActionRef,
    ): ActionOutcome.OverlayRequested? {
        val decoded = decoders.decode(action.type, action.payload)
        val payload = (decoded as? AppResult.Success)?.value as? OpenDialogPayload ?: return null
        return ActionOutcome.OverlayRequested(
            dialogId = payload.dialogId,
            title = payload.title,
            message = payload.message,
            dismissible = payload.dismissible,
            positiveAction = payload.positiveAction,
            negativeAction = payload.negativeAction,
            presentation = payload.presentation,
        )
    }
}
