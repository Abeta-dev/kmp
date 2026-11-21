package infra_test

import (
	"bytes"
	"context"
	"encoding/json"
	"flag"
	"os"
	"path/filepath"
	"testing"

	"github.com/abeta-dev/kmp/bff/internal/infra"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

var updateGolden = flag.Bool("update", false, "update golden JSON fixtures")

func goldenDir(t *testing.T) string {
	t.Helper()
	return filepath.Join("..", "..", "testdata", "golden")
}

func canonicalActionBatch() actions.ActionTrigger {
	return actions.NewBatchAction("batch_onboarding_submit", actions.BatchPayload{
		Actions: []actions.ActionTrigger{
			actions.NewSaveStateAction("save_onboarding_draft", actions.SaveStatePayload{
				Key:   "onboarding_completed",
				Value: true,
				Scope: "disk",
			}),
			actions.NewCallAPIAction("submit_onboarding", actions.CallAPIPayload{
				Endpoint: "/api/v1/actions/execute",
				Method:   "POST",
				Body:     map[string]any{"form_id": "workspace_onboarding"},
				OnSuccess: func() *actions.ActionTrigger {
					t := actions.NewNavigateAction("nav_home_after_onboarding", actions.NavigatePayload{
						Route:          "/screens/home",
						ClearBackStack: true,
					})
					return &t
				}(),
			}).WithValidations(
				actions.ValidationRule{
					FieldID: "full_name",
					Rule:    "required",
					Message: "Full name is required",
				},
				actions.ValidationRule{
					FieldID: "work_email",
					Rule:    "regex",
					Value:   `^[^@\s]+@[^@\s]+\.[^@\s]+$`,
					Message: "Enter a valid email address",
				},
			),
		},
		StopOnError: true,
	}).WithSecondaryActions(
		actions.NewTrackEventAction("track_onboarding_batch", actions.TrackEventPayload{
			EventName:  "onboarding_batch_dispatched",
			Properties: map[string]any{"screen": "form-demo"},
		}),
	)
}

func canonicalInteractiveScreen() snippets.PageResponse {
	ptrAction := func(a actions.ActionTrigger) *actions.ActionTrigger { return &a }
	ptrText := func(t atoms.TextAtom) *atoms.TextAtom { return &t }
	ptrTag := func(t atoms.TagAtom) *atoms.TagAtom { return &t }
	ptrIcon := func(i atoms.IconAtom) *atoms.IconAtom { return &i }

	cardLayout := atoms.LayoutConfig{
		Padding:      atoms.UniformSpacing(theming.SpacingLG.Dp()),
		Margin:       atoms.SymmetricSpacing(theming.SpacingLG.Dp(), theming.SpacingXS.Dp()),
		BgColor:      atoms.ColorRef(theming.ColorSurfacePrimary),
		CornerRadius: atoms.UniformRadius(12),
		Border: &atoms.BorderSpec{
			WidthDp: 1,
			Color:   *atoms.ColorRef(theming.ColorBorderSubtle),
		},
		FillMaxWidth: atoms.BoolPtr(true),
	}

	toolbarTitle := atoms.NewText("Abeta Discovery", theming.TypographyTitleLarge, theming.ColorTextPrimary)
	toolbarSub := atoms.NewText("Search, filters, and interactive swipe deck", theming.TypographyBodySmall, theming.ColorTextSecondary)
	backIcon := atoms.NewIcon("arrow_back", 22, theming.ColorTextPrimary)
	backIcon.ContentDescription = "Navigate back"
	backIcon.OnClick = ptrAction(actions.NewBackPressAction("back_from_interactive", actions.BackPressPayload{Steps: 1}))

	searchSubmit := actions.NewCallAPIAction("submit_discovery_search", actions.CallAPIPayload{
		Endpoint: "/api/v1/actions/execute",
		Method:   "POST",
		Body:     map[string]any{"search_id": "discovery_search"},
		OnSuccess: ptrAction(actions.NewShowToastAction("toast_search_done", actions.ShowToastPayload{
			Message:    "Discovery results updated",
			DurationMs: 1800,
			Variant:    "info",
		})),
	})
	searchClear := actions.NewSaveStateAction("clear_discovery_query", actions.SaveStatePayload{
		Key:   "discovery_query",
		Value: "",
		Scope: "session",
	})
	searchFilterSheet := actions.NewOpenDialogAction("open_discovery_filters", actions.OpenDialogPayload{
		DialogID:     "discovery_filters_sheet",
		Title:        "Discovery Filters",
		Message:      "Refine recommendations by platform target and availability.",
		Presentation: actions.PresentationBottomSheet,
		Dismissible:  atoms.BoolPtr(true),
		PositiveAction: ptrAction(actions.NewDismissOverlayAction("dismiss_discovery_filters", actions.DismissOverlayPayload{
			OverlayID: "discovery_filters_sheet",
		})),
	})

	searchSnippet := snippets.NewSearchBarSnippet("interactive_search_bar", "discovery_search", "Search builders, teams, or KMP kits").
		WithQuery("compose").
		WithLeadingIcon(atoms.NewIcon("search", 20, theming.ColorTextSecondary)).
		WithTrailingIcon(atoms.NewIcon("tune", 20, theming.ColorBrandPrimary)).
		WithSubmitLabel("Search").
		WithClearable(true).
		WithOnSubmit(searchSubmit).
		WithOnClear(searchClear).
		WithOnTrailingClick(searchFilterSheet).
		WithLayout(cardLayout).
		WithMotion(atoms.MotionSpec{
			Enter:         atoms.MotionTransitionFadeIn,
			DurationToken: atoms.MotionDurationFast,
		}).
		WithTracking("snippet_impression", map[string]any{
			"snippet_id": "interactive_search_bar",
			"surface":    "interactive-demo",
		}).
		Build()

	chipGroupSnippet := snippets.NewChipGroupSnippet(
		"interactive_chip_group",
		"discovery_categories",
		snippets.FilterChipItem{
			ID:       "chip_kmp",
			Label:    "KMP",
			Selected: true,
			Icon:     ptrIcon(atoms.NewIcon("sparkles", 16, theming.ColorBrandPrimary)),
			Badge:    "12",
			OnClick: ptrAction(actions.NewTrackEventAction("select_chip_kmp", actions.TrackEventPayload{
				EventName:  "filter_chip_toggled",
				Properties: map[string]any{"chip_id": "chip_kmp", "group_id": "discovery_categories"},
			})),
		},
		snippets.FilterChipItem{
			ID:       "chip_go_bff",
			Label:    "Go BFF",
			Selected: true,
			Badge:    "7",
			OnClick: ptrAction(actions.NewTrackEventAction("select_chip_go_bff", actions.TrackEventPayload{
				EventName:  "filter_chip_toggled",
				Properties: map[string]any{"chip_id": "chip_go_bff", "group_id": "discovery_categories"},
			})),
		},
		snippets.FilterChipItem{
			ID:       "chip_wearables",
			Label:    "Wearables",
			Selected: false,
			OnClick: ptrAction(actions.NewTrackEventAction("select_chip_wearables", actions.TrackEventPayload{
				EventName:  "filter_chip_toggled",
				Properties: map[string]any{"chip_id": "chip_wearables", "group_id": "discovery_categories"},
			})),
		},
	).
		WithTitle(atoms.NewText("Filter Topics", theming.TypographyTitleSmall, theming.ColorTextPrimary)).
		WithMultiSelect(true).
		WithLayout(atoms.LayoutConfig{
			Padding:      atoms.SymmetricSpacing(theming.SpacingLG.Dp(), theming.SpacingSM.Dp()),
			FillMaxWidth: atoms.BoolPtr(true),
		}).
		WithMotion(atoms.MotionSpec{
			Enter:         atoms.MotionTransitionSlideUp,
			DurationToken: atoms.MotionDurationFast,
			StaggerMs:     40,
		}).
		WithTracking("snippet_impression", map[string]any{
			"snippet_id": "interactive_chip_group",
			"group_id":   "discovery_categories",
		}).
		Build()

	card1 := snippets.SwipeCardItem{
		CardID:       "card_maya_chen",
		Title:        atoms.NewText("Maya Chen", theming.TypographyHeadlineSmall, theming.ColorTextPrimary),
		Subtitle:     ptrText(atoms.NewText("Staff KMP Architect · Compose Multiplatform", theming.TypographyBodyMedium, theming.ColorTextSecondary)),
		Bio:          ptrText(atoms.NewText("Building zero-release SDUI renderers across Android, iOS, macOS, and WasmJS.", theming.TypographyBodySmall, theming.ColorTextSecondary)),
		Badge:        ptrTag(atoms.NewTag("VERIFIED", theming.ColorSuccessContainer)),
		DistanceText: ptrText(atoms.NewText("2 km away", theming.TypographyLabelSmall, theming.ColorTextTertiary)),
		Image: &atoms.ImageAtom{
			URL:                "https://cdn.abeta.dev/profiles/maya-chen.png",
			ContentDescription: "Maya Chen profile preview",
			HeightDp:           atoms.IntPtr(220),
			ScaleMode:          "crop",
			CornerRadius:       atoms.UniformRadius(12),
		},
		Tags: []atoms.TagAtom{
			atoms.NewTag("Kotlin", theming.ColorBrandContainer),
			atoms.NewTag("SDUI", theming.ColorInfoContainer),
		},
		OnLike: ptrAction(actions.NewTrackEventAction("like_card_maya", actions.TrackEventPayload{
			EventName:  "swipe_card_liked",
			Properties: map[string]any{"card_id": "card_maya_chen", "deck_id": "builders_deck"},
		})),
		OnNope: ptrAction(actions.NewTrackEventAction("nope_card_maya", actions.TrackEventPayload{
			EventName:  "swipe_card_skipped",
			Properties: map[string]any{"card_id": "card_maya_chen", "deck_id": "builders_deck"},
		})),
		OnSuperLike: ptrAction(actions.NewTriggerFeedbackAction("super_like_maya_fx", actions.TriggerFeedbackPayload{
			Effect:     actions.FeedbackCelebration,
			Title:      "Priority Connect Sent!",
			Subtitle:   "Maya Chen has been notified",
			DurationMs: 1800,
		})),
		OnClick: ptrAction(actions.NewNavigateAction("open_maya_profile", actions.NavigatePayload{
			Route:  "/screens/profile",
			Params: map[string]string{"member_id": "card_maya_chen"},
		})),
	}

	card2 := snippets.SwipeCardItem{
		CardID:       "card_devon_patel",
		Title:        atoms.NewText("Devon Patel", theming.TypographyHeadlineSmall, theming.ColorTextPrimary),
		Subtitle:     ptrText(atoms.NewText("Principal Go Engineer · Edge BFF", theming.TypographyBodyMedium, theming.ColorTextSecondary)),
		Bio:          ptrText(atoms.NewText("Focused on HTTP/2 streaming, Brotli compression, and sub-10ms SDUI contracts.", theming.TypographyBodySmall, theming.ColorTextSecondary)),
		Badge:        ptrTag(atoms.NewTag("MENTOR", theming.ColorBrandContainer)),
		DistanceText: ptrText(atoms.NewText("5 km away", theming.TypographyLabelSmall, theming.ColorTextTertiary)),
		Tags: []atoms.TagAtom{
			atoms.NewTag("Go 1.26", theming.ColorInfoContainer),
			atoms.NewTag("OpenAPI", theming.ColorSurfaceSecondary),
		},
	}

	swipeDeckSnippet := snippets.NewSwipeDeckSnippet("interactive_swipe_deck", "builders_deck", card1, card2).
		WithLabels("Connect", "Skip", "Boost").
		WithEmptyState(
			atoms.NewText("You're all caught up", theming.TypographyTitleMedium, theming.ColorTextPrimary),
			atoms.NewText("Adjust your filter chips or check back soon for new collaborators.", theming.TypographyBodySmall, theming.ColorTextSecondary),
		).
		WithOnLike(actions.NewShowToastAction("deck_like_toast", actions.ShowToastPayload{
			Message:    "Added to connections",
			DurationMs: 1500,
			Variant:    "success",
		})).
		WithOnNope(actions.NewShowToastAction("deck_nope_toast", actions.ShowToastPayload{
			Message:    "Skipped card",
			DurationMs: 1200,
			Variant:    "info",
		})).
		WithOnSuperLike(actions.NewTriggerFeedbackAction("deck_super_like_haptic", actions.TriggerFeedbackPayload{
			Effect:   actions.FeedbackHapticTick,
			TargetID: "interactive_swipe_deck",
		})).
		WithOnUndo(actions.NewShowToastAction("deck_undo_toast", actions.ShowToastPayload{
			Message:    "Restored previous card",
			DurationMs: 1400,
			Variant:    "info",
		})).
		WithLayout(cardLayout).
		WithMotion(atoms.MotionSpec{
			Enter:         atoms.MotionTransitionScaleIn,
			DurationToken: atoms.MotionDurationNormal,
		}).
		WithTracking("snippet_impression", map[string]any{
			"snippet_id": "interactive_swipe_deck",
			"deck_id":    "builders_deck",
		}).
		Build()

	return snippets.NewPage("interactive-demo", "Abeta Discovery").
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
		AddSnippets(searchSnippet, chipGroupSnippet, swipeDeckSnippet).
		WithTracking("page_loaded", map[string]any{
			"page_id": "interactive-demo",
			"version": "v1",
		}).
		Build()
}

func writeOrCompareGolden(t *testing.T, filename string, value any) []byte {
	t.Helper()

	var buf bytes.Buffer
	enc := json.NewEncoder(&buf)
	enc.SetEscapeHTML(false)
	enc.SetIndent("", "  ")
	if err := enc.Encode(value); err != nil {
		t.Fatalf("encode %s: %v", filename, err)
	}
	got := buf.Bytes()

	path := filepath.Join(goldenDir(t), filename)
	if *updateGolden {
		if err := os.MkdirAll(filepath.Dir(path), 0o755); err != nil {
			t.Fatalf("mkdir golden dir: %v", err)
		}
		if err := os.WriteFile(path, got, 0o644); err != nil {
			t.Fatalf("write golden %s: %v", path, err)
		}
		return got
	}

	want, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read golden %s: %v", path, err)
	}
	if !bytes.Equal(bytes.TrimSpace(got), bytes.TrimSpace(want)) {
		t.Fatalf("golden mismatch for %s:\n--- got ---\n%s\n--- want ---\n%s", filename, string(got), string(want))
	}
	return want
}

func TestGoldenFixtures_RoundTrip(t *testing.T) {
	ctx := context.Background()
	repo := infra.NewInMemoryScreenRepository()

	homePage, err := repo.GetScreen(ctx, "home")
	if err != nil {
		t.Fatalf("GetScreen(home): %v", err)
	}
	homeBytes := writeOrCompareGolden(t, "home_screen.json", homePage)

	var decodedHome snippets.PageResponse
	if err := json.Unmarshal(homeBytes, &decodedHome); err != nil {
		t.Fatalf("round-trip unmarshal home_screen.json: %v", err)
	}
	if decodedHome.PageID != "home" || len(decodedHome.Snippets) == 0 {
		t.Fatalf("unexpected decoded home screen: %+v", decodedHome)
	}

	formPage, err := repo.GetScreen(ctx, "form-demo")
	if err != nil {
		t.Fatalf("GetScreen(form-demo): %v", err)
	}
	formBytes := writeOrCompareGolden(t, "form_screen.json", formPage)

	var decodedForm snippets.PageResponse
	if err := json.Unmarshal(formBytes, &decodedForm); err != nil {
		t.Fatalf("round-trip unmarshal form_screen.json: %v", err)
	}
	if decodedForm.PageID != "form-demo" || len(decodedForm.Snippets) != 2 {
		t.Fatalf("unexpected decoded form screen: %+v", decodedForm)
	}
	formData, err := snippets.DecodeSnippetData[snippets.FormSnippetData](decodedForm.Snippets[1])
	if err != nil || formData.FormID != "workspace_onboarding" || len(formData.Fields) != 3 {
		t.Fatalf("unexpected decoded form snippet: %+v, err=%v", formData, err)
	}

	batchAction := canonicalActionBatch()
	batchBytes := writeOrCompareGolden(t, "action_batch.json", batchAction)

	var decodedBatch actions.ActionTrigger
	if err := json.Unmarshal(batchBytes, &decodedBatch); err != nil {
		t.Fatalf("round-trip unmarshal action_batch.json: %v", err)
	}
	batchPayload, err := actions.DecodePayload[actions.BatchPayload](decodedBatch)
	if err != nil || len(batchPayload.Actions) != 2 || !batchPayload.StopOnError {
		t.Fatalf("unexpected decoded batch payload: %+v, err=%v", batchPayload, err)
	}

	containersPage, err := repo.GetScreen(ctx, "containers")
	if err != nil {
		t.Fatalf("GetScreen(containers): %v", err)
	}
	containersBytes := writeOrCompareGolden(t, "containers_screen.json", containersPage)

	var decodedContainers snippets.PageResponse
	if err := json.Unmarshal(containersBytes, &decodedContainers); err != nil {
		t.Fatalf("round-trip unmarshal containers_screen.json: %v", err)
	}
	if decodedContainers.PageID != "containers" || len(decodedContainers.Snippets) != 7 {
		t.Fatalf("unexpected decoded containers screen: %+v", decodedContainers)
	}
	if decodedContainers.Pagination == nil || !decodedContainers.Pagination.HasMore || decodedContainers.PullToRefresh == nil {
		t.Fatalf("expected pagination and pull_to_refresh on containers screen: %+v", decodedContainers)
	}

	hdrData, err := snippets.DecodeSnippetData[snippets.HeaderSnippetData](decodedContainers.Snippets[0])
	if err != nil || hdrData.Avatar == nil || hdrData.TrailingAction == nil || hdrData.TrailingAction.OnClick == nil {
		t.Fatalf("unexpected containers header: %+v, err=%v", hdrData, err)
	}
	dlgPayload, err := actions.DecodePayload[actions.OpenDialogPayload](*hdrData.TrailingAction.OnClick)
	if err != nil || dlgPayload.Presentation != actions.PresentationBottomSheet {
		t.Fatalf("expected bottom_sheet presentation on header action, got %+v, err=%v", dlgPayload, err)
	}

	carData, err := snippets.DecodeSnippetData[snippets.CarouselSnippetData](decodedContainers.Snippets[1])
	if err != nil || len(carData.Items) != 2 {
		t.Fatalf("unexpected carousel data: %+v, err=%v", carData, err)
	}
	gridData, err := snippets.DecodeSnippetData[snippets.GridSnippetData](decodedContainers.Snippets[2])
	if err != nil || gridData.Columns != 2 || len(gridData.Items) != 2 {
		t.Fatalf("unexpected grid data: %+v, err=%v", gridData, err)
	}
	tabsData, err := snippets.DecodeSnippetData[snippets.TabsSnippetData](decodedContainers.Snippets[3])
	if err != nil || len(tabsData.Tabs) != 2 {
		t.Fatalf("unexpected tabs data: %+v, err=%v", tabsData, err)
	}
	accData, err := snippets.DecodeSnippetData[snippets.AccordionSnippetData](decodedContainers.Snippets[4])
	if err != nil || !accData.InitiallyExpanded || len(accData.Snippets) != 1 {
		t.Fatalf("unexpected accordion data: %+v, err=%v", accData, err)
	}
	skelCard, err := snippets.DecodeSnippetData[snippets.CardSnippetData](decodedContainers.Snippets[5])
	if err != nil || skelCard.Avatar == nil || len(skelCard.Skeletons) != 3 {
		t.Fatalf("unexpected skeleton card data: %+v, err=%v", skelCard, err)
	}
	ctrlForm, err := snippets.DecodeSnippetData[snippets.FormSnippetData](decodedContainers.Snippets[6])
	if err != nil || len(ctrlForm.Switches) != 1 || len(ctrlForm.Checkboxes) != 1 || len(ctrlForm.Sliders) != 1 {
		t.Fatalf("unexpected controls form data: %+v, err=%v", ctrlForm, err)
	}

	storeHomePage, err := repo.GetScreen(ctx, "store-home")
	if err != nil {
		t.Fatalf("GetScreen(store-home): %v", err)
	}
	storeHomeBytes := writeOrCompareGolden(t, "store_home.json", storeHomePage)

	var decodedStoreHome snippets.PageResponse
	if err := json.Unmarshal(storeHomeBytes, &decodedStoreHome); err != nil {
		t.Fatalf("round-trip unmarshal store_home.json: %v", err)
	}
	if decodedStoreHome.PageID != "store-home" || len(decodedStoreHome.Snippets) != 4 || decodedStoreHome.FloatingCart == nil {
		t.Fatalf("unexpected decoded store-home screen: %+v", decodedStoreHome)
	}
	if decodedStoreHome.Toolbar == nil || decodedStoreHome.Toolbar.Title == nil || decodedStoreHome.Toolbar.Title.Text != "Abeta Fresh" {
		t.Fatalf("unexpected store-home toolbar: %+v", decodedStoreHome.Toolbar)
	}
	etaBanner, err := snippets.DecodeSnippetData[snippets.BannerSnippetData](decodedStoreHome.Snippets[0])
	if err != nil || decodedStoreHome.Snippets[0].Motion == nil || decodedStoreHome.Snippets[0].Motion.LoopEffect != atoms.MotionLoopPulse {
		t.Fatalf("unexpected store-home ETA banner: %+v, motion=%+v, err=%v", etaBanner, decodedStoreHome.Snippets[0].Motion, err)
	}
	promoCar, err := snippets.DecodeSnippetData[snippets.CarouselSnippetData](decodedStoreHome.Snippets[1])
	if err != nil || len(promoCar.Items) != 3 {
		t.Fatalf("unexpected store-home promo carousel: %+v, err=%v", promoCar, err)
	}
	catTabs, err := snippets.DecodeSnippetData[snippets.TabsSnippetData](decodedStoreHome.Snippets[2])
	if err != nil || len(catTabs.Tabs) != 3 {
		t.Fatalf("unexpected store-home category tabs: %+v, err=%v", catTabs, err)
	}
	firstGrid, err := snippets.DecodeSnippetData[snippets.GridSnippetData](catTabs.Tabs[0].Snippets[0])
	if err != nil || firstGrid.Columns != 2 || len(firstGrid.Items) != 4 {
		t.Fatalf("unexpected fruits grid: %+v, err=%v", firstGrid, err)
	}
	firstProd, err := snippets.DecodeSnippetData[snippets.ProductCardSnippetData](firstGrid.Items[0])
	if err != nil || firstProd.Stepper.Quantity != 1 || firstProd.UnitPriceCents <= 0 {
		t.Fatalf("unexpected first product card: %+v, err=%v", firstProd, err)
	}
	secondProd, err := snippets.DecodeSnippetData[snippets.ProductCardSnippetData](firstGrid.Items[1])
	if err != nil || secondProd.Stepper.Quantity != 0 || secondProd.Stepper.Label != "ADD" {
		t.Fatalf("unexpected second product card: %+v, err=%v", secondProd, err)
	}
	offersAcc, err := snippets.DecodeSnippetData[snippets.AccordionSnippetData](decodedStoreHome.Snippets[3])
	if err != nil || len(offersAcc.Snippets) != 2 {
		t.Fatalf("unexpected store-home offers accordion: %+v, err=%v", offersAcc, err)
	}
	if decodedStoreHome.FloatingCart.CTA.OnClick == nil {
		t.Fatal("expected FloatingCart CTA OnClick trigger")
	}
	checkoutDlg, err := actions.DecodePayload[actions.OpenDialogPayload](*decodedStoreHome.FloatingCart.CTA.OnClick)
	if err != nil || checkoutDlg.Presentation != actions.PresentationBottomSheet || checkoutDlg.PositiveAction == nil {
		t.Fatalf("unexpected FloatingCart checkout dialog: %+v, err=%v", checkoutDlg, err)
	}
	orderAPI, err := actions.DecodePayload[actions.CallAPIPayload](*checkoutDlg.PositiveAction)
	if err != nil || orderAPI.Endpoint != "/api/v1/orders/express" {
		t.Fatalf("unexpected positive action API payload: %+v, err=%v", orderAPI, err)
	}

	interactivePage := canonicalInteractiveScreen()
	interactiveBytes := writeOrCompareGolden(t, "interactive_screen.json", interactivePage)

	var decodedInteractive snippets.PageResponse
	if err := json.Unmarshal(interactiveBytes, &decodedInteractive); err != nil {
		t.Fatalf("round-trip unmarshal interactive_screen.json: %v", err)
	}
	if decodedInteractive.PageID != "interactive-demo" || len(decodedInteractive.Snippets) != 3 {
		t.Fatalf("unexpected decoded interactive screen: %+v", decodedInteractive)
	}
	if decodedInteractive.Snippets[0].SnippetType != snippets.SnippetTypeSearchBar ||
		decodedInteractive.Snippets[1].SnippetType != snippets.SnippetTypeChipGroup ||
		decodedInteractive.Snippets[2].SnippetType != snippets.SnippetTypeSwipeDeck {
		t.Fatalf("unexpected interactive snippet types: %+v", decodedInteractive.Snippets)
	}

	searchData, err := snippets.DecodeSnippetData[snippets.SearchBarSnippetData](decodedInteractive.Snippets[0])
	if err != nil || searchData.SearchID != "discovery_search" || searchData.Query != "compose" || !searchData.Clearable || searchData.OnSubmit == nil || searchData.OnClear == nil || searchData.OnTrailingClick == nil {
		t.Fatalf("unexpected interactive search_bar data: %+v, err=%v", searchData, err)
	}

	chipData, err := snippets.DecodeSnippetData[snippets.ChipGroupSnippetData](decodedInteractive.Snippets[1])
	if err != nil || chipData.GroupID != "discovery_categories" || !chipData.MultiSelect || chipData.Title == nil || len(chipData.Chips) != 3 || !chipData.Chips[0].Selected || chipData.Chips[0].Badge != "12" {
		t.Fatalf("unexpected interactive chip_group data: %+v, err=%v", chipData, err)
	}

	deckData, err := snippets.DecodeSnippetData[snippets.SwipeDeckSnippetData](decodedInteractive.Snippets[2])
	if err != nil || deckData.DeckID != "builders_deck" || len(deckData.Cards) != 2 || deckData.LikeLabel != "Connect" || deckData.NopeLabel != "Skip" || deckData.SuperLikeLabel != "Boost" || deckData.EmptyTitle == nil || deckData.EmptyMessage == nil || deckData.OnUndo == nil {
		t.Fatalf("unexpected interactive swipe_deck data: %+v, err=%v", deckData, err)
	}
	if deckData.Cards[0].CardID != "card_maya_chen" || deckData.Cards[0].DistanceText == nil || len(deckData.Cards[0].Tags) != 2 || deckData.Cards[0].OnSuperLike == nil {
		t.Fatalf("unexpected interactive swipe_deck card[0]: %+v", deckData.Cards[0])
	}
}
