// Package handler implements the Gin HTTP transport layer for the SDUI BFF,
// wired with github.com/abeta-dev/go-libs/ginmw observability and security middlewares.
package handler

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/abeta-dev/go-libs/apperror"
	"github.com/abeta-dev/go-libs/ginmw"
	"github.com/abeta-dev/go-libs/health"
	"github.com/abeta-dev/go-libs/logger"
	"github.com/abeta-dev/kmp/bff/internal/domain"
	"github.com/abeta-dev/kmp/bff/internal/usecase"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/gin-gonic/gin"
)

// RouterConfig configures dependencies and middleware options for the BFF Gin engine.
type RouterConfig struct {
	ServiceName       string
	Logger            *slog.Logger
	AllowedOrigins    []string
	Metrics           *ginmw.MetricsTracker
	HealthService     *health.Service
	ScreenService     *usecase.ScreenService
	ActionService     *usecase.ActionService
	CatalogService    *usecase.CatalogService
	EnableCompression bool
	EnableETag        bool
	EnableRateLimit   bool
	RequireAuth       bool
	JWTSecret         string
}

// NewRouter builds a Gin Engine configured with go-libs/ginmw middlewares and SDUI routes.
func NewRouter(cfg RouterConfig) *gin.Engine {
	serviceName := cfg.ServiceName
	if serviceName == "" {
		serviceName = "abeta-sdui-bff"
	}

	log := cfg.Logger
	if log == nil {
		log = logger.Default()
	}

	origins := cfg.AllowedOrigins
	if len(origins) == 0 {
		origins = []string{"*"}
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
	engine.Use(
		ginmw.RequestID(),
		ginmw.Recovery(ginmw.WithRecoveryLogger(log)),
		ginmw.Telemetry(serviceName),
		ginmw.Logger(ginmw.WithLogger(log)),
		ginmw.SecurityHeaders(serviceName),
		ginmw.CORS(origins),
		ginmw.REDMetrics(metricsTracker),
		ginmw.LimitBodyDefault(),
	)
	if cfg.EnableRateLimit {
		engine.Use(ginmw.GlobalRateLimit())
	}
	if cfg.EnableCompression {
		engine.Use(CompressionMiddleware())
	}

	h := &bffHandler{
		screens: cfg.ScreenService,
		actions: cfg.ActionService,
		catalog: cfg.CatalogService,
	}

	engine.GET("/healthz", gin.WrapF(health.LivenessHandler))
	engine.GET("/readyz", gin.WrapF(healthSvc.Handler))
	engine.GET("/metrics", ginmw.MetricsEndpoint(metricsTracker))
	registerOpenAPIRoutes(engine, serviceName, cfg.RequireAuth)

	withETag := func(handler gin.HandlerFunc) []gin.HandlerFunc {
		if cfg.EnableETag {
			return []gin.HandlerFunc{ETagMiddleware(), handler}
		}
		return []gin.HandlerFunc{handler}
	}

	v1 := engine.Group("/api/v1")
	if cfg.RequireAuth {
		if err := ginmw.SetJWTSecret(cfg.JWTSecret); err != nil {
			log.Error("invalid JWT secret configured for auth middleware", slog.Any("error", err))
		}
		v1.Use(ginmw.AuthMiddleware())
	}
	{
		v1.GET("/screens", withETag(h.listScreens)...)
		v1.GET("/screens/:screenId", withETag(h.getScreen)...)
		v1.POST("/actions/execute", h.executeAction)
		v1.POST("/orders/express", h.placeExpressOrder)
		v1.GET("/catalog/tokens", withETag(h.getTokenCatalog)...)
	}

	return engine
}

type bffHandler struct {
	screens *usecase.ScreenService
	actions *usecase.ActionService
	catalog *usecase.CatalogService
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
	ginmw.RespondSuccess(c, page)
}

func (h *bffHandler) executeAction(c *gin.Context) {
	if h.actions == nil {
		respondDomainError(c, apperror.Internal("action service unavailable"))
		return
	}
	rawBody, err := io.ReadAll(c.Request.Body)
	if err != nil {
		ginmw.RespondErrorWithDetail(
			c,
			http.StatusBadRequest,
			string(apperror.CodeBadRequest),
			"invalid action execution payload",
			err.Error(),
		)
		return
	}
	var input domain.ActionExecutionInput
	if err := json.Unmarshal(rawBody, &input); err != nil {
		ginmw.RespondErrorWithDetail(
			c,
			http.StatusBadRequest,
			string(apperror.CodeBadRequest),
			"invalid action execution payload",
			err.Error(),
		)
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
	if c.Request.Body != nil && c.Request.ContentLength != 0 {
		if err := c.ShouldBindJSON(&body); err != nil && !errors.Is(err, io.EOF) {
			ginmw.RespondErrorWithDetail(
				c,
				http.StatusBadRequest,
				string(apperror.CodeBadRequest),
				"invalid express order payload",
				err.Error(),
			)
			return
		}
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
	ginmw.RespondSuccess(c, catalog)
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
