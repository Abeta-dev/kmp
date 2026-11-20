package infra

import (
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

func buildHomeScreen() snippets.PageResponse {
	navCatalog := actions.NewNavigateAction("nav_catalog", actions.NavigatePayload{
		Route: "/screens/catalog",
	})
	navProfile := actions.NewNavigateAction("nav_profile", actions.NavigatePayload{
		Route: "/screens/profile",
	})
	navForm := actions.NewNavigateAction("nav_form_demo", actions.NavigatePayload{
		Route: "/screens/form-demo",
	})
	refreshAct := actions.NewRefreshPageAction("refresh_home", actions.RefreshPagePayload{
		PageID:     "home",
		ShowLoader: true,
	})
	infoDialog := actions.NewOpenDialogAction("open_home_info", actions.OpenDialogPayload{
		DialogID:    "home_info_dialog",
		Title:       "Abeta SDUI Engine",
		Message:     "Screens are streamed from the Go BFF and rendered natively on KMP.",
		Dismissible: atoms.BoolPtr(true),
		PositiveAction: ptrAction(actions.NewDismissOverlayAction("dismiss_info", actions.DismissOverlayPayload{
			OverlayID: "home_info_dialog",
		})),
	})
	copyAPI := actions.NewCopyToClipboardAction("copy_catalog_url", actions.CopyToClipboardPayload{
		Text:  "/api/v1/catalog/tokens",
		Label: "Catalog Endpoint",
	}).WithSecondaryActions(
		actions.NewShowToastAction("toast_copied", actions.ShowToastPayload{
			Message:    "Endpoint copied to clipboard",
			DurationMs: 2000,
			Variant:    "success",
		}),
	)
	refreshBatch := actions.NewBatchAction("batch_refresh_home", actions.BatchPayload{
		Actions: []actions.ActionTrigger{
			actions.NewTrackEventAction("track_home_refresh", actions.TrackEventPayload{
				EventName:  "home_refresh_clicked",
				Properties: map[string]any{"source": "bottom_bar"},
			}),
			refreshAct,
		},
		StopOnError: true,
	})

	toolbarTitle := atoms.NewText("Abeta Workspace", theming.TypographyTitleLarge, theming.ColorTextPrimary)
	toolbarSub := atoms.NewText("SDUI Control Center", theming.TypographyBodySmall, theming.ColorTextSecondary)
	refreshIcon := atoms.NewIcon("refresh", 22, theming.ColorTextPrimary)
	refreshIcon.ContentDescription = "Refresh screen"
	refreshIcon.OnClick = &refreshAct
	infoIcon := atoms.NewIcon("info", 22, theming.ColorTextSecondary)
	infoIcon.ContentDescription = "About SDUI"
	infoIcon.OnClick = &infoDialog

	headerBtn := atoms.NewButton("btn_home_catalog", "Catalog", "outline", &navCatalog)
	headerBtn.Size = "sm"

	bannerLayout := cardLayout()
	bannerLayout.BgColor = atoms.ColorRef(theming.ColorBrandContainer)
	bannerCTA := atoms.NewButton("btn_open_form", "Open Form Demo", "primary", &navForm)
	bannerCTA.Size = "sm"

	metricClick := actions.NewShowToastAction("toast_uptime", actions.ShowToastPayload{
		Message:    "All BFF services healthy (99.98% SLA)",
		DurationMs: 2500,
		Variant:    "info",
	})

	cardPrimary := atoms.NewButton("btn_explore_tokens", "Explore Tokens", "primary", &navCatalog)
	cardSecondary := atoms.NewButton("btn_copy_endpoint", "Copy Endpoint", "secondary", &copyAPI)

	bottomBtn := atoms.NewButton("btn_bottom_refresh", "Refresh Dashboard", "primary", &refreshBatch)
	helperText := atoms.NewText("Synced via Abeta BFF", theming.TypographyCaption, theming.ColorTextTertiary)

	return snippets.NewPage("home", "Abeta Workspace").
		WithToolbar(snippets.ToolbarSpec{
			Title:           &toolbarTitle,
			Subtitle:        &toolbarSub,
			TrailingActions: []atoms.IconAtom{refreshIcon, infoIcon},
			BgColor:         atoms.ColorRef(theming.ColorSurfacePrimary),
		}).
		AddPageAction(actions.NewTrackEventAction("track_home_view", actions.TrackEventPayload{
			EventName:  "screen_viewed",
			Properties: map[string]any{"screen_id": "home"},
		})).
		AddSnippets(
			snippets.NewHeaderSnippet("home_header", atoms.NewText("Welcome back, Builder", theming.TypographyHeadlineSmall, theming.ColorTextPrimary)).
				WithSubtitle(atoms.NewText("Server-driven UI powered by atomic contracts", theming.TypographyBodyMedium, theming.ColorTextSecondary)).
				WithLeadingIcon(atoms.NewIcon("sparkles", 24, theming.ColorBrandPrimary)).
				WithTrailingTag(atoms.NewTag("LIVE", theming.ColorSuccessContainer)).
				WithTrailingAction(headerBtn).
				WithLayout(atoms.LayoutConfig{
					Padding:      atoms.SymmetricSpacing(theming.SpacingLG.Dp(), theming.SpacingMD.Dp()),
					FillMaxWidth: atoms.BoolPtr(true),
				}).
				WithTracking("snippet_impression", map[string]any{"snippet_id": "home_header"}).
				Build(),
			snippets.NewBannerSnippet("home_promo_banner", atoms.NewText("Zero-Release UI Updates", theming.TypographyTitleMedium, theming.ColorTextPrimary)).
				WithBody(atoms.NewText("Ship layout, theming, and action workflows instantly across Android, iOS, and Desktop.", theming.TypographyBodySmall, theming.ColorTextSecondary)).
				WithIcon(atoms.NewIcon("rocket", 24, theming.ColorBrandPrimary)).
				WithTag(atoms.NewTag("v1.0", theming.ColorSurfacePrimary)).
				WithCTA(bannerCTA).
				WithLayout(bannerLayout).
				Build(),
			snippets.NewMetricCardSnippet(
				"home_metric_uptime",
				atoms.NewText("API Availability", theming.TypographyLabelMedium, theming.ColorTextSecondary),
				atoms.NewText("99.98%", theming.TypographyHeadlineMedium, theming.ColorTextPrimary),
			).
				WithDelta(atoms.NewTag("+0.04%", theming.ColorSuccessContainer)).
				WithIcon(atoms.NewIcon("activity", 20, theming.ColorSuccess)).
				WithProgress(atoms.ProgressAtom{
					Kind:     "linear",
					Progress: atoms.Float64Ptr(0.9998),
					Label:    "30-day SLO target",
					Color:    atoms.ColorRef(theming.ColorSuccess),
				}).
				WithOnClick(metricClick).
				WithLayout(cardLayout()).
				Build(),
			snippets.NewDividerSnippet("home_div_1", atoms.DividerAtom{
				ThicknessDp:  atoms.Float64Ptr(1),
				Color:        atoms.ColorRef(theming.ColorDivider),
				InsetStartDp: atoms.IntPtr(theming.SpacingLG.Dp()),
			}).Build(),
			snippets.NewCardSnippet("home_featured_card", atoms.NewText("Atomic Design System", theming.TypographyTitleLarge, theming.ColorTextPrimary)).
				WithSubtitle(atoms.NewText("Tokens · Atoms · Snippets", theming.TypographyLabelMedium, theming.ColorTextBrand)).
				WithDescription(atoms.NewText("Consistent semantic theming and strict action decoupling across every platform.", theming.TypographyBodyMedium, theming.ColorTextSecondary)).
				WithMedia(atoms.ImageAtom{
					URL:                "https://cdn.abeta.dev/sdui/atomic-system-preview.png",
					ContentDescription: "Atomic design architecture diagram",
					HeightDp:           atoms.IntPtr(140),
					ScaleMode:          "crop",
					CornerRadius:       atoms.UniformRadius(theming.RadiusSM.Dp()),
				}).
				WithBadge(atoms.NewTag("STABLE", theming.ColorInfoContainer)).
				WithPrimaryButton(cardPrimary).
				WithSecondaryButton(cardSecondary).
				WithLayout(cardLayout()).
				Build(),
			snippets.NewSpacerSnippet("home_spacer_1", atoms.SpacerAtom{
				HeightDp: atoms.IntPtr(theming.SpacingMD.Dp()),
			}).Build(),
			snippets.NewActionRowSnippet("home_row_profile", atoms.NewText("Account & Preferences", theming.TypographyTitleSmall, theming.ColorTextPrimary)).
				WithSubtitle(atoms.NewText("Manage workspace profile and synced state", theming.TypographyBodySmall, theming.ColorTextSecondary)).
				WithLeadingIcon(atoms.NewIcon("person", 22, theming.ColorBrandPrimary)).
				WithTrailingTag(atoms.NewTag("PRO", theming.ColorBrandContainer)).
				WithTrailingIcon(atoms.NewIcon("chevron_right", 20, theming.ColorTextTertiary)).
				WithOnClick(navProfile).
				WithLayout(cardLayout()).
				Build(),
			snippets.NewActionRowSnippet("home_row_form", atoms.NewText("Interactive Onboarding Form", theming.TypographyTitleSmall, theming.ColorTextPrimary)).
				WithSubtitle(atoms.NewText("Test declarative client and server validations", theming.TypographyBodySmall, theming.ColorTextSecondary)).
				WithLeadingIcon(atoms.NewIcon("edit_note", 22, theming.ColorAccentPrimary)).
				WithTrailingIcon(atoms.NewIcon("chevron_right", 20, theming.ColorTextTertiary)).
				WithOnClick(navForm).
				WithLayout(cardLayout()).
				Build(),
		).
		WithBottomBar(snippets.BottomBarSpec{
			PrimaryButton: &bottomBtn,
			HelperText:    &helperText,
			Layout: &atoms.LayoutConfig{
				Padding:      atoms.UniformSpacing(theming.SpacingMD.Dp()),
				BgColor:      atoms.ColorRef(theming.ColorSurfacePrimary),
				FillMaxWidth: atoms.BoolPtr(true),
			},
		}).
		WithTracking("page_loaded", map[string]any{"page_id": "home"}).
		Build()
}
