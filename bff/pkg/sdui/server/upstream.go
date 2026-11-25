package server

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	"github.com/abeta-dev/go-libs/apperror"
	"github.com/abeta-dev/go-libs/ginmw"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
)

const (
	// DefaultUpstreamTimeout is the default HTTP timeout for upstream SDUI fetches.
	DefaultUpstreamTimeout = 10 * time.Second
	// DefaultUpstreamMaxBytes caps upstream JSON payloads at 8 MiB.
	DefaultUpstreamMaxBytes int64 = 8 << 20
)

// ErrNotModified is returned by FetchScreenConditional when the upstream answers 304 Not Modified.
var ErrNotModified = errors.New("server: upstream screen not modified")

// StreamEvent represents one SSE frame delivered to SDUI streaming clients.
type StreamEvent struct {
	ID    string
	Event string
	Patch snippets.SnippetDeltaPatch
}

// StreamProvider resolves incremental SDUI SSE delta events for streamID starting after lastEventID.
type StreamProvider interface {
	Stream(ctx context.Context, streamID string, lastEventID string) (events []StreamEvent, found bool, err error)
}

// UpstreamOption configures HTTPUpstreamFetcher.
type UpstreamOption func(*HTTPUpstreamFetcher)

// WithHTTPClient sets a custom *http.Client on HTTPUpstreamFetcher.
func WithHTTPClient(client *http.Client) UpstreamOption {
	return func(f *HTTPUpstreamFetcher) {
		if client != nil {
			f.client = client
		}
	}
}

// WithTimeout configures the request timeout on the default *http.Client.
func WithTimeout(d time.Duration) UpstreamOption {
	return func(f *HTTPUpstreamFetcher) {
		if d > 0 {
			f.timeout = d
		}
	}
}

// WithHeader adds a static header sent on every upstream screen fetch.
func WithHeader(key, value string) UpstreamOption {
	return func(f *HTTPUpstreamFetcher) {
		if k := strings.TrimSpace(key); k != "" {
			f.headers.Set(k, value)
		}
	}
}

// WithClientAppVersion configures the X-Client-App-Version header forwarded to the upstream.
func WithClientAppVersion(version string) UpstreamOption {
	return func(f *HTTPUpstreamFetcher) {
		if v := strings.TrimSpace(version); v != "" {
			f.headers.Set("X-Client-App-Version", v)
		}
	}
}

// WithHeaderForwarder attaches a per-request header callback (e.g. forwarding Authorization or If-None-Match).
func WithHeaderForwarder(fn func(ctx context.Context) http.Header) UpstreamOption {
	return func(f *HTTPUpstreamFetcher) {
		f.headerForwarder = fn
	}
}

// WithScreenPathTemplate sets the URL path template containing one %s placeholder for screenID.
func WithScreenPathTemplate(tmpl string) UpstreamOption {
	return func(f *HTTPUpstreamFetcher) {
		if strings.TrimSpace(tmpl) != "" {
			f.pathTemplate = tmpl
		}
	}
}

// WithMaxResponseBytes caps the maximum upstream response body read (defaults to 8 MiB).
func WithMaxResponseBytes(limit int64) UpstreamOption {
	return func(f *HTTPUpstreamFetcher) {
		if limit > 0 {
			f.maxBytes = limit
		}
	}
}

// HTTPUpstreamFetcher fetches SDUI PageResponse documents from an upstream HTTP BFF,
// supporting both ginmw.Envelope and raw PageResponse payloads.
type HTTPUpstreamFetcher struct {
	baseURL         string
	pathTemplate    string
	client          *http.Client
	timeout         time.Duration
	headers         http.Header
	headerForwarder func(ctx context.Context) http.Header
	maxBytes        int64
}

// NewHTTPUpstreamFetcher creates an HTTPUpstreamFetcher targeting baseURL.
func NewHTTPUpstreamFetcher(baseURL string, opts ...UpstreamOption) *HTTPUpstreamFetcher {
	f := &HTTPUpstreamFetcher{
		baseURL:      strings.TrimRight(strings.TrimSpace(baseURL), "/"),
		pathTemplate: "/api/v1/screens/%s",
		timeout:      DefaultUpstreamTimeout,
		headers:      make(http.Header),
		maxBytes:     DefaultUpstreamMaxBytes,
	}
	for _, opt := range opts {
		if opt != nil {
			opt(f)
		}
	}
	if f.client == nil {
		f.client = &http.Client{Timeout: f.timeout}
	}
	return f
}

// FetchScreen fetches screenID from the upstream BFF, assignable directly to cache.ScreenFetcher.
func (f *HTTPUpstreamFetcher) FetchScreen(ctx context.Context, screenID string) (snippets.PageResponse, error) {
	page, _, _, err := f.FetchScreenConditional(ctx, screenID, "")
	return page, err
}

// FetchScreenConditional fetches screenID with an optional If-None-Match validator,
// returning (zero, true, etag, ErrNotModified) on HTTP 304 Not Modified.
func (f *HTTPUpstreamFetcher) FetchScreenConditional(
	ctx context.Context,
	screenID string,
	ifNoneMatch string,
) (snippets.PageResponse, bool, string, error) {
	id := strings.TrimSpace(screenID)
	if id == "" {
		return snippets.PageResponse{}, false, "", apperror.BadRequest("screen_id must not be empty")
	}
	if f.baseURL == "" {
		return snippets.PageResponse{}, false, "", apperror.Internal("upstream base URL is not configured")
	}

	targetURL := f.baseURL + fmt.Sprintf(f.pathTemplate, url.PathEscape(id))
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, targetURL, nil)
	if err != nil {
		return snippets.PageResponse{}, false, "", fmt.Errorf("server: build upstream request: %w", err)
	}
	req.Header.Set("Accept", "application/json")
	for k, vals := range f.headers {
		for _, v := range vals {
			req.Header.Add(k, v)
		}
	}
	if f.headerForwarder != nil {
		for k, vals := range f.headerForwarder(ctx) {
			req.Header.Del(k)
			for _, v := range vals {
				req.Header.Add(k, v)
			}
		}
	}
	if inm := strings.TrimSpace(ifNoneMatch); inm != "" {
		req.Header.Set("If-None-Match", inm)
	}

	resp, err := f.client.Do(req)
	if err != nil {
		return snippets.PageResponse{}, false, "", fmt.Errorf("server: upstream request failed: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()

	respETag := strings.TrimSpace(resp.Header.Get("ETag"))
	if resp.StatusCode == http.StatusNotModified {
		return snippets.PageResponse{}, true, respETag, ErrNotModified
	}

	raw, err := io.ReadAll(io.LimitReader(resp.Body, f.maxBytes+1))
	if err != nil {
		return snippets.PageResponse{}, false, respETag, fmt.Errorf("server: read upstream body: %w", err)
	}
	if int64(len(raw)) > f.maxBytes {
		return snippets.PageResponse{}, false, respETag, fmt.Errorf("server: upstream response exceeds %d byte limit", f.maxBytes)
	}

	if resp.StatusCode == http.StatusNotFound {
		return snippets.PageResponse{}, false, respETag, apperror.NotFound("screen not found: " + id)
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return snippets.PageResponse{}, false, respETag, upstreamStatusError(resp.StatusCode, raw)
	}

	page, err := decodePageOrEnvelope(raw)
	if err != nil {
		return snippets.PageResponse{}, false, respETag, err
	}
	if page.ETag == "" && respETag != "" && !strings.HasPrefix(respETag, "W/") {
		page.ETag = respETag
	}
	return page, false, respETag, nil
}

func decodePageOrEnvelope(raw []byte) (snippets.PageResponse, error) {
	var probe struct {
		Success *bool           `json:"success"`
		Data    json.RawMessage `json:"data"`
		Error   *ginmw.APIError `json:"error"`
	}
	if err := json.Unmarshal(raw, &probe); err != nil {
		return snippets.PageResponse{}, fmt.Errorf("server: decode upstream JSON: %w", err)
	}

	var page snippets.PageResponse
	if probe.Success != nil {
		if !*probe.Success {
			if probe.Error != nil && probe.Error.Message != "" {
				return snippets.PageResponse{}, fmt.Errorf("server: upstream error (%s): %s", probe.Error.Code, probe.Error.Message)
			}
			return snippets.PageResponse{}, errors.New("server: upstream envelope returned success=false")
		}
		if err := json.Unmarshal(probe.Data, &page); err != nil {
			return snippets.PageResponse{}, fmt.Errorf("server: decode upstream envelope data: %w", err)
		}
		return page, nil
	}

	if err := json.Unmarshal(raw, &page); err != nil {
		return snippets.PageResponse{}, fmt.Errorf("server: decode upstream PageResponse: %w", err)
	}
	return page, nil
}

func upstreamStatusError(status int, raw []byte) error {
	var probe struct {
		Error *ginmw.APIError `json:"error"`
	}
	if json.Unmarshal(raw, &probe) == nil && probe.Error != nil && probe.Error.Message != "" {
		return fmt.Errorf("server: upstream HTTP %d (%s): %s", status, probe.Error.Code, probe.Error.Message)
	}
	return fmt.Errorf("server: upstream HTTP %d", status)
}
