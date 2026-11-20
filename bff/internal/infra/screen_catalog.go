package infra

import (
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

func buildCatalogScreen() snippets.PageResponse {
	backIcon := backNavigationIcon("back_from_catalog")

	inspectAPI := actions.NewCallAPIAction("call_catalog_tokens", actions.CallAPIPayload{
		Endpoint: "/api/v1/catalog/tokens",
		Method:   "GET",
		OnSuccess: ptrAction(actions.NewShowToastAction("toast_catalog_ok", actions.ShowToastPayload{
			Message: "Token catalog loaded",
			Variant: "success",
		})),
	})
	saveBookmark := actions.NewSaveStateAction("save_catalog_bookmark", actions.SaveStatePayload{
		Key:   "bookmarked_catalog",
		Value: true,
		Scope: "disk",
	})
	deleteBookmark := actions.NewDeleteStateAction("delete_catalog_bookmark", actions.DeleteStatePayload{
		Key:   "bookmarked_catalog",
		Scope: "disk",
	})
	patchSnippet := actions.NewUpdateSnippetsAction("patch_catalog_preview", actions.UpdateSnippetsPayload{
		Operation:       "append",
		TargetSnippetID: "catalog_empty_preview",
	})

	toolbarTitle := atoms.NewText("Component Catalog", theming.TypographyTitleLarge, theming.ColorTextPrimary)
	primaryBtn := atoms.NewButton("btn_inspect_tokens", "Inspect Tokens", "primary", &inspectAPI)
	secondaryBtn := atoms.NewButton("btn_save_bookmark", "Bookmark", "outline", &saveBookmark)
	emptyBtn := atoms.NewButton("btn_patch_preview", "Patch Preview", "secondary", &patchSnippet)

	return snippets.NewPage("catalog", "Component Catalog").
		WithToolbar(snippets.ToolbarSpec{
			Title:          &toolbarTitle,
			NavigationIcon: &backIcon,
			BgColor:        atoms.ColorRef(theming.ColorSurfacePrimary),
		}).
		AddSnippets(
			snippets.NewHeaderSnippet("catalog_header", atoms.NewText("Token & Snippet Showcase", theming.TypographyHeadlineSmall, theming.ColorTextPrimary)).
				WithSubtitle(atoms.NewText("Live contracts served from pkg/sdui", theming.TypographyBodyMedium, theming.ColorTextSecondary)).
				WithTrailingTag(atoms.NewTag("9 SNIPPETS", theming.ColorBrandContainer)).
				WithLayout(atoms.LayoutConfig{
					Padding:      atoms.SymmetricSpacing(theming.SpacingLG.Dp(), theming.SpacingMD.Dp()),
					FillMaxWidth: atoms.BoolPtr(true),
				}).
				Build(),
			snippets.NewMetricCardSnippet(
				"catalog_metric_tokens",
				atoms.NewText("Registered Color Tokens", theming.TypographyLabelMedium, theming.ColorTextSecondary),
				atoms.NewText("30", theming.TypographyHeadlineMedium, theming.ColorTextPrimary),
			).
				WithDelta(atoms.NewTag("100% synced", theming.ColorSuccessContainer)).
				WithProgress(atoms.ProgressAtom{
					Kind:     "linear",
					Progress: atoms.Float64Ptr(1.0),
					Color:    atoms.ColorRef(theming.ColorBrandPrimary),
				}).
				WithLayout(cardLayout()).
				Build(),
			snippets.NewCardSnippet("catalog_card_atoms", atoms.NewText("Atomic Primitives", theming.TypographyTitleMedium, theming.ColorTextPrimary)).
				WithDescription(atoms.NewText("Text, Icon, Tag, Button, Image, TextField, Divider, Spacer, and Progress atoms.", theming.TypographyBodyMedium, theming.ColorTextSecondary)).
				WithPrimaryButton(primaryBtn).
				WithSecondaryButton(secondaryBtn).
				WithLayout(cardLayout()).
				Build(),
			snippets.NewEmptyStateSnippet("catalog_empty_preview", atoms.NewText("No Custom Overrides Yet", theming.TypographyTitleMedium, theming.ColorTextPrimary)).
				WithMessage(atoms.NewText("Patch this section dynamically with update_snippets to preview custom overrides.", theming.TypographyBodySmall, theming.ColorTextSecondary)).
				WithIcon(atoms.NewIcon("layers", 36, theming.ColorTextTertiary)).
				WithActionButton(emptyBtn).
				WithLayout(cardLayout()).
				Build(),
			snippets.NewDividerSnippet("catalog_div_1", atoms.DividerAtom{
				ThicknessDp: atoms.Float64Ptr(1),
				Color:       atoms.ColorRef(theming.ColorDivider),
			}).Build(),
			snippets.NewActionRowSnippet("catalog_row_clear", atoms.NewText("Reset Cached Preferences", theming.TypographyTitleSmall, theming.ColorError)).
				WithSubtitle(atoms.NewText("Clears bookmarked_catalog from local state", theming.TypographyBodySmall, theming.ColorTextSecondary)).
				WithLeadingIcon(atoms.NewIcon("delete_outline", 22, theming.ColorError)).
				WithOnClick(deleteBookmark).
				WithLayout(cardLayout()).
				Build(),
		).
		Build()
}
