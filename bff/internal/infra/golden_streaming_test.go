package infra_test

import (
	"encoding/json"
	"strings"
	"testing"

	"github.com/abeta-dev/kmp/bff/pkg/sdui/actions"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

func canonicalStreamingAndVersionedScreen(t *testing.T) snippets.PageResponse {
	t.Helper()
	ptrAction := func(a actions.ActionTrigger) *actions.ActionTrigger { return &a }

	cardLayout := atoms.LayoutConfig{
		Padding:      atoms.UniformSpacing(theming.SpacingLG.Dp()),
		Margin:       atoms.SymmetricSpacing(theming.SpacingLG.Dp(), theming.SpacingXS.Dp()),
		BgColor:      atoms.ColorRef(theming.ColorSurfacePrimary),
		CornerRadius: atoms.UniformRadius(theming.RadiusMD.Dp()),
		Border: &atoms.BorderSpec{
			WidthDp: 1,
			Color:   *atoms.ColorRef(theming.ColorBorderSubtle),
		},
		FillMaxWidth: atoms.BoolPtr(true),
	}

	toolbarTitle := atoms.NewText("Abeta Live Stream", theming.TypographyTitleLarge, theming.ColorTextPrimary)
	toolbarSub := atoms.NewText("Version-gated manifest & SSE delta streaming", theming.TypographyBodySmall, theming.ColorTextSecondary)
	backIcon := atoms.NewIcon("arrow_back", 22, theming.ColorTextPrimary)
	backIcon.ContentDescription = "Navigate back"
	backIcon.OnClick = ptrAction(actions.NewBackPressAction("back_from_streaming_demo", actions.BackPressPayload{Steps: 1}))

	header := snippets.NewHeaderSnippet(
		"stream_demo_header",
		atoms.NewText("Live AI Assistant Stream", theming.TypographyHeadlineSmall, theming.ColorTextPrimary),
	).
		WithSubtitle(atoms.NewText("Incremental SSE delta patches over versioned SDUI", theming.TypographyBodySmall, theming.ColorTextSecondary)).
		WithLeadingIcon(atoms.NewIcon("sparkles", 20, theming.ColorBrandPrimary)).
		WithTrailingTag(atoms.NewTag("SSE", theming.ColorInfoContainer)).
		WithLayout(cardLayout).
		WithTracking("snippet_impression", map[string]any{
			"snippet_id": "stream_demo_header",
			"surface":    "streaming-versioned-demo",
		}).
		Build()

	responseCard := snippets.NewCardSnippet(
		"stream_response_card",
		atoms.NewText("Streaming Output", theming.TypographyTitleMedium, theming.ColorTextPrimary),
	).
		WithSubtitle(atoms.NewText("Updated via append_text delta patches", theming.TypographyBodySmall, theming.ColorTextSecondary)).
		WithDescription(atoms.NewText("Connecting to live stream... ", theming.TypographyBodyMedium, theming.ColorTextPrimary)).
		WithBadge(atoms.NewTag("LIVE", theming.ColorSuccessContainer)).
		WithLayout(cardLayout).
		WithTracking("snippet_impression", map[string]any{
			"snippet_id": "stream_response_card",
			"surface":    "streaming-versioned-demo",
		}).
		Build()

	statusMetric := snippets.NewMetricCardSnippet(
		"stream_status_metric",
		atoms.NewText("Stream Progress", theming.TypographyLabelMedium, theming.ColorTextSecondary),
		atoms.NewText("0%", theming.TypographyHeadlineSmall, theming.ColorTextPrimary),
	).
		WithDelta(atoms.NewTag("CONNECTING", theming.ColorInfoContainer)).
		WithIcon(atoms.NewIcon("bolt", 20, theming.ColorBrandPrimary)).
		WithLayout(cardLayout).
		WithTracking("snippet_impression", map[string]any{
			"snippet_id": "stream_status_metric",
			"surface":    "streaming-versioned-demo",
		}).
		Build()

	page := snippets.NewPage("streaming-versioned-demo", "Abeta Live Stream").
		WithToolbar(snippets.ToolbarSpec{
			Title:          &toolbarTitle,
			Subtitle:       &toolbarSub,
			NavigationIcon: &backIcon,
			BgColor:        atoms.ColorRef(theming.ColorSurfacePrimary),
		}).
		WithAppVersion("0.7.0").
		WithStreamSpec(snippets.SseStreamSpec{
			Path:        "/api/v1/streams/ai-assistant",
			AutoConnect: true,
			Headers:     map[string]string{"X-Stream-Mode": "delta"},
			RetryMs:     snippets.Int64Ptr(3000),
		}).
		AddSnippets(header, responseCard, statusMetric).
		WithTracking("page_loaded", map[string]any{
			"page_id": "streaming-versioned-demo",
			"version": "v1",
		}).
		Build()

	snippets.EnsureComponentIDs(&page)
	etag, err := snippets.ComputePageETag(page)
	if err != nil {
		t.Fatalf("ComputePageETag failed: %v", err)
	}
	page.ETag = etag
	page.PageVersion = snippets.DerivePageVersion(etag)
	return page
}

func TestGoldenStreamingAndVersionedScreen_RoundTrip(t *testing.T) {
	page := canonicalStreamingAndVersionedScreen(t)
	rawBytes := writeOrCompareGolden(t, "streaming_and_versioned_screen.json", page)
	assertStringOnlyTrackingProperties(t, rawBytes)

	var decoded snippets.PageResponse
	if err := json.Unmarshal(rawBytes, &decoded); err != nil {
		t.Fatalf("round-trip unmarshal streaming_and_versioned_screen.json: %v", err)
	}
	if decoded.PageID != "streaming-versioned-demo" || decoded.AppVersion != "0.7.0" {
		t.Fatalf("unexpected decoded streaming page identity: %+v", decoded)
	}
	if !strings.HasPrefix(decoded.PageVersion, "v1-") || !strings.HasPrefix(decoded.ETag, `"`) || !strings.HasSuffix(decoded.ETag, `"`) {
		t.Fatalf("expected deterministic page_version and quoted etag, got version=%q etag=%q", decoded.PageVersion, decoded.ETag)
	}
	if decoded.StreamSpec == nil || decoded.StreamSpec.Path != "/api/v1/streams/ai-assistant" || !decoded.StreamSpec.AutoConnect || decoded.StreamSpec.Headers["X-Stream-Mode"] != "delta" || decoded.StreamSpec.RetryMs == nil || *decoded.StreamSpec.RetryMs != 3000 {
		t.Fatalf("unexpected decoded stream_spec: %+v", decoded.StreamSpec)
	}
	if len(decoded.Snippets) != 3 {
		t.Fatalf("expected 3 snippets, got %d", len(decoded.Snippets))
	}

	recomputedETag, err := snippets.ComputePageETag(decoded)
	if err != nil || recomputedETag != decoded.ETag {
		t.Fatalf("expected idempotent ComputePageETag=%q, got %q (err=%v)", decoded.ETag, recomputedETag, err)
	}
}

func TestStreamingAndManifestContracts_RoundTrip(t *testing.T) {
	updatedAt := snippets.Int64Ptr(1_764_000_000_000)
	generatedAt := snippets.Int64Ptr(1_764_000_005_000)
	manifest := snippets.ScreenManifestResponse{
		AppVersion: "0.7.0",
		Screens: map[string]snippets.ScreenVersionMeta{
			"home": {
				Version:          "v1-112233445566",
				ETag:             `"1122334455667788"`,
				UpdatedAtEpochMs: updatedAt,
			},
			"streaming-versioned-demo": {
				Version: "v1-aabbccddeeff",
				ETag:    `"aabbccddeeff0011"`,
			},
		},
		GeneratedAtEpochMs: generatedAt,
	}

	rawManifest, err := json.Marshal(manifest)
	if err != nil {
		t.Fatalf("marshal manifest: %v", err)
	}
	var decodedManifest snippets.ScreenManifestResponse
	if err := json.Unmarshal(rawManifest, &decodedManifest); err != nil {
		t.Fatalf("unmarshal manifest: %v", err)
	}
	if decodedManifest.AppVersion != "0.7.0" || len(decodedManifest.Screens) != 2 || decodedManifest.GeneratedAtEpochMs == nil || *decodedManifest.GeneratedAtEpochMs != *generatedAt {
		t.Fatalf("unexpected decoded manifest: %+v", decodedManifest)
	}
	if decodedManifest.Screens["home"].UpdatedAtEpochMs == nil || *decodedManifest.Screens["home"].UpdatedAtEpochMs != *updatedAt {
		t.Fatalf("unexpected home ScreenVersionMeta: %+v", decodedManifest.Screens["home"])
	}

	idx := 1
	inserted := snippets.NewHeaderSnippet("stream_inserted", atoms.NewText("New Section", theming.TypographyTitleSmall, theming.ColorTextPrimary)).Build()
	patches := []snippets.SnippetDeltaPatch{
		snippets.NewAppendTextPatch("stream_response_card", "description.text", "Hello "),
		snippets.NewPatchSnippetData("stream_status_metric", map[string]any{"value": map[string]any{"text": "50%"}}),
		snippets.NewReplaceSnippetPatch("stream_response_card", inserted),
		snippets.NewInsertSnippetPatch(inserted, &idx),
		snippets.NewRemoveSnippetPatch("stream_inserted"),
		snippets.NewUpdatePageMetaPatch("Done", "v1-final").WithDone(true),
	}

	rawPatches, err := json.Marshal(patches)
	if err != nil {
		t.Fatalf("marshal patches: %v", err)
	}
	var decodedPatches []snippets.SnippetDeltaPatch
	if err := json.Unmarshal(rawPatches, &decodedPatches); err != nil {
		t.Fatalf("unmarshal patches: %v", err)
	}
	if len(decodedPatches) != len(patches) {
		t.Fatalf("expected %d patches, got %d", len(patches), len(decodedPatches))
	}
	if decodedPatches[0].Op != snippets.DeltaOpAppendText || decodedPatches[0].TextChunk != "Hello " {
		t.Fatalf("unexpected append_text patch: %+v", decodedPatches[0])
	}
	if decodedPatches[3].Op != snippets.DeltaOpInsertSnippet || decodedPatches[3].InsertIndex == nil || *decodedPatches[3].InsertIndex != 1 {
		t.Fatalf("unexpected insert_snippet patch: %+v", decodedPatches[3])
	}
	if decodedPatches[5].Op != snippets.DeltaOpUpdatePageMeta || !decodedPatches[5].Done || decodedPatches[5].PageVersion != "v1-final" {
		t.Fatalf("unexpected update_page_meta patch: %+v", decodedPatches[5])
	}
}
