package infra_test

import (
	"encoding/json"
	"testing"

	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

func canonicalNudgesAndComposedScreen() snippets.PageResponse {
	ptrAction := func(a actions.ActionTrigger) *actions.ActionTrigger { return &a }
	ptrIcon := func(i atoms.IconAtom) *atoms.IconAtom { return &i }
	ptrTag := func(t atoms.TagAtom) *atoms.TagAtom { return &t }

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

	toolbarTitle := atoms.NewText("Abeta Live Deals & Tracker", theming.TypographyTitleLarge, theming.ColorTextPrimary)
	toolbarSub := atoms.NewText("Nudges, marquee, live order tracking & atomic molding", theming.TypographyBodySmall, theming.ColorTextSecondary)
	backIcon := atoms.NewIcon("arrow_back", 22, theming.ColorTextPrimary)
	backIcon.ContentDescription = "Navigate back"
	backIcon.OnClick = ptrAction(actions.NewBackPressAction("back_from_nudges", actions.BackPressPayload{Steps: 1}))

	// 1. Nudge Bar (free delivery progress + countdown + dismissible + pointer_edge)
	nudgeBadge := atoms.NewTag("NEARLY THERE", theming.ColorSuccessContainer)
	nudgeBadge.Motion = &atoms.MotionSpec{LoopEffect: atoms.MotionLoopWiggle}

	nudgeCountdown := atoms.NewCountdown("nudge_slot_cd", 420, "Slot holds for")
	nudgeCountdown.OnExpire = ptrAction(actions.NewShowToastAction("nudge_slot_expired", actions.ShowToastPayload{
		Message:    "Express slot refreshed",
		DurationMs: 1600,
		Variant:    "info",
	}))
	nudgeCountdown.Motion = &atoms.MotionSpec{LoopEffect: atoms.MotionLoopHeartbeat}

	nudgeCTA := atoms.NewButton(
		"btn_add_filler",
		"Add $6.50",
		"primary",
		ptrAction(actions.NewNavigateAction("nav_fresh_fillers", actions.NavigatePayload{
			Route:  "/screens/store-home",
			Params: map[string]string{"section": "fillers"},
		})),
	)
	nudgeCTA.Size = "sm"

	nudgeSnippet := snippets.NewNudgeBarSnippet(
		"demo_nudge_bar",
		"free_delivery_nudge",
		atoms.NewText("Add $6.50 more for FREE express delivery", theming.TypographyTitleSmall, theming.ColorTextPrimary),
	).
		WithSubtitle(atoms.NewText("74% unlocked · arrives chilled in 8 mins", theming.TypographyBodySmall, theming.ColorTextSecondary)).
		WithIcon(atoms.NewIcon("local_shipping", 20, theming.ColorSuccess)).
		WithBadge(nudgeBadge).
		WithCountdown(nudgeCountdown).
		WithProgress(0.74).
		WithCTA(nudgeCTA).
		WithDismissible(true).
		WithPointerEdge(snippets.PointerEdgeBottom).
		WithOnDismiss(actions.NewTrackEventAction("dismiss_delivery_nudge", actions.TrackEventPayload{
			EventName:  "nudge_dismissed",
			Properties: map[string]any{"nudge_id": "free_delivery_nudge", "surface": "nudges-and-composed-demo"},
		})).
		WithOnClick(actions.NewShowToastAction("tap_delivery_nudge", actions.ShowToastPayload{
			Message:    "Showing quick basket toppers",
			DurationMs: 1500,
			Variant:    "info",
		})).
		WithLayout(cardLayout).
		WithMotion(atoms.MotionSpec{
			Enter:         atoms.MotionTransitionSlideDown,
			DurationToken: atoms.MotionDurationFast,
		}).
		WithTracking("snippet_impression", map[string]any{
			"snippet_id": "demo_nudge_bar",
			"nudge_id":   "free_delivery_nudge",
		}).
		Build()

	// 2. Marquee Strip (continuous horizontal promo ticker)
	marqueeSnippet := snippets.NewMarqueeStripSnippet(
		"demo_marquee_strip",
		"fresh_live_ticker",
		snippets.MarqueeItem{
			ID:    "mq_free_delivery",
			Text:  "FREE express delivery over $25",
			Icon:  ptrIcon(atoms.NewIcon("bolt", 16, theming.ColorBrandPrimary)),
			Badge: ptrTag(atoms.NewTag("LIVE", theming.ColorSuccessContainer)),
			OnClick: ptrAction(actions.NewTrackEventAction("tap_mq_delivery", actions.TrackEventPayload{
				EventName:  "marquee_item_tapped",
				Properties: map[string]any{"item_id": "mq_free_delivery", "strip_id": "fresh_live_ticker"},
			})),
		},
		snippets.MarqueeItem{
			ID:    "mq_harvest",
			Text:  "Organic Hass Avocados 33% OFF",
			Icon:  ptrIcon(atoms.NewIcon("eco", 16, theming.ColorSuccess)),
			Badge: ptrTag(atoms.NewTag("FLASH", theming.ColorErrorContainer)),
		},
		snippets.MarqueeItem{
			ID:   "mq_club",
			Text: "Earn double Abeta Club stamps today",
			Icon: ptrIcon(atoms.NewIcon("stars", 16, theming.ColorWarning)),
		},
	).
		WithVelocityDpPerSec(48).
		WithSeparator("•").
		WithLayout(atoms.LayoutConfig{
			Padding:      atoms.SymmetricSpacing(theming.SpacingMD.Dp(), theming.SpacingSM.Dp()),
			BgColor:      atoms.ColorRef(theming.ColorSurfaceSecondary),
			FillMaxWidth: atoms.BoolPtr(true),
		}).
		WithMotion(atoms.MotionSpec{
			Enter:         atoms.MotionTransitionFadeIn,
			DurationToken: atoms.MotionDurationFast,
			LoopEffect:    atoms.MotionLoopMarquee,
		}).
		WithTracking("snippet_impression", map[string]any{
			"snippet_id": "demo_marquee_strip",
			"strip_id":   "fresh_live_ticker",
		}).
		Build()

	// 3. Order Tracker (4-stage live delivery progress + ETA countdown + courier details)
	etaCountdown := atoms.NewCountdown("order_eta_cd", 480, "Arriving in")
	etaCountdown.Motion = &atoms.MotionSpec{LoopEffect: atoms.MotionLoopPulse}

	callCourierBtn := atoms.NewButton(
		"btn_call_courier",
		"Call Courier",
		"primary",
		ptrAction(actions.NewShowToastAction("toast_call_courier", actions.ShowToastPayload{
			Message:    "Connecting masked voice line to Aarav K.",
			DurationMs: 1800,
			Variant:    "info",
		})),
	)
	callCourierBtn.Size = "sm"

	orderHelpBtn := atoms.NewButton(
		"btn_order_help",
		"Order Help",
		"outline",
		ptrAction(actions.NewOpenDialogAction("open_order_help", actions.OpenDialogPayload{
			DialogID:     "order_help_sheet",
			Title:        "Need help with #AF-4092?",
			Message:      "Live support responds in under 30 seconds for active express deliveries.",
			Presentation: actions.PresentationBottomSheet,
			Dismissible:  atoms.BoolPtr(true),
		})),
	)
	orderHelpBtn.Size = "sm"

	trackerSnippet := snippets.NewOrderTrackerSnippet(
		"demo_order_tracker",
		"order_af_4092",
		"#AF-4092",
		atoms.NewText("Your Abeta Fresh basket is on the way", theming.TypographyTitleMedium, theming.ColorTextPrimary),
		snippets.OrderTrackerStep{
			ID:        "step_confirmed",
			Label:     "Confirmed",
			Caption:   "14:02",
			Icon:      ptrIcon(atoms.NewIcon("check_circle", 18, theming.ColorSuccess)),
			Completed: true,
			Active:    false,
		},
		snippets.OrderTrackerStep{
			ID:        "step_packed",
			Label:     "Chilled & Packed",
			Caption:   "14:04",
			Icon:      ptrIcon(atoms.NewIcon("inventory_2", 18, theming.ColorSuccess)),
			Completed: true,
			Active:    false,
		},
		snippets.OrderTrackerStep{
			ID:        "step_transit",
			Label:     "Out for Delivery",
			Caption:   "0.8 km away",
			Icon:      ptrIcon(atoms.NewIcon("two_wheeler", 18, theming.ColorBrandPrimary)),
			Completed: false,
			Active:    true,
		},
		snippets.OrderTrackerStep{
			ID:        "step_doorstep",
			Label:     "At Doorstep",
			Caption:   "ETA 8m",
			Icon:      ptrIcon(atoms.NewIcon("home", 18, theming.ColorTextTertiary)),
			Completed: false,
			Active:    false,
		},
	).
		WithEtaCountdown(etaCountdown).
		WithEtaBadge(atoms.NewTag("8 MINS", theming.ColorSuccessContainer)).
		WithRider("Aarav K.", "4.9★ · Insulated thermal pack · Zero-contact drop").
		WithPrimaryAction(callCourierBtn).
		WithSecondaryAction(orderHelpBtn).
		WithLayout(cardLayout).
		WithMotion(atoms.MotionSpec{
			Enter:         atoms.MotionTransitionSlideUp,
			DurationToken: atoms.MotionDurationNormal,
		}).
		WithTracking("snippet_impression", map[string]any{
			"snippet_id": "demo_order_tracker",
			"order_code": "#AF-4092",
		}).
		Build()

	// 4-6. Server-molded ComposedBlock showcase recipes (zero client release required)
	flashDealSnippet := snippets.MoldFlashDealRecipe(
		"demo_flash_deal_recipe",
		"deal_hass_avocados",
		"Organic Hass Avocados · Farm Pack (4 pcs)",
		"$3.99",
		"$5.99",
		540,
		actions.NewTriggerFeedbackAction("claim_flash_deal_fx", actions.TriggerFeedbackPayload{
			Effect:     actions.FeedbackCelebration,
			Title:      "Flash Deal Claimed!",
			Subtitle:   "Added to your Abeta Fresh basket",
			DurationMs: 1800,
		}),
	)

	loyaltyStampSnippet := snippets.MoldLoyaltyStampRecipe(
		"demo_loyalty_stamp_recipe",
		"abeta_fresh_club",
		"Abeta Fresh Club",
		3,
		5,
		"2 more express orders to unlock $5 reward credit",
		actions.NewNavigateAction("nav_loyalty_details", actions.NavigatePayload{
			Route: "/screens/profile",
		}),
	)

	liveEtaPromoSnippet := snippets.MoldLiveEtaPromoRecipe(
		"demo_live_eta_promo_recipe",
		"express_8min_promo",
		"Cold-chain groceries at your door",
		"Fresh produce, artisan bakery & chilled dairy",
		"8 MINS",
		480,
		actions.NewNavigateAction("nav_express_store", actions.NavigatePayload{
			Route: "/screens/store-home",
		}),
	)

	cartBadge := atoms.NewTag("8 mins", theming.ColorSuccessContainer)
	cartBadge.Motion = &atoms.MotionSpec{LoopEffect: atoms.MotionLoopFloat}
	cartCTA := atoms.NewButton(
		"btn_view_cart_nudge",
		"View Cart",
		"primary",
		ptrAction(actions.NewShowToastAction("toast_view_cart", actions.ShowToastPayload{
			Message:    "Opening express cart drawer",
			DurationMs: 1500,
			Variant:    "info",
		})),
	)
	cartCTA.Size = "sm"

	return snippets.NewPage("nudges-and-composed-demo", "Abeta Live Deals & Tracker").
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
		WithFloatingCart(snippets.FloatingCartSpec{
			Label:                      "2 items · $18.50",
			EtaBadge:                   &cartBadge,
			CTA:                        cartCTA,
			FreeDeliveryThresholdCents: atoms.IntPtr(2500),
			NudgePrefix:                "Add $6.50 more for FREE express delivery",
			UnlockedLabel:              "FREE express delivery unlocked!",
			Motion: &atoms.MotionSpec{
				Enter:         atoms.MotionTransitionSlideUp,
				Exit:          atoms.MotionTransitionSlideDown,
				DurationToken: atoms.MotionDurationFast,
			},
		}).
		AddSnippets(
			nudgeSnippet,
			marqueeSnippet,
			trackerSnippet,
			flashDealSnippet,
			loyaltyStampSnippet,
			liveEtaPromoSnippet,
		).
		WithTracking("page_loaded", map[string]any{
			"page_id": "nudges-and-composed-demo",
			"version": "v1",
		}).
		Build()
}

// assertStringOnlyTrackingProperties recursively checks that every tracking.properties
// and track_event payload.properties map in raw JSON contains string values only,
// enforcing KMP TrackingContract (Map<String, String>) wire compatibility.
func assertStringOnlyTrackingProperties(t *testing.T, raw []byte) {
	t.Helper()
	var root any
	if err := json.Unmarshal(raw, &root); err != nil {
		t.Fatalf("unmarshal JSON for tracking check: %v", err)
	}
	var walk func(node any, path string)
	walk = func(node any, path string) {
		switch v := node.(type) {
		case map[string]any:
			for _, trackingKey := range []string{"tracking", "click_tracking"} {
				if tracking, ok := v[trackingKey].(map[string]any); ok {
					if props, ok := tracking["properties"].(map[string]any); ok {
						for k, val := range props {
							if _, isStr := val.(string); !isStr {
								t.Errorf("%s.%s.properties[%q] = %T (%v), want string", path, trackingKey, k, val, val)
							}
						}
					}
				}
			}
			if v["type"] == "track_event" {
				if payload, ok := v["payload"].(map[string]any); ok {
					if props, ok := payload["properties"].(map[string]any); ok {
						for k, val := range props {
							if _, isStr := val.(string); !isStr {
								t.Errorf("%s.payload.properties[%q] = %T (%v), want string", path, k, val, val)
							}
						}
					}
				}
			}
			for k, child := range v {
				walk(child, path+"."+k)
			}
		case []any:
			for _, elem := range v {
				walk(elem, path+"[]")
			}
		}
	}
	walk(root, "$")
}
