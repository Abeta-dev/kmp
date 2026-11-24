package handler

import (
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"strings"

	"github.com/abeta-dev/kmp/bff/internal/usecase"
	"github.com/gin-gonic/gin"
)

func (h *bffHandler) streamDeltas(c *gin.Context) {
	patches, err := usecase.BuildStreamPatches(c.Param("streamId"))
	if err != nil {
		respondDomainError(c, err)
		return
	}

	lastEventID := parseLastEventID(c.GetHeader("Last-Event-ID"))

	c.Header("Content-Type", "text/event-stream")
	c.Header("Cache-Control", "no-cache")
	c.Header("Connection", "keep-alive")
	c.Header("X-Accel-Buffering", "no")
	c.Status(http.StatusOK)
	c.Writer.Flush()

	ctx := c.Request.Context()
	for i, patch := range patches {
		eventID := i + 1
		if eventID <= lastEventID {
			continue
		}
		if ctx.Err() != nil {
			return
		}
		payload, marshalErr := json.Marshal(patch)
		if marshalErr != nil {
			return
		}
		if _, writeErr := fmt.Fprintf(c.Writer, "id: %d\nevent: delta\ndata: %s\n\n", eventID, payload); writeErr != nil {
			return
		}
		c.Writer.Flush()
	}
}

func parseLastEventID(raw string) int {
	trimmed := strings.TrimSpace(raw)
	if trimmed == "" {
		return 0
	}
	n, err := strconv.Atoi(trimmed)
	if err != nil || n < 0 {
		return 0
	}
	return n
}
