package analytics_test

import (
	"encoding/json"
	"reflect"
	"testing"

	"github.com/abeta-dev/kmp/bff/pkg/sdui/analytics"
)

func TestAnalyticsWireRoundTripAndConstants(t *testing.T) {
	if analytics.EventPageOpen != "page_open" ||
		analytics.EventSnippetImpression != "snippet_impression" ||
		analytics.EventNodeImpression != "node_impression" ||
		analytics.EventInteractionClick != "interaction_click" ||
		analytics.EventTrackEvent != "track_event" {
		t.Fatal("unexpected canonical analytics event name constants")
	}
	if analytics.CategoryAction != "action" ||
		analytics.CategoryImpression != "impression" ||
		analytics.CategoryScreen != "screen" ||
		analytics.CategoryLifecycle != "lifecycle" ||
		analytics.CategorySystem != "system" ||
		analytics.CategoryError != "error" {
		t.Fatal("unexpected canonical analytics category constants")
	}

	req := analytics.BatchRequest{
		SessionID:  "sess_01",
		UserID:     "usr_42",
		Platform:   "ios",
		AppVersion: "1.4.0",
		Events: []analytics.Event{
			{
				EventID:          "evt_1",
				EventName:        analytics.EventNodeImpression,
				Category:         analytics.CategoryImpression,
				PageID:           "fresh-home",
				SnippetID:        "fresh_product_grid",
				ComponentID:      "tile_avocados",
				ActionID:         "act_open_avocados",
				ActionType:       "open_dialog",
				TimestampEpochMs: 1735689600000,
				Properties:       map[string]string{"sku": "avocados", "eta": "8m"},
			},
		},
	}

	raw, err := json.Marshal(req)
	if err != nil {
		t.Fatalf("marshal BatchRequest: %v", err)
	}
	var gotReq analytics.BatchRequest
	if err := json.Unmarshal(raw, &gotReq); err != nil {
		t.Fatalf("unmarshal BatchRequest: %v", err)
	}
	if !reflect.DeepEqual(gotReq, req) {
		t.Fatalf("BatchRequest round-trip mismatch:\ngot:  %+v\nwant: %+v", gotReq, req)
	}

	var wireMap map[string]any
	if err := json.Unmarshal(raw, &wireMap); err != nil {
		t.Fatalf("unmarshal BatchRequest map: %v", err)
	}
	for _, k := range []string{"session_id", "user_id", "platform", "app_version", "events"} {
		if _, ok := wireMap[k]; !ok {
			t.Errorf("missing expected key %q in BatchRequest JSON", k)
		}
	}
	evMap := wireMap["events"].([]any)[0].(map[string]any)
	for _, k := range []string{
		"event_id", "event_name", "category", "page_id", "snippet_id",
		"component_id", "action_id", "action_type", "timestamp_epoch_ms", "properties",
	} {
		if _, ok := evMap[k]; !ok {
			t.Errorf("missing expected key %q in Event JSON", k)
		}
	}

	resp := analytics.IngestResponse{
		Accepted:               1,
		Dropped:                0,
		ServerTimestampEpochMs: 1735689600123,
	}
	respRaw, err := json.Marshal(resp)
	if err != nil {
		t.Fatalf("marshal IngestResponse: %v", err)
	}
	var gotResp analytics.IngestResponse
	if err := json.Unmarshal(respRaw, &gotResp); err != nil || !reflect.DeepEqual(gotResp, resp) {
		t.Fatalf("IngestResponse round-trip mismatch: got=%+v err=%v", gotResp, err)
	}

	sum := analytics.Summary{
		TotalEvents:   1,
		ByEventName:   map[string]int64{"node_impression": 1},
		ByCategory:    map[string]int64{"impression": 1},
		ByPageID:      map[string]int64{"fresh-home": 1},
		ByComponentID: map[string]int64{"tile_avocados": 1},
		RecentEvents:  req.Events,
	}
	sumRaw, err := json.Marshal(sum)
	if err != nil {
		t.Fatalf("marshal Summary: %v", err)
	}
	var gotSum analytics.Summary
	if err := json.Unmarshal(sumRaw, &gotSum); err != nil || !reflect.DeepEqual(gotSum, sum) {
		t.Fatalf("Summary round-trip mismatch: got=%+v err=%v", gotSum, err)
	}
}

func TestValidateBatch(t *testing.T) {
	valid := analytics.BatchRequest{
		Events: []analytics.Event{
			{EventName: analytics.EventPageOpen, TimestampEpochMs: 100},
		},
	}
	if err := analytics.ValidateBatch(valid); err != nil {
		t.Fatalf("expected valid batch, got %v", err)
	}

	if err := analytics.ValidateBatch(analytics.BatchRequest{}); err == nil {
		t.Fatal("expected error for empty events")
	}

	tooMany := make([]analytics.Event, analytics.MaxBatchSize+1)
	for i := range tooMany {
		tooMany[i] = analytics.Event{EventName: analytics.EventTrackEvent}
	}
	if err := analytics.ValidateBatch(analytics.BatchRequest{Events: tooMany}); err == nil {
		t.Fatal("expected error for oversized batch")
	}

	if err := analytics.ValidateBatch(analytics.BatchRequest{
		Events: []analytics.Event{{EventName: "   "}},
	}); err == nil {
		t.Fatal("expected error for blank event_name")
	}

	if err := analytics.ValidateBatch(analytics.BatchRequest{
		Events: []analytics.Event{{EventName: analytics.EventInteractionClick, TimestampEpochMs: -1}},
	}); err == nil {
		t.Fatal("expected error for negative timestamp_epoch_ms")
	}
}
