package server

import (
	"bytes"
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"
)

const (
	// DefaultCacheControl is the safe default Cache-Control directive for SDUI responses.
	DefaultCacheControl = "private, no-cache"
	// DefaultMaxBufferBytes caps middleware response buffering at 8 MiB before streaming through.
	DefaultMaxBufferBytes = 8 << 20
)

var defaultVolatileJSONKeys = []string{
	"generated_at_epoch_ms",
	"request_id",
}

// ETagConfig configures HTTP ETag validation, Cache-Control safety, and app version gating.
type ETagConfig struct {
	// CacheControl overrides the default "private, no-cache" for unauthenticated responses.
	// Authenticated requests (RequireAuth=true or Authorization/Cookie present) always
	// downgrade to "private, no-cache" + Vary: Authorization unless the handler set no-store/private.
	CacheControl string
	// RequireAuth forces private caching and Vary: Authorization on all responses.
	RequireAuth bool
	// AppVersion emits X-App-Version and forces 200 OK when X-Client-App-Version mismatches.
	AppVersion string
	// MaxBufferBytes caps response buffering (defaults to 8 MiB; larger responses stream unbuffered).
	MaxBufferBytes int
	// VolatileJSONKeys lists JSON keys stripped before hashing when computing fallback ETags.
	// Nil defaults to ["generated_at_epoch_ms", "request_id"]; pass an empty non-nil slice to disable stripping.
	VolatileJSONKeys []string
}

// ETagMiddleware validates GET and HEAD responses with strong/weak ETag matching,
// honors handler-stamped ETag headers, enforces safe Cache-Control defaults,
// and bypasses 304 Not Modified when X-Client-App-Version mismatches X-App-Version.
func ETagMiddleware(cfg ETagConfig) gin.HandlerFunc {
	maxBuf := cfg.MaxBufferBytes
	if maxBuf <= 0 {
		maxBuf = DefaultMaxBufferBytes
	}
	appVersion := strings.TrimSpace(cfg.AppVersion)
	configuredCC := strings.TrimSpace(cfg.CacheControl)

	volatileKeys := cfg.VolatileJSONKeys
	if volatileKeys == nil {
		volatileKeys = defaultVolatileJSONKeys
	}
	volatileSet := make(map[string]struct{}, len(volatileKeys))
	for _, k := range volatileKeys {
		if trimmed := strings.TrimSpace(k); trimmed != "" {
			volatileSet[trimmed] = struct{}{}
		}
	}

	return func(c *gin.Context) {
		if c.Request.Method != http.MethodGet && c.Request.Method != http.MethodHead {
			c.Next()
			return
		}

		orig := c.Writer
		preCC := orig.Header().Get("Cache-Control")
		if preCC != "" {
			orig.Header().Del("Cache-Control")
		}
		bw := newBufferedResponseWriter(orig, maxBuf)
		c.Writer = bw
		completed := false
		defer func() {
			if !completed {
				c.Writer = orig
				if preCC != "" && orig.Header().Get("Cache-Control") == "" {
					orig.Header().Set("Cache-Control", preCC)
				}
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
		h := orig.Header()
		presetETag := strings.TrimSpace(h.Get("ETag"))

		if status < 200 || status >= 300 || (len(body) == 0 && presetETag == "") {
			if preCC != "" && h.Get("Cache-Control") == "" {
				h.Set("Cache-Control", preCC)
			}
			if bw.wroteHeader || len(body) > 0 {
				orig.WriteHeader(status)
			}
			if c.Request.Method != http.MethodHead && len(body) > 0 && bodyAllowedForStatus(status) {
				_, _ = orig.Write(body)
			}
			return
		}

		etag := presetETag
		if etag == "" {
			hashInput := canonicalBodyForETag(body, h.Get("Content-Type"), volatileSet)
			sum := sha256.Sum256(hashInput)
			etag = fmt.Sprintf(`"%x"`, sum)
		}
		h.Set("ETag", etag)

		if appVersion != "" && strings.TrimSpace(h.Get("X-App-Version")) == "" {
			h.Set("X-App-Version", appVersion)
		}
		serverAppVersion := strings.TrimSpace(h.Get("X-App-Version"))

		applyCacheControlHeaders(c, h, cfg.RequireAuth, configuredCC)

		clientAppVersion := strings.TrimSpace(c.GetHeader("X-Client-App-Version"))
		versionMismatch := clientAppVersion != "" && serverAppVersion != "" && clientAppVersion != serverAppVersion

		if !versionMismatch && ifNoneMatchMatches(c.GetHeader("If-None-Match"), etag) {
			h.Del("Content-Length")
			h.Del("Content-Type")
			orig.WriteHeader(http.StatusNotModified)
			return
		}

		if c.Request.Method == http.MethodHead {
			if len(body) > 0 && h.Get("Content-Length") == "" {
				h.Set("Content-Length", strconv.Itoa(len(body)))
			}
			orig.WriteHeader(status)
			return
		}

		orig.WriteHeader(status)
		if len(body) > 0 {
			_, _ = orig.Write(body)
		}
	}
}

func applyCacheControlHeaders(c *gin.Context, h http.Header, requireAuth bool, configuredCC string) {
	isAuth := requireAuth || c.GetHeader("Authorization") != "" || c.GetHeader("Cookie") != ""
	existing := strings.TrimSpace(h.Get("Cache-Control"))
	lower := strings.ToLower(existing)
	hasNoStore := strings.Contains(lower, "no-store")
	hasPrivate := strings.Contains(lower, "private")

	if isAuth {
		appendVary(h, "Authorization")
		if !hasNoStore && !hasPrivate {
			h.Set("Cache-Control", DefaultCacheControl)
		}
	} else {
		switch {
		case hasNoStore || hasPrivate:
			// Preserve handler-set no-store or private directives.
		case existing != "":
			// Preserve explicit handler-set Cache-Control (e.g., public, no-cache for token catalog).
		case configuredCC != "":
			h.Set("Cache-Control", configuredCC)
		default:
			h.Set("Cache-Control", DefaultCacheControl)
		}
	}

	if !hasNoStore {
		h.Del("Pragma")
	}
}

func canonicalBodyForETag(body []byte, contentType string, volatileSet map[string]struct{}) []byte {
	if len(volatileSet) == 0 || !strings.Contains(strings.ToLower(contentType), "application/json") {
		return body
	}
	var decoded any
	dec := json.NewDecoder(bytes.NewReader(body))
	dec.UseNumber()
	if err := dec.Decode(&decoded); err != nil {
		return body
	}
	cleaned := stripVolatileKeys(decoded, volatileSet)
	reencoded, err := json.Marshal(cleaned)
	if err != nil {
		return body
	}
	return reencoded
}

func stripVolatileKeys(v any, volatileSet map[string]struct{}) any {
	switch typed := v.(type) {
	case map[string]any:
		out := make(map[string]any, len(typed))
		for k, val := range typed {
			if _, drop := volatileSet[k]; drop {
				continue
			}
			out[k] = stripVolatileKeys(val, volatileSet)
		}
		return out
	case []any:
		out := make([]any, len(typed))
		for i, item := range typed {
			out[i] = stripVolatileKeys(item, volatileSet)
		}
		return out
	default:
		return v
	}
}

func ifNoneMatchMatches(headerVal, currentETag string) bool {
	trimmed := strings.TrimSpace(headerVal)
	if trimmed == "" {
		return false
	}
	if trimmed == "*" {
		return true
	}

	normCurrent := strings.TrimPrefix(currentETag, "W/")
	for _, part := range strings.Split(trimmed, ",") {
		cand := strings.TrimSpace(part)
		if cand == "*" {
			return true
		}
		if cand == "" {
			continue
		}
		if strings.TrimPrefix(cand, "W/") == normCurrent {
			return true
		}
	}
	return false
}
