package handler_test

import (
	"bufio"
	"context"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/abeta-dev/kmp/bff/internal/handler"
	"github.com/abeta-dev/kmp/bff/internal/usecase"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/server"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
)

type sseFrame struct {
	ID    string
	Event string
	Data  string
}

func parseSSEFrames(t *testing.T, body string) []sseFrame {
	t.Helper()
	var frames []sseFrame
	var cur sseFrame
	hasFields := false

	scanner := bufio.NewScanner(strings.NewReader(body))
	for scanner.Scan() {
		line := scanner.Text()
		if line == "" {
			if hasFields {
				frames = append(frames, cur)
				cur = sseFrame{}
				hasFields = false
			}
			continue
		}
		switch {
		case strings.HasPrefix(line, "id: "):
			cur.ID = strings.TrimPrefix(line, "id: ")
			hasFields = true
		case strings.HasPrefix(line, "event: "):
			cur.Event = strings.TrimPrefix(line, "event: ")
			hasFields = true
		case strings.HasPrefix(line, "data: "):
			cur.Data = strings.TrimPrefix(line, "data: ")
			hasFields = true
		}
	}
	if hasFields {
		frames = append(frames, cur)
	}
	return frames
}

func TestScreenManifestEndpoint_ETagAnd304AndScreenParity(t *testing.T) {
	r := buildRouterWithOptions(func(cfg *handler.RouterConfig) {
		cfg.EnableCompression = true
		cfg.EnableETag = true
		cfg.AppVersion = "0.7.0"
	})

	// 1. Both GET /api/v1/manifest and GET /api/v1/screens/manifest return 200 OK with deterministic ETag & sdui_schema_version
	w := httptest.NewRecorder()
	r.ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/api/v1/screens/manifest", nil))
	if w.Code != http.StatusOK {
		t.Fatalf("GET /api/v1/screens/manifest status = %d, body = %s", w.Code, w.Body.String())
	}
	etag := w.Header().Get("ETag")
	if !strings.HasPrefix(etag, `"`) || !strings.HasSuffix(etag, `"`) {
		t.Fatalf("expected strong quoted ETag on manifest, got %q", etag)
	}
	if w.Header().Get("Cache-Control") != server.DefaultCacheControl {
		t.Fatalf("expected Cache-Control %q, got %q", server.DefaultCacheControl, w.Header().Get("Cache-Control"))
	}
	if w.Header().Get("X-App-Version") != "0.7.0" {
		t.Fatalf("expected X-App-Version=0.7.0 on manifest, got %q", w.Header().Get("X-App-Version"))
	}

	wAlias := httptest.NewRecorder()
	r.ServeHTTP(wAlias, httptest.NewRequest(http.MethodGet, "/api/v1/manifest", nil))
	if wAlias.Code != http.StatusOK || wAlias.Header().Get("ETag") != etag {
		t.Fatalf("expected /api/v1/manifest parity with /api/v1/screens/manifest, got %d etag=%q want=%q", wAlias.Code, wAlias.Header().Get("ETag"), etag)
	}

	var env apiEnvelope
	if err := json.Unmarshal(w.Body.Bytes(), &env); err != nil || !env.Success {
		t.Fatalf("invalid manifest envelope: %s, err=%v", w.Body.String(), err)
	}
	var manifest snippets.ScreenManifestResponse
	if err := json.Unmarshal(env.Data, &manifest); err != nil {
		t.Fatalf("unmarshal ScreenManifestResponse: %v", err)
	}
	if manifest.AppVersion != "0.7.0" || manifest.SduiSchemaVersion != snippets.DefaultSduiSchemaVersion {
		t.Fatalf("expected app_version=0.7.0 and sdui_schema_version=1.0, got %+v", manifest)
	}
	if len(manifest.Screens) < 12 {
		t.Fatalf("expected >= 12 registered screens in manifest, got %d", len(manifest.Screens))
	}

	// 2. Conditional GET with If-None-Match -> 304 Not Modified
	w304 := httptest.NewRecorder()
	req304 := httptest.NewRequest(http.MethodGet, "/api/v1/screens/manifest", nil)
	req304.Header.Set("If-None-Match", etag)
	r.ServeHTTP(w304, req304)
	if w304.Code != http.StatusNotModified || w304.Body.Len() != 0 {
		t.Fatalf("expected 304 Not Modified with empty body, got status=%d len=%d", w304.Code, w304.Body.Len())
	}

	// 3. Parity: GET /api/v1/screens/home HTTP ETag == page.ETag == manifest.Screens["home"].ETag
	wHome := httptest.NewRecorder()
	r.ServeHTTP(wHome, httptest.NewRequest(http.MethodGet, "/api/v1/screens/home", nil))
	if wHome.Code != http.StatusOK {
		t.Fatalf("GET /api/v1/screens/home status = %d", wHome.Code)
	}
	var homeEnv apiEnvelope
	if err := json.Unmarshal(wHome.Body.Bytes(), &homeEnv); err != nil || !homeEnv.Success {
		t.Fatalf("invalid home envelope: %s", wHome.Body.String())
	}
	var homePage snippets.PageResponse
	if err := json.Unmarshal(homeEnv.Data, &homePage); err != nil {
		t.Fatalf("unmarshal home PageResponse: %v", err)
	}
	homeMeta, ok := manifest.Screens["home"]
	if !ok {
		t.Fatal("manifest missing 'home' entry")
	}
	if homePage.AppVersion != manifest.AppVersion || homePage.PageVersion != homeMeta.Version || homePage.ETag != homeMeta.ETag {
		t.Fatalf("home page version meta (%s, %s, %s) mismatches manifest (%s, %s, %s)",
			homePage.AppVersion, homePage.PageVersion, homePage.ETag,
			manifest.AppVersion, homeMeta.Version, homeMeta.ETag)
	}
	if wHome.Header().Get("ETag") != homePage.ETag || wHome.Header().Get("X-Page-Version") != homePage.PageVersion || wHome.Header().Get("X-App-Version") != homePage.AppVersion {
		t.Fatalf("HTTP headers (%q, %q, %q) mismatch page/manifest (%q, %q, %q)",
			wHome.Header().Get("ETag"), wHome.Header().Get("X-Page-Version"), wHome.Header().Get("X-App-Version"),
			homePage.ETag, homePage.PageVersion, homePage.AppVersion)
	}
}

func TestScreenManifestEndpoint_CustomAppVersionAndRepoError(t *testing.T) {
	rCustom := buildRouterWithOptions(func(cfg *handler.RouterConfig) {
		cfg.AppVersion = "1.4.2"
	})
	w := httptest.NewRecorder()
	rCustom.ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/api/v1/screens/manifest", nil))
	if w.Code != http.StatusOK || !strings.Contains(w.Body.String(), `"app_version":"1.4.2"`) {
		t.Fatalf("expected custom app_version 1.4.2, got %d: %s", w.Code, w.Body.String())
	}

	wScreen := httptest.NewRecorder()
	rCustom.ServeHTTP(wScreen, httptest.NewRequest(http.MethodGet, "/api/v1/screens/home", nil))
	if wScreen.Code != http.StatusOK || !strings.Contains(wScreen.Body.String(), `"app_version":"1.4.2"`) {
		t.Fatalf("expected custom app_version 1.4.2 on /screens/home, got %d: %s", wScreen.Code, wScreen.Body.String())
	}

	rFail := buildRouterWithOptions(func(cfg *handler.RouterConfig) {
		cfg.ScreenService = usecase.NewScreenService(failingScreenRepo{})
	})
	wFail := httptest.NewRecorder()
	rFail.ServeHTTP(wFail, httptest.NewRequest(http.MethodGet, "/api/v1/screens/manifest", nil))
	if wFail.Code != http.StatusInternalServerError {
		t.Fatalf("expected 500 on failing screen repo manifest, got %d", wFail.Code)
	}
}

func TestStreamDeltasEndpoint_SSEHeadersFramesAndLastEventIDResumption(t *testing.T) {
	r := buildRouterWithOptions(func(cfg *handler.RouterConfig) {
		cfg.EnableCompression = true
		cfg.EnableETag = true
	})

	// 1. Full stream with Accept-Encoding: br, gzip stays uncompressed SSE
	w := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/api/v1/streams/ai-assistant", nil)
	req.Header.Set("Accept-Encoding", "br, gzip")
	r.ServeHTTP(w, req)
	if w.Code != http.StatusOK {
		t.Fatalf("GET /api/v1/streams/ai-assistant status = %d, body = %s", w.Code, w.Body.String())
	}
	if w.Header().Get("Content-Type") != "text/event-stream; charset=utf-8" {
		t.Fatalf("expected Content-Type=text/event-stream; charset=utf-8, got %q", w.Header().Get("Content-Type"))
	}
	if w.Header().Get("Cache-Control") != "no-cache, no-transform" {
		t.Fatalf("expected Cache-Control=no-cache, no-transform, got %q", w.Header().Get("Cache-Control"))
	}
	if w.Header().Get("Connection") != "keep-alive" {
		t.Fatalf("expected Connection=keep-alive on HTTP/1.1, got %q", w.Header().Get("Connection"))
	}
	if w.Header().Get("X-Accel-Buffering") != "no" {
		t.Fatalf("expected X-Accel-Buffering=no, got %q", w.Header().Get("X-Accel-Buffering"))
	}
	if w.Header().Get("Content-Encoding") != "" {
		t.Fatalf("expected SSE stream uncompressed, got Content-Encoding=%q", w.Header().Get("Content-Encoding"))
	}
	if !strings.Contains(w.Body.String(), "retry: 2000\n") {
		t.Fatalf("expected SSE retry: 2000 directive in body: %q", w.Body.String())
	}

	frames := parseSSEFrames(t, w.Body.String())
	if len(frames) != 4 {
		t.Fatalf("expected 4 SSE frames, got %d: %s", len(frames), w.Body.String())
	}
	var patches []snippets.SnippetDeltaPatch
	for i, f := range frames {
		if f.Event != "delta" {
			t.Fatalf("frame %d expected event=delta, got %q", i, f.Event)
		}
		var p snippets.SnippetDeltaPatch
		if err := json.Unmarshal([]byte(f.Data), &p); err != nil {
			t.Fatalf("frame %d unmarshal SnippetDeltaPatch failed: %v (data=%s)", i, err, f.Data)
		}
		patches = append(patches, p)
	}
	if frames[0].ID != "1" || frames[1].ID != "2" || frames[2].ID != "3" || frames[3].ID != "4" {
		t.Fatalf("unexpected SSE event IDs: %+v", frames)
	}
	if patches[0].Op != snippets.DeltaOpAppendText || patches[1].Op != snippets.DeltaOpAppendText {
		t.Fatalf("expected first two patches to be append_text, got %+v", patches[:2])
	}
	if patches[2].Op != snippets.DeltaOpPatchSnippetData {
		t.Fatalf("expected third patch to be patch_snippet_data, got %+v", patches[2])
	}
	if patches[3].Op != snippets.DeltaOpUpdatePageMeta || !patches[3].Done {
		t.Fatalf("expected final patch to have done=true, got %+v", patches[3])
	}

	// 2. Last-Event-ID: 2 resumes strictly after event 2 (delivers 3 and 4 only)
	wResume := httptest.NewRecorder()
	reqResume := httptest.NewRequest(http.MethodGet, "/api/v1/streams/ai-assistant", nil)
	reqResume.Header.Set("Last-Event-ID", "2")
	r.ServeHTTP(wResume, reqResume)
	if wResume.Code != http.StatusOK {
		t.Fatalf("resumed stream status = %d", wResume.Code)
	}
	resumedFrames := parseSSEFrames(t, wResume.Body.String())
	if len(resumedFrames) != 2 || resumedFrames[0].ID != "3" || resumedFrames[1].ID != "4" {
		t.Fatalf("expected resumed frames [3, 4], got %+v", resumedFrames)
	}

	// 3. Last-Event-ID >= total frames returns 204 No Content to stop reconnect loops cleanly
	wDone := httptest.NewRecorder()
	reqDone := httptest.NewRequest(http.MethodGet, "/api/v1/streams/order-live", nil)
	reqDone.Header.Set("Last-Event-ID", "4")
	r.ServeHTTP(wDone, reqDone)
	if wDone.Code != http.StatusNoContent || wDone.Body.Len() != 0 {
		t.Fatalf("expected 204 No Content when Last-Event-ID=4, got status=%d body=%q", wDone.Code, wDone.Body.String())
	}

	// 4. Malformed or negative Last-Event-ID safely replays from event 1; sync-status is built-in
	wMalformed := httptest.NewRecorder()
	reqMalformed := httptest.NewRequest(http.MethodGet, "/api/v1/streams/sync-status", nil)
	reqMalformed.Header.Set("Last-Event-ID", "not-a-number")
	r.ServeHTTP(wMalformed, reqMalformed)
	if got := parseSSEFrames(t, wMalformed.Body.String()); len(got) != 4 || got[0].ID != "1" {
		t.Fatalf("expected full replay on sync-status with malformed Last-Event-ID, got %+v", got)
	}

	// 5. Unknown streamId -> 404 NOT_FOUND; blank streamId -> 400 BAD_REQUEST
	wNotFound := httptest.NewRecorder()
	r.ServeHTTP(wNotFound, httptest.NewRequest(http.MethodGet, "/api/v1/streams/non-existent-feed", nil))
	if wNotFound.Code != http.StatusNotFound {
		t.Fatalf("expected 404 for unknown streamId, got %d: %s", wNotFound.Code, wNotFound.Body.String())
	}

	wBlank := httptest.NewRecorder()
	r.ServeHTTP(wBlank, httptest.NewRequest(http.MethodGet, "/api/v1/streams/%20%20", nil))
	if wBlank.Code != http.StatusBadRequest {
		t.Fatalf("expected 400 for blank streamId, got %d: %s", wBlank.Code, wBlank.Body.String())
	}

	// 6. HTTP/2 omits Connection: keep-alive header (RFC 9113)
	wH2 := httptest.NewRecorder()
	reqH2 := httptest.NewRequest(http.MethodGet, "/api/v1/streams/order-live", nil)
	reqH2.ProtoMajor = 2
	reqH2.ProtoMinor = 0
	reqH2.Proto = "HTTP/2.0"
	r.ServeHTTP(wH2, reqH2)
	if wH2.Code != http.StatusOK || wH2.Header().Get("Connection") != "" {
		t.Fatalf("expected HTTP/2 SSE to omit Connection header, got status=%d Connection=%q", wH2.Code, wH2.Header().Get("Connection"))
	}

	// 7. Pre-cancelled context and ShutdownContext stop streaming cleanly without error
	cancelledCtx, cancel := context.WithCancel(context.Background())
	cancel()
	wCancel := httptest.NewRecorder()
	reqCancel := httptest.NewRequest(http.MethodGet, "/api/v1/streams/ai-assistant", nil).WithContext(cancelledCtx)
	r.ServeHTTP(wCancel, reqCancel)
	if wCancel.Code != http.StatusOK || len(parseSSEFrames(t, wCancel.Body.String())) != 0 {
		t.Fatalf("expected clean early stop on cancelled context, got status=%d body=%q", wCancel.Code, wCancel.Body.String())
	}

	rShutdown := buildRouterWithOptions(func(cfg *handler.RouterConfig) {
		cfg.ShutdownContext = cancelledCtx
	})
	wShut := httptest.NewRecorder()
	rShutdown.ServeHTTP(wShut, httptest.NewRequest(http.MethodGet, "/api/v1/streams/ai-assistant", nil))
	if wShut.Code != http.StatusOK || len(parseSSEFrames(t, wShut.Body.String())) != 0 {
		t.Fatalf("expected clean early stop on cancelled ShutdownContext, got status=%d body=%q", wShut.Code, wShut.Body.String())
	}

	// 8. Pluggable StreamProvider (success + error)
	rCustom := buildRouterWithOptions(func(cfg *handler.RouterConfig) {
		cfg.StreamProvider = stubStreamProvider{
			events: []server.StreamEvent{{Patch: snippets.NewRemoveSnippetPatch("old_1").WithDone(true)}},
		}
	})
	wCustom := httptest.NewRecorder()
	rCustom.ServeHTTP(wCustom, httptest.NewRequest(http.MethodGet, "/api/v1/streams/custom", nil))
	if wCustom.Code != http.StatusOK || len(parseSSEFrames(t, wCustom.Body.String())) != 1 {
		t.Fatalf("expected custom StreamProvider to emit 1 frame, got %d body=%q", wCustom.Code, wCustom.Body.String())
	}

	rErr := buildRouterWithOptions(func(cfg *handler.RouterConfig) {
		cfg.StreamProvider = stubStreamProvider{err: errors.New("stream backend failed")}
	})
	wErr := httptest.NewRecorder()
	rErr.ServeHTTP(wErr, httptest.NewRequest(http.MethodGet, "/api/v1/streams/custom", nil))
	if wErr.Code != http.StatusInternalServerError {
		t.Fatalf("expected 500 on StreamProvider error, got %d", wErr.Code)
	}
}

type stubStreamProvider struct {
	events []server.StreamEvent
	err    error
}

func (s stubStreamProvider) Stream(_ context.Context, _ string, _ string) ([]server.StreamEvent, bool, error) {
	if s.err != nil {
		return nil, false, s.err
	}
	return s.events, true, nil
}
