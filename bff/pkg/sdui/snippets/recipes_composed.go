package snippets

import (
	"fmt"

	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

// ── Declarative ComposedNode Molding Constructors ──────────────────────────────

func cloneNodeSlice(children []ComposedNode) []ComposedNode {
	out := make([]ComposedNode, len(children))
	for i, ch := range children {
		out[i] = cloneComposedNode(ch)
	}
	return out
}

// VStack creates a vertical column container ComposedNode with optional item spacing in dp.
func VStack(spacingDp int, children ...ComposedNode) ComposedNode {
	n := ComposedNode{
		Kind:     ComposedNodeColumn,
		Children: cloneNodeSlice(children),
	}
	if spacingDp > 0 {
		n.SpacingDp = atoms.IntPtr(spacingDp)
	}
	return n
}

// HStack creates a horizontal row container ComposedNode with optional item spacing in dp.
func HStack(spacingDp int, children ...ComposedNode) ComposedNode {
	n := ComposedNode{
		Kind:     ComposedNodeRow,
		Children: cloneNodeSlice(children),
	}
	if spacingDp > 0 {
		n.SpacingDp = atoms.IntPtr(spacingDp)
	}
	return n
}

// BoxNode creates a layered box container ComposedNode.
func BoxNode(children ...ComposedNode) ComposedNode {
	return ComposedNode{
		Kind:     ComposedNodeBox,
		Children: cloneNodeSlice(children),
	}
}

// CardNode creates a styled surface card container ComposedNode.
func CardNode(layout atoms.LayoutConfig, children ...ComposedNode) ComposedNode {
	return ComposedNode{
		Kind:     ComposedNodeCard,
		Children: cloneNodeSlice(children),
		Layout:   &layout,
	}
}

// FlowRowNode creates a wrapping flow-row container ComposedNode with optional item spacing in dp.
func FlowRowNode(spacingDp int, children ...ComposedNode) ComposedNode {
	n := ComposedNode{
		Kind:     ComposedNodeFlowRow,
		Children: cloneNodeSlice(children),
	}
	if spacingDp > 0 {
		n.SpacingDp = atoms.IntPtr(spacingDp)
	}
	return n
}

// TextNode wraps a TextAtom as a composed leaf node.
func TextNode(text atoms.TextAtom) ComposedNode {
	return ComposedNode{Kind: ComposedNodeText, Text: &text}
}

// TagNode wraps a TagAtom as a composed leaf node.
func TagNode(tag atoms.TagAtom) ComposedNode {
	return ComposedNode{Kind: ComposedNodeTag, Tag: &tag}
}

// ButtonNode wraps a ButtonAtom as a composed leaf node.
func ButtonNode(btn atoms.ButtonAtom) ComposedNode {
	return ComposedNode{Kind: ComposedNodeButton, Button: &btn}
}

// IconNode wraps an IconAtom as a composed leaf node.
func IconNode(icon atoms.IconAtom) ComposedNode {
	return ComposedNode{Kind: ComposedNodeIcon, Icon: &icon}
}

// ImageNode wraps an ImageAtom as a composed leaf node.
func ImageNode(img atoms.ImageAtom) ComposedNode {
	return ComposedNode{Kind: ComposedNodeImage, Image: &img}
}

// AvatarNode wraps an AvatarAtom as a composed leaf node.
func AvatarNode(avatar atoms.AvatarAtom) ComposedNode {
	return ComposedNode{Kind: ComposedNodeAvatar, Avatar: &avatar}
}

// ProgressNode wraps a ProgressAtom as a composed leaf node.
func ProgressNode(progress atoms.ProgressAtom) ComposedNode {
	return ComposedNode{Kind: ComposedNodeProgress, Progress: &progress}
}

// DividerNode wraps a DividerAtom as a composed leaf node.
func DividerNode(divider atoms.DividerAtom) ComposedNode {
	return ComposedNode{Kind: ComposedNodeDivider, Divider: &divider}
}

// SpacerNode wraps a SpacerAtom as a composed leaf node.
func SpacerNode(spacer atoms.SpacerAtom) ComposedNode {
	return ComposedNode{Kind: ComposedNodeSpacer, Spacer: &spacer}
}

// StepperNode wraps a StepperAtom as a composed leaf node.
func StepperNode(stepper atoms.StepperAtom) ComposedNode {
	return ComposedNode{Kind: ComposedNodeStepper, Stepper: &stepper}
}

// CountdownNode wraps a CountdownAtom as a composed leaf node.
func CountdownNode(countdown atoms.CountdownAtom) ComposedNode {
	return ComposedNode{Kind: ComposedNodeCountdown, Countdown: &countdown}
}

// RollingCounterNode creates an animated odometer rolling counter leaf node
// with canonical counter_value, counter_typography, and counter_color wire fields.
func RollingCounterNode(value string, typo theming.TypographyToken, color theming.ColorToken) ComposedNode {
	return ComposedNode{
		Kind:              ComposedNodeRollingCounter,
		CounterValue:      value,
		CounterTypography: atoms.TypographyRef(typo),
		CounterColor:      atoms.ColorRef(color),
	}
}

// RadioGroupNode wraps a RadioGroupAtom as a composed leaf node.
func RadioGroupNode(radioGroup atoms.RadioGroupAtom) ComposedNode {
	return ComposedNode{Kind: ComposedNodeRadioGroup, RadioGroup: &radioGroup}
}

// SelectNode wraps a SelectAtom as a composed leaf node.
func SelectNode(sel atoms.SelectAtom) ComposedNode {
	return ComposedNode{Kind: ComposedNodeSelect, Select: &sel}
}

// OtpInputNode wraps an OtpInputAtom as a composed leaf node.
func OtpInputNode(otp atoms.OtpInputAtom) ComposedNode {
	return ComposedNode{Kind: ComposedNodeOtpInput, OtpInput: &otp}
}

// ChipNode wraps a ChipAtom as a composed leaf node.
func ChipNode(chip atoms.ChipAtom) ComposedNode {
	return ComposedNode{Kind: ComposedNodeChip, Chip: &chip}
}

// SegmentedControlNode wraps a SegmentedControlAtom as a composed leaf node.
func SegmentedControlNode(segmented atoms.SegmentedControlAtom) ComposedNode {
	return ComposedNode{Kind: ComposedNodeSegmentedControl, SegmentedControl: &segmented}
}

// RatingNode wraps a RatingAtom as a composed leaf node.
func RatingNode(rating atoms.RatingAtom) ComposedNode {
	return ComposedNode{Kind: ComposedNodeRating, Rating: &rating}
}

// PriceNode wraps a PriceAtom as a composed leaf node.
func PriceNode(price atoms.PriceAtom) ComposedNode {
	return ComposedNode{Kind: ComposedNodePrice, Price: &price}
}

// BadgeNode wraps a BadgeAtom as a composed leaf node.
func BadgeNode(badge atoms.BadgeAtom) ComposedNode {
	return ComposedNode{Kind: ComposedNodeBadge, Badge: &badge}
}

// AlertBannerNode wraps an AlertBannerAtom as a composed leaf node.
func AlertBannerNode(alert atoms.AlertBannerAtom) ComposedNode {
	return ComposedNode{Kind: ComposedNodeAlertBanner, AlertBanner: &alert}
}

// StatTileNode wraps a StatTileAtom as a composed leaf node.
func StatTileNode(stat atoms.StatTileAtom) ComposedNode {
	return ComposedNode{Kind: ComposedNodeStatTile, StatTile: &stat}
}

// KeyValueRowNode wraps a KeyValueRowAtom as a composed leaf node.
func KeyValueRowNode(row atoms.KeyValueRowAtom) ComposedNode {
	return ComposedNode{Kind: ComposedNodeKeyValueRow, KeyValueRow: &row}
}

// QrCodeNode wraps a QrCodeAtom as a composed leaf node.
func QrCodeNode(qr atoms.QrCodeAtom) ComposedNode {
	return ComposedNode{Kind: ComposedNodeQrCode, QrCode: &qr}
}

// ── Fluent Value Modifiers on ComposedNode ─────────────────────────────────────

// WithNodeID attaches a stable node_id identifier to a ComposedNode.
func (n ComposedNode) WithNodeID(id string) ComposedNode {
	n.NodeID = id
	return n
}

// WithWeight sets a flex weight on a child ComposedNode inside a Row or Column.
func (n ComposedNode) WithWeight(weight float64) ComposedNode {
	n.Weight = atoms.Float64Ptr(weight)
	return n
}

// WithContentAlignment sets the AlignmentToken ("start", "center", "end") hint on a ComposedNode.
func (n ComposedNode) WithContentAlignment(alignment string) ComposedNode {
	n.Alignment = alignment
	return n
}

// WithAlignment sets horizontal and vertical alignment hints on a ComposedNode.
func (n ComposedNode) WithAlignment(horizontal, vertical string) ComposedNode {
	n.HorizontalAlignment = horizontal
	n.VerticalAlignment = vertical
	return n
}

// WithArrangement sets the main-axis arrangement hint on a container ComposedNode.
func (n ComposedNode) WithArrangement(arrangement string) ComposedNode {
	n.MainAxisArrangement = arrangement
	return n
}

// WithLayout attaches box styling and padding/margin config to a ComposedNode.
func (n ComposedNode) WithLayout(layout atoms.LayoutConfig) ComposedNode {
	n.Layout = &layout
	return n
}

// WithMotion attaches declarative transition and loop motion to a ComposedNode.
func (n ComposedNode) WithMotion(motion atoms.MotionSpec) ComposedNode {
	n.Motion = &motion
	return n
}

// WithOnClick attaches an ActionTrigger invoked when the ComposedNode is tapped.
func (n ComposedNode) WithOnClick(trigger actions.ActionTrigger) ComposedNode {
	n.OnClick = &trigger
	return n
}

// ── Reusable Server-Crafted Showcase Recipes ───────────────────────────────────

func recipeCardLayout(bg theming.ColorToken, border theming.ColorToken) atoms.LayoutConfig {
	return atoms.LayoutConfig{
		Padding:      atoms.UniformSpacing(theming.SpacingLG.Dp()),
		BgColor:      atoms.ColorRef(bg),
		CornerRadius: atoms.UniformRadius(theming.RadiusLG.Dp()),
		Border: &atoms.BorderSpec{
			WidthDp: 1,
			Color:   *atoms.ColorRef(border),
		},
		FillMaxWidth: atoms.BoolPtr(true),
	}
}

// MoldFlashDealRecipe crafts a live flash-deal card using ComposedNode atoms
// (urgency tag, live countdown, rolling odometer price, and claim CTA) with zero client code.
func MoldFlashDealRecipe(
	snippetID string,
	dealID string,
	title string,
	dealPrice string,
	originalPrice string,
	remainingSeconds int,
	cta actions.ActionTrigger,
) SnippetEnvelope {
	badge := atoms.NewTag("FLASH DROP", theming.ColorErrorContainer)
	badge.Motion = &atoms.MotionSpec{LoopEffect: atoms.MotionLoopPulse}

	countdown := atoms.NewCountdown(dealID+"_timer", remainingSeconds, "Ends in")
	countdown.Motion = &atoms.MotionSpec{LoopEffect: atoms.MotionLoopHeartbeat}

	claimBtn := atoms.NewButton(dealID+"_claim", "Claim Deal", "primary", &cta)
	claimBtn.Size = "md"

	root := CardNode(
		recipeCardLayout(theming.ColorSurfacePrimary, theming.ColorWarning),
		VStack(
			theming.SpacingMD.Dp(),
			HStack(
				theming.SpacingSM.Dp(),
				TagNode(badge),
				CountdownNode(countdown),
			).WithArrangement(ComposedAlignSpaceBetween).WithAlignment("", ComposedAlignCenter),
			TextNode(atoms.NewText(title, theming.TypographyTitleMedium, theming.ColorTextPrimary)),
			HStack(
				theming.SpacingSM.Dp(),
				RollingCounterNode(dealPrice, theming.TypographyHeadlineSmall, theming.ColorBrandPrimary),
				TextNode(atoms.NewText(originalPrice, theming.TypographyBodySmall, theming.ColorTextTertiary)),
			).WithAlignment("", ComposedAlignCenter),
			DividerNode(atoms.DividerAtom{
				ThicknessDp: atoms.Float64Ptr(1),
				Color:       atoms.ColorRef(theming.ColorDivider),
			}),
			ButtonNode(claimBtn),
		),
	).WithNodeID(dealID + "_root").WithMotion(atoms.MotionSpec{
		Enter:         atoms.MotionTransitionScaleIn,
		DurationToken: atoms.MotionDurationNormal,
		LoopEffect:    atoms.MotionLoopGlowRing,
	})

	return NewComposedBlockSnippet(snippetID, dealID, root).
		WithRecipeName("flash_deal_card").
		WithMotion(atoms.MotionSpec{
			Enter:         atoms.MotionTransitionSlideUp,
			DurationToken: atoms.MotionDurationNormal,
		}).
		WithTracking("composed_recipe_viewed", map[string]string{
			"recipe":  "flash_deal_card",
			"deal_id": dealID,
		}).
		Build()
}

// MoldLoyaltyStampRecipe crafts a punch-card loyalty progress block with dynamic
// earned/remaining stamp icons, linear progress meter, and reward CTA.
func MoldLoyaltyStampRecipe(
	snippetID string,
	programID string,
	title string,
	earnedStamps int,
	totalStamps int,
	rewardLabel string,
	cta actions.ActionTrigger,
) SnippetEnvelope {
	if totalStamps < 1 {
		totalStamps = 1
	}
	if earnedStamps < 0 {
		earnedStamps = 0
	}
	if earnedStamps > totalStamps {
		earnedStamps = totalStamps
	}

	stamps := make([]ComposedNode, 0, totalStamps)
	for i := range totalStamps {
		if i < earnedStamps {
			stamps = append(stamps, IconNode(atoms.NewIcon("check_circle", 24, theming.ColorSuccess)))
		} else {
			stamps = append(stamps, IconNode(atoms.NewIcon("radio_button_unchecked", 24, theming.ColorTextTertiary)))
		}
	}

	btnLabel := "Keep Collecting"
	if earnedStamps >= totalStamps {
		btnLabel = "Redeem Reward"
	}
	actionBtn := atoms.NewButton(programID+"_cta", btnLabel, "primary", &cta)
	actionBtn.Size = "sm"

	ratio := float64(earnedStamps) / float64(totalStamps)
	progressLabel := fmt.Sprintf("%d of %d stamps", earnedStamps, totalStamps)

	root := CardNode(
		recipeCardLayout(theming.ColorSurfaceSecondary, theming.ColorBorderSubtle),
		VStack(
			theming.SpacingMD.Dp(),
			HStack(
				theming.SpacingMD.Dp(),
				AvatarNode(atoms.NewAvatar("AB", 36, theming.ColorBrandContainer, theming.ColorTextBrand)),
				VStack(
					theming.SpacingXXS.Dp(),
					TextNode(atoms.NewText(title, theming.TypographyTitleMedium, theming.ColorTextPrimary)),
					TextNode(atoms.NewText(rewardLabel, theming.TypographyBodySmall, theming.ColorTextSecondary)),
				).WithWeight(1),
				TagNode(atoms.NewTag(fmt.Sprintf("%d/%d", earnedStamps, totalStamps), theming.ColorBrandContainer)),
			).WithAlignment("", ComposedAlignCenter),
			FlowRowNode(theming.SpacingSM.Dp(), stamps...),
			ProgressNode(atoms.ProgressAtom{
				Kind:     "linear",
				Progress: atoms.Float64Ptr(ratio),
				Label:    progressLabel,
				Color:    atoms.ColorRef(theming.ColorSuccess),
			}),
			ButtonNode(actionBtn),
		),
	).WithNodeID(programID + "_root")

	return NewComposedBlockSnippet(snippetID, programID, root).
		WithRecipeName("loyalty_stamp_card").
		WithMotion(atoms.MotionSpec{
			Enter:         atoms.MotionTransitionFadeIn,
			DurationToken: atoms.MotionDurationNormal,
		}).
		WithTracking("composed_recipe_viewed", map[string]string{
			"recipe":     "loyalty_stamp_card",
			"program_id": programID,
		}).
		Build()
}

// MoldLiveEtaPromoRecipe crafts an express delivery ETA promotion card pairing
// animated iconography, floating ETA badge, live slot countdown, and express CTA.
func MoldLiveEtaPromoRecipe(
	snippetID string,
	promoID string,
	headline string,
	subheadline string,
	etaLabel string,
	remainingSeconds int,
	cta actions.ActionTrigger,
) SnippetEnvelope {
	etaTag := atoms.NewTag(etaLabel, theming.ColorSuccessContainer)
	etaTag.Motion = &atoms.MotionSpec{LoopEffect: atoms.MotionLoopFloat}

	boltNode := IconNode(atoms.NewIcon("bolt", 22, theming.ColorBrandPrimary)).
		WithMotion(atoms.MotionSpec{LoopEffect: atoms.MotionLoopWiggle})

	slotCountdown := atoms.NewCountdown(promoID+"_slot_timer", remainingSeconds, "Express slot")
	shopBtn := atoms.NewButton(promoID+"_shop", "Shop Express", "primary", &cta)
	shopBtn.Size = "sm"

	root := CardNode(
		recipeCardLayout(theming.ColorBrandContainer, theming.ColorBrandPrimary),
		VStack(
			theming.SpacingMD.Dp(),
			HStack(
				theming.SpacingSM.Dp(),
				boltNode,
				VStack(
					theming.SpacingXXS.Dp(),
					TextNode(atoms.NewText(headline, theming.TypographyTitleMedium, theming.ColorTextPrimary)),
					TextNode(atoms.NewText(subheadline, theming.TypographyBodySmall, theming.ColorTextSecondary)),
				).WithWeight(1),
				TagNode(etaTag),
			).WithAlignment("", ComposedAlignCenter),
			HStack(
				theming.SpacingSM.Dp(),
				CountdownNode(slotCountdown),
				ButtonNode(shopBtn),
			).WithArrangement(ComposedAlignSpaceBetween).WithAlignment("", ComposedAlignCenter),
		),
	).WithNodeID(promoID + "_root")

	return NewComposedBlockSnippet(snippetID, promoID, root).
		WithRecipeName("live_eta_promo_card").
		WithMotion(atoms.MotionSpec{
			Enter:         atoms.MotionTransitionSlideUp,
			DurationToken: atoms.MotionDurationFast,
		}).
		WithTracking("composed_recipe_viewed", map[string]string{
			"recipe":   "live_eta_promo_card",
			"promo_id": promoID,
		}).
		Build()
}
