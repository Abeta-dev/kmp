// Command bff starts the Abeta SDUI Backend-For-Frontend HTTP server.
package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"strings"
	"time"

	"github.com/abeta-dev/go-libs/env"
	"github.com/abeta-dev/go-libs/ginmw"
	"github.com/abeta-dev/go-libs/logger"
	"github.com/abeta-dev/go-libs/shutdown"
	"github.com/abeta-dev/kmp/bff/internal/handler"
	"github.com/abeta-dev/kmp/bff/internal/infra"
	"github.com/abeta-dev/kmp/bff/internal/usecase"
	"github.com/gin-gonic/gin"
)

type appConfig struct {
	ServiceName        string
	AppVersion         string
	Addr               string
	GinMode            string
	CatalogVersion     string
	AllowedOrigins     []string
	ReadTimeout        time.Duration
	WriteTimeout       time.Duration
	IdleTimeout        time.Duration
	ShutdownTimeout    time.Duration
	TLSCertFile        string
	TLSKeyFile         string
	CompressionEnabled bool
	ETagEnabled        bool
	RateLimitEnabled   bool
	RequireAuth        bool
	JWTSecret          string
}

func loadConfig() appConfig {
	originsRaw := env.String("CORS_ALLOWED_ORIGINS", "*")
	parts := strings.Split(originsRaw, ",")
	origins := make([]string, 0, len(parts))
	for _, p := range parts {
		if trimmed := strings.TrimSpace(p); trimmed != "" {
			origins = append(origins, trimmed)
		}
	}

	port := env.Int("PORT", 8080)
	addr := env.String("HTTP_ADDR", fmt.Sprintf(":%d", port))

	return appConfig{
		ServiceName:        env.String("SERVICE_NAME", "abeta-sdui-bff"),
		AppVersion:         env.String("APP_VERSION", usecase.DefaultAppVersion),
		Addr:               addr,
		GinMode:            env.String("GIN_MODE", gin.ReleaseMode),
		CatalogVersion:     env.String("TOKEN_CATALOG_VERSION", "1.0.0"),
		AllowedOrigins:     origins,
		ReadTimeout:        env.Duration("HTTP_READ_TIMEOUT", 10*time.Second),
		WriteTimeout:       env.Duration("HTTP_WRITE_TIMEOUT", 15*time.Second),
		IdleTimeout:        env.Duration("HTTP_IDLE_TIMEOUT", 60*time.Second),
		ShutdownTimeout:    env.Duration("SHUTDOWN_TIMEOUT", 10*time.Second),
		TLSCertFile:        env.String("TLS_CERT_FILE", ""),
		TLSKeyFile:         env.String("TLS_KEY_FILE", ""),
		CompressionEnabled: env.Bool("COMPRESSION_ENABLED", true),
		ETagEnabled:        env.Bool("ETAG_ENABLED", true),
		RateLimitEnabled:   env.Bool("RATE_LIMIT_ENABLED", false),
		RequireAuth:        env.Bool("AUTH_REQUIRED", false),
		JWTSecret:          env.String("JWT_SECRET", ""),
	}
}

func validateConfig(cfg appConfig) error {
	if (cfg.TLSCertFile == "") != (cfg.TLSKeyFile == "") {
		return errors.New("TLS_CERT_FILE and TLS_KEY_FILE must both be set when enabling TLS")
	}
	if cfg.RequireAuth && strings.TrimSpace(cfg.JWTSecret) == "" {
		return errors.New("AUTH_REQUIRED=true requires a non-empty JWT_SECRET")
	}
	return nil
}

func buildServer(cfg appConfig, log *slog.Logger) *http.Server {
	gin.SetMode(cfg.GinMode)

	screenRepo := infra.NewInMemoryScreenRepository()
	actionExec := infra.NewDefaultActionExecutor()
	catalogProvider := infra.NewStaticTokenCatalogProvider(cfg.CatalogVersion)
	analyticsStore := infra.NewInMemoryAnalyticsStore(100)

	router := handler.NewRouter(handler.RouterConfig{
		ServiceName:       cfg.ServiceName,
		AppVersion:        cfg.AppVersion,
		Logger:            log,
		AllowedOrigins:    cfg.AllowedOrigins,
		Metrics:           &ginmw.MetricsTracker{},
		ScreenService:     usecase.NewScreenServiceWithVersion(screenRepo, cfg.AppVersion),
		ActionService:     usecase.NewActionService(actionExec),
		CatalogService:    usecase.NewCatalogService(catalogProvider),
		AnalyticsService:  usecase.NewAnalyticsService(analyticsStore),
		EnableCompression: cfg.CompressionEnabled,
		EnableETag:        cfg.ETagEnabled,
		EnableRateLimit:   cfg.RateLimitEnabled,
		RequireAuth:       cfg.RequireAuth,
		JWTSecret:         cfg.JWTSecret,
	})

	protocols := new(http.Protocols)
	protocols.SetHTTP1(true)
	protocols.SetUnencryptedHTTP2(true)
	protocols.SetHTTP2(true)

	return &http.Server{
		Addr:              cfg.Addr,
		Handler:           router,
		Protocols:         protocols,
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       cfg.ReadTimeout,
		WriteTimeout:      cfg.WriteTimeout,
		IdleTimeout:       cfg.IdleTimeout,
	}
}

func run() error {
	cfg := loadConfig()
	if err := validateConfig(cfg); err != nil {
		return err
	}
	log := logger.Default().With(slog.String("service", cfg.ServiceName))
	srv := buildServer(cfg, log)

	errCh := make(chan error, 1)
	go func() {
		log.Info("starting SDUI BFF HTTP server",
			slog.String("addr", cfg.Addr),
			slog.Bool("tls", cfg.TLSCertFile != ""),
			slog.Bool("compression", cfg.CompressionEnabled),
			slog.Bool("etag", cfg.ETagEnabled),
			slog.Bool("rate_limit", cfg.RateLimitEnabled),
			slog.Bool("auth_required", cfg.RequireAuth),
		)
		var err error
		if cfg.TLSCertFile != "" {
			err = srv.ListenAndServeTLS(cfg.TLSCertFile, cfg.TLSKeyFile)
		} else {
			err = srv.ListenAndServe()
		}
		if err != nil && !errors.Is(err, http.ErrServerClosed) {
			errCh <- err
		}
	}()

	mgr := shutdown.New(cfg.ShutdownTimeout)
	mgr.Register("http_server", func(ctx context.Context) error {
		log.Info("shutting down SDUI BFF HTTP server")
		return srv.Shutdown(ctx)
	})

	waitCh := make(chan error, 1)
	go func() {
		waitCh <- mgr.Wait()
	}()

	select {
	case err := <-errCh:
		ctx, cancel := context.WithTimeout(context.Background(), cfg.ShutdownTimeout)
		defer cancel()
		_ = mgr.Execute(ctx)
		return err
	case err := <-waitCh:
		if err != nil {
			return err
		}
	}

	log.Info("SDUI BFF stopped cleanly")
	return nil
}

func main() {
	if err := run(); err != nil {
		logger.Default().Error("BFF exited with error", slog.Any("error", err))
		os.Exit(1)
	}
}
