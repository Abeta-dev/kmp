package atoms_test

import (
	"encoding/json"
	"testing"

	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

func assertHasKeys(t *testing.T, v any, keys ...string) {
	t.Helper()
	raw, err := json.Marshal(v)
	if err != nil {
		t.Fatalf("marshal %T: %v", v, err)
	}
	var m map[string]any
	if err := json.Unmarshal(raw, &m); err != nil {
		t.Fatalf("unmarshal %T to map: %v", v, err)
	}
	for _, k := range keys {
		if _, ok := m[k]; !ok {
			t.Errorf("%T missing expected JSON key %q in %s", v, k, string(raw))
		}
	}
}

func TestAtomsJSONWireCompatibility(t *testing.T) {
	click := actions.NewNavigateAction("act_1", actions.NavigatePayload{Route: "/next"})
	icon := atoms.NewIcon("star", 20, theming.ColorBrandPrimary)
	icon.ContentDescription = "Star"
	icon.OnClick = &click

	layout := &atoms.LayoutConfig{
		Padding:      atoms.UniformSpacing(16),
		Margin:       atoms.SymmetricSpacing(8, 4),
		BgColor:      atoms.ColorRef(theming.ColorSurfacePrimary),
		CornerRadius: atoms.UniformRadius(12),
		Border:       &atoms.BorderSpec{WidthDp: 1, Color: *atoms.ColorRef(theming.ColorBorderDefault)},
		FillMaxWidth: atoms.BoolPtr(true),
	}

	txt := atoms.NewText("Hello", theming.TypographyBodyLarge, theming.ColorTextPrimary)
	txt.ID = "txt_1"
	txt.MaxLines = atoms.IntPtr(2)
	txt.Alignment = "center"
	txt.Markdown = true
	txt.PrefixIcon = &icon
	txt.SuffixIcon = &icon
	txt.OnClick = &click
	txt.Layout = layout
	assertHasKeys(t, txt, "id", "text", "color", "typography", "max_lines", "alignment", "markdown", "prefix_icon", "suffix_icon", "on_click", "layout")

	motion := &atoms.MotionSpec{
		Enter:         atoms.MotionTransitionSpringBounce,
		Exit:          atoms.MotionTransitionFadeIn,
		DurationToken: atoms.MotionDurationNormal,
		DurationMs:    atoms.IntPtr(260),
		StaggerMs:     60,
		DelayMs:       120,
		LoopEffect:    atoms.MotionLoopPulse,
	}
	assertHasKeys(t, motion, "enter", "exit", "duration_token", "duration_ms", "stagger_ms", "delay_ms", "loop_effect")

	if atoms.MotionTransitionNone != "none" ||
		atoms.MotionTransitionFadeIn != "fade_in" ||
		atoms.MotionTransitionSlideUp != "slide_up" ||
		atoms.MotionTransitionSlideDown != "slide_down" ||
		atoms.MotionTransitionScaleIn != "scale_in" ||
		atoms.MotionTransitionSpringBounce != "spring_bounce" {
		t.Fatal("unexpected MotionTransition constant values")
	}
	if atoms.MotionDurationInstant != "instant" ||
		atoms.MotionDurationFast != "fast" ||
		atoms.MotionDurationNormal != "normal" ||
		atoms.MotionDurationSlow != "slow" {
		t.Fatal("unexpected MotionDurationToken constant values")
	}
	if atoms.MotionLoopNone != "none" ||
		atoms.MotionLoopPulse != "pulse" ||
		atoms.MotionLoopShimmer != "shimmer" ||
		atoms.MotionLoopWiggle != "wiggle" ||
		atoms.MotionLoopFloat != "float" ||
		atoms.MotionLoopHeartbeat != "heartbeat" ||
		atoms.MotionLoopGlowRing != "glow_ring" ||
		atoms.MotionLoopMarquee != "marquee" {
		t.Fatal("unexpected MotionLoopEffect constant values")
	}

	btn := atoms.NewButton("btn_1", "Submit", "primary", &click)
	btn.SubLabel = "Fast"
	btn.Size = "lg"
	btn.Loading = true
	btn.LeadingIcon = &icon
	btn.TrailingIcon = &icon
	btn.Layout = layout
	btn.Motion = motion
	assertHasKeys(t, btn, "id", "label", "sub_label", "variant", "size", "enabled", "loading", "leading_icon", "trailing_icon", "on_click", "layout", "motion")

	assertHasKeys(t, icon, "name", "size_dp", "color", "content_description", "on_click")

	tag := atoms.NewTag("NEW", theming.ColorSuccessContainer)
	tag.Border = &atoms.BorderSpec{WidthDp: 1, Color: *atoms.ColorRef(theming.ColorSuccess)}
	tag.Icon = &icon
	tag.Motion = motion
	assertHasKeys(t, tag, "label", "bg_color", "border", "icon", "motion")

	img := atoms.ImageAtom{
		URL:                "https://cdn.abeta.dev/img.png",
		ContentDescription: "Preview",
		WidthDp:            atoms.IntPtr(120),
		HeightDp:           atoms.IntPtr(80),
		ScaleMode:          "crop",
		CornerRadius:       atoms.UniformRadius(8),
		OnClick:            &click,
	}
	assertHasKeys(t, img, "url", "content_description", "width_dp", "height_dp", "scale_mode", "corner_radius", "on_click")

	field := atoms.TextFieldAtom{
		FieldID:      "email",
		Label:        "Email",
		Placeholder:  "user@abeta.dev",
		InitialValue: "a@b.c",
		HelperText:   "Required",
		Required:     true,
		Regex:        "^.+@.+$",
		KeyboardType: "email",
	}
	assertHasKeys(t, field, "field_id", "label", "placeholder", "initial_value", "helper_text", "required", "regex", "keyboard_type")

	div := atoms.DividerAtom{
		ThicknessDp:  atoms.Float64Ptr(1.5),
		Color:        atoms.ColorRef(theming.ColorDivider),
		InsetStartDp: atoms.IntPtr(16),
	}
	assertHasKeys(t, div, "thickness_dp", "color", "inset_start_dp")

	spc := atoms.SpacerAtom{WidthDp: atoms.IntPtr(8), HeightDp: atoms.IntPtr(16)}
	assertHasKeys(t, spc, "width_dp", "height_dp")

	prog := atoms.ProgressAtom{
		Kind:     "linear",
		Progress: atoms.Float64Ptr(0.5),
		Label:    "50%",
		Color:    atoms.ColorRef(theming.ColorBrandPrimary),
	}
	assertHasKeys(t, prog, "kind", "progress", "label", "color")

	assertHasKeys(t, layout, "padding", "margin", "bg_color", "corner_radius", "border", "fill_max_width")

	meta := atoms.TrackingMeta{EventName: "viewed", Properties: map[string]any{"screen": "home"}}
	assertHasKeys(t, meta, "event_name", "properties")

	sw := atoms.NewSwitch("push_alerts", "Push Alerts", true, &click)
	sw.Subtitle = "Instant notifications"
	sw.Layout = layout
	assertHasKeys(t, sw, "field_id", "label", "subtitle", "checked", "enabled", "on_change", "layout")

	cb := atoms.NewCheckbox("accept_tos", "Accept Terms", false, &click)
	cb.Subtitle = "Required before continuing"
	cb.Required = true
	cb.Layout = layout
	assertHasKeys(t, cb, "field_id", "label", "subtitle", "checked", "enabled", "required", "on_change", "layout")

	sl := atoms.NewSlider("volume", "Alert Volume", 50, 0, 100, &click)
	sl.Steps = 10
	sl.UnitLabel = "%"
	sl.Layout = layout
	assertHasKeys(t, sl, "field_id", "label", "value", "min_value", "max_value", "steps", "show_value", "unit_label", "enabled", "on_change", "layout")

	av := atoms.NewAvatar("AR", 44, theming.ColorBrandContainer, theming.ColorTextBrand)
	av.ID = "av_1"
	av.URL = "https://cdn.abeta.dev/avatar.png"
	av.ContentDescription = "Alex Rivera"
	av.OnClick = &click
	av.Layout = layout
	assertHasKeys(t, av, "id", "url", "initials", "size_dp", "bg_color", "text_color", "content_description", "on_click", "layout")

	skText := atoms.NewSkeleton(atoms.SkeletonText, 0, 16)
	skText.ID = "sk_text"
	skText.Layout = layout
	if skText.WidthDp != nil || skText.HeightDp == nil || *skText.HeightDp != 16 {
		t.Fatalf("unexpected text skeleton dimensions: %+v", skText)
	}
	assertHasKeys(t, skText, "id", "kind", "height_dp", "layout")

	skRect := atoms.NewSkeleton(atoms.SkeletonRectangle, 160, 80)
	skRect.ID = "sk_rect"
	skRect.Layout = layout
	assertHasKeys(t, skRect, "id", "kind", "width_dp", "height_dp", "layout")

	skCircle := atoms.NewSkeleton(atoms.SkeletonCircle, 40, 40)
	if skCircle.Kind != "circle" || *skCircle.WidthDp != 40 || *skCircle.HeightDp != 40 {
		t.Fatalf("unexpected circle skeleton: %+v", skCircle)
	}

	inc := actions.NewTriggerFeedbackAction("fb_inc", actions.TriggerFeedbackPayload{Effect: actions.FeedbackCartBounce})
	dec := actions.NewTrackEventAction("trk_dec", actions.TrackEventPayload{EventName: "cart_decremented"})
	stp := atoms.NewStepper("cart_qty_avocado", "ADD", 1, &inc, &dec)
	stp.MinQuantity = 1
	stp.MaxQuantity = 10
	stp.Layout = layout
	assertHasKeys(t, stp, "field_id", "label", "quantity", "min_quantity", "max_quantity", "enabled", "on_increment", "on_decrement", "layout")

	cd := atoms.NewCountdown("flash_timer", 540, "Ends in")
	if cd.CountdownID != "flash_timer" || cd.RemainingSeconds != 540 || cd.Label != "Ends in" || cd.UrgentThresholdSeconds != 60 || cd.ExpiredText != "00:00" || cd.ShowHours {
		t.Fatalf("unexpected default CountdownAtom: %+v", cd)
	}
	expireAct := actions.NewShowToastAction("cd_expired", actions.ShowToastPayload{Message: "Offer expired"})
	cd.ShowHours = true
	cd.OnExpire = &expireAct
	cd.Motion = &atoms.MotionSpec{LoopEffect: atoms.MotionLoopHeartbeat}
	assertHasKeys(t, cd, "countdown_id", "remaining_seconds", "label", "urgent_threshold_seconds", "show_hours", "expired_text", "on_expire", "motion")
}
