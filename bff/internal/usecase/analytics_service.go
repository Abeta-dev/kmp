package usecase

import (
	"context"
	"fmt"
	"strings"
	"sync/atomic"
	"time"

	"github.com/abeta-dev/go-libs/apperror"
	"github.com/abeta-dev/kmp/bff/internal/domain"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/analytics"
)

// AnalyticsService validates, enriches, and persists batched client analytics events.
type AnalyticsService struct {
	store domain.AnalyticsStore
	seq   atomic.Uint64
}

// NewAnalyticsService constructs an AnalyticsService backed by store.
func NewAnalyticsService(store domain.AnalyticsStore) *AnalyticsService {
	return &AnalyticsService{store: store}
}

// IngestBatch validates a client BatchRequest, fills missing event_id, category,
// and timestamp_epoch_ms fields, and persists the batch via AnalyticsStore.
func (s *AnalyticsService) IngestBatch(ctx context.Context, req analytics.BatchRequest) (analytics.IngestResponse, error) {
	if s.store == nil {
		return analytics.IngestResponse{}, apperror.Internal("analytics store is not configured")
	}
	if err := analytics.ValidateBatch(req); err != nil {
		return analytics.IngestResponse{}, apperror.BadRequest(err.Error())
	}

	nowMs := time.Now().UnixMilli()
	enrichedEvents := make([]analytics.Event, len(req.Events))
	for i, ev := range req.Events {
		ev.EventName = strings.TrimSpace(ev.EventName)
		ev.EventID = strings.TrimSpace(ev.EventID)
		ev.Category = strings.TrimSpace(ev.Category)
		ev.PageID = strings.TrimSpace(ev.PageID)
		ev.SnippetID = strings.TrimSpace(ev.SnippetID)
		ev.ComponentID = strings.TrimSpace(ev.ComponentID)
		ev.ActionID = strings.TrimSpace(ev.ActionID)
		ev.ActionType = strings.TrimSpace(ev.ActionType)

		if ev.EventID == "" {
			ev.EventID = fmt.Sprintf("evt_%d_%d", nowMs, s.seq.Add(1))
		}
		if ev.TimestampEpochMs == 0 {
			ev.TimestampEpochMs = nowMs
		}
		if ev.Category == "" {
			ev.Category = defaultEventCategory(ev.EventName)
		}
		if len(ev.Properties) > 0 {
			props := make(map[string]string, len(ev.Properties))
			for k, v := range ev.Properties {
				props[k] = v
			}
			ev.Properties = props
		}
		enrichedEvents[i] = ev
	}

	enrichedReq := req
	enrichedReq.Events = enrichedEvents
	return s.store.RecordBatch(ctx, enrichedReq)
}

// GetSummary returns aggregated live analytics metrics from the underlying store.
func (s *AnalyticsService) GetSummary(ctx context.Context) (analytics.Summary, error) {
	if s.store == nil {
		return analytics.Summary{}, apperror.Internal("analytics store is not configured")
	}
	return s.store.GetSummary(ctx)
}

func defaultEventCategory(eventName string) string {
	switch {
	case eventName == analytics.EventPageOpen:
		return analytics.CategoryPageOpen
	case strings.HasSuffix(eventName, "_impression"):
		return analytics.CategoryImpression
	case eventName == analytics.EventInteractionClick:
		return analytics.CategoryInteraction
	default:
		return analytics.CategoryCustom
	}
}
