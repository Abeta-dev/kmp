package dev.abeta.kmp.layout.snippet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.atoms.modifiers.applyMotionSpec
import dev.abeta.kmp.atoms.modifiers.pressBounce
import dev.abeta.kmp.atoms.modifiers.toPaddingValues
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ComposedBlockSnippetData
import dev.abeta.kmp.contracts.ComposedNode
import dev.abeta.kmp.contracts.ComposedNodeKind
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.TrackingContract
import dev.abeta.kmp.core.result.AppResult
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Maximum recursive nesting depth enforced by the Atomic Snippet Molding Engine (`"composed_block"`).
 */
internal const val MAX_COMPOSED_DEPTH = 6
private const val DEFAULT_COMPOSED_GRID_COLUMNS = 2

internal fun canRenderComposedDepth(depth: Int): Boolean = depth in 0..MAX_COMPOSED_DEPTH

private data class ComposedRenderContext(
    val snippetId: String,
    val formValues: Map<String, String>,
    val onFormValueChange: (String, String) -> Unit,
    val onAction: (ActionRef) -> Unit,
    val onNodeImpression: (String, TrackingContract) -> Unit,
)

internal class ComposedBlockSnippetRenderer(
    json: Json,
) : SnippetRenderer<ComposedBlockSnippetData> {
    override val snippetType: String = SnippetTypes.COMPOSED_BLOCK
    private val decoder = kotlinxSnippetDecoder<ComposedBlockSnippetData>(json)

    override fun decode(data: JsonObject): AppResult<ComposedBlockSnippetData> = decoder(data)

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName")
    override fun Render(
        model: ComposedBlockSnippetData,
        envelope: SnippetEnvelope,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
    ) {
        val nodeImpressionHandler = LocalNodeImpressionHandler.current
        val context =
            ComposedRenderContext(
                snippetId = envelope.snippetId,
                formValues = formValues,
                onFormValueChange = onFormValueChange,
                onAction = onAction,
                onNodeImpression = nodeImpressionHandler,
            )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .applyLayoutConfig(model.layout)
                    .testTag("composed_block_${model.blockId}"),
        ) {
            RenderComposedNode(
                node = model.root,
                depth = 0,
                nodePath = model.blockId,
                context = context,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun RenderComposedNode(
    node: ComposedNode,
    depth: Int,
    nodePath: String,
    context: ComposedRenderContext,
    modifier: Modifier = Modifier,
) {
    if (!canRenderComposedDepth(depth)) return

    val nodeKey =
        node.nodeId?.takeIf { it.isNotBlank() }
            ?: "${context.snippetId}:$nodePath:${node.kind.name.lowercase()}"
    node.tracking?.let { tracking ->
        LaunchedEffect(nodeKey, tracking.eventName) {
            context.onNodeImpression(nodeKey, tracking)
        }
    }

    val taggedModifier =
        modifier
            .let { base ->
                val id = node.nodeId?.takeIf { it.isNotBlank() }
                if (id != null) base.testTag("composed_node_$id") else base
            }.applyMotionSpec(motion = node.motion, motionKey = nodeKey)

    if (node.kind == ComposedNodeKind.CARD) {
        RenderCardNode(node, depth, nodePath, context, taggedModifier)
        return
    }

    val styledModifier =
        applyComposedLayoutAndClick(
            base = taggedModifier,
            node = node,
            onAction = context.onAction,
        )

    when (node.kind) {
        ComposedNodeKind.COLUMN,
        ComposedNodeKind.ROW,
        ComposedNodeKind.BOX,
        ComposedNodeKind.FLOW_ROW,
        ComposedNodeKind.SCROLL_ROW,
        ComposedNodeKind.GRID,
        -> {
            RenderContainerNode(node, depth, nodePath, context, styledModifier)
        }

        else -> {
            RenderComposedLeafNode(
                node = node,
                formValues = context.formValues,
                onFormValueChange = context.onFormValueChange,
                onAction = context.onAction,
                modifier = styledModifier,
            )
        }
    }
}

@Composable
private fun applyComposedLayoutAndClick(
    base: Modifier,
    node: ComposedNode,
    onAction: (ActionRef) -> Unit,
): Modifier {
    val clickAction = resolveComposedNodeClickAction(node)
    val innerPadding = node.layout?.padding
    return when {
        clickAction != null && innerPadding != null -> {
            base
                .applyLayoutConfig(node.layout?.copy(padding = null))
                .pressBounce()
                .clickable { onAction(clickAction) }
                .padding(innerPadding.toPaddingValues())
        }

        clickAction != null -> {
            base
                .applyLayoutConfig(node.layout)
                .pressBounce()
                .clickable { onAction(clickAction) }
        }

        else -> {
            base.applyLayoutConfig(node.layout)
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun RenderCardNode(
    node: ComposedNode,
    depth: Int,
    nodePath: String,
    context: ComposedRenderContext,
    modifier: Modifier,
) {
    val spacingDp = node.spacingDp.coerceAtLeast(0).dp
    val clickAction = resolveComposedNodeClickAction(node)
    UiSurfaceCard(
        modifier = modifier,
        layout = node.layout,
        onClick = clickAction?.let { action -> { context.onAction(action) } },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = resolveComposedVerticalArrangement(node.mainAxisArrangement, spacingDp),
            horizontalAlignment = resolveComposedHorizontalAlignment(node.horizontalAlignment, node.alignment),
        ) {
            node.children.forEachIndexed { idx, child ->
                RenderComposedNode(child, depth + 1, "$nodePath/$idx", context)
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun RenderContainerNode(
    node: ComposedNode,
    depth: Int,
    nodePath: String,
    context: ComposedRenderContext,
    modifier: Modifier,
) {
    val spacingDp = node.spacingDp.coerceAtLeast(0).dp
    when (node.kind) {
        ComposedNodeKind.COLUMN -> {
            RenderColumnNode(node, depth, nodePath, spacingDp, context, modifier)
        }

        ComposedNodeKind.ROW -> {
            RenderRowNode(node, depth, nodePath, spacingDp, context, modifier)
        }

        ComposedNodeKind.BOX -> {
            RenderBoxNode(node, depth, nodePath, context, modifier)
        }

        ComposedNodeKind.FLOW_ROW -> {
            RenderFlowRowNode(node, depth, nodePath, spacingDp, context, modifier)
        }

        ComposedNodeKind.SCROLL_ROW -> {
            RenderScrollRowNode(node, depth, nodePath, spacingDp, context, modifier)
        }

        ComposedNodeKind.GRID -> {
            RenderGridNode(node, depth, nodePath, spacingDp, context, modifier)
        }

        else -> {
            // Non-container nodes are rendered by RenderComposedLeafNode.
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun RenderColumnNode(
    node: ComposedNode,
    depth: Int,
    nodePath: String,
    spacingDp: Dp,
    context: ComposedRenderContext,
    modifier: Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = resolveComposedVerticalArrangement(node.mainAxisArrangement, spacingDp),
        horizontalAlignment = resolveComposedHorizontalAlignment(node.horizontalAlignment, node.alignment),
    ) {
        node.children.forEachIndexed { idx, child ->
            val resolvedWeight = (child.weight ?: child.spacer?.weight)?.takeIf { it.isFinite() && it > 0f }
            val childMod = resolvedWeight?.let { Modifier.weight(it) } ?: Modifier
            RenderComposedNode(child, depth + 1, "$nodePath/$idx", context, childMod)
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun RenderRowNode(
    node: ComposedNode,
    depth: Int,
    nodePath: String,
    spacingDp: Dp,
    context: ComposedRenderContext,
    modifier: Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = resolveComposedHorizontalArrangement(node.mainAxisArrangement, spacingDp),
        verticalAlignment = resolveComposedVerticalAlignment(node.verticalAlignment),
    ) {
        node.children.forEachIndexed { idx, child ->
            val resolvedWeight = (child.weight ?: child.spacer?.weight)?.takeIf { it.isFinite() && it > 0f }
            val childMod = resolvedWeight?.let { Modifier.weight(it) } ?: Modifier
            RenderComposedNode(child, depth + 1, "$nodePath/$idx", context, childMod)
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun RenderScrollRowNode(
    node: ComposedNode,
    depth: Int,
    nodePath: String,
    spacingDp: Dp,
    context: ComposedRenderContext,
    modifier: Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = resolveComposedHorizontalArrangement(node.mainAxisArrangement, spacingDp),
        verticalAlignment = resolveComposedVerticalAlignment(node.verticalAlignment),
    ) {
        node.children.forEachIndexed { idx, child ->
            RenderComposedNode(child, depth + 1, "$nodePath/$idx", context)
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun RenderGridNode(
    node: ComposedNode,
    depth: Int,
    nodePath: String,
    spacingDp: Dp,
    context: ComposedRenderContext,
    modifier: Modifier,
) {
    val cols =
        resolveResponsiveColumns(
            columns = (node.columns ?: DEFAULT_COMPOSED_GRID_COLUMNS).coerceAtLeast(1),
            columnsByWidth = node.columnsByWidth,
            widthClass = ThemeTokens.adaptiveMetrics.widthClass,
        )
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(spacingDp),
    ) {
        node.children.chunked(cols).forEachIndexed { rowIdx, rowChildren ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacingDp),
                verticalAlignment = resolveComposedVerticalAlignment(node.verticalAlignment),
            ) {
                rowChildren.forEachIndexed { colIdx, child ->
                    val childIdx = rowIdx * cols + colIdx
                    Box(modifier = Modifier.weight(1f)) {
                        RenderComposedNode(child, depth + 1, "$nodePath/$childIdx", context)
                    }
                }
                repeat(cols - rowChildren.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun RenderBoxNode(
    node: ComposedNode,
    depth: Int,
    nodePath: String,
    context: ComposedRenderContext,
    modifier: Modifier,
) {
    val parentAlignment =
        resolveComposedBoxAlignment(
            horizontalAlignment = node.horizontalAlignment,
            verticalAlignment = node.verticalAlignment,
            fallbackToken = node.alignment,
        )
    Box(modifier = modifier, contentAlignment = parentAlignment) {
        node.children.forEachIndexed { idx, child ->
            val childMod =
                child.boxAlignment
                    ?.takeIf { it.isNotBlank() }
                    ?.let { Modifier.align(resolveChildBoxAlignment(it, fallback = parentAlignment)) }
                    ?: Modifier
            RenderComposedNode(child, depth + 1, "$nodePath/$idx", context, childMod)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
private fun RenderFlowRowNode(
    node: ComposedNode,
    depth: Int,
    nodePath: String,
    spacingDp: Dp,
    context: ComposedRenderContext,
    modifier: Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = resolveComposedHorizontalArrangement(node.mainAxisArrangement, spacingDp),
        verticalArrangement = Arrangement.spacedBy(spacingDp),
    ) {
        node.children.forEachIndexed { idx, child ->
            RenderComposedNode(child, depth + 1, "$nodePath/$idx", context)
        }
    }
}
