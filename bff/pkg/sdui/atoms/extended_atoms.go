package atoms

import "github.com/abeta-dev/kmp/bff/pkg/sdui/actions"

// Semantic variant wire identifiers shared by BadgeAtom.Variant and AlertBannerAtom.Variant.
const (
	VariantNeutral = "neutral"
	VariantBrand   = "brand"
	VariantSuccess = "success"
	VariantWarning = "warning"
	VariantError   = "error"
	VariantInfo    = "info"
)

// OptionItem describes a selectable choice shared by RadioGroupAtom, SelectAtom,
// and SegmentedControlAtom.
type OptionItem struct {
	ID       string `json:"id"`
	Label    string `json:"label"`
	Subtitle string `json:"subtitle,omitempty"`
	Enabled  *bool  `json:"enabled,omitempty"`
	Badge    string `json:"badge,omitempty"`
}

// RadioGroupAtom describes a single-selection radio group form input primitive.
type RadioGroupAtom struct {
	FieldID    string                 `json:"field_id"`
	Label      string                 `json:"label,omitempty"`
	Options    []OptionItem           `json:"options,omitempty"`
	SelectedID string                 `json:"selected_id,omitempty"`
	Enabled    *bool                  `json:"enabled,omitempty"`
	OnChange   *actions.ActionTrigger `json:"on_change,omitempty"`
	Layout     *LayoutConfig          `json:"layout,omitempty"`
}

// SelectAtom describes a dropdown or picker selection form input primitive.
type SelectAtom struct {
	FieldID     string                 `json:"field_id"`
	Label       string                 `json:"label,omitempty"`
	Placeholder string                 `json:"placeholder,omitempty"`
	Options     []OptionItem           `json:"options,omitempty"`
	SelectedID  string                 `json:"selected_id,omitempty"`
	HelperText  string                 `json:"helper_text,omitempty"`
	ErrorText   string                 `json:"error_text,omitempty"`
	Enabled     *bool                  `json:"enabled,omitempty"`
	OnChange    *actions.ActionTrigger `json:"on_change,omitempty"`
	Layout      *LayoutConfig          `json:"layout,omitempty"`
}

// OtpInputAtom describes a fixed-length one-time passcode / PIN input primitive.
type OtpInputAtom struct {
	FieldID      string                 `json:"field_id"`
	Label        string                 `json:"label,omitempty"`
	Length       int                    `json:"length,omitempty"`
	InitialValue string                 `json:"initial_value,omitempty"`
	Masked       bool                   `json:"masked,omitempty"`
	HelperText   string                 `json:"helper_text,omitempty"`
	ErrorText    string                 `json:"error_text,omitempty"`
	Enabled      *bool                  `json:"enabled,omitempty"`
	OnComplete   *actions.ActionTrigger `json:"on_complete,omitempty"`
	Layout       *LayoutConfig          `json:"layout,omitempty"`
}

// ChipAtom describes an interactive filter, selection, or removable chip primitive.
type ChipAtom struct {
	ID          string                 `json:"id"`
	Label       string                 `json:"label"`
	Selected    bool                   `json:"selected,omitempty"`
	Enabled     *bool                  `json:"enabled,omitempty"`
	Removable   bool                   `json:"removable,omitempty"`
	LeadingIcon *IconAtom              `json:"leading_icon,omitempty"`
	OnClick     *actions.ActionTrigger `json:"on_click,omitempty"`
	OnRemove    *actions.ActionTrigger `json:"on_remove,omitempty"`
	Layout      *LayoutConfig          `json:"layout,omitempty"`
}

// SegmentedControlAtom describes an inline mutually-exclusive segmented toggle primitive.
type SegmentedControlAtom struct {
	FieldID    string                 `json:"field_id"`
	Options    []OptionItem           `json:"options,omitempty"`
	SelectedID string                 `json:"selected_id,omitempty"`
	Enabled    *bool                  `json:"enabled,omitempty"`
	OnChange   *actions.ActionTrigger `json:"on_change,omitempty"`
	Layout     *LayoutConfig          `json:"layout,omitempty"`
}

// RatingAtom describes a read-only or interactive star rating primitive.
type RatingAtom struct {
	ID          string                 `json:"id,omitempty"`
	FieldID     string                 `json:"field_id,omitempty"`
	Value       float64                `json:"value"`
	MaxStars    int                    `json:"max_stars,omitempty"`
	Interactive bool                   `json:"interactive,omitempty"`
	ReviewCount *int                   `json:"review_count,omitempty"`
	Label       string                 `json:"label,omitempty"`
	StarSizeDp  int                    `json:"star_size_dp,omitempty"`
	OnRate      *actions.ActionTrigger `json:"on_rate,omitempty"`
	Layout      *LayoutConfig          `json:"layout,omitempty"`
}

// PriceAtom describes a commerce price display with optional strikethrough original amount,
// discount callout, and unit suffix.
type PriceAtom struct {
	ID                 string        `json:"id,omitempty"`
	AmountText         string        `json:"amount_text"`
	OriginalAmountText string        `json:"original_amount_text,omitempty"`
	DiscountLabel      string        `json:"discount_label,omitempty"`
	UnitSuffix         string        `json:"unit_suffix,omitempty"`
	Emphasized         *bool         `json:"emphasized,omitempty"`
	Layout             *LayoutConfig `json:"layout,omitempty"`
}

// BadgeAtom describes a compact numeric counter, label, or status dot badge primitive.
type BadgeAtom struct {
	ID       string        `json:"id,omitempty"`
	Count    *int          `json:"count,omitempty"`
	MaxCount int           `json:"max_count,omitempty"`
	Label    string        `json:"label,omitempty"`
	DotOnly  bool          `json:"dot_only,omitempty"`
	Variant  string        `json:"variant,omitempty"`
	Layout   *LayoutConfig `json:"layout,omitempty"`
	Motion   *MotionSpec   `json:"motion,omitempty"`
}

// AlertBannerAtom describes an inline semantic callout banner with optional CTA and dismiss hook.
type AlertBannerAtom struct {
	ID          string                 `json:"id,omitempty"`
	Title       string                 `json:"title"`
	Message     string                 `json:"message,omitempty"`
	Variant     string                 `json:"variant,omitempty"`
	Icon        *IconAtom              `json:"icon,omitempty"`
	ActionLabel string                 `json:"action_label,omitempty"`
	OnAction    *actions.ActionTrigger `json:"on_action,omitempty"`
	Dismissible bool                   `json:"dismissible,omitempty"`
	OnDismiss   *actions.ActionTrigger `json:"on_dismiss,omitempty"`
	Layout      *LayoutConfig          `json:"layout,omitempty"`
}

// StatTileAtom describes a compact KPI / metric readout tile with optional trend delta and icon.
type StatTileAtom struct {
	ID            string                 `json:"id,omitempty"`
	Label         string                 `json:"label"`
	Value         string                 `json:"value"`
	DeltaLabel    string                 `json:"delta_label,omitempty"`
	DeltaPositive *bool                  `json:"delta_positive,omitempty"`
	Caption       string                 `json:"caption,omitempty"`
	Icon          *IconAtom              `json:"icon,omitempty"`
	OnClick       *actions.ActionTrigger `json:"on_click,omitempty"`
	Layout        *LayoutConfig          `json:"layout,omitempty"`
}

// KeyValueRowAtom describes a structured label/value summary row (e.g. receipts, checkout totals).
type KeyValueRowAtom struct {
	ID          string                 `json:"id,omitempty"`
	Label       string                 `json:"label"`
	Value       string                 `json:"value"`
	Sublabel    string                 `json:"sublabel,omitempty"`
	ValueColor  *ColorTokenRef         `json:"value_color,omitempty"`
	Emphasized  bool                   `json:"emphasized,omitempty"`
	ShowDivider bool                   `json:"show_divider,omitempty"`
	OnClick     *actions.ActionTrigger `json:"on_click,omitempty"`
	Layout      *LayoutConfig          `json:"layout,omitempty"`
}

// QrCodeAtom describes a deterministic QR code payload display primitive.
type QrCodeAtom struct {
	ID      string                 `json:"id,omitempty"`
	Payload string                 `json:"payload"`
	Label   string                 `json:"label,omitempty"`
	Caption string                 `json:"caption,omitempty"`
	SizeDp  int                    `json:"size_dp,omitempty"`
	OnClick *actions.ActionTrigger `json:"on_click,omitempty"`
	Layout  *LayoutConfig          `json:"layout,omitempty"`
}

// ── Pure Constructors ──────────────────────────────────────────────────────────

func cloneOptions(options []OptionItem) []OptionItem {
	if len(options) == 0 {
		return nil
	}
	out := make([]OptionItem, len(options))
	copy(out, options)
	return out
}

// NewRadioGroup creates a RadioGroupAtom with defensive copy of options and Enabled=true.
func NewRadioGroup(fieldID, label, selectedID string, options ...OptionItem) RadioGroupAtom {
	return RadioGroupAtom{
		FieldID:    fieldID,
		Label:      label,
		Options:    cloneOptions(options),
		SelectedID: selectedID,
		Enabled:    BoolPtr(true),
	}
}

// NewSelect creates a SelectAtom with defensive copy of options and Enabled=true.
func NewSelect(fieldID, label, placeholder, selectedID string, options ...OptionItem) SelectAtom {
	return SelectAtom{
		FieldID:     fieldID,
		Label:       label,
		Placeholder: placeholder,
		Options:     cloneOptions(options),
		SelectedID:  selectedID,
		Enabled:     BoolPtr(true),
	}
}

// NewOtpInput creates an OtpInputAtom with field identifier, label, digit length, and Enabled=true.
func NewOtpInput(fieldID, label string, length int, onComplete *actions.ActionTrigger) OtpInputAtom {
	return OtpInputAtom{
		FieldID:    fieldID,
		Label:      label,
		Length:     length,
		Enabled:    BoolPtr(true),
		OnComplete: onComplete,
	}
}

// NewChip creates a ChipAtom with identifier, label, selection state, and Enabled=true.
func NewChip(id, label string, selected bool, onClick *actions.ActionTrigger) ChipAtom {
	return ChipAtom{
		ID:       id,
		Label:    label,
		Selected: selected,
		Enabled:  BoolPtr(true),
		OnClick:  onClick,
	}
}

// NewSegmentedControl creates a SegmentedControlAtom with defensive copy of options and Enabled=true.
func NewSegmentedControl(fieldID, selectedID string, options ...OptionItem) SegmentedControlAtom {
	return SegmentedControlAtom{
		FieldID:    fieldID,
		Options:    cloneOptions(options),
		SelectedID: selectedID,
		Enabled:    BoolPtr(true),
	}
}

// NewRating creates a RatingAtom with numeric value and max star count.
func NewRating(id string, value float64, maxStars int) RatingAtom {
	return RatingAtom{
		ID:       id,
		Value:    value,
		MaxStars: maxStars,
	}
}

// NewPrice creates a PriceAtom with formatted amount text and optional strikethrough original amount.
func NewPrice(id, amountText, originalAmountText string) PriceAtom {
	return PriceAtom{
		ID:                 id,
		AmountText:         amountText,
		OriginalAmountText: originalAmountText,
		Emphasized:         BoolPtr(true),
	}
}

// NewBadge creates a numeric counter BadgeAtom with semantic variant.
func NewBadge(id string, count, maxCount int, variant string) BadgeAtom {
	return BadgeAtom{
		ID:       id,
		Count:    IntPtr(count),
		MaxCount: maxCount,
		Variant:  variant,
	}
}

// NewAlertBanner creates an AlertBannerAtom with title, message, and semantic variant.
func NewAlertBanner(id, title, message, variant string) AlertBannerAtom {
	return AlertBannerAtom{
		ID:      id,
		Title:   title,
		Message: message,
		Variant: variant,
	}
}

// NewStatTile creates a StatTileAtom with label and display value.
func NewStatTile(id, label, value string) StatTileAtom {
	return StatTileAtom{
		ID:    id,
		Label: label,
		Value: value,
	}
}

// NewKeyValueRow creates a KeyValueRowAtom with label and formatted value.
func NewKeyValueRow(id, label, value string) KeyValueRowAtom {
	return KeyValueRowAtom{
		ID:    id,
		Label: label,
		Value: value,
	}
}

// NewQrCode creates a QrCodeAtom with encoded payload, display label, and square size in dp.
func NewQrCode(id, payload, label string, sizeDp int) QrCodeAtom {
	return QrCodeAtom{
		ID:      id,
		Payload: payload,
		Label:   label,
		SizeDp:  sizeDp,
	}
}
