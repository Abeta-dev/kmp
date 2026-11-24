package atoms_test

import (
	"encoding/json"
	"reflect"
	"testing"

	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

func assertRoundTrip[T any](t *testing.T, want T, expectedKeys ...string) {
	t.Helper()
	assertHasKeys(t, want, expectedKeys...)

	raw, err := json.Marshal(want)
	if err != nil {
		t.Fatalf("marshal %T: %v", want, err)
	}
	var got T
	if err := json.Unmarshal(raw, &got); err != nil {
		t.Fatalf("unmarshal %T: %v", want, err)
	}
	if !reflect.DeepEqual(got, want) {
		t.Fatalf("round-trip mismatch for %T:\ngot:  %+v\nwant: %+v\njson: %s", want, got, want, string(raw))
	}
}

func assertOmitsKeys(t *testing.T, v any, omittedKeys ...string) {
	t.Helper()
	raw, err := json.Marshal(v)
	if err != nil {
		t.Fatalf("marshal %T: %v", v, err)
	}
	var m map[string]any
	if err := json.Unmarshal(raw, &m); err != nil {
		t.Fatalf("unmarshal %T to map: %v", v, err)
	}
	for _, k := range omittedKeys {
		if _, exists := m[k]; exists {
			t.Errorf("%T expected key %q to be omitted, got %s", v, k, string(raw))
		}
	}
}

func TestExtendedAtoms_JSONRoundTripAndWireParity(t *testing.T) {
	if atoms.VariantNeutral != "neutral" ||
		atoms.VariantBrand != "brand" ||
		atoms.VariantSuccess != "success" ||
		atoms.VariantWarning != "warning" ||
		atoms.VariantError != "error" ||
		atoms.VariantInfo != "info" {
		t.Fatal("unexpected semantic variant wire constant values")
	}

	act := actions.NewTrackEventAction("ext_act", actions.TrackEventPayload{
		EventName:  "extended_atom_interacted",
		Properties: map[string]any{"surface": "extended-atoms-test"},
	})
	removeAct := actions.NewShowToastAction("ext_remove", actions.ShowToastPayload{
		Message:    "Removed item",
		DurationMs: 1200,
		Variant:    "info",
	})
	icon := atoms.NewIcon("sparkles", 18, theming.ColorBrandPrimary)
	layout := &atoms.LayoutConfig{
		Padding:      atoms.UniformSpacing(12),
		BgColor:      atoms.ColorRef(theming.ColorSurfacePrimary),
		CornerRadius: atoms.UniformRadius(10),
		FillMaxWidth: atoms.BoolPtr(true),
	}
	motion := &atoms.MotionSpec{
		Enter:         atoms.MotionTransitionScaleIn,
		DurationToken: atoms.MotionDurationFast,
		LoopEffect:    atoms.MotionLoopPulse,
	}

	// 0. OptionItem (full + minimal)
	optFull := atoms.OptionItem{
		ID:       "opt_express",
		Label:    "Express",
		Subtitle: "10 min delivery",
		Enabled:  atoms.BoolPtr(false),
		Badge:    "FAST",
	}
	assertRoundTrip(t, optFull, "id", "label", "subtitle", "enabled", "badge")
	assertOmitsKeys(t, atoms.OptionItem{ID: "opt_min", Label: "Standard"}, "subtitle", "enabled", "badge")

	// 1. RadioGroupAtom + defensive option copy
	seedOpts := []atoms.OptionItem{
		{ID: "express", Label: "Express", Subtitle: "10 mins", Enabled: atoms.BoolPtr(true), Badge: "FAST"},
		{ID: "standard", Label: "Standard", Enabled: atoms.BoolPtr(true)},
	}
	radio := atoms.NewRadioGroup("delivery_speed", "Delivery Speed", "express", seedOpts...)
	seedOpts[0].Label = "Mutated"
	if radio.Options[0].Label != "Express" {
		t.Fatalf("expected defensive copy of radio options, got %+v", radio.Options)
	}
	radio.OnChange = &act
	radio.Layout = layout
	assertRoundTrip(t, radio, "field_id", "label", "options", "selected_id", "enabled", "on_change", "layout")
	assertOmitsKeys(t, atoms.RadioGroupAtom{FieldID: "rg_min"}, "label", "options", "selected_id", "enabled", "on_change", "layout")

	// 2. SelectAtom
	sel := atoms.NewSelect("delivery_slot", "Delivery Slot", "Select a window", "now",
		atoms.OptionItem{ID: "now", Label: "Now", Badge: "LIVE"},
		atoms.OptionItem{ID: "later", Label: "Later"},
	)
	sel.HelperText = "Live slots refresh every minute"
	sel.ErrorText = "Slot unavailable"
	sel.OnChange = &act
	sel.Layout = layout
	assertRoundTrip(t, sel, "field_id", "label", "placeholder", "options", "selected_id", "helper_text", "error_text", "enabled", "on_change", "layout")
	assertOmitsKeys(t, atoms.SelectAtom{FieldID: "sel_min"}, "label", "placeholder", "options", "selected_id", "helper_text", "error_text", "enabled", "on_change", "layout")

	// 3. OtpInputAtom
	otp := atoms.NewOtpInput("otp_code", "Verification Code", 6, &act)
	otp.InitialValue = "123"
	otp.Masked = true
	otp.HelperText = "6-digit SMS code"
	otp.ErrorText = "Invalid code"
	otp.Layout = layout
	assertRoundTrip(t, otp, "field_id", "label", "length", "initial_value", "masked", "helper_text", "error_text", "enabled", "on_complete", "layout")
	assertOmitsKeys(t, atoms.OtpInputAtom{FieldID: "otp_min"}, "label", "length", "initial_value", "masked", "helper_text", "error_text", "enabled", "on_complete", "layout")

	// 4. ChipAtom
	chip := atoms.NewChip("chip_organic", "Organic", true, &act)
	chip.Removable = true
	chip.LeadingIcon = &icon
	chip.OnRemove = &removeAct
	chip.Layout = layout
	assertRoundTrip(t, chip, "id", "label", "selected", "enabled", "removable", "leading_icon", "on_click", "on_remove", "layout")
	assertOmitsKeys(t, atoms.ChipAtom{ID: "ch_min", Label: "All"}, "selected", "enabled", "removable", "leading_icon", "on_click", "on_remove", "layout")

	// 5. SegmentedControlAtom (also verify empty options clone returns nil)
	emptySeg := atoms.NewSegmentedControl("empty_seg", "")
	if emptySeg.Options != nil {
		t.Fatalf("expected nil options for empty segmented control, got %+v", emptySeg.Options)
	}
	seg := atoms.NewSegmentedControl("billing_cycle", "annual",
		atoms.OptionItem{ID: "monthly", Label: "Monthly"},
		atoms.OptionItem{ID: "annual", Label: "Annual", Badge: "SAVE"},
	)
	seg.OnChange = &act
	seg.Layout = layout
	assertRoundTrip(t, seg, "field_id", "options", "selected_id", "enabled", "on_change", "layout")
	assertOmitsKeys(t, atoms.SegmentedControlAtom{FieldID: "seg_min"}, "options", "selected_id", "enabled", "on_change", "layout")

	// 6. RatingAtom (including pointer 0 review_count preservation)
	rating := atoms.NewRating("rating_1", 4.8, 5)
	rating.FieldID = "merchant_rating"
	rating.Interactive = true
	rating.ReviewCount = atoms.IntPtr(0)
	rating.Label = "4.8 stars"
	rating.StarSizeDp = 20
	rating.OnRate = &act
	rating.Layout = layout
	assertRoundTrip(t, rating, "id", "field_id", "value", "max_stars", "interactive", "review_count", "label", "star_size_dp", "on_rate", "layout")
	assertOmitsKeys(t, atoms.RatingAtom{Value: 4.5}, "id", "field_id", "max_stars", "interactive", "review_count", "label", "star_size_dp", "on_rate", "layout")

	// 7. PriceAtom (including explicit Emphasized=false preservation)
	price := atoms.NewPrice("price_1", "$3.99", "$5.99")
	price.DiscountLabel = "33% OFF"
	price.UnitSuffix = "/ pack"
	price.Emphasized = atoms.BoolPtr(false)
	price.Layout = layout
	assertRoundTrip(t, price, "id", "amount_text", "original_amount_text", "discount_label", "unit_suffix", "emphasized", "layout")
	assertOmitsKeys(t, atoms.PriceAtom{AmountText: "$1.00"}, "id", "original_amount_text", "discount_label", "unit_suffix", "emphasized", "layout")

	// 8. BadgeAtom (including pointer Count=0 preservation)
	badge := atoms.NewBadge("badge_1", 0, 99, atoms.VariantError)
	badge.Label = "NEW"
	badge.DotOnly = true
	badge.Layout = layout
	badge.Motion = motion
	assertRoundTrip(t, badge, "id", "count", "max_count", "label", "dot_only", "variant", "layout", "motion")
	assertOmitsKeys(t, atoms.BadgeAtom{}, "id", "count", "max_count", "label", "dot_only", "variant", "layout", "motion")

	// 9. AlertBannerAtom
	alert := atoms.NewAlertBanner("alert_1", "Scheduled Maintenance", "Brief read-only window tonight", atoms.VariantWarning)
	alert.Icon = &icon
	alert.ActionLabel = "Details"
	alert.OnAction = &act
	alert.Dismissible = true
	alert.OnDismiss = &removeAct
	alert.Layout = layout
	assertRoundTrip(t, alert, "id", "title", "message", "variant", "icon", "action_label", "on_action", "dismissible", "on_dismiss", "layout")
	assertOmitsKeys(t, atoms.AlertBannerAtom{Title: "Heads up"}, "id", "message", "variant", "icon", "action_label", "on_action", "dismissible", "on_dismiss", "layout")

	// 10. StatTileAtom (including explicit DeltaPositive=false preservation)
	stat := atoms.NewStatTile("stat_1", "Avg Latency", "14ms")
	stat.DeltaLabel = "-3ms"
	stat.DeltaPositive = atoms.BoolPtr(false)
	stat.Caption = "p95 last 24h"
	stat.Icon = &icon
	stat.OnClick = &act
	stat.Layout = layout
	assertRoundTrip(t, stat, "id", "label", "value", "delta_label", "delta_positive", "caption", "icon", "on_click", "layout")
	assertOmitsKeys(t, atoms.StatTileAtom{Label: "Uptime", Value: "99.9%"}, "id", "delta_label", "delta_positive", "caption", "icon", "on_click", "layout")

	// 11. KeyValueRowAtom
	kv := atoms.NewKeyValueRow("kv_1", "Total", "$24.90")
	kv.Sublabel = "Tax included"
	kv.ValueColor = atoms.ColorRef(theming.ColorBrandPrimary)
	kv.Emphasized = true
	kv.ShowDivider = true
	kv.OnClick = &act
	kv.Layout = layout
	assertRoundTrip(t, kv, "id", "label", "value", "sublabel", "value_color", "emphasized", "show_divider", "on_click", "layout")
	assertOmitsKeys(t, atoms.KeyValueRowAtom{Label: "Fee", Value: "$0.00"}, "id", "sublabel", "value_color", "emphasized", "show_divider", "on_click", "layout")

	// 12. QrCodeAtom
	qr := atoms.NewQrCode("qr_1", "abeta://pickup/ORD-1001", "Pickup Pass", 160)
	qr.Caption = "Show at locker"
	qr.OnClick = &act
	qr.Layout = layout
	assertRoundTrip(t, qr, "id", "payload", "label", "caption", "size_dp", "on_click", "layout")
	assertOmitsKeys(t, atoms.QrCodeAtom{Payload: "abeta://min"}, "id", "label", "caption", "size_dp", "on_click", "layout")

	// 13. MediaPagerAtom (29th wire atom) + defensive item copy
	seedImgs := []atoms.ImageAtom{
		{ID: "img_banner_1", URL: "https://cdn.abeta.dev/fresh/banner1.png", ContentDescription: "Fresh harvest"},
		{ID: "img_banner_2", URL: "https://cdn.abeta.dev/fresh/banner2.png", ContentDescription: "8-min dairy"},
	}
	pager := atoms.NewMediaPager("pager_hero", seedImgs, 180)
	seedImgs[0].URL = "https://mutated.example/bad.png"
	if pager.Items[0].URL != "https://cdn.abeta.dev/fresh/banner1.png" {
		t.Fatalf("expected defensive copy of MediaPagerAtom items, got %+v", pager.Items)
	}
	pager.AutoAdvanceMs = atoms.IntPtr(4000)
	pager.CornerRadius = atoms.UniformRadius(12)
	pager.OnPageClick = &act
	pager.Layout = layout
	assertRoundTrip(t, pager, "id", "items", "height_dp", "auto_advance_ms", "show_indicators", "corner_radius", "on_page_click", "layout")
	assertOmitsKeys(t, atoms.MediaPagerAtom{}, "id", "items", "height_dp", "auto_advance_ms", "show_indicators", "corner_radius", "on_page_click", "layout")
	emptyPager := atoms.NewMediaPager("pager_empty", nil, 120)
	if emptyPager.Items != nil || emptyPager.ShowIndicators == nil || !*emptyPager.ShowIndicators {
		t.Fatalf("unexpected empty MediaPagerAtom: %+v", emptyPager)
	}
}
