package dev.abeta.kmp.contracts

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpatialAndSlotContractsTest {
    private val sampleLayout =
        LayoutConfig(
            padding = SpacingValues(horizontal = 16, vertical = 12),
            cornerRadius = CornerRadiusSpec(token = "radius.md", all = 12),
            fillMaxWidth = true,
        )

    private fun action(
        id: String,
        type: String,
        key: String,
        value: String,
    ): ActionRef =
        ActionRef(
            id = id,
            type = type,
            payload = buildJsonObject { put(key, value) },
        )

    @Test
    fun snippetTypesExposeCanonicalSpatialAndSlotIdentifiers() {
        assertEquals("custom_slot", SnippetTypes.CUSTOM_SLOT)
        assertEquals("graph_canvas", SnippetTypes.GRAPH_CANVAS)
    }

    @Test
    fun customSlotSnippetDataRoundTripsWithSnakeCaseWireNamesAndFallback() {
        val fallbackCard =
            SnippetEnvelope(
                snippetId = "slot_fallback_card",
                snippetParentId = "slot_live_map",
                snippetType = SnippetTypes.CARD,
                data =
                    buildJsonObject {
                        put("title", buildJsonObject { put("text", "Map unavailable") })
                    },
            )
        val slot =
            CustomSlotSnippetData(
                slotId = "delivery_live_map",
                slotType = "native_map_view",
                minHeightDp = 240,
                aspectRatio = 1.75f,
                initialState =
                    buildJsonObject {
                        put("zoom", 14)
                        put("show_rider", true)
                        put("order_id", "AF-4092")
                    },
                onEvent =
                    mapOf(
                        "marker_tap" to action("evt_marker", "show_toast", "message", "Rider nearby"),
                        "region_change" to action("evt_region", "track_event", "event_name", "map_moved"),
                    ),
                fallback = fallbackCard,
                layout = sampleLayout,
            )

        val encoded = SduiJson.encodeToString(slot)
        for (key in listOf(
            "slot_id",
            "slot_type",
            "min_height_dp",
            "aspect_ratio",
            "initial_state",
            "on_event",
            "fallback",
            "layout",
        )) {
            assertTrue(encoded.contains("\"$key\""), "Missing wire key \"$key\" in $encoded")
        }
        assertEquals(slot, SduiJson.decodeFromString<CustomSlotSnippetData>(encoded))
    }

    @Test
    fun customSlotSnippetDataDefaultsDeserializeFromMinimalJson() {
        val minimal = SduiJson.decodeFromString<CustomSlotSnippetData>("""{"slot_id":"s1","slot_type":"chart"}""")
        assertEquals("s1", minimal.slotId)
        assertEquals("chart", minimal.slotType)
        assertNull(minimal.minHeightDp)
        assertNull(minimal.aspectRatio)
        assertTrue(minimal.initialState.isEmpty())
        assertTrue(minimal.onEvent.isEmpty())
        assertNull(minimal.fallback)
        assertNull(minimal.layout)
    }

    @Test
    fun graphEdgeStyleEnumWireLiteralsRoundTrip() {
        val styleWirePairs =
            listOf(
                GraphEdgeStyle.BEZIER to "bezier",
                GraphEdgeStyle.STRAIGHT to "straight",
                GraphEdgeStyle.DASHED to "dashed",
            )
        for ((style, wire) in styleWirePairs) {
            val encoded = SduiJson.encodeToString(style)
            assertEquals("\"$wire\"", encoded)
            assertEquals(style, SduiJson.decodeFromString<GraphEdgeStyle>(encoded))
        }
    }

    private fun sampleGraphCanvas(): GraphCanvasSnippetData =
        GraphCanvasSnippetData(
            canvasId = "topology_canvas",
            title = TextAtom(text = "Service Topology"),
            subtitle = TextAtom(text = "Interactive module graph"),
            heightDp = 420,
            nodes =
                listOf(
                    GraphNodeSpec(
                        nodeId = "node_bff",
                        label = "Go BFF",
                        subtitle = "bff@v0.8.0",
                        badge = "EDGE",
                        x = 60f,
                        y = 40f,
                        bgColor = ColorTokenRef(token = "brand.container"),
                        borderColor = ColorTokenRef(token = "brand.primary"),
                        iconName = "cloud",
                        draggable = false,
                        pinned = true,
                        onClick = action("click_bff", "show_toast", "message", "Go BFF"),
                        onDragEnd = action("drag_bff", "save_state", "key", "bff_pos"),
                        tracking =
                            TrackingContract(
                                eventName = "graph_node_clicked",
                                properties = mapOf("node_id" to "node_bff"),
                            ),
                    ),
                    GraphNodeSpec(nodeId = "node_contracts", label = "sdui-contracts", x = 240f, y = 150f),
                ),
            edges =
                listOf(
                    GraphEdgeSpec(
                        edgeId = "edge_1",
                        fromNodeId = "node_bff",
                        toNodeId = "node_contracts",
                        label = "JSON v1",
                        style = GraphEdgeStyle.DASHED,
                        color = ColorTokenRef(token = "brand.primary"),
                        highlighted = true,
                        onClick = action("click_edge_1", "show_toast", "message", "Edge clicked"),
                    ),
                ),
            allowPan = false,
            allowZoom = false,
            allowNodeDrag = false,
            minZoom = 0.25f,
            maxZoom = 3.0f,
            onNodeMoved = action("on_moved", "save_state", "key", "last_node"),
            onCanvasTap = action("on_tap", "track_event", "event_name", "canvas_tap"),
            layout = sampleLayout,
        )

    @Test
    fun graphCanvasSnippetDataRoundTripsWithNodesEdgesAndInteractionFlags() {
        val graph = sampleGraphCanvas()
        val encoded = SduiJson.encodeToString(graph)
        val expectedKeys =
            listOf(
                "canvas_id",
                "height_dp",
                "node_id",
                "bg_color",
                "border_color",
                "icon_name",
                "draggable",
                "pinned",
                "on_click",
                "on_drag_end",
                "edge_id",
                "from_node_id",
                "to_node_id",
                "highlighted",
                "allow_pan",
                "allow_zoom",
                "allow_node_drag",
                "min_zoom",
                "max_zoom",
                "on_node_moved",
                "on_canvas_tap",
            )
        for (key in expectedKeys) {
            assertTrue(encoded.contains("\"$key\""), "Missing wire key \"$key\" in $encoded")
        }
        assertEquals(graph, SduiJson.decodeFromString<GraphCanvasSnippetData>(encoded))
    }

    @Test
    fun graphCanvasAndItemDefaultsDeserializeFromMinimalJson() {
        val minimalCanvas = SduiJson.decodeFromString<GraphCanvasSnippetData>("""{"canvas_id":"arch_graph"}""")
        assertEquals("arch_graph", minimalCanvas.canvasId)
        assertEquals(360, minimalCanvas.heightDp)
        assertTrue(minimalCanvas.nodes.isEmpty())
        assertTrue(minimalCanvas.edges.isEmpty())
        assertTrue(minimalCanvas.allowPan)
        assertTrue(minimalCanvas.allowZoom)
        assertTrue(minimalCanvas.allowNodeDrag)
        assertEquals(0.5f, minimalCanvas.minZoom)
        assertEquals(2.5f, minimalCanvas.maxZoom)
        assertNull(minimalCanvas.onNodeMoved)
        assertNull(minimalCanvas.onCanvasTap)

        val minimalNode =
            SduiJson.decodeFromString<GraphNodeSpec>("""{"node_id":"n1","label":"Core","x":12.5,"y":24.0}""")
        assertEquals("n1", minimalNode.nodeId)
        assertEquals("Core", minimalNode.label)
        assertEquals(12.5f, minimalNode.x)
        assertEquals(24.0f, minimalNode.y)
        assertTrue(minimalNode.draggable)
        assertFalse(minimalNode.pinned)

        val minimalEdge =
            SduiJson.decodeFromString<GraphEdgeSpec>("""{"edge_id":"e1","from_node_id":"n1","to_node_id":"n2"}""")
        assertEquals(GraphEdgeStyle.BEZIER, minimalEdge.style)
        assertFalse(minimalEdge.highlighted)
        assertNull(minimalEdge.label)
    }
}
