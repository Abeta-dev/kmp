package actions_test

import (
	"encoding/json"
	"testing"

	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
)

func TestActionConstructorsAndDecode(t *testing.T) {
	boolTrue := true
	sub := actions.NewShowToastAction("toast_1", actions.ShowToastPayload{
		Message:    "Saved",
		DurationMs: 1500,
		Variant:    "success",
	})

	cases := []struct {
		name    string
		trigger actions.ActionTrigger
		want    actions.ActionType
		verify  func(t *testing.T, tr actions.ActionTrigger)
	}{
		{
			name: "navigate",
			trigger: actions.NewNavigateAction("nav_1", actions.NavigatePayload{
				Route:          "/catalog",
				Params:         map[string]string{"tab": "tokens"},
				ClearBackStack: true,
				LaunchMode:     "single_top",
			}),
			want: actions.ActionNavigate,
			verify: func(t *testing.T, tr actions.ActionTrigger) {
				p, err := actions.DecodePayload[actions.NavigatePayload](tr)
				if err != nil || p.Route != "/catalog" || !p.ClearBackStack || p.Params["tab"] != "tokens" {
					t.Fatalf("unexpected navigate decode: %+v, err=%v", p, err)
				}
			},
		},
		{
			name:    "back_press",
			trigger: actions.NewBackPressAction("back_1", actions.BackPressPayload{Steps: 2, Result: map[string]any{"ok": true}}),
			want:    actions.ActionBackPress,
			verify: func(t *testing.T, tr actions.ActionTrigger) {
				p, err := actions.DecodePayload[actions.BackPressPayload](tr)
				if err != nil || p.Steps != 2 {
					t.Fatalf("unexpected back_press decode: %+v, err=%v", p, err)
				}
			},
		},
		{
			name: "open_dialog",
			trigger: actions.NewOpenDialogAction("dlg_1", actions.OpenDialogPayload{
				DialogID:       "confirm",
				Title:          "Confirm",
				Message:        "Proceed?",
				Presentation:   actions.PresentationBottomSheet,
				Dismissible:    &boolTrue,
				PositiveAction: &sub,
				Snippets:       []any{map[string]any{"snippet_id": "sheet_s1", "snippet_type": "composed_block"}},
				SheetScreenID:  "variant-sheet",
			}),
			want: actions.ActionOpenDialog,
			verify: func(t *testing.T, tr actions.ActionTrigger) {
				p, err := actions.DecodePayload[actions.OpenDialogPayload](tr)
				if err != nil || p.DialogID != "confirm" || p.Presentation != actions.PresentationBottomSheet || p.PositiveAction == nil || len(p.Snippets) != 1 || p.SheetScreenID != "variant-sheet" {
					t.Fatalf("unexpected open_dialog decode: %+v, err=%v", p, err)
				}
				if actions.PresentationDialog != "dialog" || actions.PresentationBottomSheet != "bottom_sheet" {
					t.Fatalf("unexpected presentation constants")
				}
			},
		},
		{
			name:    "dismiss_overlay",
			trigger: actions.NewDismissOverlayAction("dis_1", actions.DismissOverlayPayload{OverlayID: "confirm", All: false}),
			want:    actions.ActionDismissOverlay,
			verify: func(t *testing.T, tr actions.ActionTrigger) {
				p, err := actions.DecodePayload[actions.DismissOverlayPayload](tr)
				if err != nil || p.OverlayID != "confirm" {
					t.Fatalf("unexpected dismiss_overlay decode: %+v, err=%v", p, err)
				}
			},
		},
		{
			name:    "show_toast",
			trigger: sub,
			want:    actions.ActionShowToast,
			verify: func(t *testing.T, tr actions.ActionTrigger) {
				p, err := actions.DecodePayload[actions.ShowToastPayload](tr)
				if err != nil || p.Message != "Saved" || p.DurationMs != 1500 {
					t.Fatalf("unexpected show_toast decode: %+v, err=%v", p, err)
				}
			},
		},
		{
			name: "call_api",
			trigger: actions.NewCallAPIAction("api_1", actions.CallAPIPayload{
				Endpoint:  "/api/v1/submit",
				Method:    "POST",
				Headers:   map[string]string{"X-Test": "1"},
				Body:      map[string]any{"name": "alex"},
				OnSuccess: &sub,
			}),
			want: actions.ActionCallAPI,
			verify: func(t *testing.T, tr actions.ActionTrigger) {
				p, err := actions.DecodePayload[actions.CallAPIPayload](tr)
				if err != nil || p.Endpoint != "/api/v1/submit" || p.Method != "POST" {
					t.Fatalf("unexpected call_api decode: %+v, err=%v", p, err)
				}
			},
		},
		{
			name:    "save_state",
			trigger: actions.NewSaveStateAction("save_1", actions.SaveStatePayload{Key: "theme", Value: "dark", Scope: "disk"}),
			want:    actions.ActionSaveState,
			verify: func(t *testing.T, tr actions.ActionTrigger) {
				p, err := actions.DecodePayload[actions.SaveStatePayload](tr)
				if err != nil || p.Key != "theme" || p.Value != "dark" {
					t.Fatalf("unexpected save_state decode: %+v, err=%v", p, err)
				}
			},
		},
		{
			name:    "delete_state",
			trigger: actions.NewDeleteStateAction("del_1", actions.DeleteStatePayload{Key: "theme", Scope: "disk"}),
			want:    actions.ActionDeleteState,
			verify: func(t *testing.T, tr actions.ActionTrigger) {
				p, err := actions.DecodePayload[actions.DeleteStatePayload](tr)
				if err != nil || p.Key != "theme" {
					t.Fatalf("unexpected delete_state decode: %+v, err=%v", p, err)
				}
			},
		},
		{
			name:    "track_event",
			trigger: actions.NewTrackEventAction("trk_1", actions.TrackEventPayload{EventName: "clicked", Properties: map[string]any{"k": "v"}}),
			want:    actions.ActionTrackEvent,
			verify: func(t *testing.T, tr actions.ActionTrigger) {
				p, err := actions.DecodePayload[actions.TrackEventPayload](tr)
				if err != nil || p.EventName != "clicked" {
					t.Fatalf("unexpected track_event decode: %+v, err=%v", p, err)
				}
			},
		},
		{
			name:    "refresh_page",
			trigger: actions.NewRefreshPageAction("ref_1", actions.RefreshPagePayload{PageID: "home", ShowLoader: true}),
			want:    actions.ActionRefreshPage,
			verify: func(t *testing.T, tr actions.ActionTrigger) {
				p, err := actions.DecodePayload[actions.RefreshPagePayload](tr)
				if err != nil || p.PageID != "home" || !p.ShowLoader {
					t.Fatalf("unexpected refresh_page decode: %+v, err=%v", p, err)
				}
			},
		},
		{
			name:    "copy_to_clipboard",
			trigger: actions.NewCopyToClipboardAction("cpy_1", actions.CopyToClipboardPayload{Text: "abc", Label: "Token"}),
			want:    actions.ActionCopyToClipboard,
			verify: func(t *testing.T, tr actions.ActionTrigger) {
				p, err := actions.DecodePayload[actions.CopyToClipboardPayload](tr)
				if err != nil || p.Text != "abc" {
					t.Fatalf("unexpected copy_to_clipboard decode: %+v, err=%v", p, err)
				}
			},
		},
		{
			name: "update_snippets",
			trigger: actions.NewUpdateSnippetsAction("upd_1", actions.UpdateSnippetsPayload{
				Operation:       "replace",
				TargetSnippetID: "snip_1",
				Snippets:        []json.RawMessage{json.RawMessage(`{"snippet_id":"snip_1"}`)},
			}),
			want: actions.ActionUpdateSnippets,
			verify: func(t *testing.T, tr actions.ActionTrigger) {
				p, err := actions.DecodePayload[actions.UpdateSnippetsPayload](tr)
				if err != nil || p.Operation != "replace" || len(p.Snippets) != 1 {
					t.Fatalf("unexpected update_snippets decode: %+v, err=%v", p, err)
				}
			},
		},
		{
			name: "batch",
			trigger: actions.NewBatchAction("bat_1", actions.BatchPayload{
				Actions:     []actions.ActionTrigger{sub},
				StopOnError: true,
			}),
			want: actions.ActionBatch,
			verify: func(t *testing.T, tr actions.ActionTrigger) {
				p, err := actions.DecodePayload[actions.BatchPayload](tr)
				if err != nil || len(p.Actions) != 1 || !p.StopOnError {
					t.Fatalf("unexpected batch decode: %+v, err=%v", p, err)
				}
			},
		},
		{
			name: "trigger_feedback",
			trigger: actions.NewTriggerFeedbackAction("fb_1", actions.TriggerFeedbackPayload{
				Effect:     actions.FeedbackCelebration,
				Title:      "Order Confirmed!",
				Subtitle:   "Abeta Fresh rider arriving in 8 minutes",
				DurationMs: 2400,
				TargetID:   "floating_cart_dock",
			}),
			want: actions.ActionTriggerFeedback,
			verify: func(t *testing.T, tr actions.ActionTrigger) {
				p, err := actions.DecodePayload[actions.TriggerFeedbackPayload](tr)
				if err != nil || p.Effect != actions.FeedbackCelebration || p.Title != "Order Confirmed!" || p.DurationMs != 2400 || p.TargetID != "floating_cart_dock" {
					t.Fatalf("unexpected trigger_feedback decode: %+v, err=%v", p, err)
				}
				if actions.FeedbackCelebration != "celebration" ||
					actions.FeedbackCartBounce != "cart_bounce" ||
					actions.FeedbackShakeError != "shake_error" ||
					actions.FeedbackHapticTick != "haptic_tick" {
					t.Fatal("unexpected FeedbackEffect constant values")
				}
				var wire map[string]any
				if err := json.Unmarshal(tr.Payload, &wire); err != nil {
					t.Fatalf("unmarshal trigger_feedback payload map: %v", err)
				}
				for _, k := range []string{"effect", "title", "subtitle", "duration_ms", "target_id"} {
					if _, ok := wire[k]; !ok {
						t.Errorf("missing expected key %q in trigger_feedback payload", k)
					}
				}
			},
		},
		{
			name: "open_url",
			trigger: actions.NewOpenURLAction("url_1", actions.OpenURLPayload{
				URL:           "https://abeta.dev/docs",
				FallbackRoute: "/screens/home",
			}),
			want: actions.ActionOpenURL,
			verify: func(t *testing.T, tr actions.ActionTrigger) {
				p, err := actions.DecodePayload[actions.OpenURLPayload](tr)
				if err != nil || p.URL != "https://abeta.dev/docs" || p.FallbackRoute != "/screens/home" {
					t.Fatalf("unexpected open_url decode: %+v, err=%v", p, err)
				}
			},
		},
		{
			name: "share",
			trigger: actions.NewShareAction("shr_1", actions.SharePayload{
				Title: "Abeta Order",
				Text:  "Arriving in 8 minutes",
				URL:   "https://app.abeta.dev/screens/store-home",
			}),
			want: actions.ActionShare,
			verify: func(t *testing.T, tr actions.ActionTrigger) {
				p, err := actions.DecodePayload[actions.SharePayload](tr)
				if err != nil || p.Title != "Abeta Order" || p.Text != "Arriving in 8 minutes" {
					t.Fatalf("unexpected share decode: %+v, err=%v", p, err)
				}
			},
		},
		{
			name: "request_permission",
			trigger: actions.NewRequestPermissionAction("perm_1", actions.RequestPermissionPayload{
				Permission: actions.PermissionLocation,
				Rationale:  "Find nearest delivery hub",
			}),
			want: actions.ActionRequestPermission,
			verify: func(t *testing.T, tr actions.ActionTrigger) {
				p, err := actions.DecodePayload[actions.RequestPermissionPayload](tr)
				if err != nil || p.Permission != actions.PermissionLocation {
					t.Fatalf("unexpected request_permission decode: %+v, err=%v", p, err)
				}
			},
		},
		{
			name: "biometric_auth",
			trigger: actions.NewBiometricAuthAction("bio_1", actions.BiometricAuthPayload{
				Reason: "Confirm express checkout",
			}),
			want: actions.ActionBiometricAuth,
			verify: func(t *testing.T, tr actions.ActionTrigger) {
				p, err := actions.DecodePayload[actions.BiometricAuthPayload](tr)
				if err != nil || p.Reason != "Confirm express checkout" {
					t.Fatalf("unexpected biometric_auth decode: %+v, err=%v", p, err)
				}
			},
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if tc.trigger.Type != tc.want {
				t.Fatalf("got type %q, want %q", tc.trigger.Type, tc.want)
			}
			raw, err := json.Marshal(tc.trigger)
			if err != nil {
				t.Fatalf("marshal error: %v", err)
			}
			var decoded actions.ActionTrigger
			if err := json.Unmarshal(raw, &decoded); err != nil {
				t.Fatalf("unmarshal error: %v", err)
			}
			tc.verify(t, decoded)
		})
	}
}

func TestTriggerBuildersAndErrors(t *testing.T) {
	tr, err := actions.NewTrigger("empty", actions.ActionBackPress, nil)
	if err != nil || tr.Payload != nil {
		t.Fatalf("expected nil payload without error, got %+v, err=%v", tr, err)
	}

	if _, err := actions.DecodePayload[actions.BackPressPayload](tr); err == nil {
		t.Fatal("expected error decoding empty payload")
	}

	badJSON := actions.ActionTrigger{
		ID:      "bad",
		Type:    actions.ActionNavigate,
		Payload: json.RawMessage(`{invalid`),
	}
	if _, err := actions.DecodePayload[actions.NavigatePayload](badJSON); err == nil {
		t.Fatal("expected error decoding malformed payload")
	}

	if _, err := actions.NewTrigger("bad_marshal", actions.ActionSaveState, make(chan int)); err == nil {
		t.Fatal("expected error marshaling unsupported payload type")
	}

	props := map[string]string{"surface": "nav_bar", "sku": "avocados"}
	enriched := actions.NewNavigateAction("nav", actions.NavigatePayload{Route: "/home"}).
		WithValidations(actions.ValidationRule{
			FieldID: "email",
			Rule:    "required",
			Message: "Email required",
		}).
		WithSecondaryActions(actions.NewTrackEventAction("trk", actions.TrackEventPayload{EventName: "nav"})).
		WithTracking("nav_clicked", props)
	props["sku"] = "mutated"
	if enriched.Tracking == nil || enriched.Tracking.Properties["sku"] != "avocados" {
		t.Fatalf("expected defensive copy of tracking properties, got %+v", enriched.Tracking)
	}

	raw, err := json.Marshal(enriched)
	if err != nil {
		t.Fatalf("marshal enriched trigger: %v", err)
	}
	var wire map[string]any
	if err := json.Unmarshal(raw, &wire); err != nil {
		t.Fatalf("unmarshal wire map: %v", err)
	}
	for _, key := range []string{"id", "type", "payload", "validations", "secondary_actions", "tracking"} {
		if _, ok := wire[key]; !ok {
			t.Errorf("missing expected snake_case wire key %q", key)
		}
	}
	trackingMap, ok := wire["tracking"].(map[string]any)
	if !ok || trackingMap["event_name"] != "nav_clicked" {
		t.Fatalf("unexpected tracking wire payload: %+v", wire["tracking"])
	}

	// Empty tracking props omits properties key
	noProps := actions.NewBackPressAction("bp", actions.BackPressPayload{}).WithTracking("back_pressed", nil)
	noPropsRaw, err := json.Marshal(noProps)
	if err != nil {
		t.Fatalf("marshal noProps trigger: %v", err)
	}
	var noPropsWire map[string]any
	if err := json.Unmarshal(noPropsRaw, &noPropsWire); err != nil {
		t.Fatalf("unmarshal noProps wire map: %v", err)
	}
	if _, hasProps := noPropsWire["tracking"].(map[string]any)["properties"]; hasProps {
		t.Errorf("expected nil tracking properties to be omitted")
	}
}
