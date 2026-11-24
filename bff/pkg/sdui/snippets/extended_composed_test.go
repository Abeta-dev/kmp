package snippets_test

import (
	"encoding/json"
	"testing"

	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

func TestExtendedComposedNodeKinds_WireParityAndRoundTrip(t *testing.T) {
	expectedKinds := map[string]string{
		"radio_group":       snippets.ComposedNodeRadioGroup,
		"select":            snippets.ComposedNodeSelect,
		"otp_input":         snippets.ComposedNodeOtpInput,
		"chip":              snippets.ComposedNodeChip,
		"segmented_control": snippets.ComposedNodeSegmentedControl,
		"rating":            snippets.ComposedNodeRating,
		"price":             snippets.ComposedNodePrice,
		"badge":             snippets.ComposedNodeBadge,
		"alert_banner":      snippets.ComposedNodeAlertBanner,
		"stat_tile":         snippets.ComposedNodeStatTile,
		"key_value_row":     snippets.ComposedNodeKeyValueRow,
		"qr_code":           snippets.ComposedNodeQrCode,
	}
	for want, got := range expectedKinds {
		if got != want {
			t.Errorf("ComposedNodeKind constant = %q, want %q", got, want)
		}
	}

	root := snippets.VStack(
		theming.SpacingMD.Dp(),
		snippets.RadioGroupNode(atoms.NewRadioGroup("rg", "Speed", "fast", atoms.OptionItem{ID: "fast", Label: "Fast"})),
		snippets.SelectNode(atoms.NewSelect("sel", "Slot", "Choose", "now", atoms.OptionItem{ID: "now", Label: "Now"})),
		snippets.OtpInputNode(atoms.NewOtpInput("otp", "PIN", 6, nil)),
		snippets.ChipNode(atoms.NewChip("ch", "Organic", true, nil)),
		snippets.SegmentedControlNode(atoms.NewSegmentedControl("seg", "monthly", atoms.OptionItem{ID: "monthly", Label: "Monthly"})),
		snippets.RatingNode(atoms.NewRating("rt", 4.7, 5)),
		snippets.PriceNode(atoms.NewPrice("pr", "$4.99", "$6.99")),
		snippets.BadgeNode(atoms.NewBadge("bd", 5, 99, atoms.VariantError)),
		snippets.AlertBannerNode(atoms.NewAlertBanner("ab", "Notice", "Chilled pack active", atoms.VariantInfo)),
		snippets.StatTileNode(atoms.NewStatTile("st", "On-Time", "99.4%")),
		snippets.KeyValueRowNode(atoms.NewKeyValueRow("kv", "Total", "$19.50")),
		snippets.QrCodeNode(atoms.NewQrCode("qr", "abeta://pass/1", "Pass", 144)),
	)

	env := snippets.NewComposedBlockSnippet("ext_block_1", "extended_atoms_tree", root).
		WithRecipeName("extended_atoms_showcase").
		Build()

	raw, err := json.Marshal(env)
	if err != nil {
		t.Fatalf("marshal extended composed_block: %v", err)
	}

	var decodedEnv snippets.SnippetEnvelope
	if err := json.Unmarshal(raw, &decodedEnv); err != nil {
		t.Fatalf("unmarshal extended composed_block: %v", err)
	}

	data, err := snippets.DecodeSnippetData[snippets.ComposedBlockSnippetData](decodedEnv)
	if err != nil {
		t.Fatalf("decode ComposedBlockSnippetData: %v", err)
	}
	if len(data.Root.Children) != 12 {
		t.Fatalf("expected 12 extended atom children, got %d", len(data.Root.Children))
	}

	checks := []struct {
		idx     int
		kind    string
		present bool
	}{
		{0, snippets.ComposedNodeRadioGroup, data.Root.Children[0].RadioGroup != nil},
		{1, snippets.ComposedNodeSelect, data.Root.Children[1].Select != nil},
		{2, snippets.ComposedNodeOtpInput, data.Root.Children[2].OtpInput != nil},
		{3, snippets.ComposedNodeChip, data.Root.Children[3].Chip != nil},
		{4, snippets.ComposedNodeSegmentedControl, data.Root.Children[4].SegmentedControl != nil},
		{5, snippets.ComposedNodeRating, data.Root.Children[5].Rating != nil},
		{6, snippets.ComposedNodePrice, data.Root.Children[6].Price != nil},
		{7, snippets.ComposedNodeBadge, data.Root.Children[7].Badge != nil},
		{8, snippets.ComposedNodeAlertBanner, data.Root.Children[8].AlertBanner != nil},
		{9, snippets.ComposedNodeStatTile, data.Root.Children[9].StatTile != nil},
		{10, snippets.ComposedNodeKeyValueRow, data.Root.Children[10].KeyValueRow != nil},
		{11, snippets.ComposedNodeQrCode, data.Root.Children[11].QrCode != nil},
	}
	for _, tc := range checks {
		gotKind := data.Root.Children[tc.idx].Kind
		if gotKind != tc.kind || !tc.present {
			t.Errorf("child[%d]: kind=%q (want %q), payloadPresent=%v", tc.idx, gotKind, tc.kind, tc.present)
		}
	}
}

func TestExpressComposedNodeKinds_StickyAndTrackingRoundTrip(t *testing.T) {
	expectedNewKinds := map[string]string{
		"scroll_row":  snippets.ComposedNodeScrollRow,
		"grid":        snippets.ComposedNodeGrid,
		"text_field":  snippets.ComposedNodeTextField,
		"switch":      snippets.ComposedNodeSwitch,
		"checkbox":    snippets.ComposedNodeCheckbox,
		"slider":      snippets.ComposedNodeSlider,
		"media_pager": snippets.ComposedNodeMediaPager,
	}
	for want, got := range expectedNewKinds {
		if got != want {
			t.Errorf("ComposedNodeKind constant = %q, want %q", got, want)
		}
	}

	expectedBoxAligns := map[string]string{
		"top_start":     snippets.BoxAlignTopStart,
		"top_center":    snippets.BoxAlignTopCenter,
		"top_end":       snippets.BoxAlignTopEnd,
		"center_start":  snippets.BoxAlignCenterStart,
		"center":        snippets.BoxAlignCenter,
		"center_end":    snippets.BoxAlignCenterEnd,
		"bottom_start":  snippets.BoxAlignBottomStart,
		"bottom_center": snippets.BoxAlignBottomCenter,
		"bottom_end":    snippets.BoxAlignBottomEnd,
	}
	for want, got := range expectedBoxAligns {
		if got != want {
			t.Errorf("BoxAlign constant = %q, want %q", got, want)
		}
	}

	// Verify Sticky flag on SnippetEnvelope and all builders that expose WithSticky
	stickyHdr := snippets.NewHeaderSnippet("sh", atoms.NewText("H", theming.TypographyTitleSmall, theming.ColorTextPrimary)).WithSticky(true).Build()
	stickySearch := snippets.NewSearchBarSnippet("ss", "s1", "Search").WithSticky(true).Build()
	stickyChips := snippets.NewChipGroupSnippet("sc", "g1").WithSticky(true).Build()
	stickyNudge := snippets.NewNudgeBarSnippet("sn", "n1", atoms.NewText("N", theming.TypographyBodySmall, theming.ColorTextPrimary)).WithSticky(true).Build()
	stickyEnv := snippets.NewSpacerSnippet("sp", atoms.SpacerAtom{HeightDp: atoms.IntPtr(4)}).Build().WithSticky(true)
	for _, env := range []snippets.SnippetEnvelope{stickyHdr, stickySearch, stickyChips, stickyNudge, stickyEnv} {
		if !env.Sticky {
			t.Errorf("expected Sticky=true on snippet %q", env.SnippetID)
		}
	}

	// Verify 7 new ComposedNodeKinds + BoxAlignment + ColumnsByWidth + Tracking + ClickTracking + empty container cloning
	pagerImgs := []atoms.ImageAtom{{ID: "p1", URL: "https://cdn.abeta.dev/p1.png"}}
	pagerAtom := atoms.NewMediaPager("mp_1", pagerImgs, 160)
	trackProps := map[string]any{"molecule": "tile"}
	clickProps := map[string]any{"cta": "add"}

	root := snippets.VStack(
		8,
		snippets.BoxNode(
			snippets.ImageNode(atoms.ImageAtom{ID: "i1", URL: "https://cdn.abeta.dev/i1.png"}),
			snippets.TagNode(atoms.NewTag("15% OFF", theming.ColorErrorContainer)).WithBoxAlignment(snippets.BoxAlignTopStart),
		).WithTracking("node_impression", trackProps).WithClickTracking("interaction_click", clickProps),
		snippets.ScrollRowNode(8, snippets.TextNode(atoms.NewText("Upsell", theming.TypographyBodySmall, theming.ColorTextPrimary))),
		snippets.GridNode(3, 8, snippets.TextNode(atoms.NewText("Cat", theming.TypographyLabelSmall, theming.ColorTextPrimary))).
			WithColumns(4).
			WithColumnsByWidth(snippets.ResponsiveColumnsSpec{
				Compact:  atoms.IntPtr(3),
				Medium:   atoms.IntPtr(4),
				Expanded: atoms.IntPtr(6),
			}),
		snippets.TextFieldNode(atoms.TextFieldAtom{FieldID: "note", Label: "Delivery Note"}),
		snippets.SwitchNode(atoms.NewSwitch("leave_door", "Leave at door", true, nil)),
		snippets.CheckboxNode(atoms.NewCheckbox("no_bell", "Don't ring bell", false, nil)),
		snippets.SliderNode(atoms.NewSlider("tip", "Tip", 2, 0, 10, nil)),
		snippets.MediaPagerNode(pagerAtom),
		snippets.ScrollRowNode(0),
		snippets.GridNode(0, 0),
	)
	trackProps["molecule"] = "mutated"

	env := snippets.NewComposedBlockSnippet("blk_express", "express_36_kinds", root).
		WithSticky(true).
		Build()
	raw, err := json.Marshal(env)
	if err != nil {
		t.Fatalf("marshal express composed_block: %v", err)
	}
	var decodedEnv snippets.SnippetEnvelope
	if err := json.Unmarshal(raw, &decodedEnv); err != nil {
		t.Fatalf("unmarshal express composed_block: %v", err)
	}
	if !decodedEnv.Sticky {
		t.Fatal("expected Sticky=true to survive JSON round-trip")
	}
	data, err := snippets.DecodeSnippetData[snippets.ComposedBlockSnippetData](decodedEnv)
	if err != nil {
		t.Fatalf("decode express composed_block: %v", err)
	}
	if data.Root.Children[0].Tracking == nil || data.Root.Children[0].Tracking.Properties["molecule"] != "tile" {
		t.Fatalf("expected defensive copy of Tracking props, got %+v", data.Root.Children[0].Tracking)
	}
	if data.Root.Children[0].ClickTracking == nil || data.Root.Children[0].ClickTracking.EventName != "interaction_click" {
		t.Fatalf("expected ClickTracking preserved, got %+v", data.Root.Children[0].ClickTracking)
	}
	if data.Root.Children[0].Children[1].BoxAlignment != snippets.BoxAlignTopStart {
		t.Fatalf("expected box_alignment=%q, got %q", snippets.BoxAlignTopStart, data.Root.Children[0].Children[1].BoxAlignment)
	}
	gridNode := data.Root.Children[2]
	if gridNode.Kind != snippets.ComposedNodeGrid || *gridNode.Columns != 4 || gridNode.ColumnsByWidth == nil || *gridNode.ColumnsByWidth.Expanded != 6 {
		t.Fatalf("unexpected GridNode decode: %+v", gridNode)
	}
	if data.Root.Children[3].TextField == nil || data.Root.Children[4].Switch == nil || data.Root.Children[5].Checkbox == nil || data.Root.Children[6].Slider == nil || data.Root.Children[7].MediaPager == nil {
		t.Fatalf("expected all 5 new leaf atoms present in decoded ComposedNode tree")
	}
	if data.Root.Children[8].Kind != snippets.ComposedNodeScrollRow || data.Root.Children[9].Kind != snippets.ComposedNodeGrid {
		t.Fatalf("expected empty scroll_row and grid containers preserved")
	}
}
