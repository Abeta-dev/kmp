@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.contracts

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

private const val DEFAULT_MARQUEE_VELOCITY_DP_PER_SEC = 48
private const val DEFAULT_MARQUEE_SEPARATOR = "•"
private const val DEFAULT_COMPOSED_SPACING_DP = 8

/**
 * Typed payload for an urgency, progress, or contextual nudge strip snippet (`"nudge_bar"`).
 */
@Serializable
data class NudgeBarSnippetData(
    @SerialName("nudge_id")
    val nudgeId: String,
    val title: TextAtom,
    val subtitle: TextAtom? = null,
    val icon: IconAtom? = null,
    val badge: TagAtom? = null,
    val countdown: CountdownAtom? = null,
    val progress: Float? = null,
    val cta: ButtonAtom? = null,
    val dismissible: Boolean = true,
    @SerialName("pointer_edge")
    val pointerEdge: String? = null,
    @SerialName("on_dismiss")
    val onDismiss: ActionRef? = null,
    @SerialName("on_click")
    val onClick: ActionRef? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Single scrolling offer or status item inside [MarqueeStripSnippetData].
 */
@Serializable
data class MarqueeItem(
    val id: String,
    val text: String,
    val icon: IconAtom? = null,
    val badge: TagAtom? = null,
    @SerialName("on_click")
    val onClick: ActionRef? = null,
)

/**
 * Typed payload for a continuous horizontal ticker strip snippet (`"marquee_strip"`).
 */
@Serializable
data class MarqueeStripSnippetData(
    @SerialName("strip_id")
    val stripId: String,
    val items: List<MarqueeItem> = emptyList(),
    @SerialName("velocity_dp_per_sec")
    val velocityDpPerSec: Int = DEFAULT_MARQUEE_VELOCITY_DP_PER_SEC,
    val separator: String = DEFAULT_MARQUEE_SEPARATOR,
    val layout: LayoutConfig? = null,
)

/**
 * Single milestone stage rendered inside [OrderTrackerSnippetData].
 */
@Serializable
data class OrderTrackerStep(
    val id: String,
    val label: String,
    val caption: String? = null,
    val icon: IconAtom? = null,
    val completed: Boolean = false,
    val active: Boolean = false,
)

/**
 * Typed payload for an animated multi-stage live order tracker card snippet (`"order_tracker"`).
 */
@Serializable
data class OrderTrackerSnippetData(
    @SerialName("tracker_id")
    val trackerId: String,
    @SerialName("order_code")
    val orderCode: String,
    val headline: TextAtom,
    @SerialName("eta_countdown")
    val etaCountdown: CountdownAtom? = null,
    @SerialName("eta_badge")
    val etaBadge: TagAtom? = null,
    @SerialName("rider_name")
    val riderName: String? = null,
    @SerialName("rider_caption")
    val riderCaption: String? = null,
    val steps: List<OrderTrackerStep> = emptyList(),
    @SerialName("primary_action")
    val primaryAction: ButtonAtom? = null,
    @SerialName("secondary_action")
    val secondaryAction: ButtonAtom? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Node kind discriminator for recursive server-molded [ComposedNode] trees.
 */
@Serializable
enum class ComposedNodeKind {
    @SerialName("column")
    COLUMN,

    @SerialName("row")
    ROW,

    @SerialName("box")
    BOX,

    @SerialName("card")
    CARD,

    @SerialName("flow_row")
    FLOW_ROW,

    @SerialName("text")
    TEXT,

    @SerialName("button")
    BUTTON,

    @SerialName("tag")
    TAG,

    @SerialName("icon")
    ICON,

    @SerialName("image")
    IMAGE,

    @SerialName("avatar")
    AVATAR,

    @SerialName("progress")
    PROGRESS,

    @SerialName("divider")
    DIVIDER,

    @SerialName("spacer")
    SPACER,

    @SerialName("stepper")
    STEPPER,

    @SerialName("countdown")
    COUNTDOWN,

    @SerialName("rolling_counter")
    ROLLING_COUNTER,
}

/**
 * Recursive node in the Atomic Snippet Molding Engine tree rendered by `"composed_block"`.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ComposedNode(
    @SerialName("node_id")
    @JsonNames("id")
    val nodeId: String? = null,
    val kind: ComposedNodeKind,
    @SerialName("spacing_dp")
    val spacingDp: Int = DEFAULT_COMPOSED_SPACING_DP,
    val alignment: AlignmentToken? = null,
    @SerialName("horizontal_alignment")
    val horizontalAlignment: String? = null,
    @SerialName("vertical_alignment")
    val verticalAlignment: String? = null,
    @SerialName("main_axis_arrangement")
    val mainAxisArrangement: String? = null,
    val weight: Float? = null,
    val children: List<ComposedNode> = emptyList(),
    val text: TextAtom? = null,
    val button: ButtonAtom? = null,
    val tag: TagAtom? = null,
    val icon: IconAtom? = null,
    val image: ImageAtom? = null,
    val avatar: AvatarAtom? = null,
    val progress: ProgressAtom? = null,
    val divider: DividerAtom? = null,
    val spacer: SpacerAtom? = null,
    val stepper: StepperAtom? = null,
    val countdown: CountdownAtom? = null,
    @SerialName("counter_value")
    @JsonNames("rolling_value")
    val counterValue: String? = null,
    @SerialName("counter_typography")
    val counterTypography: TypographyTokenRef? = null,
    @SerialName("counter_color")
    val counterColor: ColorTokenRef? = null,
    @SerialName("on_click")
    val onClick: ActionRef? = null,
    val layout: LayoutConfig? = null,
    val motion: MotionSpec? = null,
)

/**
 * Typed payload for a server-molded atomic composition tree snippet (`"composed_block"`).
 */
@Serializable
data class ComposedBlockSnippetData(
    @SerialName("block_id")
    val blockId: String,
    @SerialName("recipe_name")
    val recipeName: String? = null,
    val root: ComposedNode,
    val layout: LayoutConfig? = null,
)
