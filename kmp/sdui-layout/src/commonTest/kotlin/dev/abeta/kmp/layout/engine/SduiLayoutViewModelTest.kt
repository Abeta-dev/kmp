package dev.abeta.kmp.layout.engine

import androidx.lifecycle.ViewModelStore
import androidx.savedstate.savedState
import dev.abeta.kmp.actions.codec.ActionDecoderRegistry
import dev.abeta.kmp.actions.codec.BuiltInActionDecoders
import dev.abeta.kmp.actions.handler.ActionOutcome
import dev.abeta.kmp.actions.handler.BuiltInActionHandlers
import dev.abeta.kmp.actions.handler.DefaultActionDispatcher
import dev.abeta.kmp.analytics.RecordingAnalyticsTracker
import dev.abeta.kmp.contracts.FormSnippetData
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.TextFieldAtom
import dev.abeta.kmp.core.error.NotFoundError
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.layout.repository.ScreenRepository
import dev.abeta.kmp.state.session.InMemorySessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SduiLayoutViewModelTest {
    private fun sampleCheckoutPage(): PageResponse {
        val form =
            FormSnippetData(
                formId = "express_checkout",
                fields =
                    listOf(
                        TextFieldAtom(
                            fieldId = "delivery_note",
                            label = "Delivery Note",
                            initialValue = "Leave at door",
                        ),
                        TextFieldAtom(
                            fieldId = "promo_code",
                            label = "Promo Code",
                            initialValue = "",
                        ),
                    ),
            )
        val envelope =
            SnippetEnvelope(
                snippetId = "checkout_form",
                snippetType = SnippetTypes.FORM,
                data = SduiJson.encodeToJsonElement(FormSnippetData.serializer(), form) as JsonObject,
            )
        return PageResponse(
            pageId = "express-checkout",
            title = "Abeta Express Checkout",
            snippets = listOf(envelope),
        )
    }

    private fun buildViewModel(
        scope: CoroutineScope,
        pages: Map<String, PageResponse> = mapOf("express-checkout" to sampleCheckoutPage()),
        onClearedCallback: () -> Unit = {},
    ): Pair<SduiLayoutViewModel, RecordingAnalyticsTracker> {
        val tracker = RecordingAnalyticsTracker()
        val repository =
            ScreenRepository { id ->
                pages[id]?.let { AppResult.Success(it) }
                    ?: AppResult.Failure(NotFoundError(resource = id, message = "Unknown screen '$id'"))
            }
        val dispatcher =
            DefaultActionDispatcher(
                decoderRegistry = BuiltInActionDecoders.registerDefaults(ActionDecoderRegistry.builder()).build(),
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
        val vm =
            SduiLayoutViewModel.create(
                screenRepository = repository,
                actionDispatcher = dispatcher,
                sessionStore = InMemorySessionStore(),
                analyticsTracker = tracker,
                onClearedCallback = onClearedCallback,
                viewModelScope = scope,
            )
        return vm to tracker
    }

    @Test
    fun viewModelStoreClearCancelsScopeAndInvokesClearedCallback() =
        runTest(StandardTestDispatcher()) {
            val vmJob = SupervisorJob()
            val managedScope = CoroutineScope(coroutineContext + vmJob)
            var clearedCount = 0
            val (viewModel, _) =
                buildViewModel(
                    scope = managedScope,
                    onClearedCallback = { clearedCount++ },
                )

            val store = ViewModelStore()
            store.put("sdui_main", viewModel)
            viewModel.loadScreen("express-checkout").join()
            val ready = assertIs<LayoutUiState.Ready>(viewModel.uiState.value)
            assertEquals("express-checkout", ready.page.pageId)
            assertTrue(managedScope.isActive)

            store.clear()
            assertEquals(1, clearedCount)
            assertFalse(managedScope.isActive)
        }

    @Test
    fun savedStateRoundTripRestoresScreenAndEditedFormValuesAfterProcessDeath() =
        runTest(StandardTestDispatcher()) {
            val (firstVm, _) = buildViewModel(scope = this)
            firstVm.loadScreen("express-checkout").join()
            firstVm.onFormFieldChanged("delivery_note", "Ring bell twice")
            firstVm.onFormFieldChanged("promo_code", "EXPRESS20")

            val outState = savedState()
            firstVm.saveStateTo(outState)
            val createdCopy = firstVm.createSavedState()

            val decoded = SduiLayoutViewModel.readSnapshot(outState)
            assertEquals("express-checkout", decoded.screenId)
            assertEquals("Ring bell twice", decoded.formValues["delivery_note"])
            assertEquals("EXPRESS20", decoded.formValues["promo_code"])
            assertEquals(decoded, SduiLayoutViewModel.readSnapshot(createdCopy))

            // Simulate process death: brand new ViewModel + fresh InMemorySessionStore.
            val (restoredVm, restoredTracker) = buildViewModel(scope = TestScope(StandardTestDispatcher()))
            val restoredSnapshot = restoredVm.restoreStateFrom(outState, autoReload = false)
            assertEquals("express-checkout", restoredSnapshot.screenId)
            assertEquals("Ring bell twice", restoredSnapshot.formValues["delivery_note"])
            assertEquals("EXPRESS20", restoredSnapshot.formValues["promo_code"])

            // Reload on current test dispatcher to assert persisted values override defaults.
            val (autoReloadVm, autoTracker) = buildViewModel(scope = this)
            autoReloadVm.restoreStateFrom(outState, autoReload = true)
            advanceUntilIdle()

            val ready = assertIs<LayoutUiState.Ready>(autoReloadVm.uiState.value)
            assertEquals("express-checkout", ready.page.pageId)
            assertEquals("Ring bell twice", ready.formValues["delivery_note"])
            assertEquals("EXPRESS20", ready.formValues["promo_code"])
            assertTrue(autoTracker.events.any { it.name == LayoutAnalyticsHelper.EVENT_PAGE_OPEN })
            assertTrue(restoredTracker.events.isEmpty())
        }
}
