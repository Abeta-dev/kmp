package handler

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/abeta-dev/go-libs/apperror"
	"github.com/abeta-dev/kmp/bff/internal/usecase"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/server"
	"github.com/gin-gonic/gin"
)

type builtInStreamProvider struct{}

func (builtInStreamProvider) Stream(_ context.Context, streamID string, lastEventID string) ([]server.StreamEvent, bool, error) {
	id := strings.TrimSpace(streamID)
	if id == "" {
		return nil, false, apperror.BadRequest("stream_id must not be empty")
	}
	switch id {
	case "ai-assistant", "order-live", "sync-status":
	default:
		return nil, false, nil
	}

	patches, err := usecase.BuildStreamPatches(id)
	if err != nil {
		return nil, false, err
	}

	lastID := parseLastEventID(lastEventID)
	if lastID >= len(patches) {
		return []server.StreamEvent{}, true, nil
	}

	events := make([]server.StreamEvent, 0, len(patches)-lastID)
	for i := lastID; i < len(patches); i++ {
		events = append(events, server.StreamEvent{
			ID:    strconv.Itoa(i + 1),
			Event: "delta",
			Patch: patches[i],
		})
	}
	return events, true, nil
}

func (h *bffHandler) streamDeltas(c *gin.Context) {
	streamID := strings.TrimSpace(c.Param("streamId"))
	if streamID == "" {
		respondDomainError(c, apperror.BadRequest("stream_id must not be empty"))
		return
	}

	provider := h.streamProvider
	if provider == nil {
		provider = builtInStreamProvider{}
	}

	ctx := c.Request.Context()
	lastEventID := strings.TrimSpace(c.GetHeader("Last-Event-ID"))
	events, found, err := provider.Stream(ctx, streamID, lastEventID)
	if err != nil {
		respondDomainError(c, err)
		return
	}
	if !found {
		respondDomainError(c, apperror.NotFound("stream not found: "+streamID))
		return
	}
	if len(events) == 0 {
		c.Status(http.StatusNoContent)
		return
	}

	// Clear write deadline so long-lived SSE streams are not terminated by http.Server.WriteTimeout.
	rc := http.NewResponseController(c.Writer)
	_ = rc.SetWriteDeadline(time.Time{})

	c.Header("Content-Type", "text/event-stream; charset=utf-8")
	c.Header("Cache-Control", "no-cache, no-transform")
	c.Header("X-Accel-Buffering", "no")
	if c.Request.ProtoMajor == 1 {
		c.Header("Connection", "keep-alive")
	}
	c.Status(http.StatusOK)

	if h.isStreamCancelled(ctx) {
		c.Writer.Flush()
		return
	}
	if _, writeErr := ioWriteString(c.Writer, "retry: 2000\n\n"); writeErr != nil {
		h.log.Warn("failed to write SSE retry directive", slog.String("stream_id", streamID), slog.Any("error", writeErr))
		return
	}
	c.Writer.Flush()

	for i, ev := range events {
		if h.isStreamCancelled(ctx) {
			return
		}
		evID := strings.TrimSpace(ev.ID)
		if evID == "" {
			evID = strconv.Itoa(i + 1)
		}
		evName := strings.TrimSpace(ev.Event)
		if evName == "" {
			evName = "delta"
		}
		payload, marshalErr := json.Marshal(ev.Patch)
		if marshalErr != nil {
			h.log.Error("failed to marshal SSE delta patch", slog.String("stream_id", streamID), slog.Any("error", marshalErr))
			return
		}
		if _, writeErr := fmt.Fprintf(c.Writer, "id: %s\nevent: %s\ndata: %s\n\n", evID, evName, payload); writeErr != nil {
			h.log.Warn("failed to write SSE frame", slog.String("stream_id", streamID), slog.String("event_id", evID), slog.Any("error", writeErr))
			return
		}
		c.Writer.Flush()
	}
}

func (h *bffHandler) isStreamCancelled(reqCtx context.Context) bool {
	if reqCtx.Err() != nil {
		return true
	}
	if h.shutdownCtx != nil && h.shutdownCtx.Err() != nil {
		return true
	}
	return false
}

func ioWriteString(w http.ResponseWriter, s string) (int, error) {
	return w.Write([]byte(s))
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
