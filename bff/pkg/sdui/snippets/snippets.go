// Package snippets defines composable SDUI snippet envelopes, page responses,
// and strongly-typed snippet data contracts aligned with KMP sdui-contracts.
package snippets

import (
	"encoding/json"
	"fmt"

	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
)

// Snippet type wire identifiers.
const (
	SnippetTypeHeader      = "header_v1"
	SnippetTypeBanner      = "banner_v1"
	SnippetTypeMetricCard  = "metric_card"
	SnippetTypeCard        = "card_v1"
	SnippetTypeActionRow   = "action_row"
	SnippetTypeForm        = "form_v1"
	SnippetTypeEmptyState  = "empty_state"
	SnippetTypeDivider     = "divider"
	SnippetTypeSpacer      = "spacer"
	SnippetTypeCarousel    = "carousel"
	SnippetTypeGrid        = "grid"
	SnippetTypeTabs        = "tabs"
	SnippetTypeAccordion   = "accordion"
	SnippetTypeProductCard = "product_card"
)

// SnippetEnvelope wraps any typed snippet payload with identity, hierarchy, and tracking metadata.
type SnippetEnvelope struct {
	SnippetID       string              `json:"snippet_id"`
	SnippetParentID string              `json:"snippet_parent_id,omitempty"`
	SnippetType     string              `json:"snippet_type"`
	Data            any                 `json:"data"`
	Tracking        *atoms.TrackingMeta `json:"tracking,omitempty"`
	Motion          *atoms.MotionSpec   `json:"motion,omitempty"`
}

// DecodeSnippetData converts a SnippetEnvelope's Data (including unmarshaled map[string]any)
// into the expected concrete snippet data struct T.
func DecodeSnippetData[T any](env SnippetEnvelope) (T, error) {
	var zero T
	if typed, ok := env.Data.(T); ok {
		return typed, nil
	}
	raw, err := json.Marshal(env.Data)
	if err != nil {
		return zero, fmt.Errorf("snippets: marshal envelope %q data: %w", env.SnippetID, err)
	}
	var out T
	if err := json.Unmarshal(raw, &out); err != nil {
		return zero, fmt.Errorf("snippets: decode envelope %q (%s): %w", env.SnippetID, env.SnippetType, err)
	}
	return out, nil
}

// ToolbarSpec describes the top app bar of an SDUI page.
type ToolbarSpec struct {
	Title           *atoms.TextAtom      `json:"title,omitempty"`
	Subtitle        *atoms.TextAtom      `json:"subtitle,omitempty"`
	NavigationIcon  *atoms.IconAtom      `json:"navigation_icon,omitempty"`
	TrailingActions []atoms.IconAtom     `json:"trailing_actions,omitempty"`
	BgColor         *atoms.ColorTokenRef `json:"bg_color,omitempty"`
}

// BottomBarSpec describes a docked footer CTA bar on an SDUI page.
type BottomBarSpec struct {
	PrimaryButton   *atoms.ButtonAtom   `json:"primary_button,omitempty"`
	SecondaryButton *atoms.ButtonAtom   `json:"secondary_button,omitempty"`
	HelperText      *atoms.TextAtom     `json:"helper_text,omitempty"`
	Layout          *atoms.LayoutConfig `json:"layout,omitempty"`
	Motion          *atoms.MotionSpec   `json:"motion,omitempty"`
}

// FloatingCartSpec describes a persistent floating cart dock overlaid on an SDUI page.
type FloatingCartSpec struct {
	Label    string            `json:"label"`
	EtaBadge *atoms.TagAtom    `json:"eta_badge,omitempty"`
	CTA      atoms.ButtonAtom  `json:"cta"`
	Motion   *atoms.MotionSpec `json:"motion,omitempty"`
}

// PaginationSpec describes cursor-based pagination metadata and an optional load-more trigger.
type PaginationSpec struct {
	NextCursor     string                 `json:"next_cursor,omitempty"`
	HasMore        bool                   `json:"has_more"`
	LoadMoreAction *actions.ActionTrigger `json:"load_more_action,omitempty"`
}

// Screen layout mode wire identifiers for AdaptivePageSpec.LayoutMode.
const (
	LayoutModeSingleColumn = "single_column"
	LayoutModeCentered     = "centered"
	LayoutModeSplitPane    = "split_pane"
)

// ResponsiveColumnsSpec specifies column counts keyed by horizontal breakpoint.
type ResponsiveColumnsSpec struct {
	Compact  *int `json:"compact,omitempty"`
	Medium   *int `json:"medium,omitempty"`
	Expanded *int `json:"expanded,omitempty"`
}

// ResponsiveIntSpec specifies integer sizing values keyed by horizontal breakpoint.
type ResponsiveIntSpec struct {
	Compact  *int `json:"compact,omitempty"`
	Medium   *int `json:"medium,omitempty"`
	Expanded *int `json:"expanded,omitempty"`
}

// AdaptivePageSpec defines responsive width, centering, and split-pane behavior across form factors.
type AdaptivePageSpec struct {
	LayoutMode          string   `json:"layout_mode,omitempty"`
	MaxWidthDp          *int     `json:"max_width_dp,omitempty"`
	SplitPaneEnabled    bool     `json:"split_pane_enabled,omitempty"`
	SecondarySnippetIDs []string `json:"secondary_snippet_ids,omitempty"`
}

// PageResponse is the top-level SDUI screen payload returned by the BFF.
type PageResponse struct {
	PageID         string                  `json:"page_id"`
	Title          string                  `json:"title"`
	Toolbar        *ToolbarSpec            `json:"toolbar,omitempty"`
	Snippets       []SnippetEnvelope       `json:"snippets"`
	BottomBar      *BottomBarSpec          `json:"bottom_bar,omitempty"`
	FloatingCart   *FloatingCartSpec       `json:"floating_cart,omitempty"`
	PageActions    []actions.ActionTrigger `json:"page_actions,omitempty"`
	Pagination     *PaginationSpec         `json:"pagination,omitempty"`
	PullToRefresh  *actions.ActionTrigger  `json:"pull_to_refresh,omitempty"`
	AdaptiveLayout *AdaptivePageSpec       `json:"adaptive_layout,omitempty"`
	Tracking       *atoms.TrackingMeta     `json:"tracking,omitempty"`
}

// ── Typed Snippet Data Contracts ───────────────────────────────────────────────

// HeaderSnippetData represents a section or screen header ("header_v1").
type HeaderSnippetData struct {
	Title          atoms.TextAtom      `json:"title"`
	Subtitle       *atoms.TextAtom     `json:"subtitle,omitempty"`
	LeadingIcon    *atoms.IconAtom     `json:"leading_icon,omitempty"`
	Avatar         *atoms.AvatarAtom   `json:"avatar,omitempty"`
	TrailingTag    *atoms.TagAtom      `json:"trailing_tag,omitempty"`
	TrailingAction *atoms.ButtonAtom   `json:"trailing_action,omitempty"`
	Layout         *atoms.LayoutConfig `json:"layout,omitempty"`
}

// BannerSnippetData represents a promotional or status banner ("banner_v1").
type BannerSnippetData struct {
	Title   atoms.TextAtom         `json:"title"`
	Body    *atoms.TextAtom        `json:"body,omitempty"`
	Icon    *atoms.IconAtom        `json:"icon,omitempty"`
	Image   *atoms.ImageAtom       `json:"image,omitempty"`
	Tag     *atoms.TagAtom         `json:"tag,omitempty"`
	CTA     *atoms.ButtonAtom      `json:"cta,omitempty"`
	OnClick *actions.ActionTrigger `json:"on_click,omitempty"`
	Layout  *atoms.LayoutConfig    `json:"layout,omitempty"`
}

// MetricCardSnippetData represents a KPI / progress metric tile ("metric_card").
type MetricCardSnippetData struct {
	Label    atoms.TextAtom         `json:"label"`
	Value    atoms.TextAtom         `json:"value"`
	Delta    *atoms.TagAtom         `json:"delta,omitempty"`
	Icon     *atoms.IconAtom        `json:"icon,omitempty"`
	Progress *atoms.ProgressAtom    `json:"progress,omitempty"`
	OnClick  *actions.ActionTrigger `json:"on_click,omitempty"`
	Layout   *atoms.LayoutConfig    `json:"layout,omitempty"`
}

// CardSnippetData represents a general-purpose content card ("card_v1").
type CardSnippetData struct {
	Title           atoms.TextAtom         `json:"title"`
	Subtitle        *atoms.TextAtom        `json:"subtitle,omitempty"`
	Description     *atoms.TextAtom        `json:"description,omitempty"`
	Media           *atoms.ImageAtom       `json:"media,omitempty"`
	Avatar          *atoms.AvatarAtom      `json:"avatar,omitempty"`
	Skeletons       []atoms.SkeletonAtom   `json:"skeletons,omitempty"`
	Badge           *atoms.TagAtom         `json:"badge,omitempty"`
	PrimaryButton   *atoms.ButtonAtom      `json:"primary_button,omitempty"`
	SecondaryButton *atoms.ButtonAtom      `json:"secondary_button,omitempty"`
	OnClick         *actions.ActionTrigger `json:"on_click,omitempty"`
	Layout          *atoms.LayoutConfig    `json:"layout,omitempty"`
}

// ActionRowSnippetData represents a clickable list row ("action_row").
type ActionRowSnippetData struct {
	Title        atoms.TextAtom         `json:"title"`
	Subtitle     *atoms.TextAtom        `json:"subtitle,omitempty"`
	LeadingIcon  *atoms.IconAtom        `json:"leading_icon,omitempty"`
	TrailingTag  *atoms.TagAtom         `json:"trailing_tag,omitempty"`
	TrailingIcon *atoms.IconAtom        `json:"trailing_icon,omitempty"`
	OnClick      *actions.ActionTrigger `json:"on_click,omitempty"`
	Layout       *atoms.LayoutConfig    `json:"layout,omitempty"`
}

// FormSnippetData represents an interactive input form ("form_v1").
type FormSnippetData struct {
	FormID       string                `json:"form_id"`
	Title        *atoms.TextAtom       `json:"title,omitempty"`
	Description  *atoms.TextAtom       `json:"description,omitempty"`
	Fields       []atoms.TextFieldAtom `json:"fields"`
	Switches     []atoms.SwitchAtom    `json:"switches,omitempty"`
	Checkboxes   []atoms.CheckboxAtom  `json:"checkboxes,omitempty"`
	Sliders      []atoms.SliderAtom    `json:"sliders,omitempty"`
	SubmitButton atoms.ButtonAtom      `json:"submit_button"`
	CancelButton *atoms.ButtonAtom     `json:"cancel_button,omitempty"`
	Layout       *atoms.LayoutConfig   `json:"layout,omitempty"`
}

// EmptyStateSnippetData represents a zero-data placeholder ("empty_state").
type EmptyStateSnippetData struct {
	Title        atoms.TextAtom      `json:"title"`
	Message      *atoms.TextAtom     `json:"message,omitempty"`
	Icon         *atoms.IconAtom     `json:"icon,omitempty"`
	Image        *atoms.ImageAtom    `json:"image,omitempty"`
	ActionButton *atoms.ButtonAtom   `json:"action_button,omitempty"`
	Layout       *atoms.LayoutConfig `json:"layout,omitempty"`
}

// DividerSnippetData represents a visual horizontal divider ("divider").
type DividerSnippetData struct {
	Divider atoms.DividerAtom   `json:"divider"`
	Layout  *atoms.LayoutConfig `json:"layout,omitempty"`
}

// SpacerSnippetData represents fixed layout whitespace ("spacer").
type SpacerSnippetData struct {
	Spacer atoms.SpacerAtom `json:"spacer"`
}

// CarouselSnippetData represents a horizontally scrollable container of snippets ("carousel").
type CarouselSnippetData struct {
	Title            *atoms.TextAtom     `json:"title,omitempty"`
	Items            []SnippetEnvelope   `json:"items"`
	ItemWidthDp      *int                `json:"item_width_dp,omitempty"`
	ItemWidthByWidth *ResponsiveIntSpec  `json:"item_width_by_width,omitempty"`
	SpacingDp        *int                `json:"spacing_dp,omitempty"`
	Layout           *atoms.LayoutConfig `json:"layout,omitempty"`
}

// GridSnippetData represents a multi-column grid container of snippets ("grid").
type GridSnippetData struct {
	Title          *atoms.TextAtom        `json:"title,omitempty"`
	Columns        int                    `json:"columns"`
	ColumnsByWidth *ResponsiveColumnsSpec `json:"columns_by_width,omitempty"`
	SpacingDp      *int                   `json:"spacing_dp,omitempty"`
	Items          []SnippetEnvelope      `json:"items"`
	Layout         *atoms.LayoutConfig    `json:"layout,omitempty"`
}

// TabItemSpec defines a single tab label, optional badge, and nested snippets.
type TabItemSpec struct {
	ID       string            `json:"id"`
	Title    string            `json:"title"`
	Badge    *atoms.TagAtom    `json:"badge,omitempty"`
	Snippets []SnippetEnvelope `json:"snippets"`
}

// TabsSnippetData represents a tabbed container switching between snippet lists ("tabs").
type TabsSnippetData struct {
	Tabs          []TabItemSpec       `json:"tabs"`
	SelectedIndex int                 `json:"selected_index"`
	Layout        *atoms.LayoutConfig `json:"layout,omitempty"`
}

// AccordionSnippetData represents a collapsible container section ("accordion").
type AccordionSnippetData struct {
	Title             atoms.TextAtom      `json:"title"`
	Subtitle          *atoms.TextAtom     `json:"subtitle,omitempty"`
	InitiallyExpanded bool                `json:"initially_expanded"`
	Snippets          []SnippetEnvelope   `json:"snippets"`
	Layout            *atoms.LayoutConfig `json:"layout,omitempty"`
}

// ProductCardSnippetData represents a quick-commerce product card ("product_card").
type ProductCardSnippetData struct {
	ProductID      string                 `json:"product_id"`
	Title          atoms.TextAtom         `json:"title"`
	Subtitle       *atoms.TextAtom        `json:"subtitle,omitempty"`
	Price          atoms.TextAtom         `json:"price"`
	OriginalPrice  *atoms.TextAtom        `json:"original_price,omitempty"`
	UnitPriceCents int                    `json:"unit_price_cents"`
	Badge          *atoms.TagAtom         `json:"badge,omitempty"`
	Image          *atoms.ImageAtom       `json:"image,omitempty"`
	Stepper        atoms.StepperAtom      `json:"stepper"`
	Rating         *atoms.TextAtom        `json:"rating,omitempty"`
	OnClick        *actions.ActionTrigger `json:"on_click,omitempty"`
	Layout         *atoms.LayoutConfig    `json:"layout,omitempty"`
}
