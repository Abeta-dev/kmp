// Package handler implements the Gin HTTP transport layer for the SDUI BFF,
// wired with github.com/abeta-dev/go-libs/ginmw and pkg/sdui/server middlewares.
package handler

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"math"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/abeta-dev/go-libs/apperror"
	"github.com/abeta-dev/go-libs/ginmw"
	"github.com/abeta-dev/go-libs/health"
	"github.com/abeta-dev/go-libs/logger"
	"github.com/abeta-dev/kmp/bff/internal/domain"
	"github.com/abeta-dev/kmp/bff/internal/usecase"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/analytics"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/server"
	"github.com/gin-gonic/gin"
)

// RouterConfig configures dependencies and middleware options for the BFF Gin engine.
type RouterConfig struct {
	ServiceName       string
	AppVersion        string
	Logger            *slog.Logger
	AllowedOrigins    []string
	AllowCredentials  bool
	TrustedProxies    []string
	CacheControl      string
	Metrics           *ginmw.MetricsTracker
	HealthService     *health.Service
	ScreenService     *usecase.ScreenService
	ActionService     *usecase.ActionService
	CatalogService    *usecase.CatalogService
	AnalyticsService  *usecase.AnalyticsService
	StreamProvider    server.StreamProvider
	ShutdownContext   context.Context
	EnableCompression bool
	EnableETag        bool
	EnableRateLimit   bool
	RequireAuth       bool
	JWTSecret         string
}

// NewRouter builds a Gin Engine configured with go-libs/ginmw and pkg/sdui/server middlewares.
func NewRouter(cfg RouterConfig) *gin.Engine {
	serviceName := cfg.ServiceName
	if serviceName == "" {
		serviceName = "abeta-sdui-bff"
	}

	log := cfg.Logger
	if log == nil {
		log = logger.Default()
	}

	metricsTracker := cfg.Metrics
	if metricsTracker == nil {
		metricsTracker = ginmw.DefaultMetrics
	}

	healthSvc := cfg.HealthService
	if healthSvc == nil {
		healthSvc = health.New(
			health.WithStartTime(time.Now()),
			health.WithRuntimeInfo(),
			health.WithChecker("screen_repository", func(ctx context.Context) error {
				if cfg.ScreenService == nil {
					return errors.New("screen service not configured")
				}
				_, err := cfg.ScreenService.ListScreens(ctx)
				return err
			}),
		)
	}

	engine := gin.New()
	if err := engine.SetTrustedProxies(cfg.TrustedProxies); err != nil {
		log.Error("invalid trusted proxies configured; falling back to none", slog.Any("error", err))
		_ = engine.SetTrustedProxies(nil)
	}

	engine.Use(
		ginmw.RequestID(),
		ginmw.Recovery(ginmw.WithRecoveryLogger(log)),
		ginmw.Telemetry(serviceName),
		ginmw.Logger(ginmw.WithLogger(log)),
		ginmw.SecurityHeaders(serviceName),
		server.CORSMiddleware(server.CORSConfig{
			AllowedOrigins:   cfg.AllowedOrigins,
			AllowCredentials: cfg.AllowCredentials,
		}),
		ginmw.REDMetrics(metricsTracker),
		guardNilRequestBody(),
		ginmw.LimitBodyDefault(),
	)
	if cfg.EnableCompression {
		engine.Use(CompressionMiddleware())
	}

	appVersionOverride := strings.TrimSpace(cfg.AppVersion)
	appVersion := appVersionOverride
	if appVersion == "" {
		if cfg.ScreenService != nil {
			appVersion = cfg.ScreenService.AppVersion()
		} else {
			appVersion = usecase.DefaultAppVersion
		}
	}

	h := &bffHandler{
		log:                log,
		screens:            cfg.ScreenService,
		actions:            cfg.ActionService,
		catalog:            cfg.CatalogService,
		analytics:          cfg.AnalyticsService,
		streamProvider:     cfg.StreamProvider,
		shutdownCtx:        cfg.ShutdownContext,
		appVersion:         appVersion,
		appVersionOverride: appVersionOverride,
		requireAuth:        cfg.RequireAuth,
	}

	engine.GET("/healthz", gin.WrapF(health.LivenessHandler))
	engine.GET("/readyz", gin.WrapF(healthSvc.Handler))
	engine.GET("/metrics", ginmw.MetricsEndpoint(metricsTracker))
	registerOpenAPIRoutes(engine, serviceName, appVersion, cfg.RequireAuth)

	etagMW := server.ETagMiddleware(server.ETagConfig{
		CacheControl: cfg.CacheControl,
		RequireAuth:  cfg.RequireAuth,
		AppVersion:   appVersion,
	})
	withETag := func(handler gin.HandlerFunc) []gin.HandlerFunc {
		if cfg.EnableETag {
			return []gin.HandlerFunc{etagMW, handler}
		}
		return []gin.HandlerFunc{handler}
	}

	v1 := engine.Group("/api/v1")
	if cfg.EnableRateLimit {
		v1.Use(ginmw.GlobalRateLimit())
	}
	if cfg.RequireAuth {
		v1.Use(server.JWTAuthMiddleware([]byte(cfg.JWTSecret)))
	}
	{
		v1.GET("/manifest", withETag(h.getScreenManifest)...)
		v1.HEAD("/manifest", withETag(h.getScreenManifest)...)
		v1.GET("/screens", withETag(h.listScreens)...)
		v1.HEAD("/screens", withETag(h.listScreens)...)
		v1.GET("/screens/manifest", withETag(h.getScreenManifest)...)
		v1.HEAD("/screens/manifest", withETag(h.getScreenManifest)...)
		v1.GET("/screens/:screenId", withETag(h.getScreen)...)
		v1.HEAD("/screens/:screenId", withETag(h.getScreen)...)
		v1.GET("/streams/:streamId", h.streamDeltas)
		v1.POST("/actions/execute", h.executeAction)
		v1.POST("/orders/express", h.placeExpressOrder)
		v1.POST("/pay/transfer", h.executePayTransfer)
		v1.GET("/catalog/tokens", withETag(h.getTokenCatalog)...)
		v1.HEAD("/catalog/tokens", withETag(h.getTokenCatalog)...)
		v1.POST("/analytics/events", h.ingestAnalyticsEvents)
		v1.GET("/analytics/summary", h.getAnalyticsSummary)
	}

	return engine
}

type bffHandler struct {
	log                *slog.Logger
	screens            *usecase.ScreenService
	actions            *usecase.ActionService
	catalog            *usecase.CatalogService
	analytics          *usecase.AnalyticsService
	streamProvider     server.StreamProvider
	shutdownCtx        context.Context
	appVersion         string
	appVersionOverride string
	requireAuth        bool
}

func (h *bffHandler) listScreens(c *gin.Context) {
	if h.screens == nil {
		respondDomainError(c, apperror.Internal("screen service unavailable"))
		return
	}
	ids, err := h.screens.ListScreens(c.Request.Context())
	if err != nil {
		respondDomainError(c, err)
		return
	}
	ginmw.RespondSuccess(c, gin.H{"screens": ids})
}

func (h *bffHandler) getScreenManifest(c *gin.Context) {
	if h.screens == nil {
		respondDomainError(c, apperror.Internal("screen service unavailable"))
		return
	}
	manifest, err := h.screens.BuildManifest(c.Request.Context(), h.appVersion)
	if err != nil {
		respondDomainError(c, err)
		return
	}
	if manifest.AppVersion != "" {
		c.Header("X-App-Version", manifest.AppVersion)
	}
	ginmw.RespondSuccess(c, manifest)
}

func (h *bffHandler) getScreen(c *gin.Context) {
	if h.screens == nil {
		respondDomainError(c, apperror.Internal("screen service unavailable"))
		return
	}
	page, err := h.screens.GetScreen(c.Request.Context(), c.Param("screenId"))
	if err != nil {
		respondDomainError(c, err)
		return
	}
	if h.appVersionOverride != "" {
		page.AppVersion = h.appVersionOverride
	}
	if page.ETag != "" {
		c.Header("ETag", page.ETag)
	}
	if page.AppVersion != "" {
		c.Header("X-App-Version", page.AppVersion)
	}
	if page.PageVersion != "" {
		c.Header("X-Page-Version", page.PageVersion)
	}
	ginmw.RespondSuccess(c, page)
}

func (h *bffHandler) executeAction(c *gin.Context) {
	if h.actions == nil {
		respondDomainError(c, apperror.Internal("action service unavailable"))
		return
	}
	var input domain.ActionExecutionInput
	rawBody, handled := readJSONBody(c, &input, false, "invalid action execution payload")
	if handled {
		return
	}
	if input.Trigger.Type == "" {
		var rawMap map[string]any
		if json.Unmarshal(rawBody, &rawMap) == nil {
			if formID, ok := rawMap["form_id"].(string); ok && strings.TrimSpace(formID) != "" {
				input = domain.ActionExecutionInput{
					Trigger: actions.NewCallAPIAction("submit_"+strings.TrimSpace(formID), actions.CallAPIPayload{
						Endpoint: "/api/v1/actions/execute",
						Method:   http.MethodPost,
						Body:     rawMap,
					}),
					ScreenID: "form-demo",
				}
			}
		}
	}

	result, err := h.actions.Execute(c.Request.Context(), input)
	if err != nil {
		respondDomainError(c, err)
		return
	}
	ginmw.RespondSuccess(c, result)
}

func (h *bffHandler) placeExpressOrder(c *gin.Context) {
	if h.actions == nil {
		respondDomainError(c, apperror.Internal("action service unavailable"))
		return
	}
	var body map[string]any
	if _, handled := readJSONBody(c, &body, true, "invalid express order payload"); handled {
		return
	}
	result, err := h.actions.Execute(c.Request.Context(), domain.ActionExecutionInput{
		Trigger: actions.NewCallAPIAction("place_express_order", actions.CallAPIPayload{
			Endpoint: "/api/v1/orders/express",
			Method:   http.MethodPost,
			Body:     body,
		}),
		ScreenID: "store-home",
	})
	if err != nil {
		respondDomainError(c, err)
		return
	}
	ginmw.RespondSuccess(c, result)
}

func (h *bffHandler) executePayTransfer(c *gin.Context) {
	if h.actions == nil {
		respondDomainError(c, apperror.Internal("action service unavailable"))
		return
	}
	var body map[string]any
	if _, handled := readJSONBody(c, &body, true, "invalid pay transfer payload"); handled {
		return
	}
	if rawAmount, present := body["amount_cents"]; present {
		if !isPositiveIntAmount(rawAmount) {
			ginmw.RespondError(
				c,
				http.StatusBadRequest,
				string(apperror.CodeBadRequest),
				"amount_cents must be a positive integer",
			)
			return
		}
	}
	result, err := h.actions.Execute(c.Request.Context(), domain.ActionExecutionInput{
		Trigger: actions.NewCallAPIAction("pay_execute_transfer", actions.CallAPIPayload{
			Endpoint: "/api/v1/pay/transfer",
			Method:   http.MethodPost,
			Body:     body,
		}),
		ScreenID: "pay-transfer",
	})
	if err != nil {
		respondDomainError(c, err)
		return
	}
	ginmw.RespondSuccess(c, result)
}

func (h *bffHandler) getTokenCatalog(c *gin.Context) {
	if h.catalog == nil {
		respondDomainError(c, apperror.Internal("catalog service unavailable"))
		return
	}
	catalog, err := h.catalog.GetCatalog(c.Request.Context())
	if err != nil {
		respondDomainError(c, err)
		return
	}
	if !h.requireAuth {
		c.Header("Cache-Control", "public, no-cache")
	}
	ginmw.RespondSuccess(c, catalog)
}

func (h *bffHandler) ingestAnalyticsEvents(c *gin.Context) {
	if h.analytics == nil {
		respondDomainError(c, apperror.Internal("analytics service unavailable"))
		return
	}
	var req analytics.BatchRequest
	if _, handled := readJSONBody(c, &req, false, "invalid analytics batch payload"); handled {
		return
	}
	res, err := h.analytics.IngestBatch(c.Request.Context(), req)
	if err != nil {
		respondDomainError(c, err)
		return
	}
	ginmw.RespondSuccess(c, res)
}

func (h *bffHandler) getAnalyticsSummary(c *gin.Context) {
	if h.analytics == nil {
		respondDomainError(c, apperror.Internal("analytics service unavailable"))
		return
	}
	summary, err := h.analytics.GetSummary(c.Request.Context())
	if err != nil {
		respondDomainError(c, err)
		return
	}
	ginmw.RespondSuccess(c, summary)
}

func guardNilRequestBody() gin.HandlerFunc {
	return func(c *gin.Context) {
		if c.Request != nil && c.Request.Body == nil {
			c.Request.Body = http.NoBody
		}
		c.Next()
	}
}

func readJSONBody(c *gin.Context, dst any, allowEmpty bool, badReqMsg string) ([]byte, bool) {
	if c.Request == nil || c.Request.Body == nil {
		if allowEmpty {
			return nil, false
		}
		ginmw.RespondError(c, http.StatusBadRequest, string(apperror.CodeBadRequest), badReqMsg)
		return nil, true
	}

	raw, err := io.ReadAll(c.Request.Body)
	if err != nil {
		var maxBytesErr *http.MaxBytesError
		if errors.As(err, &maxBytesErr) {
			ginmw.RespondError(
				c,
				http.StatusRequestEntityTooLarge,
				"PAYLOAD_TOO_LARGE",
				"request body exceeds maximum allowed size",
			)
			return nil, true
		}
		ginmw.RespondError(c, http.StatusBadRequest, string(apperror.CodeBadRequest), badReqMsg)
		return nil, true
	}

	if len(bytes.TrimSpace(raw)) == 0 {
		if allowEmpty {
			return raw, false
		}
		ginmw.RespondError(c, http.StatusBadRequest, string(apperror.CodeBadRequest), badReqMsg)
		return nil, true
	}

	dec := json.NewDecoder(bytes.NewReader(raw))
	dec.UseNumber()
	if err := dec.Decode(dst); err != nil {
		ginmw.RespondError(c, http.StatusBadRequest, string(apperror.CodeBadRequest), badReqMsg)
		return nil, true
	}
	return raw, false
}

func isPositiveIntAmount(raw any) bool {
	switch v := raw.(type) {
	case json.Number:
		if n, err := strconv.ParseInt(v.String(), 10, 64); err == nil {
			return n > 0
		}
		return false
	case float64:
		return !math.IsNaN(v) && !math.IsInf(v, 0) && v > 0 && math.Trunc(v) == v
	case int64:
		return v > 0
	case int:
		return v > 0
	default:
		return false
	}
}

func respondDomainError(c *gin.Context, err error) {
	var appErr *apperror.Error
	if errors.As(err, &appErr) {
		if appErr.Details != nil {
			if raw, marshalErr := json.Marshal(appErr.Details); marshalErr == nil {
				ginmw.RespondErrorWithDetail(c, appErr.GetStatus(), appErr.GetCode(), appErr.Message, string(raw))
				return
			}
		}
		ginmw.RespondError(c, appErr.GetStatus(), appErr.GetCode(), appErr.Message)
		return
	}
	ginmw.RespondError(c, http.StatusInternalServerError, string(apperror.CodeInternal), "internal server error")
}
