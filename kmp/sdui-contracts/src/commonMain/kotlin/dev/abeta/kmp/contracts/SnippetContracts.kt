package dev.abeta.kmp.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Standard snippet type identifiers emitted by the Go BFF.
 */
object SnippetTypes {
    const val HEADER = "header_v1"
    const val BANNER = "banner_v1"
    const val METRIC_CARD = "metric_card"
    const val CARD = "card_v1"
    const val ACTION_ROW = "action_row"
    const val FORM = "form_v1"
    const val EMPTY_STATE = "empty_state"
    const val DIVIDER = "divider"
    const val SPACER = "spacer"
    const val CAROUSEL = "carousel"
    const val GRID = "grid"
    const val TABS = "tabs"
    const val ACCORDION = "accordion"
    const val PRODUCT_CARD = "product_card"
}

/**
 * Generic SDUI snippet envelope carrying an arbitrary [data] payload keyed by [snippetType].
 */
@Serializable
data class SnippetEnvelope(
    @SerialName("snippet_id")
    val snippetId: String,
    @SerialName("snippet_parent_id")
    val snippetParentId: String? = null,
    @SerialName("snippet_type")
    val snippetType: String,
    val data: JsonObject = JsonObject(emptyMap()),
    val tracking: TrackingContract? = null,
    val motion: MotionSpec? = null,
)

/**
 * Page top application bar contract.
 */
@Serializable
data class ToolbarSpec(
    val title: TextAtom? = null,
    val subtitle: TextAtom? = null,
    @SerialName("navigation_icon")
    val navigationIcon: IconAtom? = null,
    @SerialName("trailing_actions")
    val trailingActions: List<IconAtom> = emptyList(),
    @SerialName("bg_color")
    val bgColor: ColorTokenRef? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Persistent page bottom action bar contract.
 */
@Serializable
data class BottomBarSpec(
    @SerialName("primary_button")
    val primaryButton: ButtonAtom? = null,
    @SerialName("secondary_button")
    val secondaryButton: ButtonAtom? = null,
    @SerialName("helper_text")
    val helperText: TextAtom? = null,
    val layout: LayoutConfig? = null,
    val motion: MotionSpec? = null,
)

/**
 * Floating cart summary bar contract anchored over page content.
 */
@Serializable
data class FloatingCartSpec(
    val label: String,
    @SerialName("eta_badge")
    val etaBadge: TagAtom? = null,
    val cta: ButtonAtom,
    val motion: MotionSpec? = null,
)

/**
 * Cursor pagination metadata attached to [PageResponse] for incremental loading.
 */
@Serializable
data class PaginationSpec(
    @SerialName("next_cursor")
    val nextCursor: String? = null,
    @SerialName("has_more")
    val hasMore: Boolean = false,
    @SerialName("load_more_action")
    val loadMoreAction: ActionRef? = null,
)

/**
 * Root SDUI page payload returned by the BFF.
 */
@Serializable
data class PageResponse(
    @SerialName("page_id")
    val pageId: String,
    val title: String? = null,
    val toolbar: ToolbarSpec? = null,
    val snippets: List<SnippetEnvelope> = emptyList(),
    @SerialName("bottom_bar")
    val bottomBar: BottomBarSpec? = null,
    @SerialName("page_actions")
    val pageActions: List<ActionRef> = emptyList(),
    val tracking: TrackingContract? = null,
    val pagination: PaginationSpec? = null,
    @SerialName("pull_to_refresh")
    val pullToRefresh: ActionRef? = null,
    @SerialName("floating_cart")
    val floatingCart: FloatingCartSpec? = null,
    @SerialName("adaptive_layout")
    val adaptiveLayout: AdaptivePageSpec? = null,
)

/**
 * Typed payload for a section or page header snippet (`"header_v1"`).
 */
@Serializable
data class HeaderSnippetData(
    val title: TextAtom,
    val subtitle: TextAtom? = null,
    @SerialName("leading_icon")
    val leadingIcon: IconAtom? = null,
    @SerialName("trailing_tag")
    val trailingTag: TagAtom? = null,
    @SerialName("trailing_action")
    val trailingAction: ButtonAtom? = null,
    @SerialName("trailing_icon")
    val trailingIcon: IconAtom? = null,
    @SerialName("on_click")
    val onClick: ActionRef? = null,
    val layout: LayoutConfig? = null,
    val avatar: AvatarAtom? = null,
    val skeletons: List<SkeletonAtom> = emptyList(),
)

/**
 * Typed payload for a promotional or informational banner snippet (`"banner_v1"`).
 */
@Serializable
data class BannerSnippetData(
    val title: TextAtom,
    val body: TextAtom? = null,
    val icon: IconAtom? = null,
    val image: ImageAtom? = null,
    val tag: TagAtom? = null,
    val cta: ButtonAtom? = null,
    @SerialName("dismiss_action")
    val dismissAction: ActionRef? = null,
    @SerialName("on_click")
    val onClick: ActionRef? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Typed payload for a KPI / metric card snippet (`"metric_card"`).
 */
@Serializable
data class MetricCardSnippetData(
    val label: TextAtom,
    val value: TextAtom,
    val delta: TagAtom? = null,
    val caption: TextAtom? = null,
    val icon: IconAtom? = null,
    val progress: ProgressAtom? = null,
    @SerialName("on_click")
    val onClick: ActionRef? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Typed payload for a general-purpose content card snippet (`"card_v1"`).
 */
@Serializable
data class CardSnippetData(
    val title: TextAtom,
    val subtitle: TextAtom? = null,
    val description: TextAtom? = null,
    val media: ImageAtom? = null,
    val badge: TagAtom? = null,
    @SerialName("leading_icon")
    val leadingIcon: IconAtom? = null,
    @SerialName("primary_button")
    val primaryButton: ButtonAtom? = null,
    @SerialName("secondary_button")
    val secondaryButton: ButtonAtom? = null,
    @SerialName("on_click")
    val onClick: ActionRef? = null,
    val layout: LayoutConfig? = null,
    val avatar: AvatarAtom? = null,
    val skeletons: List<SkeletonAtom> = emptyList(),
)

/**
 * Typed payload for a clickable list/action row snippet (`"action_row"`).
 */
@Serializable
data class ActionRowSnippetData(
    val title: TextAtom,
    val subtitle: TextAtom? = null,
    @SerialName("leading_icon")
    val leadingIcon: IconAtom? = null,
    @SerialName("trailing_tag")
    val trailingTag: TagAtom? = null,
    @SerialName("trailing_icon")
    val trailingIcon: IconAtom? = null,
    @SerialName("on_click")
    val onClick: ActionRef? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Typed payload for an interactive form snippet (`"form_v1"`).
 */
@Serializable
data class FormSnippetData(
    @SerialName("form_id")
    val formId: String,
    val title: TextAtom? = null,
    val description: TextAtom? = null,
    val fields: List<TextFieldAtom> = emptyList(),
    @SerialName("submit_button")
    val submitButton: ButtonAtom? = null,
    @SerialName("cancel_button")
    val cancelButton: ButtonAtom? = null,
    val layout: LayoutConfig? = null,
    val switches: List<SwitchAtom> = emptyList(),
    val checkboxes: List<CheckboxAtom> = emptyList(),
    val sliders: List<SliderAtom> = emptyList(),
)

/**
 * Typed payload for an empty/zero-data state snippet (`"empty_state"`).
 */
@Serializable
data class EmptyStateSnippetData(
    val title: TextAtom,
    val message: TextAtom? = null,
    val icon: IconAtom? = null,
    val image: ImageAtom? = null,
    @SerialName("action_button")
    val actionButton: ButtonAtom? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Typed payload for a divider snippet (`"divider"`).
 */
@Serializable
data class DividerSnippetData(
    val divider: DividerAtom = DividerAtom(),
    val layout: LayoutConfig? = null,
)

/**
 * Typed payload for a spacer snippet (`"spacer"`).
 */
@Serializable
data class SpacerSnippetData(
    val spacer: SpacerAtom = SpacerAtom(),
)

/**
 * Typed payload for a horizontal carousel container snippet (`"carousel"`).
 */
@Serializable
data class CarouselSnippetData(
    val title: TextAtom? = null,
    val items: List<SnippetEnvelope> = emptyList(),
    @SerialName("item_width_dp")
    val itemWidthDp: Int? = null,
    @SerialName("item_width_by_width")
    val itemWidthByWidth: ResponsiveIntSpec? = null,
    @SerialName("spacing_dp")
    val spacingDp: Int? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Typed payload for a multi-column grid container snippet (`"grid"`).
 */
@Serializable
data class GridSnippetData(
    val title: TextAtom? = null,
    val columns: Int = 2,
    @SerialName("columns_by_width")
    val columnsByWidth: ResponsiveColumnsSpec? = null,
    @SerialName("spacing_dp")
    val spacingDp: Int? = null,
    val items: List<SnippetEnvelope> = emptyList(),
    val layout: LayoutConfig? = null,
)

/**
 * Single tab item descriptor inside [TabsSnippetData].
 */
@Serializable
data class TabItemSpec(
    val id: String,
    val title: String,
    val badge: TagAtom? = null,
    val snippets: List<SnippetEnvelope> = emptyList(),
)

/**
 * Typed payload for a tabbed container snippet (`"tabs"`).
 */
@Serializable
data class TabsSnippetData(
    val tabs: List<TabItemSpec> = emptyList(),
    @SerialName("selected_index")
    val selectedIndex: Int = 0,
    val layout: LayoutConfig? = null,
)

/**
 * Typed payload for a collapsible accordion container snippet (`"accordion"`).
 */
@Serializable
data class AccordionSnippetData(
    val title: TextAtom,
    val subtitle: TextAtom? = null,
    @SerialName("initially_expanded")
    val initiallyExpanded: Boolean = false,
    val snippets: List<SnippetEnvelope> = emptyList(),
    val layout: LayoutConfig? = null,
)

/**
 * Typed payload for a quick-commerce product card snippet (`"product_card"`).
 */
@Serializable
data class ProductCardSnippetData(
    @SerialName("product_id")
    val productId: String,
    val title: TextAtom,
    val subtitle: TextAtom? = null,
    val price: TextAtom,
    @SerialName("original_price")
    val originalPrice: TextAtom? = null,
    @SerialName("unit_price_cents")
    val unitPriceCents: Int = 0,
    val badge: TagAtom? = null,
    val image: ImageAtom? = null,
    val stepper: StepperAtom,
    val rating: TextAtom? = null,
    @SerialName("on_click")
    val onClick: ActionRef? = null,
    val layout: LayoutConfig? = null,
)
