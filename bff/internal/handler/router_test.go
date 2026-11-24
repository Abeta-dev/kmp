package handler_test

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/abeta-dev/go-libs/ginmw"
	"github.com/abeta-dev/go-libs/health"
	"github.com/abeta-dev/kmp/bff/internal/domain"
	"github.com/abeta-dev/kmp/bff/internal/handler"
	"github.com/abeta-dev/kmp/bff/internal/infra"
	"github.com/abeta-dev/kmp/bff/internal/usecase"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/analytics"
	"github.com/gin-gonic/gin"
)

func init() {
	gin.SetMode(gin.TestMode)
}

func discardLogger() *slog.Logger {
	return slog.New(slog.NewJSONHandler(io.Discard, nil))
}

func newTestRouter() *gin.Engine {
	repo := infra.NewInMemoryScreenRepository()
	exec := infra.NewDefaultActionExecutor()
	catalog := infra.NewStaticTokenCatalogProvider("1.0.0")
	analyticsStore := infra.NewInMemoryAnalyticsStore(100)

	return handler.NewRouter(handler.RouterConfig{
		ServiceName:      "abeta-sdui-bff-test",
		Logger:           discardLogger(),
		AllowedOrigins:   []string{"https://app.abeta.dev", "*.abeta.dev"},
		Metrics:          &ginmw.MetricsTracker{},
		ScreenService:    usecase.NewScreenService(repo),
		ActionService:    usecase.NewActionService(exec),
		CatalogService:   usecase.NewCatalogService(catalog),
		AnalyticsService: usecase.NewAnalyticsService(analyticsStore),
	})
}

type apiEnvelope struct {
	Success bool            `json:"success"`
	Data    json.RawMessage `json:"data"`
	Error   *ginmw.APIError `json:"error"`
}

func TestHealthAndMetricsEndpoints(t *testing.T) {
	r := newTestRouter()

	// /healthz
	w := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/healthz", nil)
	r.ServeHTTP(w, req)
	if w.Code != http.StatusOK || !strings.Contains(w.Body.String(), `"ok"`) {
		t.Fatalf("/healthz unexpected response: %d %s", w.Code, w.Body.String())
	}
	if w.Header().Get("X-Request-ID") == "" {
		t.Error("expected X-Request-ID header from ginmw.RequestID")
	}
	if w.Header().Get("X-Content-Type-Options") != "nosniff" {
		t.Error("expected security header X-Content-Type-Options=nosniff")
	}

	// /readyz
	w = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodGet, "/readyz", nil)
	r.ServeHTTP(w, req)
	if w.Code != http.StatusOK || !strings.Contains(w.Body.String(), `"healthy"`) {
		t.Fatalf("/readyz unexpected response: %d %s", w.Code, w.Body.String())
	}

	// /metrics (Prometheus text and JSON)
	w = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodGet, "/metrics", nil)
	r.ServeHTTP(w, req)
	if w.Code != http.StatusOK || !strings.Contains(w.Body.String(), "http_requests_total") {
		t.Fatalf("/metrics text unexpected response: %d %s", w.Code, w.Body.String())
	}

	w = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodGet, "/metrics?format=json", nil)
	r.ServeHTTP(w, req)
	if w.Code != http.StatusOK || !strings.Contains(w.Body.String(), `"http_requests_total"`) {
		t.Fatalf("/metrics json unexpected response: %d %s", w.Code, w.Body.String())
	}
}

func TestReadyz_Degraded(t *testing.T) {
	r := handler.NewRouter(handler.RouterConfig{
		Logger: discardLogger(),
		HealthService: health.New(
			health.WithChecker("downstream", func(_ context.Context) error {
				return errors.New("connection refused")
			}),
		),
	})

	w := httptest.NewRecorder()
	r.ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/readyz", nil))
	if w.Code != http.StatusServiceUnavailable {
		t.Fatalf("expected 503 for degraded readyz, got %d", w.Code)
	}
}

func TestScreenEndpoints(t *testing.T) {
	r := newTestRouter()

	// List screens
	w := httptest.NewRecorder()
	r.ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/api/v1/screens", nil))
	if w.Code != http.StatusOK {
		t.Fatalf("GET /api/v1/screens status = %d, body = %s", w.Code, w.Body.String())
	}
	var listEnv apiEnvelope
	if err := json.Unmarshal(w.Body.Bytes(), &listEnv); err != nil || !listEnv.Success {
		t.Fatalf("invalid list envelope: %s, err=%v", w.Body.String(), err)
	}

	for _, id := range []string{"home", "fresh-home", "catalog", "containers", "profile", "form-demo", "store-home", "watch-status", "pay-home", "pay-transfer", "pay-cards", "pay-receipt"} {
		w := httptest.NewRecorder()
		r.ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/api/v1/screens/"+id, nil))
		if w.Code != http.StatusOK {
			t.Fatalf("GET /api/v1/screens/%s status = %d, body = %s", id, w.Code, w.Body.String())
		}
		var env apiEnvelope
		if err := json.Unmarshal(w.Body.Bytes(), &env); err != nil || !env.Success {
			t.Fatalf("invalid screen envelope for %s: %v", id, err)
		}
	}

	// Verify store-home specific Abeta Fresh wire contract fields
	wStore := httptest.NewRecorder()
	r.ServeHTTP(wStore, httptest.NewRequest(http.MethodGet, "/api/v1/screens/store-home", nil))
	if wStore.Code != http.StatusOK || !strings.Contains(wStore.Body.String(), "Abeta Fresh") || !strings.Contains(wStore.Body.String(), "floating_cart") || !strings.Contains(wStore.Body.String(), "product_card") {
		t.Fatalf("unexpected store-home HTTP payload: %d %s", wStore.Code, wStore.Body.String())
	}

	// Unknown screen -> 404 NOT_FOUND
	w = httptest.NewRecorder()
	r.ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/api/v1/screens/unknown", nil))
	if w.Code != http.StatusNotFound {
		t.Fatalf("expected 404, got %d (%s)", w.Code, w.Body.String())
	}
	var errEnv apiEnvelope
	if err := json.Unmarshal(w.Body.Bytes(), &errEnv); err != nil || errEnv.Success || errEnv.Error == nil || errEnv.Error.Code != "NOT_FOUND" {
		t.Fatalf("unexpected 404 envelope: %s", w.Body.String())
	}
}

func TestActionExecuteEndpoint(t *testing.T) {
	r := newTestRouter()

	// Valid action execution
	input := domain.ActionExecutionInput{
		Trigger: actions.NewSaveStateAction("save_1", actions.SaveStatePayload{
			Key:   "locale",
			Value: "en-US",
			Scope: "disk",
		}),
		ScreenID: "profile",
	}
	body, _ := json.Marshal(input)
	w := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/api/v1/actions/execute", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	r.ServeHTTP(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d: %s", w.Code, w.Body.String())
	}
	var okEnv apiEnvelope
	if err := json.Unmarshal(w.Body.Bytes(), &okEnv); err != nil || !okEnv.Success {
		t.Fatalf("unexpected ok envelope: %s", w.Body.String())
	}

	// Validation failure -> 422 UNPROCESSABLE with field error detail
	invalidInput := domain.ActionExecutionInput{
		Trigger: actions.NewCallAPIAction("submit", actions.CallAPIPayload{
			Endpoint: "/api/v1/actions/execute",
			Method:   "POST",
		}).WithValidations(actions.ValidationRule{
			FieldID: "full_name",
			Rule:    "required",
			Message: "Full name is required",
		}),
		FormValues: map[string]string{"full_name": ""},
	}
	body, _ = json.Marshal(invalidInput)
	w = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodPost, "/api/v1/actions/execute", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	r.ServeHTTP(w, req)
	if w.Code != http.StatusUnprocessableEntity {
		t.Fatalf("expected 422, got %d: %s", w.Code, w.Body.String())
	}
	var errEnv apiEnvelope
	if err := json.Unmarshal(w.Body.Bytes(), &errEnv); err != nil || errEnv.Error == nil || errEnv.Error.Code != "UNPROCESSABLE" {
		t.Fatalf("unexpected 422 envelope: %s", w.Body.String())
	}
	if !strings.Contains(errEnv.Error.Detail, "full_name") {
		t.Errorf("expected field error detail in %+v", errEnv.Error)
	}

	// Malformed JSON -> 400 BAD_REQUEST
	w = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodPost, "/api/v1/actions/execute", strings.NewReader("{bad"))
	req.Header.Set("Content-Type", "application/json")
	r.ServeHTTP(w, req)
	if w.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 for malformed JSON, got %d", w.Code)
	}

	// Direct form_id call_api body (matching KMP RemoteApiCallExecutor form-demo submit) -> 200 OK
	w = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodPost, "/api/v1/actions/execute", strings.NewReader(`{"form_id":"workspace_onboarding"}`))
	req.Header.Set("Content-Type", "application/json")
	r.ServeHTTP(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("expected 200 for direct form_id payload, got %d: %s", w.Code, w.Body.String())
	}

	// trigger_feedback action execution
	fbInput := domain.ActionExecutionInput{
		Trigger: actions.NewTriggerFeedbackAction("fb_cart", actions.TriggerFeedbackPayload{
			Effect:     actions.FeedbackCartBounce,
			TargetID:   "floating_cart_dock",
			DurationMs: 320,
		}),
		ScreenID: "store-home",
	}
	body, _ = json.Marshal(fbInput)
	w = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodPost, "/api/v1/actions/execute", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	r.ServeHTTP(w, req)
	if w.Code != http.StatusOK || !strings.Contains(w.Body.String(), "cart_bounce") {
		t.Fatalf("expected 200 for trigger_feedback, got %d: %s", w.Code, w.Body.String())
	}

	// call_api to /api/v1/orders/express emits celebration + toast follow-ups
	orderExecInput := domain.ActionExecutionInput{
		Trigger: actions.NewCallAPIAction("place_express_order", actions.CallAPIPayload{
			Endpoint: "/api/v1/orders/express",
			Method:   http.MethodPost,
			Body:     map[string]any{"hub": "abeta_fresh_central"},
		}),
		ScreenID: "store-home",
	}
	body, _ = json.Marshal(orderExecInput)
	w = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodPost, "/api/v1/actions/execute", bytes.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	r.ServeHTTP(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("expected 200 for express order action, got %d: %s", w.Code, w.Body.String())
	}
	var orderEnv apiEnvelope
	if err := json.Unmarshal(w.Body.Bytes(), &orderEnv); err != nil || !orderEnv.Success {
		t.Fatalf("invalid express order envelope: %s", w.Body.String())
	}
	var orderRes domain.ActionExecutionResult
	if err := json.Unmarshal(orderEnv.Data, &orderRes); err != nil {
		t.Fatalf("unmarshal order ActionExecutionResult: %v", err)
	}
	if len(orderRes.FollowUpActions) != 2 || orderRes.FollowUpActions[0].Type != actions.ActionTriggerFeedback || orderRes.FollowUpActions[1].Type != actions.ActionShowToast {
		t.Fatalf("expected celebration + toast follow-ups, got %+v", orderRes.FollowUpActions)
	}

	// Direct POST /api/v1/orders/express endpoint (with JSON body, empty body, and malformed body)
	w = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodPost, "/api/v1/orders/express", strings.NewReader(`{"delivery_eta_mins":8}`))
	req.Header.Set("Content-Type", "application/json")
	r.ServeHTTP(w, req)
	if w.Code != http.StatusOK || !strings.Contains(w.Body.String(), "Order Confirmed!") || !strings.Contains(w.Body.String(), "Express order confirmed!") {
		t.Fatalf("expected 200 with celebration & toast on POST /api/v1/orders/express, got %d: %s", w.Code, w.Body.String())
	}

	w = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodPost, "/api/v1/orders/express", nil)
	r.ServeHTTP(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("expected 200 on empty POST /api/v1/orders/express, got %d: %s", w.Code, w.Body.String())
	}

	w = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodPost, "/api/v1/orders/express", strings.NewReader("{bad"))
	req.Header.Set("Content-Type", "application/json")
	r.ServeHTTP(w, req)
	if w.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 for malformed POST /api/v1/orders/express, got %d", w.Code)
	}

	// Direct POST /api/v1/pay/transfer endpoint (valid body, empty body, and malformed body)
	w = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodPost, "/api/v1/pay/transfer", strings.NewReader(`{"recipient":"@maya.chen","amount_cents":12500}`))
	req.Header.Set("Content-Type", "application/json")
	r.ServeHTTP(w, req)
	if w.Code != http.StatusOK || !strings.Contains(w.Body.String(), "Transfer Sent!") || !strings.Contains(w.Body.String(), "pay-receipt") {
		t.Fatalf("expected 200 with celebration & pay-receipt nav on POST /api/v1/pay/transfer, got %d: %s", w.Code, w.Body.String())
	}

	w = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodPost, "/api/v1/pay/transfer", nil)
	r.ServeHTTP(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("expected 200 on empty POST /api/v1/pay/transfer, got %d: %s", w.Code, w.Body.String())
	}

	w = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodPost, "/api/v1/pay/transfer", strings.NewReader("{bad"))
	req.Header.Set("Content-Type", "application/json")
	r.ServeHTTP(w, req)
	if w.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 for malformed POST /api/v1/pay/transfer, got %d", w.Code)
	}
}

func TestCatalogTokensAndCORSAndPanicRecovery(t *testing.T) {
	r := newTestRouter()

	// Catalog tokens
	w := httptest.NewRecorder()
	r.ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/api/v1/catalog/tokens", nil))
	if w.Code != http.StatusOK || !strings.Contains(w.Body.String(), "bg.primary") {
		t.Fatalf("unexpected /api/v1/catalog/tokens response: %d %s", w.Code, w.Body.String())
	}

	// CORS preflight
	w = httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodOptions, "/api/v1/screens/home", nil)
	req.Header.Set("Origin", "https://app.abeta.dev")
	r.ServeHTTP(w, req)
	if w.Code != http.StatusNoContent || w.Header().Get("Access-Control-Allow-Origin") != "https://app.abeta.dev" {
		t.Fatalf("unexpected CORS response: %d headers=%v", w.Code, w.Header())
	}

	// Panic recovery
	r.GET("/panic", func(_ *gin.Context) {
		panic("unexpected failure")
	})
	w = httptest.NewRecorder()
	r.ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/panic", nil))
	if w.Code != http.StatusInternalServerError {
		t.Fatalf("expected 500 from recovery middleware, got %d", w.Code)
	}
}

func TestRouter_NilServicesAndFallbackErrors(t *testing.T) {
	r := handler.NewRouter(handler.RouterConfig{
		Logger: discardLogger(),
	})

	endpoints := []struct {
		method string
		path   string
		body   string
	}{
		{http.MethodGet, "/readyz", ""},
		{http.MethodGet, "/api/v1/screens", ""},
		{http.MethodGet, "/api/v1/screens/manifest", ""},
		{http.MethodGet, "/api/v1/screens/home", ""},
		{http.MethodPost, "/api/v1/actions/execute", `{"trigger":{"id":"a","type":"back_press"}}`},
		{http.MethodPost, "/api/v1/orders/express", `{}`},
		{http.MethodPost, "/api/v1/pay/transfer", `{}`},
		{http.MethodGet, "/api/v1/catalog/tokens", ""},
		{http.MethodPost, "/api/v1/analytics/events", `{"events":[{"event_name":"page_open"}]}`},
		{http.MethodGet, "/api/v1/analytics/summary", ""},
	}

	for _, ep := range endpoints {
		w := httptest.NewRecorder()
		var bodyReader io.Reader
		if ep.body != "" {
			bodyReader = strings.NewReader(ep.body)
		}
		r.ServeHTTP(w, httptest.NewRequest(ep.method, ep.path, bodyReader))
		if w.Code != http.StatusInternalServerError && w.Code != http.StatusServiceUnavailable {
			t.Errorf("%s %s expected 500/503 with nil services, got %d", ep.method, ep.path, w.Code)
		}
	}
}

func TestAnalyticsEndpoints_IngestAndSummary(t *testing.T) {
	r := newTestRouter()

	// 1. Valid batch ingest -> 200 OK with ginmw.Envelope
	batch := analytics.BatchRequest{
		SessionID:  "sess_fresh_01",
		UserID:     "usr_101",
		Platform:   "android",
		AppVersion: "1.5.0",
		Events: []analytics.Event{
			{EventName: analytics.EventPageOpen, PageID: "fresh-home"},
			{EventName: analytics.EventSnippetImpression, PageID: "fresh-home", SnippetID: "fresh_sticky_search"},
			{EventName: analytics.EventNodeImpression, PageID: "fresh-home", SnippetID: "fresh_bestsellers_block", ComponentID: "tile_avocados"},
			{EventName: analytics.EventInteractionClick, PageID: "fresh-home", ComponentID: "tile_avocados", ActionID: "inc_fresh_avocado", ActionType: "trigger_feedback"},
			{EventName: analytics.EventTrackEvent, PageID: "fresh-home", Properties: map[string]string{"coupon": "FRESH20"}},
		},
	}
	payload, _ := json.Marshal(batch)
	w := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodPost, "/api/v1/analytics/events", bytes.NewReader(payload))
	req.Header.Set("Content-Type", "application/json")
	r.ServeHTTP(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("POST /api/v1/analytics/events status = %d, body = %s", w.Code, w.Body.String())
	}
	var ingestEnv apiEnvelope
	if err := json.Unmarshal(w.Body.Bytes(), &ingestEnv); err != nil || !ingestEnv.Success {
		t.Fatalf("invalid ingest envelope: %s, err=%v", w.Body.String(), err)
	}
	var ingestResp analytics.IngestResponse
	if err := json.Unmarshal(ingestEnv.Data, &ingestResp); err != nil || ingestResp.Accepted != 5 || ingestResp.Dropped != 0 || ingestResp.ServerTimestampEpochMs <= 0 {
		t.Fatalf("unexpected ingest response: %+v, err=%v", ingestResp, err)
	}

	// 2. GET /api/v1/analytics/summary -> 200 OK with live counters and recent events
	wSum := httptest.NewRecorder()
	r.ServeHTTP(wSum, httptest.NewRequest(http.MethodGet, "/api/v1/analytics/summary", nil))
	if wSum.Code != http.StatusOK {
		t.Fatalf("GET /api/v1/analytics/summary status = %d, body = %s", wSum.Code, wSum.Body.String())
	}
	var sumEnv apiEnvelope
	if err := json.Unmarshal(wSum.Body.Bytes(), &sumEnv); err != nil || !sumEnv.Success {
		t.Fatalf("invalid summary envelope: %s, err=%v", wSum.Body.String(), err)
	}
	var summary analytics.Summary
	if err := json.Unmarshal(sumEnv.Data, &summary); err != nil {
		t.Fatalf("unmarshal analytics.Summary: %v", err)
	}
	if summary.TotalEvents != 5 || summary.ByPageID["fresh-home"] != 5 || summary.ByComponentID["tile_avocados"] != 2 || len(summary.RecentEvents) != 5 {
		t.Fatalf("unexpected analytics summary: %+v", summary)
	}

	// 3. Malformed JSON -> 400 BAD_REQUEST
	wBad := httptest.NewRecorder()
	reqBad := httptest.NewRequest(http.MethodPost, "/api/v1/analytics/events", strings.NewReader("{bad"))
	reqBad.Header.Set("Content-Type", "application/json")
	r.ServeHTTP(wBad, reqBad)
	if wBad.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 for malformed analytics JSON, got %d", wBad.Code)
	}

	// 4. Empty events -> 400 BAD_REQUEST from service validation
	wEmpty := httptest.NewRecorder()
	reqEmpty := httptest.NewRequest(http.MethodPost, "/api/v1/analytics/events", strings.NewReader(`{"events":[]}`))
	reqEmpty.Header.Set("Content-Type", "application/json")
	r.ServeHTTP(wEmpty, reqEmpty)
	if wEmpty.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 for empty events batch, got %d: %s", wEmpty.Code, wEmpty.Body.String())
	}
}
