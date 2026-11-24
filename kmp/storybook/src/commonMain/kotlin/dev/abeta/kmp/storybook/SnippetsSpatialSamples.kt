package dev.abeta.kmp.storybook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.abeta.kmp.actions.model.ActionTypes
import dev.abeta.kmp.atoms.atoms.UiButton
import dev.abeta.kmp.atoms.atoms.UiSurfaceCard
import dev.abeta.kmp.atoms.atoms.UiTag
import dev.abeta.kmp.contracts.ActionRef
import dev.abeta.kmp.contracts.ButtonAtom
import dev.abeta.kmp.contracts.ButtonSize
import dev.abeta.kmp.contracts.CardSnippetData
import dev.abeta.kmp.contracts.ColorTokenRef
import dev.abeta.kmp.contracts.CustomSlotSnippetData
import dev.abeta.kmp.contracts.GraphCanvasSnippetData
import dev.abeta.kmp.contracts.GraphEdgeSpec
import dev.abeta.kmp.contracts.GraphEdgeStyle
import dev.abeta.kmp.contracts.GraphNodeSpec
import dev.abeta.kmp.contracts.SnippetEnvelope
import dev.abeta.kmp.contracts.SnippetTypes
import dev.abeta.kmp.contracts.TagAtom
import dev.abeta.kmp.contracts.TagVariant
import dev.abeta.kmp.layout.snippet.CustomSlotContext
import dev.abeta.kmp.layout.snippet.CustomSlotRegistry
import dev.abeta.kmp.tokens.ThemeTokens
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private const val CUSTOM_SLOT_MIN_HEIGHT_DP = 148
private const val GRAPH_CANVAS_HEIGHT_DP = 340
private const val GRAPH_MIN_ZOOM = 0.5f
private const val GRAPH_MAX_ZOOM = 2.0f
private const val NODE_MAYA_X = 24f
private const val NODE_MAYA_Y = 24f
private const val NODE_ARJUN_X = 228f
private const val NODE_ARJUN_Y = 112f
private const val NODE_KABIR_X = 32f
private const val NODE_KABIR_Y = 212f
private const val NODE_ELENA_X = 244f
private const val NODE_ELENA_Y = 228f

/**
 * Provides canonical [SnippetEnvelope] fixtures for `custom_slot` and `graph_canvas` along with
 * a live Storybook [CustomSlotRegistry] demonstrating bidirectional host-slot events.
 */
internal object SnippetsSpatialSamples {
    const val STORYBOOK_SLOT_TYPE = "storybook_live_host_widget"

    fun allItems(): List<SnippetSampleItem> =
        listOf(
            SnippetSampleItem("Custom Slot", buildCustomSlot()),
            SnippetSampleItem("Graph Canvas", buildGraphCanvas()),
        )

    fun createStorybookCustomSlotRegistry(): CustomSlotRegistry =
        CustomSlotRegistry
            .builder()
            .register(STORYBOOK_SLOT_TYPE) { context ->
                StorybookLiveCustomSlotContent(context = context)
            }.build()

    private fun buildCustomSlot(): SnippetEnvelope {
        val fallbackCard =
            CardSnippetData(
                title = SnippetsSamples.txt("Host Slot Fallback", "title.small"),
                description = SnippetsSamples.txt("Rendered when the host does not bind this slot_type."),
                layout = SnippetsSamples.cardLayout,
            )
        val slot =
            CustomSlotSnippetData(
                slotId = "story_host_canvas_slot",
                slotType = STORYBOOK_SLOT_TYPE,
                minHeightDp = CUSTOM_SLOT_MIN_HEIGHT_DP,
                initialState =
                    buildJsonObject {
                        put("title", "Native Host Canvas Bridge")
                        put("subtitle", "Bound via LocalCustomSlotRegistry · emits enriched SDUI ActionRefs")
                        put("region", "us-west-2")
                    },
                onEvent =
                    mapOf(
                        "ping" to
                            ActionRef(
                                id = "story_custom_slot_ping",
                                type = ActionTypes.SHOW_TOAST,
                                payload =
                                    buildJsonObject {
                                        put("message", "Custom slot event dispatched to ActionEngine")
                                    },
                            ),
                    ),
                fallback =
                    SnippetEnvelope(
                        snippetId = "story_custom_slot_fallback",
                        snippetType = SnippetTypes.CARD,
                        data = SnippetsSamples.encode(fallbackCard),
                    ),
                layout = SnippetsSamples.cardLayout,
            )
        return SnippetEnvelope(
            snippetId = "story_custom_slot",
            snippetType = SnippetTypes.CUSTOM_SLOT,
            data = SnippetsSamples.encode(slot),
        )
    }

    private fun buildGraphCanvas(): SnippetEnvelope {
        val graph =
            GraphCanvasSnippetData(
                canvasId = "story_people_relationship_map",
                title = SnippetsSamples.txt("People Relationship Map", "title.medium"),
                subtitle =
                    SnippetsSamples.txt(
                        "Drag nodes, pan, or zoom · Bezier, straight, and dashed edges",
                        "body.small",
                        "text.secondary",
                    ),
                heightDp = GRAPH_CANVAS_HEIGHT_DP,
                nodes = buildPeopleNodes(),
                edges = buildPeopleEdges(),
                allowPan = true,
                allowZoom = true,
                allowNodeDrag = true,
                minZoom = GRAPH_MIN_ZOOM,
                maxZoom = GRAPH_MAX_ZOOM,
                onNodeMoved = ActionRef(id = "story_graph_node_moved", type = ActionTypes.SAVE_STATE),
                onCanvasTap = ActionRef(id = "story_graph_canvas_tap", type = ActionTypes.TRACK_EVENT),
                layout = SnippetsSamples.cardLayout,
            )
        return SnippetEnvelope(
            snippetId = "story_graph_canvas",
            snippetType = SnippetTypes.GRAPH_CANVAS,
            data = SnippetsSamples.encode(graph),
        )
    }

    private fun buildPeopleNodes(): List<GraphNodeSpec> =
        listOf(
            GraphNodeSpec(
                nodeId = "maya",
                label = "Maya Lin",
                subtitle = "Staff Design Lead",
                badge = "PINNED",
                x = NODE_MAYA_X,
                y = NODE_MAYA_Y,
                bgColor = ColorTokenRef("brand.container"),
                borderColor = ColorTokenRef("brand.primary"),
                iconName = "star",
                draggable = false,
                pinned = true,
                onClick = toastAction("story_graph_click_maya", "Selected Maya Lin (Pinned Lead)"),
            ),
            GraphNodeSpec(
                nodeId = "arjun",
                label = "Arjun Nair",
                subtitle = "KMP Architect",
                badge = "KMP",
                x = NODE_ARJUN_X,
                y = NODE_ARJUN_Y,
                iconName = "code",
                onClick = toastAction("story_graph_click_arjun", "Selected Arjun Nair"),
            ),
            GraphNodeSpec(
                nodeId = "kabir",
                label = "Kabir Sharma",
                subtitle = "Go BFF Staff Eng",
                badge = "BFF",
                x = NODE_KABIR_X,
                y = NODE_KABIR_Y,
                iconName = "layers",
                onClick = toastAction("story_graph_click_kabir", "Selected Kabir Sharma"),
            ),
            GraphNodeSpec(
                nodeId = "elena",
                label = "Elena Vance",
                subtitle = "Platform PM",
                badge = "PM",
                x = NODE_ELENA_X,
                y = NODE_ELENA_Y,
                bgColor = ColorTokenRef("status.success_container"),
                borderColor = ColorTokenRef("status.success"),
                iconName = "check",
                onClick = toastAction("story_graph_click_elena", "Selected Elena Vance"),
            ),
        )

    private fun buildPeopleEdges(): List<GraphEdgeSpec> =
        listOf(
            GraphEdgeSpec(
                edgeId = "edge_maya_arjun",
                fromNodeId = "maya",
                toNodeId = "arjun",
                label = "Mentors",
                style = GraphEdgeStyle.BEZIER,
                color = ColorTokenRef("brand.primary"),
                highlighted = true,
                onClick = ActionRef(id = "story_edge_mentors", type = ActionTypes.TRACK_EVENT),
            ),
            GraphEdgeSpec(
                edgeId = "edge_arjun_kabir",
                fromNodeId = "arjun",
                toNodeId = "kabir",
                label = "Pairs",
                style = GraphEdgeStyle.STRAIGHT,
            ),
            GraphEdgeSpec(
                edgeId = "edge_maya_kabir",
                fromNodeId = "maya",
                toNodeId = "kabir",
                label = "Reviews",
                style = GraphEdgeStyle.DASHED,
                color = ColorTokenRef("status.info"),
            ),
            GraphEdgeSpec(
                edgeId = "edge_arjun_elena",
                fromNodeId = "arjun",
                toNodeId = "elena",
                label = "Ships",
                style = GraphEdgeStyle.BEZIER,
                color = ColorTokenRef("status.success"),
                highlighted = true,
            ),
        )

    private fun toastAction(
        id: String,
        message: String,
    ): ActionRef =
        ActionRef(
            id = id,
            type = ActionTypes.SHOW_TOAST,
            payload = buildJsonObject { put("message", message) },
        )
}

@Composable
@Suppress("ktlint:standard:function-naming", "FunctionName")
private fun StorybookLiveCustomSlotContent(context: CustomSlotContext) {
    val spacing = ThemeTokens.spacing
    val typography = ThemeTokens.typography
    val colors = ThemeTokens.colors
    var pingCount by remember(context.slotId) { mutableIntStateOf(0) }
    val title = context.initialState["title"]?.jsonPrimitive?.content ?: "Custom Host Slot"
    val subtitle = context.initialState["subtitle"]?.jsonPrimitive?.content ?: ""
    val region = context.initialState["region"]?.jsonPrimitive?.content ?: "local"

    UiSurfaceCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.space8)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = title, style = typography.titleSmall, color = colors.text.primary)
                UiTag(TagAtom(label = "HOST BOUND · $region", variant = TagVariant.SUCCESS))
            }
            if (subtitle.isNotBlank()) {
                Text(text = subtitle, style = typography.bodySmall, color = colors.text.secondary)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Slot '${context.slotId}' · events emitted: $pingCount",
                    style = typography.caption,
                    color = colors.text.tertiary,
                )
                UiButton(
                    atom =
                        ButtonAtom(
                            label = "Emit Slot Event",
                            size = ButtonSize.SMALL,
                            onClick = ActionRef(id = "slot_btn_local", type = ActionTypes.TRACK_EVENT),
                        ),
                    onAction = {
                        pingCount += 1
                        context.emitEvent(
                            eventName = "ping",
                            extraPayload =
                                buildJsonObject {
                                    put("slot_id", context.slotId)
                                    put("ping_count", pingCount)
                                },
                        )
                    },
                )
            }
        }
    }
}
