package infra

import (
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/molecules"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

func buildFreshHeroMediaPager() snippets.SnippetEnvelope {
	bannerTap := actions.NewNavigateAction("fresh_hero_banner_tap", actions.NavigatePayload{
		Route:  "/screens/store-home",
		Params: map[string]string{"campaign": "morning_harvest"},
	}).WithTracking("hero_banner_clicked", map[string]string{"surface": "fresh-home"})

	pager := atoms.NewMediaPager("fresh_hero_pager", []atoms.ImageAtom{
		{
			ID:                 "fresh_hero_slide_1",
			URL:                "https://cdn.abeta.dev/fresh/hero-harvest.png",
			ContentDescription: "Morning Harvest 25% OFF",
			ScaleMode:          "crop",
			CornerRadius:       atoms.UniformRadius(theming.RadiusMD.Dp()),
		},
		{
			ID:                 "fresh_hero_slide_2",
			URL:                "https://cdn.abeta.dev/fresh/hero-dairy.png",
			ContentDescription: "Chilled A2 Dairy in 8 Mins",
			ScaleMode:          "crop",
			CornerRadius:       atoms.UniformRadius(theming.RadiusMD.Dp()),
		},
		{
			ID:                 "fresh_hero_slide_3",
			URL:                "https://cdn.abeta.dev/fresh/hero-bakery.png",
			ContentDescription: "Warm Artisan Sourdough",
			ScaleMode:          "crop",
			CornerRadius:       atoms.UniformRadius(theming.RadiusMD.Dp()),
		},
	}, 168)
	pager.AutoAdvanceMs = atoms.IntPtr(4500)
	pager.CornerRadius = atoms.UniformRadius(theming.RadiusMD.Dp())
	pager.OnPageClick = &bannerTap

	root := snippets.MediaPagerNode(pager).
		WithNodeID("fresh_hero_pager_node").
		WithLayout(atoms.LayoutConfig{
			Padding:      atoms.SymmetricSpacing(theming.SpacingLG.Dp(), theming.SpacingXS.Dp()),
			FillMaxWidth: atoms.BoolPtr(true),
		}).
		WithTracking("node_impression", map[string]any{
			"component_id": "fresh_hero_pager_node",
			"surface":      "fresh-home",
		})

	return snippets.NewComposedBlockSnippet("fresh_hero_media_block", "fresh_hero_media", root).
		WithRecipeName("express_hero_media_pager").
		WithTracking("snippet_impression", map[string]any{
			"snippet_id": "fresh_hero_media_block",
			"surface":    "fresh-home",
		}).
		Build()
}

func buildFreshExpressProductGrid() snippets.SnippetEnvelope {
	incAvocado := actions.NewTriggerFeedbackAction("inc_fresh_avocado", actions.TriggerFeedbackPayload{
		Effect:   actions.FeedbackCartBounce,
		TargetID: "floating_cart_dock",
	}).WithTracking("cart_item_incremented", map[string]string{"product_id": "avocados", "surface": "fresh-home"})

	decAvocado := actions.NewTrackEventAction("dec_fresh_avocado", actions.TrackEventPayload{
		EventName:  "cart_item_decremented",
		Properties: map[string]any{"product_id": "avocados"},
	})

	selectVariant := actions.NewSaveStateAction("save_avocado_pack", actions.SaveStatePayload{
		Key:   "variant_avocados",
		Value: "500g",
		Scope: "session",
	})
	confirmVariant := actions.NewDismissOverlayAction("confirm_avocado_pack", actions.DismissOverlayPayload{
		OverlayID: "avocado_pack_sheet",
	}).WithTracking("variant_confirmed", map[string]string{"product_id": "avocados"})

	variantSheet := molecules.VariantPickerSheetAction(molecules.VariantPickerSpec{
		ActionID:          "open_fresh_avocado_variants",
		DialogID:          "avocado_pack_sheet",
		ProductID:         "avocados",
		Title:             "Organic Hass Avocados",
		Subtitle:          "8-min chilled delivery · Pick pack weight",
		ImageURL:          "https://cdn.abeta.dev/fresh/avocados.png",
		SelectedID:        "500g",
		PriceText:         "$3.49",
		OriginalPriceText: "$4.49",
		DiscountLabel:     "22% OFF",
		Options: []atoms.OptionItem{
			{ID: "500g", Label: "500 g", Subtitle: "2 pcs · $3.49", Badge: "POPULAR"},
			{ID: "1kg", Label: "1 kg", Subtitle: "4 pcs · $6.29", Badge: "SAVE 15%"},
			{ID: "2kg", Label: "2 kg", Subtitle: "8 pcs · $11.49", Badge: "BEST VALUE"},
		},
		OnSelectChange: &selectVariant,
		PositiveAction: &confirmVariant,
		SheetScreenID:  "variant-picker-avocados",
	})

	favAvocado := actions.NewShowToastAction("fav_fresh_avocado", actions.ShowToastPayload{
		Message:    "Saved Organic Hass Avocados to favorites",
		DurationMs: 1600,
		Variant:    "success",
	}).WithTracking("product_favorited", map[string]string{"product_id": "avocados"})

	tiles := []snippets.ComposedNode{
		molecules.ExpressProductTile(molecules.ProductTileSpec{
			ProductID:         "avocados",
			Title:             "Organic Hass Avocados",
			Subtitle:          "500 g · Ready to eat",
			ImageURL:          "https://cdn.abeta.dev/fresh/avocados.png",
			PriceText:         "$3.49",
			OriginalPriceText: "$4.49",
			DiscountRibbon:    "22% OFF",
			EtaLabel:          "8 MINS",
			Rating:            4.9,
			ReviewCount:       atoms.IntPtr(612),
			Quantity:          1,
			OnIncrement:       &incAvocado,
			OnDecrement:       &decAvocado,
			VariantLabel:      "500 g ▾",
			OnVariantClick:    &variantSheet,
			OnFavorite:        &favAvocado,
		}),
		molecules.ExpressProductTile(molecules.ProductTileSpec{
			ProductID:         "sourdough",
			Title:             "Artisan Sourdough Loaf",
			Subtitle:          "450 g · Stone-baked",
			ImageURL:          "https://cdn.abeta.dev/fresh/sourdough.png",
			PriceText:         "$4.99",
			OriginalPriceText: "$5.79",
			DiscountRibbon:    "14% OFF",
			TopEndBadge:       "VEG",
			EtaLabel:          "8 MINS",
			Rating:            4.8,
			ReviewCount:       atoms.IntPtr(384),
			Quantity:          1,
			OnIncrement:       &incAvocado,
			OnDecrement:       &decAvocado,
		}),
	}

	root := snippets.VStack(
		theming.SpacingSM.Dp(),
		snippets.TextNode(atoms.NewText("Bestsellers Near You", theming.TypographyTitleMedium, theming.ColorTextPrimary)).
			WithNodeID("fresh_bestsellers_title"),
		snippets.GridNode(2, theming.SpacingSM.Dp(), tiles...).
			WithNodeID("fresh_bestsellers_grid").
			WithColumnsByWidth(snippets.ResponsiveColumnsSpec{
				Compact:  atoms.IntPtr(2),
				Medium:   atoms.IntPtr(3),
				Expanded: atoms.IntPtr(4),
			}),
	).
		WithNodeID("fresh_bestsellers_root").
		WithLayout(atoms.LayoutConfig{
			Padding:      atoms.SymmetricSpacing(theming.SpacingLG.Dp(), theming.SpacingSM.Dp()),
			FillMaxWidth: atoms.BoolPtr(true),
		})

	return snippets.NewComposedBlockSnippet("fresh_bestsellers_block", "fresh_bestsellers", root).
		WithRecipeName("express_product_grid").
		WithTracking("snippet_impression", map[string]any{
			"snippet_id": "fresh_bestsellers_block",
			"surface":    "fresh-home",
		}).
		Build()
}

func buildFreshHomeScreen() snippets.PageResponse {
	backIcon := backNavigationIcon("back_from_fresh_home")
	toolbarTitle := atoms.NewText("Abeta Fresh", theming.TypographyTitleLarge, theming.ColorTextPrimary)
	toolbarSub := atoms.NewText("8 mins · Home — 742 Evergreen Terrace", theming.TypographyBodySmall, theming.ColorTextSecondary)

	stickySearch := snippets.NewSearchBarSnippet("fresh_sticky_search", "fresh_search", "Search milk, avocados, sourdough...").
		WithSticky(true).
		WithLeadingIcon(atoms.NewIcon("search", 20, theming.ColorTextSecondary)).
		WithSubmitLabel("Search").
		WithOnSubmit(actions.NewNavigateAction("submit_fresh_search", actions.NavigatePayload{
			Route: "/screens/fresh-home?q={{query}}",
		}).WithTracking("search_submitted", map[string]string{"surface": "fresh-home"})).
		WithOnClear(actions.NewNavigateAction("clear_fresh_search", actions.NavigatePayload{
			Route: "/screens/fresh-home",
		})).
		WithLayout(cardLayout()).
		WithTracking("snippet_impression", map[string]any{
			"snippet_id": "fresh_sticky_search",
			"sticky":     "true",
		}).
		Build()

	catClick := actions.NewShowToastAction("tap_fresh_category", actions.ShowToastPayload{
		Message:    "Filtering express aisle",
		DurationMs: 1400,
		Variant:    "info",
	})
	categoryGrid := molecules.CategoryGridSection("fresh_category_grid", "fresh_cats", "Shop by Category", []molecules.CategoryTileSpec{
		{CategoryID: "produce", Label: "Fruits & Veg", ImageURL: "https://cdn.abeta.dev/fresh/cat-produce.png", BadgeLabel: "FRESH", OnClick: &catClick},
		{CategoryID: "dairy", Label: "Dairy & Eggs", ImageURL: "https://cdn.abeta.dev/fresh/cat-dairy.png", OnClick: &catClick},
		{CategoryID: "bakery", Label: "Bakery", ImageURL: "https://cdn.abeta.dev/fresh/cat-bakery.png", OnClick: &catClick},
		{CategoryID: "drinks", Label: "Cold Drinks", ImageURL: "https://cdn.abeta.dev/fresh/cat-drinks.png", OnClick: &catClick},
		{CategoryID: "snacks", Label: "Munchies", ImageURL: "https://cdn.abeta.dev/fresh/cat-snacks.png", OnClick: &catClick},
		{CategoryID: "organic", Label: "Organic", ImageURL: "https://cdn.abeta.dev/fresh/cat-organic.png", BadgeLabel: "NEW", OnClick: &catClick},
	})

	upsellRail := molecules.ImpulseUpsellRail("fresh_upsell_rail", "fresh_upsell", "Before You Checkout", []molecules.ProductTileSpec{
		{ProductID: "yuzu_water", Title: "Sparkling Yuzu Water", Subtitle: "330 ml · Chilled", ImageURL: "https://cdn.abeta.dev/fresh/yuzu.png", PriceText: "$1.99", OriginalPriceText: "$2.49", DiscountRibbon: "20% OFF", EtaLabel: "8 MINS"},
		{ProductID: "kettle_chips", Title: "Sea Salt Kettle Chips", Subtitle: "140 g · Olive oil", ImageURL: "https://cdn.abeta.dev/fresh/chips.png", PriceText: "$2.99", TopEndBadge: "VEG", EtaLabel: "8 MINS"},
	})

	saveNote := actions.NewSaveStateAction("save_fresh_delivery_pref", actions.SaveStatePayload{
		Key:   "leave_at_door",
		Value: true,
		Scope: "session",
	})
	deliveryCard := molecules.DeliveryInstructionsCard("fresh_delivery_instructions", "fresh_deliv", molecules.DeliveryInstructionsSpec{
		LeaveAtDoor:          true,
		OnLeaveAtDoorChange:  &saveNote,
		DontRingBellFieldID:  "dont_ring_bell",
		DontRingBell:         true,
		OnDontRingBellChange: &saveNote,
		TipChips: []molecules.TipChipOption{
			{ID: "1", Label: "$1", OnClick: &saveNote},
			{ID: "2", Label: "$2", Selected: true, OnClick: &saveNote},
			{ID: "3", Label: "$3", OnClick: &saveNote},
		},
	})

	applyCouponAct := actions.NewShowToastAction("apply_fresh_coupon", actions.ShowToastPayload{
		Message:    "FRESH20 applied — saved $2.50!",
		DurationMs: 1800,
		Variant:    "success",
	}).WithTracking("coupon_applied", map[string]string{"code": "FRESH20"})
	couponCard := molecules.CouponApplyCard("fresh_coupon_card", "fresh_coupon", molecules.CouponApplySpec{
		InitialCode: "FRESH20",
		OnApply:     &applyCouponAct,
		QuickCoupons: []molecules.CouponChipSpec{
			{Code: "FRESH20", Selected: true, OnClick: &applyCouponAct},
			{Code: "FREEDEL", Label: "FREEDEL", OnClick: &applyCouponAct},
		},
		AppliedCode:        "FRESH20",
		AppliedSavingsText: "Saved $2.50 on express basket",
	})

	billSummary := molecules.CheckoutBillSummary("fresh_bill_summary", "fresh_bill", molecules.CheckoutBillSpec{
		SavingsBadge: "SAVED $4.30",
		Items: []molecules.BillLineItem{
			{ID: "items", Label: "Items Total", Value: "$8.48", Sublabel: "2 items"},
			{ID: "delivery", Label: "Delivery Fee", Value: "FREE", ValueColor: atoms.ColorRef(theming.ColorSuccess)},
			{ID: "coupon", Label: "FRESH20 Discount", Value: "-$2.50", ValueColor: atoms.ColorRef(theming.ColorSuccess)},
		},
		GrandTotalValue:    "$5.98",
		GrandTotalSublabel: "Incl. all taxes · 8-min cold-chain guarantee",
		SavingsCallout:     "You saved $4.30 with Abeta Fresh Express!",
	})

	placeOrderAct := actions.NewCallAPIAction("fresh_place_order", actions.CallAPIPayload{
		Endpoint: "/api/v1/orders/express",
		Method:   "POST",
		Body:     map[string]any{"store_brand": "Abeta Fresh", "delivery_eta_mins": 8},
	}).WithTracking("checkout_submitted", map[string]string{"surface": "fresh-home"})

	cartCTA := atoms.NewButton("btn_fresh_checkout", "Checkout · $5.98", "primary", &placeOrderAct)
	etaTag := atoms.NewTag("8 MINS", theming.ColorSuccessContainer)

	page := snippets.NewPage("fresh-home", "Abeta Fresh").
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
			buildFreshHeroMediaPager(),
			categoryGrid,
			buildFreshExpressProductGrid(),
			upsellRail,
			couponCard,
			deliveryCard,
			billSummary,
		).
		WithFloatingCart(snippets.FloatingCartSpec{
			Label:                      "2 items · $8.48",
			EtaBadge:                   &etaTag,
			CTA:                        cartCTA,
			FreeDeliveryThresholdCents: atoms.IntPtr(1500),
			NudgePrefix:                "Add $6.52 more for FREE express delivery",
			UnlockedLabel:              "FREE express delivery unlocked!",
		}).
		WithTracking("page_loaded", map[string]any{"page_id": "fresh-home", "vertical": "abeta-fresh"}).
		Build()

	snippets.EnsureComponentIDs(&page)
	return page
}
