package handler

import (
	"maps"

	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
)

func refSchema(name string) map[string]any {
	return map[string]any{"$ref": "#/components/schemas/" + name}
}

func arrSchema(items map[string]any) map[string]any {
	return map[string]any{"type": "array", "items": items}
}

func enumSchema(values ...string) map[string]any {
	return map[string]any{"type": "string", "enum": values}
}

func objSchema(required []string, props map[string]any) map[string]any {
	s := map[string]any{"type": "object", "properties": props}
	if len(required) > 0 {
		s["required"] = required
	}
	return s
}

func envelopeSchema(dataSchema map[string]any) map[string]any {
	return objSchema([]string{"success"}, map[string]any{
		"success": map[string]any{"type": "boolean"},
		"data":    dataSchema,
		"error":   refSchema("APIError"),
	})
}

type schemaPrimitives struct {
	str    map[string]any
	bool   map[string]any
	int    map[string]any
	num    map[string]any
	anyObj map[string]any
	strMap map[string]any
	action map[string]any
	motion map[string]any
}

func newSchemaPrimitives() schemaPrimitives {
	strT := map[string]any{"type": "string"}
	return schemaPrimitives{
		str:    strT,
		bool:   map[string]any{"type": "boolean"},
		int:    map[string]any{"type": "integer"},
		num:    map[string]any{"type": "number"},
		anyObj: map[string]any{"type": "object", "additionalProperties": true},
		strMap: map[string]any{"type": "object", "additionalProperties": strT},
		action: refSchema("ActionTrigger"),
		motion: refSchema("MotionSpec"),
	}
}

func openAPIComponentSchemas() map[string]any {
	p := newSchemaPrimitives()
	schemas := map[string]any{
		"APIError": objSchema([]string{"code", "message"}, map[string]any{
			"code": p.str, "message": p.str, "detail": p.str,
		}),
		"ActionType": enumSchema(
			string(actions.ActionNavigate), string(actions.ActionBackPress),
			string(actions.ActionOpenDialog), string(actions.ActionDismissOverlay),
			string(actions.ActionShowToast), string(actions.ActionCallAPI),
			string(actions.ActionSaveState), string(actions.ActionDeleteState),
			string(actions.ActionTrackEvent), string(actions.ActionRefreshPage),
			string(actions.ActionCopyToClipboard), string(actions.ActionUpdateSnippets),
			string(actions.ActionBatch), string(actions.ActionTriggerFeedback),
			string(actions.ActionOpenURL), string(actions.ActionShare),
			string(actions.ActionRequestPermission), string(actions.ActionBiometricAuth),
		),
		"ValidationRule": objSchema([]string{"field_id", "rule", "message"}, map[string]any{
			"field_id": p.str, "rule": p.str, "value": p.str, "message": p.str,
		}),
		"TrackingSpec": objSchema([]string{"event_name"}, map[string]any{
			"event_name": p.str, "properties": p.strMap,
		}),
		"ActionTrigger": objSchema([]string{"id", "type"}, map[string]any{
			"id":                p.str,
			"type":              refSchema("ActionType"),
			"payload":           p.anyObj,
			"validations":       arrSchema(refSchema("ValidationRule")),
			"secondary_actions": arrSchema(p.action),
			"tracking":          refSchema("TrackingSpec"),
		}),
		"SnippetType": enumSchema(
			snippets.SnippetTypeHeader, snippets.SnippetTypeBanner,
			snippets.SnippetTypeMetricCard, snippets.SnippetTypeCard,
			snippets.SnippetTypeActionRow, snippets.SnippetTypeForm,
			snippets.SnippetTypeEmptyState, snippets.SnippetTypeDivider,
			snippets.SnippetTypeSpacer, snippets.SnippetTypeCarousel,
			snippets.SnippetTypeGrid, snippets.SnippetTypeTabs,
			snippets.SnippetTypeAccordion, snippets.SnippetTypeProductCard,
			snippets.SnippetTypeSwipeDeck, snippets.SnippetTypeSearchBar,
			snippets.SnippetTypeChipGroup, snippets.SnippetTypeNudgeBar,
			snippets.SnippetTypeMarqueeStrip, snippets.SnippetTypeOrderTracker,
			snippets.SnippetTypeComposedBlock, snippets.SnippetTypeCustomSlot,
			snippets.SnippetTypeGraphCanvas,
		),
		"PaginationSpec": objSchema([]string{"has_more"}, map[string]any{
			"next_cursor": p.str, "has_more": p.bool, "load_more_action": p.action,
		}),
		"ResponsiveColumnsSpec": objSchema(nil, map[string]any{
			"compact": p.int, "medium": p.int, "expanded": p.int,
		}),
		"ResponsiveIntSpec": objSchema(nil, map[string]any{
			"compact": p.int, "medium": p.int, "expanded": p.int,
		}),
		"AdaptivePageSpec": objSchema(nil, map[string]any{
			"layout_mode":           enumSchema(snippets.LayoutModeSingleColumn, snippets.LayoutModeCentered, snippets.LayoutModeSplitPane),
			"max_width_dp":          p.int,
			"split_pane_enabled":    p.bool,
			"secondary_snippet_ids": arrSchema(p.str),
		}),
		"SnippetEnvelope": objSchema([]string{"snippet_id", "snippet_type", "data"}, map[string]any{
			"snippet_id":        p.str,
			"snippet_parent_id": p.str,
			"snippet_type":      refSchema("SnippetType"),
			"data":              p.anyObj,
			"tracking":          p.anyObj,
			"motion":            p.motion,
			"sticky":            p.bool,
		}),
		"FloatingCartSpec": objSchema([]string{"label", "cta"}, map[string]any{
			"label": p.str, "eta_badge": p.anyObj, "cta": p.anyObj, "motion": p.motion,
			"free_delivery_threshold_cents": p.int, "nudge_prefix": p.str, "unlocked_label": p.str,
		}),
		"ScreenVersionMeta": objSchema([]string{"version"}, map[string]any{
			"version": p.str, "etag": p.str, "updated_at_epoch_ms": p.int,
		}),
		"ScreenManifestResponse": objSchema([]string{"app_version", "screens"}, map[string]any{
			"app_version":           p.str,
			"screens":               map[string]any{"type": "object", "additionalProperties": refSchema("ScreenVersionMeta")},
			"generated_at_epoch_ms": p.int,
		}),
		"SseStreamSpec": objSchema([]string{"path"}, map[string]any{
			"path": p.str, "auto_connect": p.bool, "headers": p.strMap, "retry_ms": p.int,
		}),
		"SnippetDeltaOp": enumSchema(
			string(snippets.DeltaOpAppendText),
			string(snippets.DeltaOpPatchSnippetData),
			string(snippets.DeltaOpReplaceSnippet),
			string(snippets.DeltaOpInsertSnippet),
			string(snippets.DeltaOpRemoveSnippet),
			string(snippets.DeltaOpUpdatePageMeta),
		),
		"SnippetDeltaPatch": objSchema([]string{"op"}, map[string]any{
			"op":           refSchema("SnippetDeltaOp"),
			"snippet_id":   p.str,
			"field_path":   p.str,
			"text_chunk":   p.str,
			"data_patch":   p.anyObj,
			"snippet":      refSchema("SnippetEnvelope"),
			"insert_index": p.int,
			"title":        p.str,
			"page_version": p.str,
			"done":         p.bool,
		}),
		"PageResponse": objSchema([]string{"page_id", "title", "snippets"}, map[string]any{
			"page_id":         p.str,
			"title":           p.str,
			"toolbar":         p.anyObj,
			"snippets":        arrSchema(refSchema("SnippetEnvelope")),
			"bottom_bar":      p.anyObj,
			"floating_cart":   refSchema("FloatingCartSpec"),
			"page_actions":    arrSchema(p.action),
			"pagination":      refSchema("PaginationSpec"),
			"pull_to_refresh": p.action,
			"adaptive_layout": refSchema("AdaptivePageSpec"),
			"tracking":        p.anyObj,
			"page_version":    p.str,
			"app_version":     p.str,
			"etag":            p.str,
			"stream_spec":     refSchema("SseStreamSpec"),
		}),
		"ActionExecutionInput": objSchema([]string{"trigger"}, map[string]any{
			"trigger": p.action, "form_values": p.strMap, "screen_id": p.str, "metadata": p.anyObj,
		}),
		"ActionExecutionResult": objSchema([]string{"action_id", "action_type", "status"}, map[string]any{
			"action_id":         p.str,
			"action_type":       refSchema("ActionType"),
			"status":            p.str,
			"message":           p.str,
			"follow_up_actions": arrSchema(p.action),
			"updated_state":     p.anyObj,
			"field_errors":      p.strMap,
		}),
		"TokenCatalog": objSchema([]string{"version", "colors", "typography", "spacing", "corner_radius"}, map[string]any{
			"version":       p.str,
			"colors":        arrSchema(map[string]any{"type": "object"}),
			"typography":    arrSchema(map[string]any{"type": "object"}),
			"spacing":       arrSchema(map[string]any{"type": "object"}),
			"corner_radius": arrSchema(map[string]any{"type": "object"}),
		}),
		"SwitchAtom": objSchema([]string{"field_id", "label", "checked"}, map[string]any{
			"field_id": p.str, "label": p.str, "subtitle": p.str,
			"checked": p.bool, "enabled": p.bool, "on_change": p.action, "layout": p.anyObj,
		}),
		"CheckboxAtom": objSchema([]string{"field_id", "label", "checked", "required"}, map[string]any{
			"field_id": p.str, "label": p.str, "subtitle": p.str,
			"checked": p.bool, "enabled": p.bool, "required": p.bool, "on_change": p.action, "layout": p.anyObj,
		}),
		"SliderAtom": objSchema([]string{"field_id", "value", "min_value", "max_value", "steps"}, map[string]any{
			"field_id": p.str, "label": p.str, "value": p.num, "min_value": p.num, "max_value": p.num,
			"steps": p.int, "show_value": p.bool, "unit_label": p.str, "enabled": p.bool, "on_change": p.action, "layout": p.anyObj,
		}),
		"AvatarAtom": objSchema(nil, map[string]any{
			"id": p.str, "url": p.str, "initials": p.str, "size_dp": p.int,
			"bg_color": p.anyObj, "text_color": p.anyObj, "content_description": p.str,
			"on_click": p.action, "layout": p.anyObj,
		}),
		"SkeletonAtom": objSchema([]string{"kind"}, map[string]any{
			"id": p.str, "kind": enumSchema(atoms.SkeletonText, atoms.SkeletonRectangle, atoms.SkeletonCircle),
			"width_dp": p.int, "height_dp": p.int, "layout": p.anyObj,
		}),
		"MotionSpec": objSchema(nil, map[string]any{
			"enter":          enumSchema(atoms.MotionTransitionNone, atoms.MotionTransitionFadeIn, atoms.MotionTransitionSlideUp, atoms.MotionTransitionSlideDown, atoms.MotionTransitionScaleIn, atoms.MotionTransitionSpringBounce),
			"exit":           enumSchema(atoms.MotionTransitionNone, atoms.MotionTransitionFadeIn, atoms.MotionTransitionSlideUp, atoms.MotionTransitionSlideDown, atoms.MotionTransitionScaleIn, atoms.MotionTransitionSpringBounce),
			"duration_token": enumSchema(atoms.MotionDurationInstant, atoms.MotionDurationFast, atoms.MotionDurationNormal, atoms.MotionDurationSlow),
			"duration_ms":    p.int,
			"stagger_ms":     p.int,
			"delay_ms":       p.int,
			"loop_effect": enumSchema(
				atoms.MotionLoopNone, atoms.MotionLoopPulse, atoms.MotionLoopShimmer,
				atoms.MotionLoopWiggle, atoms.MotionLoopFloat, atoms.MotionLoopHeartbeat,
				atoms.MotionLoopGlowRing, atoms.MotionLoopMarquee,
			),
		}),
		"StepperAtom": objSchema([]string{"field_id", "quantity"}, map[string]any{
			"field_id": p.str, "label": p.str, "quantity": p.int, "min_quantity": p.int, "max_quantity": p.int,
			"enabled": p.bool, "on_increment": p.action, "on_decrement": p.action, "layout": p.anyObj,
		}),
		"CountdownAtom": objSchema([]string{"countdown_id", "remaining_seconds"}, map[string]any{
			"countdown_id": p.str, "remaining_seconds": p.int, "label": p.str,
			"urgent_threshold_seconds": p.int, "show_hours": p.bool, "expired_text": p.str,
			"on_expire": p.action, "motion": p.motion,
		}),
		"ProductCardSnippetData": objSchema([]string{"product_id", "title", "price", "unit_price_cents", "stepper"}, map[string]any{
			"product_id": p.str, "title": p.anyObj, "subtitle": p.anyObj, "price": p.anyObj, "original_price": p.anyObj,
			"unit_price_cents": p.int, "badge": p.anyObj, "image": p.anyObj, "stepper": refSchema("StepperAtom"),
			"rating": p.anyObj, "on_click": p.action, "layout": p.anyObj,
		}),
		"SwipeCardItem": objSchema([]string{"card_id", "title"}, map[string]any{
			"card_id": p.str, "title": p.anyObj, "subtitle": p.anyObj, "bio": p.anyObj,
			"badge": p.anyObj, "distance_text": p.anyObj, "image": p.anyObj,
			"tags": arrSchema(p.anyObj), "on_like": p.action, "on_nope": p.action,
			"on_super_like": p.action, "on_click": p.action,
		}),
		"SwipeDeckSnippetData": objSchema([]string{"deck_id", "cards"}, map[string]any{
			"deck_id": p.str, "cards": arrSchema(refSchema("SwipeCardItem")),
			"like_label": p.str, "nope_label": p.str, "super_like_label": p.str,
			"empty_title": p.anyObj, "empty_message": p.anyObj,
			"on_like": p.action, "on_nope": p.action, "on_super_like": p.action,
			"on_undo": p.action, "layout": p.anyObj,
		}),
		"SearchBarSnippetData": objSchema([]string{"search_id", "clearable"}, map[string]any{
			"search_id": p.str, "query": p.str, "placeholder": p.str,
			"leading_icon": p.anyObj, "trailing_icon": p.anyObj, "submit_label": p.str,
			"clearable": p.bool, "on_submit": p.action, "on_clear": p.action,
			"on_trailing_click": p.action, "layout": p.anyObj,
		}),
		"FilterChipItem": objSchema([]string{"id", "label", "selected"}, map[string]any{
			"id": p.str, "label": p.str, "selected": p.bool,
			"icon": p.anyObj, "badge": p.str, "on_click": p.action,
		}),
		"ChipGroupSnippetData": objSchema([]string{"group_id", "multi_select", "chips"}, map[string]any{
			"group_id": p.str, "title": p.anyObj, "multi_select": p.bool,
			"chips": arrSchema(refSchema("FilterChipItem")), "layout": p.anyObj,
		}),
		"NudgeBarSnippetData": objSchema([]string{"nudge_id", "title", "dismissible"}, map[string]any{
			"nudge_id": p.str, "title": p.anyObj, "subtitle": p.anyObj, "icon": p.anyObj,
			"badge": p.anyObj, "countdown": refSchema("CountdownAtom"), "progress": p.num,
			"cta": p.anyObj, "dismissible": p.bool,
			"pointer_edge": enumSchema(
				snippets.PointerEdgeNone, snippets.PointerEdgeTop, snippets.PointerEdgeBottom,
				snippets.PointerEdgeStart, snippets.PointerEdgeEnd,
			),
			"on_dismiss": p.action, "on_click": p.action, "layout": p.anyObj,
		}),
		"MarqueeItem": objSchema([]string{"id", "text"}, map[string]any{
			"id": p.str, "text": p.str, "icon": p.anyObj, "badge": p.anyObj, "on_click": p.action,
		}),
		"MarqueeStripSnippetData": objSchema([]string{"strip_id", "items"}, map[string]any{
			"strip_id": p.str, "items": arrSchema(refSchema("MarqueeItem")),
			"velocity_dp_per_sec": p.int, "separator": p.str, "layout": p.anyObj,
		}),
		"OrderTrackerStep": objSchema([]string{"id", "label", "completed", "active"}, map[string]any{
			"id": p.str, "label": p.str, "caption": p.str, "icon": p.anyObj,
			"completed": p.bool, "active": p.bool,
		}),
		"OrderTrackerSnippetData": objSchema([]string{"tracker_id", "order_code", "headline", "steps"}, map[string]any{
			"tracker_id": p.str, "order_code": p.str, "headline": p.anyObj,
			"eta_countdown": refSchema("CountdownAtom"), "eta_badge": p.anyObj,
			"rider_name": p.str, "rider_caption": p.str,
			"steps":          arrSchema(refSchema("OrderTrackerStep")),
			"primary_action": p.anyObj, "secondary_action": p.anyObj, "layout": p.anyObj,
		}),
		"MediaPagerAtom": objSchema(nil, map[string]any{
			"id": p.str, "items": arrSchema(p.anyObj), "height_dp": p.int,
			"auto_advance_ms": p.int, "show_indicators": p.bool,
			"corner_radius": p.anyObj, "on_page_click": p.action, "layout": p.anyObj,
		}),
		"AnalyticsEvent": objSchema([]string{"event_name"}, map[string]any{
			"event_id": p.str, "event_name": p.str, "category": p.str,
			"page_id": p.str, "snippet_id": p.str, "component_id": p.str,
			"action_id": p.str, "action_type": p.str,
			"timestamp_epoch_ms": p.int, "properties": p.strMap,
		}),
		"AnalyticsBatchRequest": objSchema([]string{"events"}, map[string]any{
			"session_id": p.str, "user_id": p.str, "platform": p.str,
			"app_version": p.str, "events": arrSchema(refSchema("AnalyticsEvent")),
		}),
		"AnalyticsIngestResponse": objSchema([]string{"accepted", "dropped", "server_timestamp_epoch_ms"}, map[string]any{
			"accepted": p.int, "dropped": p.int, "server_timestamp_epoch_ms": p.int,
		}),
		"AnalyticsSummary": objSchema([]string{"total_events", "by_event_name", "by_category", "by_page_id", "by_component_id", "recent_events"}, map[string]any{
			"total_events":    p.int,
			"by_event_name":   p.anyObj,
			"by_category":     p.anyObj,
			"by_page_id":      p.anyObj,
			"by_component_id": p.anyObj,
			"recent_events":   arrSchema(refSchema("AnalyticsEvent")),
		}),
		"ComposedNodeKind": enumSchema(
			snippets.ComposedNodeColumn, snippets.ComposedNodeRow, snippets.ComposedNodeBox,
			snippets.ComposedNodeCard, snippets.ComposedNodeFlowRow, snippets.ComposedNodeText,
			snippets.ComposedNodeButton, snippets.ComposedNodeTag, snippets.ComposedNodeIcon,
			snippets.ComposedNodeImage, snippets.ComposedNodeAvatar, snippets.ComposedNodeProgress,
			snippets.ComposedNodeDivider, snippets.ComposedNodeSpacer, snippets.ComposedNodeStepper,
			snippets.ComposedNodeCountdown, snippets.ComposedNodeRollingCounter,
			snippets.ComposedNodeRadioGroup, snippets.ComposedNodeSelect, snippets.ComposedNodeOtpInput,
			snippets.ComposedNodeChip, snippets.ComposedNodeSegmentedControl, snippets.ComposedNodeRating,
			snippets.ComposedNodePrice, snippets.ComposedNodeBadge, snippets.ComposedNodeAlertBanner,
			snippets.ComposedNodeStatTile, snippets.ComposedNodeKeyValueRow, snippets.ComposedNodeQrCode,
			snippets.ComposedNodeScrollRow, snippets.ComposedNodeGrid, snippets.ComposedNodeTextField,
			snippets.ComposedNodeSwitch, snippets.ComposedNodeCheckbox, snippets.ComposedNodeSlider,
			snippets.ComposedNodeMediaPager,
		),
		"ComposedNode": objSchema([]string{"kind"}, map[string]any{
			"node_id": p.str, "kind": refSchema("ComposedNodeKind"),
			"spacing_dp": p.int, "alignment": enumSchema(snippets.ComposedAlignStart, snippets.ComposedAlignCenter, snippets.ComposedAlignEnd),
			"horizontal_alignment": p.str, "vertical_alignment": p.str, "main_axis_arrangement": p.str,
			"box_alignment": enumSchema(
				snippets.BoxAlignTopStart, snippets.BoxAlignTopCenter, snippets.BoxAlignTopEnd,
				snippets.BoxAlignCenterStart, snippets.BoxAlignCenter, snippets.BoxAlignCenterEnd,
				snippets.BoxAlignBottomStart, snippets.BoxAlignBottomCenter, snippets.BoxAlignBottomEnd,
			),
			"columns": p.int, "columns_by_width": refSchema("ResponsiveColumnsSpec"),
			"weight": p.num, "children": arrSchema(refSchema("ComposedNode")),
			"text": p.anyObj, "button": p.anyObj, "tag": p.anyObj, "icon": p.anyObj,
			"image": p.anyObj, "avatar": p.anyObj, "progress": p.anyObj, "divider": p.anyObj,
			"spacer": p.anyObj, "stepper": refSchema("StepperAtom"), "countdown": refSchema("CountdownAtom"),
			"counter_value": p.str, "counter_typography": p.anyObj, "counter_color": p.anyObj,
			"radio_group": p.anyObj, "select": p.anyObj, "otp_input": p.anyObj, "chip": p.anyObj,
			"segmented_control": p.anyObj, "rating": p.anyObj, "price": p.anyObj, "badge": p.anyObj,
			"alert_banner": p.anyObj, "stat_tile": p.anyObj, "key_value_row": p.anyObj, "qr_code": p.anyObj,
			"text_field": p.anyObj, "switch": refSchema("SwitchAtom"), "checkbox": refSchema("CheckboxAtom"),
			"slider": refSchema("SliderAtom"), "media_pager": refSchema("MediaPagerAtom"),
			"on_click": p.action, "tracking": refSchema("TrackingSpec"), "click_tracking": refSchema("TrackingSpec"),
			"layout": p.anyObj, "motion": p.motion,
		}),
		"ComposedBlockSnippetData": objSchema([]string{"block_id", "root"}, map[string]any{
			"block_id": p.str, "recipe_name": p.str, "root": refSchema("ComposedNode"), "layout": p.anyObj,
		}),
		"CustomSlotSnippetData": objSchema([]string{"slot_id", "slot_type"}, map[string]any{
			"slot_id":       p.str,
			"slot_type":     p.str,
			"min_height_dp": p.int,
			"aspect_ratio":  p.num,
			"initial_state": p.anyObj,
			"on_event":      map[string]any{"type": "object", "additionalProperties": p.action},
			"fallback":      refSchema("SnippetEnvelope"),
			"layout":        p.anyObj,
		}),
		"GraphEdgeStyle": enumSchema(
			string(snippets.GraphEdgeBezier),
			string(snippets.GraphEdgeStraight),
			string(snippets.GraphEdgeDashed),
		),
		"GraphNodeSpec": objSchema([]string{"node_id", "label", "x", "y"}, map[string]any{
			"node_id":      p.str,
			"label":        p.str,
			"subtitle":     p.str,
			"badge":        p.str,
			"x":            p.num,
			"y":            p.num,
			"bg_color":     p.anyObj,
			"border_color": p.anyObj,
			"icon_name":    p.str,
			"draggable":    p.bool,
			"pinned":       p.bool,
			"on_click":     p.action,
			"on_drag_end":  p.action,
			"tracking":     refSchema("TrackingSpec"),
		}),
		"GraphEdgeSpec": objSchema([]string{"edge_id", "from_node_id", "to_node_id"}, map[string]any{
			"edge_id":      p.str,
			"from_node_id": p.str,
			"to_node_id":   p.str,
			"label":        p.str,
			"style":        refSchema("GraphEdgeStyle"),
			"color":        p.anyObj,
			"highlighted":  p.bool,
			"on_click":     p.action,
		}),
		"GraphCanvasSnippetData": objSchema([]string{"canvas_id"}, map[string]any{
			"canvas_id":       p.str,
			"title":           p.anyObj,
			"subtitle":        p.anyObj,
			"height_dp":       p.int,
			"nodes":           arrSchema(refSchema("GraphNodeSpec")),
			"edges":           arrSchema(refSchema("GraphEdgeSpec")),
			"allow_pan":       p.bool,
			"allow_zoom":      p.bool,
			"allow_node_drag": p.bool,
			"min_zoom":        p.num,
			"max_zoom":        p.num,
			"on_node_moved":   p.action,
			"on_canvas_tap":   p.action,
			"layout":          p.anyObj,
		}),
	}

	maps.Copy(schemas, openAPIActionPayloadSchemas(p))
	return schemas
}

func openAPIActionPayloadSchemas(p schemaPrimitives) map[string]any {
	return map[string]any{
		"NavigatePayload": objSchema([]string{"route"}, map[string]any{
			"route": p.str, "params": p.strMap, "clear_back_stack": p.bool, "launch_mode": p.str,
		}),
		"BackPressPayload": objSchema(nil, map[string]any{
			"result": p.anyObj, "steps": p.int,
		}),
		"OpenDialogPayload": objSchema([]string{"dialog_id", "title"}, map[string]any{
			"dialog_id": p.str, "title": p.str, "message": p.str,
			"presentation":    enumSchema(actions.PresentationDialog, actions.PresentationBottomSheet),
			"dismissible":     p.bool,
			"positive_action": p.action,
			"negative_action": p.action,
			"snippets":        arrSchema(refSchema("SnippetEnvelope")),
			"sheet_screen_id": p.str,
		}),
		"DismissOverlayPayload": objSchema(nil, map[string]any{
			"overlay_id": p.str, "all": p.bool,
		}),
		"ShowToastPayload": objSchema([]string{"message"}, map[string]any{
			"message": p.str, "duration_ms": p.int, "variant": p.str,
		}),
		"CallAPIPayload": objSchema([]string{"endpoint", "method"}, map[string]any{
			"endpoint": p.str, "method": p.str, "headers": p.strMap, "body": p.anyObj,
			"on_success": p.action, "on_error": p.action,
		}),
		"SaveStatePayload": objSchema([]string{"key", "value"}, map[string]any{
			"key": p.str, "value": map[string]any{}, "scope": p.str,
		}),
		"DeleteStatePayload": objSchema([]string{"key"}, map[string]any{
			"key": p.str, "scope": p.str,
		}),
		"TrackEventPayload": objSchema([]string{"event_name"}, map[string]any{
			"event_name": p.str, "properties": p.anyObj,
		}),
		"RefreshPagePayload": objSchema(nil, map[string]any{
			"page_id": p.str, "show_loader": p.bool,
		}),
		"CopyToClipboardPayload": objSchema([]string{"text"}, map[string]any{
			"text": p.str, "label": p.str,
		}),
		"UpdateSnippetsPayload": objSchema([]string{"operation"}, map[string]any{
			"operation": p.str, "target_snippet_id": p.str, "snippets": arrSchema(p.anyObj),
		}),
		"BatchPayload": objSchema([]string{"actions"}, map[string]any{
			"actions": arrSchema(p.action), "stop_on_error": p.bool, "parallel": p.bool,
		}),
		"TriggerFeedbackPayload": objSchema([]string{"effect"}, map[string]any{
			"effect": enumSchema(actions.FeedbackCelebration, actions.FeedbackCartBounce, actions.FeedbackShakeError, actions.FeedbackHapticTick),
			"title":  p.str, "subtitle": p.str, "duration_ms": p.int, "target_id": p.str,
		}),
		"OpenURLPayload": objSchema([]string{"url"}, map[string]any{
			"url": p.str, "external_browser": p.bool, "fallback_route": p.str,
		}),
		"SharePayload": objSchema([]string{"text"}, map[string]any{
			"title": p.str, "text": p.str, "url": p.str, "chooser_title": p.str,
		}),
		"RequestPermissionPayload": objSchema([]string{"permission"}, map[string]any{
			"permission": enumSchema(actions.PermissionLocation, actions.PermissionNotifications, actions.PermissionCamera, actions.PermissionStorage),
			"rationale":  p.str, "on_granted": p.action, "on_denied": p.action,
		}),
		"BiometricAuthPayload": objSchema([]string{"reason"}, map[string]any{
			"reason": p.str, "title": p.str, "allow_device_credential": p.bool,
			"on_authenticated": p.action, "on_failed": p.action,
		}),
	}
}
