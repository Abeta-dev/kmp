package dev.abeta.kmp.layout.snippet

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.abeta.kmp.actions.handler.ActionOutcome
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.CheckboxAtom
import dev.abeta.kmp.contracts.ComposedBlockSnippetData
import dev.abeta.kmp.contracts.ComposedNode
import dev.abeta.kmp.contracts.ComposedNodeKind
import dev.abeta.kmp.contracts.HeaderSnippetData
import dev.abeta.kmp.contracts.ImageAtom
import dev.abeta.kmp.contracts.LayoutConfig
import dev.abeta.kmp.contracts.MediaPagerAtom
import dev.abeta.kmp.contracts.OverlayPresentation
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.ResponsiveColumnsSpec
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SliderAtom
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.SwitchAtom
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.contracts.TextFieldAtom
import dev.abeta.kmp.contracts.TrackingContract
import dev.abeta.kmp.layout.engine.LayoutFormSeeder
import dev.abeta.kmp.layout.engine.LayoutUiState
import dev.abeta.kmp.layout.ui.SduiScreen
import dev.abeta.kmp.state.session.InMemorySessionStore
import dev.abeta.kmp.tokens.DesignTheme
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ComposedExpressMoleculesComposeTest {
    @Test
    fun resolveChildBoxAlignmentSupportsAllNinePositionsAndFallback() {
        assertEquals(Alignment.TopStart, resolveChildBoxAlignment("top_start"))
        assertEquals(Alignment.TopCenter, resolveChildBoxAlignment("top_center"))
        assertEquals(Alignment.TopEnd, resolveChildBoxAlignment("top_end"))
        assertEquals(Alignment.CenterStart, resolveChildBoxAlignment("center_start"))
        assertEquals(Alignment.Center, resolveChildBoxAlignment("center"))
        assertEquals(Alignment.CenterEnd, resolveChildBoxAlignment("center_end"))
        assertEquals(Alignment.BottomStart, resolveChildBoxAlignment("bottom_start"))
        assertEquals(Alignment.BottomCenter, resolveChildBoxAlignment("bottom_center"))
        assertEquals(Alignment.BottomEnd, resolveChildBoxAlignment("bottom_end"))
        assertEquals(Alignment.Center, resolveChildBoxAlignment("unknown", fallback = Alignment.Center))
        assertEquals(Alignment.BottomEnd, resolveChildBoxAlignment(null, fallback = Alignment.BottomEnd))
    }

    @Test
    fun layoutFormSeederSeedsTextFieldSwitchCheckboxAndSliderInsideComposedTrees() {
        val page = buildExpressPage(listOf(buildExpressControlsBlock()))
        val seeded = LayoutFormSeeder.extractInitialFormValues(page, InMemorySessionStore())

        assertEquals("Leave with guard", seeded["express_note"])
        assertEquals("true", seeded["contactless_drop"])
        assertEquals("false", seeded["include_cutlery"])
        assertEquals("3.0", seeded["express_tip"])
    }

    @Test
    fun composedEngineRendersScrollRowGridMediaPagerControlsAndBoxAlignments() =
        runComposeUiTest {
            val capturedActions = mutableListOf<ActionRef>()
            val capturedNodeImpressions = mutableListOf<Pair<String, TrackingContract>>()
            val observedFormUpdates = mutableMapOf<String, String>()
            val page =
                buildExpressPage(
                    listOf(
                        buildExpressShowcaseBlock(),
                        buildExpressControlsBlock(),
                    ),
                )
            val seeded = LayoutFormSeeder.extractInitialFormValues(page, InMemorySessionStore())

            setContent {
                val registry = remember { BuiltInSnippetModule.createDefaultRegistry() }
                val formValues = remember { mutableStateMapOf<String, String>().apply { putAll(seeded) } }
                DesignTheme(darkTheme = false, reducedMotion = true) {
                    SduiScreen(
                        state = LayoutUiState.Ready(page = page, formValues = formValues.toMap()),
                        snippetRegistry = registry,
                        onAction = capturedActions::add,
                        onFormValueChange = { fieldId, value ->
                            formValues[fieldId] = value
                            observedFormUpdates[fieldId] = value
                        },
                        onNodeImpression = { nodeKey, tracking ->
                            capturedNodeImpressions.add(nodeKey to tracking)
                        },
                    )
                }
            }

            verifyShowcaseNodes(capturedActions, capturedNodeImpressions)
            verifyComposedInputControls(observedFormUpdates)
        }

    private fun ComposeUiTest.verifyShowcaseNodes(
        capturedActions: List<ActionRef>,
        capturedNodeImpressions: List<Pair<String, TrackingContract>>,
    ) {
        onNodeWithTag("composed_node_express_scroll_row").assertIsDisplayed()
        onNodeWithText("Express Fruits").assertIsDisplayed()
        onNodeWithText("Fresh Dairy").assertIsDisplayed()

        onNodeWithTag("composed_node_express_grid").assertIsDisplayed()
        onNodeWithText("Tile A").assertIsDisplayed()
        onNodeWithText("Tile B").assertIsDisplayed()
        onNodeWithText("Tile C").assertIsDisplayed()

        onNodeWithTag("media_pager_hero_carousel").assertIsDisplayed()

        val topStartBounds = onNodeWithTag("composed_node_badge_top_start").getUnclippedBoundsInRoot()
        val bottomEndBounds = onNodeWithTag("composed_node_badge_bottom_end").getUnclippedBoundsInRoot()
        assertTrue(bottomEndBounds.left > topStartBounds.left)
        assertTrue(bottomEndBounds.top > topStartBounds.top)

        assertTrue(
            capturedNodeImpressions.any { it.first == "express_deal_card" && it.second.eventName == "deal_seen" },
        )

        onNodeWithTag("composed_node_express_deal_card").performClick()
        val mergedClick = capturedActions.last()
        assertEquals("open_deal", mergedClick.id)
        assertEquals("deal_card_clicked", assertNotNull(mergedClick.tracking).eventName)

        onNodeWithTag("composed_node_track_only_chip").performClick()
        val trackOnlyAction = capturedActions.last()
        assertEquals(ActionTypes.TRACK_EVENT, trackOnlyAction.type)
        assertEquals("chip_tapped", assertNotNull(trackOnlyAction.tracking).eventName)
    }

    private fun ComposeUiTest.verifyComposedInputControls(observedFormUpdates: Map<String, String>) {
        onNodeWithTag("composed_node_note_input").performScrollTo().assertIsDisplayed()
        onNode(hasSetTextAction()).performTextInput("!")
        assertTrue(observedFormUpdates["express_note"]?.contains("!") == true)

        onNodeWithContentDescription("Contactless Delivery")
            .performScrollTo()
            .assertIsOn()
            .performClick()
            .assertIsOff()
        assertEquals("false", observedFormUpdates["contactless_drop"])

        onNodeWithContentDescription("Include Cutlery")
            .performScrollTo()
            .assertIsOff()
            .performClick()
            .assertIsOn()
        assertEquals("true", observedFormUpdates["include_cutlery"])

        onNodeWithContentDescription("Rider Tip")
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { setProgress -> setProgress(5f) }
        assertEquals("5.0", observedFormUpdates["express_tip"])
    }

    @Test
    fun stickyHeaderAndRichBottomSheetSnippetsRenderInteractively() =
        runComposeUiTest {
            val capturedActions = mutableListOf<ActionRef>()
            val capturedImpressions = mutableListOf<String>()
            val (page, overlay) = buildStickyAndSheetFixture()

            setContent {
                val registry = remember { BuiltInSnippetModule.createDefaultRegistry() }
                val activeOverlay = remember { mutableStateOf<ActionOutcome.OverlayRequested?>(overlay) }
                DesignTheme(darkTheme = false, reducedMotion = true) {
                    SduiScreen(
                        state = LayoutUiState.Ready(page = page, activeOverlay = activeOverlay.value),
                        snippetRegistry = registry,
                        onAction = capturedActions::add,
                        onFormValueChange = { _, _ -> },
                        onDismissOverlay = { activeOverlay.value = null },
                        onSnippetImpression = { env -> capturedImpressions.add(env.snippetId) },
                    )
                }
            }

            onNodeWithTag("sdui_sticky_snippet_sticky_location_bar").assertIsDisplayed()
            onNodeWithText("Delivering in 8 mins").assertIsDisplayed()
            onNodeWithTag("overlay_snippets_express_perk_sheet").assertIsDisplayed()
            onNodeWithText("Unlock Express Free Delivery").assertIsDisplayed()
            onNodeWithText("Apply Perk").performClick()

            assertEquals("apply_perk", capturedActions.last().id)
            assertTrue("sheet_express_card" in capturedImpressions)
        }

    private fun buildStickyAndSheetFixture(): Pair<PageResponse, ActionOutcome.OverlayRequested> {
        val stickyHeader =
            SnippetEnvelope(
                snippetId = "sticky_location_bar",
                snippetType = SnippetTypes.HEADER,
                sticky = true,
                data =
                    SduiJson.encodeToJsonElement(
                        HeaderSnippetData.serializer(),
                        HeaderSnippetData(title = TextAtom(text = "Delivering in 8 mins")),
                    ) as JsonObject,
            )
        val sheetBlock =
            ComposedBlockSnippetData(
                blockId = "sheet_offer_block",
                root =
                    ComposedNode(
                        nodeId = "sheet_root",
                        kind = ComposedNodeKind.COLUMN,
                        spacingDp = 8,
                        children =
                            listOf(
                                ComposedNode(
                                    kind = ComposedNodeKind.TEXT,
                                    text = TextAtom(text = "Unlock Express Free Delivery"),
                                ),
                                ComposedNode(
                                    kind = ComposedNodeKind.BUTTON,
                                    button =
                                        ButtonAtom(
                                            id = "btn_apply_perk",
                                            label = "Apply Perk",
                                            onClick = ActionRef(id = "apply_perk", type = ActionTypes.SHOW_TOAST),
                                        ),
                                ),
                            ),
                    ),
            )
        val sheetSnippet =
            SnippetEnvelope(
                snippetId = "sheet_express_card",
                snippetType = SnippetTypes.COMPOSED_BLOCK,
                tracking = TrackingContract(eventName = "sheet_card_impression"),
                data = SduiJson.encodeToJsonElement(ComposedBlockSnippetData.serializer(), sheetBlock) as JsonObject,
            )
        val overlay =
            ActionOutcome.OverlayRequested(
                dialogId = "express_perk_sheet",
                title = "Express Delivery Perks",
                presentation = OverlayPresentation.BOTTOM_SHEET,
                snippets = listOf(sheetSnippet),
            )
        val page = PageResponse(pageId = "express-sticky-sheet", snippets = listOf(stickyHeader))
        return page to overlay
    }

    private fun buildExpressPage(snippets: List<SnippetEnvelope>): PageResponse =
        PageResponse(pageId = "express-molecules", snippets = snippets)

    private fun buildExpressShowcaseBlock(): SnippetEnvelope {
        val block =
            ComposedBlockSnippetData(
                blockId = "express_showcase",
                root =
                    ComposedNode(
                        nodeId = "showcase_root",
                        kind = ComposedNodeKind.COLUMN,
                        spacingDp = 12,
                        children =
                            listOf(
                                buildScrollRowNode(),
                                buildResponsiveGridNode(),
                                buildAlignedMediaBoxNode(),
                                buildTrackedCardNode(),
                            ),
                    ),
            )
        return SnippetEnvelope(
            snippetId = "cb_express_showcase",
            snippetType = SnippetTypes.COMPOSED_BLOCK,
            data = SduiJson.encodeToJsonElement(ComposedBlockSnippetData.serializer(), block) as JsonObject,
        )
    }

    private fun buildScrollRowNode(): ComposedNode =
        ComposedNode(
            nodeId = "express_scroll_row",
            kind = ComposedNodeKind.SCROLL_ROW,
            spacingDp = 8,
            children =
                listOf(
                    ComposedNode(kind = ComposedNodeKind.TAG, tag = TagAtom(label = "Express Fruits")),
                    ComposedNode(kind = ComposedNodeKind.TAG, tag = TagAtom(label = "Fresh Dairy")),
                    ComposedNode(
                        nodeId = "track_only_chip",
                        kind = ComposedNodeKind.TAG,
                        tag = TagAtom(label = "Track Only"),
                        clickTracking = TrackingContract(eventName = "chip_tapped", properties = mapOf("id" to "c1")),
                    ),
                ),
        )

    private fun buildResponsiveGridNode(): ComposedNode =
        ComposedNode(
            nodeId = "express_grid",
            kind = ComposedNodeKind.GRID,
            columns = 2,
            columnsByWidth = ResponsiveColumnsSpec(compact = 2, medium = 3, expanded = 3),
            spacingDp = 8,
            children =
                listOf(
                    ComposedNode(kind = ComposedNodeKind.TEXT, text = TextAtom(text = "Tile A")),
                    ComposedNode(kind = ComposedNodeKind.TEXT, text = TextAtom(text = "Tile B")),
                    ComposedNode(kind = ComposedNodeKind.TEXT, text = TextAtom(text = "Tile C")),
                ),
        )

    private fun buildAlignedMediaBoxNode(): ComposedNode =
        ComposedNode(
            nodeId = "aligned_hero_box",
            kind = ComposedNodeKind.BOX,
            layout = LayoutConfig(fillMaxWidth = true),
            children =
                listOf(
                    ComposedNode(
                        kind = ComposedNodeKind.MEDIA_PAGER,
                        mediaPager =
                            MediaPagerAtom(
                                id = "hero_carousel",
                                heightDp = 120,
                                items =
                                    listOf(
                                        ImageAtom(
                                            url = "https://cdn.abeta.dev/fresh-1.png",
                                            contentDescription = "Hero 1",
                                        ),
                                        ImageAtom(
                                            url = "https://cdn.abeta.dev/fresh-2.png",
                                            contentDescription = "Hero 2",
                                        ),
                                    ),
                            ),
                    ),
                    ComposedNode(
                        nodeId = "badge_top_start",
                        kind = ComposedNodeKind.TAG,
                        boxAlignment = "top_start",
                        tag = TagAtom(label = "8 MIN"),
                    ),
                    ComposedNode(
                        nodeId = "badge_bottom_end",
                        kind = ComposedNodeKind.TAG,
                        boxAlignment = "bottom_end",
                        tag = TagAtom(label = "20% OFF"),
                    ),
                ),
        )

    private fun buildTrackedCardNode(): ComposedNode =
        ComposedNode(
            nodeId = "express_deal_card",
            kind = ComposedNodeKind.CARD,
            onClick = ActionRef(id = "open_deal", type = ActionTypes.NAVIGATE),
            tracking = TrackingContract(eventName = "deal_seen", properties = mapOf("deal" to "avocado")),
            clickTracking = TrackingContract(eventName = "deal_card_clicked", properties = mapOf("deal" to "avocado")),
            children =
                listOf(
                    ComposedNode(kind = ComposedNodeKind.TEXT, text = TextAtom(text = "Hass Avocados 2pk")),
                ),
        )

    private fun buildExpressControlsBlock(): SnippetEnvelope {
        val block =
            ComposedBlockSnippetData(
                blockId = "express_controls",
                root =
                    ComposedNode(
                        nodeId = "controls_root",
                        kind = ComposedNodeKind.COLUMN,
                        spacingDp = 8,
                        children =
                            listOf(
                                ComposedNode(
                                    nodeId = "note_input",
                                    kind = ComposedNodeKind.TEXT_FIELD,
                                    textField =
                                        TextFieldAtom(
                                            fieldId = "express_note",
                                            label = "Delivery Note",
                                            initialValue = "Leave with guard",
                                        ),
                                ),
                                ComposedNode(
                                    kind = ComposedNodeKind.SWITCH,
                                    switch =
                                        SwitchAtom(
                                            fieldId = "contactless_drop",
                                            label = "Contactless Delivery",
                                            checked = true,
                                        ),
                                ),
                                ComposedNode(
                                    kind = ComposedNodeKind.CHECKBOX,
                                    checkbox =
                                        CheckboxAtom(
                                            fieldId = "include_cutlery",
                                            label = "Include Cutlery",
                                            checked = false,
                                        ),
                                ),
                                ComposedNode(
                                    kind = ComposedNodeKind.SLIDER,
                                    slider =
                                        SliderAtom(
                                            fieldId = "express_tip",
                                            label = "Rider Tip",
                                            value = 3f,
                                            minValue = 0f,
                                            maxValue = 10f,
                                            unitLabel = "USD",
                                        ),
                                ),
                            ),
                    ),
            )
        return SnippetEnvelope(
            snippetId = "cb_express_controls",
            snippetType = SnippetTypes.COMPOSED_BLOCK,
            data = SduiJson.encodeToJsonElement(ComposedBlockSnippetData.serializer(), block) as JsonObject,
        )
    }
}
