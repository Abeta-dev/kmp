// Package atoms defines pure Go SDUI atomic UI primitives matching the KMP
// sdui-contracts schema. Atoms only reference lightweight actions.ActionTrigger
// wire structs and contain zero action execution logic.
package atoms

import (
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

// ColorTokenRef references a semantic ColorToken with optional alpha override and fallback hex.
type ColorTokenRef struct {
	Token       theming.ColorToken `json:"token"`
	Alpha       *float64           `json:"alpha,omitempty"`
	FallbackHex string             `json:"fallback_hex,omitempty"`
}

// TypographyTokenRef references a semantic TypographyToken with optional overrides.
type TypographyTokenRef struct {
	Token         theming.TypographyToken `json:"token"`
	FontWeight    string                  `json:"font_weight,omitempty"`
	LetterSpacing *float64                `json:"letter_spacing,omitempty"`
}

// SpacingValues represents directional spacing in dp.
type SpacingValues struct {
	Start  int `json:"start,omitempty"`
	Top    int `json:"top,omitempty"`
	End    int `json:"end,omitempty"`
	Bottom int `json:"bottom,omitempty"`
}

// CornerRadiusSpec defines uniform or per-corner radii in dp.
type CornerRadiusSpec struct {
	All         *int `json:"all,omitempty"`
	TopStart    *int `json:"top_start,omitempty"`
	TopEnd      *int `json:"top_end,omitempty"`
	BottomStart *int `json:"bottom_start,omitempty"`
	BottomEnd   *int `json:"bottom_end,omitempty"`
}

// BorderSpec describes a stroke width in dp and a color token reference.
type BorderSpec struct {
	WidthDp float64       `json:"width_dp"`
	Color   ColorTokenRef `json:"color"`
}

// LayoutConfig defines box styling and spacing for atoms and snippets.
type LayoutConfig struct {
	Padding      *SpacingValues    `json:"padding,omitempty"`
	Margin       *SpacingValues    `json:"margin,omitempty"`
	BgColor      *ColorTokenRef    `json:"bg_color,omitempty"`
	CornerRadius *CornerRadiusSpec `json:"corner_radius,omitempty"`
	Border       *BorderSpec       `json:"border,omitempty"`
	FillMaxWidth *bool             `json:"fill_max_width,omitempty"`
}

// TrackingMeta holds analytics event metadata attached to pages or snippets.
type TrackingMeta struct {
	EventName  string         `json:"event_name"`
	Properties map[string]any `json:"properties,omitempty"`
}

// IconAtom describes a vector icon primitive.
type IconAtom struct {
	Name               string                 `json:"name"`
	SizeDp             *int                   `json:"size_dp,omitempty"`
	Color              *ColorTokenRef         `json:"color,omitempty"`
	ContentDescription string                 `json:"content_description,omitempty"`
	OnClick            *actions.ActionTrigger `json:"on_click,omitempty"`
}

// TagAtom describes a compact pill/badge label.
type TagAtom struct {
	Label   string         `json:"label"`
	BgColor *ColorTokenRef `json:"bg_color,omitempty"`
	Border  *BorderSpec    `json:"border,omitempty"`
	Icon    *IconAtom      `json:"icon,omitempty"`
	Motion  *MotionSpec    `json:"motion,omitempty"`
}

// TextAtom describes a styled text primitive with optional inline icons and click action.
type TextAtom struct {
	ID         string                 `json:"id,omitempty"`
	Text       string                 `json:"text"`
	Color      *ColorTokenRef         `json:"color,omitempty"`
	Typography *TypographyTokenRef    `json:"typography,omitempty"`
	MaxLines   *int                   `json:"max_lines,omitempty"`
	Alignment  string                 `json:"alignment,omitempty"`
	Markdown   bool                   `json:"markdown,omitempty"`
	PrefixIcon *IconAtom              `json:"prefix_icon,omitempty"`
	SuffixIcon *IconAtom              `json:"suffix_icon,omitempty"`
	OnClick    *actions.ActionTrigger `json:"on_click,omitempty"`
	Layout     *LayoutConfig          `json:"layout,omitempty"`
}

// ButtonAtom describes an interactive button primitive.
type ButtonAtom struct {
	ID           string                 `json:"id,omitempty"`
	Label        string                 `json:"label"`
	SubLabel     string                 `json:"sub_label,omitempty"`
	Variant      string                 `json:"variant,omitempty"`
	Size         string                 `json:"size,omitempty"`
	Enabled      *bool                  `json:"enabled,omitempty"`
	Loading      bool                   `json:"loading,omitempty"`
	LeadingIcon  *IconAtom              `json:"leading_icon,omitempty"`
	TrailingIcon *IconAtom              `json:"trailing_icon,omitempty"`
	OnClick      *actions.ActionTrigger `json:"on_click,omitempty"`
	Layout       *LayoutConfig          `json:"layout,omitempty"`
	Motion       *MotionSpec            `json:"motion,omitempty"`
}

// ImageAtom describes a remote image primitive.
type ImageAtom struct {
	ID                 string                 `json:"id,omitempty"`
	URL                string                 `json:"url"`
	ContentDescription string                 `json:"content_description,omitempty"`
	WidthDp            *int                   `json:"width_dp,omitempty"`
	HeightDp           *int                   `json:"height_dp,omitempty"`
	ScaleMode          string                 `json:"scale_mode,omitempty"`
	CornerRadius       *CornerRadiusSpec      `json:"corner_radius,omitempty"`
	OnClick            *actions.ActionTrigger `json:"on_click,omitempty"`
}

// TextFieldAtom describes an editable form input field primitive.
type TextFieldAtom struct {
	FieldID      string `json:"field_id"`
	Label        string `json:"label"`
	Placeholder  string `json:"placeholder,omitempty"`
	InitialValue string `json:"initial_value,omitempty"`
	HelperText   string `json:"helper_text,omitempty"`
	Required     bool   `json:"required,omitempty"`
	Regex        string `json:"regex,omitempty"`
	KeyboardType string `json:"keyboard_type,omitempty"`
}

// DividerAtom describes a horizontal visual separator.
type DividerAtom struct {
	ThicknessDp  *float64       `json:"thickness_dp,omitempty"`
	Color        *ColorTokenRef `json:"color,omitempty"`
	InsetStartDp *int           `json:"inset_start_dp,omitempty"`
}

// SpacerAtom describes fixed whitespace in dp.
type SpacerAtom struct {
	WidthDp  *int `json:"width_dp,omitempty"`
	HeightDp *int `json:"height_dp,omitempty"`
}

// ProgressAtom describes a linear or circular progress indicator.
type ProgressAtom struct {
	Kind     string         `json:"kind"`
	Progress *float64       `json:"progress,omitempty"`
	Label    string         `json:"label,omitempty"`
	Color    *ColorTokenRef `json:"color,omitempty"`
}

// SkeletonKind wire identifiers for SkeletonAtom.Kind.
const (
	SkeletonText      = "text"
	SkeletonRectangle = "rectangle"
	SkeletonCircle    = "circle"
)

// SwitchAtom describes a toggle switch input control.
type SwitchAtom struct {
	FieldID  string                 `json:"field_id"`
	Label    string                 `json:"label"`
	Subtitle string                 `json:"subtitle,omitempty"`
	Checked  bool                   `json:"checked"`
	Enabled  *bool                  `json:"enabled,omitempty"`
	OnChange *actions.ActionTrigger `json:"on_change,omitempty"`
	Layout   *LayoutConfig          `json:"layout,omitempty"`
}

// CheckboxAtom describes a boolean checkbox input control.
type CheckboxAtom struct {
	FieldID  string                 `json:"field_id"`
	Label    string                 `json:"label"`
	Subtitle string                 `json:"subtitle,omitempty"`
	Checked  bool                   `json:"checked"`
	Enabled  *bool                  `json:"enabled,omitempty"`
	Required bool                   `json:"required"`
	OnChange *actions.ActionTrigger `json:"on_change,omitempty"`
	Layout   *LayoutConfig          `json:"layout,omitempty"`
}

// SliderAtom describes a continuous or stepped numeric range slider input control.
type SliderAtom struct {
	FieldID   string                 `json:"field_id"`
	Label     string                 `json:"label,omitempty"`
	Value     float64                `json:"value"`
	MinValue  float64                `json:"min_value"`
	MaxValue  float64                `json:"max_value"`
	Steps     int                    `json:"steps"`
	ShowValue *bool                  `json:"show_value,omitempty"`
	UnitLabel string                 `json:"unit_label,omitempty"`
	Enabled   *bool                  `json:"enabled,omitempty"`
	OnChange  *actions.ActionTrigger `json:"on_change,omitempty"`
	Layout    *LayoutConfig          `json:"layout,omitempty"`
}

// AvatarAtom describes a circular user or entity avatar image/initials primitive.
type AvatarAtom struct {
	ID                 string                 `json:"id,omitempty"`
	URL                string                 `json:"url,omitempty"`
	Initials           string                 `json:"initials,omitempty"`
	SizeDp             *int                   `json:"size_dp,omitempty"`
	BgColor            *ColorTokenRef         `json:"bg_color,omitempty"`
	TextColor          *ColorTokenRef         `json:"text_color,omitempty"`
	ContentDescription string                 `json:"content_description,omitempty"`
	OnClick            *actions.ActionTrigger `json:"on_click,omitempty"`
	Layout             *LayoutConfig          `json:"layout,omitempty"`
}

// SkeletonAtom describes a loading placeholder shimmer shape.
type SkeletonAtom struct {
	ID       string        `json:"id,omitempty"`
	Kind     string        `json:"kind"`
	WidthDp  *int          `json:"width_dp,omitempty"`
	HeightDp *int          `json:"height_dp,omitempty"`
	Layout   *LayoutConfig `json:"layout,omitempty"`
}

// MotionTransition wire identifiers for MotionSpec.Enter and MotionSpec.Exit.
const (
	MotionTransitionNone         = "none"
	MotionTransitionFadeIn       = "fade_in"
	MotionTransitionSlideUp      = "slide_up"
	MotionTransitionSlideDown    = "slide_down"
	MotionTransitionScaleIn      = "scale_in"
	MotionTransitionSpringBounce = "spring_bounce"
)

// MotionDurationToken wire identifiers for MotionSpec.DurationToken.
const (
	MotionDurationInstant = "instant"
	MotionDurationFast    = "fast"
	MotionDurationNormal  = "normal"
	MotionDurationSlow    = "slow"
)

// MotionLoopEffect wire identifiers for MotionSpec.LoopEffect.
const (
	MotionLoopNone      = "none"
	MotionLoopPulse     = "pulse"
	MotionLoopShimmer   = "shimmer"
	MotionLoopWiggle    = "wiggle"
	MotionLoopFloat     = "float"
	MotionLoopHeartbeat = "heartbeat"
	MotionLoopGlowRing  = "glow_ring"
	MotionLoopMarquee   = "marquee"
)

// MotionSpec describes declarative entrance/exit transitions, timing, and ambient loop effects.
type MotionSpec struct {
	Enter         string `json:"enter,omitempty"`
	Exit          string `json:"exit,omitempty"`
	DurationToken string `json:"duration_token,omitempty"`
	DurationMs    *int   `json:"duration_ms,omitempty"`
	StaggerMs     int    `json:"stagger_ms,omitempty"`
	DelayMs       int    `json:"delay_ms,omitempty"`
	LoopEffect    string `json:"loop_effect,omitempty"`
}

// StepperAtom describes an inline quantity add/increment/decrement stepper control.
type StepperAtom struct {
	FieldID     string                 `json:"field_id"`
	Label       string                 `json:"label,omitempty"`
	Quantity    int                    `json:"quantity"`
	MinQuantity int                    `json:"min_quantity,omitempty"`
	MaxQuantity int                    `json:"max_quantity,omitempty"`
	Enabled     *bool                  `json:"enabled,omitempty"`
	OnIncrement *actions.ActionTrigger `json:"on_increment,omitempty"`
	OnDecrement *actions.ActionTrigger `json:"on_decrement,omitempty"`
	Layout      *LayoutConfig          `json:"layout,omitempty"`
}

// CountdownAtom describes a live ticking countdown timer primitive with optional expiry action.
type CountdownAtom struct {
	CountdownID            string                 `json:"countdown_id"`
	RemainingSeconds       int                    `json:"remaining_seconds"`
	Label                  string                 `json:"label,omitempty"`
	UrgentThresholdSeconds int                    `json:"urgent_threshold_seconds,omitempty"`
	ShowHours              bool                   `json:"show_hours,omitempty"`
	ExpiredText            string                 `json:"expired_text,omitempty"`
	OnExpire               *actions.ActionTrigger `json:"on_expire,omitempty"`
	Motion                 *MotionSpec            `json:"motion,omitempty"`
}

// ── Pure Constructors & Pointer Helpers ────────────────────────────────────────

// IntPtr returns a pointer to v.
func IntPtr(v int) *int { return &v }

// Float64Ptr returns a pointer to v.
func Float64Ptr(v float64) *float64 { return &v }

// BoolPtr returns a pointer to v.
func BoolPtr(v bool) *bool { return &v }

// ColorRef creates a ColorTokenRef for the given ColorToken.
func ColorRef(token theming.ColorToken) *ColorTokenRef {
	return &ColorTokenRef{Token: token}
}

// TypographyRef creates a TypographyTokenRef for the given TypographyToken.
func TypographyRef(token theming.TypographyToken) *TypographyTokenRef {
	return &TypographyTokenRef{Token: token}
}

// UniformSpacing returns a SpacingValues with equal dp on all sides.
func UniformSpacing(dp int) *SpacingValues {
	return &SpacingValues{Start: dp, Top: dp, End: dp, Bottom: dp}
}

// SymmetricSpacing returns a SpacingValues with horizontal and vertical dp.
func SymmetricSpacing(horizontal, vertical int) *SpacingValues {
	return &SpacingValues{Start: horizontal, Top: vertical, End: horizontal, Bottom: vertical}
}

// UniformRadius returns a CornerRadiusSpec with an all-corners radius in dp.
func UniformRadius(dp int) *CornerRadiusSpec {
	return &CornerRadiusSpec{All: &dp}
}

// NewText creates a styled TextAtom with token references.
func NewText(text string, typo theming.TypographyToken, color theming.ColorToken) TextAtom {
	return TextAtom{
		Text:       text,
		Typography: TypographyRef(typo),
		Color:      ColorRef(color),
	}
}

// NewButton creates a ButtonAtom with label, variant, and optional click trigger.
func NewButton(id, label, variant string, onClick *actions.ActionTrigger) ButtonAtom {
	return ButtonAtom{
		ID:      id,
		Label:   label,
		Variant: variant,
		Enabled: BoolPtr(true),
		OnClick: onClick,
	}
}

// NewIcon creates an IconAtom with name, size in dp, and semantic color token.
func NewIcon(name string, sizeDp int, color theming.ColorToken) IconAtom {
	return IconAtom{
		Name:   name,
		SizeDp: IntPtr(sizeDp),
		Color:  ColorRef(color),
	}
}

// NewTag creates a TagAtom with label and semantic background color token.
func NewTag(label string, bgColor theming.ColorToken) TagAtom {
	return TagAtom{
		Label:   label,
		BgColor: ColorRef(bgColor),
	}
}

// NewSwitch creates a SwitchAtom with field identifier, label, initial state, and optional change trigger.
func NewSwitch(fieldID, label string, checked bool, onChange *actions.ActionTrigger) SwitchAtom {
	return SwitchAtom{
		FieldID:  fieldID,
		Label:    label,
		Checked:  checked,
		Enabled:  BoolPtr(true),
		OnChange: onChange,
	}
}

// NewCheckbox creates a CheckboxAtom with field identifier, label, initial state, and optional change trigger.
func NewCheckbox(fieldID, label string, checked bool, onChange *actions.ActionTrigger) CheckboxAtom {
	return CheckboxAtom{
		FieldID:  fieldID,
		Label:    label,
		Checked:  checked,
		Enabled:  BoolPtr(true),
		OnChange: onChange,
	}
}

// NewSlider creates a SliderAtom with field identifier, label, range bounds, and optional change trigger.
func NewSlider(fieldID, label string, value, minVal, maxVal float64, onChange *actions.ActionTrigger) SliderAtom {
	return SliderAtom{
		FieldID:   fieldID,
		Label:     label,
		Value:     value,
		MinValue:  minVal,
		MaxValue:  maxVal,
		ShowValue: BoolPtr(true),
		Enabled:   BoolPtr(true),
		OnChange:  onChange,
	}
}

// NewAvatar creates an AvatarAtom with initials, size in dp, and semantic background/text color tokens.
func NewAvatar(initials string, sizeDp int, bgColor, textColor theming.ColorToken) AvatarAtom {
	return AvatarAtom{
		Initials:  initials,
		SizeDp:    IntPtr(sizeDp),
		BgColor:   ColorRef(bgColor),
		TextColor: ColorRef(textColor),
	}
}

// NewSkeleton creates a SkeletonAtom with kind ("text", "rectangle", "circle") and optional dp dimensions.
func NewSkeleton(kind string, widthDp, heightDp int) SkeletonAtom {
	s := SkeletonAtom{
		Kind: kind,
	}
	if widthDp > 0 {
		s.WidthDp = IntPtr(widthDp)
	}
	if heightDp > 0 {
		s.HeightDp = IntPtr(heightDp)
	}
	return s
}

// NewStepper creates a StepperAtom with field identifier, add label (e.g. "ADD"),
// initial quantity, and optional increment/decrement action triggers.
func NewStepper(fieldID, label string, quantity int, onIncrement, onDecrement *actions.ActionTrigger) StepperAtom {
	return StepperAtom{
		FieldID:     fieldID,
		Label:       label,
		Quantity:    quantity,
		Enabled:     BoolPtr(true),
		OnIncrement: onIncrement,
		OnDecrement: onDecrement,
	}
}

// NewCountdown creates a CountdownAtom with default urgency threshold (60s)
// and expired display text ("00:00").
func NewCountdown(id string, remainingSeconds int, label string) CountdownAtom {
	return CountdownAtom{
		CountdownID:            id,
		RemainingSeconds:       remainingSeconds,
		Label:                  label,
		UrgentThresholdSeconds: 60,
		ExpiredText:            "00:00",
	}
}
