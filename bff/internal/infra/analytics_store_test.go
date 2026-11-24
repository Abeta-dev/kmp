package infra_test

import (
	"context"
	"fmt"
	"sync"
	"testing"

	"github.com/abeta-dev/go-libs/apperror"
	"github.com/abeta-dev/kmp/bff/internal/infra"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/analytics"
)

func TestInMemoryAnalyticsStore_CountersRingBufferAndConcurrency(t *testing.T) {
	ctx := context.Background()

	// Default capacity when <= 0
	defaultStore := infra.NewInMemoryAnalyticsStore(0)
	res, err := defaultStore.RecordBatch(ctx, analytics.BatchRequest{
		SessionID: "sess_1",
		Events: []analytics.Event{
			{
				EventID:     "e1",
				EventName:   analytics.EventPageOpen,
				Category:    analytics.CategoryScreen,
				PageID:      "fresh-home",
				ComponentID: "fresh_sticky_search",
				Properties:  map[string]string{"env": "test"},
			},
			{
				EventID:   "e_drop",
				EventName: "   ",
			},
		},
	})
	if err != nil || res.Accepted != 1 || res.Dropped != 1 || res.ServerTimestampEpochMs <= 0 {
		t.Fatalf("unexpected RecordBatch response: %+v, err=%v", res, err)
	}

	summary, err := defaultStore.GetSummary(ctx)
	if err != nil {
		t.Fatalf("GetSummary error: %v", err)
	}
	if summary.TotalEvents != 1 || summary.ByEventName["page_open"] != 1 || summary.ByCategory["screen"] != 1 || summary.ByPageID["fresh-home"] != 1 || summary.ByComponentID["fresh_sticky_search"] != 1 || len(summary.RecentEvents) != 1 {
		t.Fatalf("unexpected summary: %+v", summary)
	}
	// Mutating returned summary maps/events must not corrupt internal store state
	summary.ByEventName["page_open"] = 999
	summary.RecentEvents[0].Properties["env"] = "mutated"
	summary2, _ := defaultStore.GetSummary(ctx)
	if summary2.ByEventName["page_open"] != 1 || summary2.RecentEvents[0].Properties["env"] != "test" {
		t.Fatalf("store state leaked via summary mutation: %+v", summary2)
	}

	// Ring buffer wraparound beyond capacity=3
	ringStore := infra.NewInMemoryAnalyticsStore(3)
	for i := 1; i <= 5; i++ {
		if _, err := ringStore.RecordBatch(ctx, analytics.BatchRequest{
			Events: []analytics.Event{{
				EventID:     fmt.Sprintf("ev_%d", i),
				EventName:   analytics.EventNodeImpression,
				Category:    analytics.CategoryImpression,
				PageID:      "fresh-home",
				ComponentID: fmt.Sprintf("tile_%d", i),
			}},
		}); err != nil {
			t.Fatalf("RecordBatch %d failed: %v", i, err)
		}
	}
	ringSum, err := ringStore.GetSummary(ctx)
	if err != nil || ringSum.TotalEvents != 5 || len(ringSum.RecentEvents) != 3 {
		t.Fatalf("unexpected ring summary: %+v, err=%v", ringSum, err)
	}
	wantIDs := []string{"ev_3", "ev_4", "ev_5"}
	for i, want := range wantIDs {
		if ringSum.RecentEvents[i].EventID != want {
			t.Errorf("RecentEvents[%d].EventID = %q, want %q", i, ringSum.RecentEvents[i].EventID, want)
		}
	}

	// Cancelled context checks
	cancelledCtx, cancel := context.WithCancel(ctx)
	cancel()
	if _, err := ringStore.RecordBatch(cancelledCtx, analytics.BatchRequest{
		Events: []analytics.Event{{EventName: analytics.EventTrackEvent}},
	}); !apperror.Is(err, apperror.CodeUnavailable) {
		t.Fatalf("expected CodeUnavailable on cancelled RecordBatch, got %v", err)
	}
	if _, err := ringStore.GetSummary(cancelledCtx); !apperror.Is(err, apperror.CodeUnavailable) {
		t.Fatalf("expected CodeUnavailable on cancelled GetSummary, got %v", err)
	}

	// Concurrent reads and writes under -race
	var wg sync.WaitGroup
	for g := range 8 {
		wg.Add(1)
		go func(worker int) {
			defer wg.Done()
			for j := range 10 {
				_, _ = defaultStore.RecordBatch(ctx, analytics.BatchRequest{
					Events: []analytics.Event{{
						EventID:     fmt.Sprintf("w%d_%d", worker, j),
						EventName:   analytics.EventInteractionClick,
						Category:    analytics.CategoryAction,
						PageID:      "fresh-home",
						ComponentID: "tile_avocados",
					}},
				})
				_, _ = defaultStore.GetSummary(ctx)
			}
		}(g)
	}
	wg.Wait()
	finalSum, err := defaultStore.GetSummary(ctx)
	if err != nil || finalSum.TotalEvents != 81 {
		t.Fatalf("expected 81 total events after concurrent batches, got %+v, err=%v", finalSum, err)
	}
}
