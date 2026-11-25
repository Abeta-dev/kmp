// Package server provides reusable, production-hardened HTTP/Gin middleware,
// upstream SDUI fetchers, per-router JWT verification, and SSE streaming ports.
package server

import (
	"net/http"
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
)

var (
	defaultCORSAllowedMethods = []string{
		http.MethodGet,
		http.MethodPost,
		http.MethodPut,
		http.MethodPatch,
		http.MethodDelete,
		http.MethodOptions,
		http.MethodHead,
	}
	defaultCORSAllowedHeaders = []string{
		"Content-Type",
		"Authorization",
		"If-None-Match",
		"Last-Event-ID",
		"Cache-Control",
		"X-CSRF-Token",
		"X-Request-ID",
		"X-Client-App-Version",
	}
	defaultCORSExposedHeaders = []string{
		"ETag",
		"Cache-Control",
		"Retry-After",
		"RateLimit-Remaining",
		"X-Request-ID",
		"X-App-Version",
		"X-Page-Version",
	}
)

// CORSConfig configures fail-closed Cross-Origin Resource Sharing behavior.
type CORSConfig struct {
	// AllowedOrigins lists exact origins (e.g. "https://app.abeta.dev"),
	// wildcard subdomains (e.g. "*.abeta.dev" or "https://*.abeta.dev"), or "*".
	// When empty, CORS fails closed and emits no Access-Control-Allow-Origin header.
	AllowedOrigins   []string
	AllowCredentials bool
	AllowedMethods   []string
	AllowedHeaders   []string
	ExposedHeaders   []string
	MaxAge           time.Duration
}

// CORSMiddleware enforces fail-closed CORS validation, always sets Vary: Origin,
// supports exact origins, *.example.com subdomains, and explicit "*" (never
// combining wildcard "*" with Access-Control-Allow-Credentials: true).
func CORSMiddleware(cfg CORSConfig) gin.HandlerFunc {
	patterns := normalizeOriginPatterns(cfg.AllowedOrigins)
	methodsHeader := strings.Join(mergeHeaderValues(defaultCORSAllowedMethods, cfg.AllowedMethods), ", ")
	if len(cfg.AllowedMethods) > 0 {
		methodsHeader = strings.Join(dedupeStrings(cfg.AllowedMethods), ", ")
	}
	allowedHeaders := strings.Join(mergeHeaderValues(defaultCORSAllowedHeaders, cfg.AllowedHeaders), ", ")
	exposedHeaders := strings.Join(mergeHeaderValues(defaultCORSExposedHeaders, cfg.ExposedHeaders), ", ")

	maxAge := cfg.MaxAge
	if maxAge <= 0 {
		maxAge = 12 * time.Hour
	}
	maxAgeHeader := strconv.Itoa(int(maxAge.Seconds()))

	return func(c *gin.Context) {
		h := c.Writer.Header()
		appendVary(h, "Origin")

		origin := strings.TrimSpace(c.GetHeader("Origin"))
		isPreflight := c.Request.Method == http.MethodOptions && origin != ""

		if origin == "" || len(patterns) == 0 {
			if isPreflight {
				c.AbortWithStatus(http.StatusForbidden)
				return
			}
			c.Next()
			return
		}

		allowVal, isWildcard, matched := matchCORSOrigin(origin, patterns)
		if !matched {
			if isPreflight {
				c.AbortWithStatus(http.StatusForbidden)
				return
			}
			c.Next()
			return
		}

		h.Set("Access-Control-Allow-Origin", allowVal)
		h.Set("Access-Control-Expose-Headers", exposedHeaders)
		if cfg.AllowCredentials && !isWildcard {
			h.Set("Access-Control-Allow-Credentials", "true")
		}

		if isPreflight {
			appendVary(h, "Access-Control-Request-Method")
			appendVary(h, "Access-Control-Request-Headers")
			h.Set("Access-Control-Allow-Methods", methodsHeader)
			h.Set("Access-Control-Allow-Headers", allowedHeaders)
			h.Set("Access-Control-Max-Age", maxAgeHeader)
			c.AbortWithStatus(http.StatusNoContent)
			return
		}

		c.Next()
	}
}

func normalizeOriginPatterns(raw []string) []string {
	out := make([]string, 0, len(raw))
	for _, entry := range raw {
		trimmed := strings.TrimRight(strings.TrimSpace(entry), "/")
		if trimmed != "" {
			out = append(out, trimmed)
		}
	}
	return out
}

func matchCORSOrigin(origin string, patterns []string) (allowVal string, isWildcard bool, ok bool) {
	cleanOrigin := strings.TrimRight(strings.TrimSpace(origin), "/")
	if cleanOrigin == "" {
		return "", false, false
	}

	parsedOrigin, parseErr := url.Parse(cleanOrigin)
	originScheme := ""
	originHost := ""
	if parseErr == nil {
		originScheme = strings.ToLower(parsedOrigin.Scheme)
		originHost = strings.ToLower(parsedOrigin.Hostname())
	}

	hasWildcard := false
	for _, pat := range patterns {
		if pat == "*" {
			hasWildcard = true
			continue
		}
		if strings.EqualFold(pat, cleanOrigin) {
			return cleanOrigin, false, true
		}
		if matchWildcardSubdomain(pat, originScheme, originHost) {
			return cleanOrigin, false, true
		}
	}

	if hasWildcard {
		return "*", true, true
	}
	return "", false, false
}

func matchWildcardSubdomain(pattern, originScheme, originHost string) bool {
	if originHost == "" || (originScheme != "http" && originScheme != "https") {
		return false
	}

	requiredScheme := ""
	hostPattern := pattern
	if scheme, rest, found := strings.Cut(pattern, "://"); found {
		requiredScheme = strings.ToLower(scheme)
		hostPattern = rest
	}
	if requiredScheme != "" && requiredScheme != originScheme {
		return false
	}
	if !strings.HasPrefix(hostPattern, "*.") {
		return false
	}
	suffix := strings.ToLower(strings.TrimPrefix(hostPattern, "*"))
	// Require a non-empty subdomain label before ".example.com"
	return len(originHost) > len(suffix) && strings.HasSuffix(originHost, suffix)
}

func mergeHeaderValues(base, extras []string) []string {
	combined := make([]string, 0, len(base)+len(extras))
	combined = append(combined, base...)
	combined = append(combined, extras...)
	return dedupeStrings(combined)
}

func dedupeStrings(items []string) []string {
	out := make([]string, 0, len(items))
	seen := make(map[string]struct{}, len(items))
	for _, item := range items {
		trimmed := strings.TrimSpace(item)
		if trimmed == "" {
			continue
		}
		key := strings.ToLower(trimmed)
		if _, ok := seen[key]; ok {
			continue
		}
		seen[key] = struct{}{}
		out = append(out, trimmed)
	}
	return out
}
