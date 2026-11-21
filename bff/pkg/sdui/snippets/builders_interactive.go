package snippets

import (
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
)

// ── SwipeDeck Builder ──────────────────────────────────────────────────────────

// SwipeDeckSnippetBuilder fluently builds a swipe_deck SnippetEnvelope.
type SwipeDeckSnippetBuilder struct {
	meta envelopeMeta
	data SwipeDeckSnippetData
}

// NewSwipeDeckSnippet creates a SwipeDeckSnippetBuilder with required snippet ID,
// deck identifier, and optional initial cards.
func NewSwipeDeckSnippet(id, deckID string, cards ...SwipeCardItem) *SwipeDeckSnippetBuilder {
	initial := make([]SwipeCardItem, 0, len(cards))
	initial = append(initial, cards...)
	return &SwipeDeckSnippetBuilder{
		meta: envelopeMeta{id: id, kind: SnippetTypeSwipeDeck},
		data: SwipeDeckSnippetData{
			DeckID: deckID,
			Cards:  initial,
		},
	}
}

func (b *SwipeDeckSnippetBuilder) WithParentID(parentID string) *SwipeDeckSnippetBuilder {
	b.meta.parentID = parentID
	return b
}

func (b *SwipeDeckSnippetBuilder) WithTracking(eventName string, props map[string]any) *SwipeDeckSnippetBuilder {
	b.meta.tracking = &atoms.TrackingMeta{EventName: eventName, Properties: props}
	return b
}

func (b *SwipeDeckSnippetBuilder) WithMotion(motion atoms.MotionSpec) *SwipeDeckSnippetBuilder {
	b.meta.motion = &motion
	return b
}

func (b *SwipeDeckSnippetBuilder) AddCard(card SwipeCardItem) *SwipeDeckSnippetBuilder {
	b.data.Cards = append(b.data.Cards, card)
	return b
}

func (b *SwipeDeckSnippetBuilder) AddCards(cards ...SwipeCardItem) *SwipeDeckSnippetBuilder {
	b.data.Cards = append(b.data.Cards, cards...)
	return b
}

func (b *SwipeDeckSnippetBuilder) WithLabels(like, nope, superLike string) *SwipeDeckSnippetBuilder {
	b.data.LikeLabel = like
	b.data.NopeLabel = nope
	b.data.SuperLikeLabel = superLike
	return b
}

func (b *SwipeDeckSnippetBuilder) WithEmptyState(title, message atoms.TextAtom) *SwipeDeckSnippetBuilder {
	b.data.EmptyTitle = &title
	b.data.EmptyMessage = &message
	return b
}

func (b *SwipeDeckSnippetBuilder) WithOnLike(trigger actions.ActionTrigger) *SwipeDeckSnippetBuilder {
	b.data.OnLike = &trigger
	return b
}

func (b *SwipeDeckSnippetBuilder) WithOnNope(trigger actions.ActionTrigger) *SwipeDeckSnippetBuilder {
	b.data.OnNope = &trigger
	return b
}

func (b *SwipeDeckSnippetBuilder) WithOnSuperLike(trigger actions.ActionTrigger) *SwipeDeckSnippetBuilder {
	b.data.OnSuperLike = &trigger
	return b
}

func (b *SwipeDeckSnippetBuilder) WithOnUndo(trigger actions.ActionTrigger) *SwipeDeckSnippetBuilder {
	b.data.OnUndo = &trigger
	return b
}

func (b *SwipeDeckSnippetBuilder) WithLayout(layout atoms.LayoutConfig) *SwipeDeckSnippetBuilder {
	b.data.Layout = &layout
	return b
}

func (b *SwipeDeckSnippetBuilder) Build() SnippetEnvelope {
	return b.meta.wrap(b.data)
}

// ── SearchBar Builder ──────────────────────────────────────────────────────────

// SearchBarSnippetBuilder fluently builds a search_bar SnippetEnvelope.
type SearchBarSnippetBuilder struct {
	meta envelopeMeta
	data SearchBarSnippetData
}

// NewSearchBarSnippet creates a SearchBarSnippetBuilder with Clearable enabled by default.
func NewSearchBarSnippet(id, searchID, placeholder string) *SearchBarSnippetBuilder {
	return &SearchBarSnippetBuilder{
		meta: envelopeMeta{id: id, kind: SnippetTypeSearchBar},
		data: SearchBarSnippetData{
			SearchID:    searchID,
			Placeholder: placeholder,
			Clearable:   true,
		},
	}
}

func (b *SearchBarSnippetBuilder) WithParentID(parentID string) *SearchBarSnippetBuilder {
	b.meta.parentID = parentID
	return b
}

func (b *SearchBarSnippetBuilder) WithTracking(eventName string, props map[string]any) *SearchBarSnippetBuilder {
	b.meta.tracking = &atoms.TrackingMeta{EventName: eventName, Properties: props}
	return b
}

func (b *SearchBarSnippetBuilder) WithMotion(motion atoms.MotionSpec) *SearchBarSnippetBuilder {
	b.meta.motion = &motion
	return b
}

func (b *SearchBarSnippetBuilder) WithQuery(query string) *SearchBarSnippetBuilder {
	b.data.Query = query
	return b
}

func (b *SearchBarSnippetBuilder) WithLeadingIcon(icon atoms.IconAtom) *SearchBarSnippetBuilder {
	b.data.LeadingIcon = &icon
	return b
}

func (b *SearchBarSnippetBuilder) WithTrailingIcon(icon atoms.IconAtom) *SearchBarSnippetBuilder {
	b.data.TrailingIcon = &icon
	return b
}

func (b *SearchBarSnippetBuilder) WithSubmitLabel(label string) *SearchBarSnippetBuilder {
	b.data.SubmitLabel = label
	return b
}

func (b *SearchBarSnippetBuilder) WithClearable(clearable bool) *SearchBarSnippetBuilder {
	b.data.Clearable = clearable
	return b
}

func (b *SearchBarSnippetBuilder) WithOnSubmit(trigger actions.ActionTrigger) *SearchBarSnippetBuilder {
	b.data.OnSubmit = &trigger
	return b
}

func (b *SearchBarSnippetBuilder) WithOnClear(trigger actions.ActionTrigger) *SearchBarSnippetBuilder {
	b.data.OnClear = &trigger
	return b
}

func (b *SearchBarSnippetBuilder) WithOnTrailingClick(trigger actions.ActionTrigger) *SearchBarSnippetBuilder {
	b.data.OnTrailingClick = &trigger
	return b
}

func (b *SearchBarSnippetBuilder) WithLayout(layout atoms.LayoutConfig) *SearchBarSnippetBuilder {
	b.data.Layout = &layout
	return b
}

func (b *SearchBarSnippetBuilder) Build() SnippetEnvelope {
	return b.meta.wrap(b.data)
}

// ── ChipGroup Builder ──────────────────────────────────────────────────────────

// ChipGroupSnippetBuilder fluently builds a chip_group SnippetEnvelope.
type ChipGroupSnippetBuilder struct {
	meta envelopeMeta
	data ChipGroupSnippetData
}

// NewChipGroupSnippet creates a ChipGroupSnippetBuilder with required snippet ID,
// group identifier, and optional initial filter chips.
func NewChipGroupSnippet(id, groupID string, chips ...FilterChipItem) *ChipGroupSnippetBuilder {
	initial := make([]FilterChipItem, 0, len(chips))
	initial = append(initial, chips...)
	return &ChipGroupSnippetBuilder{
		meta: envelopeMeta{id: id, kind: SnippetTypeChipGroup},
		data: ChipGroupSnippetData{
			GroupID: groupID,
			Chips:   initial,
		},
	}
}

func (b *ChipGroupSnippetBuilder) WithParentID(parentID string) *ChipGroupSnippetBuilder {
	b.meta.parentID = parentID
	return b
}

func (b *ChipGroupSnippetBuilder) WithTracking(eventName string, props map[string]any) *ChipGroupSnippetBuilder {
	b.meta.tracking = &atoms.TrackingMeta{EventName: eventName, Properties: props}
	return b
}

func (b *ChipGroupSnippetBuilder) WithMotion(motion atoms.MotionSpec) *ChipGroupSnippetBuilder {
	b.meta.motion = &motion
	return b
}

func (b *ChipGroupSnippetBuilder) WithTitle(title atoms.TextAtom) *ChipGroupSnippetBuilder {
	b.data.Title = &title
	return b
}

func (b *ChipGroupSnippetBuilder) WithMultiSelect(multiSelect bool) *ChipGroupSnippetBuilder {
	b.data.MultiSelect = multiSelect
	return b
}

func (b *ChipGroupSnippetBuilder) AddChip(chip FilterChipItem) *ChipGroupSnippetBuilder {
	b.data.Chips = append(b.data.Chips, chip)
	return b
}

func (b *ChipGroupSnippetBuilder) AddChips(chips ...FilterChipItem) *ChipGroupSnippetBuilder {
	b.data.Chips = append(b.data.Chips, chips...)
	return b
}

func (b *ChipGroupSnippetBuilder) WithLayout(layout atoms.LayoutConfig) *ChipGroupSnippetBuilder {
	b.data.Layout = &layout
	return b
}

func (b *ChipGroupSnippetBuilder) Build() SnippetEnvelope {
	return b.meta.wrap(b.data)
}
