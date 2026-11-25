package handler

import (
	"github.com/abeta-dev/kmp/bff/pkg/sdui/server"
	"github.com/gin-gonic/gin"
)

// ETagMiddleware delegates to pkg/sdui/server.ETagMiddleware with safe private, no-cache defaults.
func ETagMiddleware() gin.HandlerFunc {
	return server.ETagMiddleware(server.ETagConfig{})
}
