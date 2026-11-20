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
		WithTracking("hdr_view", map[string]any{"pos": 1}).
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

	page := snippets.NewPage("demo", "Demo Page").
		WithToolbar(snippets.ToolbarSpec{Title: &title, Subtitle: &sub}).
		AddSnippet(headerEnv).
		AddSnippets(bannerEnv, metricEnv, cardEnv, rowEnv, formEnv, emptyEnv, divEnv, spcEnv, carouselEnv, gridEnv, tabsEnv, accEnv, productEnv).
		WithBottomBar(snippets.BottomBarSpec{PrimaryButton: &btn, HelperText: &sub, Motion: &motion}).
		WithFloatingCart(snippets.FloatingCartSpec{
			Label:    "1 item · $3.49",
			EtaBadge: &tag,
			CTA:      btn,
			Motion:   &motion,
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
		WithTracking("page_view", map[string]any{"page": "demo"}).
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
