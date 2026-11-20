package infra

import (
	"fmt"

	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

type freshProductSpec struct {
	id             string
	title          string
	subtitle       string
	price          string
	originalPrice  string
	unitPriceCents int
	badge          string
	badgeColor     theming.ColorToken
	rating         string
	initialQty     int
}

func buildFreshProductCard(parentID string, index int, spec freshProductSpec) snippets.SnippetEnvelope {
	fieldID := "cart_qty_" + spec.id
	incAct := actions.NewBatchAction("inc_"+spec.id, actions.BatchPayload{
		Actions: []actions.ActionTrigger{
			actions.NewTriggerFeedbackAction("bounce_"+spec.id, actions.TriggerFeedbackPayload{
				Effect:     actions.FeedbackCartBounce,
				TargetID:   "floating_cart_dock",
				DurationMs: 320,
			}),
			actions.NewTrackEventAction("track_inc_"+spec.id, actions.TrackEventPayload{
				EventName: "cart_item_incremented",
				Properties: map[string]any{
					"product_id":       spec.id,
					"field_id":         fieldID,
					"unit_price_cents": spec.unitPriceCents,
				},
			}),
		},
		StopOnError: true,
	})
	decAct := actions.NewTrackEventAction("dec_"+spec.id, actions.TrackEventPayload{
		EventName: "cart_item_decremented",
		Properties: map[string]any{
			"product_id": spec.id,
			"field_id":   fieldID,
		},
	})
	stepper := atoms.NewStepper(fieldID, "ADD", spec.initialQty, &incAct, &decAct)
	stepper.MinQuantity = 0
	stepper.MaxQuantity = 10

	previewToast := actions.NewShowToastAction("preview_"+spec.id, actions.ShowToastPayload{
		Message:    fmt.Sprintf("%s (%s) · Delivered chilled in 8 mins", spec.title, spec.price),
		DurationMs: 2000,
		Variant:    "info",
	})

	return snippets.NewProductCardSnippet(
		"prod_"+spec.id,
		spec.id,
		atoms.NewText(spec.title, theming.TypographyTitleSmall, theming.ColorTextPrimary),
		atoms.NewText(spec.price, theming.TypographyLabelLarge, theming.ColorTextPrimary),
		spec.unitPriceCents,
		stepper,
	).
		WithParentID(parentID).
		WithSubtitle(atoms.NewText(spec.subtitle, theming.TypographyBodySmall, theming.ColorTextSecondary)).
		WithOriginalPrice(atoms.NewText(spec.originalPrice, theming.TypographyBodySmall, theming.ColorTextTertiary)).
		WithBadge(atoms.NewTag(spec.badge, spec.badgeColor)).
		WithImage(atoms.ImageAtom{
			URL:                fmt.Sprintf("https://cdn.abeta.dev/fresh/%s.png", spec.id),
			ContentDescription: spec.title,
			HeightDp:           atoms.IntPtr(110),
			ScaleMode:          "crop",
			CornerRadius:       atoms.UniformRadius(theming.RadiusSM.Dp()),
		}).
		WithRating(atoms.NewText(spec.rating, theming.TypographyLabelSmall, theming.ColorSuccess)).
		WithOnClick(previewToast).
		WithLayout(compactCardLayout()).
		WithMotion(atoms.MotionSpec{
			Enter:         atoms.MotionTransitionScaleIn,
			DurationToken: atoms.MotionDurationFast,
			StaggerMs:     60,
			DelayMs:       index * 60,
		}).
		Build()
}

func buildStoreCategoryTabGrid(tabID, gridID string, products []freshProductSpec) snippets.SnippetEnvelope {
	items := make([]snippets.SnippetEnvelope, 0, len(products))
	for i, prod := range products {
		items = append(items, buildFreshProductCard(gridID, i, prod))
	}
	return snippets.NewGridSnippet(gridID, 2).
		WithParentID("store_category_tabs").
		WithColumnsByWidth(snippets.ResponsiveColumnsSpec{
			Compact:  atoms.IntPtr(2),
			Medium:   atoms.IntPtr(3),
			Expanded: atoms.IntPtr(4),
		}).
		WithSpacingDp(12).
		AddItems(items...).
		WithLayout(atoms.LayoutConfig{
			Padding:      atoms.SymmetricSpacing(theming.SpacingSM.Dp(), theming.SpacingSM.Dp()),
			FillMaxWidth: atoms.BoolPtr(true),
		}).
		WithTracking("category_grid_viewed", map[string]any{"tab_id": tabID}).
		Build()
}

func buildStorePromoCards() []snippets.SnippetEnvelope {
	promoItems := []struct {
		id    string
		title string
		sub   string
		badge string
		color theming.ColorToken
		delay int
	}{
		{
			id:    "promo_harvest",
			title: "Morning Harvest · 25% OFF",
			sub:   "Farm-fresh avocados, berries & leafy greens",
			badge: "FRESH",
			color: theming.ColorSuccessContainer,
			delay: 0,
		},
		{
			id:    "promo_bakery",
			title: "Warm Bakery & Dairy",
			sub:   "Artisan sourdough & chilled A2 milk in 8 mins",
			badge: "CHILLED",
			color: theming.ColorInfoContainer,
			delay: 80,
		},
		{
			id:    "promo_hydration",
			title: "Cold-Pressed Hydration",
			sub:   "Zero-sugar yuzu spritzers & oat cold brew",
			badge: "COMBO",
			color: theming.ColorWarningContainer,
			delay: 160,
		},
	}

	promoCards := make([]snippets.SnippetEnvelope, 0, len(promoItems))
	for _, p := range promoItems {
		promoToast := actions.NewShowToastAction("toast_"+p.id, actions.ShowToastPayload{
			Message:    p.title + " deal highlighted",
			DurationMs: 1800,
			Variant:    "info",
		})
		card := snippets.NewCardSnippet(p.id, atoms.NewText(p.title, theming.TypographyTitleMedium, theming.ColorTextPrimary)).
			WithParentID("store_promo_carousel").
			WithSubtitle(atoms.NewText(p.sub, theming.TypographyBodySmall, theming.ColorTextSecondary)).
			WithBadge(atoms.NewTag(p.badge, p.color)).
			WithOnClick(promoToast).
			WithLayout(compactCardLayout()).
			WithMotion(atoms.MotionSpec{
				Enter:         atoms.MotionTransitionSlideUp,
				DurationToken: atoms.MotionDurationNormal,
				StaggerMs:     80,
				DelayMs:       p.delay,
			}).
			Build()
		promoCards = append(promoCards, card)
	}
	return promoCards
}

func buildStoreCategoryTabs() snippets.SnippetEnvelope {
	fruitsGrid := buildStoreCategoryTabGrid("fruits_veg", "grid_fruits_veg", []freshProductSpec{
		{
			id:             "avocados",
			title:          "Fresh Avocados",
			subtitle:       "2 pcs · Ready to eat",
			price:          "$3.49",
			originalPrice:  "$4.49",
			unitPriceCents: 349,
			badge:          "BESTSELLER",
			badgeColor:     theming.ColorSuccessContainer,
			rating:         "4.9 ★",
			initialQty:     1,
		},
		{
			id:             "strawberries",
			title:          "Organic Strawberries",
			subtitle:       "250 g · Sweet harvest",
			price:          "$4.99",
			originalPrice:  "$5.99",
			unitPriceCents: 499,
			badge:          "ORGANIC",
			badgeColor:     theming.ColorBrandContainer,
			rating:         "4.8 ★",
			initialQty:     0,
		},
		{
			id:             "cherry_tomatoes",
			title:          "Vine Cherry Tomatoes",
			subtitle:       "300 g · Greenhouse",
			price:          "$2.79",
			originalPrice:  "$3.29",
			unitPriceCents: 279,
			badge:          "LOCAL",
			badgeColor:     theming.ColorInfoContainer,
			rating:         "4.7 ★",
			initialQty:     0,
		},
		{
			id:             "baby_spinach",
			title:          "Crisp Baby Spinach",
			subtitle:       "200 g · Triple washed",
			price:          "$2.49",
			originalPrice:  "$2.99",
			unitPriceCents: 249,
			badge:          "WASHED",
			badgeColor:     theming.ColorSuccessContainer,
			rating:         "4.8 ★",
			initialQty:     0,
		},
	})

	dairyGrid := buildStoreCategoryTabGrid("dairy_bread", "grid_dairy_bread", []freshProductSpec{
		{
			id:             "sourdough",
			title:          "Artisan Sourdough",
			subtitle:       "450 g · Stone-baked",
			price:          "$4.99",
			originalPrice:  "$5.79",
			unitPriceCents: 499,
			badge:          "BAKED TODAY",
			badgeColor:     theming.ColorWarningContainer,
			rating:         "4.9 ★",
			initialQty:     1,
		},
		{
			id:             "whole_milk",
			title:          "A2 Farm Whole Milk",
			subtitle:       "1 L · Chilled glass",
			price:          "$3.29",
			originalPrice:  "$3.79",
			unitPriceCents: 329,
			badge:          "CHILLED",
			badgeColor:     theming.ColorInfoContainer,
			rating:         "4.9 ★",
			initialQty:     0,
		},
		{
			id:             "greek_yogurt",
			title:          "Greek Strained Yogurt",
			subtitle:       "400 g · 18g protein",
			price:          "$3.99",
			originalPrice:  "$4.49",
			unitPriceCents: 399,
			badge:          "HIGH PROTEIN",
			badgeColor:     theming.ColorBrandContainer,
			rating:         "4.8 ★",
			initialQty:     0,
		},
		{
			id:             "cultured_butter",
			title:          "Sea Salt Cultured Butter",
			subtitle:       "200 g · Grass-fed",
			price:          "$4.29",
			originalPrice:  "$4.99",
			unitPriceCents: 429,
			badge:          "FARMSTEAD",
			badgeColor:     theming.ColorSuccessContainer,
			rating:         "4.9 ★",
			initialQty:     0,
		},
	})

	snacksGrid := buildStoreCategoryTabGrid("snacks_drinks", "grid_snacks_drinks", []freshProductSpec{
		{
			id:             "cold_brew",
			title:          "Oat Milk Cold Brew",
			subtitle:       "250 ml · Single origin",
			price:          "$3.79",
			originalPrice:  "$4.29",
			unitPriceCents: 379,
			badge:          "CHILLED",
			badgeColor:     theming.ColorInfoContainer,
			rating:         "4.8 ★",
			initialQty:     0,
		},
		{
			id:             "sparkling_yuzu",
			title:          "Sparkling Yuzu Water",
			subtitle:       "330 ml · Zero sugar",
			price:          "$1.99",
			originalPrice:  "$2.49",
			unitPriceCents: 199,
			badge:          "ZERO SUGAR",
			badgeColor:     theming.ColorSuccessContainer,
			rating:         "4.7 ★",
			initialQty:     0,
		},
		{
			id:             "sea_salt_chips",
			title:          "Kettle Sea Salt Chips",
			subtitle:       "140 g · Olive oil",
			price:          "$2.99",
			originalPrice:  "$3.49",
			unitPriceCents: 299,
			badge:          "CRUNCHY",
			badgeColor:     theming.ColorWarningContainer,
			rating:         "4.8 ★",
			initialQty:     0,
		},
		{
			id:             "roasted_almonds",
			title:          "Roasted Tamari Almonds",
			subtitle:       "180 g · Small batch",
			price:          "$5.49",
			originalPrice:  "$6.49",
			unitPriceCents: 549,
			badge:          "ROASTED",
			badgeColor:     theming.ColorBrandContainer,
			rating:         "4.9 ★",
			initialQty:     0,
		},
	})

	return snippets.NewTabsSnippet("store_category_tabs").
		AddTabs(
			snippets.TabItemSpec{
				ID:       "fruits_veg",
				Title:    "Fruits & Veg",
				Badge:    ptrTag(atoms.NewTag("FRESH", theming.ColorSuccessContainer)),
				Snippets: []snippets.SnippetEnvelope{fruitsGrid},
			},
			snippets.TabItemSpec{
				ID:       "dairy_bread",
				Title:    "Dairy & Bread",
				Badge:    ptrTag(atoms.NewTag("COLD", theming.ColorInfoContainer)),
				Snippets: []snippets.SnippetEnvelope{dairyGrid},
			},
			snippets.TabItemSpec{
				ID:       "snacks_drinks",
				Title:    "Snacks & Drinks",
				Snippets: []snippets.SnippetEnvelope{snacksGrid},
			},
		).
		WithSelectedIndex(0).
		WithLayout(atoms.LayoutConfig{
			Padding:      atoms.SymmetricSpacing(theming.SpacingMD.Dp(), theming.SpacingXS.Dp()),
			FillMaxWidth: atoms.BoolPtr(true),
		}).
		Build()
}

func buildStoreOffersAccordion(freeDeliveryToast actions.ActionTrigger) snippets.SnippetEnvelope {
	saveCouponAct := actions.NewSaveStateAction("save_fresh_coupon", actions.SaveStatePayload{
		Key:   "apply_fresh_coupon",
		Value: true,
		Scope: "session",
	})
	saveTipAct := actions.NewSaveStateAction("save_rider_tip", actions.SaveStatePayload{
		Key:   "rider_tip_dollars",
		Value: 2,
		Scope: "session",
	})
	applyOffersAct := actions.NewSaveStateAction("apply_store_offers", actions.SaveStatePayload{
		Key:   "fresh_offers_saved",
		Value: true,
		Scope: "session",
	}).WithSecondaryActions(
		actions.NewShowToastAction("toast_offers_applied", actions.ShowToastPayload{
			Message:    "FRESH20 coupon & rider tip updated",
			DurationMs: 2000,
			Variant:    "success",
		}),
	)

	couponSwitch := atoms.NewSwitch("apply_fresh_coupon", "Apply FRESH20 Instant Coupon", true, &saveCouponAct)
	couponSwitch.Subtitle = "Saves $2.00 on express baskets over $8"

	tipSlider := atoms.NewSlider("rider_tip_dollars", "Express Rider Tip", 2, 0, 10, &saveTipAct)
	tipSlider.Steps = 5
	tipSlider.UnitLabel = "$"

	offersForm := snippets.NewFormSnippet(
		"store_offers_form",
		"fresh_order_prefs",
		atoms.NewButton("btn_apply_offers", "Apply Offers", "secondary", &applyOffersAct),
	).
		WithParentID("store_offers_accordion").
		WithTitle(atoms.NewText("Savings & Rider Tip", theming.TypographyTitleSmall, theming.ColorTextPrimary)).
		AddSwitch(couponSwitch).
		AddSlider(tipSlider).
		WithLayout(cardLayout()).
		Build()

	promiseRow := snippets.NewActionRowSnippet(
		"store_delivery_promise",
		atoms.NewText("8-Min Cold-Chain Guarantee", theming.TypographyTitleSmall, theming.ColorTextPrimary),
	).
		WithParentID("store_offers_accordion").
		WithSubtitle(atoms.NewText("Chilled dispatch from Abeta Fresh Hub · Seal verified", theming.TypographyBodySmall, theming.ColorTextSecondary)).
		WithLeadingIcon(atoms.NewIcon("verified", 22, theming.ColorSuccess)).
		WithTrailingTag(atoms.NewTag("GUARANTEED", theming.ColorSuccessContainer)).
		WithOnClick(freeDeliveryToast).
		WithLayout(cardLayout()).
		Build()

	return snippets.NewAccordionSnippet(
		"store_offers_accordion",
		atoms.NewText("Offers & Delivery Promise", theming.TypographyTitleMedium, theming.ColorTextPrimary),
	).
		WithSubtitle(atoms.NewText("Apply FRESH20 coupon, rider tip & view cold-chain guarantee", theming.TypographyBodySmall, theming.ColorTextSecondary)).
		WithInitiallyExpanded(true).
		AddSnippets(promiseRow, offersForm).
		WithLayout(cardLayout()).
		Build()
}

func buildStoreHomeScreen() snippets.PageResponse {
	backIcon := backNavigationIcon("back_from_store_home")

	placeOrderAct := actions.NewCallAPIAction("place_express_order", actions.CallAPIPayload{
		Endpoint: "/api/v1/orders/express",
		Method:   "POST",
		Body: map[string]any{
			"store_brand":       "Abeta Fresh",
			"delivery_eta_mins": 8,
			"coupon_code":       "FRESH20",
		},
	}).WithSecondaryActions(
		actions.NewDismissOverlayAction("dismiss_express_checkout", actions.DismissOverlayPayload{
			OverlayID: "express_checkout_sheet",
		}),
	)

	checkoutSheetAct := actions.NewOpenDialogAction("open_express_checkout", actions.OpenDialogPayload{
		DialogID:       "express_checkout_sheet",
		Title:          "Place Express Order",
		Message:        "Abeta Fresh · 8-Min Express Delivery to Home · Free delivery applied.",
		Presentation:   actions.PresentationBottomSheet,
		Dismissible:    atoms.BoolPtr(true),
		PositiveAction: &placeOrderAct,
		NegativeAction: ptrAction(actions.NewDismissOverlayAction("cancel_express_checkout", actions.DismissOverlayPayload{
			OverlayID: "express_checkout_sheet",
		})),
	})

	cartIcon := atoms.NewIcon("shopping_cart", 22, theming.ColorBrandPrimary)
	cartIcon.ContentDescription = "Open cart checkout"
	cartIcon.OnClick = &checkoutSheetAct

	toolbarTitle := atoms.NewText("Abeta Fresh", theming.TypographyTitleLarge, theming.ColorTextPrimary)
	toolbarSub := atoms.NewText("Home · Express 8 mins", theming.TypographyBodySmall, theming.ColorTextSecondary)

	freeDeliveryToast := actions.NewShowToastAction("toast_free_delivery", actions.ShowToastPayload{
		Message:    "FREE DELIVERY unlocked on your Abeta Fresh express order!",
		DurationMs: 2200,
		Variant:    "success",
	})
	freeDeliveryPill := atoms.NewButton("btn_free_delivery_pill", "FREE DELIVERY", "outline", &freeDeliveryToast)
	freeDeliveryPill.Size = "sm"
	freeDeliveryPill.Motion = &atoms.MotionSpec{
		Enter:         atoms.MotionTransitionFadeIn,
		DurationToken: atoms.MotionDurationFast,
	}

	etaTag := atoms.NewTag("8 MINS", theming.ColorSuccessContainer)
	etaTag.Motion = &atoms.MotionSpec{LoopEffect: atoms.MotionLoopPulse}

	bannerLayout := cardLayout()
	bannerLayout.BgColor = atoms.ColorRef(theming.ColorBrandContainer)

	checkoutCTA := atoms.NewButton("btn_floating_checkout", "Place Express Order", "primary", &checkoutSheetAct)
	checkoutCTA.Motion = &atoms.MotionSpec{
		Enter:         atoms.MotionTransitionSpringBounce,
		DurationToken: atoms.MotionDurationFast,
	}

	floatingEta := atoms.NewTag("8 MINS", theming.ColorSuccessContainer)
	floatingEta.Motion = &atoms.MotionSpec{LoopEffect: atoms.MotionLoopPulse}

	return snippets.NewPage("store-home", "Abeta Fresh").
		WithToolbar(snippets.ToolbarSpec{
			Title:           &toolbarTitle,
			Subtitle:        &toolbarSub,
			NavigationIcon:  &backIcon,
			TrailingActions: []atoms.IconAtom{cartIcon},
			BgColor:         atoms.ColorRef(theming.ColorSurfacePrimary),
		}).
		WithAdaptiveLayout(snippets.AdaptivePageSpec{
			LayoutMode:          snippets.LayoutModeCentered,
			MaxWidthDp:          atoms.IntPtr(1120),
			SplitPaneEnabled:    true,
			SecondarySnippetIDs: []string{"store_offers_accordion"},
		}).
		WithPullToRefresh(actions.NewRefreshPageAction("pull_refresh_store_home", actions.RefreshPagePayload{
			PageID:     "store-home",
			ShowLoader: true,
		})).
		AddPageAction(actions.NewTrackEventAction("track_store_home_view", actions.TrackEventPayload{
			EventName:  "screen_viewed",
			Properties: map[string]any{"screen_id": "store-home", "brand": "Abeta Fresh"},
		})).
		AddSnippets(
			snippets.NewBannerSnippet("store_eta_banner", atoms.NewText("8-Min Express Delivery", theming.TypographyTitleMedium, theming.ColorTextPrimary)).
				WithBody(atoms.NewText("Chilled produce, bakery & pantry staples delivered fresh from Abeta Fresh Hub.", theming.TypographyBodySmall, theming.ColorTextSecondary)).
				WithIcon(atoms.NewIcon("bolt", 24, theming.ColorBrandPrimary)).
				WithTag(etaTag).
				WithCTA(freeDeliveryPill).
				WithLayout(bannerLayout).
				WithMotion(atoms.MotionSpec{
					Enter:         atoms.MotionTransitionSlideDown,
					DurationToken: atoms.MotionDurationNormal,
					LoopEffect:    atoms.MotionLoopPulse,
				}).
				Build(),
			snippets.NewCarouselSnippet("store_promo_carousel").
				WithTitle(atoms.NewText("Flash Express Deals", theming.TypographyTitleMedium, theming.ColorTextPrimary)).
				AddItems(buildStorePromoCards()...).
				WithItemWidthDp(240).
				WithSpacingDp(12).
				WithLayout(atoms.LayoutConfig{
					Padding:      atoms.SymmetricSpacing(theming.SpacingLG.Dp(), theming.SpacingSM.Dp()),
					FillMaxWidth: atoms.BoolPtr(true),
				}).
				Build(),
			buildStoreCategoryTabs(),
			buildStoreOffersAccordion(freeDeliveryToast),
		).
		WithFloatingCart(snippets.FloatingCartSpec{
			Label:    "2 items · $8.48",
			EtaBadge: &floatingEta,
			CTA:      checkoutCTA,
			Motion: &atoms.MotionSpec{
				Enter:         atoms.MotionTransitionSlideUp,
				Exit:          atoms.MotionTransitionSlideDown,
				DurationToken: atoms.MotionDurationNormal,
			},
		}).
		WithTracking("page_loaded", map[string]any{"page_id": "store-home"}).
		Build()
}
