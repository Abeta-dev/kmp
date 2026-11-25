package dev.abeta.kmp.layout.snippet

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.CardSnippetData
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.CustomSlotSnippetData
import dev.abeta.kmp.contracts.GraphCanvasSnippetData
import dev.abeta.kmp.contracts.GraphEdgeSpec
import dev.abeta.kmp.contracts.GraphEdgeStyle
import dev.abeta.kmp.contracts.GraphNodeSpec
import dev.abeta.kmp.contracts.PageResponse
import dev.abeta.kmp.contracts.SduiJson
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.TextAtom
import dev.abeta.kmp.core.platform.FakeAudioCapturePort
import dev.abeta.kmp.core.platform.FakeSpeechToTextPort
import dev.abeta.kmp.core.platform.RecordingDesktopSystemPort
import dev.abeta.kmp.core.platform.UnavailableAudioCapturePort
import dev.abeta.kmp.core.platform.UnavailableSpeechToTextPort
import dev.abeta.kmp.layout.engine.LayoutUiState
import dev.abeta.kmp.layout.platform.PlatformBridgeBundle
import dev.abeta.kmp.layout.ui.SduiScreen
import dev.abeta.kmp.tokens.DesignTheme
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SpatialAndCustomSlotComposeTest {
    @Test
    fun customSlotRendersRegisteredHostViewAndEmitEventMergesExtraPayload() =
        runComposeUiTest {
            val capturedActions = mutableListOf<ActionRef>()
            val capturedFormUpdates = mutableMapOf<String, String>()
            val slotData = sampleRegisteredCustomSlotData()

            setContent {
                SpatialPageHost(
                    page =
                        PageResponse(
                            pageId = "custom_slot_test",
                            snippets = listOf(snippetEnvelope("cs_1", SnippetTypes.CUSTOM_SLOT, slotData)),
                        ),
                    customSlotRegistry = sampleMapCustomSlotRegistry(),
                    onAction = capturedActions::add,
                    onFormUpdate = { k, v -> capturedFormUpdates[k] = v },
                )
            }

            onNodeWithTag("custom_slot_delivery_map").assertIsDisplayed()
            onNodeWithText("Host Map: Order #AF-4092 (all)").assertIsDisplayed()

            onNodeWithText("Select Rider Marker").performClick()

            assertEquals("express", capturedFormUpdates["map_filter"])
            val emitted = capturedActions.single()
            assertEquals("map_marker_action", emitted.id)
            assertEquals("slot_host", emitted.payload["origin"]?.jsonPrimitive?.content)
            assertEquals("rider_42", emitted.payload["marker_id"]?.jsonPrimitive?.content)
            assertEquals("4", emitted.payload["eta_mins"]?.jsonPrimitive?.content)
            onNodeWithText("Host Map: Order #AF-4092 (express)").assertIsDisplayed()
        }

    @Test
    fun customSlotFallsBackToDeclarativeSnippetWhenSlotTypeUnregistered() =
        runComposeUiTest {
            val capturedActions = mutableListOf<ActionRef>()
            val fallbackCard =
                CardSnippetData(
                    title = TextAtom(text = "Interactive Map Unavailable"),
                    description = TextAtom(text = "Graceful declarative SDUI fallback rendered."),
                    primaryButton =
                        ButtonAtom(
                            label = "Open Directions",
                            onClick = ActionRef(id = "fallback_open_maps", type = "open_url"),
                        ),
                )
            val slotData =
                CustomSlotSnippetData(
                    slotId = "unregistered_map",
                    slotType = "native_map_view",
                    fallback = snippetEnvelope("cs_fallback", SnippetTypes.CARD, fallbackCard),
                )

            setContent {
                SpatialPageHost(
                    page =
                        PageResponse(
                            pageId = "custom_slot_fallback_test",
                            snippets = listOf(snippetEnvelope("cs_2", SnippetTypes.CUSTOM_SLOT, slotData)),
                        ),
                    customSlotRegistry = CustomSlotRegistry.Empty,
                    onAction = capturedActions::add,
                )
            }

            onNodeWithText("Interactive Map Unavailable").assertIsDisplayed()
            onNodeWithText("Graceful declarative SDUI fallback rendered.").assertIsDisplayed()
            onNodeWithText("Open Directions").performClick()
            assertEquals("fallback_open_maps", capturedActions.single().id)
        }

    @Test
    fun customSlotShowsSubtleNoticeWhenUnregisteredWithoutFallback() =
        runComposeUiTest {
            val slotData = CustomSlotSnippetData(slotId = "plain_slot", slotType = "gpu_waveform_view")
            setContent {
                SpatialPageHost(
                    page =
                        PageResponse(
                            pageId = "custom_slot_notice_test",
                            snippets = listOf(snippetEnvelope("cs_3", SnippetTypes.CUSTOM_SLOT, slotData)),
                        ),
                    customSlotRegistry = CustomSlotRegistry.Empty,
                    onAction = {},
                )
            }

            onNodeWithTag("custom_slot_unregistered_plain_slot").assertIsDisplayed()
            onNodeWithText("Slot 'gpu_waveform_view' is not registered on this host").assertIsDisplayed()
        }

    @Test
    fun graphCanvasRendersNodesEdgesZoomControlsAndFiresNodeClick() =
        runComposeUiTest {
            val capturedActions = mutableListOf<ActionRef>()
            val graphData = sampleGraphCanvasData()

            setContent {
                SpatialPageHost(
                    page =
                        PageResponse(
                            pageId = "graph_canvas_test",
                            snippets = listOf(snippetEnvelope("gc_1", SnippetTypes.GRAPH_CANVAS, graphData)),
                        ),
                    onAction = capturedActions::add,
                )
            }

            onNodeWithTag("graph_canvas_team_map").assertIsDisplayed()
            onNodeWithText("People Relationship Map").assertIsDisplayed()
            onNodeWithTag("graph_node_maya").assertIsDisplayed()
            onNodeWithTag("graph_node_arjun").assertIsDisplayed()
            onNodeWithTag("graph_node_kabir").assertIsDisplayed()
            onNodeWithTag("graph_edge_label_e_mentors").assertIsDisplayed()
            onNodeWithText("100%").assertIsDisplayed()

            onNodeWithTag("graph_zoom_in_team_map").performClick()
            onNodeWithText("125%").assertIsDisplayed()

            onNodeWithTag("graph_zoom_out_team_map").performClick()
            onNodeWithTag("graph_zoom_out_team_map").performClick()
            onNodeWithText("75%").assertIsDisplayed()

            onNodeWithTag("graph_zoom_reset_team_map").performClick()
            onNodeWithText("100%").assertIsDisplayed()

            onNodeWithTag("graph_node_maya").performClick()
            assertEquals("inspect_maya", capturedActions.last().id)

            onNodeWithTag("graph_edge_label_e_mentors").performClick()
            assertEquals("inspect_mentorship", capturedActions.last().id)
        }

    @Test
    fun graphCanvasHelpersEnrichNodeMovedActionComputeMidpointAndClampZoom() {
        val baseAction =
            ActionRef(
                id = "persist_node",
                type = "call_api",
                payload = buildJsonObject { put("endpoint", "/api/v1/graph/move") },
            )
        val enriched =
            enrichNodeMovedAction(
                action = baseAction,
                canvasId = "topology_canvas",
                nodeId = "node_contracts",
                x = 248.5f,
                y = 162.25f,
            )
        assertEquals("/api/v1/graph/move", enriched.payload["endpoint"]?.jsonPrimitive?.content)
        assertEquals("topology_canvas", enriched.payload["canvas_id"]?.jsonPrimitive?.content)
        assertEquals("node_contracts", enriched.payload["node_id"]?.jsonPrimitive?.content)
        assertEquals(248.5f, enriched.payload["x"]?.jsonPrimitive?.float)
        assertEquals(162.25f, enriched.payload["y"]?.jsonPrimitive?.float)

        val midpoint = computeBezierMidpoint(Offset(100f, 50f), Offset(300f, 150f))
        assertEquals(200f, midpoint.x)
        assertEquals(100f, midpoint.y)

        assertEquals(0.5f, clampGraphZoom(0.2f, minZoom = 0.5f, maxZoom = 2.5f))
        assertEquals(2.5f, clampGraphZoom(4.0f, minZoom = 0.5f, maxZoom = 2.5f))
        assertEquals(1.25f, clampGraphZoom(1.25f, minZoom = 0.5f, maxZoom = 2.5f))
        assertEquals(0.5f, clampGraphZoom(1.0f, minZoom = 0.5f, maxZoom = 0.2f))

        // Pivoting zoom around viewport center keeps the world point under the pivot fixed.
        val zoomedPan =
            computeZoomedPan(
                currentPanPx = Offset(10f, 20f),
                pivotPx = Offset(200f, 100f),
                oldZoom = 1.0f,
                newZoom = 2.0f,
            )
        assertEquals(Offset(-180f, -60f), zoomedPan)
    }

    @Test
    fun customSlotEnforcesMaxNestingDepthGuardAgainstDeepFallbackRecursion() =
        runComposeUiTest {
            var nested: SnippetEnvelope =
                snippetEnvelope(
                    "deep_leaf",
                    SnippetTypes.CARD,
                    CardSnippetData(title = TextAtom(text = "Should Be Clipped")),
                )
            repeat(MAX_SNIPPET_NESTING_DEPTH + 2) { level ->
                nested =
                    snippetEnvelope(
                        "deep_slot_$level",
                        SnippetTypes.CUSTOM_SLOT,
                        CustomSlotSnippetData(
                            slotId = "slot_$level",
                            slotType = "missing_slot_$level",
                            fallback = nested,
                        ),
                    )
            }
            setContent {
                SpatialPageHost(
                    page = PageResponse(pageId = "depth_guard", snippets = listOf(nested)),
                    customSlotRegistry = CustomSlotRegistry.Empty,
                    onAction = {},
                )
            }

            waitForIdle()
            onNodeWithText("Should Be Clipped").assertDoesNotExist()
        }

    @Test
    fun platformBridgeBundleDefaultsWireVoiceAndDesktopPortsCleanly() {
        val testBridges = PlatformBridgeBundle.createTestDefault()
        assertIs<FakeAudioCapturePort>(testBridges.audioCapturePort)
        assertIs<FakeSpeechToTextPort>(testBridges.speechToTextPort)
        assertIs<RecordingDesktopSystemPort>(testBridges.desktopSystemPort)
        assertTrue(testBridges.desktopSystemPort.isSupported)

        val platformBridges = PlatformBridgeBundle.createPlatformDefault()
        assertEquals(UnavailableAudioCapturePort, platformBridges.audioCapturePort)
        assertEquals(UnavailableSpeechToTextPort, platformBridges.speechToTextPort)
        assertNotNull(platformBridges.desktopSystemPort)
    }

    private fun sampleMapCustomSlotRegistry(): CustomSlotRegistry =
        CustomSlotRegistry
            .builder()
            .register("native_map_view") { context ->
                val title = context.initialState["title"]?.jsonPrimitive?.content ?: "Missing"
                val currentFilter = context.formValues["map_filter"] ?: "all"
                Column {
                    Text("Host Map: $title ($currentFilter)")
                    Button(
                        onClick = {
                            context.onFormValueChange("map_filter", "express")
                            context.emitEvent(
                                eventName = "marker_tap",
                                extraPayload =
                                    buildJsonObject {
                                        put("marker_id", "rider_42")
                                        put("eta_mins", 4)
                                    },
                            )
                        },
                    ) {
                        Text("Select Rider Marker")
                    }
                }
            }.build()

    private fun sampleRegisteredCustomSlotData(): CustomSlotSnippetData =
        CustomSlotSnippetData(
            slotId = "delivery_map",
            slotType = "native_map_view",
            minHeightDp = 180,
            aspectRatio = 1.75f,
            initialState = buildJsonObject { put("title", "Order #AF-4092") },
            onEvent =
                mapOf(
                    "marker_tap" to
                        ActionRef(
                            id = "map_marker_action",
                            type = "show_toast",
                            payload = buildJsonObject { put("origin", "slot_host") },
                        ),
                ),
        )

    private fun sampleGraphCanvasData(): GraphCanvasSnippetData =
        GraphCanvasSnippetData(
            canvasId = "team_map",
            title = TextAtom(text = "People Relationship Map"),
            subtitle = TextAtom(text = "Interactive Bezier, straight, and dashed edges"),
            heightDp = 320,
            nodes = sampleGraphNodes(),
            edges = sampleGraphEdges(),
            allowPan = true,
            allowZoom = true,
            allowNodeDrag = true,
            minZoom = 0.5f,
            maxZoom = 2.0f,
        )

    private fun sampleGraphNodes(): List<GraphNodeSpec> =
        listOf(
            GraphNodeSpec(
                nodeId = "maya",
                label = "Maya Lin",
                subtitle = "Staff Designer",
                badge = "LEAD",
                x = 16f,
                y = 20f,
                iconName = "star",
                pinned = true,
                draggable = false,
                onClick = ActionRef(id = "inspect_maya", type = "show_toast"),
            ),
            GraphNodeSpec(
                nodeId = "arjun",
                label = "Arjun Nair",
                subtitle = "KMP Architect",
                x = 200f,
                y = 110f,
                bgColor = ColorTokenRef(token = "surface.primary"),
                borderColor = ColorTokenRef(token = "brand.primary"),
            ),
            GraphNodeSpec(
                nodeId = "kabir",
                label = "Kabir Sharma",
                subtitle = "Go BFF Lead",
                x = 36f,
                y = 210f,
            ),
        )

    private fun sampleGraphEdges(): List<GraphEdgeSpec> =
        listOf(
            GraphEdgeSpec(
                edgeId = "e_mentors",
                fromNodeId = "maya",
                toNodeId = "arjun",
                label = "Mentors",
                style = GraphEdgeStyle.BEZIER,
                highlighted = true,
                onClick = ActionRef(id = "inspect_mentorship", type = "track_event"),
            ),
            GraphEdgeSpec(
                edgeId = "e_pairs",
                fromNodeId = "arjun",
                toNodeId = "kabir",
                label = "Pairs",
                style = GraphEdgeStyle.STRAIGHT,
            ),
            GraphEdgeSpec(
                edgeId = "e_reviews",
                fromNodeId = "maya",
                toNodeId = "kabir",
                label = "Reviews",
                style = GraphEdgeStyle.DASHED,
            ),
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
    private fun SpatialPageHost(
        page: PageResponse,
        customSlotRegistry: CustomSlotRegistry? = null,
        onAction: (ActionRef) -> Unit,
        onFormUpdate: (String, String) -> Unit = { _, _ -> },
    ) {
        val registry = remember { BuiltInSnippetModule.createDefaultRegistry() }
        val formValues = remember { mutableStateMapOf<String, String>() }
        DesignTheme(darkTheme = false, reducedMotion = true) {
            SduiScreen(
                state = LayoutUiState.Ready(page = page, formValues = formValues.toMap()),
                snippetRegistry = registry,
                onAction = onAction,
                onFormValueChange = { fieldId, value ->
                    formValues[fieldId] = value
                    onFormUpdate(fieldId, value)
                },
                customSlotRegistry = customSlotRegistry,
            )
        }
    }
}
