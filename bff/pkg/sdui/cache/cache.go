// Package cache provides pure Go, transport-agnostic SDUI screen caching,
// singleflight fetch coalescing, tag/prefix invalidation, and O(1) manifest generation.
// This package must never import HTTP, Gin, or database drivers.
package cache

import (
	"container/list"
	"context"
	"errors"
	"fmt"
	"maps"
	"sort"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
	"golang.org/x/sync/singleflight"
)

const (
	// DefaultTTL is the default cache entry lifetime when WithTTL is not provided.
	DefaultTTL = 5 * time.Minute
	// DefaultMaxEntries is the default bounded LRU cache capacity when WithMaxEntries is not provided.
	DefaultMaxEntries = 256
)

var (
	// ErrEmptyScreenID indicates an empty or whitespace-only screen identifier.
	ErrEmptyScreenID = errors.New("cache: screen_id must not be empty")
	// ErrNilFetcher indicates UpstreamCachedScreenRepository was created without a ScreenFetcher.
	ErrNilFetcher = errors.New("cache: screen fetcher is not configured")
)

// ScreenRepository retrieves SDUI screen definitions and lists known screen IDs.
type ScreenRepository interface {
	GetScreen(ctx context.Context, screenID string) (snippets.PageResponse, error)
	ListScreenIDs(ctx context.Context) ([]string, error)
}

// ManifestProvider produces a version & ETag manifest for registered SDUI screens.
type ManifestProvider interface {
	BuildManifest(ctx context.Context, appVersion string) (snippets.ScreenManifestResponse, error)
}

// ScreenFetcher loads a single SDUI screen from an upstream source.
type ScreenFetcher func(ctx context.Context, screenID string) (snippets.PageResponse, error)

// ScopeKeyFunc derives a cache partition key (e.g. tenant, locale, or tier) from ctx.
type ScopeKeyFunc func(ctx context.Context) string

// Option configures UpstreamCachedScreenRepository.
type Option func(*UpstreamCachedScreenRepository)

// WithTTL sets the cache entry time-to-live (defaults to 5 minutes).
func WithTTL(ttl time.Duration) Option {
	return func(r *UpstreamCachedScreenRepository) {
		if ttl > 0 {
			r.ttl = ttl
		}
	}
}

// WithMaxEntries sets the bounded LRU capacity (defaults to 256).
func WithMaxEntries(maxEntries int) Option {
	return func(r *UpstreamCachedScreenRepository) {
		if maxEntries > 0 {
			r.maxEntries = maxEntries
		}
	}
}

// WithAppVersion sets the default app_version stamped on fetched pages and manifests.
func WithAppVersion(appVersion string) Option {
	return func(r *UpstreamCachedScreenRepository) {
		r.appVersion = strings.TrimSpace(appVersion)
	}
}

// WithSchemaVersion sets the sdui_schema_version advertised in manifests (defaults to "1.0").
func WithSchemaVersion(schemaVersion string) Option {
	return func(r *UpstreamCachedScreenRepository) {
		if v := strings.TrimSpace(schemaVersion); v != "" {
			r.schemaVersion = v
		}
	}
}

// WithScopeKeyFunc sets the per-request cache partitioning function.
func WithScopeKeyFunc(fn ScopeKeyFunc) Option {
	return func(r *UpstreamCachedScreenRepository) {
		r.scopeKeyFunc = fn
	}
}

// WithClock overrides the wall clock used for TTL expiry and updated_at timestamps (for tests).
func WithClock(now func() time.Time) Option {
	return func(r *UpstreamCachedScreenRepository) {
		if now != nil {
			r.now = now
		}
	}
}

// WithKnownScreens registers initial known screen identifiers.
func WithKnownScreens(ids ...string) Option {
	return func(r *UpstreamCachedScreenRepository) {
		for _, id := range ids {
			if clean := strings.TrimSpace(id); clean != "" {
				r.knownScreens[clean] = struct{}{}
			}
		}
	}
}

type cacheKey struct {
	scope    string
	screenID string
}

type cacheEntry struct {
	key         cacheKey
	page        snippets.PageResponse
	version     string
	etag        string
	updatedAtMs int64
	expiresAt   time.Time
	elem        *list.Element
}

type manifestKey struct {
	scope      string
	appVersion string
}

type manifestSnapshot struct {
	generation uint64
	expiresAt  time.Time
	manifest   snippets.ScreenManifestResponse
}

// UpstreamCachedScreenRepository caches SDUI screens fetched from an upstream
// ScreenFetcher with singleflight coalescing, bounded LRU eviction, TTL expiry,
// tag/prefix invalidation, and O(1) cached manifest assembly.
type UpstreamCachedScreenRepository struct {
	fetcher       ScreenFetcher
	ttl           time.Duration
	maxEntries    int
	appVersion    string
	schemaVersion string
	scopeKeyFunc  ScopeKeyFunc
	now           func() time.Time

	group singleflight.Group

	mu           sync.Mutex
	entries      map[cacheKey]*cacheEntry
	lru          *list.List
	knownScreens map[string]struct{}
	screenTags   map[string]map[string]struct{}
	manifests    map[manifestKey]manifestSnapshot

	manifestGen     atomic.Uint64
	invalidationGen atomic.Uint64
}

var (
	_ ScreenRepository = (*UpstreamCachedScreenRepository)(nil)
	_ ManifestProvider = (*UpstreamCachedScreenRepository)(nil)
)

// NewUpstreamCachedScreenRepository constructs a bounded, concurrency-safe SDUI screen cache.
func NewUpstreamCachedScreenRepository(fetcher ScreenFetcher, opts ...Option) *UpstreamCachedScreenRepository {
	r := &UpstreamCachedScreenRepository{
		fetcher:       fetcher,
		ttl:           DefaultTTL,
		maxEntries:    DefaultMaxEntries,
		schemaVersion: snippets.DefaultSduiSchemaVersion,
		now:           time.Now,
		entries:       make(map[cacheKey]*cacheEntry),
		lru:           list.New(),
		knownScreens:  make(map[string]struct{}),
		screenTags:    make(map[string]map[string]struct{}),
		manifests:     make(map[manifestKey]manifestSnapshot),
	}
	for _, opt := range opts {
		if opt != nil {
			opt(r)
		}
	}
	return r
}

// ManifestGeneration returns the monotonically increasing manifest generation counter.
func (r *UpstreamCachedScreenRepository) ManifestGeneration() uint64 {
	return r.manifestGen.Load()
}

// RegisterScreen registers screenID as a known screen and associates optional invalidation tags.
func (r *UpstreamCachedScreenRepository) RegisterScreen(screenID string, tags ...string) {
	id := strings.TrimSpace(screenID)
	if id == "" {
		return
	}
	r.mu.Lock()
	defer r.mu.Unlock()

	r.knownScreens[id] = struct{}{}
	if len(tags) > 0 {
		tagSet := r.screenTags[id]
		if tagSet == nil {
			tagSet = make(map[string]struct{}, len(tags))
			r.screenTags[id] = tagSet
		}
		for _, t := range tags {
			if clean := strings.TrimSpace(t); clean != "" {
				tagSet[clean] = struct{}{}
			}
		}
	}
	clear(r.manifests)
	r.manifestGen.Add(1)
}

// SetKnownScreens replaces the set of known screen identifiers (preserving tags for retained IDs).
func (r *UpstreamCachedScreenRepository) SetKnownScreens(ids []string) {
	r.mu.Lock()
	defer r.mu.Unlock()

	next := make(map[string]struct{}, len(ids))
	for _, id := range ids {
		if clean := strings.TrimSpace(id); clean != "" {
			next[clean] = struct{}{}
		}
	}
	for existing := range r.screenTags {
		if _, ok := next[existing]; !ok {
			delete(r.screenTags, existing)
		}
	}
	r.knownScreens = next
	clear(r.manifests)
	r.manifestGen.Add(1)
}

// Invalidate evicts screenID across all cache scopes and atomically increments the manifest generation.
func (r *UpstreamCachedScreenRepository) Invalidate(screenID string) {
	id := strings.TrimSpace(screenID)
	r.mu.Lock()
	defer r.mu.Unlock()

	for k, e := range r.entries {
		if k.screenID == id {
			r.removeLocked(e)
		}
	}
	clear(r.manifests)
	r.invalidationGen.Add(1)
	r.manifestGen.Add(1)
}

// InvalidatePrefix evicts all cached screens whose ID starts with prefix and bumps manifest generation.
func (r *UpstreamCachedScreenRepository) InvalidatePrefix(prefix string) {
	p := strings.TrimSpace(prefix)
	r.mu.Lock()
	defer r.mu.Unlock()

	for k, e := range r.entries {
		if strings.HasPrefix(k.screenID, p) {
			r.removeLocked(e)
		}
	}
	clear(r.manifests)
	r.invalidationGen.Add(1)
	r.manifestGen.Add(1)
}

// InvalidateTag evicts all cached screens registered with tag and bumps manifest generation.
func (r *UpstreamCachedScreenRepository) InvalidateTag(tag string) {
	clean := strings.TrimSpace(tag)
	r.mu.Lock()
	defer r.mu.Unlock()

	if clean != "" {
		for k, e := range r.entries {
			if tags, ok := r.screenTags[k.screenID]; ok {
				if _, matched := tags[clean]; matched {
					r.removeLocked(e)
				}
			}
		}
	}
	clear(r.manifests)
	r.invalidationGen.Add(1)
	r.manifestGen.Add(1)
}

// InvalidateAll clears the entire screen and manifest cache and bumps manifest generation.
func (r *UpstreamCachedScreenRepository) InvalidateAll() {
	r.mu.Lock()
	defer r.mu.Unlock()

	clear(r.entries)
	r.lru.Init()
	clear(r.manifests)
	r.invalidationGen.Add(1)
	r.manifestGen.Add(1)
}

// ListScreenIDs returns a sorted list of known screen IDs (or cached IDs in scope when none are registered).
func (r *UpstreamCachedScreenRepository) ListScreenIDs(ctx context.Context) ([]string, error) {
	if err := ctx.Err(); err != nil {
		return nil, err
	}
	scope := r.resolveScope(ctx)

	r.mu.Lock()
	defer r.mu.Unlock()

	if len(r.knownScreens) > 0 {
		ids := make([]string, 0, len(r.knownScreens))
		for id := range r.knownScreens {
			ids = append(ids, id)
		}
		sort.Strings(ids)
		return ids, nil
	}

	now := r.now()
	ids := make([]string, 0, len(r.entries))
	for k, e := range r.entries {
		if k.scope == scope && now.Before(e.expiresAt) {
			ids = append(ids, k.screenID)
		}
	}
	sort.Strings(ids)
	return ids, nil
}

// GetScreen returns a cached SDUI PageResponse or coalesces an upstream fetch via singleflight.
func (r *UpstreamCachedScreenRepository) GetScreen(ctx context.Context, screenID string) (snippets.PageResponse, error) {
	if err := ctx.Err(); err != nil {
		return snippets.PageResponse{}, err
	}
	id := strings.TrimSpace(screenID)
	if id == "" {
		return snippets.PageResponse{}, ErrEmptyScreenID
	}
	scope := r.resolveScope(ctx)
	key := cacheKey{scope: scope, screenID: id}

	if page, ok := r.getCached(key); ok {
		return page, nil
	}
	if r.fetcher == nil {
		return snippets.PageResponse{}, ErrNilFetcher
	}

	sfKey := scope + "\x00" + id
	fetchCtx := context.WithoutCancel(ctx)
	ch := r.group.DoChan(sfKey, func() (any, error) {
		if page, ok := r.getCached(key); ok {
			return page, nil
		}
		invGen := r.invalidationGen.Load()
		page, err := r.fetcher(fetchCtx, id)
		if err != nil {
			return snippets.PageResponse{}, err
		}
		if err := r.normalizePage(&page); err != nil {
			return snippets.PageResponse{}, err
		}
		if r.invalidationGen.Load() == invGen {
			r.putCached(key, page)
		}
		return clonePageShallow(page), nil
	})

	select {
	case <-ctx.Done():
		return snippets.PageResponse{}, ctx.Err()
	case res := <-ch:
		if res.Err != nil {
			return snippets.PageResponse{}, res.Err
		}
		return clonePageShallow(res.Val.(snippets.PageResponse)), nil
	}
}

// BuildManifest returns a deterministic ScreenManifestResponse in O(1) from cached metadata
// once known screens have been populated, fetching any uncached known screens on demand.
func (r *UpstreamCachedScreenRepository) BuildManifest(ctx context.Context, appVersion string) (snippets.ScreenManifestResponse, error) {
	if err := ctx.Err(); err != nil {
		return snippets.ScreenManifestResponse{}, err
	}
	scope := r.resolveScope(ctx)
	ver := strings.TrimSpace(appVersion)
	if ver == "" {
		ver = r.appVersion
	}
	mKey := manifestKey{scope: scope, appVersion: ver}

	now := r.now()
	curGen := r.manifestGen.Load()
	r.mu.Lock()
	if snap, ok := r.manifests[mKey]; ok && snap.generation == curGen && now.Before(snap.expiresAt) {
		out := cloneManifest(snap.manifest)
		r.mu.Unlock()
		return out, nil
	}
	r.mu.Unlock()

	ids, err := r.ListScreenIDs(ctx)
	if err != nil {
		return snippets.ScreenManifestResponse{}, err
	}

	screens := make(map[string]snippets.ScreenVersionMeta, len(ids))
	var earliestExpiry time.Time

	for _, id := range ids {
		meta, exp, ok := r.getCachedMeta(cacheKey{scope: scope, screenID: id})
		if !ok {
			page, err := r.GetScreen(ctx, id)
			if err != nil {
				return snippets.ScreenManifestResponse{}, err
			}
			meta, exp, ok = r.getCachedMeta(cacheKey{scope: scope, screenID: id})
			if !ok {
				// Evicted immediately or invalidated mid-flight: build metadata directly from page.
				meta = snippets.ScreenVersionMeta{
					Version:          page.PageVersion,
					ETag:             page.ETag,
					UpdatedAtEpochMs: snippets.Int64Ptr(r.now().UnixMilli()),
				}
				exp = r.now().Add(r.ttl)
			}
		}
		screens[id] = meta
		if earliestExpiry.IsZero() || exp.Before(earliestExpiry) {
			earliestExpiry = exp
		}
	}

	if earliestExpiry.IsZero() {
		earliestExpiry = r.now().Add(r.ttl)
	}

	manifest := snippets.ScreenManifestResponse{
		AppVersion:        ver,
		SduiSchemaVersion: r.schemaVersion,
		Screens:           screens,
	}

	r.mu.Lock()
	finalGen := r.manifestGen.Load()
	r.manifests[mKey] = manifestSnapshot{
		generation: finalGen,
		expiresAt:  earliestExpiry,
		manifest:   cloneManifest(manifest),
	}
	r.mu.Unlock()

	return manifest, nil
}

func (r *UpstreamCachedScreenRepository) resolveScope(ctx context.Context) string {
	if r.scopeKeyFunc == nil {
		return ""
	}
	return r.scopeKeyFunc(ctx)
}

func (r *UpstreamCachedScreenRepository) normalizePage(page *snippets.PageResponse) error {
	snippets.EnsureComponentIDs(page)
	etag, err := snippets.ComputePageETag(*page)
	if err != nil {
		return fmt.Errorf("cache: compute page etag: %w", err)
	}
	page.ETag = etag
	page.PageVersion = snippets.DerivePageVersion(etag)
	if page.AppVersion == "" && r.appVersion != "" {
		page.AppVersion = r.appVersion
	}
	return nil
}

func (r *UpstreamCachedScreenRepository) getCached(key cacheKey) (snippets.PageResponse, bool) {
	r.mu.Lock()
	defer r.mu.Unlock()

	e, ok := r.entries[key]
	if !ok {
		return snippets.PageResponse{}, false
	}
	if !r.now().Before(e.expiresAt) {
		r.removeLocked(e)
		return snippets.PageResponse{}, false
	}
	r.lru.MoveToFront(e.elem)
	return clonePageShallow(e.page), true
}

func (r *UpstreamCachedScreenRepository) getCachedMeta(key cacheKey) (snippets.ScreenVersionMeta, time.Time, bool) {
	r.mu.Lock()
	defer r.mu.Unlock()

	e, ok := r.entries[key]
	if !ok {
		return snippets.ScreenVersionMeta{}, time.Time{}, false
	}
	if !r.now().Before(e.expiresAt) {
		r.removeLocked(e)
		return snippets.ScreenVersionMeta{}, time.Time{}, false
	}
	r.lru.MoveToFront(e.elem)
	return snippets.ScreenVersionMeta{
		Version:          e.version,
		ETag:             e.etag,
		UpdatedAtEpochMs: snippets.Int64Ptr(e.updatedAtMs),
	}, e.expiresAt, true
}

func (r *UpstreamCachedScreenRepository) putCached(key cacheKey, page snippets.PageResponse) {
	now := r.now()
	expiresAt := now.Add(r.ttl)

	r.mu.Lock()
	defer r.mu.Unlock()

	if existing, ok := r.entries[key]; ok {
		changed := existing.etag != page.ETag || existing.version != page.PageVersion
		existing.page = clonePageShallow(page)
		existing.version = page.PageVersion
		existing.etag = page.ETag
		existing.updatedAtMs = now.UnixMilli()
		existing.expiresAt = expiresAt
		r.lru.MoveToFront(existing.elem)
		if changed {
			clear(r.manifests)
			r.manifestGen.Add(1)
		}
		return
	}

	// Evict expired entries first, then LRU entries if still at capacity.
	if len(r.entries) >= r.maxEntries {
		r.evictExpiredLocked(now)
	}
	for len(r.entries) >= r.maxEntries {
		back := r.lru.Back()
		if back == nil {
			break
		}
		r.removeLocked(back.Value.(*cacheEntry))
	}

	entry := &cacheEntry{
		key:         key,
		page:        clonePageShallow(page),
		version:     page.PageVersion,
		etag:        page.ETag,
		updatedAtMs: now.UnixMilli(),
		expiresAt:   expiresAt,
	}
	entry.elem = r.lru.PushFront(entry)
	r.entries[key] = entry
	clear(r.manifests)
	r.manifestGen.Add(1)
}

func (r *UpstreamCachedScreenRepository) evictExpiredLocked(now time.Time) {
	for el := r.lru.Back(); el != nil; {
		prev := el.Prev()
		e := el.Value.(*cacheEntry)
		if !now.Before(e.expiresAt) {
			r.removeLocked(e)
		}
		el = prev
	}
}

func (r *UpstreamCachedScreenRepository) removeLocked(e *cacheEntry) {
	delete(r.entries, e.key)
	if e.elem != nil {
		r.lru.Remove(e.elem)
		e.elem = nil
	}
}

func clonePageShallow(page snippets.PageResponse) snippets.PageResponse {
	if len(page.Snippets) > 0 {
		envs := make([]snippets.SnippetEnvelope, len(page.Snippets))
		copy(envs, page.Snippets)
		page.Snippets = envs
	}
	return page
}

func cloneManifest(m snippets.ScreenManifestResponse) snippets.ScreenManifestResponse {
	out := m
	if m.Screens != nil {
		out.Screens = make(map[string]snippets.ScreenVersionMeta, len(m.Screens))
		maps.Copy(out.Screens, m.Screens)
	}
	if m.GeneratedAtEpochMs != nil {
		v := *m.GeneratedAtEpochMs
		out.GeneratedAtEpochMs = &v
	}
	return out
}
