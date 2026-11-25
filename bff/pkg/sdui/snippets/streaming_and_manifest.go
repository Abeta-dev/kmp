package snippets

import (
	"bytes"
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"strings"
)

// DefaultSduiSchemaVersion is the canonical SDUI wire schema version advertised in manifests.
const DefaultSduiSchemaVersion = "1.0"

// ScreenVersionMeta holds deterministic version and cache validation metadata for one SDUI screen.
type ScreenVersionMeta struct {
	Version          string `json:"version"`
	ETag             string `json:"etag,omitempty"`
	UpdatedAtEpochMs *int64 `json:"updated_at_epoch_ms,omitempty"`
}

// ScreenManifestResponse maps registered screen IDs to their version metadata for cache gating.
type ScreenManifestResponse struct {
	AppVersion         string                       `json:"app_version"`
	SduiSchemaVersion  string                       `json:"sdui_schema_version,omitempty"`
	Screens            map[string]ScreenVersionMeta `json:"screens"`
	GeneratedAtEpochMs *int64                       `json:"generated_at_epoch_ms,omitempty"`
}

// SseStreamSpec declares a Server-Sent Events delta stream attached to a PageResponse.
type SseStreamSpec struct {
	Path        string            `json:"path"`
	AutoConnect bool              `json:"auto_connect"`
	Headers     map[string]string `json:"headers,omitempty"`
	RetryMs     *int64            `json:"retry_ms,omitempty"`
}

// SnippetDeltaOp identifies an incremental mutation operation delivered via SSE.
type SnippetDeltaOp string

// Wire identifiers for SnippetDeltaPatch.Op matching KMP SnippetDeltaOp 1:1.
const (
	DeltaOpAppendText       SnippetDeltaOp = "append_text"
	DeltaOpPatchSnippetData SnippetDeltaOp = "patch_snippet_data"
	DeltaOpReplaceSnippet   SnippetDeltaOp = "replace_snippet"
	DeltaOpInsertSnippet    SnippetDeltaOp = "insert_snippet"
	DeltaOpRemoveSnippet    SnippetDeltaOp = "remove_snippet"
	DeltaOpUpdatePageMeta   SnippetDeltaOp = "update_page_meta"
)

// SnippetDeltaPatch represents a single incremental SDUI mutation emitted over SSE.
type SnippetDeltaPatch struct {
	Op          SnippetDeltaOp   `json:"op"`
	SnippetID   string           `json:"snippet_id,omitempty"`
	FieldPath   string           `json:"field_path,omitempty"`
	TextChunk   string           `json:"text_chunk,omitempty"`
	DataPatch   map[string]any   `json:"data_patch,omitempty"`
	Snippet     *SnippetEnvelope `json:"snippet,omitempty"`
	InsertIndex *int             `json:"insert_index,omitempty"`
	Title       string           `json:"title,omitempty"`
	PageVersion string           `json:"page_version,omitempty"`
	Done        bool             `json:"done,omitempty"`
}

// WithDone returns a copy of p with Done configured.
func (p SnippetDeltaPatch) WithDone(done bool) SnippetDeltaPatch {
	p.Done = done
	return p
}

// Int64Ptr returns a pointer to v for optional int64 wire fields.
func Int64Ptr(v int64) *int64 {
	return &v
}

// NewAppendTextPatch constructs an append_text delta patch for snippetID at fieldPath.
func NewAppendTextPatch(snippetID, fieldPath, textChunk string) SnippetDeltaPatch {
	return SnippetDeltaPatch{
		Op:        DeltaOpAppendText,
		SnippetID: snippetID,
		FieldPath: fieldPath,
		TextChunk: textChunk,
	}
}

// NewPatchSnippetData constructs a patch_snippet_data delta patch with a defensive shallow copy of dataPatch.
func NewPatchSnippetData(snippetID string, dataPatch map[string]any) SnippetDeltaPatch {
	var copied map[string]any
	if len(dataPatch) > 0 {
		copied = make(map[string]any, len(dataPatch))
		for k, v := range dataPatch {
			copied[k] = v
		}
	}
	return SnippetDeltaPatch{
		Op:        DeltaOpPatchSnippetData,
		SnippetID: snippetID,
		DataPatch: copied,
	}
}

// NewReplaceSnippetPatch constructs a replace_snippet delta patch replacing snippetID with snippet.
func NewReplaceSnippetPatch(snippetID string, snippet SnippetEnvelope) SnippetDeltaPatch {
	cp := snippet
	return SnippetDeltaPatch{
		Op:        DeltaOpReplaceSnippet,
		SnippetID: snippetID,
		Snippet:   &cp,
	}
}

// NewInsertSnippetPatch constructs an insert_snippet delta patch at optional insertIndex.
func NewInsertSnippetPatch(snippet SnippetEnvelope, insertIndex *int) SnippetDeltaPatch {
	cp := snippet
	var idxPtr *int
	if insertIndex != nil {
		idx := *insertIndex
		idxPtr = &idx
	}
	return SnippetDeltaPatch{
		Op:          DeltaOpInsertSnippet,
		SnippetID:   snippet.SnippetID,
		Snippet:     &cp,
		InsertIndex: idxPtr,
	}
}

// NewRemoveSnippetPatch constructs a remove_snippet delta patch targeting snippetID.
func NewRemoveSnippetPatch(snippetID string) SnippetDeltaPatch {
	return SnippetDeltaPatch{
		Op:        DeltaOpRemoveSnippet,
		SnippetID: snippetID,
	}
}

// NewUpdatePageMetaPatch constructs an update_page_meta delta patch updating title and/or pageVersion.
func NewUpdatePageMetaPatch(title, pageVersion string) SnippetDeltaPatch {
	return SnippetDeltaPatch{
		Op:          DeltaOpUpdatePageMeta,
		Title:       title,
		PageVersion: pageVersion,
	}
}

// ComputePageETag computes a deterministic quoted SHA-256 ETag (`"<hex>"`) for page,
// excluding volatile version/etag stamps and canonicalizing object key order so
// typed in-memory envelopes and unmarshaled map-backed envelopes hash identically.
func ComputePageETag(page PageResponse) (string, error) {
	canonical := page
	canonical.PageVersion = ""
	canonical.AppVersion = ""
	canonical.ETag = ""
	raw, err := json.Marshal(canonical)
	if err != nil {
		return "", fmt.Errorf("snippets: compute page etag for %q: %w", page.PageID, err)
	}
	var normalized any
	dec := json.NewDecoder(bytes.NewReader(raw))
	dec.UseNumber()
	if err := dec.Decode(&normalized); err != nil {
		return "", fmt.Errorf("snippets: canonicalize page etag for %q: %w", page.PageID, err)
	}
	canonicalBytes, err := json.Marshal(normalized)
	if err != nil {
		return "", fmt.Errorf("snippets: encode canonical page etag for %q: %w", page.PageID, err)
	}
	sum := sha256.Sum256(canonicalBytes)
	return fmt.Sprintf(`"%x"`, sum), nil
}

// DerivePageVersion derives a deterministic short page version identifier (`v1-<12hex>`)
// from a quoted or unquoted hex ETag string.
func DerivePageVersion(etag string) string {
	clean := strings.TrimSpace(etag)
	clean = strings.TrimPrefix(clean, "W/")
	clean = strings.Trim(clean, `"`)
	if len(clean) > 12 {
		clean = clean[:12]
	}
	if clean == "" {
		return "v1-000000000000"
	}
	return "v1-" + clean
}
