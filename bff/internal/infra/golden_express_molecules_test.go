package infra_test

import (
	"encoding/json"
	"testing"

	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/molecules"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

func canonicalExpressMoleculesAndAnalyticsScreen() snippets.PageResponse {
	ptrAction := func(a actions.ActionTrigger) *actions.ActionTrigger { return &a }

	toolbarTitle := atoms.NewText("Abeta Fresh Express", theming.TypographyTitleLarge, theming.ColorTextPrimary)
	toolbarSub := atoms.NewText("8 mins · Atomic molecules & component analytics", theming.TypographyBodySmall, theming.ColorTextSecondary)
	backIcon := atoms.NewIcon("arrow_back", 22, theming.ColorTextPrimary)
	backIcon.ContentDescription = "Navigate back"
	backIcon.OnClick = ptrAction(actions.NewBackPressAction("back_from_express_molecules", actions.BackPressPayload{Steps: 1}))

	// 1. Sticky search bar
	stickySearch := snippets.NewSearchBarSnippet("express_sticky_search", "express_search", "Search milk, avocados, snacks...").
		WithSticky(true).
		WithLeadingIcon(atoms.NewIcon("search", 20, theming.ColorTextSecondary)).
		WithSubmitLabel("Search").
		WithOnSubmit(actions.NewNavigateAction("submit_express_search", actions.NavigatePayload{
			Route: "/screens/fresh-home?q={{query}}",
		}).WithTracking("search_submitted", map[string]string{
			"surface": "express-molecules-demo",
		})).
		WithLayout(atoms.LayoutConfig{
			Padding:      atoms.SymmetricSpacing(theming.SpacingMD.Dp(), theming.SpacingXS.Dp()),
			Margin:       atoms.SymmetricSpacing(theming.SpacingLG.Dp(), theming.SpacingXS.Dp()),
			BgColor:      atoms.ColorRef(theming.ColorSurfacePrimary),
			CornerRadius: atoms.UniformRadius(theming.RadiusMD.Dp()),
			FillMaxWidth: atoms.BoolPtr(true),
		}).
		WithTracking("snippet_impression", map[string]any{
			"snippet_id": "express_sticky_search",
			"sticky":     "true",
		}).
		Build()

	// 2. Hero MediaPagerAtom (29th wire atom) inside composed_block
	heroPager := atoms.NewMediaPager("express_hero_pager", []atoms.ImageAtom{
		{
			ID:                 "hero_img_1",
			URL:                "https://cdn.abeta.dev/fresh/hero-harvest.png",
			ContentDescription: "Morning Harvest 25% OFF",
			ScaleMode:          "crop",
			CornerRadius:       atoms.UniformRadius(theming.RadiusMD.Dp()),
		},
		{
			ID:                 "hero_img_2",
			URL:                "https://cdn.abeta.dev/fresh/hero-dairy.png",
			ContentDescription: "Chilled Dairy in 8 Mins",
			ScaleMode:          "crop",
			CornerRadius:       atoms.UniformRadius(theming.RadiusMD.Dp()),
		},
	}, 168)
	heroPager.AutoAdvanceMs = atoms.IntPtr(4000)
	heroPager.CornerRadius = atoms.UniformRadius(theming.RadiusMD.Dp())
	heroPager.OnPageClick = ptrAction(actions.NewNavigateAction("tap_express_hero_pager", actions.NavigatePayload{
		Route: "/screens/fresh-home",
	}).WithTracking("hero_banner_clicked", map[string]string{
		"surface": "express-molecules-demo",
	}))

	heroPagerBlock := snippets.NewComposedBlockSnippet(
		"express_hero_pager_block",
		"express_hero_media",
		snippets.MediaPagerNode(heroPager).
			WithNodeID("node_express_hero_pager").
			WithTracking("node_impression", map[string]any{
				"component_id": "node_express_hero_pager",
				"molecule":     "hero_media_pager",
			}),
	).
		WithRecipeName("express_hero_media_pager").
		WithTracking("snippet_impression", map[string]any{
			"snippet_id": "express_hero_pager_block",
		}).
		Build()

	// 3. CategoryGridSection (molded GridNode with columns_by_width 3/4/6)
	catTap := actions.NewShowToastAction("tap_cat_produce", actions.ShowToastPayload{
		Message:    "Opened Fruits & Veg aisle",
		DurationMs: 1400,
		Variant:    "info",
	}).WithTracking("category_tapped", map[string]string{"category_id": "produce"})

	categoryGrid := molecules.CategoryGridSection("express_category_grid", "express_cats", "Shop by Category", []molecules.CategoryTileSpec{
		{CategoryID: "produce", Label: "Fruits & Veg", Subtitle: "Farm fresh", ImageURL: "https://cdn.abeta.dev/fresh/cat-produce.png", BadgeLabel: "FRESH", OnClick: &catTap},
		{CategoryID: "dairy", Label: "Dairy & Eggs", ImageURL: "https://cdn.abeta.dev/fresh/cat-dairy.png"},
		{CategoryID: "bakery", Label: "Bakery", ImageURL: "https://cdn.abeta.dev/fresh/cat-bakery.png", BadgeLabel: "WARM"},
	})

	// 4. ExpressProductTile grid with box_alignment corner ribbons, Tracking/ClickTracking & VariantPickerSheetAction
	incAct := actions.NewTriggerFeedbackAction("inc_express_avocado", actions.TriggerFeedbackPayload{
		Effect:   actions.FeedbackCartBounce,
		TargetID: "floating_cart_dock",
	}).WithTracking("cart_item_incremented", map[string]string{"product_id": "avocados"})
	decAct := actions.NewTrackEventAction("dec_express_avocado", actions.TrackEventPayload{
		EventName:  "cart_item_decremented",
		Properties: map[string]any{"product_id": "avocados"},
	})
	confirmVariant := actions.NewDismissOverlayAction("confirm_avocado_variant", actions.DismissOverlayPayload{
		OverlayID: "avocado_variant_sheet",
	}).WithTracking("variant_confirmed", map[string]string{"product_id": "avocados"})
	selectVariant := actions.NewSaveStateAction("select_avocado_pack", actions.SaveStatePayload{
		Key:   "variant_avocados",
		Value: "500g",
		Scope: "session",
	})

	variantSheetAct := molecules.VariantPickerSheetAction(molecules.VariantPickerSpec{
		ActionID:          "open_avocado_variant_sheet",
		DialogID:          "avocado_variant_sheet",
		ProductID:         "avocados",
		Title:             "Organic Hass Avocados",
		Subtitle:          "Choose pack size",
		ImageURL:          "https://cdn.abeta.dev/fresh/avocados.png",
		SelectedID:        "500g",
		PriceText:         "$3.49",
		OriginalPriceText: "$4.49",
		DiscountLabel:     "22% OFF",
		Options: []atoms.OptionItem{
			{ID: "500g", Label: "500 g", Subtitle: "2 pcs", Badge: "POPULAR"},
			{ID: "1kg", Label: "1 kg", Subtitle: "4 pcs", Badge: "SAVE 15%"},
			{ID: "2kg", Label: "2 kg", Subtitle: "8 pcs", Badge: "BEST VALUE"},
		},
		OnSelectChange: &selectVariant,
		PositiveAction: &confirmVariant,
		SheetScreenID:  "variant-picker-avocados",
	})

	productGridRoot := snippets.GridNode(
		2,
		theming.SpacingSM.Dp(),
		molecules.ExpressProductTile(molecules.ProductTileSpec{
			ProductID:         "avocados",
			Title:             "Organic Hass Avocados",
			Subtitle:          "500 g · Ready to eat",
			ImageURL:          "https://cdn.abeta.dev/fresh/avocados.png",
			PriceText:         "$3.49",
			OriginalPriceText: "$4.49",
			DiscountRibbon:    "22% OFF",
			TopEndBadge:       "VEG",
			EtaLabel:          "8 MINS",
			Rating:            4.9,
			ReviewCount:       atoms.IntPtr(540),
			Quantity:          1,
			OnIncrement:       &incAct,
			OnDecrement:       &decAct,
			VariantLabel:      "500 g ▾",
			OnVariantClick:    &variantSheetAct,
		}),
		molecules.ExpressProductTile(molecules.ProductTileSpec{
			ProductID:         "sourdough",
			Title:             "Artisan Sourdough",
			Subtitle:          "450 g · Stone-baked",
			ImageURL:          "https://cdn.abeta.dev/fresh/sourdough.png",
			PriceText:         "$4.99",
			OriginalPriceText: "$5.79",
			DiscountRibbon:    "14% OFF",
			TopEndBadge:       "VEG",
			EtaLabel:          "8 MINS",
			Quantity:          0,
			OnIncrement:       &incAct,
			OnDecrement:       &decAct,
		}),
	).
		WithNodeID("express_products_grid").
		WithColumnsByWidth(snippets.ResponsiveColumnsSpec{
			Compact:  atoms.IntPtr(2),
			Medium:   atoms.IntPtr(3),
			Expanded: atoms.IntPtr(4),
		})

	productGridSnippet := snippets.NewComposedBlockSnippet("express_product_grid", "express_products", productGridRoot).
		WithRecipeName("express_product_grid").
		WithTracking("snippet_impression", map[string]any{
			"snippet_id": "express_product_grid",
		}).
		Build()

	// 5. ImpulseUpsellRail (molded ScrollRowNode)
	upsellRail := molecules.ImpulseUpsellRail("express_upsell_rail", "express_upsell", "Before You Checkout", []molecules.ProductTileSpec{
		{ProductID: "yuzu", Title: "Sparkling Yuzu", Subtitle: "330 ml", ImageURL: "https://cdn.abeta.dev/fresh/yuzu.png", PriceText: "$1.99", DiscountRibbon: "20% OFF", EtaLabel: "8 MINS"},
		{ProductID: "chips", Title: "Kettle Sea Salt Chips", Subtitle: "140 g", ImageURL: "https://cdn.abeta.dev/fresh/chips.png", PriceText: "$2.99", TopEndBadge: "VEG", EtaLabel: "8 MINS"},
	})

	// 6. DeliveryInstructionsCard (switch, checkbox, text_field, chip, slider inside composed_block)
	savePref := actions.NewSaveStateAction("save_express_delivery_pref", actions.SaveStatePayload{
		Key:   "leave_at_door",
		Value: true,
		Scope: "session",
	})
	deliveryCard := molecules.DeliveryInstructionsCard("express_delivery_card", "express_delivery", molecules.DeliveryInstructionsSpec{
		LeaveAtDoor:          true,
		OnLeaveAtDoorChange:  &savePref,
		DontRingBellFieldID:  "dont_ring_bell",
		DontRingBell:         true,
		OnDontRingBellChange: &savePref,
		NotesInitialValue:    "Leave with lobby concierge",
		TipChips: []molecules.TipChipOption{
			{ID: "1", Label: "$1", OnClick: &savePref},
			{ID: "2", Label: "$2", Selected: true, OnClick: &savePref},
			{ID: "5", Label: "$5", OnClick: &savePref},
		},
		TipSliderFieldID:  "custom_rider_tip",
		TipSliderValue:    2,
		TipSliderMax:      10,
		OnTipSliderChange: &savePref,
	})

	// 7. CouponApplyCard & CheckoutBillSummary
	applyCoupon := actions.NewShowToastAction("apply_express_coupon", actions.ShowToastPayload{
		Message:    "FRESH20 applied!",
		DurationMs: 1600,
		Variant:    "success",
	}).WithTracking("coupon_applied", map[string]string{"code": "FRESH20"})
	couponCard := molecules.CouponApplyCard("express_coupon_card", "express_coupon", molecules.CouponApplySpec{
		InitialCode: "FRESH20",
		OnApply:     &applyCoupon,
		QuickCoupons: []molecules.CouponChipSpec{
			{Code: "FRESH20", Selected: true, OnClick: &applyCoupon},
			{Code: "FREEDEL", Label: "FREEDEL", OnClick: &applyCoupon},
		},
		AppliedCode:        "FRESH20",
		AppliedSavingsText: "Saved $2.50 on your express basket",
	})

	billSummary := molecules.CheckoutBillSummary("express_bill_summary", "express_bill", molecules.CheckoutBillSpec{
		SavingsBadge: "SAVED $4.30",
		Items: []molecules.BillLineItem{
			{ID: "items", Label: "Items Total", Value: "$8.48", Sublabel: "2 items"},
			{ID: "delivery", Label: "Delivery Fee", Value: "FREE", ValueColor: atoms.ColorRef(theming.ColorSuccess)},
			{ID: "coupon", Label: "FRESH20 Discount", Value: "-$2.50", ValueColor: atoms.ColorRef(theming.ColorSuccess)},
		},
		GrandTotalValue:    "$5.98",
		GrandTotalSublabel: "Incl. taxes · 8-min guarantee",
		SavingsCallout:     "You saved $4.30 on this express order!",
	})

	page := snippets.NewPage("express-molecules-demo", "Abeta Fresh Express").
		WithToolbar(snippets.ToolbarSpec{
			Title:          &toolbarTitle,
			Subtitle:       &toolbarSub,
			NavigationIcon: &backIcon,
			BgColor:        atoms.ColorRef(theming.ColorSurfacePrimary),
		}).
		WithAdaptiveLayout(snippets.AdaptivePageSpec{
			LayoutMode: snippets.LayoutModeCentered,
			MaxWidthDp: atoms.IntPtr(1120),
		}).
		AddSnippets(
			stickySearch,
			heroPagerBlock,
			categoryGrid,
			productGridSnippet,
			upsellRail,
			deliveryCard,
			couponCard,
			billSummary,
		).
		WithTracking("page_open", map[string]any{
			"page_id": "express-molecules-demo",
			"version": "v1",
		}).
		Build()

	snippets.EnsureComponentIDs(&page)
	return page
}

func TestGoldenExpressMoleculesAndAnalyticsScreen_RoundTrip(t *testing.T) {
	page := canonicalExpressMoleculesAndAnalyticsScreen()
	rawBytes := writeOrCompareGolden(t, "express_molecules_and_analytics_screen.json", page)
	assertStringOnlyTrackingProperties(t, rawBytes)

	var decoded snippets.PageResponse
	if err := json.Unmarshal(rawBytes, &decoded); err != nil {
		t.Fatalf("round-trip unmarshal express_molecules_and_analytics_screen.json: %v", err)
	}
	if decoded.PageID != "express-molecules-demo" || len(decoded.Snippets) != 8 {
		t.Fatalf("unexpected decoded express molecules screen: %+v", decoded)
	}
	if !decoded.Snippets[0].Sticky || decoded.Snippets[0].SnippetType != snippets.SnippetTypeSearchBar {
		t.Fatalf("expected sticky search_bar as snippet[0], got %+v", decoded.Snippets[0])
	}

	// Collect every ComposedNodeKind and verify every node has non-empty node_id
	seenKinds := map[string]bool{}
	seenBoxAligns := map[string]bool{}
	foundVariantSheet := false

	var walkNode func(n snippets.ComposedNode)
	walkNode = func(n snippets.ComposedNode) {
		if n.NodeID == "" {
			t.Errorf("composed node kind=%q missing node_id", n.Kind)
		}
		seenKinds[n.Kind] = true
		if n.BoxAlignment != "" {
			seenBoxAligns[n.BoxAlignment] = true
		}
		if n.Chip != nil && n.Chip.OnClick != nil && n.Chip.OnClick.Type == actions.ActionOpenDialog {
			dlg, err := actions.DecodePayload[actions.OpenDialogPayload](*n.Chip.OnClick)
			if err == nil && dlg.Presentation == actions.PresentationBottomSheet && len(dlg.Snippets) == 1 && n.Chip.OnClick.Tracking != nil {
				foundVariantSheet = true
			}
		}
		for _, ch := range n.Children {
			walkNode(ch)
		}
	}

	for _, env := range decoded.Snippets[1:] {
		if env.SnippetID == "" {
			t.Fatalf("snippet missing snippet_id: %+v", env)
		}
		blk, err := snippets.DecodeSnippetData[snippets.ComposedBlockSnippetData](env)
		if err != nil {
			t.Fatalf("decode composed_block %q: %v", env.SnippetID, err)
		}
		walkNode(blk.Root)
	}

	for _, wantKind := range []string{
		snippets.ComposedNodeMediaPager,
		snippets.ComposedNodeGrid,
		snippets.ComposedNodeScrollRow,
		snippets.ComposedNodeBox,
		snippets.ComposedNodeCard,
		snippets.ComposedNodeSwitch,
		snippets.ComposedNodeCheckbox,
		snippets.ComposedNodeTextField,
		snippets.ComposedNodeSlider,
		snippets.ComposedNodeChip,
		snippets.ComposedNodePrice,
		snippets.ComposedNodeStepper,
		snippets.ComposedNodeKeyValueRow,
		snippets.ComposedNodeAlertBanner,
	} {
		if !seenKinds[wantKind] {
			t.Errorf("golden fixture #9 missing expected ComposedNodeKind %q", wantKind)
		}
	}
	for _, wantAlign := range []string{
		snippets.BoxAlignTopStart,
		snippets.BoxAlignTopEnd,
		snippets.BoxAlignBottomStart,
	} {
		if !seenBoxAligns[wantAlign] {
			t.Errorf("golden fixture #9 missing expected BoxAlignment %q", wantAlign)
		}
	}
	if !foundVariantSheet {
		t.Fatal("golden fixture #9 missing VariantPickerSheetAction open_dialog with snippets[] and tracking")
	}
}
