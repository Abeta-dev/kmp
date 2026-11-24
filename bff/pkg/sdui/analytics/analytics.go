// Package analytics defines pure Go SDUI analytics wire DTOs and batch
// validation helpers matching the KMP analytics pipeline 1:1.
package analytics

import (
	"fmt"
	"strings"
)

// MaxBatchSize caps the number of events accepted in a single ingest request.
const MaxBatchSize = 200

// Canonical SDUI analytics event names shared across KMP clients and the Go BFF.
const (
	EventPageOpen          = "page_open"
	EventSnippetImpression = "snippet_impression"
	EventNodeImpression    = "node_impression"
	EventInteractionClick  = "interaction_click"
	EventTrackEvent        = "track_event"
)

// Canonical analytics event categories matching KMP AnalyticsCategory wire names.
const (
	CategoryAction     = "action"
	CategoryImpression = "impression"
	CategoryScreen     = "screen"
	CategoryLifecycle  = "lifecycle"
	CategorySystem     = "system"
	CategoryError      = "error"
)

// Event represents a single structured client or BFF analytics telemetry record.
type Event struct {
	EventID          string            `json:"event_id"`
	EventName        string            `json:"event_name"`
	Category         string            `json:"category,omitempty"`
	PageID           string            `json:"page_id,omitempty"`
	SnippetID        string            `json:"snippet_id,omitempty"`
	ComponentID      string            `json:"component_id,omitempty"`
	ActionID         string            `json:"action_id,omitempty"`
	ActionType       string            `json:"action_type,omitempty"`
	TimestampEpochMs int64             `json:"timestamp_epoch_ms,omitempty"`
	Properties       map[string]string `json:"properties,omitempty"`
}

// BatchRequest wraps a batch of analytics events submitted by a KMP client.
type BatchRequest struct {
	SessionID  string  `json:"session_id,omitempty"`
	UserID     string  `json:"user_id,omitempty"`
	Platform   string  `json:"platform,omitempty"`
	AppVersion string  `json:"app_version,omitempty"`
	Events     []Event `json:"events"`
}

// IngestResponse reports how many events in a batch were accepted or dropped.
type IngestResponse struct {
	Accepted               int   `json:"accepted"`
	Dropped                int   `json:"dropped"`
	ServerTimestampEpochMs int64 `json:"server_timestamp_epoch_ms"`
}

// Summary provides live aggregated telemetry counters and recent events.
type Summary struct {
	TotalEvents   int64            `json:"total_events"`
	ByEventName   map[string]int64 `json:"by_event_name"`
	ByCategory    map[string]int64 `json:"by_category"`
	ByPageID      map[string]int64 `json:"by_page_id"`
	ByComponentID map[string]int64 `json:"by_component_id"`
	RecentEvents  []Event          `json:"recent_events"`
}

// ValidateBatch verifies that a BatchRequest contains between 1 and MaxBatchSize
// well-formed events with non-empty event names and non-negative timestamps.
func ValidateBatch(req BatchRequest) error {
	if len(req.Events) == 0 {
		return fmt.Errorf("analytics: events must not be empty")
	}
	if len(req.Events) > MaxBatchSize {
		return fmt.Errorf("analytics: batch size %d exceeds maximum of %d events", len(req.Events), MaxBatchSize)
	}
	for i, ev := range req.Events {
		if strings.TrimSpace(ev.EventName) == "" {
			return fmt.Errorf("analytics: events[%d].event_name must not be empty", i)
		}
		if ev.TimestampEpochMs < 0 {
			return fmt.Errorf("analytics: events[%d].timestamp_epoch_ms must not be negative", i)
		}
	}
	return nil
}
