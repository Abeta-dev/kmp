package main

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/pem"
	"io"
	"log/slog"
	"math/big"
	"net"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"syscall"
	"testing"
	"time"

	"github.com/abeta-dev/go-libs/ginmw"
	"github.com/gin-gonic/gin"
)

func TestLoadConfigAndBuildServer(t *testing.T) {
	t.Setenv("SERVICE_NAME", "custom-bff")
	t.Setenv("PORT", "9090")
	t.Setenv("HTTP_ADDR", ":9091")
	t.Setenv("GIN_MODE", gin.TestMode)
	t.Setenv("TOKEN_CATALOG_VERSION", "2.0.0")
	t.Setenv("CORS_ALLOWED_ORIGINS", "https://a.abeta.dev, https://b.abeta.dev")
	t.Setenv("HTTP_READ_TIMEOUT", "5s")
	t.Setenv("HTTP_WRITE_TIMEOUT", "7s")
	t.Setenv("HTTP_IDLE_TIMEOUT", "30s")
	t.Setenv("SHUTDOWN_TIMEOUT", "3s")
	t.Setenv("COMPRESSION_ENABLED", "true")
	t.Setenv("ETAG_ENABLED", "true")
	t.Setenv("RATE_LIMIT_ENABLED", "false")
	t.Setenv("AUTH_REQUIRED", "false")
	t.Setenv("JWT_SECRET", "unit-test-secret")
	t.Cleanup(func() { _ = ginmw.SetJWTSecret("") })

	cfg := loadConfig()
	if cfg.ServiceName != "custom-bff" || cfg.AppVersion != "0.8.0" || cfg.Addr != ":9091" || cfg.CatalogVersion != "2.0.0" {
		t.Fatalf("unexpected config: %+v", cfg)
	}
	if len(cfg.AllowedOrigins) != 2 || cfg.ReadTimeout != 5*time.Second || cfg.ShutdownTimeout != 3*time.Second {
		t.Fatalf("unexpected timeouts/origins: %+v", cfg)
	}
	if !cfg.CompressionEnabled || !cfg.ETagEnabled || cfg.RateLimitEnabled || cfg.RequireAuth || cfg.JWTSecret != "unit-test-secret" {
		t.Fatalf("unexpected feature toggles: %+v", cfg)
	}
	if err := validateConfig(cfg); err != nil {
		t.Fatalf("expected valid config, got %v", err)
	}

	log := slog.New(slog.NewJSONHandler(io.Discard, nil))
	srv := buildServer(cfg, log)
	if srv.Addr != ":9091" || srv.Handler == nil || srv.Protocols == nil {
		t.Fatalf("unexpected http.Server: %+v", srv)
	}
	if !srv.Protocols.HTTP1() || !srv.Protocols.HTTP2() || !srv.Protocols.UnencryptedHTTP2() {
		t.Fatalf("expected HTTP/1.1, HTTP/2, and h2c enabled, got %s", srv.Protocols.String())
	}

	w := httptest.NewRecorder()
	srv.Handler.ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/api/v1/screens/home", nil))
	if w.Code != http.StatusOK {
		t.Fatalf("expected 200 from built server, got %d: %s", w.Code, w.Body.String())
	}
	if w.Header().Get("ETag") == "" || w.Header().Get("Cache-Control") != "public, max-age=30" {
		t.Fatalf("expected ETag and Cache-Control by default, got headers=%v", w.Header())
	}
}

func TestValidateConfig_Errors(t *testing.T) {
	if err := validateConfig(appConfig{TLSCertFile: "cert.pem"}); err == nil {
		t.Fatal("expected error when TLS_CERT_FILE set without TLS_KEY_FILE")
	}
	if err := validateConfig(appConfig{TLSKeyFile: "key.pem"}); err == nil {
		t.Fatal("expected error when TLS_KEY_FILE set without TLS_CERT_FILE")
	}
	if err := validateConfig(appConfig{RequireAuth: true, JWTSecret: "   "}); err == nil {
		t.Fatal("expected error when AUTH_REQUIRED=true without JWT_SECRET")
	}

	t.Setenv("GIN_MODE", gin.TestMode)
	t.Setenv("AUTH_REQUIRED", "true")
	t.Setenv("JWT_SECRET", "")
	if err := run(); err == nil {
		t.Fatal("expected run() to fail fast when AUTH_REQUIRED=true without JWT_SECRET")
	}
}

func TestServer_UnencryptedHTTP2_H2C(t *testing.T) {
	t.Setenv("GIN_MODE", gin.TestMode)
	cfg := loadConfig()
	log := slog.New(slog.NewJSONHandler(io.Discard, nil))
	srv := buildServer(cfg, log)

	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("net.Listen: %v", err)
	}
	defer func() { _ = srv.Close() }()

	go func() {
		_ = srv.Serve(ln)
	}()

	clientProtocols := new(http.Protocols)
	clientProtocols.SetUnencryptedHTTP2(true)
	client := &http.Client{
		Transport: &http.Transport{
			Protocols: clientProtocols,
		},
		Timeout: 3 * time.Second,
	}

	req, err := http.NewRequest(http.MethodGet, "http://"+ln.Addr().String()+"/api/v1/screens/containers", nil)
	if err != nil {
		t.Fatalf("NewRequest: %v", err)
	}
	req.Header.Set("Accept-Encoding", "br")

	resp, err := client.Do(req)
	if err != nil {
		t.Fatalf("h2c request failed: %v", err)
	}
	defer func() { _ = resp.Body.Close() }()

	if resp.Proto != "HTTP/2.0" {
		t.Fatalf("expected HTTP/2.0 over h2c, got %s", resp.Proto)
	}
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("expected 200 OK over h2c, got %d", resp.StatusCode)
	}
	if resp.Header.Get("Content-Encoding") != "br" {
		t.Fatalf("expected Content-Encoding=br over h2c, got %q", resp.Header.Get("Content-Encoding"))
	}
}

func generateSelfSignedCert(t *testing.T, dir string) (string, string, *x509.CertPool) {
	t.Helper()
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		t.Fatalf("GenerateKey: %v", err)
	}
	tmpl := &x509.Certificate{
		SerialNumber: big.NewInt(1),
		Subject:      pkix.Name{CommonName: "127.0.0.1"},
		NotBefore:    time.Now().Add(-time.Hour),
		NotAfter:     time.Now().Add(time.Hour),
		KeyUsage:     x509.KeyUsageKeyEncipherment | x509.KeyUsageDigitalSignature,
		ExtKeyUsage:  []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
		IPAddresses:  []net.IP{net.ParseIP("127.0.0.1")},
	}
	derBytes, err := x509.CreateCertificate(rand.Reader, tmpl, tmpl, &key.PublicKey, key)
	if err != nil {
		t.Fatalf("CreateCertificate: %v", err)
	}
	certPath := filepath.Join(dir, "cert.pem")
	keyPath := filepath.Join(dir, "key.pem")

	certPEM := pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: derBytes})
	if err := os.WriteFile(certPath, certPEM, 0o600); err != nil {
		t.Fatalf("WriteFile cert: %v", err)
	}

	keyDER, err := x509.MarshalECPrivateKey(key)
	if err != nil {
		t.Fatalf("MarshalECPrivateKey: %v", err)
	}
	keyPEM := pem.EncodeToMemory(&pem.Block{Type: "EC PRIVATE KEY", Bytes: keyDER})
	if err := os.WriteFile(keyPath, keyPEM, 0o600); err != nil {
		t.Fatalf("WriteFile key: %v", err)
	}

	pool := x509.NewCertPool()
	pool.AppendCertsFromPEM(certPEM)
	return certPath, keyPath, pool
}

func freeLocalAddr(t *testing.T) string {
	t.Helper()
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatalf("net.Listen: %v", err)
	}
	addr := ln.Addr().String()
	_ = ln.Close()
	return addr
}

func TestRun_InvalidAddrAndGracefulShutdown(t *testing.T) {
	t.Setenv("GIN_MODE", gin.TestMode)
	t.Setenv("HTTP_ADDR", "127.0.0.1:99999")
	t.Setenv("SHUTDOWN_TIMEOUT", "1s")

	if err := run(); err == nil {
		t.Fatal("expected error when binding invalid port")
	}

	t.Setenv("HTTP_ADDR", "127.0.0.1:0")
	done := make(chan error, 1)
	go func() {
		done <- run()
	}()

	time.Sleep(150 * time.Millisecond)
	p, err := os.FindProcess(os.Getpid())
	if err != nil {
		t.Fatalf("FindProcess: %v", err)
	}
	if err := p.Signal(syscall.SIGTERM); err != nil {
		t.Fatalf("Signal SIGTERM: %v", err)
	}

	select {
	case err := <-done:
		if err != nil {
			t.Fatalf("expected clean shutdown, got %v", err)
		}
	case <-time.After(3 * time.Second):
		t.Fatal("timed out waiting for graceful shutdown")
	}
}

func TestRun_TLS_HTTP2_AndGracefulShutdown(t *testing.T) {
	certPath, keyPath, pool := generateSelfSignedCert(t, t.TempDir())
	addr := freeLocalAddr(t)

	t.Setenv("GIN_MODE", gin.TestMode)
	t.Setenv("HTTP_ADDR", addr)
	t.Setenv("TLS_CERT_FILE", certPath)
	t.Setenv("TLS_KEY_FILE", keyPath)
	t.Setenv("SHUTDOWN_TIMEOUT", "2s")

	done := make(chan error, 1)
	go func() {
		done <- run()
	}()

	clientProtocols := new(http.Protocols)
	clientProtocols.SetHTTP2(true)
	client := &http.Client{
		Transport: &http.Transport{
			Protocols: clientProtocols,
			TLSClientConfig: &tls.Config{
				RootCAs:    pool,
				NextProtos: []string{"h2"},
			},
		},
		Timeout: 2 * time.Second,
	}

	var resp *http.Response
	var reqErr error
	deadline := time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		resp, reqErr = client.Get("https://" + addr + "/openapi.json")
		if reqErr == nil {
			break
		}
		time.Sleep(40 * time.Millisecond)
	}
	if reqErr != nil {
		t.Fatalf("TLS HTTP/2 request failed: %v", reqErr)
	}
	bodyBytes, _ := io.ReadAll(resp.Body)
	_ = resp.Body.Close()

	if resp.Proto != "HTTP/2.0" || resp.StatusCode != http.StatusOK || !strings.Contains(string(bodyBytes), `"3.1.0"`) {
		t.Fatalf("unexpected TLS HTTP/2 response: proto=%s status=%d body=%s", resp.Proto, resp.StatusCode, string(bodyBytes))
	}

	p, err := os.FindProcess(os.Getpid())
	if err != nil {
		t.Fatalf("FindProcess: %v", err)
	}
	if err := p.Signal(syscall.SIGTERM); err != nil {
		t.Fatalf("Signal SIGTERM: %v", err)
	}

	select {
	case err := <-done:
		if err != nil {
			t.Fatalf("expected clean TLS shutdown, got %v", err)
		}
	case <-time.After(3 * time.Second):
		t.Fatal("timed out waiting for TLS graceful shutdown")
	}
}
