package handler

import (
	"github.com/abeta-dev/kmp/bff/pkg/sdui/server"
	"github.com/gin-gonic/gin"
)

// CompressionMiddleware delegates to pkg/sdui/server.CompressionMiddleware using pooled
// Brotli (level 4) and gzip writers with SSE & metrics bypasses and an 8 MiB buffer cap.
func CompressionMiddleware() gin.HandlerFunc {
	return server.CompressionMiddleware()
}
