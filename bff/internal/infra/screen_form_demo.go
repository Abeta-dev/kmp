package infra

import (
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

func buildFormDemoScreen() snippets.PageResponse {
	backIcon := backNavigationIcon("back_from_form")

	submitAct := actions.NewCallAPIAction("submit_workspace_form", actions.CallAPIPayload{
		Endpoint: "/api/v1/actions/execute",
		Method:   "POST",
		Body: map[string]any{
			"form_id": "workspace_onboarding",
		},
		OnSuccess: ptrAction(actions.NewShowToastAction("toast_form_saved", actions.ShowToastPayload{
			Message:    "Workspace setup saved!",
			DurationMs: 2500,
			Variant:    "success",
		})),
	}).WithValidations(
		actions.ValidationRule{
			FieldID: "full_name",
			Rule:    "required",
			Message: "Full name is required",
		},
		actions.ValidationRule{
			FieldID: "work_email",
			Rule:    "required",
			Message: "Work email is required",
		},
		actions.ValidationRule{
			FieldID: "work_email",
			Rule:    "regex",
			Value:   `^[^@\s]+@[^@\s]+\.[^@\s]+$`,
			Message: "Enter a valid email address",
		},
	).WithSecondaryActions(
		actions.NewTrackEventAction("track_form_submit", actions.TrackEventPayload{
			EventName:  "workspace_form_submitted",
			Properties: map[string]any{"form_id": "workspace_onboarding"},
		}),
	)

	cancelAct := actions.NewBackPressAction("cancel_workspace_form", actions.BackPressPayload{Steps: 1})
	submitBtn := atoms.NewButton("btn_submit_form", "Submit Setup", "primary", &submitAct)
	cancelBtn := atoms.NewButton("btn_cancel_form", "Cancel", "outline", &cancelAct)
	toolbarTitle := atoms.NewText("Form Demo", theming.TypographyTitleLarge, theming.ColorTextPrimary)

	return snippets.NewPage("form-demo", "Form Demo").
		WithToolbar(snippets.ToolbarSpec{
			Title:          &toolbarTitle,
			NavigationIcon: &backIcon,
			BgColor:        atoms.ColorRef(theming.ColorSurfacePrimary),
		}).
		AddSnippets(
			snippets.NewHeaderSnippet("form_header", atoms.NewText("Workspace Setup", theming.TypographyHeadlineSmall, theming.ColorTextPrimary)).
				WithSubtitle(atoms.NewText("All fields are validated declaratively on submit", theming.TypographyBodyMedium, theming.ColorTextSecondary)).
				WithTrailingTag(atoms.NewTag("VALIDATED", theming.ColorInfoContainer)).
				WithLayout(atoms.LayoutConfig{
					Padding:      atoms.SymmetricSpacing(theming.SpacingLG.Dp(), theming.SpacingMD.Dp()),
					FillMaxWidth: atoms.BoolPtr(true),
				}).
				Build(),
			snippets.NewFormSnippet("form_workspace_setup", "workspace_onboarding", submitBtn).
				WithTitle(atoms.NewText("Organization Details", theming.TypographyTitleMedium, theming.ColorTextPrimary)).
				WithDescription(atoms.NewText("Provide your contact info to initialize your SDUI workspace.", theming.TypographyBodySmall, theming.ColorTextSecondary)).
				AddFields(
					atoms.TextFieldAtom{
						FieldID:      "full_name",
						Label:        "Full Name",
						Placeholder:  "Alex Rivera",
						Required:     true,
						KeyboardType: "text",
					},
					atoms.TextFieldAtom{
						FieldID:      "work_email",
						Label:        "Work Email",
						Placeholder:  "alex@abeta.dev",
						Required:     true,
						Regex:        `^[^@\s]+@[^@\s]+\.[^@\s]+$`,
						KeyboardType: "email",
					},
					atoms.TextFieldAtom{
						FieldID:      "team_size",
						Label:        "Team Size",
						Placeholder:  "12",
						HelperText:   "Optional number between 1 and 9999",
						Required:     false,
						Regex:        `^\d+$`,
						KeyboardType: "number",
					},
				).
				WithCancelButton(cancelBtn).
				WithLayout(cardLayout()).
				Build(),
		).
		Build()
}
