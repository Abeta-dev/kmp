package molecules_test

import (
	"encoding/json"
	"testing"

	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/molecules"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

func TestExpressProductTile_AllBranchesAndRoundTrip(t *testing.T) {
	inc := actions.NewTrackEventAction("inc_avocado", actions.TrackEventPayload{EventName: "cart_inc"})
	dec := actions.NewTrackEventAction("dec_avocado", actions.TrackEventPayload{EventName: "cart_dec"})
	fav := actions.NewTrackEventAction("fav_avocado", actions.TrackEventPayload{EventName: "fav_toggle"})
	cardClick := actions.NewShowToastAction("tap_avocado", actions.ShowToastPayload{Message: "Avocados"})

	variantAct := molecules.VariantPickerSheetAction(molecules.VariantPickerSpec{
		ActionID:          "open_avocado_variants",
		DialogID:          "avocado_variants_sheet",
		ProductID:         "avocados",
		Title:             "Organic Hass Avocados",
		Subtitle:          "Select pack size",
		ImageURL:          "https://cdn.abeta.dev/fresh/avocados.png",
		SelectedID:        "500g",
		PriceText:         "$3.49",
		OriginalPriceText: "$4.49",
		DiscountLabel:     "22% OFF",
		Options: []atoms.OptionItem{
			{ID: "500g", Label: "500 g", Subtitle: "2 pcs", Badge: "BESTSELLER"},
			{ID: "1kg", Label: "1 kg", Subtitle: "4 pcs", Badge: "SAVE 15%"},
			{ID: "2kg", Label: "2 kg", Subtitle: "8 pcs", Badge: "VALUE PACK"},
		},
		OnSelectChange: &inc,
		PositiveAction: &cardClick,
		SheetScreenID:  "variant-picker-avocados",
	})

	// 1. Rich tile with discount ribbon, favorite icon (top_end), ETA pill (bottom_start), variant chip, rating, OnClick
	richTile := molecules.ExpressProductTile(molecules.ProductTileSpec{
		ProductID:         "avocados",
		Title:             "Organic Hass Avocados",
		Subtitle:          "500 g · Ready to eat",
		ImageURL:          "https://cdn.abeta.dev/fresh/avocados.png",
		PriceText:         "$3.49",
		OriginalPriceText: "$4.49",
		DiscountRibbon:    "22% OFF",
		EtaLabel:          "8 MINS",
		UnitSuffix:        "/ pack",
		Rating:            4.9,
		ReviewCount:       atoms.IntPtr(420),
		Quantity:          1,
		OnIncrement:       &inc,
		OnDecrement:       &dec,
		OnVariantClick:    &variantAct,
		OnFavorite:        &fav,
		OnClick:           &cardClick,
	})

	if richTile.NodeID != "tile_avocados" || richTile.Tracking == nil || richTile.ClickTracking == nil || richTile.OnClick == nil {
		t.Fatalf("unexpected rich tile metadata: %+v", richTile)
	}
	raw, err := json.Marshal(richTile)
	if err != nil {
		t.Fatalf("marshal rich tile: %v", err)
	}
	var decoded snippets.ComposedNode
	if err := json.Unmarshal(raw, &decoded); err != nil {
		t.Fatalf("unmarshal rich tile: %v", err)
	}
	mediaBox := decoded.Children[0].Children[0]
	if mediaBox.Kind != snippets.ComposedNodeBox || len(mediaBox.Children) != 4 {
		t.Fatalf("expected BoxNode with image + 3 corner overlays, got %+v", mediaBox)
	}
	if mediaBox.Children[1].BoxAlignment != snippets.BoxAlignTopStart ||
		mediaBox.Children[2].BoxAlignment != snippets.BoxAlignTopEnd ||
		mediaBox.Children[3].BoxAlignment != snippets.BoxAlignBottomStart {
		t.Fatalf("unexpected corner alignments: %+v", mediaBox.Children)
	}

	// Verify VariantPickerSheetAction payload
	dlgPayload, err := actions.DecodePayload[actions.OpenDialogPayload](variantAct)
	if err != nil || dlgPayload.Presentation != actions.PresentationBottomSheet || len(dlgPayload.Snippets) != 1 || dlgPayload.SheetScreenID != "variant-picker-avocados" {
		t.Fatalf("unexpected variant picker dialog payload: %+v, err=%v", dlgPayload, err)
	}
	if variantAct.Tracking == nil || variantAct.Tracking.EventName != "variant_picker_opened" {
		t.Fatalf("expected tracking on variant picker action, got %+v", variantAct.Tracking)
	}

	// 2. Tile with TopEndBadge (VEG) instead of OnFavorite + explicit StepperFieldID, MaxQuantity, VariantLabel
	vegTile := molecules.ExpressProductTile(molecules.ProductTileSpec{
		ProductID:      "spinach",
		Title:          "Baby Spinach",
		ImageURL:       "https://cdn.abeta.dev/fresh/spinach.png",
		PriceText:      "$2.49",
		TopEndBadge:    "VEG",
		StepperFieldID: "custom_qty_spinach",
		MaxQuantity:    5,
		VariantLabel:   "200 g ▾",
		OnVariantClick: &variantAct,
	})
	vegMedia := vegTile.Children[0].Children[0]
	if len(vegMedia.Children) != 2 || vegMedia.Children[1].BoxAlignment != snippets.BoxAlignTopEnd || vegMedia.Children[1].Tag.Label != "VEG" {
		t.Fatalf("expected TopEndBadge VEG tag, got %+v", vegMedia.Children)
	}
}

func TestCategoryGridAndImpulseUpsellRail(t *testing.T) {
	nav := actions.NewNavigateAction("nav_cat_produce", actions.NavigatePayload{Route: "/screens/fresh-home?cat=produce"})

	catEnv := molecules.CategoryGridSection("fresh_cats", "cat_grid_blk", "Shop by Category", []molecules.CategoryTileSpec{
		{
			CategoryID: "produce",
			Label:      "Fruits & Veg",
			Subtitle:   "Farm fresh",
			ImageURL:   "https://cdn.abeta.dev/cats/produce.png",
			BadgeLabel: "FRESH",
			OnClick:    &nav,
		},
		{
			CategoryID: "dairy",
			Label:      "Dairy & Bread",
			ImageURL:   "https://cdn.abeta.dev/cats/dairy.png",
		},
	})
	if catEnv.SnippetType != snippets.SnippetTypeComposedBlock {
		t.Fatalf("expected composed_block for CategoryGridSection, got %q", catEnv.SnippetType)
	}
	catData, err := snippets.DecodeSnippetData[snippets.ComposedBlockSnippetData](catEnv)
	if err != nil || catData.RecipeName != "express_category_grid" {
		t.Fatalf("unexpected CategoryGridSection data: %+v err=%v", catData, err)
	}
	gridNode := catData.Root.Children[1]
	if gridNode.Kind != snippets.ComposedNodeGrid || *gridNode.ColumnsByWidth.Compact != 3 || *gridNode.ColumnsByWidth.Medium != 4 || *gridNode.ColumnsByWidth.Expanded != 6 || len(gridNode.Children) != 2 {
		t.Fatalf("unexpected CategoryGridSection grid node: %+v", gridNode)
	}

	// ImpulseUpsellRail with default heading fallback
	railEnv := molecules.ImpulseUpsellRail("fresh_upsell", "upsell_blk", "", []molecules.ProductTileSpec{
		{ProductID: "mint", Title: "Fresh Mint", ImageURL: "https://cdn.abeta.dev/fresh/mint.png", PriceText: "$0.99", EtaLabel: "8 MINS"},
		{ProductID: "lime", Title: "Key Limes", ImageURL: "https://cdn.abeta.dev/fresh/lime.png", PriceText: "$1.29", DiscountRibbon: "10% OFF"},
	})
	railData, err := snippets.DecodeSnippetData[snippets.ComposedBlockSnippetData](railEnv)
	if err != nil || railData.RecipeName != "express_impulse_upsell_rail" {
		t.Fatalf("unexpected ImpulseUpsellRail data: %+v err=%v", railData, err)
	}
	if railData.Root.Children[0].Children[0].Text.Text != "Before You Checkout" {
		t.Fatalf("expected default upsell rail title, got %q", railData.Root.Children[0].Children[0].Text.Text)
	}
	scrollNode := railData.Root.Children[1]
	if scrollNode.Kind != snippets.ComposedNodeScrollRow || len(scrollNode.Children) != 2 {
		t.Fatalf("unexpected ImpulseUpsellRail ScrollRowNode: %+v", scrollNode)
	}
}

func TestCheckoutMolecules_DeliveryCouponAndBillSummary(t *testing.T) {
	saveAct := actions.NewSaveStateAction("save_pref", actions.SaveStatePayload{Key: "k", Value: "v"})

	// 1. DeliveryInstructionsCard with defaults + checkbox + tip chips + tip slider
	delivEnv := molecules.DeliveryInstructionsCard("fresh_delivery_card", "deliv_blk", molecules.DeliveryInstructionsSpec{
		LeaveAtDoor:          true,
		OnLeaveAtDoorChange:  &saveAct,
		DontRingBellFieldID:  "dont_ring_bell",
		DontRingBell:         true,
		OnDontRingBellChange: &saveAct,
		NotesInitialValue:    "Leave by lobby concierge",
		TipChips: []molecules.TipChipOption{
			{ID: "1", Label: "$1", OnClick: &saveAct},
			{ID: "2", Label: "$2", Selected: true, OnClick: &saveAct},
			{ID: "5", Label: "$5", OnClick: &saveAct},
		},
		TipSliderFieldID:  "custom_rider_tip",
		TipSliderValue:    2,
		OnTipSliderChange: &saveAct,
	})
	delivData, err := snippets.DecodeSnippetData[snippets.ComposedBlockSnippetData](delivEnv)
	if err != nil || delivData.RecipeName != "express_delivery_instructions" {
		t.Fatalf("unexpected DeliveryInstructionsCard data: %+v err=%v", delivData, err)
	}
	kinds := map[string]bool{}
	var walk func(n snippets.ComposedNode)
	walk = func(n snippets.ComposedNode) {
		kinds[n.Kind] = true
		for _, ch := range n.Children {
			walk(ch)
		}
	}
	walk(delivData.Root)
	for _, wantKind := range []string{
		snippets.ComposedNodeSwitch,
		snippets.ComposedNodeCheckbox,
		snippets.ComposedNodeTextField,
		snippets.ComposedNodeChip,
		snippets.ComposedNodeSlider,
	} {
		if !kinds[wantKind] {
			t.Errorf("DeliveryInstructionsCard missing expected ComposedNodeKind %q", wantKind)
		}
	}

	// 2. CouponApplyCard with defaults + quick chips + applied code banner
	couponEnv := molecules.CouponApplyCard("fresh_coupon_card", "coupon_blk", molecules.CouponApplySpec{
		InitialCode: "FRESH20",
		OnApply:     &saveAct,
		QuickCoupons: []molecules.CouponChipSpec{
			{Code: "FRESH20", Selected: true, OnClick: &saveAct},
			{Code: "FREEDEL", Label: "Free Delivery", OnClick: &saveAct},
		},
		AppliedCode: "FRESH20",
	})
	couponData, err := snippets.DecodeSnippetData[snippets.ComposedBlockSnippetData](couponEnv)
	if err != nil || couponData.RecipeName != "express_coupon_apply_card" {
		t.Fatalf("unexpected CouponApplyCard data: %+v err=%v", couponData, err)
	}

	// 3. CheckoutBillSummary with defaults + savings badge + line items + savings callout
	billEnv := molecules.CheckoutBillSummary("fresh_bill_summary", "bill_blk", molecules.CheckoutBillSpec{
		SavingsBadge: "SAVED $4.50",
		Items: []molecules.BillLineItem{
			{ID: "subtotal", Label: "Items Total", Value: "$18.48", Sublabel: "3 items"},
			{ID: "delivery", Label: "Delivery Charge", Value: "FREE", ValueColor: atoms.ColorRef(theming.ColorSuccess)},
			{ID: "discount", Label: "FRESH20 Discount", Value: "-$2.50", ValueColor: atoms.ColorRef(theming.ColorSuccess)},
		},
		GrandTotalValue:    "$15.98",
		GrandTotalSublabel: "Inclusive of all taxes",
		SavingsCallout:     "You saved $4.50 on this 8-minute express order!",
	})
	billData, err := snippets.DecodeSnippetData[snippets.ComposedBlockSnippetData](billEnv)
	if err != nil || billData.RecipeName != "express_checkout_bill_summary" {
		t.Fatalf("unexpected CheckoutBillSummary data: %+v err=%v", billData, err)
	}
}
