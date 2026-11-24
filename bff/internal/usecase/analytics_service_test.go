package usecase_test

import (
	"context"
	"testing"

	"github.com/abeta-dev/go-libs/apperror"
	"github.com/abeta-dev/kmp/bff/internal/usecase"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/analytics"
)

type stubAnalyticsStore struct {
	recorded analytics.BatchRequest
	resp     analytics.IngestResponse
	summary  analytics.Summary
	err      error
}

func (s *stubAnalyticsStore) RecordBatch(_ context.Context, req analytics.BatchRequest) (analytics.IngestResponse, error) {
	s.recorded = req
	return s.resp, s.err
}

func (s *stubAnalyticsStore) GetSummary(_ context.Context) (analytics.Summary, error) {
	return s.summary, s.err
}

func TestAnalyticsService(t *testing.T) {
	ctx := context.Background()

	// Nil store -> internal error
	nilSvc := usecase.NewAnalyticsService(nil)
	if _, err := nilSvc.IngestBatch(ctx, analytics.BatchRequest{
		Events: []analytics.Event{{EventName: analytics.EventPageOpen}},
	}); !apperror.Is(err, apperror.CodeInternal) {
		t.Fatalf("expected CodeInternal on nil store IngestBatch, got %v", err)
	}
	if _, err := nilSvc.GetSummary(ctx); !apperror.Is(err, apperror.CodeInternal) {
		t.Fatalf("expected CodeInternal on nil store GetSummary, got %v", err)
	}

	store := &stubAnalyticsStore{
		resp: analytics.IngestResponse{Accepted: 3, Dropped: 0, ServerTimestampEpochMs: 12345},
		summary: analytics.Summary{
			TotalEvents: 3,
			ByEventName: map[string]int64{"page_open": 1, "node_impression": 1, "interaction_click": 1},
		},
	}
	svc := usecase.NewAnalyticsService(store)

	// Invalid batch -> CodeBadRequest
	if _, err := svc.IngestBatch(ctx, analytics.BatchRequest{}); !apperror.Is(err, apperror.CodeBadRequest) {
		t.Fatalf("expected CodeBadRequest on empty batch, got %v", err)
	}

	// Enrichment of missing event_id, timestamp_epoch_ms, default categories, and defensive properties copy
	srcProps := map[string]string{"sku": "avocados"}
	resp, err := svc.IngestBatch(ctx, analytics.BatchRequest{
		SessionID: "sess_1",
		Platform:  "ios",
		Events: []analytics.Event{
			{EventName: " page_open ", PageID: " fresh-home "},
			{EventName: analytics.EventNodeImpression, ComponentID: "tile_avocados", Properties: srcProps},
			{EventName: analytics.EventInteractionClick, EventID: "explicit_id", Category: "custom", TimestampEpochMs: 999},
		},
	})
	if err != nil || resp.Accepted != 3 {
		t.Fatalf("unexpected IngestBatch result: %+v, err=%v", resp, err)
	}
	srcProps["sku"] = "mutated"

	if len(store.recorded.Events) != 3 {
		t.Fatalf("expected 3 recorded events, got %d", len(store.recorded.Events))
	}
	e0 := store.recorded.Events[0]
	if e0.EventID == "" || e0.TimestampEpochMs == 0 || e0.EventName != "page_open" || e0.PageID != "fresh-home" || e0.Category != analytics.CategoryScreen {
		t.Fatalf("unexpected enriched event[0]: %+v", e0)
	}
	e1 := store.recorded.Events[1]
	if e1.EventID == "" || e1.Category != analytics.CategoryImpression || e1.Properties["sku"] != "avocados" {
		t.Fatalf("unexpected enriched event[1]: %+v", e1)
	}
	e2 := store.recorded.Events[2]
	if e2.EventID != "explicit_id" || e2.Category != "custom" || e2.TimestampEpochMs != 999 {
		t.Fatalf("expected explicit fields preserved on event[2]: %+v", e2)
	}

	// Default action category for non-impression/non-page_open events
	if _, err := svc.IngestBatch(ctx, analytics.BatchRequest{
		Events: []analytics.Event{{EventName: analytics.EventTrackEvent}},
	}); err != nil {
		t.Fatalf("IngestBatch track_event failed: %v", err)
	}
	if store.recorded.Events[0].Category != analytics.CategoryAction {
		t.Fatalf("expected default category action, got %q", store.recorded.Events[0].Category)
	}

	// Summary delegation
	sum, err := svc.GetSummary(ctx)
	if err != nil || sum.TotalEvents != 3 {
		t.Fatalf("unexpected GetSummary result: %+v, err=%v", sum, err)
	}
}
