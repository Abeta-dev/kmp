package dev.abeta.kmp.layout.snippet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.modifiers.applyLayoutConfig
import dev.abeta.kmp.atoms.modifiers.applyMotionSpec
import dev.abeta.kmp.atoms.modifiers.pressBounce
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ComposedBlockSnippetData
import dev.abeta.kmp.contracts.ComposedNode
import dev.abeta.kmp.contracts.ComposedNodeKind
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.core.result.AppResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/**
 * Maximum recursive nesting depth enforced by the Atomic Snippet Molding Engine (`"composed_block"`).
 */
internal const val MAX_COMPOSED_DEPTH = 6

internal fun canRenderComposedDepth(depth: Int): Boolean = depth in 0..MAX_COMPOSED_DEPTH

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
                formValues = formValues,
                onFormValueChange = onFormValueChange,
                onAction = onAction,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
    private fun RenderComposedNode(
        node: ComposedNode,
        depth: Int,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
        modifier: Modifier = Modifier,
    ) {
        if (!canRenderComposedDepth(depth)) return

        val taggedModifier =
            modifier
                .let { base ->
                    val id = node.nodeId?.takeIf { it.isNotBlank() }
                    if (id != null) base.testTag("composed_node_$id") else base
                }.applyMotionSpec(motion = node.motion, motionKey = node.nodeId ?: "${node.kind}_$depth")

        if (node.kind == ComposedNodeKind.CARD) {
            RenderCardNode(node, depth, formValues, onFormValueChange, onAction, taggedModifier)
            return
        }

        val styledModifier =
            taggedModifier
                .applyLayoutConfig(node.layout)
                .let { base ->
                    val clickAction = node.onClick
                    if (clickAction != null) base.pressBounce().clickable { onAction(clickAction) } else base
                }

        when (node.kind) {
            ComposedNodeKind.COLUMN,
            ComposedNodeKind.ROW,
            ComposedNodeKind.BOX,
            ComposedNodeKind.FLOW_ROW,
            -> {
                RenderContainerNode(node, depth, formValues, onFormValueChange, onAction, styledModifier)
            }

            else -> {
                RenderComposedLeafNode(node, formValues, onFormValueChange, onAction, styledModifier)
            }
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
    private fun RenderCardNode(
        node: ComposedNode,
        depth: Int,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
        modifier: Modifier,
    ) {
        val spacingDp = node.spacingDp.coerceAtLeast(0).dp
        UiSurfaceCard(
            modifier = modifier,
            layout = node.layout,
            onClick = node.onClick?.let { action -> { onAction(action) } },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = resolveComposedVerticalArrangement(node.mainAxisArrangement, spacingDp),
                horizontalAlignment = resolveComposedHorizontalAlignment(node.horizontalAlignment, node.alignment),
            ) {
                node.children.forEach { child ->
                    RenderComposedNode(child, depth + 1, formValues, onFormValueChange, onAction)
                }
            }
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
    private fun RenderContainerNode(
        node: ComposedNode,
        depth: Int,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
        modifier: Modifier,
    ) {
        val spacingDp = node.spacingDp.coerceAtLeast(0).dp
        when (node.kind) {
            ComposedNodeKind.COLUMN -> {
                RenderColumnNode(node, depth, spacingDp, formValues, onFormValueChange, onAction, modifier)
            }

            ComposedNodeKind.ROW -> {
                RenderRowNode(node, depth, spacingDp, formValues, onFormValueChange, onAction, modifier)
            }

            ComposedNodeKind.BOX -> {
                RenderBoxNode(node, depth, formValues, onFormValueChange, onAction, modifier)
            }

            ComposedNodeKind.FLOW_ROW -> {
                RenderFlowRowNode(node, depth, spacingDp, formValues, onFormValueChange, onAction, modifier)
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
        spacingDp: Dp,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
        modifier: Modifier,
    ) {
        Column(
            modifier = modifier,
            verticalArrangement = resolveComposedVerticalArrangement(node.mainAxisArrangement, spacingDp),
            horizontalAlignment = resolveComposedHorizontalAlignment(node.horizontalAlignment, node.alignment),
        ) {
            node.children.forEach { child ->
                val childMod = child.weight?.takeIf { it > 0f }?.let { Modifier.weight(it) } ?: Modifier
                RenderComposedNode(child, depth + 1, formValues, onFormValueChange, onAction, childMod)
            }
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
    private fun RenderRowNode(
        node: ComposedNode,
        depth: Int,
        spacingDp: Dp,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
        modifier: Modifier,
    ) {
        Row(
            modifier = modifier,
            horizontalArrangement = resolveComposedHorizontalArrangement(node.mainAxisArrangement, spacingDp),
            verticalAlignment = resolveComposedVerticalAlignment(node.verticalAlignment),
        ) {
            node.children.forEach { child ->
                val childMod = child.weight?.takeIf { it > 0f }?.let { Modifier.weight(it) } ?: Modifier
                RenderComposedNode(child, depth + 1, formValues, onFormValueChange, onAction, childMod)
            }
        }
    }

    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
    private fun RenderBoxNode(
        node: ComposedNode,
        depth: Int,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
        modifier: Modifier,
    ) {
        val alignment =
            resolveComposedBoxAlignment(
                horizontalAlignment = node.horizontalAlignment,
                verticalAlignment = node.verticalAlignment,
                fallbackToken = node.alignment,
            )
        Box(modifier = modifier, contentAlignment = alignment) {
            node.children.forEach { child ->
                RenderComposedNode(child, depth + 1, formValues, onFormValueChange, onAction)
            }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    @Suppress("ktlint:standard:function-naming", "FunctionName", "LongParameterList")
    private fun RenderFlowRowNode(
        node: ComposedNode,
        depth: Int,
        spacingDp: Dp,
        formValues: Map<String, String>,
        onFormValueChange: (String, String) -> Unit,
        onAction: (ActionRef) -> Unit,
        modifier: Modifier,
    ) {
        FlowRow(
            modifier = modifier,
            horizontalArrangement = resolveComposedHorizontalArrangement(node.mainAxisArrangement, spacingDp),
            verticalArrangement = Arrangement.spacedBy(spacingDp),
        ) {
            node.children.forEach { child ->
                RenderComposedNode(child, depth + 1, formValues, onFormValueChange, onAction)
            }
        }
    }
}
