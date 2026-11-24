package dev.abeta.kmp.layout.snippet

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.GraphNodeSpec
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

internal const val MIN_POSITIVE_GRAPH_ZOOM = 0.1f
internal const val GRAPH_ZOOM_STEP = 0.25f
internal const val DEFAULT_GRAPH_NODE_WIDTH_DP = 152f
internal const val DEFAULT_GRAPH_NODE_HEIGHT_DP = 68f
internal const val GRAPH_ARROW_LENGTH_PX = 12f
internal const val GRAPH_ARROW_CURVE_T = 0.78f

private const val BEZIER_HANDLE_RATIO = 0.5f
private const val BEZIER_END_WEIGHT = 0.125f
private const val BEZIER_CONTROL_WEIGHT = 0.375f
private const val CUBIC_DERIVATIVE_SCALE = 3f
private const val ARROW_HALF_ANGLE_RAD = 0.48

/**
 * Enriches a node-moved [ActionRef] by preserving its existing payload entries and merging
 * `canvas_id`, `node_id`, `x`, and `y` coordinates.
 */
internal fun enrichNodeMovedAction(
    action: ActionRef,
    canvasId: String,
    nodeId: String,
    x: Float,
    y: Float,
): ActionRef {
    val enrichedPayload =
        buildJsonObject {
            action.payload.forEach { (key, value) -> put(key, value) }
            put("canvas_id", canvasId)
            put("node_id", nodeId)
            put("x", x)
            put("y", y)
        }
    return action.copy(payload = enrichedPayload)
}

/**
 * Computes symmetric cubic Bezier control handles oriented along the dominant axis between
 * [start] and [end].
 */
internal fun computeBezierControlPoints(
    start: Offset,
    end: Offset,
): Pair<Offset, Offset> {
    val dx = end.x - start.x
    val dy = end.y - start.y
    return if (abs(dx) >= abs(dy)) {
        val handle = dx * BEZIER_HANDLE_RATIO
        Offset(start.x + handle, start.y) to Offset(end.x - handle, end.y)
    } else {
        val handle = dy * BEZIER_HANDLE_RATIO
        Offset(start.x, start.y + handle) to Offset(end.x, end.y - handle)
    }
}

/**
 * Computes the midpoint (`t = 0.5`) of the cubic Bezier edge connecting [start] and [end].
 */
internal fun computeBezierMidpoint(
    start: Offset,
    end: Offset,
): Offset {
    val (cp1, cp2) = computeBezierControlPoints(start, end)
    val x =
        BEZIER_END_WEIGHT * start.x +
            BEZIER_CONTROL_WEIGHT * cp1.x +
            BEZIER_CONTROL_WEIGHT * cp2.x +
            BEZIER_END_WEIGHT * end.x
    val y =
        BEZIER_END_WEIGHT * start.y +
            BEZIER_CONTROL_WEIGHT * cp1.y +
            BEZIER_CONTROL_WEIGHT * cp2.y +
            BEZIER_END_WEIGHT * end.y
    return Offset(x, y)
}

/**
 * Evaluates a point and tangent direction on the cubic Bezier curve between [start] and [end]
 * at parameter [t] in `0f..1f`.
 */
internal fun evaluateBezierPointAndTangent(
    start: Offset,
    end: Offset,
    t: Float,
): Pair<Offset, Offset> {
    val (cp1, cp2) = computeBezierControlPoints(start, end)
    val clamped = t.coerceIn(0f, 1f)
    val inv = 1f - clamped
    val invSq = inv * inv
    val tSq = clamped * clamped
    val point =
        Offset(
            x =
                invSq * inv * start.x +
                    CUBIC_DERIVATIVE_SCALE * invSq * clamped * cp1.x +
                    CUBIC_DERIVATIVE_SCALE * inv * tSq * cp2.x +
                    tSq * clamped * end.x,
            y =
                invSq * inv * start.y +
                    CUBIC_DERIVATIVE_SCALE * invSq * clamped * cp1.y +
                    CUBIC_DERIVATIVE_SCALE * inv * tSq * cp2.y +
                    tSq * clamped * end.y,
        )
    val tangent =
        Offset(
            x =
                CUBIC_DERIVATIVE_SCALE * invSq * (cp1.x - start.x) +
                    2f * CUBIC_DERIVATIVE_SCALE * inv * clamped * (cp2.x - cp1.x) +
                    CUBIC_DERIVATIVE_SCALE * tSq * (end.x - cp2.x),
            y =
                CUBIC_DERIVATIVE_SCALE * invSq * (cp1.y - start.y) +
                    2f * CUBIC_DERIVATIVE_SCALE * inv * clamped * (cp2.y - cp1.y) +
                    CUBIC_DERIVATIVE_SCALE * tSq * (end.y - cp2.y),
        )
    return point to tangent
}

/**
 * Clamps [zoom] within the valid inclusive `[minZoom, maxZoom]` range, guarding against invalid
 * non-positive or inverted bounds.
 */
internal fun clampGraphZoom(
    zoom: Float,
    minZoom: Float,
    maxZoom: Float,
): Float {
    val safeMin = minZoom.coerceAtLeast(MIN_POSITIVE_GRAPH_ZOOM)
    val safeMax = maxZoom.coerceAtLeast(safeMin)
    return zoom.coerceIn(safeMin, safeMax)
}

/**
 * Converts a world-space coordinate in dp to viewport pixel space under [zoom] and [panPx].
 */
internal fun worldToViewportPx(
    worldCoordDp: Offset,
    zoom: Float,
    panPx: Offset,
    density: Float,
): Offset =
    Offset(
        x = worldCoordDp.x * density * zoom + panPx.x,
        y = worldCoordDp.y * density * zoom + panPx.y,
    )

/**
 * Computes the two wing endpoints of a directional arrowhead whose tip sits at [tip] pointing along
 * [direction].
 */
internal fun computeArrowheadWings(
    tip: Offset,
    direction: Offset,
    lengthPx: Float = GRAPH_ARROW_LENGTH_PX,
): Pair<Offset, Offset> {
    val angle = atan2(direction.y.toDouble(), direction.x.toDouble())
    val leftAngle = angle + ARROW_HALF_ANGLE_RAD
    val rightAngle = angle - ARROW_HALF_ANGLE_RAD
    val leftWing =
        Offset(
            x = tip.x - (lengthPx * cos(leftAngle)).toFloat(),
            y = tip.y - (lengthPx * sin(leftAngle)).toFloat(),
        )
    val rightWing =
        Offset(
            x = tip.x - (lengthPx * cos(rightAngle)).toFloat(),
            y = tip.y - (lengthPx * sin(rightAngle)).toFloat(),
        )
    return leftWing to rightWing
}

/**
 * Computes viewport pixel centers for every node in [nodes] using measured sizes when available
 * and default node dimensions prior to first layout.
 */
@Suppress("LongParameterList")
internal fun resolveNodeScreenCenters(
    nodes: List<GraphNodeSpec>,
    nodePositions: Map<String, Offset>,
    nodeSizes: Map<String, IntSize>,
    zoom: Float,
    panPx: Offset,
    density: Float,
): Map<String, Offset> =
    nodes.associate { node ->
        val worldPos = nodePositions[node.nodeId] ?: Offset(node.x, node.y)
        val topLeft = worldToViewportPx(worldPos, zoom, panPx, density)
        val measured = nodeSizes[node.nodeId]
        val widthPx = (measured?.width?.toFloat() ?: (DEFAULT_GRAPH_NODE_WIDTH_DP * density)) * zoom
        val heightPx = (measured?.height?.toFloat() ?: (DEFAULT_GRAPH_NODE_HEIGHT_DP * density)) * zoom
        node.nodeId to Offset(topLeft.x + widthPx / 2f, topLeft.y + heightPx / 2f)
    }
