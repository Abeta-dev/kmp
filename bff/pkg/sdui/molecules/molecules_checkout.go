package molecules

import (
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

// TipChipOption configures a single rider-tip chip inside DeliveryInstructionsCard.
type TipChipOption struct {
	ID       string
	Label    string
	Selected bool
	OnClick  *actions.ActionTrigger
}

// DeliveryInstructionsSpec configures the molded DeliveryInstructionsCard molecule.
type DeliveryInstructionsSpec struct {
	Title                string
	LeaveAtDoorFieldID   string
	LeaveAtDoor          bool
	OnLeaveAtDoorChange  *actions.ActionTrigger
	DontRingBellFieldID  string
	DontRingBellLabel    string
	DontRingBell         bool
	OnDontRingBellChange *actions.ActionTrigger
	NotesFieldID         string
	NotesLabel           string
	NotesPlaceholder     string
	NotesInitialValue    string
	TipChips             []TipChipOption
	TipSliderFieldID     string
	TipSliderLabel       string
	TipSliderValue       float64
	TipSliderMax         float64
	OnTipSliderChange    *actions.ActionTrigger
}

// VariantPickerSpec configures a bottom-sheet variant picker OpenDialogPayload action.
type VariantPickerSpec struct {
	ActionID          string
	DialogID          string
	ProductID         string
	Title             string
	Subtitle          string
	ImageURL          string
	FieldID           string
	SelectedID        string
	PriceText         string
	OriginalPriceText string
	DiscountLabel     string
	Options           []atoms.OptionItem
	OnSelectChange    *actions.ActionTrigger
	PositiveAction    *actions.ActionTrigger
	SheetScreenID     string
}

// CouponChipSpec configures a quick-apply coupon chip inside CouponApplyCard.
type CouponChipSpec struct {
	Code     string
	Label    string
	Selected bool
	OnClick  *actions.ActionTrigger
}

// CouponApplySpec configures the molded CouponApplyCard checkout molecule.
type CouponApplySpec struct {
	Title              string
	FieldID            string
	Placeholder        string
	InitialCode        string
	ApplyLabel         string
	OnApply            *actions.ActionTrigger
	QuickCoupons       []CouponChipSpec
	AppliedCode        string
	AppliedSavingsText string
}

// BillLineItem configures a single row in CheckoutBillSummary.
type BillLineItem struct {
	ID         string
	Label      string
	Value      string
	Sublabel   string
	ValueColor *atoms.ColorTokenRef
}

// CheckoutBillSpec configures the molded CheckoutBillSummary molecule.
type CheckoutBillSpec struct {
	Title              string
	SavingsBadge       string
	Items              []BillLineItem
	GrandTotalLabel    string
	GrandTotalValue    string
	GrandTotalSublabel string
	SavingsCallout     string
}

func checkoutCardLayout() atoms.LayoutConfig {
	return atoms.LayoutConfig{
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
}

// DeliveryInstructionsCard molds an interactive checkout delivery instructions card
// combining SwitchNode ("Leave at door"), optional CheckboxNode, TextFieldNode,
// rider-tip ChipNodes, and optional SliderNode inside a composed_block.
func DeliveryInstructionsCard(snippetID, blockID string, spec DeliveryInstructionsSpec) snippets.SnippetEnvelope {
	title := spec.Title
	if title == "" {
		title = "Delivery Instructions"
	}
	leaveField := spec.LeaveAtDoorFieldID
	if leaveField == "" {
		leaveField = "leave_at_door"
	}
	notesField := spec.NotesFieldID
	if notesField == "" {
		notesField = "delivery_instructions"
	}
	notesLabel := spec.NotesLabel
	if notesLabel == "" {
		notesLabel = "Delivery instructions"
	}
	notesPlaceholder := spec.NotesPlaceholder
	if notesPlaceholder == "" {
		notesPlaceholder = "Ring bell once or leave with lobby desk"
	}

	leaveSwitch := atoms.NewSwitch(leaveField, "Leave at door", spec.LeaveAtDoor, spec.OnLeaveAtDoorChange)
	leaveSwitch.Subtitle = "Zero-contact doorstep drop with photo confirmation"

	children := []snippets.ComposedNode{
		snippets.TextNode(atoms.NewText(title, theming.TypographyTitleMedium, theming.ColorTextPrimary)).
			WithNodeID(blockID + "_title"),
		snippets.SwitchNode(leaveSwitch).WithNodeID(blockID + "_leave_at_door_switch"),
	}

	if spec.DontRingBellFieldID != "" {
		cbLabel := spec.DontRingBellLabel
		if cbLabel == "" {
			cbLabel = "Don't ring the doorbell"
		}
		cb := atoms.NewCheckbox(spec.DontRingBellFieldID, cbLabel, spec.DontRingBell, spec.OnDontRingBellChange)
		children = append(children, snippets.CheckboxNode(cb).WithNodeID(blockID+"_dont_ring_checkbox"))
	}

	children = append(children,
		snippets.TextFieldNode(atoms.TextFieldAtom{
			FieldID:      notesField,
			Label:        notesLabel,
			Placeholder:  notesPlaceholder,
			InitialValue: spec.NotesInitialValue,
			KeyboardType: "text",
		}).WithNodeID(blockID+"_notes_field"),
	)

	if len(spec.TipChips) > 0 {
		chipNodes := make([]snippets.ComposedNode, 0, len(spec.TipChips))
		for _, tc := range spec.TipChips {
			chip := atoms.NewChip(blockID+"_tip_"+tc.ID, tc.Label, tc.Selected, tc.OnClick)
			chipNodes = append(chipNodes, snippets.ChipNode(chip).WithNodeID(blockID+"_tip_chip_"+tc.ID))
		}
		children = append(children,
			snippets.TextNode(atoms.NewText("Tip your delivery partner", theming.TypographyLabelLarge, theming.ColorTextPrimary)).
				WithNodeID(blockID+"_tip_heading"),
			snippets.FlowRowNode(theming.SpacingSM.Dp(), chipNodes...).WithNodeID(blockID+"_tip_chips_row"),
		)
	}

	if spec.TipSliderFieldID != "" {
		sliderLabel := spec.TipSliderLabel
		if sliderLabel == "" {
			sliderLabel = "Custom Rider Tip"
		}
		maxVal := spec.TipSliderMax
		if maxVal <= 0 {
			maxVal = 10
		}
		sl := atoms.NewSlider(spec.TipSliderFieldID, sliderLabel, spec.TipSliderValue, 0, maxVal, spec.OnTipSliderChange)
		sl.Steps = 10
		sl.UnitLabel = "$"
		children = append(children, snippets.SliderNode(sl).WithNodeID(blockID+"_tip_slider"))
	}

	root := snippets.CardNode(
		checkoutCardLayout(),
		snippets.VStack(theming.SpacingMD.Dp(), children...).WithNodeID(blockID+"_col"),
	).
		WithNodeID(blockID+"_root").
		WithTracking("node_impression", map[string]string{
			"component_id": blockID + "_root",
			"molecule":     "delivery_instructions_card",
		}).
		WithClickTracking("interaction_click", map[string]string{
			"component_id": blockID + "_root",
			"molecule":     "delivery_instructions_card",
		})

	return snippets.NewComposedBlockSnippet(snippetID, blockID, root).
		WithRecipeName("express_delivery_instructions").
		WithTracking("snippet_impression", map[string]string{
			"snippet_id": snippetID,
			"molecule":   "delivery_instructions_card",
		}).
		Build()
}

// VariantPickerSheetAction constructs an open_dialog bottom-sheet ActionTrigger
// embedding a molded composed_block with RadioGroupNode pack-size options and PriceNode.
func VariantPickerSheetAction(spec VariantPickerSpec) actions.ActionTrigger {
	fieldID := spec.FieldID
	if fieldID == "" {
		fieldID = "variant_" + spec.ProductID
	}
	blockID := spec.DialogID + "_content"

	headerChildren := []snippets.ComposedNode{}
	if spec.ImageURL != "" {
		headerChildren = append(headerChildren,
			snippets.ImageNode(atoms.ImageAtom{
				ID:                 blockID + "_img",
				URL:                spec.ImageURL,
				ContentDescription: spec.Title,
				WidthDp:            atoms.IntPtr(56),
				HeightDp:           atoms.IntPtr(56),
				ScaleMode:          "crop",
				CornerRadius:       atoms.UniformRadius(theming.RadiusSM.Dp()),
			}).WithNodeID(blockID+"_img_node"),
		)
	}

	price := atoms.NewPrice(blockID+"_price_atom", spec.PriceText, spec.OriginalPriceText)
	price.DiscountLabel = spec.DiscountLabel

	infoChildren := []snippets.ComposedNode{
		snippets.TextNode(atoms.NewText(spec.Title, theming.TypographyTitleSmall, theming.ColorTextPrimary)).
			WithNodeID(blockID + "_title"),
	}
	if spec.Subtitle != "" {
		infoChildren = append(infoChildren,
			snippets.TextNode(atoms.NewText(spec.Subtitle, theming.TypographyBodySmall, theming.ColorTextSecondary)).
				WithNodeID(blockID+"_subtitle"),
		)
	}
	infoChildren = append(infoChildren, snippets.PriceNode(price).WithNodeID(blockID+"_price"))

	headerChildren = append(headerChildren,
		snippets.VStack(theming.SpacingXXS.Dp(), infoChildren...).
			WithNodeID(blockID+"_info_col").
			WithWeight(1),
	)

	radio := atoms.NewRadioGroup(fieldID, "Choose pack size", spec.SelectedID, spec.Options...)
	radio.OnChange = spec.OnSelectChange

	root := snippets.VStack(
		theming.SpacingMD.Dp(),
		snippets.HStack(theming.SpacingMD.Dp(), headerChildren...).
			WithNodeID(blockID+"_header_row").
			WithAlignment("", snippets.ComposedAlignCenter),
		snippets.DividerNode(atoms.DividerAtom{
			ThicknessDp: atoms.Float64Ptr(1),
			Color:       atoms.ColorRef(theming.ColorDivider),
		}).WithNodeID(blockID+"_divider"),
		snippets.RadioGroupNode(radio).WithNodeID(blockID+"_radio_group"),
	).WithNodeID(blockID + "_root")

	sheetSnippet := snippets.NewComposedBlockSnippet(spec.DialogID+"_snippet", blockID, root).
		WithRecipeName("express_variant_picker_sheet").
		Build()

	dismissAct := actions.NewDismissOverlayAction("dismiss_"+spec.DialogID, actions.DismissOverlayPayload{
		OverlayID: spec.DialogID,
	})

	return actions.NewOpenDialogAction(spec.ActionID, actions.OpenDialogPayload{
		DialogID:       spec.DialogID,
		Title:          spec.Title,
		Message:        spec.Subtitle,
		Presentation:   actions.PresentationBottomSheet,
		Dismissible:    atoms.BoolPtr(true),
		PositiveAction: spec.PositiveAction,
		NegativeAction: &dismissAct,
		Snippets:       []any{sheetSnippet},
		SheetScreenID:  spec.SheetScreenID,
	}).WithTracking("variant_picker_opened", map[string]string{
		"product_id": spec.ProductID,
		"dialog_id":  spec.DialogID,
	})
}

// CouponApplyCard molds a checkout coupon entry and quick-apply chip card
// using TextFieldNode, ButtonNode, FlowRowNode of ChipNodes, and optional AlertBannerNode.
func CouponApplyCard(snippetID, blockID string, spec CouponApplySpec) snippets.SnippetEnvelope {
	title := spec.Title
	if title == "" {
		title = "Apply Coupon"
	}
	fieldID := spec.FieldID
	if fieldID == "" {
		fieldID = "coupon_code"
	}
	placeholder := spec.Placeholder
	if placeholder == "" {
		placeholder = "Enter promo code (e.g. FRESH20)"
	}
	applyLabel := spec.ApplyLabel
	if applyLabel == "" {
		applyLabel = "Apply"
	}

	applyBtn := atoms.NewButton(blockID+"_apply_btn", applyLabel, "primary", spec.OnApply)
	applyBtn.Size = "sm"

	children := []snippets.ComposedNode{
		snippets.HStack(
			theming.SpacingSM.Dp(),
			snippets.IconNode(atoms.NewIcon("local_offer", 20, theming.ColorBrandPrimary)).
				WithNodeID(blockID+"_icon"),
			snippets.TextNode(atoms.NewText(title, theming.TypographyTitleMedium, theming.ColorTextPrimary)).
				WithNodeID(blockID+"_title").
				WithWeight(1),
			snippets.TagNode(atoms.NewTag("SAVE", theming.ColorSuccessContainer)).
				WithNodeID(blockID+"_save_tag"),
		).
			WithNodeID(blockID+"_header").
			WithAlignment("", snippets.ComposedAlignCenter),
		snippets.HStack(
			theming.SpacingSM.Dp(),
			snippets.TextFieldNode(atoms.TextFieldAtom{
				FieldID:      fieldID,
				Label:        "Promo Code",
				Placeholder:  placeholder,
				InitialValue: spec.InitialCode,
				KeyboardType: "text",
			}).WithNodeID(blockID+"_input").WithWeight(1),
			snippets.ButtonNode(applyBtn).WithNodeID(blockID+"_apply_btn_node"),
		).
			WithNodeID(blockID+"_input_row").
			WithAlignment("", snippets.ComposedAlignCenter),
	}

	if len(spec.QuickCoupons) > 0 {
		chips := make([]snippets.ComposedNode, 0, len(spec.QuickCoupons))
		for _, qc := range spec.QuickCoupons {
			label := qc.Label
			if label == "" {
				label = qc.Code
			}
			ch := atoms.NewChip(blockID+"_coupon_"+qc.Code, label, qc.Selected, qc.OnClick)
			chips = append(chips, snippets.ChipNode(ch).WithNodeID(blockID+"_chip_"+qc.Code))
		}
		children = append(children, snippets.FlowRowNode(theming.SpacingSM.Dp(), chips...).WithNodeID(blockID+"_quick_chips"))
	}

	if spec.AppliedCode != "" {
		msg := spec.AppliedSavingsText
		if msg == "" {
			msg = "Promo savings applied to your basket"
		}
		banner := atoms.NewAlertBanner(blockID+"_applied_alert", spec.AppliedCode+" applied", msg, atoms.VariantSuccess)
		children = append(children, snippets.AlertBannerNode(banner).WithNodeID(blockID+"_applied_banner"))
	}

	root := snippets.CardNode(
		checkoutCardLayout(),
		snippets.VStack(theming.SpacingMD.Dp(), children...).WithNodeID(blockID+"_col"),
	).
		WithNodeID(blockID+"_root").
		WithTracking("node_impression", map[string]string{
			"component_id": blockID + "_root",
			"molecule":     "coupon_apply_card",
		})

	return snippets.NewComposedBlockSnippet(snippetID, blockID, root).
		WithRecipeName("express_coupon_apply_card").
		WithTracking("snippet_impression", map[string]string{
			"snippet_id": snippetID,
			"molecule":   "coupon_apply_card",
		}).
		Build()
}

// CheckoutBillSummary molds an itemized quick-commerce bill breakdown card using
// KeyValueRowNodes, DividerNode, emphasized Grand Total row, and optional savings AlertBannerNode.
func CheckoutBillSummary(snippetID, blockID string, spec CheckoutBillSpec) snippets.SnippetEnvelope {
	title := spec.Title
	if title == "" {
		title = "Bill Details"
	}
	grandLabel := spec.GrandTotalLabel
	if grandLabel == "" {
		grandLabel = "Grand Total"
	}

	headerChildren := []snippets.ComposedNode{
		snippets.TextNode(atoms.NewText(title, theming.TypographyTitleMedium, theming.ColorTextPrimary)).
			WithNodeID(blockID + "_title").
			WithWeight(1),
	}
	if spec.SavingsBadge != "" {
		headerChildren = append(headerChildren,
			snippets.TagNode(atoms.NewTag(spec.SavingsBadge, theming.ColorSuccessContainer)).
				WithNodeID(blockID+"_savings_tag"),
		)
	}

	children := []snippets.ComposedNode{
		snippets.HStack(theming.SpacingSM.Dp(), headerChildren...).
			WithNodeID(blockID+"_header").
			WithAlignment("", snippets.ComposedAlignCenter),
	}

	for _, item := range spec.Items {
		kv := atoms.NewKeyValueRow(blockID+"_row_"+item.ID, item.Label, item.Value)
		kv.Sublabel = item.Sublabel
		kv.ValueColor = item.ValueColor
		children = append(children, snippets.KeyValueRowNode(kv).WithNodeID(blockID+"_kv_"+item.ID))
	}

	children = append(children,
		snippets.DividerNode(atoms.DividerAtom{
			ThicknessDp: atoms.Float64Ptr(1),
			Color:       atoms.ColorRef(theming.ColorDivider),
		}).WithNodeID(blockID+"_divider"),
	)

	totalRow := atoms.NewKeyValueRow(blockID+"_grand_total_atom", grandLabel, spec.GrandTotalValue)
	totalRow.Sublabel = spec.GrandTotalSublabel
	totalRow.ValueColor = atoms.ColorRef(theming.ColorBrandPrimary)
	totalRow.Emphasized = true
	children = append(children, snippets.KeyValueRowNode(totalRow).WithNodeID(blockID+"_grand_total"))

	if spec.SavingsCallout != "" {
		callout := atoms.NewAlertBanner(blockID+"_savings_alert", "Total Basket Savings", spec.SavingsCallout, atoms.VariantSuccess)
		children = append(children, snippets.AlertBannerNode(callout).WithNodeID(blockID+"_savings_banner"))
	}

	root := snippets.CardNode(
		checkoutCardLayout(),
		snippets.VStack(theming.SpacingSM.Dp(), children...).WithNodeID(blockID+"_col"),
	).
		WithNodeID(blockID+"_root").
		WithTracking("node_impression", map[string]string{
			"component_id": blockID + "_root",
			"molecule":     "checkout_bill_summary",
		})

	return snippets.NewComposedBlockSnippet(snippetID, blockID, root).
		WithRecipeName("express_checkout_bill_summary").
		WithTracking("snippet_impression", map[string]string{
			"snippet_id": snippetID,
			"molecule":   "checkout_bill_summary",
		}).
		Build()
}
