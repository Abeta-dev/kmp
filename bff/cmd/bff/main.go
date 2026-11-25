// Command bff starts the Abeta SDUI Backend-For-Frontend HTTP server.
package main

import (
	"context"
	"crypto/tls"
	"errors"
	"fmt"
	"log/slog"
	"net"
	"net/http"
	"os"
	"os/signal"
	"slices"
	"strings"
	"syscall"
	"time"

	"github.com/abeta-dev/go-libs/env"
	"github.com/abeta-dev/go-libs/ginmw"
	"github.com/abeta-dev/go-libs/logger"
	"github.com/abeta-dev/kmp/bff/internal/handler"
	"github.com/abeta-dev/kmp/bff/internal/infra"
	"github.com/abeta-dev/kmp/bff/internal/usecase"
	"github.com/gin-gonic/gin"
)

const (
	minJWTSecretLength       = 32
	defaultReadHeaderTimeout = 5 * time.Second
)

type appConfig struct {
	ServiceName        string
	AppVersion         string
	Port               int
	Addr               string
	GinMode            string
	CatalogVersion     string
	AllowedOrigins     []string
	TrustedProxies     []string
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

func splitCSV(raw string) []string {
	parts := strings.Split(raw, ",")
	out := make([]string, 0, len(parts))
	for _, p := range parts {
		if trimmed := strings.TrimSpace(p); trimmed != "" {
			out = append(out, trimmed)
		}
	}
	if len(out) == 0 {
		return nil
	}
	return out
}

func loadConfig() appConfig {
	// Default CORS_ALLOWED_ORIGINS is empty (fail-closed) unless explicitly configured.
	origins := splitCSV(env.String("CORS_ALLOWED_ORIGINS", ""))
	trustedProxies := splitCSV(env.String("TRUSTED_PROXIES", ""))

	port := env.Int("PORT", 8080)
	addr := env.String("HTTP_ADDR", fmt.Sprintf(":%d", port))

	return appConfig{
		ServiceName:        env.String("SERVICE_NAME", "abeta-sdui-bff"),
		AppVersion:         env.String("APP_VERSION", usecase.DefaultAppVersion),
		Port:               port,
		Addr:               addr,
		GinMode:            env.String("GIN_MODE", gin.ReleaseMode),
		CatalogVersion:     env.String("TOKEN_CATALOG_VERSION", "1.0.0"),
		AllowedOrigins:     origins,
		TrustedProxies:     trustedProxies,
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
	switch cfg.GinMode {
	case "", gin.DebugMode, gin.ReleaseMode, gin.TestMode:
	default:
		return fmt.Errorf("invalid GIN_MODE %q: must be one of debug, release, test", cfg.GinMode)
	}
	if cfg.Port != 0 && (cfg.Port < 1 || cfg.Port > 65535) {
		return fmt.Errorf("invalid PORT %d: must be between 1 and 65535", cfg.Port)
	}
	if cfg.ReadTimeout < 0 || cfg.WriteTimeout < 0 || cfg.IdleTimeout < 0 || cfg.ShutdownTimeout < 0 {
		return errors.New("HTTP and shutdown timeouts must be non-negative")
	}
	if (cfg.TLSCertFile == "") != (cfg.TLSKeyFile == "") {
		return errors.New("TLS_CERT_FILE and TLS_KEY_FILE must both be set when enabling TLS")
	}
	if cfg.RequireAuth && len(strings.TrimSpace(cfg.JWTSecret)) < minJWTSecretLength {
		return fmt.Errorf("AUTH_REQUIRED=true requires JWT_SECRET of at least %d characters", minJWTSecretLength)
	}
	return nil
}

func resolveReadHeaderTimeout(readTimeout time.Duration) time.Duration {
	if readTimeout > 0 && readTimeout < defaultReadHeaderTimeout {
		return readTimeout
	}
	return defaultReadHeaderTimeout
}

func buildServer(cfg appConfig, log *slog.Logger) *http.Server {
	return buildServerWithContext(context.Background(), cfg, log)
}

func buildServerWithContext(shutdownCtx context.Context, cfg appConfig, log *slog.Logger) *http.Server {
	mode := cfg.GinMode
	if mode == "" {
		mode = gin.ReleaseMode
	}
	gin.SetMode(mode)

	if cfg.RequireAuth && slices.Contains(cfg.AllowedOrigins, "*") {
		log.Warn("CORS_ALLOWED_ORIGINS includes wildcard '*' while AUTH_REQUIRED=true; configure explicit origins in production")
	}

	screenRepo := infra.NewInMemoryScreenRepository()
	actionExec := infra.NewDefaultActionExecutor()
	catalogProvider := infra.NewStaticTokenCatalogProvider(cfg.CatalogVersion)
	analyticsStore := infra.NewInMemoryAnalyticsStore(100)

	router := handler.NewRouter(handler.RouterConfig{
		ServiceName:       cfg.ServiceName,
		AppVersion:        cfg.AppVersion,
		Logger:            log,
		AllowedOrigins:    cfg.AllowedOrigins,
		TrustedProxies:    cfg.TrustedProxies,
		Metrics:           &ginmw.MetricsTracker{},
		ScreenService:     usecase.NewScreenServiceWithVersion(screenRepo, cfg.AppVersion),
		ActionService:     usecase.NewActionService(actionExec),
		CatalogService:    usecase.NewCatalogService(catalogProvider),
		AnalyticsService:  usecase.NewAnalyticsService(analyticsStore),
		ShutdownContext:   shutdownCtx,
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
		TLSConfig:         &tls.Config{MinVersion: tls.VersionTLS12},
		ReadHeaderTimeout: resolveReadHeaderTimeout(cfg.ReadTimeout),
		ReadTimeout:       cfg.ReadTimeout,
		WriteTimeout:      cfg.WriteTimeout,
		IdleTimeout:       cfg.IdleTimeout,
		BaseContext: func(_ net.Listener) context.Context {
			return shutdownCtx
		},
	}
}

func run() error {
	cfg := loadConfig()
	if err := validateConfig(cfg); err != nil {
		return err
	}
	log := logger.Default().With(slog.String("service", cfg.ServiceName))

	shutdownCtx, stopSignals := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stopSignals()

	srv := buildServerWithContext(shutdownCtx, cfg, log)

	errCh := make(chan error, 1)
	go func() {
		log.Info("starting SDUI BFF HTTP server",
			slog.String("addr", cfg.Addr),
			slog.String("app_version", cfg.AppVersion),
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

	shutdownTimeout := cfg.ShutdownTimeout
	if shutdownTimeout <= 0 {
		shutdownTimeout = 10 * time.Second
	}

	select {
	case err := <-errCh:
		stopSignals()
		graceCtx, cancel := context.WithTimeout(context.Background(), shutdownTimeout)
		defer cancel()
		_ = srv.Shutdown(graceCtx)
		return err
	case <-shutdownCtx.Done():
		stopSignals()
		log.Info("shutting down SDUI BFF HTTP server")
		graceCtx, cancel := context.WithTimeout(context.Background(), shutdownTimeout)
		defer cancel()
		if err := srv.Shutdown(graceCtx); err != nil {
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
