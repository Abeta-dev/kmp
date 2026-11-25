package infra

import (
	"context"
	"maps"
	"strings"
	"sync"
	"time"

	"github.com/abeta-dev/go-libs/apperror"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/analytics"
)

const (
	defaultAnalyticsRingCapacity = 100
	maxAnalyticsDimensionKeys    = 1000
	analyticsOverflowKey         = "_other"
)

// InMemoryAnalyticsStore is a concurrency-safe in-memory AnalyticsStore tracking
// aggregated event counters and a bounded circular ring buffer of recent events.
type InMemoryAnalyticsStore struct {
	mu            sync.RWMutex
	maxRecent     int
	totalEvents   int64
	byEventName   map[string]int64
	byCategory    map[string]int64
	byPageID      map[string]int64
	byComponentID map[string]int64
	ring          []analytics.Event
	head          int
	size          int
}

// NewInMemoryAnalyticsStore creates an InMemoryAnalyticsStore keeping at most
// maxRecent recent events (defaults to 100 when maxRecent <= 0).
func NewInMemoryAnalyticsStore(maxRecent int) *InMemoryAnalyticsStore {
	if maxRecent <= 0 {
		maxRecent = defaultAnalyticsRingCapacity
	}
	return &InMemoryAnalyticsStore{
		maxRecent:     maxRecent,
		byEventName:   make(map[string]int64),
		byCategory:    make(map[string]int64),
		byPageID:      make(map[string]int64),
		byComponentID: make(map[string]int64),
		ring:          make([]analytics.Event, maxRecent),
	}
}

// RecordBatch ingests all valid events in req, updates dimension counters,
// appends to the bounded ring buffer, and returns an IngestResponse.
func (s *InMemoryAnalyticsStore) RecordBatch(ctx context.Context, req analytics.BatchRequest) (analytics.IngestResponse, error) {
	if err := ctx.Err(); err != nil {
		return analytics.IngestResponse{}, apperror.Unavailable("request cancelled: " + err.Error())
	}

	s.mu.Lock()
	defer s.mu.Unlock()

	accepted := 0
	dropped := 0
	for _, ev := range req.Events {
		if strings.TrimSpace(ev.EventName) == "" {
			dropped++
			continue
		}
		cloned := cloneAnalyticsEvent(ev)
		s.totalEvents++
		incrementBoundedDimension(s.byEventName, cloned.EventName)
		if cloned.Category != "" {
			incrementBoundedDimension(s.byCategory, cloned.Category)
		}
		if cloned.PageID != "" {
			incrementBoundedDimension(s.byPageID, cloned.PageID)
		}
		if cloned.ComponentID != "" {
			incrementBoundedDimension(s.byComponentID, cloned.ComponentID)
		}

		s.ring[s.head] = cloned
		s.head = (s.head + 1) % s.maxRecent
		if s.size < s.maxRecent {
			s.size++
		}
		accepted++
	}

	return analytics.IngestResponse{
		Accepted:               accepted,
		Dropped:                dropped,
		ServerTimestampEpochMs: time.Now().UnixMilli(),
	}, nil
}

// GetSummary returns a concurrency-safe snapshot of aggregated counters and
// the bounded ring buffer of recent events in chronological order.
func (s *InMemoryAnalyticsStore) GetSummary(ctx context.Context) (analytics.Summary, error) {
	if err := ctx.Err(); err != nil {
		return analytics.Summary{}, apperror.Unavailable("request cancelled: " + err.Error())
	}

	s.mu.RLock()
	defer s.mu.RUnlock()

	recent := make([]analytics.Event, 0, s.size)
	start := 0
	if s.size == s.maxRecent {
		start = s.head
	}
	for i := range s.size {
		idx := (start + i) % s.maxRecent
		recent = append(recent, cloneAnalyticsEvent(s.ring[idx]))
	}

	return analytics.Summary{
		TotalEvents:   s.totalEvents,
		ByEventName:   cloneCounterMap(s.byEventName),
		ByCategory:    cloneCounterMap(s.byCategory),
		ByPageID:      cloneCounterMap(s.byPageID),
		ByComponentID: cloneCounterMap(s.byComponentID),
		RecentEvents:  recent,
	}, nil
}

func cloneAnalyticsEvent(ev analytics.Event) analytics.Event {
	if len(ev.Properties) > 0 {
		props := make(map[string]string, len(ev.Properties))
		maps.Copy(props, ev.Properties)
		ev.Properties = props
	}
	return ev
}

func incrementBoundedDimension(m map[string]int64, key string) {
	if _, exists := m[key]; exists {
		m[key]++
		return
	}
	if len(m) >= maxAnalyticsDimensionKeys {
		m[analyticsOverflowKey]++
		return
	}
	m[key] = 1
}

func cloneCounterMap(src map[string]int64) map[string]int64 {
	dst := make(map[string]int64, len(src))
	maps.Copy(dst, src)
	return dst
}
