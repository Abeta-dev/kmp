package dev.abeta.kmp.layout.snippet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ChipGroupSnippetData
import dev.abeta.kmp.contracts.FilterChipItem
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SearchBarSnippetData
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.SwipeCardItem
import dev.abeta.kmp.contracts.SwipeDeckSnippetData
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.layout.engine.LayoutUiState
import dev.abeta.kmp.layout.ui.SduiScreen
import dev.abeta.kmp.tokens.DesignTheme
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class InteractiveSnippetsComposeTest {
    @Test
    fun swipeDeckSupportsActionButtonsUndoExhaustionAndReset() =
        runComposeUiTest {
            val capturedActions = mutableListOf<ActionRef>()
            val deckData = sampleSwipeDeckData()

            setContent {
                SingleSnippetHost(
                    envelope = snippetEnvelope("deck_1", SnippetTypes.SWIPE_DECK, deckData),
                    onAction = capturedActions::add,
                )
            }

            onNodeWithTag("swipe_deck_discover").assertIsDisplayed()
            onNodeWithTag("swipe_card_c1").assertIsDisplayed()
            onNodeWithText("Maya, 28").assertIsDisplayed()

            onNodeWithTag("swipe_btn_like").performClick()
            onNodeWithTag("swipe_card_c2").assertIsDisplayed()
            assertEquals("like_c1", capturedActions.last().id)

            onNodeWithTag("swipe_btn_undo").performClick()
            onNodeWithTag("swipe_card_c1").assertIsDisplayed()
            assertEquals("deck_undo", capturedActions.last().id)

            onNodeWithTag("swipe_btn_super_like").performClick()
            assertEquals("deck_super", capturedActions.last().id)
            onNodeWithTag("swipe_card_c2").assertIsDisplayed()

            onNodeWithTag("swipe_btn_nope").performClick()
            assertEquals("nope_c2", capturedActions.last().id)
            onNodeWithText("All caught up").assertIsDisplayed()
            onNodeWithText("Reset Deck").assertIsDisplayed().performClick()
            onNodeWithTag("swipe_card_c1").assertIsDisplayed()
        }

    @Test
    fun swipeDeckRespondsToPointerRightSwipeGesture() =
        runComposeUiTest {
            val capturedActions = mutableListOf<ActionRef>()
            val deckData = sampleSwipeDeckData()

            setContent {
                SingleSnippetHost(
                    envelope = snippetEnvelope("deck_gesture", SnippetTypes.SWIPE_DECK, deckData),
                    onAction = capturedActions::add,
                )
            }

            onNodeWithTag("swipe_card_c1").performTouchInput {
                swipeRight(startX = left + 20f, endX = right - 10f, durationMillis = 150)
            }
            waitForIdle()

            onNodeWithTag("swipe_card_c2").assertIsDisplayed()
            assertTrue(capturedActions.any { it.id == "like_c1" })
        }

    @Test
    fun searchBarResolvesQueryPlaceholdersAndClearsViaOnClear() =
        runComposeUiTest {
            val capturedActions = mutableListOf<ActionRef>()
            val formState = mutableMapOf<String, String>()
            val searchData =
                SearchBarSnippetData(
                    searchId = "catalog_q",
                    placeholder = "Search fresh produce...",
                    trailingIcon = IconAtom(name = "settings", contentDescription = "Filter options"),
                    submitLabel = "Search",
                    clearable = true,
                    onSubmit =
                        ActionRef(
                            id = "submit_search",
                            type = "navigate",
                            payload =
                                buildJsonObject {
                                    put("route", "/catalog?q={{query}}")
                                    put("raw_query", "{{query}}")
                                },
                        ),
                    onClear = ActionRef(id = "clear_search", type = "refresh_page"),
                    onTrailingClick = ActionRef(id = "open_filters", type = "open_dialog"),
                )

            setContent {
                SingleSnippetHost(
                    envelope = snippetEnvelope("search_1", SnippetTypes.SEARCH_BAR, searchData),
                    onAction = capturedActions::add,
                    onFormUpdate = { k, v -> formState[k] = v },
                )
            }

            onNodeWithTag("search_bar_catalog_q").assertIsDisplayed()
            onNodeWithTag("search_clear_catalog_q").assertDoesNotExist()
            onNodeWithTag("search_input_catalog_q").performTextInput("  organic apples  ")
            onNodeWithTag("search_clear_catalog_q").assertIsDisplayed()

            onNodeWithTag("search_submit_catalog_q").performClick()
            val submitted = capturedActions.last()
            assertEquals("submit_search", submitted.id)
            assertEquals("/catalog?q=organic%20apples", submitted.payload["route"]?.jsonPrimitive?.content)
            assertEquals("organic apples", submitted.payload["raw_query"]?.jsonPrimitive?.content)

            onNodeWithContentDescription("Filter options").performClick()
            assertEquals("open_filters", capturedActions.last().id)

            onNodeWithTag("search_clear_catalog_q").performClick()
            assertEquals("", formState["catalog_q"])
            assertEquals("clear_search", capturedActions.last().id)
        }

    @Test
    fun searchBarClearFallsBackToOnSubmitWithEmptyQueryWhenOnClearOmitted() =
        runComposeUiTest {
            val capturedActions = mutableListOf<ActionRef>()
            val searchData =
                SearchBarSnippetData(
                    searchId = "quick_q",
                    query = "avocado",
                    clearable = true,
                    onSubmit =
                        ActionRef(
                            id = "submit_empty",
                            type = "call_api",
                            payload = buildJsonObject { put("endpoint", "/api/v1/items?q={{query}}") },
                        ),
                )

            setContent {
                SingleSnippetHost(
                    envelope = snippetEnvelope("search_fallback", SnippetTypes.SEARCH_BAR, searchData),
                    onAction = capturedActions::add,
                )
            }

            onNodeWithTag("search_clear_quick_q").assertIsDisplayed().performClick()
            val fallback = capturedActions.last()
            assertEquals("submit_empty", fallback.id)
            assertEquals("/api/v1/items?q=", fallback.payload["endpoint"]?.jsonPrimitive?.content)
        }

    @Test
    fun chipGroupSupportsSingleSelectMultiSelectAndServerRefresh() =
        runComposeUiTest {
            val capturedActions = mutableListOf<ActionRef>()
            val formState = mutableMapOf<String, String>()
            var multiChips by mutableStateOf(
                ChipGroupSnippetData(
                    groupId = "categories",
                    title = TextAtom(text = "Categories"),
                    multiSelect = true,
                    chips =
                        listOf(
                            FilterChipItem(
                                id = "all",
                                label = "All",
                                selected = true,
                                badge = "24",
                                onClick = ActionRef(id = "click_all", type = "refresh_page"),
                            ),
                            FilterChipItem(
                                id = "produce",
                                label = "Produce",
                                selected = false,
                                icon = IconAtom(name = "star"),
                                onClick = ActionRef(id = "click_produce", type = "refresh_page"),
                            ),
                        ),
                ),
            )

            setContent {
                SingleSnippetHost(
                    envelope = snippetEnvelope("chips_1", SnippetTypes.CHIP_GROUP, multiChips),
                    onAction = capturedActions::add,
                    onFormUpdate = { k, v -> formState[k] = v },
                )
            }

            onNodeWithTag("chip_group_categories").assertIsDisplayed()
            onNodeWithText("Categories").assertIsDisplayed()
            onNodeWithTag("chip_all").assertIsSelected()
            onNodeWithTag("chip_produce").assertIsNotSelected()

            onNodeWithTag("chip_produce").performClick()
            onNodeWithTag("chip_all").assertIsSelected()
            onNodeWithTag("chip_produce").assertIsSelected()
            assertEquals("all,produce", formState["chip_categories"])
            assertEquals("click_produce", capturedActions.last().id)

            // Simulate server refresh overriding selection back to single chip.
            multiChips =
                multiChips.copy(
                    multiSelect = false,
                    chips =
                        multiChips.chips.map { chip ->
                            chip.copy(selected = chip.id == "produce")
                        },
                )
            waitForIdle()

            onNodeWithTag("chip_all").assertIsNotSelected()
            onNodeWithTag("chip_produce").assertIsSelected()

            onNodeWithTag("chip_all").performClick()
            onNodeWithTag("chip_all").assertIsSelected()
            onNodeWithTag("chip_produce").assertIsNotSelected()
            assertEquals("all", formState["chip_categories"])
        }

    @Test
    fun searchBarEncodesUrlAndFallbackRouteAndClearsUserTypedQueryWithoutInitialQuery() =
        runComposeUiTest {
            val capturedActions = mutableListOf<ActionRef>()
            val searchData =
                SearchBarSnippetData(
                    searchId = "live_q",
                    query = "",
                    clearable = true,
                    onSubmit =
                        ActionRef(
                            id = "search_open",
                            type = "open_url",
                            payload =
                                buildJsonObject {
                                    put("url", "https://abeta.dev/find?q={{query}}")
                                    put("fallback_route", "/search?q={{query}}")
                                },
                        ),
                )

            setContent {
                SingleSnippetHost(
                    envelope = snippetEnvelope("search_url", SnippetTypes.SEARCH_BAR, searchData),
                    onAction = capturedActions::add,
                )
            }

            onNodeWithTag("search_input_live_q").performTextInput("ice cream")
            onNodeWithTag("search_clear_live_q").assertIsDisplayed().performClick()
            val cleared = capturedActions.last()
            assertEquals("https://abeta.dev/find?q=", cleared.payload["url"]?.jsonPrimitive?.content)
            assertEquals("/search?q=", cleared.payload["fallback_route"]?.jsonPrimitive?.content)

            val resolved = resolveQueryPlaceholders(searchData.onSubmit!!, "cold brew")
            assertEquals("https://abeta.dev/find?q=cold%20brew", resolved.payload["url"]?.jsonPrimitive?.content)
            assertEquals("/search?q=cold%20brew", resolved.payload["fallback_route"]?.jsonPrimitive?.content)
        }

    @Test
    fun chipGroupRestoresSelectionFromFormValuesAcrossRemount() =
        runComposeUiTest {
            val registry = BuiltInSnippetModule.createDefaultRegistry()
            val chipsData =
                ChipGroupSnippetData(
                    groupId = "diet",
                    multiSelect = true,
                    chips =
                        listOf(
                            FilterChipItem(id = "all", label = "All", selected = true),
                            FilterChipItem(id = "vegan", label = "Vegan", selected = false),
                        ),
                )
            setContent {
                DesignTheme(darkTheme = false, reducedMotion = true) {
                    SduiScreen(
                        state =
                            LayoutUiState.Ready(
                                page =
                                    PageResponse(
                                        pageId = "chip_restore",
                                        snippets =
                                            listOf(
                                                snippetEnvelope("chips_diet", SnippetTypes.CHIP_GROUP, chipsData),
                                            ),
                                    ),
                                formValues = mapOf("chip_diet" to "vegan"),
                            ),
                        snippetRegistry = registry,
                        onAction = {},
                        onFormValueChange = { _, _ -> },
                    )
                }
            }

            onNodeWithTag("chip_all").assertIsNotSelected()
            onNodeWithTag("chip_vegan").assertIsSelected()
        }

    private fun sampleSwipeDeckData(): SwipeDeckSnippetData =
        SwipeDeckSnippetData(
            deckId = "discover",
            cards =
                listOf(
                    SwipeCardItem(
                        cardId = "c1",
                        title = TextAtom(text = "Maya, 28"),
                        subtitle = TextAtom(text = "Kotlin Multiplatform"),
                        distanceText = TextAtom(text = "2 km"),
                        tags = listOf(TagAtom(label = "Compose")),
                        onLike = ActionRef(id = "like_c1", type = "show_toast"),
                    ),
                    SwipeCardItem(
                        cardId = "c2",
                        title = TextAtom(text = "Liam, 30"),
                        subtitle = TextAtom(text = "Systems Go"),
                        onNope = ActionRef(id = "nope_c2", type = "show_toast"),
                    ),
                ),
            emptyTitle = TextAtom(text = "All caught up"),
            emptyMessage = TextAtom(text = "No more cards nearby."),
            onSuperLike = ActionRef(id = "deck_super", type = "trigger_feedback"),
            onUndo = ActionRef(id = "deck_undo", type = "show_toast"),
        )

    private inline fun <reified T> snippetEnvelope(
        id: String,
        type: String,
        model: T,
    ): SnippetEnvelope =
        SnippetEnvelope(
            snippetId = id,
            snippetType = type,
            data = SduiJson.encodeToJsonElement(serializer<T>(), model) as JsonObject,
        )

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    private fun SingleSnippetHost(
        envelope: SnippetEnvelope,
        onAction: (ActionRef) -> Unit,
        onFormUpdate: (String, String) -> Unit = { _, _ -> },
    ) {
        val registry = remember { BuiltInSnippetModule.createDefaultRegistry() }
        val formValues = remember { mutableStateMapOf<String, String>() }
        DesignTheme(darkTheme = false, reducedMotion = true) {
            SduiScreen(
                state =
                    LayoutUiState.Ready(
                        page = PageResponse(pageId = "interactive_test", snippets = listOf(envelope)),
                        formValues = formValues.toMap(),
                    ),
                snippetRegistry = registry,
                onAction = onAction,
                onFormValueChange = { fieldId, value ->
                    formValues[fieldId] = value
                    onFormUpdate(fieldId, value)
                },
            )
        }
    }
}
