package infra

import (
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

func buildContainersScreen() snippets.PageResponse {
	backIcon := backNavigationIcon("back_from_containers")

	pullRefreshAct := actions.NewRefreshPageAction("pull_refresh_containers", actions.RefreshPagePayload{
		PageID:     "containers",
		ShowLoader: true,
	})
	loadMoreAct := actions.NewCallAPIAction("load_more_containers", actions.CallAPIPayload{
		Endpoint: "/api/v1/screens/containers?cursor=cursor_page_2",
		Method:   "GET",
		OnSuccess: ptrAction(actions.NewShowToastAction("toast_containers_loaded", actions.ShowToastPayload{
			Message: "Loaded next page of containers",
			Variant: "info",
		})),
	})
	bottomSheetAct := actions.NewOpenDialogAction("open_containers_sheet", actions.OpenDialogPayload{
		DialogID:     "container_options_sheet",
		Title:        "Container Layout Options",
		Message:      "Configure responsive columns, spacing, and loading placeholders.",
		Presentation: actions.PresentationBottomSheet,
		Dismissible:  atoms.BoolPtr(true),
		PositiveAction: ptrAction(actions.NewDismissOverlayAction("dismiss_containers_sheet", actions.DismissOverlayPayload{
			OverlayID: "container_options_sheet",
		})),
	})
	toggleStateAct := actions.NewSaveStateAction("save_container_pref", actions.SaveStatePayload{
		Key:   "compact_containers",
		Value: true,
		Scope: "session",
	})
	submitControlsAct := actions.NewCallAPIAction("submit_container_controls", actions.CallAPIPayload{
		Endpoint: "/api/v1/actions/execute",
		Method:   "POST",
		Body:     map[string]any{"form_id": "container_preferences"},
		OnSuccess: ptrAction(actions.NewShowToastAction("toast_controls_saved", actions.ShowToastPayload{
			Message:    "Container preferences updated",
			DurationMs: 2000,
			Variant:    "success",
		})),
	})

	headerAvatar := atoms.NewAvatar("AC", 44, theming.ColorBrandContainer, theming.ColorTextBrand)
	headerAvatar.ID = "avatar_header_lead"
	headerAvatar.ContentDescription = "Abeta Core Team"

	cardAvatar := atoms.NewAvatar("SR", 36, theming.ColorSuccessContainer, theming.ColorSuccess)
	cardAvatar.ID = "avatar_card_author"
	cardAvatar.URL = "https://cdn.abeta.dev/avatars/sr.png"
	cardAvatar.ContentDescription = "Sam Rivera"

	sheetBtn := atoms.NewButton("btn_open_sheet", "Options", "outline", &bottomSheetAct)
	sheetBtn.Size = "sm"

	carouselCard1 := snippets.NewCardSnippet("car_card_1", atoms.NewText("Carousel Card One", theming.TypographyTitleMedium, theming.ColorTextPrimary)).
		WithParentID("containers_carousel").
		WithSubtitle(atoms.NewText("Adaptive layout", theming.TypographyLabelMedium, theming.ColorTextBrand)).
		WithAvatar(cardAvatar).
		WithBadge(atoms.NewTag("NEW", theming.ColorBrandContainer)).
		WithLayout(cardLayout()).
		Build()

	carouselMetric := snippets.NewMetricCardSnippet(
		"car_metric_2",
		atoms.NewText("Render P95", theming.TypographyLabelMedium, theming.ColorTextSecondary),
		atoms.NewText("11.4 ms", theming.TypographyHeadlineSmall, theming.ColorTextPrimary),
	).
		WithParentID("containers_carousel").
		WithDelta(atoms.NewTag("-18%", theming.ColorSuccessContainer)).
		WithLayout(cardLayout()).
		Build()

	gridItem1 := snippets.NewMetricCardSnippet(
		"grid_metric_1",
		atoms.NewText("Active Snippets", theming.TypographyLabelMedium, theming.ColorTextSecondary),
		atoms.NewText("13", theming.TypographyHeadlineMedium, theming.ColorTextPrimary),
	).
		WithParentID("containers_grid").
		WithLayout(cardLayout()).
		Build()

	gridItem2 := snippets.NewMetricCardSnippet(
		"grid_metric_2",
		atoms.NewText("Atoms Ready", theming.TypographyLabelMedium, theming.ColorTextSecondary),
		atoms.NewText("14", theming.TypographyHeadlineMedium, theming.ColorTextPrimary),
	).
		WithParentID("containers_grid").
		WithLayout(cardLayout()).
		Build()

	tabOverviewCard := snippets.NewCardSnippet("tab_overview_card", atoms.NewText("Composable Containers", theming.TypographyTitleMedium, theming.ColorTextPrimary)).
		WithParentID("containers_tabs").
		WithDescription(atoms.NewText("Nest any SnippetEnvelope inside Carousel, Grid, Tabs, or Accordion.", theming.TypographyBodySmall, theming.ColorTextSecondary)).
		WithAvatar(cardAvatar).
		WithPrimaryButton(atoms.NewButton("btn_tab_sheet", "Open Sheet", "primary", &bottomSheetAct)).
		WithLayout(cardLayout()).
		Build()

	tabSkeletonCard := snippets.NewCardSnippet("tab_skeleton_card", atoms.NewText("Skeleton Loading Preview", theming.TypographyTitleMedium, theming.ColorTextPrimary)).
		WithParentID("containers_tabs").
		WithDescription(atoms.NewText("Shimmer placeholders rendered using pure SkeletonAtom contracts.", theming.TypographyBodySmall, theming.ColorTextSecondary)).
		WithSkeletons(
			atoms.NewSkeleton(atoms.SkeletonCircle, 40, 40),
			atoms.NewSkeleton(atoms.SkeletonText, 180, 14),
			atoms.NewSkeleton(atoms.SkeletonRectangle, 280, 96),
		).
		WithLayout(cardLayout()).
		Build()

	accordionRow := snippets.NewActionRowSnippet("acc_row_sheet", atoms.NewText("Preview Bottom Sheet", theming.TypographyTitleSmall, theming.ColorTextPrimary)).
		WithParentID("containers_accordion").
		WithSubtitle(atoms.NewText("Opens an overlay with presentation=bottom_sheet", theming.TypographyBodySmall, theming.ColorTextSecondary)).
		WithLeadingIcon(atoms.NewIcon("layers", 22, theming.ColorBrandPrimary)).
		WithTrailingIcon(atoms.NewIcon("chevron_right", 20, theming.ColorTextTertiary)).
		WithOnClick(bottomSheetAct).
		WithLayout(cardLayout()).
		Build()

	topSkeletonCard := snippets.NewCardSnippet("containers_skeleton_card", atoms.NewText("Async Feed Placeholder", theming.TypographyTitleMedium, theming.ColorTextPrimary)).
		WithSubtitle(atoms.NewText("Avatar & Skeleton atoms", theming.TypographyLabelMedium, theming.ColorTextSecondary)).
		WithAvatar(cardAvatar).
		WithSkeletons(
			atoms.NewSkeleton(atoms.SkeletonCircle, 36, 36),
			atoms.NewSkeleton(atoms.SkeletonText, 220, 16),
			atoms.NewSkeleton(atoms.SkeletonRectangle, 300, 110),
		).
		WithLayout(cardLayout()).
		Build()

	swCompact := atoms.NewSwitch("compact_mode", "Compact Container Spacing", true, &toggleStateAct)
	swCompact.Subtitle = "Reduces inner card padding to 8dp"

	cbTerms := atoms.NewCheckbox("confirm_schema", "Enable Strict Schema Mode", false, &toggleStateAct)
	cbTerms.Subtitle = "Reject unrecognized snippet envelopes in preview"
	cbTerms.Required = true

	slColumns := atoms.NewSlider("grid_density", "Grid Density", 2, 1, 4, &toggleStateAct)
	slColumns.Steps = 3
	slColumns.UnitLabel = "cols"

	toolbarTitle := atoms.NewText("Container Showcase", theming.TypographyTitleLarge, theming.ColorTextPrimary)

	return snippets.NewPage("containers", "Container Showcase").
		WithToolbar(snippets.ToolbarSpec{
			Title:          &toolbarTitle,
			NavigationIcon: &backIcon,
			BgColor:        atoms.ColorRef(theming.ColorSurfacePrimary),
		}).
		WithPullToRefresh(pullRefreshAct).
		WithPagination(snippets.PaginationSpec{
			NextCursor:     "cursor_page_2",
			HasMore:        true,
			LoadMoreAction: &loadMoreAct,
		}).
		AddSnippets(
			snippets.NewHeaderSnippet("containers_header", atoms.NewText("SDUI Layout Containers", theming.TypographyHeadlineSmall, theming.ColorTextPrimary)).
				WithSubtitle(atoms.NewText("Carousel, Grid, Tabs, Accordion, and rich form atoms", theming.TypographyBodyMedium, theming.ColorTextSecondary)).
				WithAvatar(headerAvatar).
				WithTrailingTag(atoms.NewTag("V2 WIRE", theming.ColorInfoContainer)).
				WithTrailingAction(sheetBtn).
				WithLayout(atoms.LayoutConfig{
					Padding:      atoms.SymmetricSpacing(theming.SpacingLG.Dp(), theming.SpacingMD.Dp()),
					FillMaxWidth: atoms.BoolPtr(true),
				}).
				Build(),
			snippets.NewCarouselSnippet("containers_carousel").
				WithTitle(atoms.NewText("Featured Carousel", theming.TypographyTitleMedium, theming.ColorTextPrimary)).
				AddItems(carouselCard1, carouselMetric).
				WithItemWidthDp(240).
				WithSpacingDp(12).
				WithLayout(atoms.LayoutConfig{
					Padding:      atoms.SymmetricSpacing(theming.SpacingLG.Dp(), theming.SpacingSM.Dp()),
					FillMaxWidth: atoms.BoolPtr(true),
				}).
				Build(),
			snippets.NewGridSnippet("containers_grid", 2).
				WithTitle(atoms.NewText("Telemetry Grid", theming.TypographyTitleMedium, theming.ColorTextPrimary)).
				WithSpacingDp(12).
				AddItems(gridItem1, gridItem2).
				WithLayout(atoms.LayoutConfig{
					Padding:      atoms.SymmetricSpacing(theming.SpacingLG.Dp(), theming.SpacingSM.Dp()),
					FillMaxWidth: atoms.BoolPtr(true),
				}).
				Build(),
			snippets.NewTabsSnippet("containers_tabs").
				AddTabs(
					snippets.TabItemSpec{
						ID:       "overview",
						Title:    "Overview",
						Badge:    ptrTag(atoms.NewTag("LIVE", theming.ColorSuccessContainer)),
						Snippets: []snippets.SnippetEnvelope{tabOverviewCard},
					},
					snippets.TabItemSpec{
						ID:       "loading",
						Title:    "Skeletons",
						Snippets: []snippets.SnippetEnvelope{tabSkeletonCard},
					},
				).
				WithSelectedIndex(0).
				WithLayout(cardLayout()).
				Build(),
			snippets.NewAccordionSnippet("containers_accordion", atoms.NewText("Overlay & Sheet Triggers", theming.TypographyTitleMedium, theming.ColorTextPrimary)).
				WithSubtitle(atoms.NewText("Expand to inspect bottom sheet action triggers", theming.TypographyBodySmall, theming.ColorTextSecondary)).
				WithInitiallyExpanded(true).
				AddSnippet(accordionRow).
				WithLayout(cardLayout()).
				Build(),
			topSkeletonCard,
			snippets.NewFormSnippet("containers_controls_form", "container_preferences", atoms.NewButton("btn_save_controls", "Save Preferences", "primary", &submitControlsAct)).
				WithTitle(atoms.NewText("Interactive Atom Controls", theming.TypographyTitleMedium, theming.ColorTextPrimary)).
				WithDescription(atoms.NewText("Switch, Checkbox, and Slider atoms inside form_v1.", theming.TypographyBodySmall, theming.ColorTextSecondary)).
				AddField(atoms.TextFieldAtom{
					FieldID:      "preset_name",
					Label:        "Preset Name",
					Placeholder:  "Compact Dashboard",
					KeyboardType: "text",
				}).
				AddSwitch(swCompact).
				AddCheckbox(cbTerms).
				AddSlider(slColumns).
				WithLayout(cardLayout()).
				Build(),
		).
		WithTracking("page_loaded", map[string]string{"page_id": "containers"}).
		Build()
}
