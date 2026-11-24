@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

private const val DEFAULT_GRAPH_HEIGHT_DP = 360
private const val DEFAULT_MIN_ZOOM = 0.5f
private const val DEFAULT_MAX_ZOOM = 2.5f

/**
 * Typed payload for a host-provided custom view slot snippet (`"custom_slot"`).
 */
@Serializable
data class CustomSlotSnippetData(
    @SerialName("slot_id")
    val slotId: String,
    @SerialName("slot_type")
    val slotType: String,
    @SerialName("min_height_dp")
    val minHeightDp: Int? = null,
    @SerialName("aspect_ratio")
    val aspectRatio: Float? = null,
    @SerialName("initial_state")
    val initialState: JsonObject = JsonObject(emptyMap()),
    @SerialName("on_event")
    val onEvent: Map<String, ActionRef> = emptyMap(),
    val fallback: SnippetEnvelope? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Visual path style for a directed edge inside [GraphCanvasSnippetData].
 */
@Serializable
enum class GraphEdgeStyle {
    @SerialName("bezier")
    BEZIER,

    @SerialName("straight")
    STRAIGHT,

    @SerialName("dashed")
    DASHED,
}

/**
 * Positioned interactive node rendered on a spatial [GraphCanvasSnippetData] surface.
 */
@Serializable
data class GraphNodeSpec(
    @SerialName("node_id")
    val nodeId: String,
    val label: String,
    val subtitle: String? = null,
    val badge: String? = null,
    val x: Float,
    val y: Float,
    @SerialName("bg_color")
    val bgColor: ColorTokenRef? = null,
    @SerialName("border_color")
    val borderColor: ColorTokenRef? = null,
    @SerialName("icon_name")
    val iconName: String? = null,
    val draggable: Boolean = true,
    val pinned: Boolean = false,
    @SerialName("on_click")
    val onClick: ActionRef? = null,
    @SerialName("on_drag_end")
    val onDragEnd: ActionRef? = null,
    val tracking: TrackingContract? = null,
)

/**
 * Directed edge connecting [fromNodeId] to [toNodeId] inside [GraphCanvasSnippetData].
 */
@Serializable
data class GraphEdgeSpec(
    @SerialName("edge_id")
    val edgeId: String,
    @SerialName("from_node_id")
    val fromNodeId: String,
    @SerialName("to_node_id")
    val toNodeId: String,
    val label: String? = null,
    val style: GraphEdgeStyle = GraphEdgeStyle.BEZIER,
    val color: ColorTokenRef? = null,
    val highlighted: Boolean = false,
    @SerialName("on_click")
    val onClick: ActionRef? = null,
)

/**
 * Typed payload for an interactive pan/zoom spatial node-and-edge graph snippet (`"graph_canvas"`).
 */
@Serializable
data class GraphCanvasSnippetData(
    @SerialName("canvas_id")
    val canvasId: String,
    val title: TextAtom? = null,
    val subtitle: TextAtom? = null,
    @SerialName("height_dp")
    val heightDp: Int = DEFAULT_GRAPH_HEIGHT_DP,
    val nodes: List<GraphNodeSpec> = emptyList(),
    val edges: List<GraphEdgeSpec> = emptyList(),
    @SerialName("allow_pan")
    val allowPan: Boolean = true,
    @SerialName("allow_zoom")
    val allowZoom: Boolean = true,
    @SerialName("allow_node_drag")
    val allowNodeDrag: Boolean = true,
    @SerialName("min_zoom")
    val minZoom: Float = DEFAULT_MIN_ZOOM,
    @SerialName("max_zoom")
    val maxZoom: Float = DEFAULT_MAX_ZOOM,
    @SerialName("on_node_moved")
    val onNodeMoved: ActionRef? = null,
    @SerialName("on_canvas_tap")
    val onCanvasTap: ActionRef? = null,
    val layout: LayoutConfig? = null,
)
