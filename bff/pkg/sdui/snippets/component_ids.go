package snippets

import (
	"fmt"
	"strconv"
	"strings"

	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
)

// EnsureComponentIDs recursively walks page.Snippets, nested container snippets
// (carousel, grid, tabs, accordion), page chrome (toolbar, bottom bar, floating cart,
// pagination, page actions), and composed_block ComposedNode trees to guarantee every
// snippet, composed node, button, image, media pager, and action trigger has a
// deterministic, stable, non-empty id/node_id when omitted.
//
// All modified slices and pointer structs are defensively copied so calling
// EnsureComponentIDs on a page fetched from a shared cache is concurrency-safe.
func EnsureComponentIDs(page *PageResponse) {
	if page == nil {
		return
	}
	pageID := strings.TrimSpace(page.PageID)
	if pageID == "" {
		pageID = "page"
	}

	if page.Toolbar != nil {
		tb := *page.Toolbar
		tb.NavigationIcon = ensureIconPtr(tb.NavigationIcon, pageID+".toolbar.nav")
		if len(tb.TrailingActions) > 0 {
			trailing := make([]atoms.IconAtom, len(tb.TrailingActions))
			for i, ic := range tb.TrailingActions {
				trailing[i] = ensureIcon(ic, fmt.Sprintf("%s.toolbar.action_%d", pageID, i))
			}
			tb.TrailingActions = trailing
		}
		page.Toolbar = &tb
	}

	if page.BottomBar != nil {
		bb := *page.BottomBar
		bb.PrimaryButton = ensureButtonPtr(bb.PrimaryButton, pageID+".bottom_bar.primary")
		bb.SecondaryButton = ensureButtonPtr(bb.SecondaryButton, pageID+".bottom_bar.secondary")
		page.BottomBar = &bb
	}

	if page.FloatingCart != nil {
		fc := *page.FloatingCart
		fc.CTA = ensureButton(fc.CTA, pageID+".floating_cart.cta")
		page.FloatingCart = &fc
	}

	if len(page.PageActions) > 0 {
		acts := make([]actions.ActionTrigger, len(page.PageActions))
		for i, act := range page.PageActions {
			acts[i] = ensureAction(act, fmt.Sprintf("%s.page_action_%d", pageID, i))
		}
		page.PageActions = acts
	}

	if page.Pagination != nil {
		pg := *page.Pagination
		pg.LoadMoreAction = ensureActionPtr(pg.LoadMoreAction, pageID+".pagination.load_more")
		page.Pagination = &pg
	}

	page.PullToRefresh = ensureActionPtr(page.PullToRefresh, pageID+".pull_to_refresh")

	if len(page.Snippets) > 0 {
		envs := make([]SnippetEnvelope, len(page.Snippets))
		for i, env := range page.Snippets {
			envs[i] = ensureSnippetEnvelope(env, pageID, strconv.Itoa(i))
		}
		page.Snippets = envs
	}
}

func ensureSnippetEnvelope(env SnippetEnvelope, pageID, path string) SnippetEnvelope {
	if strings.TrimSpace(env.SnippetID) == "" {
		env.SnippetID = pageID + ".snippet_" + path
	}
	scope := pageID + "." + env.SnippetID

	switch d := env.Data.(type) {
	case HeaderSnippetData:
		d.LeadingIcon = ensureIconPtr(d.LeadingIcon, scope+".leading_icon")
		d.Avatar = ensureAvatarPtr(d.Avatar, scope+".avatar")
		d.TrailingAction = ensureButtonPtr(d.TrailingAction, scope+".trailing_action")
		env.Data = d
	case BannerSnippetData:
		d.Icon = ensureIconPtr(d.Icon, scope+".icon")
		d.Image = ensureImagePtr(d.Image, scope+".image")
		d.CTA = ensureButtonPtr(d.CTA, scope+".cta")
		d.OnClick = ensureActionPtr(d.OnClick, scope+".click")
		env.Data = d
	case MetricCardSnippetData:
		d.Icon = ensureIconPtr(d.Icon, scope+".icon")
		d.OnClick = ensureActionPtr(d.OnClick, scope+".click")
		env.Data = d
	case CardSnippetData:
		d.Media = ensureImagePtr(d.Media, scope+".media")
		d.Avatar = ensureAvatarPtr(d.Avatar, scope+".avatar")
		d.PrimaryButton = ensureButtonPtr(d.PrimaryButton, scope+".primary_btn")
		d.SecondaryButton = ensureButtonPtr(d.SecondaryButton, scope+".secondary_btn")
		d.OnClick = ensureActionPtr(d.OnClick, scope+".click")
		env.Data = d
	case ActionRowSnippetData:
		d.LeadingIcon = ensureIconPtr(d.LeadingIcon, scope+".leading_icon")
		d.TrailingIcon = ensureIconPtr(d.TrailingIcon, scope+".trailing_icon")
		d.OnClick = ensureActionPtr(d.OnClick, scope+".click")
		env.Data = d
	case FormSnippetData:
		d.SubmitButton = ensureButton(d.SubmitButton, scope+".submit_btn")
		d.CancelButton = ensureButtonPtr(d.CancelButton, scope+".cancel_btn")
		if len(d.Switches) > 0 {
			switches := make([]atoms.SwitchAtom, len(d.Switches))
			for i, sw := range d.Switches {
				sw.OnChange = ensureActionPtr(sw.OnChange, fmt.Sprintf("%s.switch_%d.change", scope, i))
				switches[i] = sw
			}
			d.Switches = switches
		}
		if len(d.Checkboxes) > 0 {
			cbs := make([]atoms.CheckboxAtom, len(d.Checkboxes))
			for i, cb := range d.Checkboxes {
				cb.OnChange = ensureActionPtr(cb.OnChange, fmt.Sprintf("%s.checkbox_%d.change", scope, i))
				cbs[i] = cb
			}
			d.Checkboxes = cbs
		}
		if len(d.Sliders) > 0 {
			sls := make([]atoms.SliderAtom, len(d.Sliders))
			for i, sl := range d.Sliders {
				sl.OnChange = ensureActionPtr(sl.OnChange, fmt.Sprintf("%s.slider_%d.change", scope, i))
				sls[i] = sl
			}
			d.Sliders = sls
		}
		env.Data = d
	case EmptyStateSnippetData:
		d.Icon = ensureIconPtr(d.Icon, scope+".icon")
		d.Image = ensureImagePtr(d.Image, scope+".image")
		d.ActionButton = ensureButtonPtr(d.ActionButton, scope+".action_btn")
		env.Data = d
	case CarouselSnippetData:
		if len(d.Items) > 0 {
			items := make([]SnippetEnvelope, len(d.Items))
			for i, item := range d.Items {
				items[i] = ensureSnippetEnvelope(item, pageID, path+"_"+strconv.Itoa(i))
			}
			d.Items = items
		}
		env.Data = d
	case GridSnippetData:
		if len(d.Items) > 0 {
			items := make([]SnippetEnvelope, len(d.Items))
			for i, item := range d.Items {
				items[i] = ensureSnippetEnvelope(item, pageID, path+"_"+strconv.Itoa(i))
			}
			d.Items = items
		}
		env.Data = d
	case TabsSnippetData:
		if len(d.Tabs) > 0 {
			tabs := make([]TabItemSpec, len(d.Tabs))
			for ti, tab := range d.Tabs {
				if len(tab.Snippets) > 0 {
					subs := make([]SnippetEnvelope, len(tab.Snippets))
					for si, sub := range tab.Snippets {
						subs[si] = ensureSnippetEnvelope(sub, pageID, fmt.Sprintf("%s_t%d_%d", path, ti, si))
					}
					tab.Snippets = subs
				}
				tabs[ti] = tab
			}
			d.Tabs = tabs
		}
		env.Data = d
	case AccordionSnippetData:
		if len(d.Snippets) > 0 {
			subs := make([]SnippetEnvelope, len(d.Snippets))
			for i, sub := range d.Snippets {
				subs[i] = ensureSnippetEnvelope(sub, pageID, path+"_"+strconv.Itoa(i))
			}
			d.Snippets = subs
		}
		env.Data = d
	case ProductCardSnippetData:
		d.Image = ensureImagePtr(d.Image, scope+".image")
		d.Stepper = ensureStepper(d.Stepper, scope+".stepper")
		d.OnClick = ensureActionPtr(d.OnClick, scope+".click")
		env.Data = d
	case SwipeDeckSnippetData:
		d.OnLike = ensureActionPtr(d.OnLike, scope+".like")
		d.OnNope = ensureActionPtr(d.OnNope, scope+".nope")
		d.OnSuperLike = ensureActionPtr(d.OnSuperLike, scope+".super_like")
		d.OnUndo = ensureActionPtr(d.OnUndo, scope+".undo")
		if len(d.Cards) > 0 {
			cards := make([]SwipeCardItem, len(d.Cards))
			for i, card := range d.Cards {
				cardScope := fmt.Sprintf("%s.card_%d", scope, i)
				card.Image = ensureImagePtr(card.Image, cardScope+".image")
				card.OnLike = ensureActionPtr(card.OnLike, cardScope+".like")
				card.OnNope = ensureActionPtr(card.OnNope, cardScope+".nope")
				card.OnSuperLike = ensureActionPtr(card.OnSuperLike, cardScope+".super_like")
				card.OnClick = ensureActionPtr(card.OnClick, cardScope+".click")
				cards[i] = card
			}
			d.Cards = cards
		}
		env.Data = d
	case SearchBarSnippetData:
		d.LeadingIcon = ensureIconPtr(d.LeadingIcon, scope+".leading_icon")
		d.TrailingIcon = ensureIconPtr(d.TrailingIcon, scope+".trailing_icon")
		d.OnSubmit = ensureActionPtr(d.OnSubmit, scope+".submit")
		d.OnClear = ensureActionPtr(d.OnClear, scope+".clear")
		d.OnTrailingClick = ensureActionPtr(d.OnTrailingClick, scope+".trailing_click")
		env.Data = d
	case ChipGroupSnippetData:
		if len(d.Chips) > 0 {
			chips := make([]FilterChipItem, len(d.Chips))
			for i, ch := range d.Chips {
				chScope := fmt.Sprintf("%s.chip_%d", scope, i)
				ch.Icon = ensureIconPtr(ch.Icon, chScope+".icon")
				ch.OnClick = ensureActionPtr(ch.OnClick, chScope+".click")
				chips[i] = ch
			}
			d.Chips = chips
		}
		env.Data = d
	case NudgeBarSnippetData:
		d.Icon = ensureIconPtr(d.Icon, scope+".icon")
		d.Countdown = ensureCountdownPtr(d.Countdown, scope+".countdown")
		d.CTA = ensureButtonPtr(d.CTA, scope+".cta")
		d.OnDismiss = ensureActionPtr(d.OnDismiss, scope+".dismiss")
		d.OnClick = ensureActionPtr(d.OnClick, scope+".click")
		env.Data = d
	case MarqueeStripSnippetData:
		if len(d.Items) > 0 {
			items := make([]MarqueeItem, len(d.Items))
			for i, it := range d.Items {
				itScope := fmt.Sprintf("%s.item_%d", scope, i)
				it.Icon = ensureIconPtr(it.Icon, itScope+".icon")
				it.OnClick = ensureActionPtr(it.OnClick, itScope+".click")
				items[i] = it
			}
			d.Items = items
		}
		env.Data = d
	case OrderTrackerSnippetData:
		d.EtaCountdown = ensureCountdownPtr(d.EtaCountdown, scope+".eta_countdown")
		d.PrimaryAction = ensureButtonPtr(d.PrimaryAction, scope+".primary_action")
		d.SecondaryAction = ensureButtonPtr(d.SecondaryAction, scope+".secondary_action")
		if len(d.Steps) > 0 {
			steps := make([]OrderTrackerStep, len(d.Steps))
			for i, st := range d.Steps {
				st.Icon = ensureIconPtr(st.Icon, fmt.Sprintf("%s.step_%d.icon", scope, i))
				steps[i] = st
			}
			d.Steps = steps
		}
		env.Data = d
	case ComposedBlockSnippetData:
		d.Root = ensureComposedNode(d.Root, scope, "0")
		env.Data = d
	}

	return env
}

func ensureComposedNode(node ComposedNode, scope, path string) ComposedNode {
	if strings.TrimSpace(node.NodeID) == "" {
		node.NodeID = scope + ".node_" + path
	}
	ns := node.NodeID

	node.OnClick = ensureActionPtr(node.OnClick, ns+".click")
	node.Button = ensureButtonPtr(node.Button, ns+".btn")
	node.Image = ensureImagePtr(node.Image, ns+".img")
	node.MediaPager = ensureMediaPagerPtr(node.MediaPager, ns+".media_pager")
	node.Icon = ensureIconPtr(node.Icon, ns+".icon")
	node.Avatar = ensureAvatarPtr(node.Avatar, ns+".avatar")
	node.Countdown = ensureCountdownPtr(node.Countdown, ns+".countdown")

	if node.Text != nil {
		cp := *node.Text
		cp.OnClick = ensureActionPtr(cp.OnClick, ns+".text.click")
		node.Text = &cp
	}
	if node.Stepper != nil {
		cp := ensureStepper(*node.Stepper, ns+".stepper")
		node.Stepper = &cp
	}
	if node.Switch != nil {
		cp := *node.Switch
		cp.OnChange = ensureActionPtr(cp.OnChange, ns+".switch.change")
		node.Switch = &cp
	}
	if node.Checkbox != nil {
		cp := *node.Checkbox
		cp.OnChange = ensureActionPtr(cp.OnChange, ns+".checkbox.change")
		node.Checkbox = &cp
	}
	if node.Slider != nil {
		cp := *node.Slider
		cp.OnChange = ensureActionPtr(cp.OnChange, ns+".slider.change")
		node.Slider = &cp
	}
	if node.RadioGroup != nil {
		cp := *node.RadioGroup
		cp.OnChange = ensureActionPtr(cp.OnChange, ns+".radio_group.change")
		node.RadioGroup = &cp
	}
	if node.Select != nil {
		cp := *node.Select
		cp.OnChange = ensureActionPtr(cp.OnChange, ns+".select.change")
		node.Select = &cp
	}
	if node.OtpInput != nil {
		cp := *node.OtpInput
		cp.OnComplete = ensureActionPtr(cp.OnComplete, ns+".otp.complete")
		node.OtpInput = &cp
	}
	if node.Chip != nil {
		cp := *node.Chip
		cp.OnClick = ensureActionPtr(cp.OnClick, ns+".chip.click")
		cp.OnRemove = ensureActionPtr(cp.OnRemove, ns+".chip.remove")
		node.Chip = &cp
	}
	if node.SegmentedControl != nil {
		cp := *node.SegmentedControl
		cp.OnChange = ensureActionPtr(cp.OnChange, ns+".segmented.change")
		node.SegmentedControl = &cp
	}
	if node.Rating != nil {
		cp := *node.Rating
		cp.OnRate = ensureActionPtr(cp.OnRate, ns+".rating.rate")
		node.Rating = &cp
	}
	if node.AlertBanner != nil {
		cp := *node.AlertBanner
		cp.OnAction = ensureActionPtr(cp.OnAction, ns+".alert.action")
		cp.OnDismiss = ensureActionPtr(cp.OnDismiss, ns+".alert.dismiss")
		node.AlertBanner = &cp
	}
	if node.StatTile != nil {
		cp := *node.StatTile
		cp.OnClick = ensureActionPtr(cp.OnClick, ns+".stat.click")
		node.StatTile = &cp
	}
	if node.KeyValueRow != nil {
		cp := *node.KeyValueRow
		cp.OnClick = ensureActionPtr(cp.OnClick, ns+".kv.click")
		node.KeyValueRow = &cp
	}
	if node.QrCode != nil {
		cp := *node.QrCode
		cp.OnClick = ensureActionPtr(cp.OnClick, ns+".qr.click")
		node.QrCode = &cp
	}

	if len(node.Children) > 0 {
		children := make([]ComposedNode, len(node.Children))
		for i, ch := range node.Children {
			children[i] = ensureComposedNode(ch, scope, path+"_"+strconv.Itoa(i))
		}
		node.Children = children
	}
	return node
}

func ensureAction(tr actions.ActionTrigger, fallbackID string) actions.ActionTrigger {
	if strings.TrimSpace(tr.ID) == "" {
		tr.ID = fallbackID
	}
	if len(tr.SecondaryActions) > 0 {
		secs := make([]actions.ActionTrigger, len(tr.SecondaryActions))
		for i, sec := range tr.SecondaryActions {
			secs[i] = ensureAction(sec, fmt.Sprintf("%s.sec_%d", tr.ID, i))
		}
		tr.SecondaryActions = secs
	}
	return tr
}

func ensureActionPtr(tr *actions.ActionTrigger, fallbackID string) *actions.ActionTrigger {
	if tr == nil {
		return nil
	}
	cp := ensureAction(*tr, fallbackID)
	return &cp
}

func ensureButton(btn atoms.ButtonAtom, fallbackID string) atoms.ButtonAtom {
	if strings.TrimSpace(btn.ID) == "" {
		btn.ID = fallbackID
	}
	btn.LeadingIcon = ensureIconPtr(btn.LeadingIcon, btn.ID+".leading_icon")
	btn.TrailingIcon = ensureIconPtr(btn.TrailingIcon, btn.ID+".trailing_icon")
	btn.OnClick = ensureActionPtr(btn.OnClick, btn.ID+".click")
	return btn
}

func ensureButtonPtr(btn *atoms.ButtonAtom, fallbackID string) *atoms.ButtonAtom {
	if btn == nil {
		return nil
	}
	cp := ensureButton(*btn, fallbackID)
	return &cp
}

func ensureImage(img atoms.ImageAtom, fallbackID string) atoms.ImageAtom {
	if strings.TrimSpace(img.ID) == "" {
		img.ID = fallbackID
	}
	img.OnClick = ensureActionPtr(img.OnClick, img.ID+".click")
	return img
}

func ensureImagePtr(img *atoms.ImageAtom, fallbackID string) *atoms.ImageAtom {
	if img == nil {
		return nil
	}
	cp := ensureImage(*img, fallbackID)
	return &cp
}

func ensureMediaPagerPtr(mp *atoms.MediaPagerAtom, fallbackID string) *atoms.MediaPagerAtom {
	if mp == nil {
		return nil
	}
	cp := *mp
	if strings.TrimSpace(cp.ID) == "" {
		cp.ID = fallbackID
	}
	if len(cp.Items) > 0 {
		items := make([]atoms.ImageAtom, len(cp.Items))
		for i, img := range cp.Items {
			items[i] = ensureImage(img, fmt.Sprintf("%s.item_%d", cp.ID, i))
		}
		cp.Items = items
	}
	cp.OnPageClick = ensureActionPtr(cp.OnPageClick, cp.ID+".page_click")
	return &cp
}

func ensureIcon(ic atoms.IconAtom, fallbackID string) atoms.IconAtom {
	ic.OnClick = ensureActionPtr(ic.OnClick, fallbackID+".click")
	return ic
}

func ensureIconPtr(ic *atoms.IconAtom, fallbackID string) *atoms.IconAtom {
	if ic == nil {
		return nil
	}
	cp := ensureIcon(*ic, fallbackID)
	return &cp
}

func ensureAvatarPtr(av *atoms.AvatarAtom, fallbackID string) *atoms.AvatarAtom {
	if av == nil {
		return nil
	}
	cp := *av
	cp.OnClick = ensureActionPtr(cp.OnClick, fallbackID+".click")
	return &cp
}

func ensureStepper(st atoms.StepperAtom, fallbackID string) atoms.StepperAtom {
	st.OnIncrement = ensureActionPtr(st.OnIncrement, fallbackID+".inc")
	st.OnDecrement = ensureActionPtr(st.OnDecrement, fallbackID+".dec")
	return st
}

func ensureCountdownPtr(cd *atoms.CountdownAtom, fallbackID string) *atoms.CountdownAtom {
	if cd == nil {
		return nil
	}
	cp := *cd
	cp.OnExpire = ensureActionPtr(cp.OnExpire, fallbackID+".expire")
	return &cp
}
