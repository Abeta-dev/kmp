package snippets_test

import (
	"testing"

	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

func TestEnsureComponentIDs_ComprehensiveAndDeterministic(t *testing.T) {
	// Safe no-op on nil page
	snippets.EnsureComponentIDs(nil)

	anonAct := func() actions.ActionTrigger {
		return actions.NewShowToastAction("", actions.ShowToastPayload{Message: "ok"}).
			WithSecondaryActions(actions.NewTrackEventAction("", actions.TrackEventPayload{EventName: "sec"}))
	}
	anonActPtr := func() *actions.ActionTrigger {
		a := anonAct()
		return &a
	}
	anonBtn := func(label string) atoms.ButtonAtom {
		ic := atoms.NewIcon("bolt", 16, theming.ColorBrandPrimary)
		ic.OnClick = anonActPtr()
		b := atoms.NewButton("", label, "primary", anonActPtr())
		b.LeadingIcon = &ic
		b.TrailingIcon = &ic
		return b
	}
	anonImg := func(url string) atoms.ImageAtom {
		return atoms.ImageAtom{URL: url, OnClick: anonActPtr()}
	}
	anonIcon := func() atoms.IconAtom {
		ic := atoms.NewIcon("star", 18, theming.ColorBrandPrimary)
		ic.OnClick = anonActPtr()
		return ic
	}
	anonAvatar := func() atoms.AvatarAtom {
		av := atoms.NewAvatar("AB", 32, theming.ColorBrandContainer, theming.ColorTextBrand)
		av.OnClick = anonActPtr()
		return av
	}
	anonCountdown := func() atoms.CountdownAtom {
		cd := atoms.NewCountdown("cd_1", 60, "Ends")
		cd.OnExpire = anonActPtr()
		return cd
	}
	title := atoms.NewText("Title", theming.TypographyTitleSmall, theming.ColorTextPrimary)
	title.OnClick = anonActPtr()
	stepper := atoms.NewStepper("qty_1", "ADD", 0, anonActPtr(), anonActPtr())
	sw := atoms.NewSwitch("sw_1", "Leave at door", true, anonActPtr())
	cb := atoms.NewCheckbox("cb_1", "No bell", false, anonActPtr())
	sl := atoms.NewSlider("sl_1", "Tip", 2, 0, 10, anonActPtr())
	rg := atoms.NewRadioGroup("rg_1", "Size", "500g", atoms.OptionItem{ID: "500g", Label: "500g"})
	rg.OnChange = anonActPtr()
	sel := atoms.NewSelect("sel_1", "Slot", "Pick", "now", atoms.OptionItem{ID: "now", Label: "Now"})
	sel.OnChange = anonActPtr()
	otp := atoms.NewOtpInput("otp_1", "PIN", 4, anonActPtr())
	ch := atoms.NewChip("ch_1", "Organic", true, anonActPtr())
	ch.OnRemove = anonActPtr()
	seg := atoms.NewSegmentedControl("seg_1", "a", atoms.OptionItem{ID: "a", Label: "A"})
	seg.OnChange = anonActPtr()
	rt := atoms.NewRating("rt_1", 4.9, 5)
	rt.OnRate = anonActPtr()
	ab := atoms.NewAlertBanner("ab_1", "Offer", "Save 15%", atoms.VariantSuccess)
	ab.OnAction = anonActPtr()
	ab.OnDismiss = anonActPtr()
	st := atoms.NewStatTile("st_1", "ETA", "8m")
	st.OnClick = anonActPtr()
	kv := atoms.NewKeyValueRow("kv_1", "Total", "$10")
	kv.OnClick = anonActPtr()
	qr := atoms.NewQrCode("qr_1", "abeta://1", "Pass", 120)
	qr.OnClick = anonActPtr()
	mp := atoms.NewMediaPager("", []atoms.ImageAtom{anonImg("https://cdn.abeta.dev/1.png")}, 140)
	mp.OnPageClick = anonActPtr()

	navIc := anonIcon()
	tbTrail := anonIcon()
	bbPrimary := anonBtn("Primary")
	bbSecondary := anonBtn("Secondary")

	hdr := snippets.NewHeaderSnippet("", title).
		WithLeadingIcon(anonIcon()).
		WithAvatar(anonAvatar()).
		WithTrailingAction(anonBtn("Hdr")).
		Build()
	bnr := snippets.NewBannerSnippet("", title).
		WithIcon(anonIcon()).
		WithImage(anonImg("https://cdn.abeta.dev/b.png")).
		WithCTA(anonBtn("Bnr")).
		WithOnClick(anonAct()).
		Build()
	met := snippets.NewMetricCardSnippet("", title, title).
		WithIcon(anonIcon()).
		WithOnClick(anonAct()).
		Build()
	crd := snippets.NewCardSnippet("", title).
		WithMedia(anonImg("https://cdn.abeta.dev/c.png")).
		WithAvatar(anonAvatar()).
		WithPrimaryButton(anonBtn("CardP")).
		WithSecondaryButton(anonBtn("CardS")).
		WithOnClick(anonAct()).
		Build()
	row := snippets.NewActionRowSnippet("", title).
		WithLeadingIcon(anonIcon()).
		WithTrailingIcon(anonIcon()).
		WithOnClick(anonAct()).
		Build()
	frm := snippets.NewFormSnippet("", "f1", anonBtn("Submit")).
		WithCancelButton(anonBtn("Cancel")).
		AddSwitch(sw).
		AddCheckbox(cb).
		AddSlider(sl).
		Build()
	emp := snippets.NewEmptyStateSnippet("", title).
		WithIcon(anonIcon()).
		WithImage(anonImg("https://cdn.abeta.dev/e.png")).
		WithActionButton(anonBtn("Retry")).
		Build()
	prod := snippets.NewProductCardSnippet("", "sku1", title, title, 399, stepper).
		WithImage(anonImg("https://cdn.abeta.dev/p.png")).
		WithOnClick(anonAct()).
		Build()
	cardImg := anonImg("https://cdn.abeta.dev/s.png")
	deck := snippets.NewSwipeDeckSnippet("", "d1", snippets.SwipeCardItem{
		CardID:      "c1",
		Title:       title,
		Image:       &cardImg,
		OnLike:      anonActPtr(),
		OnNope:      anonActPtr(),
		OnSuperLike: anonActPtr(),
		OnClick:     anonActPtr(),
	}).
		WithOnLike(anonAct()).
		WithOnNope(anonAct()).
		WithOnSuperLike(anonAct()).
		WithOnUndo(anonAct()).
		Build()
	srch := snippets.NewSearchBarSnippet("", "s1", "Search").
		WithLeadingIcon(anonIcon()).
		WithTrailingIcon(anonIcon()).
		WithOnSubmit(anonAct()).
		WithOnClear(anonAct()).
		WithOnTrailingClick(anonAct()).
		Build()
	chipIc := anonIcon()
	chips := snippets.NewChipGroupSnippet("", "cg1", snippets.FilterChipItem{
		ID: "c1", Label: "Fresh", Icon: &chipIc, OnClick: anonActPtr(),
	}).Build()
	nudge := snippets.NewNudgeBarSnippet("", "n1", title).
		WithIcon(anonIcon()).
		WithCountdown(anonCountdown()).
		WithCTA(anonBtn("Add")).
		WithOnDismiss(anonAct()).
		WithOnClick(anonAct()).
		Build()
	mqIc := anonIcon()
	marquee := snippets.NewMarqueeStripSnippet("", "m1", snippets.MarqueeItem{
		ID: "mi1", Text: "Deal", Icon: &mqIc, OnClick: anonActPtr(),
	}).Build()
	trIc := anonIcon()
	tracker := snippets.NewOrderTrackerSnippet("", "ot1", "#1", title, snippets.OrderTrackerStep{
		ID: "s1", Label: "Packed", Icon: &trIc,
	}).
		WithEtaCountdown(anonCountdown()).
		WithPrimaryAction(anonBtn("Call")).
		WithSecondaryAction(anonBtn("Help")).
		Build()

	composedRoot := snippets.VStack(
		8,
		snippets.ButtonNode(anonBtn("CBtn")).WithOnClick(anonAct()),
		snippets.ImageNode(anonImg("https://cdn.abeta.dev/ci.png")),
		snippets.MediaPagerNode(mp),
		snippets.TextNode(title),
		snippets.IconNode(anonIcon()),
		snippets.AvatarNode(anonAvatar()),
		snippets.StepperNode(stepper),
		snippets.CountdownNode(anonCountdown()),
		snippets.SwitchNode(sw),
		snippets.CheckboxNode(cb),
		snippets.SliderNode(sl),
		snippets.TextFieldNode(atoms.TextFieldAtom{FieldID: "note", Label: "Note"}),
		snippets.RadioGroupNode(rg),
		snippets.SelectNode(sel),
		snippets.OtpInputNode(otp),
		snippets.ChipNode(ch),
		snippets.SegmentedControlNode(seg),
		snippets.RatingNode(rt),
		snippets.AlertBannerNode(ab),
		snippets.StatTileNode(st),
		snippets.KeyValueRowNode(kv),
		snippets.QrCodeNode(qr),
	)
	composed := snippets.NewComposedBlockSnippet("explicit_blk", "blk1", composedRoot).Build()

	carousel := snippets.NewCarouselSnippet("").AddItem(crd).Build()
	grid := snippets.NewGridSnippet("", 2).AddItem(prod).Build()
	tabs := snippets.NewTabsSnippet("").AddTab(snippets.TabItemSpec{
		ID: "t0", Title: "Tab", Snippets: []snippets.SnippetEnvelope{row},
	}).Build()
	acc := snippets.NewAccordionSnippet("", title).AddSnippet(met).Build()

	page := snippets.NewPage("", "Blank PageID Test").
		WithToolbar(snippets.ToolbarSpec{NavigationIcon: &navIc, TrailingActions: []atoms.IconAtom{tbTrail}}).
		WithBottomBar(snippets.BottomBarSpec{PrimaryButton: &bbPrimary, SecondaryButton: &bbSecondary}).
		WithFloatingCart(snippets.FloatingCartSpec{Label: "1 item", CTA: anonBtn("Cart")}).
		AddPageAction(anonAct()).
		WithPagination(snippets.PaginationSpec{HasMore: true, LoadMoreAction: anonActPtr()}).
		WithPullToRefresh(anonAct()).
		AddSnippets(hdr, bnr, frm, emp, deck, srch, chips, nudge, marquee, tracker, composed, carousel, grid, tabs, acc).
		Build()

	// Snapshot original slice element before mutation to verify defensive copying
	origFirstID := page.Snippets[0].SnippetID
	snippets.EnsureComponentIDs(&page)

	if origFirstID != "" {
		t.Fatal("expected original snippet ID to start empty")
	}
	if page.Toolbar.NavigationIcon.OnClick.ID != "page.toolbar.nav.click" {
		t.Fatalf("unexpected toolbar nav action ID: %q", page.Toolbar.NavigationIcon.OnClick.ID)
	}
	if page.Toolbar.TrailingActions[0].OnClick.ID != "page.toolbar.action_0.click" {
		t.Fatalf("unexpected toolbar trailing action ID: %q", page.Toolbar.TrailingActions[0].OnClick.ID)
	}
	if page.BottomBar.PrimaryButton.ID != "page.bottom_bar.primary" || page.BottomBar.SecondaryButton.ID != "page.bottom_bar.secondary" {
		t.Fatalf("unexpected bottom bar button IDs: %+v", page.BottomBar)
	}
	if page.FloatingCart.CTA.ID != "page.floating_cart.cta" || page.FloatingCart.CTA.OnClick.ID == "" {
		t.Fatalf("unexpected floating cart CTA IDs: %+v", page.FloatingCart.CTA)
	}
	if page.PageActions[0].ID != "page.page_action_0" || page.PageActions[0].SecondaryActions[0].ID != "page.page_action_0.sec_0" {
		t.Fatalf("unexpected page action IDs: %+v", page.PageActions[0])
	}
	if page.Pagination.LoadMoreAction.ID != "page.pagination.load_more" || page.PullToRefresh.ID != "page.pull_to_refresh" {
		t.Fatalf("unexpected pagination/pull_to_refresh IDs")
	}
	for i, env := range page.Snippets {
		if env.SnippetID == "" {
			t.Errorf("snippet[%d] still has empty SnippetID", i)
		}
	}

	// Verify explicit snippet ID preserved and composed tree IDs deterministic
	compEnv := page.Snippets[10]
	if compEnv.SnippetID != "explicit_blk" {
		t.Fatalf("expected explicit snippet ID preserved, got %q", compEnv.SnippetID)
	}
	compData, err := snippets.DecodeSnippetData[snippets.ComposedBlockSnippetData](compEnv)
	if err != nil {
		t.Fatalf("decode composed block: %v", err)
	}
	if compData.Root.NodeID != "page.explicit_blk.node_0" {
		t.Fatalf("unexpected composed root node_id: %q", compData.Root.NodeID)
	}
	if compData.Root.Children[0].NodeID != "page.explicit_blk.node_0_0" || compData.Root.Children[0].Button.ID == "" {
		t.Fatalf("unexpected composed button node: %+v", compData.Root.Children[0])
	}
	if compData.Root.Children[1].Image.ID == "" || compData.Root.Children[1].Image.OnClick.ID == "" {
		t.Fatalf("unexpected composed image node: %+v", compData.Root.Children[1].Image)
	}
	if compData.Root.Children[2].MediaPager.ID == "" || compData.Root.Children[2].MediaPager.Items[0].ID == "" || compData.Root.Children[2].MediaPager.OnPageClick.ID == "" {
		t.Fatalf("unexpected composed media_pager node: %+v", compData.Root.Children[2].MediaPager)
	}

	// Verify nested container snippet IDs
	carData, _ := snippets.DecodeSnippetData[snippets.CarouselSnippetData](page.Snippets[11])
	if carData.Items[0].SnippetID != "page.snippet_11_0" {
		t.Fatalf("unexpected nested carousel item ID: %q", carData.Items[0].SnippetID)
	}
	grdData, _ := snippets.DecodeSnippetData[snippets.GridSnippetData](page.Snippets[12])
	if grdData.Items[0].SnippetID != "page.snippet_12_0" {
		t.Fatalf("unexpected nested grid item ID: %q", grdData.Items[0].SnippetID)
	}
	tabData, _ := snippets.DecodeSnippetData[snippets.TabsSnippetData](page.Snippets[13])
	if tabData.Tabs[0].Snippets[0].SnippetID != "page.snippet_13_t0_0" {
		t.Fatalf("unexpected nested tab snippet ID: %q", tabData.Tabs[0].Snippets[0].SnippetID)
	}
	accData, _ := snippets.DecodeSnippetData[snippets.AccordionSnippetData](page.Snippets[14])
	if accData.Snippets[0].SnippetID != "page.snippet_14_0" {
		t.Fatalf("unexpected nested accordion snippet ID: %q", accData.Snippets[0].SnippetID)
	}

	// Idempotency check: running EnsureComponentIDs a second time leaves IDs identical
	beforeRootID := compData.Root.NodeID
	snippets.EnsureComponentIDs(&page)
	afterCompData, _ := snippets.DecodeSnippetData[snippets.ComposedBlockSnippetData](page.Snippets[10])
	if afterCompData.Root.NodeID != beforeRootID {
		t.Fatalf("EnsureComponentIDs not idempotent: %q vs %q", afterCompData.Root.NodeID, beforeRootID)
	}
}
