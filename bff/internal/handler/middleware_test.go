package handler_test

import (
	"compress/gzip"
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
	"github.com/abeta-dev/kmp/bff/internal/domain"
	"github.com/abeta-dev/kmp/bff/internal/handler"
	"github.com/abeta-dev/kmp/bff/internal/infra"
	"github.com/abeta-dev/kmp/bff/internal/usecase"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/analytics"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
	"github.com/andybalholm/brotli"
	"github.com/gin-gonic/gin"
)

func buildRouterWithOptions(mutator func(*handler.RouterConfig)) *gin.Engine {
	repo := infra.NewInMemoryScreenRepository()
	exec := infra.NewDefaultActionExecutor()
	catalog := infra.NewStaticTokenCatalogProvider("1.0.0")

	cfg := handler.RouterConfig{
		ServiceName:    "abeta-sdui-bff-test",
		Logger:         slog.New(slog.NewJSONHandler(io.Discard, nil)),
		AllowedOrigins: []string{"https://app.abeta.dev"},
		Metrics:        &ginmw.MetricsTracker{},
		ScreenService:  usecase.NewScreenService(repo),
		ActionService:  usecase.NewActionService(exec),
		CatalogService: usecase.NewCatalogService(catalog),
	}
	if mutator != nil {
		mutator(&cfg)
	}
	return handler.NewRouter(cfg)
}

func decodeBrotli(t *testing.T, data []byte) []byte {
	t.Helper()
	r := brotli.NewReader(strings.NewReader(string(data)))
	out, err := io.ReadAll(r)
	if err != nil {
		t.Fatalf("brotli decode failed: %v", err)
	}
	return out
}

func decodeGzip(t *testing.T, data []byte) []byte {
	t.Helper()
	r, err := gzip.NewReader(strings.NewReader(string(data)))
	if err != nil {
		t.Fatalf("gzip NewReader failed: %v", err)
	}
	defer func() { _ = r.Close() }()
	out, err := io.ReadAll(r)
	if err != nil {
		t.Fatalf("gzip ReadAll failed: %v", err)
	}
	return out
}

func TestCompression_BrotliGzipAndSkips(t *testing.T) {
	r := buildRouterWithOptions(func(cfg *handler.RouterConfig) {
		cfg.EnableCompression = true
		cfg.EnableETag = true
	})

	// Extra edge routes for HEAD, 204, pre-encoded, and panic recovery under compression
	r.HEAD("/head-check", func(c *gin.Context) { c.Status(http.StatusOK) })
	r.GET("/no-content", func(c *gin.Context) { c.Status(http.StatusNoContent) })
	r.GET("/pre-encoded", func(c *gin.Context) {
		c.Header("Content-Encoding", "custom")
		c.String(http.StatusOK, "raw")
	})
	r.GET("/boom", func(_ *gin.Context) { panic("boom under compression") })

	// 1. Brotli preferred over gzip on equal quality + preserves Vary: Origin
	w := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/api/v1/screens/containers", nil)
	req.Header.Set("Accept-Encoding", "gzip, deflate, br")
	req.Header.Set("Origin", "https://app.abeta.dev")
	r.ServeHTTP(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", w.Code)
	}
	if w.Header().Get("Content-Encoding") != "br" {
		t.Fatalf("expected Content-Encoding=br, got %q", w.Header().Get("Content-Encoding"))
	}
	vary := w.Header().Get("Vary")
	if !strings.Contains(vary, "Accept-Encoding") || !strings.Contains(vary, "Origin") {
		t.Fatalf("expected Vary to contain Origin and Accept-Encoding, got %q", vary)
	}
	plain := decodeBrotli(t, w.Body.Bytes())
	var env apiEnvelope
	if err := json.Unmarshal(plain, &env); err != nil || !env.Success {
		t.Fatalf("invalid brotli JSON payload: err=%v body=%s", err, string(plain))
	}

	// 2. Gzip selected when gzip q > br q or br disabled
	for _, ae := range []string{"gzip", "br;q=0.4, gzip;q=0.9", "br;q=0, gzip;q=0.5"} {
		w := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodGet, "/api/v1/screens/home", nil)
		req.Header.Set("Accept-Encoding", ae)
		r.ServeHTTP(w, req)
		if w.Code != http.StatusOK || w.Header().Get("Content-Encoding") != "gzip" {
			t.Fatalf("Accept-Encoding=%q expected gzip, got status=%d encoding=%q", ae, w.Code, w.Header().Get("Content-Encoding"))
		}
		out := decodeGzip(t, w.Body.Bytes())
		if !strings.Contains(string(out), `"home"`) {
			t.Fatalf("expected home payload after gzip decode, got %s", string(out))
		}
	}

	// 3. Wildcard matches br; q=0 or malformed q falls back to uncompressed
	w = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodGet, "/api/v1/screens", nil)
	req.Header.Set("Accept-Encoding", "*;q=0.5")
	r.ServeHTTP(w, req)
	if w.Code != http.StatusOK || w.Header().Get("Content-Encoding") != "br" {
		t.Fatalf("expected br for *;q=0.5, got %q", w.Header().Get("Content-Encoding"))
	}

	for _, ae := range []string{"", "identity", "br;q=0, gzip;q=0", "br;q=bad"} {
		w := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodGet, "/api/v1/screens", nil)
		if ae != "" {
			req.Header.Set("Accept-Encoding", ae)
		}
		r.ServeHTTP(w, req)
		if w.Code != http.StatusOK || w.Header().Get("Content-Encoding") != "" {
			t.Fatalf("Accept-Encoding=%q expected uncompressed, got %q", ae, w.Header().Get("Content-Encoding"))
		}
		if !strings.Contains(w.Header().Get("Vary"), "Accept-Encoding") {
			t.Fatalf("expected Vary: Accept-Encoding even when uncompressed")
		}
	}

	// 4. Skips /metrics, HEAD, 204 No Content, pre-encoded responses
	w = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodGet, "/metrics", nil)
	req.Header.Set("Accept-Encoding", "br, gzip")
	r.ServeHTTP(w, req)
	if w.Code != http.StatusOK || w.Header().Get("Content-Encoding") != "" {
		t.Fatalf("expected /metrics uncompressed, got encoding=%q", w.Header().Get("Content-Encoding"))
	}

	w = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodHead, "/head-check", nil)
	req.Header.Set("Accept-Encoding", "br")
	r.ServeHTTP(w, req)
	if w.Code != http.StatusOK || w.Header().Get("Content-Encoding") != "" {
		t.Fatalf("expected HEAD uncompressed, got %d encoding=%q", w.Code, w.Header().Get("Content-Encoding"))
	}

	w = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodGet, "/no-content", nil)
	req.Header.Set("Accept-Encoding", "br")
	r.ServeHTTP(w, req)
	if w.Code != http.StatusNoContent || w.Header().Get("Content-Encoding") != "" || w.Body.Len() != 0 {
		t.Fatalf("expected 204 empty uncompressed, got status=%d encoding=%q len=%d", w.Code, w.Header().Get("Content-Encoding"), w.Body.Len())
	}

	w = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodGet, "/pre-encoded", nil)
	req.Header.Set("Accept-Encoding", "br")
	r.ServeHTTP(w, req)
	if w.Code != http.StatusOK || w.Header().Get("Content-Encoding") != "custom" || w.Body.String() != "raw" {
		t.Fatalf("expected pre-encoded response untouched, got encoding=%q body=%q", w.Header().Get("Content-Encoding"), w.Body.String())
	}

	// 5. Panic recovery still succeeds under compression
	w = httptest.NewRecorder()
	req = httptest.NewRequest(http.MethodGet, "/boom", nil)
	req.Header.Set("Accept-Encoding", "br")
	r.ServeHTTP(w, req)
	if w.Code != http.StatusInternalServerError {
		t.Fatalf("expected 500 on panic under compression, got %d", w.Code)
	}
}

func TestETagAndConditional304(t *testing.T) {
	r := buildRouterWithOptions(func(cfg *handler.RouterConfig) {
		cfg.EnableCompression = true
		cfg.EnableETag = true
	})

	// Uncompressed GET returns strong ETag + Cache-Control: public, max-age=30
	w1 := httptest.NewRecorder()
	r.ServeHTTP(w1, httptest.NewRequest(http.MethodGet, "/api/v1/screens/home", nil))
	if w1.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", w1.Code)
	}
	etag := w1.Header().Get("ETag")
	if !strings.HasPrefix(etag, `"`) || !strings.HasSuffix(etag, `"`) {
		t.Fatalf("expected strong quoted ETag, got %q", etag)
	}
	if w1.Header().Get("Cache-Control") != "public, max-age=30" {
		t.Fatalf("expected Cache-Control public, max-age=30, got %q", w1.Header().Get("Cache-Control"))
	}
	if w1.Header().Get("Pragma") != "" {
		t.Fatalf("expected Pragma deleted on cacheable response, got %q", w1.Header().Get("Pragma"))
	}

	// Matching If-None-Match -> 304 Not Modified (supports strong, weak, comma-list, wildcard)
	for _, inm := range []string{etag, "W/" + etag, `"stale", ` + etag, "*"} {
		w304 := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodGet, "/api/v1/screens/home", nil)
		req.Header.Set("If-None-Match", inm)
		r.ServeHTTP(w304, req)
		if w304.Code != http.StatusNotModified || w304.Body.Len() != 0 {
			t.Fatalf("If-None-Match=%q expected 304 empty body, got %d len=%d", inm, w304.Code, w304.Body.Len())
		}
		if w304.Header().Get("ETag") != etag || w304.Header().Get("Cache-Control") != "public, max-age=30" {
			t.Fatalf("304 missing cache headers: ETag=%q Cache-Control=%q", w304.Header().Get("ETag"), w304.Header().Get("Cache-Control"))
		}
	}

	// Mismatched If-None-Match -> 200 OK
	wMiss := httptest.NewRecorder()
	reqMiss := httptest.NewRequest(http.MethodGet, "/api/v1/screens/home", nil)
	reqMiss.Header.Set("If-None-Match", `"0000"`)
	r.ServeHTTP(wMiss, reqMiss)
	if wMiss.Code != http.StatusOK || wMiss.Body.Len() == 0 {
		t.Fatalf("expected 200 on ETag mismatch, got %d", wMiss.Code)
	}

	// Compressed GET /api/v1/catalog/tokens emits weak ETag and 304 omits Content-Encoding
	wBr := httptest.NewRecorder()
	reqBr := httptest.NewRequest(http.MethodGet, "/api/v1/catalog/tokens", nil)
	reqBr.Header.Set("Accept-Encoding", "br")
	r.ServeHTTP(wBr, reqBr)
	if wBr.Code != http.StatusOK || wBr.Header().Get("Content-Encoding") != "br" {
		t.Fatalf("expected 200 br on catalog/tokens, got %d enc=%q", wBr.Code, wBr.Header().Get("Content-Encoding"))
	}
	weakETag := wBr.Header().Get("ETag")
	if !strings.HasPrefix(weakETag, `W/"`) {
		t.Fatalf("expected weak ETag W/\"...\" on compressed response, got %q", weakETag)
	}

	wBr304 := httptest.NewRecorder()
	reqBr304 := httptest.NewRequest(http.MethodGet, "/api/v1/catalog/tokens", nil)
	reqBr304.Header.Set("Accept-Encoding", "br")
	reqBr304.Header.Set("If-None-Match", weakETag)
	r.ServeHTTP(wBr304, reqBr304)
	if wBr304.Code != http.StatusNotModified || wBr304.Body.Len() != 0 || wBr304.Header().Get("Content-Encoding") != "" {
		t.Fatalf("expected 304 empty uncompressed, got status=%d len=%d enc=%q", wBr304.Code, wBr304.Body.Len(), wBr304.Header().Get("Content-Encoding"))
	}
	if wBr304.Header().Get("ETag") != weakETag {
		t.Fatalf("expected 304 weak ETag %q, got %q", weakETag, wBr304.Header().Get("ETag"))
	}

	// 404 screen must not emit ETag or public Cache-Control
	w404 := httptest.NewRecorder()
	r.ServeHTTP(w404, httptest.NewRequest(http.MethodGet, "/api/v1/screens/unknown", nil))
	if w404.Code != http.StatusNotFound || w404.Header().Get("ETag") != "" {
		t.Fatalf("expected 404 without ETag, got status=%d etag=%q", w404.Code, w404.Header().Get("ETag"))
	}

	// Direct ETagMiddleware edge cases: non-GET and panic recovery
	r.POST("/etag-post", handler.ETagMiddleware(), func(c *gin.Context) {
		c.String(http.StatusOK, "posted")
	})
	wPost := httptest.NewRecorder()
	r.ServeHTTP(wPost, httptest.NewRequest(http.MethodPost, "/etag-post", nil))
	if wPost.Code != http.StatusOK || wPost.Header().Get("ETag") != "" {
		t.Fatalf("expected POST to skip ETag, got status=%d etag=%q", wPost.Code, wPost.Header().Get("ETag"))
	}

	r.GET("/etag-panic", handler.ETagMiddleware(), func(_ *gin.Context) {
		panic("boom inside etag")
	})
	wPanic := httptest.NewRecorder()
	r.ServeHTTP(wPanic, httptest.NewRequest(http.MethodGet, "/etag-panic", nil))
	if wPanic.Code != http.StatusInternalServerError {
		t.Fatalf("expected 500 on panic inside ETagMiddleware, got %d", wPanic.Code)
	}
}

func TestOpenAPIAndDocsEndpoints(t *testing.T) {
	r := buildRouterWithOptions(func(cfg *handler.RouterConfig) {
		cfg.RequireAuth = true
		cfg.JWTSecret = "test-secret-for-openapi-check-32b"
	})
	t.Cleanup(func() { _ = ginmw.SetJWTSecret("") })

	// /openapi.json accessible without auth and contains OpenAPI 3.1 + all routes & new schemas
	w := httptest.NewRecorder()
	r.ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/openapi.json", nil))
	if w.Code != http.StatusOK {
		t.Fatalf("GET /openapi.json status = %d", w.Code)
	}
	if !strings.Contains(w.Header().Get("Content-Type"), "application/json") {
		t.Fatalf("unexpected Content-Type for /openapi.json: %q", w.Header().Get("Content-Type"))
	}

	var spec struct {
		OpenAPI    string                    `json:"openapi"`
		Info       map[string]any            `json:"info"`
		Paths      map[string]map[string]any `json:"paths"`
		Components struct {
			Schemas map[string]any `json:"schemas"`
		} `json:"components"`
	}
	if err := json.Unmarshal(w.Body.Bytes(), &spec); err != nil {
		t.Fatalf("unmarshal /openapi.json: %v", err)
	}
	if spec.OpenAPI != "3.1.0" {
		t.Fatalf("expected openapi 3.1.0, got %q", spec.OpenAPI)
	}
	for _, route := range []string{
		"/healthz",
		"/readyz",
		"/metrics",
		"/openapi.json",
		"/docs",
		"/api/v1/screens",
		"/api/v1/screens/manifest",
		"/api/v1/screens/{screenId}",
		"/api/v1/streams/{streamId}",
		"/api/v1/actions/execute",
		"/api/v1/orders/express",
		"/api/v1/pay/transfer",
		"/api/v1/catalog/tokens",
		"/api/v1/analytics/events",
		"/api/v1/analytics/summary",
	} {
		if _, ok := spec.Paths[route]; !ok {
			t.Errorf("openapi.json missing path %q", route)
		}
	}
	rawSpec := w.Body.String()
	for _, screenID := range []string{"home", "fresh-home", "store-home", "catalog", "profile", "form-demo", "containers", "watch-status", "pay-home", "pay-transfer", "pay-cards", "pay-receipt"} {
		if !strings.Contains(rawSpec, `"`+screenID+`"`) {
			t.Errorf("openapi.json missing screen documentation for %q", screenID)
		}
	}
	for _, actionType := range []string{
		"navigate", "back_press", "open_dialog", "dismiss_overlay", "show_toast",
		"call_api", "save_state", "delete_state", "track_event", "refresh_page",
		"copy_to_clipboard", "update_snippets", "batch", "trigger_feedback",
		"open_url", "share", "request_permission", "biometric_auth",
	} {
		if !strings.Contains(rawSpec, `"`+actionType+`"`) {
			t.Errorf("openapi.json missing action type %q", actionType)
		}
	}
	for _, snippetType := range []string{
		"header_v1", "banner_v1", "metric_card", "card_v1", "action_row", "form_v1",
		"empty_state", "divider", "spacer", "carousel", "grid", "tabs", "accordion",
		"product_card", "swipe_deck", "search_bar", "chip_group",
		"nudge_bar", "marquee_strip", "order_tracker", "composed_block",
	} {
		if !strings.Contains(rawSpec, `"`+snippetType+`"`) {
			t.Errorf("openapi.json missing snippet type %q", snippetType)
		}
	}
	for _, loopEffect := range []string{
		"none", "pulse", "shimmer", "wiggle", "float", "heartbeat", "glow_ring", "marquee",
	} {
		if !strings.Contains(rawSpec, `"`+loopEffect+`"`) {
			t.Errorf("openapi.json missing motion loop effect %q", loopEffect)
		}
	}
	for _, schema := range []string{
		"ActionType",
		"TrackingSpec",
		"SnippetType",
		"ValidationRule",
		"ScreenVersionMeta",
		"ScreenManifestResponse",
		"SseStreamSpec",
		"SnippetDeltaOp",
		"SnippetDeltaPatch",
		"PageResponse",
		"AdaptivePageSpec",
		"ResponsiveColumnsSpec",
		"ResponsiveIntSpec",
		"PaginationSpec",
		"FloatingCartSpec",
		"MotionSpec",
		"SwitchAtom",
		"CheckboxAtom",
		"SliderAtom",
		"AvatarAtom",
		"SkeletonAtom",
		"StepperAtom",
		"CountdownAtom",
		"MediaPagerAtom",
		"AnalyticsEvent",
		"AnalyticsBatchRequest",
		"AnalyticsIngestResponse",
		"AnalyticsSummary",
		"ProductCardSnippetData",
		"SwipeCardItem",
		"SwipeDeckSnippetData",
		"SearchBarSnippetData",
		"FilterChipItem",
		"ChipGroupSnippetData",
		"NudgeBarSnippetData",
		"MarqueeItem",
		"MarqueeStripSnippetData",
		"OrderTrackerStep",
		"OrderTrackerSnippetData",
		"ComposedNodeKind",
		"ComposedNode",
		"ComposedBlockSnippetData",
		"NavigatePayload",
		"BackPressPayload",
		"OpenDialogPayload",
		"DismissOverlayPayload",
		"ShowToastPayload",
		"CallAPIPayload",
		"SaveStatePayload",
		"DeleteStatePayload",
		"TrackEventPayload",
		"RefreshPagePayload",
		"CopyToClipboardPayload",
		"UpdateSnippetsPayload",
		"BatchPayload",
		"TriggerFeedbackPayload",
		"OpenURLPayload",
		"SharePayload",
		"RequestPermissionPayload",
		"BiometricAuthPayload",
	} {
		if _, ok := spec.Components.Schemas[schema]; !ok {
			t.Errorf("openapi.json missing component schema %q", schema)
		}
	}

	// /docs serves HTML with Scalar UI and permissive CSP for docs assets
	wDocs := httptest.NewRecorder()
	r.ServeHTTP(wDocs, httptest.NewRequest(http.MethodGet, "/docs", nil))
	if wDocs.Code != http.StatusOK {
		t.Fatalf("GET /docs status = %d", wDocs.Code)
	}
	if !strings.Contains(wDocs.Header().Get("Content-Type"), "text/html") {
		t.Fatalf("unexpected Content-Type for /docs: %q", wDocs.Header().Get("Content-Type"))
	}
	body := wDocs.Body.String()
	if !strings.Contains(body, "/openapi.json") || !strings.Contains(body, "@scalar/api-reference") {
		t.Fatalf("unexpected /docs HTML body: %s", body)
	}
	if !strings.Contains(wDocs.Header().Get("Content-Security-Policy"), "cdn.jsdelivr.net") {
		t.Fatalf("expected /docs CSP to allow Scalar CDN, got %q", wDocs.Header().Get("Content-Security-Policy"))
	}
}

func TestRateLimitMiddleware(t *testing.T) {
	r := buildRouterWithOptions(func(cfg *handler.RouterConfig) {
		cfg.EnableRateLimit = true
	})

	// ginmw.GlobalRateLimit allows 200 requests/min per IP, 201st returns 429
	for i := range 200 {
		w := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodGet, "/api/v1/screens", nil)
		req.RemoteAddr = "198.51.100.42:54321"
		r.ServeHTTP(w, req)
		if w.Code != http.StatusOK {
			t.Fatalf("request %d unexpectedly rejected with %d", i+1, w.Code)
		}
	}

	w429 := httptest.NewRecorder()
	req429 := httptest.NewRequest(http.MethodGet, "/api/v1/screens", nil)
	req429.RemoteAddr = "198.51.100.42:54321"
	r.ServeHTTP(w429, req429)
	if w429.Code != http.StatusTooManyRequests {
		t.Fatalf("expected 429 Too Many Requests on 201st request, got %d", w429.Code)
	}
}

func TestAuthMiddleware_JWT(t *testing.T) {
	const secret = "abeta-test-jwt-secret-32-bytes-minimum"
	r := buildRouterWithOptions(func(cfg *handler.RouterConfig) {
		cfg.RequireAuth = true
		cfg.JWTSecret = secret
	})
	t.Cleanup(func() { _ = ginmw.SetJWTSecret("") })

	// 1. Missing Authorization -> 401
	wNoAuth := httptest.NewRecorder()
	r.ServeHTTP(wNoAuth, httptest.NewRequest(http.MethodGet, "/api/v1/screens/home", nil))
	if wNoAuth.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401 without token, got %d", wNoAuth.Code)
	}

	// 2. Invalid token -> 401
	wBadAuth := httptest.NewRecorder()
	reqBad := httptest.NewRequest(http.MethodGet, "/api/v1/screens/home", nil)
	reqBad.Header.Set("Authorization", "Bearer not.a.valid.jwt")
	r.ServeHTTP(wBadAuth, reqBad)
	if wBadAuth.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401 with invalid token, got %d", wBadAuth.Code)
	}

	// 3. Valid JWT minted via go-libs ginmw.GenerateToken -> 200
	token, err := ginmw.GenerateToken(&ginmw.Claims{
		Email: "architect@abeta.dev",
		Roles: []string{"user"},
	}, 15)
	if err != nil {
		t.Fatalf("GenerateToken failed: %v", err)
	}
	wOK := httptest.NewRecorder()
	reqOK := httptest.NewRequest(http.MethodGet, "/api/v1/screens/containers", nil)
	reqOK.Header.Set("Authorization", "Bearer "+token)
	r.ServeHTTP(wOK, reqOK)
	if wOK.Code != http.StatusOK {
		t.Fatalf("expected 200 with valid JWT, got %d: %s", wOK.Code, wOK.Body.String())
	}

	// 4. Health & docs remain unauthenticated
	wHealth := httptest.NewRecorder()
	r.ServeHTTP(wHealth, httptest.NewRequest(http.MethodGet, "/healthz", nil))
	if wHealth.Code != http.StatusOK {
		t.Fatalf("expected /healthz 200 even with RequireAuth=true, got %d", wHealth.Code)
	}

	// 5. RequireAuth with empty secret fails closed (401)
	rEmptySecret := buildRouterWithOptions(func(cfg *handler.RouterConfig) {
		cfg.RequireAuth = true
		cfg.JWTSecret = ""
	})
	wFailClosed := httptest.NewRecorder()
	reqFailClosed := httptest.NewRequest(http.MethodGet, "/api/v1/screens/home", nil)
	reqFailClosed.Header.Set("Authorization", "Bearer "+token)
	rEmptySecret.ServeHTTP(wFailClosed, reqFailClosed)
	if wFailClosed.Code != http.StatusUnauthorized {
		t.Fatalf("expected fail-closed 401 with empty secret, got %d", wFailClosed.Code)
	}
}

type failingScreenRepo struct{}

func (failingScreenRepo) GetScreen(_ context.Context, _ string) (snippets.PageResponse, error) {
	return snippets.PageResponse{}, errors.New("storage failure")
}

func (failingScreenRepo) ListScreenIDs(_ context.Context) ([]string, error) {
	return nil, errors.New("storage failure")
}

type failingCatalogProvider struct{}

func (failingCatalogProvider) GetCatalog(_ context.Context) (domain.TokenCatalog, error) {
	return domain.TokenCatalog{}, errors.New("catalog unavailable")
}

type failingAnalyticsStore struct{}

func (failingAnalyticsStore) RecordBatch(_ context.Context, _ analytics.BatchRequest) (analytics.IngestResponse, error) {
	return analytics.IngestResponse{}, errors.New("analytics unavailable")
}

func (failingAnalyticsStore) GetSummary(_ context.Context) (analytics.Summary, error) {
	return analytics.Summary{}, errors.New("analytics unavailable")
}

func TestHandlerEdgeCases_WriterAndRepoErrors(t *testing.T) {
	r := buildRouterWithOptions(func(cfg *handler.RouterConfig) {
		cfg.EnableCompression = true
		cfg.EnableETag = true
		cfg.ScreenService = usecase.NewScreenService(failingScreenRepo{})
		cfg.CatalogService = usecase.NewCatalogService(failingCatalogProvider{})
		cfg.AnalyticsService = usecase.NewAnalyticsService(failingAnalyticsStore{})
	})

	// Cover WriteString, WriteHeaderNow, duplicate WriteHeader, Size, Written, Vary duplicate
	r.GET("/writer-methods", func(c *gin.Context) {
		c.Writer.Header().Add("Vary", "Origin, , Origin")
		c.Writer.WriteHeaderNow()
		c.Writer.WriteHeader(http.StatusCreated) // second WriteHeader is a no-op
		_, _ = c.Writer.WriteString("hello-writer")
		if !c.Writer.Written() || c.Writer.Size() != len("hello-writer") {
			t.Errorf("unexpected writer state: written=%v size=%d", c.Writer.Written(), c.Writer.Size())
		}
	})
	r.GET("/write-direct", func(c *gin.Context) {
		// Write without prior WriteHeader triggers implicit 200
		_, _ = c.Writer.Write([]byte("implicit-200"))
	})
	r.GET("/info-status", func(c *gin.Context) {
		c.Writer.WriteHeader(http.StatusEarlyHints)
	})

	w := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/writer-methods", nil)
	req.Header.Set("Accept-Encoding", "br")
	r.ServeHTTP(w, req)
	if w.Code != http.StatusOK || w.Header().Get("Content-Encoding") != "br" {
		t.Fatalf("expected 200 br from /writer-methods, got %d enc=%q", w.Code, w.Header().Get("Content-Encoding"))
	}
	if string(decodeBrotli(t, w.Body.Bytes())) != "hello-writer" {
		t.Fatalf("unexpected decoded /writer-methods body")
	}

	wDirect := httptest.NewRecorder()
	reqDirect := httptest.NewRequest(http.MethodGet, "/write-direct", nil)
	reqDirect.Header.Set("Accept-Encoding", "gzip")
	r.ServeHTTP(wDirect, reqDirect)
	if wDirect.Code != http.StatusOK || string(decodeGzip(t, wDirect.Body.Bytes())) != "implicit-200" {
		t.Fatalf("unexpected /write-direct response: %d", wDirect.Code)
	}

	wInfo := httptest.NewRecorder()
	reqInfo := httptest.NewRequest(http.MethodGet, "/info-status", nil)
	reqInfo.Header.Set("Accept-Encoding", "br")
	r.ServeHTTP(wInfo, reqInfo)
	if wInfo.Code != http.StatusEarlyHints || wInfo.Header().Get("Content-Encoding") != "" {
		t.Fatalf("expected 103 uncompressed, got %d enc=%q", wInfo.Code, wInfo.Header().Get("Content-Encoding"))
	}

	// Cover If-None-Match empty segment
	wEmptySeg := httptest.NewRecorder()
	reqEmptySeg := httptest.NewRequest(http.MethodGet, "/write-direct", nil)
	reqEmptySeg.Header.Set("If-None-Match", ", ")
	r.ServeHTTP(wEmptySeg, reqEmptySeg)
	if wEmptySeg.Code != http.StatusOK {
		t.Fatalf("expected 200 for empty If-None-Match token, got %d", wEmptySeg.Code)
	}

	// Cover non-apperror fallback 500 on listScreens, getScreen, getTokenCatalog, and analytics summary
	for _, path := range []string{"/api/v1/screens", "/api/v1/screens/home", "/api/v1/catalog/tokens", "/api/v1/analytics/summary"} {
		wErr := httptest.NewRecorder()
		r.ServeHTTP(wErr, httptest.NewRequest(http.MethodGet, path, nil))
		if wErr.Code != http.StatusInternalServerError {
			t.Fatalf("expected 500 for failing repo on %s, got %d", path, wErr.Code)
		}
	}
}
