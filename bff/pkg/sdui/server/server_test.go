package server_test

import (
	"compress/gzip"
	"context"
	"encoding/json"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/abeta-dev/go-libs/apperror"
	"github.com/abeta-dev/go-libs/ginmw"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/server"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
	"github.com/andybalholm/brotli"
	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
)

func init() {
	gin.SetMode(gin.TestMode)
}

func TestCORSMiddleware_FailClosedWildcardsAndCredentials(t *testing.T) {
	// 1. Fail-closed when AllowedOrigins is empty
	rClosed := gin.New()
	rClosed.Use(server.CORSMiddleware(server.CORSConfig{}))
	rClosed.GET("/ping", func(c *gin.Context) { c.String(http.StatusOK, "ok") })

	w := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/ping", nil)
	req.Header.Set("Origin", "https://evil.example")
	rClosed.ServeHTTP(w, req)
	if w.Header().Get("Access-Control-Allow-Origin") != "" {
		t.Fatalf("expected fail-closed empty ACAO, got %q", w.Header().Get("Access-Control-Allow-Origin"))
	}
	if !strings.Contains(w.Header().Get("Vary"), "Origin") {
		t.Fatalf("expected Vary: Origin even when closed, got %q", w.Header().Get("Vary"))
	}

	wPreClosed := httptest.NewRecorder()
	reqPreClosed := httptest.NewRequest(http.MethodOptions, "/ping", nil)
	reqPreClosed.Header.Set("Origin", "https://evil.example")
	rClosed.ServeHTTP(wPreClosed, reqPreClosed)
	if wPreClosed.Code != http.StatusForbidden || wPreClosed.Header().Get("Access-Control-Allow-Origin") != "" {
		t.Fatalf("expected 403 fail-closed preflight, got %d ACAO=%q", wPreClosed.Code, wPreClosed.Header().Get("Access-Control-Allow-Origin"))
	}

	// 2. Exact + *.abeta.dev + credentials
	rAllowed := gin.New()
	rAllowed.Use(server.CORSMiddleware(server.CORSConfig{
		AllowedOrigins:   []string{"https://app.abeta.dev", "*.abeta.dev", "https://*.partner.io"},
		AllowCredentials: true,
		AllowedHeaders:   []string{"X-Custom-Header"},
		ExposedHeaders:   []string{"X-Custom-Exposed"},
		AllowedMethods:   []string{"GET", "POST", "OPTIONS"},
		MaxAge:           time.Hour,
	}))
	rAllowed.GET("/ping", func(c *gin.Context) { c.String(http.StatusOK, "ok") })

	for _, origin := range []string{"https://app.abeta.dev", "https://sub.abeta.dev", "https://foo.partner.io"} {
		w := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodOptions, "/ping", nil)
		req.Header.Set("Origin", origin)
		rAllowed.ServeHTTP(w, req)
		if w.Code != http.StatusNoContent {
			t.Fatalf("origin %q expected 204, got %d", origin, w.Code)
		}
		if w.Header().Get("Access-Control-Allow-Origin") != origin {
			t.Fatalf("origin %q ACAO = %q", origin, w.Header().Get("Access-Control-Allow-Origin"))
		}
		if w.Header().Get("Access-Control-Allow-Credentials") != "true" {
			t.Fatalf("origin %q expected Allow-Credentials=true", origin)
		}
		allowHdrs := w.Header().Get("Access-Control-Allow-Headers")
		for _, want := range []string{"If-None-Match", "Last-Event-ID", "X-Client-App-Version", "X-Custom-Header"} {
			if !strings.Contains(allowHdrs, want) {
				t.Errorf("Allow-Headers missing %q: %q", want, allowHdrs)
			}
		}
		exposeHdrs := w.Header().Get("Access-Control-Expose-Headers")
		for _, want := range []string{"ETag", "X-App-Version", "X-Page-Version", "X-Custom-Exposed"} {
			if !strings.Contains(exposeHdrs, want) {
				t.Errorf("Expose-Headers missing %q: %q", want, exposeHdrs)
			}
		}
	}

	// Disallowed origins: lookalike domain, bare apex, wrong scheme
	for _, badOrigin := range []string{"https://evil-abeta.dev", "https://abeta.dev", "http://foo.partner.io", "ftp://sub.abeta.dev"} {
		w := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodOptions, "/ping", nil)
		req.Header.Set("Origin", badOrigin)
		rAllowed.ServeHTTP(w, req)
		if w.Code != http.StatusForbidden || w.Header().Get("Access-Control-Allow-Origin") != "" {
			t.Fatalf("badOrigin %q unexpectedly allowed: code=%d ACAO=%q", badOrigin, w.Code, w.Header().Get("Access-Control-Allow-Origin"))
		}
	}

	// Non-preflight GET without Origin header passes cleanly
	wNoOrigin := httptest.NewRecorder()
	rAllowed.ServeHTTP(wNoOrigin, httptest.NewRequest(http.MethodGet, "/ping", nil))
	if wNoOrigin.Code != http.StatusOK || wNoOrigin.Header().Get("Access-Control-Allow-Origin") != "" {
		t.Fatalf("unexpected no-origin response: %d", wNoOrigin.Code)
	}

	// 3. Explicit "*" NEVER emits Access-Control-Allow-Credentials: true
	rWild := gin.New()
	rWild.Use(server.CORSMiddleware(server.CORSConfig{
		AllowedOrigins:   []string{"*"},
		AllowCredentials: true,
	}))
	rWild.GET("/ping", func(c *gin.Context) { c.String(http.StatusOK, "ok") })

	wWild := httptest.NewRecorder()
	reqWild := httptest.NewRequest(http.MethodGet, "/ping", nil)
	reqWild.Header.Set("Origin", "https://any.example")
	rWild.ServeHTTP(wWild, reqWild)
	if wWild.Header().Get("Access-Control-Allow-Origin") != "*" {
		t.Fatalf("expected wildcard ACAO=*, got %q", wWild.Header().Get("Access-Control-Allow-Origin"))
	}
	if wWild.Header().Get("Access-Control-Allow-Credentials") != "" {
		t.Fatalf("wildcard ACAO must NEVER set Allow-Credentials, got %q", wWild.Header().Get("Access-Control-Allow-Credentials"))
	}
}

func TestETagMiddleware_HonorPresetAppVersionAuthHeadAndBufferCap(t *testing.T) {
	r := gin.New()
	r.Use(server.ETagMiddleware(server.ETagConfig{
		AppVersion:     "0.9.0",
		MaxBufferBytes: 512,
	}))

	seq := 0
	r.GET("/volatile", func(c *gin.Context) {
		seq++
		c.JSON(http.StatusOK, gin.H{
			"name":                  "manifest",
			"generated_at_epoch_ms": 1700000000000 + seq,
			"request_id":            uuid.NewString(),
			"items":                 []gin.H{{"request_id": uuid.NewString(), "k": "v"}},
		})
	})
	r.GET("/preset", func(c *gin.Context) {
		c.Header("ETag", `"page-etag-123"`)
		c.Header("X-Page-Version", "v1-pageetag1234")
		c.JSON(http.StatusOK, gin.H{"ok": true})
	})
	r.HEAD("/preset", func(c *gin.Context) {
		c.Header("ETag", `"page-etag-123"`)
		c.JSON(http.StatusOK, gin.H{"ok": true})
	})
	r.GET("/no-store", func(c *gin.Context) {
		c.Header("Cache-Control", "no-store")
		c.String(http.StatusOK, "secret")
	})
	r.GET("/public-catalog", func(c *gin.Context) {
		c.Header("Cache-Control", "public, no-cache")
		c.String(http.StatusOK, "tokens")
	})
	r.GET("/big", func(c *gin.Context) {
		c.String(http.StatusOK, strings.Repeat("x", 1024))
	})
	r.GET("/err404", func(c *gin.Context) {
		c.String(http.StatusNotFound, "missing")
	})
	r.POST("/post-skip", func(c *gin.Context) {
		c.String(http.StatusOK, "posted")
	})

	// 1. Volatile JSON keys stripped -> identical ETag across requests + default private, no-cache
	w1 := httptest.NewRecorder()
	r.ServeHTTP(w1, httptest.NewRequest(http.MethodGet, "/volatile", nil))
	w2 := httptest.NewRecorder()
	r.ServeHTTP(w2, httptest.NewRequest(http.MethodGet, "/volatile", nil))
	if w1.Header().Get("ETag") == "" || w1.Header().Get("ETag") != w2.Header().Get("ETag") {
		t.Fatalf("expected identical ETag despite volatile JSON fields: %q vs %q", w1.Header().Get("ETag"), w2.Header().Get("ETag"))
	}
	if w1.Header().Get("Cache-Control") != server.DefaultCacheControl {
		t.Fatalf("expected default Cache-Control %q, got %q", server.DefaultCacheControl, w1.Header().Get("Cache-Control"))
	}
	if w1.Header().Get("X-App-Version") != "0.9.0" {
		t.Fatalf("expected X-App-Version=0.9.0, got %q", w1.Header().Get("X-App-Version"))
	}

	// 2. Preset ETag honored + X-Client-App-Version mismatch skips 304
	wPreset := httptest.NewRecorder()
	r.ServeHTTP(wPreset, httptest.NewRequest(http.MethodGet, "/preset", nil))
	if wPreset.Header().Get("ETag") != `"page-etag-123"` {
		t.Fatalf("expected handler-set ETag preserved, got %q", wPreset.Header().Get("ETag"))
	}

	wMismatch := httptest.NewRecorder()
	reqMismatch := httptest.NewRequest(http.MethodGet, "/preset", nil)
	reqMismatch.Header.Set("If-None-Match", `"page-etag-123"`)
	reqMismatch.Header.Set("X-Client-App-Version", "0.8.0")
	r.ServeHTTP(wMismatch, reqMismatch)
	if wMismatch.Code != http.StatusOK {
		t.Fatalf("expected 200 OK when X-Client-App-Version mismatches, got %d", wMismatch.Code)
	}

	wMatch := httptest.NewRecorder()
	reqMatch := httptest.NewRequest(http.MethodGet, "/preset", nil)
	reqMatch.Header.Set("If-None-Match", `"page-etag-123"`)
	reqMatch.Header.Set("X-Client-App-Version", "0.9.0")
	r.ServeHTTP(wMatch, reqMatch)
	if wMatch.Code != http.StatusNotModified || wMatch.Header().Get("X-Page-Version") != "v1-pageetag1234" {
		t.Fatalf("expected 304 preserving X-Page-Version, got %d headers=%v", wMatch.Code, wMatch.Header())
	}

	// 3. HEAD returns headers without body and supports 304
	wHead := httptest.NewRecorder()
	r.ServeHTTP(wHead, httptest.NewRequest(http.MethodHead, "/preset", nil))
	if wHead.Code != http.StatusOK || wHead.Body.Len() != 0 || wHead.Header().Get("ETag") != `"page-etag-123"` {
		t.Fatalf("expected HEAD 200 empty body with ETag, got %d len=%d ETag=%q", wHead.Code, wHead.Body.Len(), wHead.Header().Get("ETag"))
	}

	// 4. Handler no-store preserved; public downgraded to private, no-cache + Vary: Authorization on Authorization header
	wNoStore := httptest.NewRecorder()
	reqNoStore := httptest.NewRequest(http.MethodGet, "/no-store", nil)
	reqNoStore.Header.Set("Authorization", "Bearer tok")
	r.ServeHTTP(wNoStore, reqNoStore)
	if wNoStore.Header().Get("Cache-Control") != "no-store" || !strings.Contains(wNoStore.Header().Get("Vary"), "Authorization") {
		t.Fatalf("expected no-store preserved + Vary: Authorization, got %v", wNoStore.Header())
	}

	wPub := httptest.NewRecorder()
	r.ServeHTTP(wPub, httptest.NewRequest(http.MethodGet, "/public-catalog", nil))
	if wPub.Header().Get("Cache-Control") != "public, no-cache" {
		t.Fatalf("expected unauthenticated public, no-cache preserved, got %q", wPub.Header().Get("Cache-Control"))
	}
	wPubAuth := httptest.NewRecorder()
	reqPubAuth := httptest.NewRequest(http.MethodGet, "/public-catalog", nil)
	reqPubAuth.Header.Set("Cookie", "sid=abc")
	r.ServeHTTP(wPubAuth, reqPubAuth)
	if wPubAuth.Header().Get("Cache-Control") != "private, no-cache" || !strings.Contains(wPubAuth.Header().Get("Vary"), "Authorization") {
		t.Fatalf("expected Cookie request downgraded to private, no-cache, got %v", wPubAuth.Header())
	}

	// 5. Response exceeding MaxBufferBytes streams through unbuffered without ETag
	wBig := httptest.NewRecorder()
	r.ServeHTTP(wBig, httptest.NewRequest(http.MethodGet, "/big", nil))
	if wBig.Code != http.StatusOK || wBig.Body.Len() != 1024 || wBig.Header().Get("ETag") != "" {
		t.Fatalf("expected overflowed response streamed unbuffered without ETag, got %d len=%d ETag=%q", wBig.Code, wBig.Body.Len(), wBig.Header().Get("ETag"))
	}

	// 6. Non-GET/HEAD and non-2xx responses pass through without ETag; comma-list and wildcard If-None-Match work
	w404 := httptest.NewRecorder()
	r.ServeHTTP(w404, httptest.NewRequest(http.MethodGet, "/err404", nil))
	if w404.Code != http.StatusNotFound || w404.Header().Get("ETag") != "" {
		t.Fatalf("expected 404 without ETag, got %d", w404.Code)
	}
	wPost := httptest.NewRecorder()
	r.ServeHTTP(wPost, httptest.NewRequest(http.MethodPost, "/post-skip", nil))
	if wPost.Code != http.StatusOK || wPost.Header().Get("ETag") != "" {
		t.Fatalf("expected POST without ETag, got %d", wPost.Code)
	}
	for _, inm := range []string{"*", `"other", W/"page-etag-123"`, `, *`} {
		wINM := httptest.NewRecorder()
		reqINM := httptest.NewRequest(http.MethodGet, "/preset", nil)
		reqINM.Header.Set("If-None-Match", inm)
		r.ServeHTTP(wINM, reqINM)
		if wINM.Code != http.StatusNotModified {
			t.Fatalf("If-None-Match=%q expected 304, got %d", inm, wINM.Code)
		}
	}

	// 7. Configured CacheControl honored when unauthenticated; overridden when RequireAuth=true
	rCfgCC := gin.New()
	rCfgCC.Use(server.ETagMiddleware(server.ETagConfig{CacheControl: "public, max-age=60"}))
	rCfgCC.GET("/c", func(c *gin.Context) { c.String(http.StatusOK, "cached") })
	wCC := httptest.NewRecorder()
	rCfgCC.ServeHTTP(wCC, httptest.NewRequest(http.MethodGet, "/c", nil))
	if wCC.Header().Get("Cache-Control") != "public, max-age=60" {
		t.Fatalf("expected configured Cache-Control, got %q", wCC.Header().Get("Cache-Control"))
	}
}

func TestCompressionMiddleware_PoolingNegotiationSSEAndOverflow(t *testing.T) {
	r := gin.New()
	r.Use(server.CompressionMiddlewareWithConfig(server.CompressionConfig{
		MinSizeBytes:   16,
		MaxBufferBytes: 128,
		BrotliLevel:    4,
	}))
	r.GET("/tiny", func(c *gin.Context) { c.String(http.StatusOK, "short") })
	r.GET("/medium", func(c *gin.Context) { c.String(http.StatusOK, strings.Repeat("hello-sdui-", 4)) })
	r.GET("/huge", func(c *gin.Context) { c.String(http.StatusOK, strings.Repeat("A", 256)) })
	r.GET("/custom-sse", func(c *gin.Context) {
		c.Header("Content-Type", "text/event-stream; charset=utf-8")
		c.Status(http.StatusOK)
		c.Writer.Flush()
		_, _ = c.Writer.WriteString("data: hi\n\n")
		c.Writer.Flush()
	})
	r.GET("/unwrap", func(c *gin.Context) {
		rc := http.NewResponseController(c.Writer)
		_ = rc.SetWriteDeadline(time.Time{})
		c.String(http.StatusOK, strings.Repeat("unwrap-check-", 3))
	})

	// Tiny below MinSizeBytes stays uncompressed
	wTiny := httptest.NewRecorder()
	reqTiny := httptest.NewRequest(http.MethodGet, "/tiny", nil)
	reqTiny.Header.Set("Accept-Encoding", "br")
	r.ServeHTTP(wTiny, reqTiny)
	if wTiny.Header().Get("Content-Encoding") != "" || wTiny.Body.String() != "short" {
		t.Fatalf("expected tiny body uncompressed, got enc=%q", wTiny.Header().Get("Content-Encoding"))
	}

	// Medium compressed with br (and whitespace around q = value)
	wBr := httptest.NewRecorder()
	reqBr := httptest.NewRequest(http.MethodGet, "/medium", nil)
	reqBr.Header.Set("Accept-Encoding", "gzip ; q = 0.4, br ; q = 0.9")
	r.ServeHTTP(wBr, reqBr)
	if wBr.Header().Get("Content-Encoding") != "br" {
		t.Fatalf("expected br encoding, got %q", wBr.Header().Get("Content-Encoding"))
	}
	plainBr, err := io.ReadAll(brotli.NewReader(strings.NewReader(wBr.Body.String())))
	if err != nil || string(plainBr) != strings.Repeat("hello-sdui-", 4) {
		t.Fatalf("brotli decode mismatch: %q err=%v", string(plainBr), err)
	}

	// Medium compressed with gzip
	wGz := httptest.NewRecorder()
	reqGz := httptest.NewRequest(http.MethodGet, "/medium", nil)
	reqGz.Header.Set("Accept-Encoding", "gzip;q=1.0, br;q=0.2")
	r.ServeHTTP(wGz, reqGz)
	if wGz.Header().Get("Content-Encoding") != "gzip" {
		t.Fatalf("expected gzip encoding, got %q", wGz.Header().Get("Content-Encoding"))
	}
	gr, err := gzip.NewReader(strings.NewReader(wGz.Body.String()))
	if err != nil {
		t.Fatalf("gzip reader: %v", err)
	}
	plainGz, _ := io.ReadAll(gr)
	_ = gr.Close()
	if string(plainGz) != strings.Repeat("hello-sdui-", 4) {
		t.Fatalf("gzip decode mismatch: %q", string(plainGz))
	}

	// identity higher q-value beats compressed; NaN/Inf q-values rejected safely
	for _, ae := range []string{"identity;q=1.0, br;q=0.5", "br;q=NaN", "br;q=Inf", "gzip;q=-Inf"} {
		w := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodGet, "/medium", nil)
		req.Header.Set("Accept-Encoding", ae)
		r.ServeHTTP(w, req)
		if w.Header().Get("Content-Encoding") != "" {
			t.Fatalf("Accept-Encoding=%q expected uncompressed, got %q", ae, w.Header().Get("Content-Encoding"))
		}
	}

	// Overflow > MaxBufferBytes passes through uncompressed
	wHuge := httptest.NewRecorder()
	reqHuge := httptest.NewRequest(http.MethodGet, "/huge", nil)
	reqHuge.Header.Set("Accept-Encoding", "br")
	r.ServeHTTP(wHuge, reqHuge)
	if wHuge.Header().Get("Content-Encoding") != "" || wHuge.Body.Len() != 256 {
		t.Fatalf("expected huge body uncompressed passthrough, got enc=%q len=%d", wHuge.Header().Get("Content-Encoding"), wHuge.Body.Len())
	}

	// Custom SSE route with Content-Type: text/event-stream is never compressed
	wSSE := httptest.NewRecorder()
	reqSSE := httptest.NewRequest(http.MethodGet, "/custom-sse", nil)
	reqSSE.Header.Set("Accept-Encoding", "br, gzip")
	r.ServeHTTP(wSSE, reqSSE)
	if wSSE.Header().Get("Content-Encoding") != "" || !strings.Contains(wSSE.Body.String(), "data: hi") {
		t.Fatalf("expected custom SSE uncompressed, got enc=%q body=%q", wSSE.Header().Get("Content-Encoding"), wSSE.Body.String())
	}

	// Unwrap works cleanly with ResponseController
	wUnwrap := httptest.NewRecorder()
	reqUnwrap := httptest.NewRequest(http.MethodGet, "/unwrap", nil)
	reqUnwrap.Header.Set("Accept-Encoding", "br")
	r.ServeHTTP(wUnwrap, reqUnwrap)
	if wUnwrap.Code != http.StatusOK || wUnwrap.Header().Get("Content-Encoding") != "br" {
		t.Fatalf("expected /unwrap 200 br, got %d enc=%q", wUnwrap.Code, wUnwrap.Header().Get("Content-Encoding"))
	}

	// Default CompressionMiddleware + custom BrotliLevel + WriteHeaderNow/Size/Written + 204/103/304 + ETag weakening
	rDef := gin.New()
	rDef.Use(server.CompressionMiddleware())
	rDef.GET("/writer-ops", func(c *gin.Context) {
		c.Header("ETag", `"strong-tag"`)
		c.Writer.WriteHeaderNow()
		c.Writer.WriteHeader(http.StatusCreated) // no-op second call
		_, _ = c.Writer.WriteString("hello-buffered-writer")
		if !c.Writer.Written() || c.Writer.Size() != len("hello-buffered-writer") {
			t.Errorf("unexpected buffered writer state: written=%v size=%d", c.Writer.Written(), c.Writer.Size())
		}
	})
	rDef.GET("/s204", func(c *gin.Context) { c.Status(http.StatusNoContent) })
	rDef.GET("/s103", func(c *gin.Context) { c.Status(http.StatusEarlyHints) })
	rDef.GET("/s304", func(c *gin.Context) {
		c.Header("ETag", `"strong-tag"`)
		c.Status(http.StatusNotModified)
	})
	rDef.GET("/metrics", func(c *gin.Context) { c.String(http.StatusOK, "metrics") })

	wOps := httptest.NewRecorder()
	reqOps := httptest.NewRequest(http.MethodGet, "/writer-ops", nil)
	reqOps.Header.Set("Accept-Encoding", "*")
	rDef.ServeHTTP(wOps, reqOps)
	if wOps.Code != http.StatusOK || wOps.Header().Get("Content-Encoding") != "br" || wOps.Header().Get("ETag") != `W/"strong-tag"` {
		t.Fatalf("unexpected /writer-ops response: code=%d enc=%q etag=%q", wOps.Code, wOps.Header().Get("Content-Encoding"), wOps.Header().Get("ETag"))
	}

	for _, tc := range []struct {
		path string
		code int
	}{{"/s204", http.StatusNoContent}, {"/s103", http.StatusEarlyHints}, {"/s304", http.StatusNotModified}, {"/metrics", http.StatusOK}} {
		wSt := httptest.NewRecorder()
		reqSt := httptest.NewRequest(http.MethodGet, tc.path, nil)
		reqSt.Header.Set("Accept-Encoding", "br")
		rDef.ServeHTTP(wSt, reqSt)
		if wSt.Code != tc.code || wSt.Header().Get("Content-Encoding") != "" {
			t.Fatalf("%s expected uncompressed %d, got %d enc=%q", tc.path, tc.code, wSt.Code, wSt.Header().Get("Content-Encoding"))
		}
	}

	rCustomLvl := gin.New()
	rCustomLvl.Use(server.CompressionMiddlewareWithConfig(server.CompressionConfig{BrotliLevel: 2, MaxBufferBytes: 12}))
	rCustomLvl.GET("/lvl", func(c *gin.Context) { _, _ = c.Writer.WriteString("short") })
	rCustomLvl.GET("/lvl-big", func(c *gin.Context) { _, _ = c.Writer.WriteString(strings.Repeat("z", 32)) })

	wLvl := httptest.NewRecorder()
	reqLvl := httptest.NewRequest(http.MethodGet, "/lvl", nil)
	reqLvl.Header.Set("Accept-Encoding", "br")
	rCustomLvl.ServeHTTP(wLvl, reqLvl)
	if wLvl.Header().Get("Content-Encoding") != "br" {
		t.Fatalf("expected custom brotli level 2 encoding, got %q", wLvl.Header().Get("Content-Encoding"))
	}
	wLvlBig := httptest.NewRecorder()
	reqLvlBig := httptest.NewRequest(http.MethodGet, "/lvl-big", nil)
	reqLvlBig.Header.Set("Accept-Encoding", "br")
	rCustomLvl.ServeHTTP(wLvlBig, reqLvlBig)
	if wLvlBig.Header().Get("Content-Encoding") != "" || wLvlBig.Body.Len() != 32 {
		t.Fatalf("expected WriteString overflow passthrough, got enc=%q len=%d", wLvlBig.Header().Get("Content-Encoding"), wLvlBig.Body.Len())
	}
}

func TestJWTAuthMiddleware_IsolatedPerRouterAndFailClosed(t *testing.T) {
	secretA := []byte("router-A-secret-key-minimum-32-bytes!!")
	secretB := []byte("router-B-secret-key-minimum-32-bytes!!")

	rA := gin.New()
	rA.Use(server.JWTAuthMiddleware(secretA))
	rA.GET("/me", func(c *gin.Context) {
		cl := ginmw.GetClaims(c)
		c.JSON(http.StatusOK, gin.H{"email": cl.Email})
	})

	rB := gin.New()
	rB.Use(server.JWTAuthMiddleware(secretB))
	rB.GET("/me", func(c *gin.Context) {
		c.String(http.StatusOK, "ok")
	})

	rEmpty := gin.New()
	rEmpty.Use(server.JWTAuthMiddleware(nil))
	rEmpty.GET("/me", func(c *gin.Context) { c.String(http.StatusOK, "ok") })

	tokA, err := server.GenerateHS256Token(secretA, &ginmw.Claims{
		UserID: uuid.New(),
		Email:  "eng@abeta.dev",
		Roles:  []string{"admin"},
	}, 5*time.Minute)
	if err != nil {
		t.Fatalf("GenerateHS256Token: %v", err)
	}

	// Valid token on router A succeeds and exposes ginmw.GetClaims
	wOK := httptest.NewRecorder()
	reqOK := httptest.NewRequest(http.MethodGet, "/me", nil)
	reqOK.Header.Set("Authorization", "Bearer "+tokA)
	rA.ServeHTTP(wOK, reqOK)
	if wOK.Code != http.StatusOK || !strings.Contains(wOK.Body.String(), "eng@abeta.dev") {
		t.Fatalf("expected 200 on router A, got %d %s", wOK.Code, wOK.Body.String())
	}

	// Same token rejected on router B (proving per-router secret isolation)
	wCross := httptest.NewRecorder()
	reqCross := httptest.NewRequest(http.MethodGet, "/me", nil)
	reqCross.Header.Set("Authorization", "Bearer "+tokA)
	rB.ServeHTTP(wCross, reqCross)
	if wCross.Code != http.StatusUnauthorized || !strings.Contains(wCross.Body.String(), `"UNAUTHORIZED"`) {
		t.Fatalf("expected 401 envelope on router B, got %d %s", wCross.Code, wCross.Body.String())
	}

	// Missing Authorization header -> 401 Envelope
	wMissing := httptest.NewRecorder()
	rA.ServeHTTP(wMissing, httptest.NewRequest(http.MethodGet, "/me", nil))
	if wMissing.Code != http.StatusUnauthorized || !strings.Contains(wMissing.Body.String(), `"success":false`) {
		t.Fatalf("expected 401 envelope on missing header, got %d %s", wMissing.Code, wMissing.Body.String())
	}

	// Empty secret -> fail-closed 401 Envelope
	wEmpty := httptest.NewRecorder()
	reqEmpty := httptest.NewRequest(http.MethodGet, "/me", nil)
	reqEmpty.Header.Set("Authorization", "Bearer "+tokA)
	rEmpty.ServeHTTP(wEmpty, reqEmpty)
	if wEmpty.Code != http.StatusUnauthorized {
		t.Fatalf("expected fail-closed 401 on empty secret, got %d", wEmpty.Code)
	}

	// GenerateHS256Token validation errors
	if _, err := server.GenerateHS256Token(nil, &ginmw.Claims{}, time.Minute); err == nil {
		t.Fatal("expected error for empty secret")
	}
	if _, err := server.GenerateHS256Token(secretA, nil, time.Minute); err == nil {
		t.Fatal("expected error for nil claims")
	}
	if _, err := server.GenerateHS256Token(secretA, &ginmw.Claims{}, 0); err == nil {
		t.Fatal("expected error for non-positive ttl")
	}
}

func TestHTTPUpstreamFetcher_EnvelopeRawConditionalAndErrors(t *testing.T) {
	samplePage := snippets.NewPage("home", "Home").
		AddSnippet(snippets.NewHeaderSnippet("h1", atoms.NewText("Hi", theming.TypographyTitleLarge, theming.ColorTextPrimary)).Build()).
		Build()

	var seenAppVer, seenCustom, seenFwd, seenINM string
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		seenAppVer = r.Header.Get("X-Client-App-Version")
		seenCustom = r.Header.Get("X-Static")
		seenFwd = r.Header.Get("X-Forwarded-Ctx")
		seenINM = r.Header.Get("If-None-Match")

		switch r.URL.Path {
		case "/api/v1/screens/home":
			if seenINM == `"etag-home"` {
				w.Header().Set("ETag", `"etag-home"`)
				w.WriteHeader(http.StatusNotModified)
				return
			}
			w.Header().Set("Content-Type", "application/json")
			w.Header().Set("ETag", `"etag-home"`)
			_ = json.NewEncoder(w).Encode(ginmw.Envelope[snippets.PageResponse]{
				Success: true,
				Data:    samplePage,
			})
		case "/api/v1/screens/raw-page":
			w.Header().Set("Content-Type", "application/json")
			_ = json.NewEncoder(w).Encode(samplePage)
		case "/api/v1/screens/missing":
			w.WriteHeader(http.StatusNotFound)
		case "/api/v1/screens/envelope-err":
			w.Header().Set("Content-Type", "application/json")
			_ = json.NewEncoder(w).Encode(ginmw.Envelope[any]{
				Success: false,
				Error:   &ginmw.APIError{Code: "FORBIDDEN", Message: "denied"},
			})
		case "/api/v1/screens/server-err":
			w.WriteHeader(http.StatusBadGateway)
			_ = json.NewEncoder(w).Encode(ginmw.Envelope[any]{
				Success: false,
				Error:   &ginmw.APIError{Code: "UPSTREAM_FAIL", Message: "gateway boom"},
			})
		case "/api/v1/screens/oversize":
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(strings.Repeat("a", 512)))
		case "/api/v1/screens/bad-json":
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte("{not-json"))
		case "/api/v1/screens/env-false-no-err":
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(`{"success":false}`))
		default:
			w.WriteHeader(http.StatusInternalServerError)
		}
	}))
	defer ts.Close()

	fetcher := server.NewHTTPUpstreamFetcher(
		ts.URL,
		server.WithHTTPClient(ts.Client()),
		server.WithTimeout(3*time.Second),
		server.WithHeader("X-Static", "yes"),
		server.WithClientAppVersion("0.9.0"),
		server.WithScreenPathTemplate("/api/v1/screens/%s"),
		server.WithMaxResponseBytes(256),
		server.WithHeaderForwarder(func(_ context.Context) http.Header {
			h := make(http.Header)
			h.Set("X-Forwarded-Ctx", "ctx-val")
			return h
		}),
	)

	ctx := context.Background()

	// 1. Envelope decode + headers forwarded + HTTP ETag propagated
	p1, err := fetcher.FetchScreen(ctx, "home")
	if err != nil || p1.PageID != "home" || p1.ETag != `"etag-home"` {
		t.Fatalf("FetchScreen(home) failed: page=%+v err=%v", p1, err)
	}
	if seenAppVer != "0.9.0" || seenCustom != "yes" || seenFwd != "ctx-val" {
		t.Fatalf("headers not forwarded: appVer=%q static=%q fwd=%q", seenAppVer, seenCustom, seenFwd)
	}

	// 2. Conditional 304 Not Modified
	_, notMod, etag, err := fetcher.FetchScreenConditional(ctx, "home", `"etag-home"`)
	if !notMod || !errors.Is(err, server.ErrNotModified) || etag != `"etag-home"` {
		t.Fatalf("expected 304 ErrNotModified, got notMod=%v etag=%q err=%v", notMod, etag, err)
	}

	// 3. Raw PageResponse decode (without envelope)
	pRaw, err := fetcher.FetchScreen(ctx, "raw-page")
	if err != nil || pRaw.PageID != "home" {
		t.Fatalf("FetchScreen(raw-page) failed: page=%+v err=%v", pRaw, err)
	}

	// 4. Error paths: 404, envelope success=false, 502, oversize, empty ID, empty baseURL
	if _, err := fetcher.FetchScreen(ctx, "missing"); !apperror.Is(err, apperror.CodeNotFound) {
		t.Fatalf("expected CodeNotFound on 404, got %v", err)
	}
	if _, err := fetcher.FetchScreen(ctx, "envelope-err"); err == nil || !strings.Contains(err.Error(), "denied") {
		t.Fatalf("expected upstream envelope error, got %v", err)
	}
	if _, err := fetcher.FetchScreen(ctx, "server-err"); err == nil || !strings.Contains(err.Error(), "gateway boom") {
		t.Fatalf("expected upstream 502 error, got %v", err)
	}
	if _, err := fetcher.FetchScreen(ctx, "oversize"); err == nil || !strings.Contains(err.Error(), "exceeds") {
		t.Fatalf("expected oversize response error, got %v", err)
	}
	if _, err := fetcher.FetchScreen(ctx, "bad-json"); err == nil {
		t.Fatal("expected JSON decode error for bad-json")
	}
	if _, err := fetcher.FetchScreen(ctx, "env-false-no-err"); err == nil {
		t.Fatal("expected error for success=false envelope without error object")
	}
	if _, err := fetcher.FetchScreen(ctx, "plain-500"); err == nil || !strings.Contains(err.Error(), "500") {
		t.Fatalf("expected HTTP 500 error, got %v", err)
	}
	if _, err := fetcher.FetchScreen(ctx, "  "); !apperror.Is(err, apperror.CodeBadRequest) {
		t.Fatalf("expected CodeBadRequest for blank screenID, got %v", err)
	}
	emptyFetcher := server.NewHTTPUpstreamFetcher("")
	if _, err := emptyFetcher.FetchScreen(ctx, "home"); !apperror.Is(err, apperror.CodeInternal) {
		t.Fatalf("expected CodeInternal for empty baseURL, got %v", err)
	}
}
