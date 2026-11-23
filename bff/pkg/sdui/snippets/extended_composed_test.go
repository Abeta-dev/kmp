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
