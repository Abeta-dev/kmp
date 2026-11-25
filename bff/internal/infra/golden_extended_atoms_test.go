package infra_test

import (
	"encoding/json"
	"testing"

	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

func canonicalExtendedAtomsScreen() snippets.PageResponse {
	ptrAction := func(a actions.ActionTrigger) *actions.ActionTrigger { return &a }
	ptrIcon := func(i atoms.IconAtom) *atoms.IconAtom { return &i }

	cardLayout := atoms.LayoutConfig{
		Padding:      atoms.UniformSpacing(theming.SpacingLG.Dp()),
		Margin:       atoms.SymmetricSpacing(theming.SpacingLG.Dp(), theming.SpacingXS.Dp()),
		BgColor:      atoms.ColorRef(theming.ColorSurfacePrimary),
		CornerRadius: atoms.UniformRadius(theming.RadiusMD.Dp()),
		Border: &atoms.BorderSpec{
			WidthDp: 1,
			Color:   *atoms.ColorRef(theming.ColorBorderSubtle),
		},
		FillMaxWidth: atoms.BoolPtr(true),
	}

	toolbarTitle := atoms.NewText("Abeta Extended Atoms", theming.TypographyTitleLarge, theming.ColorTextPrimary)
	toolbarSub := atoms.NewText("12 extended UI atoms molded inside composed_block", theming.TypographyBodySmall, theming.ColorTextSecondary)
	backIcon := atoms.NewIcon("arrow_back", 22, theming.ColorTextPrimary)
	backIcon.ContentDescription = "Navigate back"
	backIcon.OnClick = ptrAction(actions.NewBackPressAction("back_from_extended_atoms", actions.BackPressPayload{Steps: 1}))

	// 1. radio_group
	radioAtom := atoms.NewRadioGroup(
		"delivery_speed",
		"Delivery Speed",
		"express",
		atoms.OptionItem{
			ID:       "express",
			Label:    "Express 10 min",
			Subtitle: "Cold-chain courier",
			Enabled:  atoms.BoolPtr(true),
			Badge:    "FAST",
		},
		atoms.OptionItem{
			ID:       "standard",
			Label:    "Standard 30 min",
			Subtitle: "Free with Abeta Club",
			Enabled:  atoms.BoolPtr(true),
		},
		atoms.OptionItem{
			ID:       "scheduled",
			Label:    "Evening Slot",
			Subtitle: "Currently full",
			Enabled:  atoms.BoolPtr(false),
		},
	)
	radioAtom.OnChange = ptrAction(actions.NewSaveStateAction("save_delivery_speed", actions.SaveStatePayload{
		Key:   "delivery_speed",
		Value: "express",
		Scope: "session",
	}))

	// 2. select
	selectAtom := atoms.NewSelect(
		"delivery_slot",
		"Preferred Window",
		"Choose a delivery slot",
		"now",
		atoms.OptionItem{ID: "now", Label: "Right Now (8-10 min)", Badge: "LIVE"},
		atoms.OptionItem{ID: "noon", Label: "12:00 - 13:00", Subtitle: "Zero surge fee"},
	)
	selectAtom.HelperText = "Live availability updated every minute"
	selectAtom.OnChange = ptrAction(actions.NewTrackEventAction("select_slot_changed", actions.TrackEventPayload{
		EventName:  "slot_selected",
		Properties: map[string]any{"field_id": "delivery_slot"},
	}))

	// 3. otp_input
	otpAtom := atoms.NewOtpInput(
		"checkout_otp",
		"Verification Code",
		6,
		ptrAction(actions.NewCallAPIAction("verify_checkout_otp", actions.CallAPIPayload{
			Endpoint: "/api/v1/actions/execute",
			Method:   "POST",
			Body:     map[string]any{"field_id": "checkout_otp"},
		})),
	)
	otpAtom.InitialValue = "482"
	otpAtom.Masked = true
	otpAtom.HelperText = "Enter the 6-digit SMS code sent to +1 ***-0192"

	// 4. chip
	chipAtom := atoms.NewChip(
		"chip_organic",
		"Organic",
		true,
		ptrAction(actions.NewTrackEventAction("toggle_chip_organic", actions.TrackEventPayload{
			EventName:  "chip_clicked",
			Properties: map[string]any{"chip_id": "chip_organic"},
		})),
	)
	chipAtom.Removable = true
	chipAtom.LeadingIcon = ptrIcon(atoms.NewIcon("eco", 16, theming.ColorSuccess))
	chipAtom.OnRemove = ptrAction(actions.NewShowToastAction("remove_chip_organic", actions.ShowToastPayload{
		Message:    "Removed Organic filter",
		DurationMs: 1400,
		Variant:    "info",
	}))

	// 5. segmented_control
	segmentedAtom := atoms.NewSegmentedControl(
		"billing_cycle",
		"annual",
		atoms.OptionItem{ID: "monthly", Label: "Monthly"},
		atoms.OptionItem{ID: "annual", Label: "Annual", Badge: "SAVE 20%"},
	)
	segmentedAtom.OnChange = ptrAction(actions.NewSaveStateAction("save_billing_cycle", actions.SaveStatePayload{
		Key:   "billing_cycle",
		Value: "annual",
		Scope: "session",
	}))

	// 6. rating
	ratingAtom := atoms.NewRating("rating_merchant", 4.8, 5)
	ratingAtom.FieldID = "merchant_rating"
	ratingAtom.Interactive = true
	ratingAtom.ReviewCount = atoms.IntPtr(1248)
	ratingAtom.Label = "4.8 out of 5"
	ratingAtom.StarSizeDp = 20
	ratingAtom.OnRate = ptrAction(actions.NewTrackEventAction("rate_merchant", actions.TrackEventPayload{
		EventName:  "rating_submitted",
		Properties: map[string]any{"field_id": "merchant_rating"},
	}))

	// 7. price
	priceAtom := atoms.NewPrice("price_avocados", "$3.99", "$5.99")
	priceAtom.DiscountLabel = "33% OFF"
	priceAtom.UnitSuffix = "/ pack"

	// 8. badge
	badgeAtom := atoms.NewBadge("badge_unread_alerts", 12, 99, atoms.VariantError)
	badgeAtom.Motion = &atoms.MotionSpec{LoopEffect: atoms.MotionLoopPulse}

	// 9. alert_banner
	alertAtom := atoms.NewAlertBanner(
		"alert_cold_chain",
		"Cold-chain guarantee active",
		"Insulated thermal bag maintained below 4°C until doorstep handoff.",
		atoms.VariantInfo,
	)
	alertAtom.Icon = ptrIcon(atoms.NewIcon("info", 20, theming.ColorInfo))
	alertAtom.ActionLabel = "Learn more"
	alertAtom.OnAction = ptrAction(actions.NewShowToastAction("alert_cold_chain_info", actions.ShowToastPayload{
		Message:    "Sensors verified every 30s",
		DurationMs: 1600,
		Variant:    "info",
	}))
	alertAtom.Dismissible = true
	alertAtom.OnDismiss = ptrAction(actions.NewTrackEventAction("dismiss_cold_chain_alert", actions.TrackEventPayload{
		EventName:  "alert_dismissed",
		Properties: map[string]any{"alert_id": "alert_cold_chain"},
	}))

	// 10. stat_tile
	statAtom := atoms.NewStatTile("stat_on_time_rate", "On-Time Rate", "99.4%")
	statAtom.DeltaLabel = "+1.2%"
	statAtom.DeltaPositive = atoms.BoolPtr(true)
	statAtom.Caption = "Last 30 days"
	statAtom.Icon = ptrIcon(atoms.NewIcon("bolt", 20, theming.ColorSuccess))
	statAtom.OnClick = ptrAction(actions.NewNavigateAction("nav_metrics_detail", actions.NavigatePayload{
		Route: "/screens/home",
	}))

	// 11. key_value_row
	kvAtom := atoms.NewKeyValueRow("kv_order_total", "Express Total", "$24.90")
	kvAtom.Sublabel = "Includes $0.00 Abeta Club delivery"
	kvAtom.ValueColor = atoms.ColorRef(theming.ColorBrandPrimary)
	kvAtom.Emphasized = true
	kvAtom.ShowDivider = true
	kvAtom.OnClick = ptrAction(actions.NewShowToastAction("tap_order_total", actions.ShowToastPayload{
		Message:    "Price breakdown expanded",
		DurationMs: 1400,
		Variant:    "info",
	}))

	// 12. qr_code
	qrAtom := atoms.NewQrCode("qr_pickup_pass", "abeta://pickup/ORD-84920?sig=v1", "Express Pickup Pass", 160)
	qrAtom.Caption = "Scan at Abeta Fresh locker #14"
	qrAtom.OnClick = ptrAction(actions.NewCopyToClipboardAction("copy_pickup_code", actions.CopyToClipboardPayload{
		Text:  "ORD-84920",
		Label: "Pickup Code",
	}))

	root := snippets.CardNode(
		cardLayout,
		snippets.VStack(
			theming.SpacingMD.Dp(),
			snippets.AlertBannerNode(alertAtom).WithNodeID("node_alert_banner"),
			snippets.HStack(
				theming.SpacingSM.Dp(),
				snippets.PriceNode(priceAtom).WithNodeID("node_price"),
				snippets.BadgeNode(badgeAtom).WithNodeID("node_badge"),
			).WithArrangement(snippets.ComposedAlignSpaceBetween).WithAlignment("", snippets.ComposedAlignCenter),
			snippets.HStack(
				theming.SpacingSM.Dp(),
				snippets.RatingNode(ratingAtom).WithNodeID("node_rating"),
				snippets.ChipNode(chipAtom).WithNodeID("node_chip"),
			).WithArrangement(snippets.ComposedAlignSpaceBetween).WithAlignment("", snippets.ComposedAlignCenter),
			snippets.SegmentedControlNode(segmentedAtom).WithNodeID("node_segmented_control"),
			snippets.RadioGroupNode(radioAtom).WithNodeID("node_radio_group"),
			snippets.SelectNode(selectAtom).WithNodeID("node_select"),
			snippets.OtpInputNode(otpAtom).WithNodeID("node_otp_input"),
			snippets.StatTileNode(statAtom).WithNodeID("node_stat_tile"),
			snippets.KeyValueRowNode(kvAtom).WithNodeID("node_key_value_row"),
			snippets.QrCodeNode(qrAtom).WithNodeID("node_qr_code"),
		),
	).WithNodeID("extended_atoms_root")

	extendedBlock := snippets.NewComposedBlockSnippet("demo_extended_atoms_block", "extended_atoms_showcase", root).
		WithRecipeName("extended_atoms_all_12").
		WithMotion(atoms.MotionSpec{
			Enter:         atoms.MotionTransitionFadeIn,
			DurationToken: atoms.MotionDurationNormal,
		}).
		WithTracking("snippet_impression", map[string]string{
			"snippet_id": "demo_extended_atoms_block",
			"recipe":     "extended_atoms_all_12",
		}).
		Build()

	return snippets.NewPage("extended-atoms-demo", "Abeta Extended Atoms").
		WithToolbar(snippets.ToolbarSpec{
			Title:          &toolbarTitle,
			Subtitle:       &toolbarSub,
			NavigationIcon: &backIcon,
			BgColor:        atoms.ColorRef(theming.ColorSurfacePrimary),
		}).
		WithAdaptiveLayout(snippets.AdaptivePageSpec{
			LayoutMode: snippets.LayoutModeCentered,
			MaxWidthDp: atoms.IntPtr(960),
		}).
		AddSnippets(extendedBlock).
		WithTracking("page_loaded", map[string]string{
			"page_id": "extended-atoms-demo",
			"version": "v1",
		}).
		Build()
}

func TestGoldenExtendedAtomsScreen_RoundTrip(t *testing.T) {
	page := canonicalExtendedAtomsScreen()
	rawBytes := writeOrCompareGolden(t, "extended_atoms_screen.json", page)
	assertStringOnlyTrackingProperties(t, rawBytes)

	var decoded snippets.PageResponse
	if err := json.Unmarshal(rawBytes, &decoded); err != nil {
		t.Fatalf("round-trip unmarshal extended_atoms_screen.json: %v", err)
	}
	if decoded.PageID != "extended-atoms-demo" || len(decoded.Snippets) != 1 {
		t.Fatalf("unexpected decoded extended_atoms screen: %+v", decoded)
	}
	if decoded.Snippets[0].SnippetType != snippets.SnippetTypeComposedBlock {
		t.Fatalf("expected composed_block snippet, got %q", decoded.Snippets[0].SnippetType)
	}

	block, err := snippets.DecodeSnippetData[snippets.ComposedBlockSnippetData](decoded.Snippets[0])
	if err != nil {
		t.Fatalf("decode ComposedBlockSnippetData: %v", err)
	}
	if block.BlockID != "extended_atoms_showcase" || block.RecipeName != "extended_atoms_all_12" {
		t.Fatalf("unexpected block metadata: %+v", block)
	}

	foundKinds := map[string]snippets.ComposedNode{}
	var collect func(n snippets.ComposedNode)
	collect = func(n snippets.ComposedNode) {
		foundKinds[n.Kind] = n
		for _, ch := range n.Children {
			collect(ch)
		}
	}
	collect(block.Root)

	if n, ok := foundKinds[snippets.ComposedNodeRadioGroup]; !ok || n.RadioGroup == nil || n.RadioGroup.FieldID != "delivery_speed" || len(n.RadioGroup.Options) != 3 {
		t.Fatalf("unexpected radio_group node: %+v", n)
	}
	if n, ok := foundKinds[snippets.ComposedNodeSelect]; !ok || n.Select == nil || n.Select.FieldID != "delivery_slot" || len(n.Select.Options) != 2 {
		t.Fatalf("unexpected select node: %+v", n)
	}
	if n, ok := foundKinds[snippets.ComposedNodeOtpInput]; !ok || n.OtpInput == nil || n.OtpInput.FieldID != "checkout_otp" || n.OtpInput.Length != 6 || !n.OtpInput.Masked {
		t.Fatalf("unexpected otp_input node: %+v", n)
	}
	if n, ok := foundKinds[snippets.ComposedNodeChip]; !ok || n.Chip == nil || n.Chip.ID != "chip_organic" || !n.Chip.Selected || !n.Chip.Removable {
		t.Fatalf("unexpected chip node: %+v", n)
	}
	if n, ok := foundKinds[snippets.ComposedNodeSegmentedControl]; !ok || n.SegmentedControl == nil || n.SegmentedControl.FieldID != "billing_cycle" || n.SegmentedControl.SelectedID != "annual" {
		t.Fatalf("unexpected segmented_control node: %+v", n)
	}
	if n, ok := foundKinds[snippets.ComposedNodeRating]; !ok || n.Rating == nil || n.Rating.Value != 4.8 || n.Rating.ReviewCount == nil || *n.Rating.ReviewCount != 1248 {
		t.Fatalf("unexpected rating node: %+v", n)
	}
	if n, ok := foundKinds[snippets.ComposedNodePrice]; !ok || n.Price == nil || n.Price.AmountText != "$3.99" || n.Price.DiscountLabel != "33% OFF" {
		t.Fatalf("unexpected price node: %+v", n)
	}
	if n, ok := foundKinds[snippets.ComposedNodeBadge]; !ok || n.Badge == nil || n.Badge.Count == nil || *n.Badge.Count != 12 || n.Badge.Variant != atoms.VariantError {
		t.Fatalf("unexpected badge node: %+v", n)
	}
	if n, ok := foundKinds[snippets.ComposedNodeAlertBanner]; !ok || n.AlertBanner == nil || n.AlertBanner.ID != "alert_cold_chain" || !n.AlertBanner.Dismissible {
		t.Fatalf("unexpected alert_banner node: %+v", n)
	}
	if n, ok := foundKinds[snippets.ComposedNodeStatTile]; !ok || n.StatTile == nil || n.StatTile.Value != "99.4%" || n.StatTile.DeltaPositive == nil || !*n.StatTile.DeltaPositive {
		t.Fatalf("unexpected stat_tile node: %+v", n)
	}
	if n, ok := foundKinds[snippets.ComposedNodeKeyValueRow]; !ok || n.KeyValueRow == nil || n.KeyValueRow.Value != "$24.90" || !n.KeyValueRow.Emphasized || !n.KeyValueRow.ShowDivider {
		t.Fatalf("unexpected key_value_row node: %+v", n)
	}
	if n, ok := foundKinds[snippets.ComposedNodeQrCode]; !ok || n.QrCode == nil || n.QrCode.Payload != "abeta://pickup/ORD-84920?sig=v1" || n.QrCode.SizeDp != 160 {
		t.Fatalf("unexpected qr_code node: %+v", n)
	}
}
