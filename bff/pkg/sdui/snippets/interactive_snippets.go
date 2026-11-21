package snippets

import (
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
)

// SwipeCardItem represents an individual swipeable card inside SwipeDeckSnippetData.
type SwipeCardItem struct {
	CardID       string                 `json:"card_id"`
	Title        atoms.TextAtom         `json:"title"`
	Subtitle     *atoms.TextAtom        `json:"subtitle,omitempty"`
	Bio          *atoms.TextAtom        `json:"bio,omitempty"`
	Badge        *atoms.TagAtom         `json:"badge,omitempty"`
	DistanceText *atoms.TextAtom        `json:"distance_text,omitempty"`
	Image        *atoms.ImageAtom       `json:"image,omitempty"`
	Tags         []atoms.TagAtom        `json:"tags,omitempty"`
	OnLike       *actions.ActionTrigger `json:"on_like,omitempty"`
	OnNope       *actions.ActionTrigger `json:"on_nope,omitempty"`
	OnSuperLike  *actions.ActionTrigger `json:"on_super_like,omitempty"`
	OnClick      *actions.ActionTrigger `json:"on_click,omitempty"`
}

// SwipeDeckSnippetData represents an interactive swipeable card deck ("swipe_deck").
type SwipeDeckSnippetData struct {
	DeckID         string                 `json:"deck_id"`
	Cards          []SwipeCardItem        `json:"cards"`
	LikeLabel      string                 `json:"like_label,omitempty"`
	NopeLabel      string                 `json:"nope_label,omitempty"`
	SuperLikeLabel string                 `json:"super_like_label,omitempty"`
	EmptyTitle     *atoms.TextAtom        `json:"empty_title,omitempty"`
	EmptyMessage   *atoms.TextAtom        `json:"empty_message,omitempty"`
	OnLike         *actions.ActionTrigger `json:"on_like,omitempty"`
	OnNope         *actions.ActionTrigger `json:"on_nope,omitempty"`
	OnSuperLike    *actions.ActionTrigger `json:"on_super_like,omitempty"`
	OnUndo         *actions.ActionTrigger `json:"on_undo,omitempty"`
	Layout         *atoms.LayoutConfig    `json:"layout,omitempty"`
}

// SearchBarSnippetData represents an interactive search input bar ("search_bar").
type SearchBarSnippetData struct {
	SearchID        string                 `json:"search_id"`
	Query           string                 `json:"query,omitempty"`
	Placeholder     string                 `json:"placeholder,omitempty"`
	LeadingIcon     *atoms.IconAtom        `json:"leading_icon,omitempty"`
	TrailingIcon    *atoms.IconAtom        `json:"trailing_icon,omitempty"`
	SubmitLabel     string                 `json:"submit_label,omitempty"`
	Clearable       bool                   `json:"clearable"`
	OnSubmit        *actions.ActionTrigger `json:"on_submit,omitempty"`
	OnClear         *actions.ActionTrigger `json:"on_clear,omitempty"`
	OnTrailingClick *actions.ActionTrigger `json:"on_trailing_click,omitempty"`
	Layout          *atoms.LayoutConfig    `json:"layout,omitempty"`
}

// FilterChipItem represents a selectable chip option inside ChipGroupSnippetData.
type FilterChipItem struct {
	ID       string                 `json:"id"`
	Label    string                 `json:"label"`
	Selected bool                   `json:"selected"`
	Icon     *atoms.IconAtom        `json:"icon,omitempty"`
	Badge    string                 `json:"badge,omitempty"`
	OnClick  *actions.ActionTrigger `json:"on_click,omitempty"`
}

// ChipGroupSnippetData represents a selectable group of filter chips ("chip_group").
type ChipGroupSnippetData struct {
	GroupID     string              `json:"group_id"`
	Title       *atoms.TextAtom     `json:"title,omitempty"`
	MultiSelect bool                `json:"multi_select"`
	Chips       []FilterChipItem    `json:"chips"`
	Layout      *atoms.LayoutConfig `json:"layout,omitempty"`
}
