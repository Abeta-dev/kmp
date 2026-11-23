package snippets

import (
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
)

// PointerEdge wire identifiers for NudgeBarSnippetData.PointerEdge.
const (
	PointerEdgeNone   = "none"
	PointerEdgeTop    = "top"
	PointerEdgeBottom = "bottom"
	PointerEdgeStart  = "start"
	PointerEdgeEnd    = "end"
)

// NudgeBarSnippetData represents an urgency, progress, or contextual nudge strip ("nudge_bar").
type NudgeBarSnippetData struct {
	NudgeID     string                 `json:"nudge_id"`
	Title       atoms.TextAtom         `json:"title"`
	Subtitle    *atoms.TextAtom        `json:"subtitle,omitempty"`
	Icon        *atoms.IconAtom        `json:"icon,omitempty"`
	Badge       *atoms.TagAtom         `json:"badge,omitempty"`
	Countdown   *atoms.CountdownAtom   `json:"countdown,omitempty"`
	Progress    *float64               `json:"progress,omitempty"`
	CTA         *atoms.ButtonAtom      `json:"cta,omitempty"`
	Dismissible bool                   `json:"dismissible"`
	PointerEdge string                 `json:"pointer_edge,omitempty"`
	OnDismiss   *actions.ActionTrigger `json:"on_dismiss,omitempty"`
	OnClick     *actions.ActionTrigger `json:"on_click,omitempty"`
	Layout      *atoms.LayoutConfig    `json:"layout,omitempty"`
}

// MarqueeItem represents an individual item scrolled inside MarqueeStripSnippetData.
type MarqueeItem struct {
	ID      string                 `json:"id"`
	Text    string                 `json:"text"`
	Icon    *atoms.IconAtom        `json:"icon,omitempty"`
	Badge   *atoms.TagAtom         `json:"badge,omitempty"`
	OnClick *actions.ActionTrigger `json:"on_click,omitempty"`
}

// MarqueeStripSnippetData represents a continuously scrolling ticker strip ("marquee_strip").
type MarqueeStripSnippetData struct {
	StripID          string              `json:"strip_id"`
	Items            []MarqueeItem       `json:"items"`
	VelocityDpPerSec int                 `json:"velocity_dp_per_sec,omitempty"`
	Separator        string              `json:"separator,omitempty"`
	Layout           *atoms.LayoutConfig `json:"layout,omitempty"`
}

// OrderTrackerStep represents a milestone stage inside OrderTrackerSnippetData.
type OrderTrackerStep struct {
	ID        string          `json:"id"`
	Label     string          `json:"label"`
	Caption   string          `json:"caption,omitempty"`
	Icon      *atoms.IconAtom `json:"icon,omitempty"`
	Completed bool            `json:"completed"`
	Active    bool            `json:"active"`
}

// OrderTrackerSnippetData represents a live multi-stage order tracking card ("order_tracker").
type OrderTrackerSnippetData struct {
	TrackerID       string               `json:"tracker_id"`
	OrderCode       string               `json:"order_code"`
	Headline        atoms.TextAtom       `json:"headline"`
	EtaCountdown    *atoms.CountdownAtom `json:"eta_countdown,omitempty"`
	EtaBadge        *atoms.TagAtom       `json:"eta_badge,omitempty"`
	RiderName       string               `json:"rider_name,omitempty"`
	RiderCaption    string               `json:"rider_caption,omitempty"`
	Steps           []OrderTrackerStep   `json:"steps"`
	PrimaryAction   *atoms.ButtonAtom    `json:"primary_action,omitempty"`
	SecondaryAction *atoms.ButtonAtom    `json:"secondary_action,omitempty"`
	Layout          *atoms.LayoutConfig  `json:"layout,omitempty"`
}

// ComposedNodeKind wire identifiers for ComposedNode.Kind.
const (
	ComposedNodeColumn           = "column"
	ComposedNodeRow              = "row"
	ComposedNodeBox              = "box"
	ComposedNodeCard             = "card"
	ComposedNodeFlowRow          = "flow_row"
	ComposedNodeText             = "text"
	ComposedNodeButton           = "button"
	ComposedNodeTag              = "tag"
	ComposedNodeIcon             = "icon"
	ComposedNodeImage            = "image"
	ComposedNodeAvatar           = "avatar"
	ComposedNodeProgress         = "progress"
	ComposedNodeDivider          = "divider"
	ComposedNodeSpacer           = "spacer"
	ComposedNodeStepper          = "stepper"
	ComposedNodeCountdown        = "countdown"
	ComposedNodeRollingCounter   = "rolling_counter"
	ComposedNodeRadioGroup       = "radio_group"
	ComposedNodeSelect           = "select"
	ComposedNodeOtpInput         = "otp_input"
	ComposedNodeChip             = "chip"
	ComposedNodeSegmentedControl = "segmented_control"
	ComposedNodeRating           = "rating"
	ComposedNodePrice            = "price"
	ComposedNodeBadge            = "badge"
	ComposedNodeAlertBanner      = "alert_banner"
	ComposedNodeStatTile         = "stat_tile"
	ComposedNodeKeyValueRow      = "key_value_row"
	ComposedNodeQrCode           = "qr_code"
)

// ComposedNode alignment and main-axis arrangement wire identifiers.
const (
	ComposedAlignStart        = "start"
	ComposedAlignCenter       = "center"
	ComposedAlignEnd          = "end"
	ComposedAlignTop          = "top"
	ComposedAlignBottom       = "bottom"
	ComposedAlignSpaceBetween = "space_between"
	ComposedAlignSpaceAround  = "space_around"
	ComposedAlignSpaceEvenly  = "space_evenly"
)

// ComposedNode represents a recursive atomic-molding tree node rendered by
// the composed_block snippet renderer without requiring a client app release.
type ComposedNode struct {
	NodeID              string                      `json:"node_id,omitempty"`
	Kind                string                      `json:"kind"`
	SpacingDp           *int                        `json:"spacing_dp,omitempty"`
	Alignment           string                      `json:"alignment,omitempty"`
	HorizontalAlignment string                      `json:"horizontal_alignment,omitempty"`
	VerticalAlignment   string                      `json:"vertical_alignment,omitempty"`
	MainAxisArrangement string                      `json:"main_axis_arrangement,omitempty"`
	Weight              *float64                    `json:"weight,omitempty"`
	Children            []ComposedNode              `json:"children,omitempty"`
	Text                *atoms.TextAtom             `json:"text,omitempty"`
	Button              *atoms.ButtonAtom           `json:"button,omitempty"`
	Tag                 *atoms.TagAtom              `json:"tag,omitempty"`
	Icon                *atoms.IconAtom             `json:"icon,omitempty"`
	Image               *atoms.ImageAtom            `json:"image,omitempty"`
	Avatar              *atoms.AvatarAtom           `json:"avatar,omitempty"`
	Progress            *atoms.ProgressAtom         `json:"progress,omitempty"`
	Divider             *atoms.DividerAtom          `json:"divider,omitempty"`
	Spacer              *atoms.SpacerAtom           `json:"spacer,omitempty"`
	Stepper             *atoms.StepperAtom          `json:"stepper,omitempty"`
	Countdown           *atoms.CountdownAtom        `json:"countdown,omitempty"`
	CounterValue        string                      `json:"counter_value,omitempty"`
	CounterTypography   *atoms.TypographyTokenRef   `json:"counter_typography,omitempty"`
	CounterColor        *atoms.ColorTokenRef        `json:"counter_color,omitempty"`
	RadioGroup          *atoms.RadioGroupAtom       `json:"radio_group,omitempty"`
	Select              *atoms.SelectAtom           `json:"select,omitempty"`
	OtpInput            *atoms.OtpInputAtom         `json:"otp_input,omitempty"`
	Chip                *atoms.ChipAtom             `json:"chip,omitempty"`
	SegmentedControl    *atoms.SegmentedControlAtom `json:"segmented_control,omitempty"`
	Rating              *atoms.RatingAtom           `json:"rating,omitempty"`
	Price               *atoms.PriceAtom            `json:"price,omitempty"`
	Badge               *atoms.BadgeAtom            `json:"badge,omitempty"`
	AlertBanner         *atoms.AlertBannerAtom      `json:"alert_banner,omitempty"`
	StatTile            *atoms.StatTileAtom         `json:"stat_tile,omitempty"`
	KeyValueRow         *atoms.KeyValueRowAtom      `json:"key_value_row,omitempty"`
	QrCode              *atoms.QrCodeAtom           `json:"qr_code,omitempty"`
	OnClick             *actions.ActionTrigger      `json:"on_click,omitempty"`
	Layout              *atoms.LayoutConfig         `json:"layout,omitempty"`
	Motion              *atoms.MotionSpec           `json:"motion,omitempty"`
}

// ComposedBlockSnippetData represents a server-molded atomic composition tree ("composed_block").
type ComposedBlockSnippetData struct {
	BlockID    string              `json:"block_id"`
	RecipeName string              `json:"recipe_name,omitempty"`
	Root       ComposedNode        `json:"root"`
	Layout     *atoms.LayoutConfig `json:"layout,omitempty"`
}
