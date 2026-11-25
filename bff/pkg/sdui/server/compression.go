package server

import (
	"bytes"
	"compress/gzip"
	"io"
	"math"
	"net/http"
	"strconv"
	"strings"
	"sync"

	"github.com/andybalholm/brotli"
	"github.com/gin-gonic/gin"
)

const defaultBrotliLevel = 4

// CompressionConfig tunes response compression thresholds, level, and buffer limits.
type CompressionConfig struct {
	// MinSizeBytes skips compression for responses smaller than this byte count (default 0).
	MinSizeBytes int
	// MaxBufferBytes caps in-memory buffering before streaming uncompressed (default 8 MiB).
	MaxBufferBytes int
	// BrotliLevel configures Brotli compression quality (defaults to 4).
	BrotliLevel int
}

var (
	brotliLevel4Pool = sync.Pool{
		New: func() any {
			return brotli.NewWriterLevel(io.Discard, defaultBrotliLevel)
		},
	}
	gzipWriterPool = sync.Pool{
		New: func() any {
			return gzip.NewWriter(io.Discard)
		},
	}
)

type bufferedResponseWriter struct {
	gin.ResponseWriter
	buf         bytes.Buffer
	status      int
	wroteHeader bool
	maxBytes    int
	overflowed  bool
	passthrough bool
	totalSize   int
}

func newBufferedResponseWriter(w gin.ResponseWriter, maxBytes int) *bufferedResponseWriter {
	return &bufferedResponseWriter{
		ResponseWriter: w,
		status:         http.StatusOK,
		maxBytes:       maxBytes,
	}
}

// Unwrap exposes the underlying http.ResponseWriter for http.NewResponseController.
func (w *bufferedResponseWriter) Unwrap() http.ResponseWriter {
	return w.ResponseWriter
}

func (w *bufferedResponseWriter) WriteHeader(code int) {
	if w.wroteHeader {
		return
	}
	w.status = code
	w.wroteHeader = true
	if isEventStreamContentType(w.Header().Get("Content-Type")) {
		w.flushToOrig()
	}
}

func (w *bufferedResponseWriter) WriteHeaderNow() {
	if !w.wroteHeader {
		w.WriteHeader(http.StatusOK)
	}
}

func (w *bufferedResponseWriter) Write(data []byte) (int, error) {
	if !w.wroteHeader {
		w.WriteHeader(http.StatusOK)
	}
	if w.passthrough || w.overflowed {
		n, err := w.ResponseWriter.Write(data)
		w.totalSize += n
		return n, err
	}
	if w.maxBytes > 0 && w.buf.Len()+len(data) > w.maxBytes {
		w.overflowed = true
		w.flushToOrig()
		n, err := w.ResponseWriter.Write(data)
		w.totalSize += n
		return n, err
	}
	n, err := w.buf.Write(data)
	w.totalSize += n
	return n, err
}

func (w *bufferedResponseWriter) WriteString(s string) (int, error) {
	if !w.wroteHeader {
		w.WriteHeader(http.StatusOK)
	}
	if w.passthrough || w.overflowed {
		n, err := w.ResponseWriter.WriteString(s)
		w.totalSize += n
		return n, err
	}
	if w.maxBytes > 0 && w.buf.Len()+len(s) > w.maxBytes {
		w.overflowed = true
		w.flushToOrig()
		n, err := w.ResponseWriter.WriteString(s)
		w.totalSize += n
		return n, err
	}
	n, err := w.buf.WriteString(s)
	w.totalSize += n
	return n, err
}

func (w *bufferedResponseWriter) Flush() {
	if !w.passthrough && isEventStreamContentType(w.Header().Get("Content-Type")) {
		w.flushToOrig()
	}
	if w.passthrough || w.overflowed {
		w.ResponseWriter.Flush()
	}
}

func (w *bufferedResponseWriter) flushToOrig() {
	if w.passthrough {
		return
	}
	w.passthrough = true
	w.ResponseWriter.WriteHeader(w.status)
	if w.buf.Len() > 0 {
		_, _ = w.ResponseWriter.Write(w.buf.Bytes())
		w.buf.Reset()
	}
}

func (w *bufferedResponseWriter) Status() int {
	return w.status
}

func (w *bufferedResponseWriter) Size() int {
	return w.totalSize
}

func (w *bufferedResponseWriter) Written() bool {
	return w.wroteHeader
}

// CompressionMiddleware negotiates pooled Brotli ("br", level 4) or gzip ("gzip")
// compression with an 8 MiB buffer cap, skipping metrics, SSE streams, and HEAD requests.
func CompressionMiddleware() gin.HandlerFunc {
	return CompressionMiddlewareWithConfig(CompressionConfig{})
}

// CompressionMiddlewareWithConfig creates a CompressionMiddleware with custom size/level bounds.
func CompressionMiddlewareWithConfig(cfg CompressionConfig) gin.HandlerFunc {
	maxBuf := cfg.MaxBufferBytes
	if maxBuf <= 0 {
		maxBuf = DefaultMaxBufferBytes
	}
	minSize := cfg.MinSizeBytes
	if minSize < 0 {
		minSize = 0
	}
	level := cfg.BrotliLevel
	if level <= 0 {
		level = defaultBrotliLevel
	}

	return func(c *gin.Context) {
		if c.Request.URL.Path == "/metrics" ||
			strings.HasPrefix(c.Request.URL.Path, "/api/v1/streams/") ||
			c.Request.Method == http.MethodHead ||
			strings.Contains(strings.ToLower(c.GetHeader("Accept")), "text/event-stream") {
			c.Next()
			return
		}

		appendVary(c.Writer.Header(), "Accept-Encoding")

		enc := negotiateEncoding(c.GetHeader("Accept-Encoding"))
		if enc == "" {
			c.Next()
			return
		}

		orig := c.Writer
		bw := newBufferedResponseWriter(orig, maxBuf)
		c.Writer = bw
		completed := false
		defer func() {
			if !completed {
				c.Writer = orig
			}
		}()

		c.Next()
		completed = true
		c.Writer = orig

		if bw.passthrough || bw.overflowed {
			return
		}

		status := bw.Status()
		body := bw.buf.Bytes()

		if !bodyAllowedForStatus(status) ||
			len(body) == 0 ||
			len(body) < minSize ||
			orig.Header().Get("Content-Encoding") != "" ||
			isEventStreamContentType(orig.Header().Get("Content-Type")) {
			if status == http.StatusNotModified {
				weakenETag(orig.Header())
			}
			if bw.wroteHeader || len(body) > 0 {
				orig.WriteHeader(status)
			}
			if len(body) > 0 && bodyAllowedForStatus(status) {
				_, _ = orig.Write(body)
			}
			return
		}

		compressed, err := compressBytes(enc, body, level)
		if err != nil {
			if bw.wroteHeader || len(body) > 0 {
				orig.WriteHeader(status)
			}
			_, _ = orig.Write(body)
			return
		}

		orig.Header().Set("Content-Encoding", enc)
		orig.Header().Del("Content-Length")
		weakenETag(orig.Header())
		orig.WriteHeader(status)
		_, _ = orig.Write(compressed)
	}
}

func compressBytes(encoding string, raw []byte, brotliLevel int) ([]byte, error) {
	var out bytes.Buffer
	switch encoding {
	case "br":
		if brotliLevel == defaultBrotliLevel {
			bw := brotliLevel4Pool.Get().(*brotli.Writer)
			bw.Reset(&out)
			if _, err := bw.Write(raw); err != nil {
				_ = bw.Close()
				return nil, err
			}
			if err := bw.Close(); err != nil {
				return nil, err
			}
			bw.Reset(io.Discard)
			brotliLevel4Pool.Put(bw)
			return out.Bytes(), nil
		}
		bw := brotli.NewWriterLevel(&out, brotliLevel)
		if _, err := bw.Write(raw); err != nil {
			_ = bw.Close()
			return nil, err
		}
		if err := bw.Close(); err != nil {
			return nil, err
		}
		return out.Bytes(), nil
	case "gzip":
		gw := gzipWriterPool.Get().(*gzip.Writer)
		gw.Reset(&out)
		if _, err := gw.Write(raw); err != nil {
			_ = gw.Close()
			return nil, err
		}
		if err := gw.Close(); err != nil {
			return nil, err
		}
		gw.Reset(io.Discard)
		gzipWriterPool.Put(gw)
		return out.Bytes(), nil
	default:
		return raw, nil
	}
}

func isEventStreamContentType(ct string) bool {
	return strings.Contains(strings.ToLower(ct), "text/event-stream")
}

func bodyAllowedForStatus(status int) bool {
	switch {
	case status >= 100 && status <= 199:
		return false
	case status == http.StatusNoContent:
		return false
	case status == http.StatusNotModified:
		return false
	default:
		return true
	}
}

func weakenETag(h http.Header) {
	if etag := h.Get("ETag"); etag != "" && !strings.HasPrefix(etag, "W/") {
		h.Set("ETag", "W/"+etag)
	}
}

func appendVary(h http.Header, value string) {
	existing := make([]string, 0, 4)
	seen := make(map[string]struct{}, 4)
	for _, raw := range h.Values("Vary") {
		for _, tok := range strings.Split(raw, ",") {
			trimmed := strings.TrimSpace(tok)
			if trimmed == "" {
				continue
			}
			lower := strings.ToLower(trimmed)
			if _, ok := seen[lower]; !ok {
				seen[lower] = struct{}{}
				existing = append(existing, trimmed)
			}
		}
	}
	if _, ok := seen[strings.ToLower(value)]; !ok {
		existing = append(existing, value)
	}
	h.Set("Vary", strings.Join(existing, ", "))
}

func negotiateEncoding(acceptEncoding string) string {
	if strings.TrimSpace(acceptEncoding) == "" {
		return ""
	}
	brQ := -1.0
	gzipQ := -1.0
	identityQ := -1.0
	wildcardQ := -1.0

	for _, part := range strings.Split(acceptEncoding, ",") {
		part = strings.TrimSpace(part)
		if part == "" {
			continue
		}
		segments := strings.Split(part, ";")
		coding := strings.ToLower(strings.TrimSpace(segments[0]))
		q := 1.0
		valid := true
		for _, param := range segments[1:] {
			k, v, found := strings.Cut(param, "=")
			if !found {
				continue
			}
			if strings.EqualFold(strings.TrimSpace(k), "q") {
				parsed, err := strconv.ParseFloat(strings.TrimSpace(v), 64)
				if err != nil || math.IsNaN(parsed) || math.IsInf(parsed, 0) || parsed < 0 || parsed > 1 {
					valid = false
					break
				}
				q = parsed
			}
		}
		if !valid {
			continue
		}
		switch coding {
		case "br":
			if q > brQ {
				brQ = q
			}
		case "gzip":
			if q > gzipQ {
				gzipQ = q
			}
		case "identity":
			if q > identityQ {
				identityQ = q
			}
		case "*":
			if q > wildcardQ {
				wildcardQ = q
			}
		}
	}

	if brQ < 0 && wildcardQ >= 0 {
		brQ = wildcardQ
	}
	if gzipQ < 0 && wildcardQ >= 0 {
		gzipQ = wildcardQ
	}

	bestEnc := ""
	bestQ := 0.0
	if brQ > bestQ {
		bestEnc = "br"
		bestQ = brQ
	}
	if gzipQ > bestQ {
		bestEnc = "gzip"
		bestQ = gzipQ
	}
	if bestEnc == "" {
		return ""
	}
	if identityQ > bestQ {
		return ""
	}
	return bestEnc
}
