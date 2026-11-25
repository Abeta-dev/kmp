package dev.abeta.kmp.layout.snippet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import kotlin.math.abs
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
        var viewportSize by remember(envelope.snippetId, model.canvasId) { mutableStateOf(IntSize.Zero) }
        val nodePositions =
            remember(envelope.snippetId, model.canvasId) {
                mutableStateMapOf<String, Offset>().apply {
                    model.nodes.forEach { node -> put(node.nodeId, Offset(node.x, node.y)) }
                }
            }
        val nodeSizes = remember(envelope.snippetId, model.canvasId) { mutableStateMapOf<String, IntSize>() }
        LaunchedEffect(envelope.snippetId, model.canvasId, model.nodes) {
            reconcileGraphNodeState(model.nodes, nodePositions, nodeSizes)
        }
        val applyToolbarZoom: (Float) -> Unit = { stepDelta ->
            val (nextZoom, nextPan) = computeToolbarZoom(model, zoom, panPx, viewportSize, stepDelta)
            zoom = nextZoom
            panPx = nextPan
        }
        val handleTransform: (Offset, Offset, Float) -> Unit = { centroid, panDelta, zoomFactor ->
            val (nextZoom, nextPan) = applyViewportTransform(model, zoom, panPx, centroid, panDelta, zoomFactor)
            zoom = nextZoom
            panPx = nextPan
        }

        UiSurfaceCard(modifier = Modifier.fillMaxWidth(), layout = model.layout) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(spacing.space12),
            ) {
                if (hasGraphHeaderOrControls(model)) {
                    GraphCanvasHeaderBar(
                        model = model,
                        zoom = zoom,
                        onZoomOut = { applyToolbarZoom(-GRAPH_ZOOM_STEP) },
                        onZoomIn = { applyToolbarZoom(GRAPH_ZOOM_STEP) },
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
                    onViewportSizeChanged = { viewportSize = it },
                    onTransform = handleTransform,
                    onAction = onAction,
                )
            }
        }
    }
}

private fun hasGraphHeaderOrControls(model: GraphCanvasSnippetData): Boolean {
    val hasHeader = model.title != null || model.subtitle != null
    val hasControls = model.allowZoom || model.allowPan
    return hasHeader || hasControls
}

private fun reconcileGraphNodeState(
    nodes: List<GraphNodeSpec>,
    nodePositions: MutableMap<String, Offset>,
    nodeSizes: MutableMap<String, IntSize>,
) {
    val incomingIds = nodes.mapTo(HashSet(nodes.size)) { it.nodeId }
    nodePositions.keys.retainAll(incomingIds)
    nodeSizes.keys.retainAll(incomingIds)
    nodes.forEach { node ->
        if (node.nodeId !in nodePositions) {
            nodePositions[node.nodeId] = Offset(node.x, node.y)
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
            GraphToolbarChip(
                label = "-",
                description = "Zoom out",
                testTag = "graph_zoom_out_$canvasId",
                onClick = onZoomOut,
            )
            Text(
                text = zoomPercent,
                style = typography.labelMedium,
                color = colors.text.secondary,
                modifier = Modifier.testTag("graph_zoom_label_$canvasId"),
            )
            GraphToolbarChip(
                label = "+",
                description = "Zoom in",
                testTag = "graph_zoom_in_$canvasId",
                onClick = onZoomIn,
            )
        }
        GraphToolbarChip(
            label = "Reset",
            description = "Reset zoom and pan",
            testTag = "graph_zoom_reset_$canvasId",
            onClick = onReset,
        )
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun GraphToolbarChip(
    label: String,
    description: String,
    testTag: String,
    onClick: () -> Unit,
) {
    val colors = ThemeTokens.colors
    val sizing = ThemeTokens.sizing
    val shape = RoundedCornerShape(ThemeTokens.cornerRadius.sm)

    Box(
        modifier =
            Modifier
                .defaultMinSize(minWidth = sizing.minTouchTarget, minHeight = sizing.minTouchTarget)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description }
                .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .clip(shape)
                    .background(colors.surface.secondary, shape)
                    .border(ThemeTokens.strokeWidth.thin, colors.border.subtle, shape)
                    .padding(horizontal = TOOLBAR_BUTTON_HORIZONTAL_DP.dp, vertical = TOOLBAR_BUTTON_VERTICAL_DP.dp),
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
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun GraphCanvasViewport(
    model: GraphCanvasSnippetData,
    zoom: Float,
    panPx: Offset,
    nodePositions: MutableMap<String, Offset>,
    nodeSizes: MutableMap<String, IntSize>,
    onViewportSizeChanged: (IntSize) -> Unit,
    onTransform: (Offset, Offset, Float) -> Unit,
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
    val viewportModifier =
        rememberViewportModifier(
            model = model,
            onViewportSizeChanged = onViewportSizeChanged,
            onTransform = onTransform,
            onAction = onAction,
        )

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
    onViewportSizeChanged: (IntSize) -> Unit,
    onTransform: (Offset, Offset, Float) -> Unit,
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
        .onSizeChanged(onViewportSizeChanged)
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
            when {
                model.allowPan -> {
                    base.pointerInput(model.canvasId, model.allowPan, model.allowZoom) {
                        detectTransformGestures { centroid, pan, gestureZoom, _ ->
                            latestOnTransform(centroid, pan, gestureZoom)
                        }
                    }
                }

                model.allowZoom -> {
                    base.pointerInput(model.canvasId, model.allowZoom) {
                        detectMultiTouchZoomOnly { centroid, gestureZoom ->
                            latestOnTransform(centroid, Offset.Zero, gestureZoom)
                        }
                    }
                }

                else -> {
                    base
                }
            }
        }.testTag("graph_canvas_${model.canvasId}")
}

private suspend fun PointerInputScope.detectMultiTouchZoomOnly(onZoom: (centroid: Offset, zoomFactor: Float) -> Unit) {
    awaitEachGesture {
        var zoomAccum = 1f
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop
        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            val pressedCount = event.changes.count { it.pressed }
            if (!canceled && pressedCount > 1) {
                val zoomChange = event.calculateZoom()
                if (!pastTouchSlop) {
                    zoomAccum *= zoomChange
                    val zoomMotion = abs(1f - zoomAccum) * event.calculateCentroidSize(useCurrent = false)
                    if (zoomMotion > touchSlop) {
                        pastTouchSlop = true
                    }
                }
                if (pastTouchSlop && zoomChange != 1f) {
                    onZoom(event.calculateCentroid(useCurrent = true), zoomChange)
                    event.changes.forEach { change ->
                        if (change.positionChanged()) change.consume()
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
    }
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
        key(edge.edgeId) {
            GraphEdgeLabelPill(
                edge = edge,
                labelText = labelText,
                midpointPx = computeBezierMidpoint(start, end),
                accentColor = resolvedEdgeColors[edge.edgeId] ?: fallbackAccent,
                onAction = onAction,
            )
        }
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
        key(node.nodeId) {
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
