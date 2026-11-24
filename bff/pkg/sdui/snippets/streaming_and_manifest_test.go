package snippets_test

import (
	"encoding/json"
	"strings"
	"testing"

	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

func TestStreamingAndManifest_WireFormatAndHelpers(t *testing.T) {
	headers := map[string]string{"X-Stream-Mode": "delta"}
	retry := snippets.Int64Ptr(2500)
	page := snippets.NewPage("live-demo", "Live Stream").
		WithAppVersion("0.7.0").
		WithStreamSpec(snippets.SseStreamSpec{
			Path:        "/api/v1/streams/ai-assistant",
			AutoConnect: true,
			Headers:     headers,
			RetryMs:     retry,
		}).
		AddSnippet(snippets.NewHeaderSnippet("hdr_1", atoms.NewText("Live", theming.TypographyTitleLarge, theming.ColorTextPrimary)).Build()).
		Build()

	// Defensive copy check for stream spec headers and retry pointer
	headers["X-Stream-Mode"] = "mutated"
	*retry = 9999
	if page.StreamSpec == nil || page.StreamSpec.Headers["X-Stream-Mode"] != "delta" || *page.StreamSpec.RetryMs != 2500 {
		t.Fatalf("expected defensive copy in WithStreamSpec, got %+v", page.StreamSpec)
	}

	etag1, err := snippets.ComputePageETag(page)
	if err != nil {
		t.Fatalf("ComputePageETag failed: %v", err)
	}
	if !strings.HasPrefix(etag1, `"`) || !strings.HasSuffix(etag1, `"`) || len(etag1) != 66 {
		t.Fatalf("expected quoted 64-hex SHA-256 ETag, got %q", etag1)
	}

	version := snippets.DerivePageVersion(etag1)
	if !strings.HasPrefix(version, "v1-") || len(version) != 15 {
		t.Fatalf("unexpected derived version %q", version)
	}
	if snippets.DerivePageVersion("") != "v1-000000000000" {
		t.Fatalf("unexpected empty fallback version: %q", snippets.DerivePageVersion(""))
	}
	if snippets.DerivePageVersion(`W/"abcdef0123456789"`) != "v1-abcdef012345" {
		t.Fatalf("unexpected weak ETag derived version: %q", snippets.DerivePageVersion(`W/"abcdef0123456789"`))
	}

	// Idempotence: stamping version/appVersion/etag does not change ComputePageETag
	stamped := page
	stamped.PageVersion = version
	stamped.AppVersion = "0.8.0"
	stamped.ETag = etag1
	etag2, err := snippets.ComputePageETag(stamped)
	if err != nil || etag2 != etag1 {
		t.Fatalf("expected idempotent ETag, got %q vs %q (err=%v)", etag2, etag1, err)
	}

	// ComputePageETag error on unmarshalable snippet data
	badPage := snippets.PageResponse{
		PageID:   "bad",
		Snippets: []snippets.SnippetEnvelope{{SnippetID: "s1", Data: make(chan int)}},
	}
	if _, err := snippets.ComputePageETag(badPage); err == nil {
		t.Fatal("expected ComputePageETag error for unmarshalable data")
	}

	// PageBuilder WithPageVersion / WithETag round-trip
	fullPage := snippets.NewPage("ver-page", "Versioned").
		WithPageVersion(version).
		WithAppVersion("0.7.0").
		WithETag(etag1).
		WithStreamSpec(snippets.SseStreamSpec{
			Path:        "/api/v1/streams/live",
			AutoConnect: false,
		}).
		Build()
	rawPage, err := json.Marshal(fullPage)
	if err != nil {
		t.Fatalf("marshal fullPage: %v", err)
	}
	var decodedPage snippets.PageResponse
	if err := json.Unmarshal(rawPage, &decodedPage); err != nil {
		t.Fatalf("unmarshal fullPage: %v", err)
	}
	if decodedPage.PageVersion != version || decodedPage.AppVersion != "0.7.0" || decodedPage.ETag != etag1 || decodedPage.StreamSpec == nil || decodedPage.StreamSpec.AutoConnect {
		t.Fatalf("unexpected round-tripped page: %+v", decodedPage)
	}

	// Manifest round-trip
	ts := snippets.Int64Ptr(1_764_000_000_000)
	manifest := snippets.ScreenManifestResponse{
		AppVersion: "0.7.0",
		Screens: map[string]snippets.ScreenVersionMeta{
			"home": {Version: version, ETag: etag1, UpdatedAtEpochMs: ts},
		},
		GeneratedAtEpochMs: ts,
	}
	rawManifest, err := json.Marshal(manifest)
	if err != nil {
		t.Fatalf("marshal manifest: %v", err)
	}
	var decodedManifest snippets.ScreenManifestResponse
	if err := json.Unmarshal(rawManifest, &decodedManifest); err != nil {
		t.Fatalf("unmarshal manifest: %v", err)
	}
	if decodedManifest.AppVersion != "0.7.0" || decodedManifest.Screens["home"].ETag != etag1 || *decodedManifest.GeneratedAtEpochMs != *ts {
		t.Fatalf("unexpected round-tripped manifest: %+v", decodedManifest)
	}

	// All 6 SnippetDeltaPatch builders + defensive copies + WithDone
	patchMap := map[string]any{"status": "live"}
	pData := snippets.NewPatchSnippetData("metric_1", patchMap)
	patchMap["status"] = "mutated"
	if pData.DataPatch["status"] != "live" {
		t.Fatalf("expected defensive copy in NewPatchSnippetData, got %+v", pData.DataPatch)
	}

	idx := 0
	env := snippets.NewHeaderSnippet("ins_1", atoms.NewText("Inserted", theming.TypographyTitleSmall, theming.ColorTextPrimary)).Build()
	pInsert := snippets.NewInsertSnippetPatch(env, &idx)
	idx = 99
	if pInsert.InsertIndex == nil || *pInsert.InsertIndex != 0 {
		t.Fatalf("expected defensive copy of insertIndex=0, got %+v", pInsert.InsertIndex)
	}

	allPatches := []snippets.SnippetDeltaPatch{
		snippets.NewAppendTextPatch("card_1", "description.text", "chunk-1"),
		pData,
		snippets.NewReplaceSnippetPatch("card_1", env),
		pInsert,
		snippets.NewRemoveSnippetPatch("card_old"),
		snippets.NewUpdatePageMetaPatch("Done Title", version).WithDone(true),
	}
	for _, p := range allPatches {
		raw, err := json.Marshal(p)
		if err != nil {
			t.Fatalf("marshal patch %s: %v", p.Op, err)
		}
		var out snippets.SnippetDeltaPatch
		if err := json.Unmarshal(raw, &out); err != nil {
			t.Fatalf("unmarshal patch %s: %v", p.Op, err)
		}
		if out.Op != p.Op || out.Done != p.Done || out.SnippetID != p.SnippetID {
			t.Fatalf("patch %s round-trip mismatch: got %+v, want %+v", p.Op, out, p)
		}
	}
}
