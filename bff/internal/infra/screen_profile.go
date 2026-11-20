package infra

import (
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

func buildProfileScreen() snippets.PageResponse {
	backIcon := backNavigationIcon("back_from_profile")

	copyWS := actions.NewCopyToClipboardAction("copy_workspace_id", actions.CopyToClipboardPayload{
		Text:  "ws_abeta_01",
		Label: "Workspace ID",
	})
	syncBatch := actions.NewBatchAction("batch_sync_profile", actions.BatchPayload{
		Actions: []actions.ActionTrigger{
			actions.NewSaveStateAction("save_last_sync", actions.SaveStatePayload{
				Key:   "profile_synced",
				Value: true,
				Scope: "session",
			}),
			actions.NewShowToastAction("toast_profile_synced", actions.ShowToastPayload{
				Message:    "Profile preferences synced",
				DurationMs: 2000,
				Variant:    "success",
			}),
		},
		StopOnError: true,
	})
	signOutDialog := actions.NewOpenDialogAction("open_signout_dialog", actions.OpenDialogPayload{
		DialogID:    "signout_confirm",
		Title:       "Sign out of workspace?",
		Message:     "Your local session state will be cleared.",
		Dismissible: atoms.BoolPtr(true),
		PositiveAction: ptrAction(actions.NewBatchAction("confirm_signout", actions.BatchPayload{
			Actions: []actions.ActionTrigger{
				actions.NewDismissOverlayAction("dismiss_signout", actions.DismissOverlayPayload{OverlayID: "signout_confirm"}),
				actions.NewNavigateAction("nav_home_after_signout", actions.NavigatePayload{
					Route:          "/screens/home",
					ClearBackStack: true,
				}),
			},
		})),
		NegativeAction: ptrAction(actions.NewDismissOverlayAction("cancel_signout", actions.DismissOverlayPayload{
			OverlayID: "signout_confirm",
		})),
	})

	toolbarTitle := atoms.NewText("Profile", theming.TypographyTitleLarge, theming.ColorTextPrimary)

	return snippets.NewPage("profile", "Profile").
		WithToolbar(snippets.ToolbarSpec{
			Title:          &toolbarTitle,
			NavigationIcon: &backIcon,
			BgColor:        atoms.ColorRef(theming.ColorSurfacePrimary),
		}).
		AddSnippets(
			snippets.NewHeaderSnippet("profile_header", atoms.NewText("Alex Rivera", theming.TypographyHeadlineSmall, theming.ColorTextPrimary)).
				WithSubtitle(atoms.NewText("Principal Mobile Architect", theming.TypographyBodyMedium, theming.ColorTextSecondary)).
				WithLeadingIcon(atoms.NewIcon("account_circle", 32, theming.ColorBrandPrimary)).
				WithTrailingTag(atoms.NewTag("VERIFIED", theming.ColorSuccessContainer)).
				WithLayout(cardLayout()).
				Build(),
			snippets.NewActionRowSnippet("profile_row_workspace", atoms.NewText("Workspace ID", theming.TypographyTitleSmall, theming.ColorTextPrimary)).
				WithSubtitle(atoms.NewText("ws_abeta_01", theming.TypographyBodySmall, theming.ColorTextSecondary)).
				WithLeadingIcon(atoms.NewIcon("badge", 22, theming.ColorBrandPrimary)).
				WithTrailingIcon(atoms.NewIcon("content_copy", 20, theming.ColorTextTertiary)).
				WithOnClick(copyWS).
				WithLayout(cardLayout()).
				Build(),
			snippets.NewActionRowSnippet("profile_row_sync", atoms.NewText("Force Sync State", theming.TypographyTitleSmall, theming.ColorTextPrimary)).
				WithSubtitle(atoms.NewText("Persist session preferences and show confirmation", theming.TypographyBodySmall, theming.ColorTextSecondary)).
				WithLeadingIcon(atoms.NewIcon("sync", 22, theming.ColorInfo)).
				WithTrailingIcon(atoms.NewIcon("chevron_right", 20, theming.ColorTextTertiary)).
				WithOnClick(syncBatch).
				WithLayout(cardLayout()).
				Build(),
			snippets.NewActionRowSnippet("profile_row_signout", atoms.NewText("Sign Out", theming.TypographyTitleSmall, theming.ColorError)).
				WithLeadingIcon(atoms.NewIcon("logout", 22, theming.ColorError)).
				WithOnClick(signOutDialog).
				WithLayout(cardLayout()).
				Build(),
		).
		Build()
}
