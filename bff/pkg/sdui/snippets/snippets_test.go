package snippets_test

import (
	"encoding/json"
	"testing"

	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

func TestBuildersAndWireFormat(t *testing.T) {
	act := actions.NewNavigateAction("nav", actions.NavigatePayload{Route: "/catalog"})
	title := atoms.NewText("Title", theming.TypographyTitleLarge, theming.ColorTextPrimary)
	sub := atoms.NewText("Subtitle", theming.TypographyBodyMedium, theming.ColorTextSecondary)
	icon := atoms.NewIcon("star", 20, theming.ColorBrandPrimary)
	tag := atoms.NewTag("BETA", theming.ColorInfoContainer)
	btn := atoms.NewButton("btn_1", "Go", "primary", &act)
	img := atoms.ImageAtom{URL: "https://cdn.abeta.dev/a.png"}
	avatar := atoms.NewAvatar("AR", 40, theming.ColorBrandContainer, theming.ColorTextBrand)
	skeleton := atoms.NewSkeleton(atoms.SkeletonText, 140, 16)
	sw := atoms.NewSwitch("alerts", "Alerts", true, &act)
	cb := atoms.NewCheckbox("terms", "Accept Terms", false, &act)
	sl := atoms.NewSlider("limit", "Limit", 25, 0, 100, &act)
	layout := atoms.LayoutConfig{FillMaxWidth: atoms.BoolPtr(true)}
	motion := atoms.MotionSpec{
		Enter:         atoms.MotionTransitionSlideUp,
		DurationToken: atoms.MotionDurationNormal,
		StaggerMs:     60,
		LoopEffect:    atoms.MotionLoopPulse,
	}
	stepper := atoms.NewStepper("cart_qty_avocado", "ADD", 1, &act, &act)
	price := atoms.NewText("$3.49", theming.TypographyLabelLarge, theming.ColorTextPrimary)
	origPrice := atoms.NewText("$4.49", theming.TypographyBodySmall, theming.ColorTextTertiary)
	rating := atoms.NewText("4.9 ★", theming.TypographyLabelSmall, theming.ColorSuccess)

	headerEnv := snippets.NewHeaderSnippet("h1", title).
		WithParentID("root").
		WithSubtitle(sub).
		WithLeadingIcon(icon).
		WithAvatar(avatar).
		WithTrailingTag(tag).
		WithTrailingAction(btn).
		WithLayout(layout).
		WithMotion(motion).
		WithTracking("hdr_view", map[string]string{"pos": "1"}).
		Build()

	bannerEnv := snippets.NewBannerSnippet("b1", title).
		WithParentID("root").
		WithBody(sub).
		WithIcon(icon).
		WithImage(img).
		WithTag(tag).
		WithCTA(btn).
		WithOnClick(act).
		WithLayout(layout).
		WithMotion(motion).
		WithTracking("bnr_view", nil).
		Build()

	metricEnv := snippets.NewMetricCardSnippet("m1", sub, title).
		WithParentID("root").
		WithDelta(tag).
		WithIcon(icon).
		WithProgress(atoms.ProgressAtom{Kind: "linear", Progress: atoms.Float64Ptr(0.8)}).
		WithOnClick(act).
		WithLayout(layout).
		WithMotion(motion).
		WithTracking("metric_view", nil).
		Build()

	cardEnv := snippets.NewCardSnippet("c1", title).
		WithParentID("root").
		WithSubtitle(sub).
		WithDescription(sub).
		WithMedia(img).
		WithAvatar(avatar).
		WithSkeletons(skeleton).
		WithBadge(tag).
		WithPrimaryButton(btn).
		WithSecondaryButton(btn).
		WithOnClick(act).
		WithLayout(layout).
		WithMotion(motion).
		WithTracking("card_view", nil).
		Build()

	rowEnv := snippets.NewActionRowSnippet("r1", title).
		WithParentID("root").
		WithSubtitle(sub).
		WithLeadingIcon(icon).
		WithTrailingTag(tag).
		WithTrailingIcon(icon).
		WithOnClick(act).
		WithLayout(layout).
		WithMotion(motion).
		WithTracking("row_view", nil).
		Build()

	formEnv := snippets.NewFormSnippet("f1", "onboarding", btn).
		WithParentID("root").
		WithTitle(title).
		WithDescription(sub).
		AddField(atoms.TextFieldAtom{FieldID: "name", Label: "Name", Required: true}).
		AddFields(atoms.TextFieldAtom{FieldID: "email", Label: "Email"}).
		AddSwitch(sw).
		AddSwitches(sw).
		AddCheckbox(cb).
		AddCheckboxes(cb).
		AddSlider(sl).
		AddSliders(sl).
		WithCancelButton(btn).
		WithLayout(layout).
		WithMotion(motion).
		WithTracking("form_view", nil).
		Build()

	emptyEnv := snippets.NewEmptyStateSnippet("e1", title).
		WithParentID("root").
		WithMessage(sub).
		WithIcon(icon).
		WithImage(img).
		WithActionButton(btn).
		WithLayout(layout).
		WithMotion(motion).
		WithTracking("empty_view", nil).
		Build()

	divEnv := snippets.NewDividerSnippet("d1", atoms.DividerAtom{ThicknessDp: atoms.Float64Ptr(1)}).
		WithParentID("root").
		WithLayout(layout).
		WithMotion(motion).
		WithTracking("div_view", nil).
		Build()

	spcEnv := snippets.NewSpacerSnippet("s1", atoms.SpacerAtom{HeightDp: atoms.IntPtr(16)}).
		WithParentID("root").
		WithMotion(motion).
		WithTracking("spc_view", nil).
		Build()

	carouselEnv := snippets.NewCarouselSnippet("car1").
		WithParentID("root").
		WithTitle(title).
		AddItem(cardEnv).
		AddItems(metricEnv).
		WithItemWidthDp(220).
		WithItemWidthByWidth(snippets.ResponsiveIntSpec{
			Compact:  atoms.IntPtr(220),
			Medium:   atoms.IntPtr(260),
			Expanded: atoms.IntPtr(300),
		}).
		WithSpacingDp(12).
		WithLayout(layout).
		WithMotion(motion).
		WithTracking("car_view", nil).
		Build()

	gridEnv := snippets.NewGridSnippet("grd1", 2).
		WithParentID("root").
		WithTitle(title).
		WithColumnsByWidth(snippets.ResponsiveColumnsSpec{
			Compact:  atoms.IntPtr(2),
			Medium:   atoms.IntPtr(3),
			Expanded: atoms.IntPtr(4),
		}).
		WithSpacingDp(8).
		AddItem(metricEnv).
		AddItems(cardEnv).
		WithLayout(layout).
		WithMotion(motion).
		WithTracking("grid_view", nil).
		Build()

	tabsEnv := snippets.NewTabsSnippet("tab1").
		WithParentID("root").
		AddTab(snippets.TabItemSpec{ID: "t1", Title: "Overview", Badge: &tag, Snippets: []snippets.SnippetEnvelope{cardEnv}}).
		AddTabs(snippets.TabItemSpec{ID: "t2", Title: "Details", Snippets: []snippets.SnippetEnvelope{rowEnv}}).
		WithSelectedIndex(1).
		WithLayout(layout).
		WithMotion(motion).
		WithTracking("tabs_view", nil).
		Build()

	accEnv := snippets.NewAccordionSnippet("acc1", title).
		WithParentID("root").
		WithSubtitle(sub).
		WithInitiallyExpanded(true).
		AddSnippet(rowEnv).
		AddSnippets(divEnv).
		WithLayout(layout).
		WithMotion(motion).
		WithTracking("acc_view", nil).
		Build()

	productEnv := snippets.NewProductCardSnippet("prod1", "avocados", title, price, 349, stepper).
		WithParentID("root").
		WithSubtitle(sub).
		WithOriginalPrice(origPrice).
		WithBadge(tag).
		WithImage(img).
		WithRating(rating).
		WithOnClick(act).
		WithLayout(layout).
		WithMotion(motion).
		WithTracking("prod_view", nil).
		Build()

	swipeCard1 := snippets.SwipeCardItem{
		CardID:       "card_1",
		Title:        title,
		Subtitle:     &sub,
		Bio:          &sub,
		Badge:        &tag,
		DistanceText: &rating,
		Image:        &img,
		Tags:         []atoms.TagAtom{tag},
		OnLike:       &act,
		OnNope:       &act,
		OnSuperLike:  &act,
		OnClick:      &act,
	}
	swipeCard2 := snippets.SwipeCardItem{
		CardID: "card_2",
		Title:  title,
	}
	swipeCard3 := snippets.SwipeCardItem{
		CardID: "card_3",
		Title:  title,
	}
	swipeDeckEnv := snippets.NewSwipeDeckSnippet("deck1", "discover_deck", swipeCard1).
		WithParentID("root").
		AddCard(swipeCard2).
		AddCards(swipeCard3).
		WithLabels("LIKE", "NOPE", "SUPER").
		WithEmptyState(title, sub).
		WithOnLike(act).
		WithOnNope(act).
		WithOnSuperLike(act).
		WithOnUndo(act).
		WithLayout(layout).
		WithMotion(motion).
		WithTracking("deck_view", map[string]string{"deck": "discover_deck"}).
		Build()

	searchBarEnv := snippets.NewSearchBarSnippet("srch1", "global_search", "Search catalog").
		WithParentID("root").
		WithQuery("avocado").
		WithLeadingIcon(icon).
		WithTrailingIcon(icon).
		WithSubmitLabel("Search").
		WithClearable(true).
		WithOnSubmit(act).
		WithOnClear(act).
		WithOnTrailingClick(act).
		WithLayout(layout).
		WithMotion(motion).
		WithTracking("search_view", map[string]string{"surface": "demo"}).
		Build()

	chip1 := snippets.FilterChipItem{
		ID:       "chip_all",
		Label:    "All",
		Selected: true,
		Icon:     &icon,
		Badge:    "12",
		OnClick:  &act,
	}
	chip2 := snippets.FilterChipItem{
		ID:       "chip_new",
		Label:    "New",
		Selected: false,
	}
	chip3 := snippets.FilterChipItem{
		ID:       "chip_top",
		Label:    "Top Rated",
		Selected: true,
	}
	chipGroupEnv := snippets.NewChipGroupSnippet("chips1", "catalog_filters", chip1).
		WithParentID("root").
		WithTitle(title).
		WithMultiSelect(true).
		AddChip(chip2).
		AddChips(chip3).
		WithLayout(layout).
		WithMotion(motion).
		WithTracking("chips_view", map[string]string{"group": "catalog_filters"}).
		Build()

	countdown := atoms.NewCountdown("flash_cd", 420, "Ends in")
	nudgeEnv := snippets.NewNudgeBarSnippet("nudge1", "free_delivery_nudge", title).
		WithParentID("root").
		WithSubtitle(sub).
		WithIcon(icon).
		WithBadge(tag).
		WithCountdown(countdown).
		WithProgress(0.75).
		WithCTA(btn).
		WithDismissible(true).
		WithPointerEdge(snippets.PointerEdgeBottom).
		WithOnDismiss(act).
		WithOnClick(act).
		WithLayout(layout).
		WithMotion(motion).
		WithTracking("nudge_view", map[string]string{"nudge_id": "free_delivery_nudge"}).
		Build()

	marqueeItem1 := snippets.MarqueeItem{ID: "mq_1", Text: "Free express delivery over $25", Icon: &icon, Badge: &tag, OnClick: &act}
	marqueeItem2 := snippets.MarqueeItem{ID: "mq_2", Text: "Fresh drop ends soon"}
	marqueeItem3 := snippets.MarqueeItem{ID: "mq_3", Text: "Zero-release SDUI"}
	marqueeEnv := snippets.NewMarqueeStripSnippet("marquee1", "promo_ticker", marqueeItem1).
		WithParentID("root").
		AddItem(marqueeItem2).
		AddItems(marqueeItem3).
		WithVelocityDpPerSec(56).
		WithSeparator("✦").
		WithLayout(layout).
		WithMotion(atoms.MotionSpec{LoopEffect: atoms.MotionLoopMarquee}).
		WithTracking("marquee_view", map[string]string{"strip_id": "promo_ticker"}).
		Build()

	step1 := snippets.OrderTrackerStep{ID: "s_packed", Label: "Packed", Caption: "2 mins ago", Icon: &icon, Completed: true, Active: false}
	step2 := snippets.OrderTrackerStep{ID: "s_transit", Label: "On the way", Caption: "0.8 km away", Completed: false, Active: true}
	step3 := snippets.OrderTrackerStep{ID: "s_door", Label: "Arriving", Completed: false, Active: false}
	trackerEnv := snippets.NewOrderTrackerSnippet("tracker1", "live_order_1", "#AF-4092", title, step1).
		WithParentID("root").
		WithEtaCountdown(countdown).
		WithEtaBadge(tag).
		WithRider("Aarav K.", "4.9★ · Insulated pack").
		AddStep(step2).
		AddSteps(step3).
		WithPrimaryAction(btn).
		WithSecondaryAction(btn).
		WithLayout(layout).
		WithMotion(motion).
		WithTracking("tracker_view", map[string]string{"order_code": "#AF-4092"}).
		Build()

	composedRoot := snippets.CardNode(
		layout,
		snippets.VStack(
			8,
			snippets.HStack(
				8,
				snippets.AvatarNode(avatar),
				snippets.TextNode(title).WithWeight(1),
				snippets.TagNode(tag),
			).WithArrangement(snippets.ComposedAlignSpaceBetween).WithAlignment(snippets.ComposedAlignStart, snippets.ComposedAlignCenter),
			snippets.BoxNode(
				snippets.ImageNode(img),
				snippets.IconNode(icon),
			).WithContentAlignment(snippets.ComposedAlignCenter),
			snippets.FlowRowNode(
				6,
				snippets.RollingCounterNode("$12.40", theming.TypographyHeadlineSmall, theming.ColorBrandPrimary),
				snippets.CountdownNode(countdown),
				snippets.StepperNode(stepper),
			),
			snippets.ProgressNode(atoms.ProgressAtom{Kind: "linear", Progress: atoms.Float64Ptr(0.6)}),
			snippets.DividerNode(atoms.DividerAtom{ThicknessDp: atoms.Float64Ptr(1)}),
			snippets.SpacerNode(atoms.SpacerAtom{HeightDp: atoms.IntPtr(4)}),
			snippets.ButtonNode(btn).WithOnClick(act),
		).WithLayout(layout).WithMotion(motion),
	).WithNodeID("composed_root")
	composedEnv := snippets.NewComposedBlockSnippet("composed1", "molded_block_1", composedRoot).
		WithParentID("root").
		WithRecipeName("custom_card").
		WithLayout(layout).
		WithMotion(motion).
		WithTracking("composed_view", map[string]string{"block_id": "molded_block_1"}).
		Build()

	page := snippets.NewPage("demo", "Demo Page").
		WithToolbar(snippets.ToolbarSpec{Title: &title, Subtitle: &sub}).
		AddSnippet(headerEnv).
		AddSnippets(
			bannerEnv, metricEnv, cardEnv, rowEnv, formEnv, emptyEnv, divEnv, spcEnv,
			carouselEnv, gridEnv, tabsEnv, accEnv, productEnv,
			swipeDeckEnv, searchBarEnv, chipGroupEnv,
			nudgeEnv, marqueeEnv, trackerEnv, composedEnv,
		).
		WithBottomBar(snippets.BottomBarSpec{PrimaryButton: &btn, HelperText: &sub, Motion: &motion}).
		WithFloatingCart(snippets.FloatingCartSpec{
			Label:                      "1 item · $3.49",
			EtaBadge:                   &tag,
			CTA:                        btn,
			Motion:                     &motion,
			FreeDeliveryThresholdCents: atoms.IntPtr(2500),
			NudgePrefix:                "Add more for FREE delivery",
			UnlockedLabel:              "FREE delivery unlocked!",
		}).
		AddPageAction(act).
		WithPagination(snippets.PaginationSpec{
			NextCursor:     "c_2",
			HasMore:        true,
			LoadMoreAction: &act,
		}).
		WithPullToRefresh(act).
		WithAdaptiveLayout(snippets.AdaptivePageSpec{
			LayoutMode:          snippets.LayoutModeCentered,
			MaxWidthDp:          atoms.IntPtr(1120),
			SplitPaneEnabled:    true,
			SecondarySnippetIDs: []string{"acc1"},
		}).
		WithTracking("page_view", map[string]string{"page": "demo"}).
		Build()

	expectedTypes := []string{
		snippets.SnippetTypeHeader,
		snippets.SnippetTypeBanner,
		snippets.SnippetTypeMetricCard,
		snippets.SnippetTypeCard,
		snippets.SnippetTypeActionRow,
		snippets.SnippetTypeForm,
		snippets.SnippetTypeEmptyState,
		snippets.SnippetTypeDivider,
		snippets.SnippetTypeSpacer,
		snippets.SnippetTypeCarousel,
		snippets.SnippetTypeGrid,
		snippets.SnippetTypeTabs,
		snippets.SnippetTypeAccordion,
		snippets.SnippetTypeProductCard,
		snippets.SnippetTypeSwipeDeck,
		snippets.SnippetTypeSearchBar,
		snippets.SnippetTypeChipGroup,
		snippets.SnippetTypeNudgeBar,
		snippets.SnippetTypeMarqueeStrip,
		snippets.SnippetTypeOrderTracker,
		snippets.SnippetTypeComposedBlock,
	}
	if len(page.Snippets) != len(expectedTypes) {
		t.Fatalf("expected %d snippets, got %d", len(expectedTypes), len(page.Snippets))
	}
	for i, want := range expectedTypes {
		if page.Snippets[i].SnippetType != want {
			t.Errorf("snippet[%d] type = %q, want %q", i, page.Snippets[i].SnippetType, want)
		}
	}

	raw, err := json.Marshal(page)
	if err != nil {
		t.Fatalf("marshal page: %v", err)
	}
	var decoded snippets.PageResponse
	if err := json.Unmarshal(raw, &decoded); err != nil {
		t.Fatalf("unmarshal page: %v", err)
	}
	if decoded.PageID != "demo" || len(decoded.Snippets) != len(expectedTypes) || decoded.Pagination == nil || !decoded.Pagination.HasMore || decoded.PullToRefresh == nil || decoded.FloatingCart == nil || decoded.AdaptiveLayout == nil || decoded.AdaptiveLayout.LayoutMode != snippets.LayoutModeCentered {
		t.Fatalf("unexpected decoded page: %+v", decoded)
	}
	if decoded.BottomBar == nil || decoded.BottomBar.Motion == nil || decoded.FloatingCart.Label != "1 item · $3.49" || decoded.FloatingCart.EtaBadge == nil || decoded.FloatingCart.Motion == nil {
		t.Fatalf("unexpected floating cart or bottom bar motion: %+v", decoded)
	}
	if decoded.FloatingCart.FreeDeliveryThresholdCents == nil || *decoded.FloatingCart.FreeDeliveryThresholdCents != 2500 || decoded.FloatingCart.NudgePrefix == "" || decoded.FloatingCart.UnlockedLabel == "" {
		t.Fatalf("expected floating cart free delivery nudge fields, got %+v", decoded.FloatingCart)
	}
	if decoded.Snippets[0].Motion == nil || decoded.Snippets[0].Motion.Enter != atoms.MotionTransitionSlideUp {
		t.Fatalf("expected snippet envelope motion, got %+v", decoded.Snippets[0].Motion)
	}

	// Direct typed fast path
	hdrDirect, err := snippets.DecodeSnippetData[snippets.HeaderSnippetData](headerEnv)
	if err != nil || hdrDirect.Title.Text != "Title" || hdrDirect.Avatar == nil {
		t.Fatalf("direct DecodeSnippetData failed: %+v, err=%v", hdrDirect, err)
	}

	// JSON map-backed path after Unmarshal for containers & form controls
	hdrDecoded, err := snippets.DecodeSnippetData[snippets.HeaderSnippetData](decoded.Snippets[0])
	if err != nil || hdrDecoded.Title.Text != "Title" {
		t.Fatalf("map-backed DecodeSnippetData failed: %+v, err=%v", hdrDecoded, err)
	}
	formDecoded, err := snippets.DecodeSnippetData[snippets.FormSnippetData](decoded.Snippets[5])
	if err != nil || len(formDecoded.Switches) != 2 || len(formDecoded.Checkboxes) != 2 || len(formDecoded.Sliders) != 2 {
		t.Fatalf("form DecodeSnippetData failed: %+v, err=%v", formDecoded, err)
	}
	carDecoded, err := snippets.DecodeSnippetData[snippets.CarouselSnippetData](decoded.Snippets[9])
	if err != nil || len(carDecoded.Items) != 2 || *carDecoded.ItemWidthDp != 220 || carDecoded.ItemWidthByWidth == nil || *carDecoded.ItemWidthByWidth.Expanded != 300 {
		t.Fatalf("carousel DecodeSnippetData failed: %+v, err=%v", carDecoded, err)
	}
	gridDecoded, err := snippets.DecodeSnippetData[snippets.GridSnippetData](decoded.Snippets[10])
	if err != nil || gridDecoded.Columns != 2 || len(gridDecoded.Items) != 2 || gridDecoded.ColumnsByWidth == nil || *gridDecoded.ColumnsByWidth.Expanded != 4 {
		t.Fatalf("grid DecodeSnippetData failed: %+v, err=%v", gridDecoded, err)
	}
	tabsDecoded, err := snippets.DecodeSnippetData[snippets.TabsSnippetData](decoded.Snippets[11])
	if err != nil || tabsDecoded.SelectedIndex != 1 || len(tabsDecoded.Tabs) != 2 {
		t.Fatalf("tabs DecodeSnippetData failed: %+v, err=%v", tabsDecoded, err)
	}
	accDecoded, err := snippets.DecodeSnippetData[snippets.AccordionSnippetData](decoded.Snippets[12])
	if err != nil || !accDecoded.InitiallyExpanded || len(accDecoded.Snippets) != 2 {
		t.Fatalf("accordion DecodeSnippetData failed: %+v, err=%v", accDecoded, err)
	}
	prodDecoded, err := snippets.DecodeSnippetData[snippets.ProductCardSnippetData](decoded.Snippets[13])
	if err != nil || prodDecoded.ProductID != "avocados" || prodDecoded.UnitPriceCents != 349 || prodDecoded.OriginalPrice == nil || prodDecoded.Rating == nil || prodDecoded.Stepper.Quantity != 1 {
		t.Fatalf("product_card DecodeSnippetData failed: %+v, err=%v", prodDecoded, err)
	}
	deckDecoded, err := snippets.DecodeSnippetData[snippets.SwipeDeckSnippetData](decoded.Snippets[14])
	if err != nil || deckDecoded.DeckID != "discover_deck" || len(deckDecoded.Cards) != 3 || deckDecoded.LikeLabel != "LIKE" || deckDecoded.NopeLabel != "NOPE" || deckDecoded.SuperLikeLabel != "SUPER" || deckDecoded.EmptyTitle == nil || deckDecoded.EmptyMessage == nil || deckDecoded.OnLike == nil || deckDecoded.OnNope == nil || deckDecoded.OnSuperLike == nil || deckDecoded.OnUndo == nil {
		t.Fatalf("swipe_deck DecodeSnippetData failed: %+v, err=%v", deckDecoded, err)
	}
	if deckDecoded.Cards[0].CardID != "card_1" || deckDecoded.Cards[0].Subtitle == nil || deckDecoded.Cards[0].Bio == nil || deckDecoded.Cards[0].Badge == nil || deckDecoded.Cards[0].DistanceText == nil || deckDecoded.Cards[0].Image == nil || len(deckDecoded.Cards[0].Tags) != 1 || deckDecoded.Cards[0].OnLike == nil || deckDecoded.Cards[0].OnNope == nil || deckDecoded.Cards[0].OnSuperLike == nil || deckDecoded.Cards[0].OnClick == nil {
		t.Fatalf("swipe_deck card[0] DecodeSnippetData failed: %+v", deckDecoded.Cards[0])
	}
	searchDecoded, err := snippets.DecodeSnippetData[snippets.SearchBarSnippetData](decoded.Snippets[15])
	if err != nil || searchDecoded.SearchID != "global_search" || searchDecoded.Query != "avocado" || searchDecoded.Placeholder != "Search catalog" || !searchDecoded.Clearable || searchDecoded.LeadingIcon == nil || searchDecoded.TrailingIcon == nil || searchDecoded.SubmitLabel != "Search" || searchDecoded.OnSubmit == nil || searchDecoded.OnClear == nil || searchDecoded.OnTrailingClick == nil {
		t.Fatalf("search_bar DecodeSnippetData failed: %+v, err=%v", searchDecoded, err)
	}
	chipsDecoded, err := snippets.DecodeSnippetData[snippets.ChipGroupSnippetData](decoded.Snippets[16])
	if err != nil || chipsDecoded.GroupID != "catalog_filters" || !chipsDecoded.MultiSelect || chipsDecoded.Title == nil || len(chipsDecoded.Chips) != 3 || !chipsDecoded.Chips[0].Selected || chipsDecoded.Chips[0].Icon == nil || chipsDecoded.Chips[0].Badge != "12" || chipsDecoded.Chips[0].OnClick == nil {
		t.Fatalf("chip_group DecodeSnippetData failed: %+v, err=%v", chipsDecoded, err)
	}
	nudgeDecoded, err := snippets.DecodeSnippetData[snippets.NudgeBarSnippetData](decoded.Snippets[17])
	if err != nil || nudgeDecoded.NudgeID != "free_delivery_nudge" || nudgeDecoded.Subtitle == nil || nudgeDecoded.Icon == nil || nudgeDecoded.Badge == nil || nudgeDecoded.Countdown == nil || nudgeDecoded.Progress == nil || *nudgeDecoded.Progress != 0.75 || nudgeDecoded.CTA == nil || !nudgeDecoded.Dismissible || nudgeDecoded.PointerEdge != snippets.PointerEdgeBottom || nudgeDecoded.OnDismiss == nil || nudgeDecoded.OnClick == nil {
		t.Fatalf("nudge_bar DecodeSnippetData failed: %+v, err=%v", nudgeDecoded, err)
	}
	marqueeDecoded, err := snippets.DecodeSnippetData[snippets.MarqueeStripSnippetData](decoded.Snippets[18])
	if err != nil || marqueeDecoded.StripID != "promo_ticker" || len(marqueeDecoded.Items) != 3 || marqueeDecoded.VelocityDpPerSec != 56 || marqueeDecoded.Separator != "✦" || marqueeDecoded.Items[0].Badge == nil || marqueeDecoded.Items[0].OnClick == nil {
		t.Fatalf("marquee_strip DecodeSnippetData failed: %+v, err=%v", marqueeDecoded, err)
	}
	trackerDecoded, err := snippets.DecodeSnippetData[snippets.OrderTrackerSnippetData](decoded.Snippets[19])
	if err != nil || trackerDecoded.TrackerID != "live_order_1" || trackerDecoded.OrderCode != "#AF-4092" || trackerDecoded.EtaCountdown == nil || trackerDecoded.EtaBadge == nil || trackerDecoded.RiderName != "Aarav K." || len(trackerDecoded.Steps) != 3 || !trackerDecoded.Steps[0].Completed || !trackerDecoded.Steps[1].Active || trackerDecoded.PrimaryAction == nil || trackerDecoded.SecondaryAction == nil {
		t.Fatalf("order_tracker DecodeSnippetData failed: %+v, err=%v", trackerDecoded, err)
	}
	composedDecoded, err := snippets.DecodeSnippetData[snippets.ComposedBlockSnippetData](decoded.Snippets[20])
	if err != nil || composedDecoded.BlockID != "molded_block_1" || composedDecoded.RecipeName != "custom_card" || composedDecoded.Root.NodeID != "composed_root" || composedDecoded.Root.Kind != snippets.ComposedNodeCard || len(composedDecoded.Root.Children) != 1 {
		t.Fatalf("composed_block DecodeSnippetData failed: %+v, err=%v", composedDecoded, err)
	}

	// Direct typed fast path for interactive & nudge/composed snippets
	if directDeck, err := snippets.DecodeSnippetData[snippets.SwipeDeckSnippetData](swipeDeckEnv); err != nil || directDeck.DeckID != "discover_deck" {
		t.Fatalf("direct swipe_deck DecodeSnippetData failed: %+v, err=%v", directDeck, err)
	}
	if directSearch, err := snippets.DecodeSnippetData[snippets.SearchBarSnippetData](searchBarEnv); err != nil || directSearch.SearchID != "global_search" {
		t.Fatalf("direct search_bar DecodeSnippetData failed: %+v, err=%v", directSearch, err)
	}
	if directChips, err := snippets.DecodeSnippetData[snippets.ChipGroupSnippetData](chipGroupEnv); err != nil || directChips.GroupID != "catalog_filters" {
		t.Fatalf("direct chip_group DecodeSnippetData failed: %+v, err=%v", directChips, err)
	}
	if directNudge, err := snippets.DecodeSnippetData[snippets.NudgeBarSnippetData](nudgeEnv); err != nil || directNudge.NudgeID != "free_delivery_nudge" {
		t.Fatalf("direct nudge_bar DecodeSnippetData failed: %+v, err=%v", directNudge, err)
	}
	if directMarquee, err := snippets.DecodeSnippetData[snippets.MarqueeStripSnippetData](marqueeEnv); err != nil || directMarquee.StripID != "promo_ticker" {
		t.Fatalf("direct marquee_strip DecodeSnippetData failed: %+v, err=%v", directMarquee, err)
	}
	if directTracker, err := snippets.DecodeSnippetData[snippets.OrderTrackerSnippetData](trackerEnv); err != nil || directTracker.TrackerID != "live_order_1" {
		t.Fatalf("direct order_tracker DecodeSnippetData failed: %+v, err=%v", directTracker, err)
	}
	if directComposed, err := snippets.DecodeSnippetData[snippets.ComposedBlockSnippetData](composedEnv); err != nil || directComposed.BlockID != "molded_block_1" {
		t.Fatalf("direct composed_block DecodeSnippetData failed: %+v, err=%v", directComposed, err)
	}

	// Error paths for DecodeSnippetData
	badEnv := snippets.SnippetEnvelope{SnippetID: "bad", Data: make(chan int)}
	if _, err := snippets.DecodeSnippetData[snippets.HeaderSnippetData](badEnv); err == nil {
		t.Fatal("expected marshal error for channel data")
	}
	mismatchEnv := snippets.SnippetEnvelope{SnippetID: "mismatch", SnippetType: "header_v1", Data: "not_an_object"}
	if _, err := snippets.DecodeSnippetData[snippets.HeaderSnippetData](mismatchEnv); err == nil {
		t.Fatal("expected unmarshal error for non-object data")
	}
}

func TestInteractiveSnippets_WireJSONAndDefaults(t *testing.T) {
	// 1. SearchBar defaults Clearable=true and omits unset optional fields
	defaultSearch := snippets.NewSearchBarSnippet("s_default", "main_search", "Search modules").Build()
	rawDefaultSearch, err := json.Marshal(defaultSearch)
	if err != nil {
		t.Fatalf("marshal default search_bar: %v", err)
	}
	var searchMap map[string]any
	if err := json.Unmarshal(rawDefaultSearch, &searchMap); err != nil {
		t.Fatalf("unmarshal default search_bar map: %v", err)
	}
	if searchMap["snippet_type"] != snippets.SnippetTypeSearchBar {
		t.Fatalf("expected snippet_type=%q, got %v", snippets.SnippetTypeSearchBar, searchMap["snippet_type"])
	}
	searchData := searchMap["data"].(map[string]any)
	if searchData["search_id"] != "main_search" || searchData["placeholder"] != "Search modules" || searchData["clearable"] != true {
		t.Fatalf("unexpected default search_bar data: %+v", searchData)
	}
	for _, omitted := range []string{"query", "leading_icon", "trailing_icon", "submit_label", "on_submit", "on_clear", "on_trailing_click", "layout"} {
		if _, exists := searchData[omitted]; exists {
			t.Errorf("expected optional search_bar key %q to be omitted", omitted)
		}
	}

	// Explicit WithClearable(false) still emits "clearable": false on wire
	nonClearableSearch := snippets.NewSearchBarSnippet("s_noclear", "locked_search", "Filter").
		WithClearable(false).
		Build()
	rawNoClear, err := json.Marshal(nonClearableSearch)
	if err != nil {
		t.Fatalf("marshal non-clearable search_bar: %v", err)
	}
	var noClearEnv map[string]any
	if err := json.Unmarshal(rawNoClear, &noClearEnv); err != nil {
		t.Fatalf("unmarshal non-clearable search_bar: %v", err)
	}
	noClearData := noClearEnv["data"].(map[string]any)
	if v, ok := noClearData["clearable"]; !ok || v != false {
		t.Fatalf("expected explicit clearable=false on wire, got %v (present=%v)", v, ok)
	}

	// 2. Empty SwipeDeck serializes cards as [] (non-null) and omits optional labels/actions
	emptyDeck := snippets.NewSwipeDeckSnippet("d_empty", "empty_deck").Build()
	rawEmptyDeck, err := json.Marshal(emptyDeck)
	if err != nil {
		t.Fatalf("marshal empty swipe_deck: %v", err)
	}
	var deckEnvMap map[string]any
	if err := json.Unmarshal(rawEmptyDeck, &deckEnvMap); err != nil {
		t.Fatalf("unmarshal empty swipe_deck: %v", err)
	}
	deckData := deckEnvMap["data"].(map[string]any)
	cardsField, ok := deckData["cards"].([]any)
	if !ok || len(cardsField) != 0 {
		t.Fatalf("expected empty JSON array for cards, got %#v", deckData["cards"])
	}
	for _, omitted := range []string{"like_label", "nope_label", "super_like_label", "empty_title", "empty_message", "on_like", "on_nope", "on_super_like", "on_undo", "layout"} {
		if _, exists := deckData[omitted]; exists {
			t.Errorf("expected optional swipe_deck key %q to be omitted", omitted)
		}
	}

	// Defensive copy check on initial cards slice
	seedCards := []snippets.SwipeCardItem{
		{CardID: "c_seed", Title: atoms.NewText("Seed", theming.TypographyTitleSmall, theming.ColorTextPrimary)},
	}
	copiedDeck := snippets.NewSwipeDeckSnippet("d_copy", "copy_deck", seedCards...).Build()
	seedCards[0].CardID = "mutated"
	decodedCopiedDeck, err := snippets.DecodeSnippetData[snippets.SwipeDeckSnippetData](copiedDeck)
	if err != nil || decodedCopiedDeck.Cards[0].CardID != "c_seed" {
		t.Fatalf("expected defensive copy of cards slice, got %+v, err=%v", decodedCopiedDeck, err)
	}

	// 3. Empty ChipGroup serializes chips as [] and emits multi_select=false
	emptyChips := snippets.NewChipGroupSnippet("g_empty", "empty_group").Build()
	rawEmptyChips, err := json.Marshal(emptyChips)
	if err != nil {
		t.Fatalf("marshal empty chip_group: %v", err)
	}
	var chipsEnvMap map[string]any
	if err := json.Unmarshal(rawEmptyChips, &chipsEnvMap); err != nil {
		t.Fatalf("unmarshal empty chip_group: %v", err)
	}
	chipsData := chipsEnvMap["data"].(map[string]any)
	chipsArr, ok := chipsData["chips"].([]any)
	if !ok || len(chipsArr) != 0 {
		t.Fatalf("expected empty JSON array for chips, got %#v", chipsData["chips"])
	}
	if v, ok := chipsData["multi_select"]; !ok || v != false {
		t.Fatalf("expected explicit multi_select=false on wire, got %v (present=%v)", v, ok)
	}
	for _, omitted := range []string{"title", "layout"} {
		if _, exists := chipsData[omitted]; exists {
			t.Errorf("expected optional chip_group key %q to be omitted", omitted)
		}
	}

	// Defensive copy check on initial chips slice
	seedChips := []snippets.FilterChipItem{{ID: "ch_1", Label: "One", Selected: false}}
	copiedChips := snippets.NewChipGroupSnippet("g_copy", "copy_group", seedChips...).Build()
	seedChips[0].ID = "mutated"
	decodedCopiedChips, err := snippets.DecodeSnippetData[snippets.ChipGroupSnippetData](copiedChips)
	if err != nil || decodedCopiedChips.Chips[0].ID != "ch_1" {
		t.Fatalf("expected defensive copy of chips slice, got %+v, err=%v", decodedCopiedChips, err)
	}
}

func TestNudgesAndComposed_WireJSONDefensiveCopiesAndRecipes(t *testing.T) {
	// 1. NudgeBar defaults Dismissible=true and omits unset optional keys
	defaultNudge := snippets.NewNudgeBarSnippet("n_def", "nudge_default", atoms.NewText("Free delivery", theming.TypographyTitleSmall, theming.ColorTextPrimary)).Build()
	rawNudge, err := json.Marshal(defaultNudge)
	if err != nil {
		t.Fatalf("marshal default nudge_bar: %v", err)
	}
	var nudgeEnv map[string]any
	if err := json.Unmarshal(rawNudge, &nudgeEnv); err != nil {
		t.Fatalf("unmarshal default nudge_bar: %v", err)
	}
	nudgeData := nudgeEnv["data"].(map[string]any)
	if nudgeData["nudge_id"] != "nudge_default" || nudgeData["dismissible"] != true {
		t.Fatalf("unexpected default nudge_bar data: %+v", nudgeData)
	}
	for _, omitted := range []string{"subtitle", "icon", "badge", "countdown", "progress", "cta", "pointer_edge", "on_dismiss", "on_click", "layout"} {
		if _, exists := nudgeData[omitted]; exists {
			t.Errorf("expected optional nudge_bar key %q to be omitted", omitted)
		}
	}

	// Explicit WithDismissible(false) emits "dismissible": false
	lockedNudge := snippets.NewNudgeBarSnippet("n_lock", "nudge_locked", atoms.NewText("Locked", theming.TypographyBodyMedium, theming.ColorTextPrimary)).
		WithDismissible(false).
		Build()
	rawLocked, _ := json.Marshal(lockedNudge)
	var lockedEnv map[string]any
	_ = json.Unmarshal(rawLocked, &lockedEnv)
	if v, ok := lockedEnv["data"].(map[string]any)["dismissible"]; !ok || v != false {
		t.Fatalf("expected dismissible=false on wire, got %v (present=%v)", v, ok)
	}

	// 2. Empty MarqueeStrip serializes items as [] (never null) and defensive-copies input
	emptyMarquee := snippets.NewMarqueeStripSnippet("m_empty", "empty_strip").Build()
	rawMarquee, err := json.Marshal(emptyMarquee)
	if err != nil {
		t.Fatalf("marshal empty marquee_strip: %v", err)
	}
	var marqueeEnv map[string]any
	if err := json.Unmarshal(rawMarquee, &marqueeEnv); err != nil {
		t.Fatalf("unmarshal empty marquee_strip: %v", err)
	}
	marqueeData := marqueeEnv["data"].(map[string]any)
	itemsArr, ok := marqueeData["items"].([]any)
	if !ok || len(itemsArr) != 0 {
		t.Fatalf("expected empty JSON array for marquee items, got %#v", marqueeData["items"])
	}
	seedItems := []snippets.MarqueeItem{{ID: "m1", Text: "Original"}}
	copiedMarquee := snippets.NewMarqueeStripSnippet("m_copy", "copy_strip", seedItems...).Build()
	seedItems[0].Text = "Mutated"
	decodedMarquee, err := snippets.DecodeSnippetData[snippets.MarqueeStripSnippetData](copiedMarquee)
	if err != nil || decodedMarquee.Items[0].Text != "Original" {
		t.Fatalf("expected defensive copy of marquee items, got %+v, err=%v", decodedMarquee, err)
	}

	// 3. Empty OrderTracker serializes steps as [] (never null) and defensive-copies input
	emptyTracker := snippets.NewOrderTrackerSnippet("t_empty", "tr_empty", "#AF-0001", atoms.NewText("Preparing", theming.TypographyTitleSmall, theming.ColorTextPrimary)).Build()
	rawTracker, err := json.Marshal(emptyTracker)
	if err != nil {
		t.Fatalf("marshal empty order_tracker: %v", err)
	}
	var trackerEnv map[string]any
	if err := json.Unmarshal(rawTracker, &trackerEnv); err != nil {
		t.Fatalf("unmarshal empty order_tracker: %v", err)
	}
	stepsArr, ok := trackerEnv["data"].(map[string]any)["steps"].([]any)
	if !ok || len(stepsArr) != 0 {
		t.Fatalf("expected empty JSON array for tracker steps, got %#v", trackerEnv["data"].(map[string]any)["steps"])
	}
	seedSteps := []snippets.OrderTrackerStep{{ID: "st1", Label: "Placed", Completed: true}}
	copiedTracker := snippets.NewOrderTrackerSnippet("t_copy", "tr_copy", "#AF-0002", atoms.NewText("Confirmed", theming.TypographyTitleSmall, theming.ColorTextPrimary), seedSteps...).Build()
	seedSteps[0].Label = "Mutated"
	decodedTracker, err := snippets.DecodeSnippetData[snippets.OrderTrackerSnippetData](copiedTracker)
	if err != nil || decodedTracker.Steps[0].Label != "Placed" {
		t.Fatalf("expected defensive copy of tracker steps, got %+v, err=%v", decodedTracker, err)
	}

	// 4. ComposedBlock defensive deep-copy + non-nil container Children
	emptyContainer := snippets.NewComposedBlockSnippet("cb_empty", "empty_box", snippets.VStack(0)).Build()
	decodedEmptyCB, err := snippets.DecodeSnippetData[snippets.ComposedBlockSnippetData](emptyContainer)
	if err != nil || decodedEmptyCB.Root.Kind != snippets.ComposedNodeColumn || decodedEmptyCB.Root.Children == nil {
		t.Fatalf("expected non-nil empty children slice on container node, got %+v, err=%v", decodedEmptyCB, err)
	}
	// Verify empty FlowRow/HStack/BoxNode without spacing
	emptyRow := snippets.HStack(0)
	emptyFlow := snippets.FlowRowNode(0)
	if emptyRow.SpacingDp != nil || emptyFlow.SpacingDp != nil || emptyRow.Children == nil || emptyFlow.Children == nil {
		t.Fatalf("expected non-nil empty children and nil zero spacing: row=%+v flow=%+v", emptyRow, emptyFlow)
	}

	childNodes := []snippets.ComposedNode{
		snippets.TextNode(atoms.NewText("Original Leaf", theming.TypographyBodyMedium, theming.ColorTextPrimary)),
	}
	rootWithSlice := snippets.ComposedNode{Kind: snippets.ComposedNodeColumn, Children: childNodes}
	copiedBlock := snippets.NewComposedBlockSnippet("cb_copy", "copy_block", rootWithSlice).Build()
	childNodes[0] = snippets.TextNode(atoms.NewText("Mutated Leaf", theming.TypographyBodyMedium, theming.ColorTextPrimary))
	decodedCB, err := snippets.DecodeSnippetData[snippets.ComposedBlockSnippetData](copiedBlock)
	if err != nil || len(decodedCB.Root.Children) != 1 || decodedCB.Root.Children[0].Text.Text != "Original Leaf" {
		t.Fatalf("expected defensive deep copy of ComposedNode children, got %+v, err=%v", decodedCB, err)
	}

	// 5. All 3 showcase recipes + clamping edge cases in MoldLoyaltyStampRecipe
	cta := actions.NewShowToastAction("act_recipe", actions.ShowToastPayload{Message: "Triggered"})

	flashEnv := snippets.MoldFlashDealRecipe("rc_flash", "deal_avocado", "Organic Hass Avocados (4-pack)", "$3.99", "$5.99", 540, cta)
	flashData, err := snippets.DecodeSnippetData[snippets.ComposedBlockSnippetData](flashEnv)
	if err != nil || flashEnv.SnippetType != snippets.SnippetTypeComposedBlock || flashData.RecipeName != "flash_deal_card" || flashData.Root.Kind != snippets.ComposedNodeCard {
		t.Fatalf("unexpected MoldFlashDealRecipe output: env=%+v data=%+v err=%v", flashEnv, flashData, err)
	}

	loyaltyPartial := snippets.MoldLoyaltyStampRecipe("rc_loyal_1", "fresh_club", "Abeta Fresh Club", 3, 5, "2 more orders for $5 off", cta)
	loyaltyData, err := snippets.DecodeSnippetData[snippets.ComposedBlockSnippetData](loyaltyPartial)
	if err != nil || loyaltyData.RecipeName != "loyalty_stamp_card" || loyaltyData.BlockID != "fresh_club" {
		t.Fatalf("unexpected MoldLoyaltyStampRecipe partial output: %+v err=%v", loyaltyData, err)
	}

	// Edge cases: totalStamps <= 0, earnedStamps < 0, earnedStamps > totalStamps (triggers Redeem Reward)
	loyaltyNeg := snippets.MoldLoyaltyStampRecipe("rc_loyal_neg", "neg_club", "Fresh Club", -2, 0, "Free delivery", cta)
	if loyaltyNeg.SnippetType != snippets.SnippetTypeComposedBlock {
		t.Fatalf("expected composed_block for negative clamp test, got %q", loyaltyNeg.SnippetType)
	}
	loyaltyFull := snippets.MoldLoyaltyStampRecipe("rc_loyal_full", "full_club", "Fresh Club", 8, 5, "Reward ready", cta)
	fullData, err := snippets.DecodeSnippetData[snippets.ComposedBlockSnippetData](loyaltyFull)
	if err != nil || len(fullData.Root.Children) != 1 {
		t.Fatalf("unexpected MoldLoyaltyStampRecipe full output: %+v err=%v", fullData, err)
	}
	colChildren := fullData.Root.Children[0].Children
	if btn := colChildren[len(colChildren)-1].Button; btn == nil || btn.Label != "Redeem Reward" {
		t.Fatalf("expected 'Redeem Reward' CTA when stamps complete, got %+v", btn)
	}

	etaEnv := snippets.MoldLiveEtaPromoRecipe("rc_eta", "eta_8m", "Groceries at your door", "Chilled dairy & produce packed fresh", "8 MINS", 480, cta)
	etaData, err := snippets.DecodeSnippetData[snippets.ComposedBlockSnippetData](etaEnv)
	if err != nil || etaData.RecipeName != "live_eta_promo_card" || etaData.BlockID != "eta_8m" {
		t.Fatalf("unexpected MoldLiveEtaPromoRecipe output: %+v err=%v", etaData, err)
	}
}
