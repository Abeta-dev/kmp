package dev.abeta.kmp.layout.snippet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiText
import dev.abeta.kmp.atoms.tokens.resolve
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.GraphCanvasSnippetData
import dev.abeta.kmp.contracts.GraphNodeSpec
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.math.roundToInt

private const val MIN_CANVAS_HEIGHT_DP = 160
private const val GRID_DOT_ALPHA = 0.55f
private const val ZOOM_PERCENT_SCALE = 100
private const val TOOLBAR_BUTTON_HORIZONTAL_DP = 8
private const val TOOLBAR_BUTTON_VERTICAL_DP = 4

internal class GraphCanvasSnippetRenderer(
    json: Json,
) : SnippetRenderer<GraphCanvasSnippetData> {
    override val snippetType: String = SnippetTypes.GRAPH_CANVAS
    private val decoder = kotlinxSnippetDecoder<GraphCanvasSnippetData>(json)

    override fun decode(data: JsonObject): AppResult<GraphCanvasSnippetData> = decoder(data)

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    override fun Render(
        model: GraphCanvasSnippetData,
        envelope: SnippetEnvelope,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        val spacing = ThemeTokens.spacing
        val initialZoom = clampGraphZoom(1f, model.minZoom, model.maxZoom)
        var zoom by remember(envelope.snippetId, model.canvasId) { mutableFloatStateOf(initialZoom) }
        var panPx by remember(envelope.snippetId, model.canvasId) { mutableStateOf(Offset.Zero) }
        val nodePositions =
            remember(envelope.snippetId, model.canvasId, model.nodes) {
                mutableStateMapOf<String, Offset>().apply {
                    model.nodes.forEach { node -> put(node.nodeId, Offset(node.x, node.y)) }
                }
            }
        val nodeSizes = remember(envelope.snippetId, model.canvasId) { mutableStateMapOf<String, IntSize>() }
        val hasHeaderText = model.title != null || model.subtitle != null
        val hasTransformControls = model.allowZoom || model.allowPan

        UiSurfaceCard(
            modifier = Modifier.fillMaxWidth(),
            layout = model.layout,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.space12),
            ) {
                if (hasHeaderText || hasTransformControls) {
                    GraphCanvasHeaderBar(
                        model = model,
                        zoom = zoom,
                        onZoomOut = { zoom = clampGraphZoom(zoom - GRAPH_ZOOM_STEP, model.minZoom, model.maxZoom) },
                        onZoomIn = { zoom = clampGraphZoom(zoom + GRAPH_ZOOM_STEP, model.minZoom, model.maxZoom) },
                        onReset = {
                            zoom = clampGraphZoom(1f, model.minZoom, model.maxZoom)
                            panPx = Offset.Zero
                        },
                        onAction = onAction,
                    )
                }

                GraphCanvasViewport(
                    model = model,
                    zoom = zoom,
                    panPx = panPx,
                    nodePositions = nodePositions,
                    nodeSizes = nodeSizes,
                    onTransform = { panDelta, zoomFactor ->
                        if (model.allowPan) panPx += panDelta
                        if (model.allowZoom && zoomFactor != 1f) {
                            zoom = clampGraphZoom(zoom * zoomFactor, model.minZoom, model.maxZoom)
                        }
                    },
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun GraphCanvasHeaderBar(
    model: GraphCanvasSnippetData,
    zoom: Float,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onReset: () -> Unit,
    onAction: (ActionRef) -> Unit,
) {
    val spacing = ThemeTokens.spacing
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space8),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(spacing.space2),
        ) {
            model.title?.let { title -> UiText(atom = title, onAction = onAction) }
            model.subtitle?.let { subtitle -> UiText(atom = subtitle, onAction = onAction) }
        }
        if (model.allowZoom || model.allowPan) {
            GraphZoomToolbar(
                canvasId = model.canvasId,
                allowZoom = model.allowZoom,
                zoom = zoom,
                onZoomOut = onZoomOut,
                onZoomIn = onZoomIn,
                onReset = onReset,
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun GraphZoomToolbar(
    canvasId: String,
    allowZoom: Boolean,
    zoom: Float,
    onZoomOut: () -> Unit,
    onZoomIn: () -> Unit,
    onReset: () -> Unit,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    val zoomPercent = "${(zoom * ZOOM_PERCENT_SCALE).roundToInt()}%"

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.space4),
    ) {
        if (allowZoom) {
            GraphToolbarChip(label = "-", testTag = "graph_zoom_out_$canvasId", onClick = onZoomOut)
            Text(
                text = zoomPercent,
                style = typography.labelMedium,
                color = colors.text.secondary,
                modifier = Modifier.testTag("graph_zoom_label_$canvasId"),
            )
            GraphToolbarChip(label = "+", testTag = "graph_zoom_in_$canvasId", onClick = onZoomIn)
        }
        GraphToolbarChip(label = "Reset", testTag = "graph_zoom_reset_$canvasId", onClick = onReset)
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun GraphToolbarChip(
    label: String,
    testTag: String,
    onClick: () -> Unit,
) {
    val colors = ThemeTokens.colors
    val shape = RoundedCornerShape(ThemeTokens.cornerRadius.sm)

    Box(
        modifier =
            Modifier
                .clip(shape)
                .background(colors.surface.secondary, shape)
                .border(ThemeTokens.strokeWidth.thin, colors.border.subtle, shape)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = TOOLBAR_BUTTON_HORIZONTAL_DP.dp, vertical = TOOLBAR_BUTTON_VERTICAL_DP.dp)
                .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = ThemeTokens.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.text.primary,
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun GraphCanvasViewport(
    model: GraphCanvasSnippetData,
    zoom: Float,
    panPx: Offset,
    nodePositions: MutableMap<String, Offset>,
    nodeSizes: MutableMap<String, IntSize>,
    onTransform: (Offset, Float) -> Unit,
    onAction: (ActionRef) -> Unit,
) {
    val colors = ThemeTokens.colors
    val density = LocalDensity.current.density
    val resolvedEdgeColors =
        model.edges.associate { edge ->
            val fallback = if (edge.highlighted) colors.brand.primary else colors.border.strong
            edge.edgeId to edge.color.resolve(fallback = fallback)
        }
    val edgeCenters =
        resolveNodeScreenCenters(
            nodes = model.nodes,
            nodePositions = nodePositions,
            nodeSizes = nodeSizes,
            zoom = zoom,
            panPx = panPx,
            density = density,
        )
    val viewportModifier = rememberViewportModifier(model = model, onTransform = onTransform, onAction = onAction)

    Box(modifier = viewportModifier) {
        GraphEdgesCanvasLayer(
            edges = model.edges,
            edgeCenters = edgeCenters,
            resolvedEdgeColors = resolvedEdgeColors,
            dotColor = colors.border.subtle.copy(alpha = GRID_DOT_ALPHA),
            zoom = zoom,
            panPx = panPx,
        )
        GraphEdgeLabelsLayer(
            model = model,
            edgeCenters = edgeCenters,
            resolvedEdgeColors = resolvedEdgeColors,
            onAction = onAction,
        )
        GraphNodesLayer(
            model = model,
            zoom = zoom,
            panPx = panPx,
            density = density,
            nodePositions = nodePositions,
            nodeSizes = nodeSizes,
            onAction = onAction,
        )
    }
}

@Composable
private fun rememberViewportModifier(
    model: GraphCanvasSnippetData,
    onTransform: (Offset, Float) -> Unit,
    onAction: (ActionRef) -> Unit,
): Modifier {
    val colors = ThemeTokens.colors
    val shape = RoundedCornerShape(ThemeTokens.cornerRadius.sm)
    val latestOnTransform by rememberUpdatedState(onTransform)
    val latestOnAction by rememberUpdatedState(onAction)
    val tapAction = model.onCanvasTap

    return Modifier
        .fillMaxWidth()
        .height(model.heightDp.coerceAtLeast(MIN_CANVAS_HEIGHT_DP).dp)
        .clip(shape)
        .background(colors.background.secondary, shape)
        .border(ThemeTokens.strokeWidth.thin, colors.border.subtle, shape)
        .clipToBounds()
        .let { base ->
            if (tapAction != null) {
                base.pointerInput(model.canvasId, tapAction) {
                    detectTapGestures(onTap = { latestOnAction(tapAction) })
                }
            } else {
                base
            }
        }.let { base ->
            if (model.allowPan || model.allowZoom) {
                base.pointerInput(model.canvasId, model.allowPan, model.allowZoom) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        latestOnTransform(pan, gestureZoom)
                    }
                }
            } else {
                base
            }
        }.testTag("graph_canvas_${model.canvasId}")
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun GraphEdgeLabelsLayer(
    model: GraphCanvasSnippetData,
    edgeCenters: Map<String, Offset>,
    resolvedEdgeColors: Map<String, Color>,
    onAction: (ActionRef) -> Unit,
) {
    val fallbackAccent = ThemeTokens.colors.border.strong
    model.edges.forEach { edge ->
        val labelText = edge.label?.takeIf { it.isNotBlank() } ?: return@forEach
        val start = edgeCenters[edge.fromNodeId] ?: return@forEach
        val end = edgeCenters[edge.toNodeId] ?: return@forEach
        GraphEdgeLabelPill(
            edge = edge,
            labelText = labelText,
            midpointPx = computeBezierMidpoint(start, end),
            accentColor = resolvedEdgeColors[edge.edgeId] ?: fallbackAccent,
            onAction = onAction,
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun GraphNodesLayer(
    model: GraphCanvasSnippetData,
    zoom: Float,
    panPx: Offset,
    density: Float,
    nodePositions: MutableMap<String, Offset>,
    nodeSizes: MutableMap<String, IntSize>,
    onAction: (ActionRef) -> Unit,
) {
    val latestOnAction by rememberUpdatedState(onAction)
    model.nodes.forEach { node ->
        val worldPos = nodePositions[node.nodeId] ?: Offset(node.x, node.y)
        GraphNodeCard(
            node = node,
            screenTopLeft = worldToViewportPx(worldPos, zoom, panPx, density),
            zoom = zoom,
            density = density,
            canDrag = model.allowNodeDrag && node.draggable && !node.pinned,
            onMeasured = { size -> nodeSizes[node.nodeId] = size },
            onDragDeltaDp = { deltaDp ->
                val current = nodePositions[node.nodeId] ?: Offset(node.x, node.y)
                nodePositions[node.nodeId] = current + deltaDp
            },
            onDragFinished = { emitNodeMoved(model, node, nodePositions, latestOnAction) },
            onAction = latestOnAction,
        )
    }
}

private fun emitNodeMoved(
    model: GraphCanvasSnippetData,
    node: GraphNodeSpec,
    nodePositions: Map<String, Offset>,
    onAction: (ActionRef) -> Unit,
) {
    val moveAction = node.onDragEnd ?: model.onNodeMoved ?: return
    val finalPos = nodePositions[node.nodeId] ?: Offset(node.x, node.y)
    onAction(
        enrichNodeMovedAction(
            action = moveAction,
            canvasId = model.canvasId,
            nodeId = node.nodeId,
            x = finalPos.x,
            y = finalPos.y,
        ),
    )
}
