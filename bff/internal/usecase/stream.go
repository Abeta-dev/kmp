package usecase

import (
	"strings"

	"github.com/abeta-dev/go-libs/apperror"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
)

// BuildStreamPatches produces a deterministic sequence of SnippetDeltaPatch frames
// for streamID demonstrating append_text, patch_snippet_data, and terminal done: true.
func BuildStreamPatches(streamID string) ([]snippets.SnippetDeltaPatch, error) {
	id := strings.TrimSpace(streamID)
	if id == "" {
		return nil, apperror.BadRequest("stream_id must not be empty")
	}

	switch id {
	case "order-live":
		return []snippets.SnippetDeltaPatch{
			snippets.NewAppendTextPatch(
				"stream_response_card",
				"description.text",
				"Courier Aarav K. picked up your chilled basket. ",
			),
			snippets.NewAppendTextPatch(
				"stream_response_card",
				"description.text",
				"Arriving at your doorstep in 4 minutes.",
			),
			snippets.NewPatchSnippetData("stream_status_metric", map[string]any{
				"value": map[string]any{"text": "4 mins"},
				"delta": map[string]any{"label": "LIVE"},
			}),
			snippets.NewUpdatePageMetaPatch("Order #AF-4092 · Arriving Soon", "v1-order-live").WithDone(true),
		}, nil
	case "ai-assistant":
		return []snippets.SnippetDeltaPatch{
			snippets.NewAppendTextPatch(
				"stream_response_card",
				"description.text",
				"Streaming SDUI deltas let the BFF update ",
			),
			snippets.NewAppendTextPatch(
				"stream_response_card",
				"description.text",
				"any snippet incrementally without full-page reloads.",
			),
			snippets.NewPatchSnippetData("stream_status_metric", map[string]any{
				"value": map[string]any{"text": "100%"},
				"delta": map[string]any{"label": "SYNCED"},
			}),
			snippets.NewUpdatePageMetaPatch("Abeta Live Stream · Complete", "v1-ai-assistant").WithDone(true),
		}, nil
	default:
		return []snippets.SnippetDeltaPatch{
			snippets.NewAppendTextPatch(
				"stream_response_card",
				"description.text",
				"Connected to stream "+id+". ",
			),
			snippets.NewAppendTextPatch(
				"stream_response_card",
				"description.text",
				"Delta patches applied cleanly.",
			),
			snippets.NewPatchSnippetData("stream_status_metric", map[string]any{
				"value": map[string]any{"text": "OK"},
				"delta": map[string]any{"label": "STREAMED"},
			}),
			snippets.NewUpdatePageMetaPatch("Stream "+id+" Complete", "v1-stream-"+id).WithDone(true),
		}, nil
	}
}
