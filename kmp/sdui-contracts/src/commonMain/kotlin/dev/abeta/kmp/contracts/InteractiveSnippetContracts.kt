@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.contracts

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Single swipeable card item rendered inside [SwipeDeckSnippetData].
 */
@Serializable
data class SwipeCardItem(
    @SerialName("card_id")
    val cardId: String,
    val title: TextAtom,
    val subtitle: TextAtom? = null,
    val bio: TextAtom? = null,
    val badge: TagAtom? = null,
    @SerialName("distance_text")
    val distanceText: TextAtom? = null,
    val image: ImageAtom? = null,
    val tags: List<TagAtom> = emptyList(),
    @SerialName("on_like")
    val onLike: ActionRef? = null,
    @SerialName("on_nope")
    val onNope: ActionRef? = null,
    @SerialName("on_super_like")
    val onSuperLike: ActionRef? = null,
    @SerialName("on_click")
    val onClick: ActionRef? = null,
)

/**
 * Typed payload for an interactive stacked swipe card deck snippet (`"swipe_deck"`).
 */
@Serializable
data class SwipeDeckSnippetData(
    @SerialName("deck_id")
    val deckId: String,
    val cards: List<SwipeCardItem> = emptyList(),
    @SerialName("like_label")
    val likeLabel: String = "LIKE",
    @SerialName("nope_label")
    val nopeLabel: String = "NOPE",
    @SerialName("super_like_label")
    val superLikeLabel: String = "SUPER LIKE",
    @SerialName("empty_title")
    val emptyTitle: TextAtom? = null,
    @SerialName("empty_message")
    val emptyMessage: TextAtom? = null,
    @SerialName("on_like")
    val onLike: ActionRef? = null,
    @SerialName("on_nope")
    val onNope: ActionRef? = null,
    @SerialName("on_super_like")
    val onSuperLike: ActionRef? = null,
    @SerialName("on_undo")
    val onUndo: ActionRef? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Typed payload for an interactive search bar snippet (`"search_bar"`).
 */
@Serializable
data class SearchBarSnippetData(
    @SerialName("search_id")
    val searchId: String,
    val query: String = "",
    val placeholder: String = "Search...",
    @SerialName("leading_icon")
    val leadingIcon: IconAtom? = null,
    @SerialName("trailing_icon")
    val trailingIcon: IconAtom? = null,
    @SerialName("submit_label")
    val submitLabel: String? = null,
    val clearable: Boolean = true,
    @SerialName("on_submit")
    val onSubmit: ActionRef? = null,
    @SerialName("on_clear")
    val onClear: ActionRef? = null,
    @SerialName("on_trailing_click")
    val onTrailingClick: ActionRef? = null,
    val layout: LayoutConfig? = null,
)

/**
 * Single selectable filter chip item rendered inside [ChipGroupSnippetData].
 */
@Serializable
data class FilterChipItem(
    val id: String,
    val label: String,
    val selected: Boolean = false,
    val icon: IconAtom? = null,
    val badge: String? = null,
    @SerialName("on_click")
    val onClick: ActionRef? = null,
)

/**
 * Typed payload for a horizontally scrollable filter chip group snippet (`"chip_group"`).
 */
@Serializable
data class ChipGroupSnippetData(
    @SerialName("group_id")
    val groupId: String,
    val title: TextAtom? = null,
    @SerialName("multi_select")
    val multiSelect: Boolean = false,
    val chips: List<FilterChipItem> = emptyList(),
    val layout: LayoutConfig? = null,
)
