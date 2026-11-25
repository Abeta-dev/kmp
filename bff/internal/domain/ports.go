// Package domain defines pure business interfaces (ports) and domain models
// following Uncle Bob's Clean Architecture dependency rule.
// This package must never import HTTP, Gin, or database libraries.
package domain

import (
	"context"

	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/analytics"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

// ActionExecutionInput captures an incoming SDUI action trigger and optional
// form/screen context submitted by a client.
type ActionExecutionInput struct {
	Trigger    actions.ActionTrigger `json:"trigger"`
	FormValues map[string]string     `json:"form_values,omitempty"`
	ScreenID   string                `json:"screen_id,omitempty"`
	Metadata   map[string]any        `json:"metadata,omitempty"`
}

// ActionExecutionResult describes the outcome of executing an SDUI action.
type ActionExecutionResult struct {
	ActionID        string                  `json:"action_id"`
	ActionType      actions.ActionType      `json:"action_type"`
	Status          string                  `json:"status"`
	Message         string                  `json:"message,omitempty"`
	FollowUpActions []actions.ActionTrigger `json:"follow_up_actions,omitempty"`
	UpdatedState    map[string]any          `json:"updated_state,omitempty"`
	FieldErrors     map[string]string       `json:"field_errors,omitempty"`
}

// TokenCatalog exposes all registered SDUI design tokens to clients.
type TokenCatalog struct {
	Version      string                     `json:"version"`
	Colors       []theming.ColorSpec        `json:"colors"`
	Typography   []theming.TypographySpec   `json:"typography"`
	Spacing      []theming.SpacingSpec      `json:"spacing"`
	CornerRadius []theming.CornerRadiusSpec `json:"corner_radius"`
}

// ScreenRepository retrieves SDUI screen definitions by identifier.
type ScreenRepository interface {
	GetScreen(ctx context.Context, screenID string) (snippets.PageResponse, error)
	ListScreenIDs(ctx context.Context) ([]string, error)
}

// ManifestProvider supplies precomputed screen version and ETag manifests in O(1).
type ManifestProvider interface {
	BuildManifest(ctx context.Context, appVersion string) (snippets.ScreenManifestResponse, error)
}

// ActionExecutor executes an ActionExecutionInput and returns a structured result.
type ActionExecutor interface {
	Execute(ctx context.Context, input ActionExecutionInput) (ActionExecutionResult, error)
}

// TokenCatalogProvider supplies the active SDUI design token catalog.
type TokenCatalogProvider interface {
	GetCatalog(ctx context.Context) (TokenCatalog, error)
}

// AnalyticsStore persists ingested client/BFF analytics batches and aggregates live summaries.
type AnalyticsStore interface {
	RecordBatch(ctx context.Context, req analytics.BatchRequest) (analytics.IngestResponse, error)
	GetSummary(ctx context.Context) (analytics.Summary, error)
}
