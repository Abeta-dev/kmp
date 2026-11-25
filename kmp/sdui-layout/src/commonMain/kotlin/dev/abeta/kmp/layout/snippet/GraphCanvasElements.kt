package dev.abeta.kmp.layout.snippet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.atoms.UiIcon
import dev.abeta.kmp.atoms.modifiers.LocalUiHapticFeedback
import dev.abeta.kmp.atoms.modifiers.UiHapticKind
import dev.abeta.kmp.atoms.tokens.resolve
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.GraphEdgeSpec
import dev.abeta.kmp.contracts.GraphEdgeStyle
import dev.abeta.kmp.contracts.GraphNodeSpec
import dev.abeta.kmp.contracts.IconAtom
import dev.abeta.kmp.tokens.ThemeTokens
import kotlin.math.roundToInt

private const val GRID_DOT_STEP_DP = 24f
private const val GRID_DOT_MIN_STEP_PX = 12f
private const val GRID_DOT_RADIUS_DP = 1.25f
private const val EDGE_NORMAL_STROKE_DP = 1.75f
private const val EDGE_HIGHLIGHT_STROKE_DP = 3f
private const val EDGE_DASH_ON_PX = 12f
private const val EDGE_DASH_OFF_PX = 8f
private const val NODE_ICON_SIZE_DP = 16
private const val NODE_MIN_WIDTH_DP = 124
private const val NODE_MAX_WIDTH_DP = 196
private const val EDGE_LABEL_OFFSET_X_DP = 44
private const val EDGE_LABEL_OFFSET_Y_DP = 12
private const val BADGE_HORIZONTAL_DP = 6
private const val BADGE_VERTICAL_DP = 2

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
internal fun GraphEdgesCanvasLayer(
    edges: List<GraphEdgeSpec>,
    edgeCenters: Map<String, Offset>,
    resolvedEdgeColors: Map<String, Color>,
    dotColor: Color,
    zoom: Float,
    panPx: Offset,
) {
    val fallbackColor = ThemeTokens.colors.border.strong
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawSpatialDotGrid(dotColor = dotColor, zoom = zoom, panPx = panPx)
        edges.forEach { edge ->
            val start = edgeCenters[edge.fromNodeId] ?: return@forEach
            val end = edgeCenters[edge.toNodeId] ?: return@forEach
            val strokeColor = resolvedEdgeColors[edge.edgeId] ?: fallbackColor
            val strokePx = (if (edge.highlighted) EDGE_HIGHLIGHT_STROKE_DP else EDGE_NORMAL_STROKE_DP).dp.toPx()
            drawDirectedGraphEdge(
                start = start,
                end = end,
                style = edge.style,
                color = strokeColor,
                strokePx = strokePx,
            )
        }
    }
}

private fun DrawScope.drawSpatialDotGrid(
    dotColor: Color,
    zoom: Float,
    panPx: Offset,
) {
    val stepPx = (GRID_DOT_STEP_DP * density * zoom).coerceAtLeast(GRID_DOT_MIN_STEP_PX)
    val startX = ((panPx.x % stepPx) + stepPx) % stepPx
    val startY = ((panPx.y % stepPx) + stepPx) % stepPx
    val radius = GRID_DOT_RADIUS_DP.dp.toPx()
    var y = startY
    while (y <= size.height) {
        var x = startX
        while (x <= size.width) {
            drawCircle(color = dotColor, radius = radius, center = Offset(x, y))
            x += stepPx
        }
        y += stepPx
    }
}

private fun DrawScope.drawDirectedGraphEdge(
    start: Offset,
    end: Offset,
    style: GraphEdgeStyle,
    color: Color,
    strokePx: Float,
) {
    val dashEffect =
        if (style == GraphEdgeStyle.DASHED) {
            PathEffect.dashPathEffect(floatArrayOf(EDGE_DASH_ON_PX, EDGE_DASH_OFF_PX), 0f)
        } else {
            null
        }
    when (style) {
        GraphEdgeStyle.BEZIER -> {
            val (cp1, cp2) = computeBezierControlPoints(start, end)
            val path =
                Path().apply {
                    moveTo(start.x, start.y)
                    cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, end.x, end.y)
                }
            drawPath(path = path, color = color, style = Stroke(width = strokePx, cap = StrokeCap.Round))
            val (tip, tangent) = evaluateBezierPointAndTangent(start, end, GRAPH_ARROW_CURVE_T)
            drawArrowhead(tip = tip, direction = tangent, color = color, strokePx = strokePx)
        }

        GraphEdgeStyle.STRAIGHT, GraphEdgeStyle.DASHED -> {
            drawLine(
                color = color,
                start = start,
                end = end,
                strokeWidth = strokePx,
                cap = StrokeCap.Round,
                pathEffect = dashEffect,
            )
            val tip =
                Offset(
                    x = start.x + (end.x - start.x) * GRAPH_ARROW_CURVE_T,
                    y = start.y + (end.y - start.y) * GRAPH_ARROW_CURVE_T,
                )
            drawArrowhead(tip = tip, direction = end - start, color = color, strokePx = strokePx)
        }
    }
}

private fun DrawScope.drawArrowhead(
    tip: Offset,
    direction: Offset,
    color: Color,
    strokePx: Float,
) {
    if (direction == Offset.Zero) return
    val (leftWing, rightWing) = computeArrowheadWings(tip = tip, direction = direction)
    val arrowPath =
        Path().apply {
            moveTo(leftWing.x, leftWing.y)
            lineTo(tip.x, tip.y)
            lineTo(rightWing.x, rightWing.y)
        }
    drawPath(path = arrowPath, color = color, style = Stroke(width = strokePx, cap = StrokeCap.Round))
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
internal fun GraphEdgeLabelPill(
    edge: GraphEdgeSpec,
    labelText: String,
    midpointPx: Offset,
    accentColor: Color,
    onAction: (ActionRef) -> Unit,
) {
    val colors = ThemeTokens.colors
    val cornerRadius = ThemeTokens.cornerRadius
    val strokeWidth = ThemeTokens.strokeWidth
    val typography = ThemeTokens.typography
    val density = LocalDensity.current.density
    val shape = RoundedCornerShape(cornerRadius.full)
    val offsetX = (midpointPx.x - EDGE_LABEL_OFFSET_X_DP * density).roundToInt()
    val offsetY = (midpointPx.y - EDGE_LABEL_OFFSET_Y_DP * density).roundToInt()

    Box(
        modifier =
            Modifier
                .offset { IntOffset(offsetX, offsetY) }
                .clip(shape)
                .background(colors.surface.elevated, shape)
                .border(strokeWidth.thin, accentColor, shape)
                .let { mod ->
                    val clickAction = edge.onClick
                    if (clickAction != null) {
                        mod.clickable(role = Role.Button) { onAction(clickAction) }
                    } else {
                        mod
                    }
                }.padding(horizontal = BADGE_HORIZONTAL_DP.dp, vertical = BADGE_VERTICAL_DP.dp)
                .testTag("graph_edge_label_${edge.edgeId}"),
    ) {
        Text(text = labelText, style = typography.caption, color = colors.text.primary, maxLines = 1)
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
internal fun GraphNodeCard(
    node: GraphNodeSpec,
    screenTopLeft: Offset,
    zoom: Float,
    density: Float,
    canDrag: Boolean,
    onMeasured: (IntSize) -> Unit,
    onDragDeltaDp: (Offset) -> Unit,
    onDragFinished: () -> Unit,
    onAction: (ActionRef) -> Unit,
) {
    val cardModifier =
        rememberGraphNodeModifier(
            node = node,
            screenTopLeft = screenTopLeft,
            zoom = zoom,
            density = density,
            canDrag = canDrag,
            onMeasured = onMeasured,
            onDragDeltaDp = onDragDeltaDp,
            onDragFinished = onDragFinished,
            onAction = onAction,
        )
    GraphNodeCardContent(node = node, modifier = cardModifier)
}

@Composable
@Suppress("LongParameterList")
private fun rememberGraphNodeModifier(
    node: GraphNodeSpec,
    screenTopLeft: Offset,
    zoom: Float,
    density: Float,
    canDrag: Boolean,
    onMeasured: (IntSize) -> Unit,
    onDragDeltaDp: (Offset) -> Unit,
    onDragFinished: () -> Unit,
    onAction: (ActionRef) -> Unit,
): Modifier {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val shape = RoundedCornerShape(ThemeTokens.cornerRadius.md)
    val haptic = LocalUiHapticFeedback.current
    val bgColor = node.bgColor.resolve(fallback = colors.surface.primary)
    val borderColor =
        node.borderColor.resolve(fallback = if (node.pinned) colors.brand.primary else colors.border.default)
    val latestOnDragDelta by rememberUpdatedState(onDragDeltaDp)
    val latestOnDragFinished by rememberUpdatedState(onDragFinished)
    val latestOnAction by rememberUpdatedState(onAction)
    val latestZoom by rememberUpdatedState(zoom)

    return Modifier
        .offset { IntOffset(screenTopLeft.x.roundToInt(), screenTopLeft.y.roundToInt()) }
        .onSizeChanged(onMeasured)
        .applyNodeDragGesture(
            nodeId = node.nodeId,
            canDrag = canDrag,
            density = density,
            zoomProvider = { latestZoom },
            onDragDeltaDp = { latestOnDragDelta(it) },
            onDragFinished = { latestOnDragFinished() },
        ).graphicsLayer {
            scaleX = zoom
            scaleY = zoom
            transformOrigin = TransformOrigin(0f, 0f)
        }.widthIn(min = NODE_MIN_WIDTH_DP.dp, max = NODE_MAX_WIDTH_DP.dp)
        .clip(shape)
        .background(bgColor, shape)
        .border(ThemeTokens.strokeWidth.thin, borderColor, shape)
        .applyNodeClickAction(node = node, haptic = haptic, onAction = { latestOnAction(it) })
        .padding(spacing.space8)
        .testTag("graph_node_${node.nodeId}")
}

private fun Modifier.applyNodeClickAction(
    node: GraphNodeSpec,
    haptic: (UiHapticKind) -> Unit,
    onAction: (ActionRef) -> Unit,
): Modifier {
    val clickAction = node.onClick ?: return this
    val resolvedAction =
        if (clickAction.tracking == null && node.tracking != null) {
            clickAction.copy(tracking = node.tracking)
        } else {
            clickAction
        }
    return clickable(role = Role.Button) {
        haptic(UiHapticKind.CLICK)
        onAction(resolvedAction)
    }
}

@Suppress("LongParameterList")
private fun Modifier.applyNodeDragGesture(
    nodeId: String,
    canDrag: Boolean,
    density: Float,
    zoomProvider: () -> Float,
    onDragDeltaDp: (Offset) -> Unit,
    onDragFinished: () -> Unit,
): Modifier {
    if (!canDrag) return this
    return pointerInput(nodeId, density) {
        detectDragGestures(
            onDragEnd = onDragFinished,
            onDragCancel = onDragFinished,
        ) { change, dragAmount ->
            change.consume()
            val scale = density * zoomProvider().coerceAtLeast(MIN_POSITIVE_GRAPH_ZOOM)
            onDragDeltaDp(Offset(dragAmount.x / scale, dragAmount.y / scale))
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun GraphNodeCardContent(
    node: GraphNodeSpec,
    modifier: Modifier,
) {
    val colors = ThemeTokens.colors
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.space2),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.space4),
        ) {
            node.iconName?.takeIf { it.isNotBlank() }?.let { iconName ->
                UiIcon(
                    atom =
                        IconAtom(
                            name = iconName,
                            sizeDp = NODE_ICON_SIZE_DP,
                            color = ColorTokenRef(token = "icon.brand"),
                        ),
                )
            }
            Text(
                text = node.label,
                style = typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.text.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            node.badge?.takeIf { it.isNotBlank() }?.let { badgeText ->
                GraphNodeBadge(text = badgeText)
            }
        }
        node.subtitle?.takeIf { it.isNotBlank() }?.let { subtitleText ->
            Text(
                text = subtitleText,
                style = typography.caption,
                color = colors.text.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun GraphNodeBadge(text: String) {
    val colors = ThemeTokens.colors
    val shape = RoundedCornerShape(ThemeTokens.cornerRadius.full)
    Box(
        modifier =
            Modifier
                .clip(shape)
                .background(colors.tag.brandBackground, shape)
                .padding(horizontal = BADGE_HORIZONTAL_DP.dp, vertical = BADGE_VERTICAL_DP.dp),
    ) {
        Text(
            text = text,
            style = ThemeTokens.typography.overline,
            color = colors.tag.brandForeground,
            maxLines = 1,
        )
    }
}
