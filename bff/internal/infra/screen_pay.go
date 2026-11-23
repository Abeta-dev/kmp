package infra

import (
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

const (
	payRecipientRegex = `^(@[a-zA-Z0-9._-]{2,}|[^@\s]+@[^@\s]+\.[^@\s]+)$`
	payAmountRegex    = `^\d+(\.\d{1,2})?$`
)

func payCenteredAdaptive() snippets.AdaptivePageSpec {
	return snippets.AdaptivePageSpec{
		LayoutMode: snippets.LayoutModeCentered,
		MaxWidthDp: atoms.IntPtr(1120),
	}
}

func payToolbar(title, subtitle, backActionID string, trailing ...atoms.IconAtom) snippets.ToolbarSpec {
	t := atoms.NewText(title, theming.TypographyTitleLarge, theming.ColorTextPrimary)
	sub := atoms.NewText(subtitle, theming.TypographyBodySmall, theming.ColorTextSecondary)
	back := backNavigationIcon(backActionID)
	return snippets.ToolbarSpec{
		Title:           &t,
		Subtitle:        &sub,
		NavigationIcon:  &back,
		TrailingActions: trailing,
		BgColor:         atoms.ColorRef(theming.ColorSurfacePrimary),
	}
}

func paySearchLayout() atoms.LayoutConfig {
	return atoms.LayoutConfig{
		Padding:      atoms.SymmetricSpacing(theming.SpacingMD.Dp(), 6),
		Margin:       atoms.SymmetricSpacing(theming.SpacingLG.Dp(), theming.SpacingXS.Dp()),
		BgColor:      atoms.ColorRef(theming.ColorSurfacePrimary),
		CornerRadius: atoms.UniformRadius(theming.RadiusMD.Dp()),
		Border: &atoms.BorderSpec{
			WidthDp: 1,
			Color:   *atoms.ColorRef(theming.ColorBorderDefault),
		},
		FillMaxWidth: atoms.BoolPtr(true),
	}
}

func payMarqueeLayout() atoms.LayoutConfig {
	return atoms.LayoutConfig{
		Padding:      atoms.SymmetricSpacing(theming.SpacingMD.Dp(), theming.SpacingSM.Dp()),
		Margin:       atoms.SymmetricSpacing(theming.SpacingLG.Dp(), theming.SpacingXS.Dp()),
		BgColor:      atoms.ColorRef(theming.ColorSurfaceSecondary),
		CornerRadius: atoms.UniformRadius(theming.RadiusMD.Dp()),
		FillMaxWidth: atoms.BoolPtr(true),
	}
}

func payHorizontalLayout(verticalDp int) atoms.LayoutConfig {
	return atoms.LayoutConfig{
		Padding:      atoms.SymmetricSpacing(theming.SpacingLG.Dp(), verticalDp),
		FillMaxWidth: atoms.BoolPtr(true),
	}
}

func payActionRow(id, title, subtitle, iconName string, iconColor theming.ColorToken, tagLabel string, tagBg theming.ColorToken, onClick actions.ActionTrigger) snippets.SnippetEnvelope {
	return snippets.NewActionRowSnippet(id, atoms.NewText(title, theming.TypographyTitleSmall, theming.ColorTextPrimary)).
		WithSubtitle(atoms.NewText(subtitle, theming.TypographyBodySmall, theming.ColorTextSecondary)).
		WithLeadingIcon(atoms.NewIcon(iconName, 20, iconColor)).
		WithTrailingTag(atoms.NewTag(tagLabel, tagBg)).
		WithTrailingIcon(atoms.NewIcon("chevron_right", 18, theming.ColorTextTertiary)).
		WithOnClick(onClick).
		WithLayout(cardLayout()).
		Build()
}

func payPerksMarqueeSnippet() snippets.SnippetEnvelope {
	instantToast := actions.NewShowToastAction("tap_pay_instant", actions.ShowToastPayload{
		Message:    "Instant P2P transfers settle in < 2s",
		DurationMs: 2200,
		Variant:    "info",
	})
	cashbackToast := actions.NewShowToastAction("tap_pay_cashback", actions.ShowToastPayload{
		Message:    "5% cashback active on Abeta Metal!",
		DurationMs: 2200,
		Variant:    "success",
	})
	instantIcon := atoms.NewIcon("star", 16, theming.ColorBrandPrimary)
	cashbackIcon := atoms.NewIcon("check", 16, theming.ColorSuccess)
	fxIcon := atoms.NewIcon("info", 16, theming.ColorInfo)

	return snippets.NewMarqueeStripSnippet(
		"pay_perks_marquee",
		"pay_live_perks",
		snippets.MarqueeItem{
			ID:      "pay_mq_instant",
			Text:    "Zero-fee instant P2P transfers",
			Icon:    &instantIcon,
			Badge:   ptrTag(atoms.NewTag("INSTANT", theming.ColorSuccessContainer)),
			OnClick: &instantToast,
		},
		snippets.MarqueeItem{
			ID:      "pay_mq_cashback",
			Text:    "5% cashback on Metal Card",
			Icon:    &cashbackIcon,
			Badge:   ptrTag(atoms.NewTag("5% BACK", theming.ColorBrandContainer)),
			OnClick: &cashbackToast,
		},
		snippets.MarqueeItem{
			ID:    "pay_mq_fx",
			Text:  "0% international FX markup",
			Icon:  &fxIcon,
			Badge: ptrTag(atoms.NewTag("GLOBAL", theming.ColorInfoContainer)),
		},
	).
		WithVelocityDpPerSec(48).
		WithSeparator("•").
		WithLayout(payMarqueeLayout()).
		WithMotion(atoms.MotionSpec{
			Enter:         atoms.MotionTransitionFadeIn,
			DurationToken: atoms.MotionDurationFast,
		}).
		Build()
}

func payCashbackNudgeSnippet() snippets.SnippetEnvelope {
	badge := atoms.NewTag("5% CASHBACK", theming.ColorWarningContainer)
	badge.Motion = &atoms.MotionSpec{LoopEffect: atoms.MotionLoopWiggle}

	countdown := atoms.NewCountdown("pay_cashback_cd", 480, "Boost ends in")
	countdown.UrgentThresholdSeconds = 60
	countdown.Motion = &atoms.MotionSpec{LoopEffect: atoms.MotionLoopPulse}

	navTransfer := actions.NewNavigateAction("nav_pay_nudge_transfer", actions.NavigatePayload{Route: "pay-transfer"})
	cta := atoms.NewButton("btn_pay_nudge_send", "Send Now", "primary", &navTransfer)
	cta.Size = "sm"

	dismissToast := actions.NewShowToastAction("dismiss_pay_nudge", actions.ShowToastPayload{
		Message:    "Cashback reminder dismissed",
		DurationMs: 2200,
		Variant:    "info",
	})

	return snippets.NewNudgeBarSnippet(
		"pay_cashback_nudge",
		"pay_zero_fee_cashback",
		atoms.NewText("Zero-Fee 5% Cashback Window", theming.TypographyTitleSmall, theming.ColorTextPrimary),
	).
		WithSubtitle(atoms.NewText("Send P2P or pay utilities before boost closes", theming.TypographyBodySmall, theming.ColorTextSecondary)).
		WithIcon(atoms.NewIcon("notifications", 20, theming.ColorBrandPrimary)).
		WithBadge(badge).
		WithCountdown(countdown).
		WithProgress(0.72).
		WithCTA(cta).
		WithDismissible(true).
		WithPointerEdge(snippets.PointerEdgeBottom).
		WithOnDismiss(dismissToast).
		WithLayout(cardLayout()).
		WithMotion(atoms.MotionSpec{
			Enter:         atoms.MotionTransitionSlideDown,
			DurationToken: atoms.MotionDurationFast,
		}).
		Build()
}

func payMetalVirtualCardSnippet() snippets.SnippetEnvelope {
	brandTag := atoms.NewTag("ABETA METAL", theming.ColorBrandContainer)
	brandTag.Motion = &atoms.MotionSpec{LoopEffect: atoms.MotionLoopPulse}

	navSend := actions.NewNavigateAction("nav_metal_send", actions.NavigatePayload{Route: "pay-transfer"})
	sendBtn := atoms.NewButton("btn_metal_send", "Send Money", "primary", &navSend)
	sendBtn.Size = "sm"

	navCards := actions.NewNavigateAction("nav_metal_controls", actions.NavigatePayload{Route: "pay-cards"})
	controlsBtn := atoms.NewButton("btn_metal_controls", "Card Controls", "outline", &navCards)
	controlsBtn.Size = "sm"

	fullWidth := atoms.LayoutConfig{FillMaxWidth: atoms.BoolPtr(true)}

	headerRow := snippets.HStack(
		theming.SpacingSM.Dp(),
		snippets.TagNode(brandTag).WithNodeID("pay_metal_brand_tag"),
		snippets.TagNode(atoms.NewTag("VIRTUAL · ACTIVE", theming.ColorSuccessContainer)).WithNodeID("pay_metal_virtual_tag"),
	).WithNodeID("pay_metal_hdr_row").
		WithArrangement(snippets.ComposedAlignSpaceBetween).
		WithAlignment("", snippets.ComposedAlignCenter).
		WithLayout(fullWidth)

	footerRow := snippets.HStack(
		theming.SpacingSM.Dp(),
		snippets.ButtonNode(sendBtn).WithNodeID("pay_metal_send_btn"),
		snippets.ButtonNode(controlsBtn).WithNodeID("pay_metal_controls_btn"),
	).WithNodeID("pay_metal_footer_row").
		WithArrangement(snippets.ComposedAlignSpaceBetween).
		WithAlignment("", snippets.ComposedAlignCenter).
		WithLayout(fullWidth)

	root := snippets.CardNode(
		cardLayout(),
		snippets.VStack(
			theming.SpacingSM.Dp(),
			headerRow,
			snippets.TextNode(atoms.NewText("••••  ••••  ••••  4092", theming.TypographyTitleMedium, theming.ColorTextPrimary)).WithNodeID("pay_metal_number"),
			snippets.RollingCounterNode("$2,480.50", theming.TypographyHeadlineSmall, theming.ColorBrandPrimary).WithNodeID("pay_metal_balance"),
			snippets.TextNode(atoms.NewText("ALEX RIVERA · EXP 09/29 · CVV •••", theming.TypographyLabelMedium, theming.ColorTextSecondary)).WithNodeID("pay_metal_holder"),
			snippets.DividerNode(atoms.DividerAtom{
				ThicknessDp: atoms.Float64Ptr(1),
				Color:       atoms.ColorRef(theming.ColorDivider),
			}).WithNodeID("pay_metal_divider"),
			footerRow,
		).WithNodeID("pay_metal_col").WithLayout(fullWidth),
	).WithNodeID("pay_metal_virtual_card_root").
		WithMotion(atoms.MotionSpec{LoopEffect: atoms.MotionLoopGlowRing})

	return snippets.NewComposedBlockSnippet("pay_metal_card_block", "pay_metal_virtual_card", root).
		WithRecipeName("metal_virtual_card").
		WithMotion(atoms.MotionSpec{
			Enter:         atoms.MotionTransitionSlideUp,
			DurationToken: atoms.MotionDurationNormal,
		}).
		Build()
}

func payBillPayCarouselSnippet() snippets.SnippetEnvelope {
	bills := []struct {
		id       string
		title    string
		subtitle string
	}{
		{id: "bill_power", title: "City Power & Grid", subtitle: "Auto-pay due in 3 days · $68.40 · 5% back"},
		{id: "bill_fiber", title: "HyperFiber Gigabit", subtitle: "Monthly broadband · $59.00 · Zero fee"},
		{id: "bill_mobile", title: "Abeta Wireless 5G", subtitle: "Unlimited plan · $45.00 · Instant top-up"},
	}

	items := make([]snippets.SnippetEnvelope, 0, len(bills))
	for _, b := range bills {
		card := snippets.NewCardSnippet(b.id, atoms.NewText(b.title, theming.TypographyTitleSmall, theming.ColorTextPrimary)).
			WithParentID("pay_bill_carousel").
			WithSubtitle(atoms.NewText(b.subtitle, theming.TypographyBodySmall, theming.ColorTextSecondary)).
			WithBadge(atoms.NewTag("BILL PAY", theming.ColorBrandContainer)).
			WithOnClick(actions.NewNavigateAction("open_"+b.id, actions.NavigatePayload{Route: "pay-transfer"})).
			WithLayout(compactCardLayout()).
			Build()
		items = append(items, card)
	}

	return snippets.NewCarouselSnippet("pay_bill_carousel").
		WithTitle(atoms.NewText("Bill Pay & Utilities", theming.TypographyTitleMedium, theming.ColorTextPrimary)).
		AddItems(items...).
		WithItemWidthDp(240).
		WithItemWidthByWidth(snippets.ResponsiveIntSpec{
			Compact:  atoms.IntPtr(220),
			Medium:   atoms.IntPtr(260),
			Expanded: atoms.IntPtr(280),
		}).
		WithSpacingDp(12).
		WithLayout(payHorizontalLayout(theming.SpacingSM.Dp())).
		Build()
}

func buildPayHomeScreen() snippets.PageResponse {
	navCards := actions.NewNavigateAction("nav_pay_cards_toolbar", actions.NavigatePayload{Route: "pay-cards"})
	cardsIcon := atoms.NewIcon("settings", 22, theming.ColorBrandPrimary)
	cardsIcon.ContentDescription = "Open Smart Card Controls"
	cardsIcon.OnClick = &navCards

	searchBar := snippets.NewSearchBarSnippet("pay_search_bar", "pay_search", "Search contacts, @handles & bills...").
		WithLeadingIcon(atoms.NewIcon("search", 18, theming.ColorTextTertiary)).
		WithSubmitLabel("Search").
		WithClearable(true).
		WithOnSubmit(actions.NewNavigateAction("submit_pay_search", actions.NavigatePayload{Route: "pay-home?q={{query}}"})).
		WithOnClear(actions.NewNavigateAction("clear_pay_search", actions.NavigatePayload{Route: "pay-home"})).
		WithLayout(paySearchLayout()).
		Build()

	quickChips := snippets.NewChipGroupSnippet(
		"pay_quick_chips",
		"pay_filters",
		snippets.FilterChipItem{ID: "all", Label: "Wallet", Selected: true, OnClick: ptrAction(actions.NewNavigateAction("chip_pay_all", actions.NavigatePayload{Route: "pay-home"}))},
		snippets.FilterChipItem{ID: "send", Label: "Send Money", OnClick: ptrAction(actions.NewNavigateAction("chip_pay_send", actions.NavigatePayload{Route: "pay-transfer"}))},
		snippets.FilterChipItem{ID: "cards", Label: "Smart Cards", OnClick: ptrAction(actions.NewNavigateAction("chip_pay_cards", actions.NavigatePayload{Route: "pay-cards"}))},
		snippets.FilterChipItem{ID: "bills", Label: "Bill Pay", OnClick: ptrAction(actions.NewNavigateAction("chip_pay_bills", actions.NavigatePayload{Route: "pay-home?filter=bills"}))},
		snippets.FilterChipItem{ID: "receipts", Label: "Receipt #PAY-3081", OnClick: ptrAction(actions.NewNavigateAction("chip_pay_rcpt", actions.NavigatePayload{Route: "pay-receipt"}))},
	).WithLayout(payHorizontalLayout(theming.SpacingXS.Dp())).Build()

	balanceMetric := snippets.NewMetricCardSnippet(
		"pay_balance_metric",
		atoms.NewText("Available Wallet Balance", theming.TypographyLabelMedium, theming.ColorTextSecondary),
		atoms.NewText("$2,480.50", theming.TypographyHeadlineSmall, theming.ColorTextPrimary),
	).
		WithDelta(atoms.NewTag("+5.0% APY", theming.ColorSuccessContainer)).
		WithIcon(atoms.NewIcon("star", 20, theming.ColorBrandPrimary)).
		WithProgress(atoms.ProgressAtom{
			Kind:     "linear",
			Progress: atoms.Float64Ptr(0.78),
			Label:    "$1,950 / $2,500 monthly cashback cap",
			Color:    atoms.ColorRef(theming.ColorSuccess),
		}).
		WithLayout(cardLayout()).
		Build()

	return snippets.NewPage("pay-home", "Abeta Pay").
		WithToolbar(payToolbar("Abeta Pay", "Wallet & Smart Cards · Zero-Fee P2P", "back_pay", cardsIcon)).
		WithAdaptiveLayout(payCenteredAdaptive()).
		AddSnippets(
			searchBar,
			payPerksMarqueeSnippet(),
			quickChips,
			balanceMetric,
			payCashbackNudgeSnippet(),
			payMetalVirtualCardSnippet(),
			payActionRow(
				"pay_quick_send_row",
				"Quick Send P2P",
				"Zero-fee instant transfer to @maya.chen, @arjun.rao or any handle",
				"arrow_forward",
				theming.ColorBrandPrimary,
				"INSTANT",
				theming.ColorSuccessContainer,
				actions.NewNavigateAction("nav_pay_quick_send", actions.NavigatePayload{Route: "pay-transfer"}),
			),
			payActionRow(
				"pay_card_controls_row",
				"Smart Card Controls",
				"Freeze card, international spend, contactless & monthly limit",
				"settings",
				theming.ColorBrandPrimary,
				"ACTIVE",
				theming.ColorInfoContainer,
				actions.NewNavigateAction("nav_pay_card_controls", actions.NavigatePayload{Route: "pay-cards"}),
			),
			payActionRow(
				"pay_recent_receipt_row",
				"Latest Settlement #PAY-3081",
				"$125.00 to @maya.chen · 4-stage live settlement tracker",
				"check",
				theming.ColorSuccess,
				"SETTLED",
				theming.ColorSuccessContainer,
				actions.NewNavigateAction("nav_pay_latest_receipt", actions.NavigatePayload{Route: "pay-receipt"}),
			),
			payBillPayCarouselSnippet(),
		).
		WithTracking("page_loaded", map[string]any{"page_id": "pay-home", "vertical": "abeta-pay"}).
		Build()
}

func payTransferAPIAction(withValidations bool) actions.ActionTrigger {
	act := actions.NewCallAPIAction("pay_execute_transfer", actions.CallAPIPayload{
		Endpoint: "/api/v1/pay/transfer",
		Method:   "POST",
		Body: map[string]any{
			"vertical":     "abeta-pay",
			"recipient":    "@maya.chen",
			"amount_cents": 12500,
			"currency":     "USD",
		},
	})
	if withValidations {
		act = act.WithValidations(
			actions.ValidationRule{FieldID: "pay_recipient", Rule: "required", Message: "Recipient handle or email is required"},
			actions.ValidationRule{FieldID: "pay_recipient", Rule: "regex", Value: payRecipientRegex, Message: "Enter a valid @handle or email"},
			actions.ValidationRule{FieldID: "pay_amount", Rule: "required", Message: "Transfer amount is required"},
			actions.ValidationRule{FieldID: "pay_amount", Rule: "regex", Value: payAmountRegex, Message: "Enter a valid USD amount (e.g. 125.00)"},
		)
	}
	return act
}

func buildPayTransferScreen() snippets.PageResponse {
	header := snippets.NewHeaderSnippet(
		"pay_transfer_header",
		atoms.NewText("Instant P2P Transfer", theming.TypographyHeadlineSmall, theming.ColorTextPrimary),
	).
		WithSubtitle(atoms.NewText("Available balance: $2,480.50 · Settles in < 2 seconds", theming.TypographyBodySmall, theming.ColorTextSecondary)).
		WithTrailingTag(atoms.NewTag("ZERO FEE", theming.ColorSuccessContainer)).
		WithLayout(cardLayout()).
		Build()

	contactChips := snippets.NewChipGroupSnippet(
		"pay_contact_chips",
		"pay_recent_contacts",
		snippets.FilterChipItem{ID: "maya", Label: "@maya.chen", Selected: true, OnClick: ptrAction(actions.NewNavigateAction("chip_pay_recent_contacts_maya", actions.NavigatePayload{Route: "pay-transfer?filter=maya"}))},
		snippets.FilterChipItem{ID: "arjun", Label: "@arjun.rao", OnClick: ptrAction(actions.NewNavigateAction("chip_pay_recent_contacts_arjun", actions.NavigatePayload{Route: "pay-transfer?filter=arjun"}))},
		snippets.FilterChipItem{ID: "elena", Label: "@elena.m", OnClick: ptrAction(actions.NewNavigateAction("chip_pay_recent_contacts_elena", actions.NavigatePayload{Route: "pay-transfer?filter=elena"}))},
		snippets.FilterChipItem{ID: "noah", Label: "@noah.park", OnClick: ptrAction(actions.NewNavigateAction("chip_pay_recent_contacts_noah", actions.NavigatePayload{Route: "pay-transfer?filter=noah"}))},
	).WithLayout(payHorizontalLayout(theming.SpacingXS.Dp())).Build()

	submitAct := payTransferAPIAction(true)
	submitBtn := atoms.NewButton("btn_pay_send_now", "Send $125.00 Instantly", "primary", &submitAct)

	transferForm := snippets.NewFormSnippet("pay_transfer_form_snippet", "pay_transfer_form", submitBtn).
		WithTitle(atoms.NewText("Transfer Details", theming.TypographyTitleMedium, theming.ColorTextPrimary)).
		WithDescription(atoms.NewText("Validated client & server before instant settlement.", theming.TypographyBodySmall, theming.ColorTextSecondary)).
		AddFields(
			atoms.TextFieldAtom{
				FieldID:      "pay_recipient",
				Label:        "Recipient (@handle or email)",
				Placeholder:  "@maya.chen",
				InitialValue: "@maya.chen",
				HelperText:   "Instant Abeta Pay handle or verified email",
				Required:     true,
				Regex:        payRecipientRegex,
				KeyboardType: "text",
			},
			atoms.TextFieldAtom{
				FieldID:      "pay_amount",
				Label:        "Amount (USD)",
				Placeholder:  "125.00",
				InitialValue: "125.00",
				HelperText:   "Zero fee up to $2,500 per instant transfer",
				Required:     true,
				Regex:        payAmountRegex,
				KeyboardType: "number",
			},
			atoms.TextFieldAtom{
				FieldID:      "pay_note",
				Label:        "Memo (Optional)",
				Placeholder:  "Dinner split & coffee",
				InitialValue: "Dinner split",
				KeyboardType: "text",
			},
		).
		WithLayout(cardLayout()).
		Build()

	onAuth := payTransferAPIAction(false)
	onFailed := actions.NewShowToastAction("pay_biometric_fallback_toast", actions.ShowToastPayload{
		Message:    "Biometric unavailable on this host — tap Send Instantly above",
		DurationMs: 2200,
		Variant:    "warning",
	})
	bioAct := actions.NewBiometricAuthAction("pay_biometric_authorize", actions.BiometricAuthPayload{
		Reason:                "Authorize instant $125.00 P2P transfer to @maya.chen",
		Title:                 "Abeta Pay Biometric Auth",
		AllowDeviceCredential: atoms.BoolPtr(true),
		OnAuthenticated:       &onAuth,
		OnFailed:              &onFailed,
	})

	bioRow := payActionRow(
		"pay_biometric_auth_row",
		"Authorize with Face ID / Touch ID",
		"Confirm $125.00 instant transfer via OS biometric prompt",
		"person",
		theming.ColorBrandPrimary,
		"BIOMETRIC",
		theming.ColorBrandContainer,
		bioAct,
	)

	receiptRow := payActionRow(
		"pay_transfer_receipt_row",
		"View Latest Settlement #PAY-3081",
		"Inspect 4-stage live clearing tracker & share receipt",
		"check",
		theming.ColorSuccess,
		"RECEIPT",
		theming.ColorSuccessContainer,
		actions.NewNavigateAction("nav_pay_transfer_receipt", actions.NavigatePayload{Route: "pay-receipt"}),
	)

	return snippets.NewPage("pay-transfer", "Send Money").
		WithToolbar(payToolbar("Send Money", "Abeta Pay · Zero-Fee Instant P2P", "back_pay_transfer")).
		WithAdaptiveLayout(payCenteredAdaptive()).
		AddSnippets(header, contactChips, transferForm, bioRow, receiptRow).
		WithTracking("page_loaded", map[string]any{"page_id": "pay-transfer", "vertical": "abeta-pay"}).
		Build()
}

func buildPayCardsScreen() snippets.PageResponse {
	header := snippets.NewHeaderSnippet(
		"pay_cards_header",
		atoms.NewText("Abeta Metal Virtual Card", theming.TypographyHeadlineSmall, theming.ColorTextPrimary),
	).
		WithSubtitle(atoms.NewText("Instant real-time card security & spending controls", theming.TypographyBodySmall, theming.ColorTextSecondary)).
		WithTrailingTag(atoms.NewTag("ACTIVE · •••• 4092", theming.ColorSuccessContainer)).
		WithLayout(cardLayout()).
		Build()

	spendMetric := snippets.NewMetricCardSnippet(
		"pay_cards_spend_metric",
		atoms.NewText("Monthly Card Spend", theming.TypographyLabelMedium, theming.ColorTextSecondary),
		atoms.NewText("$1,420.00", theming.TypographyHeadlineSmall, theming.ColorTextPrimary),
	).
		WithDelta(atoms.NewTag("28% OF LIMIT", theming.ColorSuccessContainer)).
		WithIcon(atoms.NewIcon("settings", 20, theming.ColorBrandPrimary)).
		WithProgress(atoms.ProgressAtom{
			Kind:     "linear",
			Progress: atoms.Float64Ptr(0.284),
			Label:    "$1,420 / $5,000 monthly spend limit",
			Color:    atoms.ColorRef(theming.ColorBrandPrimary),
		}).
		WithLayout(cardLayout()).
		Build()

	saveFreeze := actions.NewSaveStateAction("save_card_freeze", actions.SaveStatePayload{Key: "card_freeze", Value: false, Scope: "session"})
	saveIntl := actions.NewSaveStateAction("save_card_intl", actions.SaveStatePayload{Key: "card_international", Value: true, Scope: "session"})
	saveNfc := actions.NewSaveStateAction("save_card_nfc", actions.SaveStatePayload{Key: "card_contactless", Value: true, Scope: "session"})
	saveLimit := actions.NewSaveStateAction("save_card_limit", actions.SaveStatePayload{Key: "card_spend_limit", Value: 5000, Scope: "session"})
	saveAll := actions.NewSaveStateAction("save_pay_card_controls", actions.SaveStatePayload{
		Key:   "pay_card_controls_saved",
		Value: true,
		Scope: "session",
	}).WithSecondaryActions(
		actions.NewShowToastAction("toast_pay_cards_saved", actions.ShowToastPayload{
			Message:    "Smart Card Controls saved & synced!",
			DurationMs: 2200,
			Variant:    "success",
		}),
	)

	freezeSwitch := atoms.NewSwitch("card_freeze", "Freeze Card Temporarily", false, &saveFreeze)
	freezeSwitch.Subtitle = "Instantly block all new card transactions & subscriptions"

	intlSwitch := atoms.NewSwitch("card_international", "International & FX Spend", true, &saveIntl)
	intlSwitch.Subtitle = "Zero FX markup across 140+ global currencies"

	nfcSwitch := atoms.NewSwitch("card_contactless", "Contactless & Tap-to-Pay", true, &saveNfc)
	nfcSwitch.Subtitle = "Enable NFC tap-to-pay on phones & wearables"

	limitSlider := atoms.NewSlider("card_spend_limit", "Monthly Spend Limit", 5000, 500, 15000, &saveLimit)
	limitSlider.Steps = 29
	limitSlider.UnitLabel = "USD"

	controlsForm := snippets.NewFormSnippet(
		"pay_cards_controls_form",
		"pay_card_controls_form",
		atoms.NewButton("btn_save_pay_cards", "Save Card Controls", "primary", &saveAll),
	).
		WithTitle(atoms.NewText("Card Security & Spend Limits", theming.TypographyTitleMedium, theming.ColorTextPrimary)).
		WithDescription(atoms.NewText("Real-time switches & monthly spend cap for •••• 4092.", theming.TypographyBodySmall, theming.ColorTextSecondary)).
		AddSwitches(freezeSwitch, intlSwitch, nfcSwitch).
		AddSlider(limitSlider).
		WithLayout(cardLayout()).
		Build()

	return snippets.NewPage("pay-cards", "Smart Card Controls").
		WithToolbar(payToolbar("Smart Card Controls", "Abeta Metal · •••• 4092", "back_pay_cards")).
		WithAdaptiveLayout(payCenteredAdaptive()).
		AddSnippets(header, spendMetric, controlsForm).
		WithTracking("page_loaded", map[string]any{"page_id": "pay-cards", "vertical": "abeta-pay"}).
		Build()
}

func buildPayReceiptScreen() snippets.PageResponse {
	shareAct := actions.NewShareAction("share_pay_receipt_3081", actions.SharePayload{
		Title: "Abeta Pay Receipt #PAY-3081",
		Text:  "Sent $125.00 to @maya.chen via Abeta Pay (Zero Fee · Ref #PAY-3081)",
		URL:   "https://abeta.dev/pay/receipt/PAY-3081",
	})

	etaCountdown := atoms.NewCountdown("pay_settlement_cd", 12, "Ledger sync")
	etaCountdown.UrgentThresholdSeconds = 5
	etaCountdown.Motion = &atoms.MotionSpec{LoopEffect: atoms.MotionLoopPulse}

	etaBadge := atoms.NewTag("INSTANT", theming.ColorSuccessContainer)
	etaBadge.Motion = &atoms.MotionSpec{LoopEffect: atoms.MotionLoopPulse}

	primaryBtn := atoms.NewButton("btn_pay_share_receipt", "Share Receipt", "primary", &shareAct)
	primaryBtn.Size = "sm"

	navHome := actions.NewNavigateAction("nav_pay_receipt_home", actions.NavigatePayload{Route: "pay-home"})
	secondaryBtn := atoms.NewButton("btn_pay_back_wallet", "Back to Wallet", "outline", &navHome)
	secondaryBtn.Size = "sm"

	checkIcon := atoms.NewIcon("check", 16, theming.ColorSuccess)
	settledIcon := atoms.NewIcon("star", 16, theming.ColorTextInverse)

	tracker := snippets.NewOrderTrackerSnippet(
		"pay_settlement_tracker",
		"pay_settlement_3081",
		"#PAY-3081",
		atoms.NewText("$125.00 sent to @maya.chen · Instant Settlement", theming.TypographyTitleMedium, theming.ColorTextPrimary),
		snippets.OrderTrackerStep{ID: "step_initiated", Label: "Initiated", Caption: "09:42:01", Icon: &checkIcon, Completed: true},
		snippets.OrderTrackerStep{ID: "step_verified", Label: "Biometric Verified", Caption: "09:42:02", Icon: &checkIcon, Completed: true},
		snippets.OrderTrackerStep{ID: "step_clearing", Label: "Instant Clearing", Caption: "09:42:03", Icon: &checkIcon, Completed: true},
		snippets.OrderTrackerStep{ID: "step_settled", Label: "Settled", Caption: "Confirmed", Icon: &settledIcon, Active: true},
	).
		WithEtaCountdown(etaCountdown).
		WithEtaBadge(etaBadge).
		WithRider("Abeta Real-Time Clearing", "Ref #PAY-3081 · Zero fee · +$6.25 (5%) cashback credited").
		WithPrimaryAction(primaryBtn).
		WithSecondaryAction(secondaryBtn).
		WithLayout(cardLayout()).
		WithMotion(atoms.MotionSpec{
			Enter:         atoms.MotionTransitionSlideUp,
			DurationToken: atoms.MotionDurationNormal,
		}).
		Build()

	shareRow := payActionRow(
		"pay_receipt_share_row",
		"Share Settlement Receipt #PAY-3081",
		"Send verified zero-fee transfer confirmation via OS share sheet",
		"info",
		theming.ColorBrandPrimary,
		"SHARE",
		theming.ColorBrandContainer,
		shareAct,
	)

	cashbackRow := payActionRow(
		"pay_receipt_cashback_row",
		"5% Instant Cashback Credited",
		"+$6.25 added to your Abeta Pay Vault · Zero FX & transfer fees",
		"star",
		theming.ColorSuccess,
		"+$6.25",
		theming.ColorSuccessContainer,
		actions.NewShowToastAction("toast_pay_cashback_vault", actions.ShowToastPayload{
			Message:    "+$6.25 cashback credited to Abeta Pay Vault",
			DurationMs: 2200,
			Variant:    "success",
		}),
	)

	return snippets.NewPage("pay-receipt", "Receipt #PAY-3081").
		WithToolbar(payToolbar("Receipt #PAY-3081", "Abeta Pay · Instant Settlement", "back_pay_receipt")).
		WithAdaptiveLayout(payCenteredAdaptive()).
		AddPageAction(actions.NewTriggerFeedbackAction("pay_receipt_celebration", actions.TriggerFeedbackPayload{
			Effect:     actions.FeedbackCelebration,
			Title:      "Transfer Sent!",
			Subtitle:   "Instant settlement #PAY-3081 confirmed via Abeta Pay",
			DurationMs: 2400,
		})).
		AddSnippets(tracker, shareRow, cashbackRow).
		WithTracking("page_loaded", map[string]any{"page_id": "pay-receipt", "vertical": "abeta-pay"}).
		Build()
}
