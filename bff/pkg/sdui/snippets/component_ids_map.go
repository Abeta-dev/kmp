package snippets

import (
	"fmt"
	"maps"
	"strconv"
	"strings"
)

func derefSnippetData(data any) any {
	switch p := data.(type) {
	case *HeaderSnippetData:
		if p != nil {
			return *p
		}
	case *BannerSnippetData:
		if p != nil {
			return *p
		}
	case *MetricCardSnippetData:
		if p != nil {
			return *p
		}
	case *CardSnippetData:
		if p != nil {
			return *p
		}
	case *ActionRowSnippetData:
		if p != nil {
			return *p
		}
	case *FormSnippetData:
		if p != nil {
			return *p
		}
	case *EmptyStateSnippetData:
		if p != nil {
			return *p
		}
	case *DividerSnippetData:
		if p != nil {
			return *p
		}
	case *SpacerSnippetData:
		if p != nil {
			return *p
		}
	case *CarouselSnippetData:
		if p != nil {
			return *p
		}
	case *GridSnippetData:
		if p != nil {
			return *p
		}
	case *TabsSnippetData:
		if p != nil {
			return *p
		}
	case *AccordionSnippetData:
		if p != nil {
			return *p
		}
	case *ProductCardSnippetData:
		if p != nil {
			return *p
		}
	case *SwipeDeckSnippetData:
		if p != nil {
			return *p
		}
	case *SearchBarSnippetData:
		if p != nil {
			return *p
		}
	case *ChipGroupSnippetData:
		if p != nil {
			return *p
		}
	case *NudgeBarSnippetData:
		if p != nil {
			return *p
		}
	case *MarqueeStripSnippetData:
		if p != nil {
			return *p
		}
	case *OrderTrackerSnippetData:
		if p != nil {
			return *p
		}
	case *ComposedBlockSnippetData:
		if p != nil {
			return *p
		}
	case *CustomSlotSnippetData:
		if p != nil {
			return *p
		}
	case *GraphCanvasSnippetData:
		if p != nil {
			return *p
		}
	}
	return data
}

func cloneAnyMap(src map[string]any) map[string]any {
	dst := make(map[string]any, len(src))
	maps.Copy(dst, src)
	return dst
}

func mapString(m map[string]any, key string) string {
	v, _ := m[key].(string)
	return v
}

func ensureMapSnippetData(raw map[string]any, snippetType, pageID, scope, path string) map[string]any {
	if raw == nil {
		return nil
	}
	d := cloneAnyMap(raw)
	switch snippetType {
	case SnippetTypeHeader:
		ensureMapIconField(d, "leading_icon", scope+".leading_icon")
		ensureMapAvatarField(d, "avatar", scope+".avatar")
		ensureMapButtonField(d, "trailing_action", scope+".trailing_action")
	case SnippetTypeBanner:
		ensureMapIconField(d, "icon", scope+".icon")
		ensureMapImageField(d, "image", scope+".image")
		ensureMapButtonField(d, "cta", scope+".cta")
		ensureMapActionField(d, "on_click", scope+".click")
	case SnippetTypeMetricCard:
		ensureMapIconField(d, "icon", scope+".icon")
		ensureMapActionField(d, "on_click", scope+".click")
	case SnippetTypeCard:
		ensureMapImageField(d, "media", scope+".media")
		ensureMapAvatarField(d, "avatar", scope+".avatar")
		ensureMapButtonField(d, "primary_button", scope+".primary_btn")
		ensureMapButtonField(d, "secondary_button", scope+".secondary_btn")
		ensureMapActionField(d, "on_click", scope+".click")
	case SnippetTypeActionRow:
		ensureMapIconField(d, "leading_icon", scope+".leading_icon")
		ensureMapIconField(d, "trailing_icon", scope+".trailing_icon")
		ensureMapActionField(d, "on_click", scope+".click")
	case SnippetTypeForm:
		ensureMapButtonField(d, "submit_button", scope+".submit_btn")
		ensureMapButtonField(d, "cancel_button", scope+".cancel_btn")
		ensureMapSliceActionField(d, "switches", "on_change", scope, "switch", "change")
		ensureMapSliceActionField(d, "checkboxes", "on_change", scope, "checkbox", "change")
		ensureMapSliceActionField(d, "sliders", "on_change", scope, "slider", "change")
	case SnippetTypeEmptyState:
		ensureMapIconField(d, "icon", scope+".icon")
		ensureMapImageField(d, "image", scope+".image")
		ensureMapButtonField(d, "action_button", scope+".action_btn")
	case SnippetTypeCarousel, SnippetTypeGrid:
		ensureMapNestedEnvelopes(d, "items", pageID, path)
	case SnippetTypeTabs:
		if tabs, ok := d["tabs"].([]any); ok && len(tabs) > 0 {
			out := make([]any, len(tabs))
			for ti, rawTab := range tabs {
				if tabMap, ok := rawTab.(map[string]any); ok {
					cp := cloneAnyMap(tabMap)
					ensureMapNestedEnvelopes(cp, "snippets", pageID, fmt.Sprintf("%s_t%d", path, ti))
					out[ti] = cp
				} else {
					out[ti] = rawTab
				}
			}
			d["tabs"] = out
		}
	case SnippetTypeAccordion:
		ensureMapNestedEnvelopes(d, "snippets", pageID, path)
	case SnippetTypeProductCard:
		ensureMapImageField(d, "image", scope+".image")
		ensureMapStepperField(d, "stepper", scope+".stepper")
		ensureMapActionField(d, "on_click", scope+".click")
	case SnippetTypeSwipeDeck:
		ensureMapActionField(d, "on_like", scope+".like")
		ensureMapActionField(d, "on_nope", scope+".nope")
		ensureMapActionField(d, "on_super_like", scope+".super_like")
		ensureMapActionField(d, "on_undo", scope+".undo")
		if cards, ok := d["cards"].([]any); ok && len(cards) > 0 {
			out := make([]any, len(cards))
			for i, rawCard := range cards {
				if cm, ok := rawCard.(map[string]any); ok {
					cp := cloneAnyMap(cm)
					cs := fmt.Sprintf("%s.card_%d", scope, i)
					ensureMapImageField(cp, "image", cs+".image")
					ensureMapActionField(cp, "on_like", cs+".like")
					ensureMapActionField(cp, "on_nope", cs+".nope")
					ensureMapActionField(cp, "on_super_like", cs+".super_like")
					ensureMapActionField(cp, "on_click", cs+".click")
					out[i] = cp
				} else {
					out[i] = rawCard
				}
			}
			d["cards"] = out
		}
	case SnippetTypeSearchBar:
		ensureMapIconField(d, "leading_icon", scope+".leading_icon")
		ensureMapIconField(d, "trailing_icon", scope+".trailing_icon")
		ensureMapActionField(d, "on_submit", scope+".submit")
		ensureMapActionField(d, "on_clear", scope+".clear")
		ensureMapActionField(d, "on_trailing_click", scope+".trailing_click")
	case SnippetTypeChipGroup:
		if chips, ok := d["chips"].([]any); ok && len(chips) > 0 {
			out := make([]any, len(chips))
			for i, rawChip := range chips {
				if cm, ok := rawChip.(map[string]any); ok {
					cp := cloneAnyMap(cm)
					cs := fmt.Sprintf("%s.chip_%d", scope, i)
					ensureMapIconField(cp, "icon", cs+".icon")
					ensureMapActionField(cp, "on_click", cs+".click")
					out[i] = cp
				} else {
					out[i] = rawChip
				}
			}
			d["chips"] = out
		}
	case SnippetTypeNudgeBar:
		ensureMapIconField(d, "icon", scope+".icon")
		ensureMapCountdownField(d, "countdown", scope+".countdown")
		ensureMapButtonField(d, "cta", scope+".cta")
		ensureMapActionField(d, "on_dismiss", scope+".dismiss")
		ensureMapActionField(d, "on_click", scope+".click")
	case SnippetTypeMarqueeStrip:
		if items, ok := d["items"].([]any); ok && len(items) > 0 {
			out := make([]any, len(items))
			for i, rawItem := range items {
				if im, ok := rawItem.(map[string]any); ok {
					cp := cloneAnyMap(im)
					is := fmt.Sprintf("%s.item_%d", scope, i)
					ensureMapIconField(cp, "icon", is+".icon")
					ensureMapActionField(cp, "on_click", is+".click")
					out[i] = cp
				} else {
					out[i] = rawItem
				}
			}
			d["items"] = out
		}
	case SnippetTypeOrderTracker:
		ensureMapCountdownField(d, "eta_countdown", scope+".eta_countdown")
		ensureMapButtonField(d, "primary_action", scope+".primary_action")
		ensureMapButtonField(d, "secondary_action", scope+".secondary_action")
		if steps, ok := d["steps"].([]any); ok && len(steps) > 0 {
			out := make([]any, len(steps))
			for i, rawStep := range steps {
				if sm, ok := rawStep.(map[string]any); ok {
					cp := cloneAnyMap(sm)
					ensureMapIconField(cp, "icon", fmt.Sprintf("%s.step_%d.icon", scope, i))
					out[i] = cp
				} else {
					out[i] = rawStep
				}
			}
			d["steps"] = out
		}
	case SnippetTypeComposedBlock:
		if root, ok := d["root"].(map[string]any); ok {
			d["root"] = ensureMapComposedNode(root, scope, "0")
		}
	case SnippetTypeCustomSlot:
		if evs, ok := d["on_event"].(map[string]any); ok && len(evs) > 0 {
			out := make(map[string]any, len(evs))
			for k, rawAct := range evs {
				if am, ok := rawAct.(map[string]any); ok {
					out[k] = ensureMapAction(am, scope+".event_"+k)
				} else {
					out[k] = rawAct
				}
			}
			d["on_event"] = out
		}
		if fb, ok := d["fallback"].(map[string]any); ok {
			ensured := ensureMapEnvelope(fb, pageID, path+"_fallback")
			if strings.TrimSpace(mapString(ensured, "snippet_parent_id")) == "" {
				parentID := strings.TrimPrefix(scope, pageID+".")
				if strings.HasPrefix(scope, pageID+".snippet_") {
					parentID = scope
				}
				ensured["snippet_parent_id"] = parentID
			}
			d["fallback"] = ensured
		}
	case SnippetTypeGraphCanvas:
		ensureMapNestedActionField(d, "title", "on_click", scope+".title.click")
		ensureMapNestedActionField(d, "subtitle", "on_click", scope+".subtitle.click")
		if nodes, ok := d["nodes"].([]any); ok && len(nodes) > 0 {
			out := make([]any, len(nodes))
			for i, rawNode := range nodes {
				if nm, ok := rawNode.(map[string]any); ok {
					cp := cloneAnyMap(nm)
					nodeID := strings.TrimSpace(mapString(cp, "node_id"))
					if nodeID == "" {
						nodeID = fmt.Sprintf("%s.node_%d", scope, i)
						cp["node_id"] = nodeID
					}
					nScope := nodeID
					if !strings.HasPrefix(nScope, scope+".") {
						nScope = scope + "." + nScope
					}
					ensureMapActionField(cp, "on_click", nScope+".click")
					ensureMapActionField(cp, "on_drag_end", nScope+".drag_end")
					out[i] = cp
				} else {
					out[i] = rawNode
				}
			}
			d["nodes"] = out
		}
		if edges, ok := d["edges"].([]any); ok && len(edges) > 0 {
			out := make([]any, len(edges))
			for i, rawEdge := range edges {
				if em, ok := rawEdge.(map[string]any); ok {
					cp := cloneAnyMap(em)
					edgeID := strings.TrimSpace(mapString(cp, "edge_id"))
					if edgeID == "" {
						edgeID = fmt.Sprintf("%s.edge_%d", scope, i)
						cp["edge_id"] = edgeID
					}
					eScope := edgeID
					if !strings.HasPrefix(eScope, scope+".") {
						eScope = scope + "." + eScope
					}
					ensureMapActionField(cp, "on_click", eScope+".click")
					out[i] = cp
				} else {
					out[i] = rawEdge
				}
			}
			d["edges"] = out
		}
		ensureMapActionField(d, "on_node_moved", scope+".node_moved")
		ensureMapActionField(d, "on_canvas_tap", scope+".canvas_tap")
	}
	return d
}

func ensureMapNestedEnvelopes(parent map[string]any, key, pageID, path string) {
	items, ok := parent[key].([]any)
	if !ok || len(items) == 0 {
		return
	}
	out := make([]any, len(items))
	for i, rawItem := range items {
		if em, ok := rawItem.(map[string]any); ok {
			out[i] = ensureMapEnvelope(em, pageID, path+"_"+strconv.Itoa(i))
		} else {
			out[i] = rawItem
		}
	}
	parent[key] = out
}

func ensureMapEnvelope(raw map[string]any, pageID, path string) map[string]any {
	env := cloneAnyMap(raw)
	snippetID := strings.TrimSpace(mapString(env, "snippet_id"))
	if snippetID == "" {
		snippetID = pageID + ".snippet_" + path
		env["snippet_id"] = snippetID
	}
	scope := snippetID
	if !strings.HasPrefix(scope, pageID+".") {
		scope = pageID + "." + scope
	}
	if dm, ok := env["data"].(map[string]any); ok {
		env["data"] = ensureMapSnippetData(dm, mapString(env, "snippet_type"), pageID, scope, path)
	}
	return env
}

func ensureMapComposedNode(raw map[string]any, scope, path string) map[string]any {
	node := cloneAnyMap(raw)
	nodeID := strings.TrimSpace(mapString(node, "node_id"))
	if nodeID == "" {
		nodeID = scope + ".node_" + path
		node["node_id"] = nodeID
	}
	ns := nodeID

	ensureMapActionField(node, "on_click", ns+".click")
	ensureMapButtonField(node, "button", ns+".btn")
	ensureMapImageField(node, "image", ns+".img")
	ensureMapMediaPagerField(node, "media_pager", ns+".media_pager")
	ensureMapIconField(node, "icon", ns+".icon")
	ensureMapAvatarField(node, "avatar", ns+".avatar")
	ensureMapCountdownField(node, "countdown", ns+".countdown")
	ensureMapNestedActionField(node, "text", "on_click", ns+".text.click")
	ensureMapStepperField(node, "stepper", ns+".stepper")
	ensureMapNestedActionField(node, "switch", "on_change", ns+".switch.change")
	ensureMapNestedActionField(node, "checkbox", "on_change", ns+".checkbox.change")
	ensureMapNestedActionField(node, "slider", "on_change", ns+".slider.change")
	ensureMapNestedActionField(node, "radio_group", "on_change", ns+".radio_group.change")
	ensureMapNestedActionField(node, "select", "on_change", ns+".select.change")
	ensureMapNestedActionField(node, "otp_input", "on_complete", ns+".otp.complete")
	ensureMapNestedActionField(node, "chip", "on_click", ns+".chip.click")
	ensureMapNestedActionField(node, "chip", "on_remove", ns+".chip.remove")
	ensureMapNestedActionField(node, "segmented_control", "on_change", ns+".segmented.change")
	ensureMapNestedActionField(node, "rating", "on_rate", ns+".rating.rate")
	ensureMapNestedActionField(node, "alert_banner", "on_action", ns+".alert.action")
	ensureMapNestedActionField(node, "alert_banner", "on_dismiss", ns+".alert.dismiss")
	ensureMapNestedActionField(node, "stat_tile", "on_click", ns+".stat.click")
	ensureMapNestedActionField(node, "key_value_row", "on_click", ns+".kv.click")
	ensureMapNestedActionField(node, "qr_code", "on_click", ns+".qr.click")

	if children, ok := node["children"].([]any); ok && len(children) > 0 {
		out := make([]any, len(children))
		for i, rawCh := range children {
			if chm, ok := rawCh.(map[string]any); ok {
				out[i] = ensureMapComposedNode(chm, scope, path+"_"+strconv.Itoa(i))
			} else {
				out[i] = rawCh
			}
		}
		node["children"] = out
	}
	return node
}

func ensureMapAction(raw map[string]any, fallbackID string) map[string]any {
	act := cloneAnyMap(raw)
	id := strings.TrimSpace(mapString(act, "id"))
	if id == "" {
		id = fallbackID
		act["id"] = id
	}
	if secs, ok := act["secondary_actions"].([]any); ok && len(secs) > 0 {
		out := make([]any, len(secs))
		for i, rawSec := range secs {
			if sm, ok := rawSec.(map[string]any); ok {
				out[i] = ensureMapAction(sm, fmt.Sprintf("%s.sec_%d", id, i))
			} else {
				out[i] = rawSec
			}
		}
		act["secondary_actions"] = out
	}
	return act
}

func ensureMapActionField(parent map[string]any, key, fallbackID string) {
	if am, ok := parent[key].(map[string]any); ok {
		parent[key] = ensureMapAction(am, fallbackID)
	}
}

func ensureMapNestedActionField(parent map[string]any, objKey, actionKey, fallbackID string) {
	if om, ok := parent[objKey].(map[string]any); ok {
		cp := cloneAnyMap(om)
		ensureMapActionField(cp, actionKey, fallbackID)
		parent[objKey] = cp
	}
}

func ensureMapSliceActionField(parent map[string]any, sliceKey, actionKey, scope, prefix, suffix string) {
	items, ok := parent[sliceKey].([]any)
	if !ok || len(items) == 0 {
		return
	}
	out := make([]any, len(items))
	for i, rawItem := range items {
		if im, ok := rawItem.(map[string]any); ok {
			cp := cloneAnyMap(im)
			ensureMapActionField(cp, actionKey, fmt.Sprintf("%s.%s_%d.%s", scope, prefix, i, suffix))
			out[i] = cp
		} else {
			out[i] = rawItem
		}
	}
	parent[sliceKey] = out
}

func ensureMapButtonField(parent map[string]any, key, fallbackID string) {
	bm, ok := parent[key].(map[string]any)
	if !ok {
		return
	}
	btn := cloneAnyMap(bm)
	id := strings.TrimSpace(mapString(btn, "id"))
	if id == "" {
		id = fallbackID
		btn["id"] = id
	}
	ensureMapIconField(btn, "leading_icon", id+".leading_icon")
	ensureMapIconField(btn, "trailing_icon", id+".trailing_icon")
	ensureMapActionField(btn, "on_click", id+".click")
	parent[key] = btn
}

func ensureMapImage(raw map[string]any, fallbackID string) map[string]any {
	img := cloneAnyMap(raw)
	id := strings.TrimSpace(mapString(img, "id"))
	if id == "" {
		id = fallbackID
		img["id"] = id
	}
	ensureMapActionField(img, "on_click", id+".click")
	return img
}

func ensureMapImageField(parent map[string]any, key, fallbackID string) {
	if im, ok := parent[key].(map[string]any); ok {
		parent[key] = ensureMapImage(im, fallbackID)
	}
}

func ensureMapMediaPagerField(parent map[string]any, key, fallbackID string) {
	mm, ok := parent[key].(map[string]any)
	if !ok {
		return
	}
	mp := cloneAnyMap(mm)
	id := strings.TrimSpace(mapString(mp, "id"))
	if id == "" {
		id = fallbackID
		mp["id"] = id
	}
	if items, ok := mp["items"].([]any); ok && len(items) > 0 {
		out := make([]any, len(items))
		for i, rawImg := range items {
			if im, ok := rawImg.(map[string]any); ok {
				out[i] = ensureMapImage(im, fmt.Sprintf("%s.item_%d", id, i))
			} else {
				out[i] = rawImg
			}
		}
		mp["items"] = out
	}
	ensureMapActionField(mp, "on_page_click", id+".page_click")
	parent[key] = mp
}

func ensureMapIconField(parent map[string]any, key, fallbackID string) {
	ensureMapNestedActionField(parent, key, "on_click", fallbackID+".click")
}

func ensureMapAvatarField(parent map[string]any, key, fallbackID string) {
	ensureMapNestedActionField(parent, key, "on_click", fallbackID+".click")
}

func ensureMapStepperField(parent map[string]any, key, fallbackID string) {
	if sm, ok := parent[key].(map[string]any); ok {
		cp := cloneAnyMap(sm)
		ensureMapActionField(cp, "on_increment", fallbackID+".inc")
		ensureMapActionField(cp, "on_decrement", fallbackID+".dec")
		parent[key] = cp
	}
}

func ensureMapCountdownField(parent map[string]any, key, fallbackID string) {
	ensureMapNestedActionField(parent, key, "on_expire", fallbackID+".expire")
}
