package server

import (
	"bytes"
	"errors"
	"net/http"
	"strings"
	"time"

	"github.com/abeta-dev/go-libs/ginmw"
	"github.com/gin-gonic/gin"
	"github.com/golang-jwt/jwt/v5"
)

// JWTAuthMiddleware returns a per-router HS256 JWT authentication middleware that
// closes over an immutable defensive copy of secret (never mutating global ginmw state).
// Fails closed with a standard ginmw.Envelope 401 when secret is empty or token is invalid.
func JWTAuthMiddleware(secret []byte) gin.HandlerFunc {
	var key []byte
	if len(bytes.TrimSpace(secret)) > 0 {
		key = bytes.Clone(secret)
	}

	return func(c *gin.Context) {
		if len(key) == 0 {
			ginmw.RespondError(c, http.StatusUnauthorized, "UNAUTHORIZED", "authentication is not configured")
			c.Abort()
			return
		}

		tokenStr := extractBearerToken(c.GetHeader("Authorization"))
		if tokenStr == "" {
			ginmw.RespondError(c, http.StatusUnauthorized, "UNAUTHORIZED", "authorization bearer token required")
			c.Abort()
			return
		}

		claims := &ginmw.Claims{}
		token, err := jwt.ParseWithClaims(
			tokenStr,
			claims,
			func(_ *jwt.Token) (any, error) {
				return key, nil
			},
			jwt.WithValidMethods([]string{jwt.SigningMethodHS256.Alg()}),
		)
		if err != nil || !token.Valid {
			ginmw.RespondError(c, http.StatusUnauthorized, "UNAUTHORIZED", "invalid or expired token")
			c.Abort()
			return
		}

		// Store under ginmw's canonical context key so ginmw.GetClaims(c) works seamlessly.
		c.Set("claims", claims)
		c.Next()
	}
}

// GenerateHS256Token signs claims with secret using HS256 for ttl without mutating global state.
func GenerateHS256Token(secret []byte, claims *ginmw.Claims, ttl time.Duration) (string, error) {
	if len(bytes.TrimSpace(secret)) == 0 {
		return "", errors.New("server: jwt secret must not be empty")
	}
	if claims == nil {
		return "", errors.New("server: claims must not be nil")
	}
	if ttl <= 0 {
		return "", errors.New("server: token ttl must be positive")
	}

	now := time.Now()
	cp := *claims
	cp.IssuedAt = jwt.NewNumericDate(now)
	cp.ExpiresAt = jwt.NewNumericDate(now.Add(ttl))

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, &cp)
	return token.SignedString(bytes.Clone(secret))
}

func extractBearerToken(authHeader string) string {
	scheme, token, found := strings.Cut(strings.TrimSpace(authHeader), " ")
	if !found || !strings.EqualFold(scheme, "bearer") {
		return ""
	}
	return strings.TrimSpace(token)
}
