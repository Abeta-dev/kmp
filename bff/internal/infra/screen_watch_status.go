package infra

import (
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

func buildWatchStatusScreen() snippets.PageResponse {
	etaProgress := atoms.ProgressAtom{
		Kind:     "linear",
		Progress: atoms.Float64Ptr(0.78),
		Label:    "Arriving in 8 mins",
		Color:    atoms.ColorRef(theming.ColorBrandPrimary),
	}
	reorderAct := actions.NewCallAPIAction("watch_express_reorder", actions.CallAPIPayload{
		Endpoint: "/api/v1/orders/express",
		Method:   "POST",
		Body:     map[string]any{"source": "watch_companion", "delivery_eta_mins": 8},
	}).WithSecondaryActions(
		actions.NewTriggerFeedbackAction("watch_haptic_confirm", actions.TriggerFeedbackPayload{
			Effect: actions.FeedbackHapticTick,
		}),
	)
	shareAct := actions.NewShareAction("watch_share_eta", actions.SharePayload{
		Title: "Abeta Fresh Delivery",
		Text:  "My Abeta Fresh express order arrives in 8 minutes!",
		URL:   "https://app.abeta.dev/screens/store-home",
	})
	helpUrlAct := actions.NewOpenURLAction("watch_open_support", actions.OpenURLPayload{
		URL: "https://abeta.dev/docs",
	})

	btnReorder := atoms.NewButton("btn_watch_reorder", "1-Tap Reorder", "primary", &reorderAct)
	btnReorder.Size = "sm"
	btnShare := atoms.NewButton("btn_watch_share", "Share ETA", "outline", &shareAct)
	btnShare.Size = "sm"

	card := snippets.NewCardSnippet(
		"watch_active_order",
		atoms.NewText("Rider En Route · 8 min", theming.TypographyTitleSmall, theming.ColorTextPrimary),
	).
		WithSubtitle(atoms.NewText("Abeta Fresh · Bag #AF-409", theming.TypographyBodySmall, theming.ColorTextSecondary)).
		WithBadge(atoms.NewTag("LIVE", theming.ColorSuccessContainer)).
		WithPrimaryButton(btnReorder).
		WithSecondaryButton(btnShare).
		Build()

	metric := snippets.NewMetricCardSnippet(
		"watch_eta_metric",
		atoms.NewText("Express SLA", theming.TypographyLabelSmall, theming.ColorTextSecondary),
		atoms.NewText("8 mins", theming.TypographyTitleMedium, theming.ColorBrandPrimary),
	).
		WithProgress(etaProgress).
		WithOnClick(helpUrlAct).
		Build()

	return snippets.NewPage("watch-status", "Abeta Watch").
		WithAdaptiveLayout(snippets.AdaptivePageSpec{
			LayoutMode: snippets.LayoutModeSingleColumn,
			MaxWidthDp: atoms.IntPtr(280),
		}).
		AddSnippets(metric, card).
		WithTracking("watch_screen_viewed", map[string]any{"page_id": "watch-status"}).
		Build()
}
