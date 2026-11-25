package cache_test

import (
	"context"
	"errors"
	"fmt"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/abeta-dev/kmp/bff/pkg/sdui/atoms"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/cache"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/snippets"
	"github.com/abeta-dev/kmp/bff/pkg/sdui/theming"
)

type tenantCtxKey struct{}

func makeTestPage(id, title string) snippets.PageResponse {
	return snippets.NewPage(id, title).
		AddSnippet(snippets.NewHeaderSnippet("", atoms.NewText(title, theming.TypographyTitleLarge, theming.ColorTextPrimary)).Build()).
		Build()
}

func TestUpstreamCachedScreenRepository_SingleflightAndMetadata(t *testing.T) {
	ctx := context.Background()
	var calls atomic.Int32
	gate := make(chan struct{})

	repo := cache.NewUpstreamCachedScreenRepository(
		func(_ context.Context, screenID string) (snippets.PageResponse, error) {
			calls.Add(1)
			<-gate
			return makeTestPage(screenID, "Home "+screenID), nil
		},
		cache.WithTTL(2*time.Minute),
		cache.WithMaxEntries(16),
		cache.WithAppVersion("0.9.0"),
		cache.WithSchemaVersion("1.0"),
		cache.WithKnownScreens("home", "profile"),
	)

	const workers = 12
	var wg sync.WaitGroup
	pages := make([]snippets.PageResponse, workers)
	errs := make([]error, workers)
	for i := range workers {
		wg.Add(1)
		go func(idx int) {
			defer wg.Done()
			pages[idx], errs[idx] = repo.GetScreen(ctx, "home")
		}(i)
	}

	// Allow all workers to enter singleflight before releasing gate.
	time.Sleep(25 * time.Millisecond)
	close(gate)
	wg.Wait()

	if calls.Load() != 1 {
		t.Fatalf("expected singleflight to coalesce %d concurrent fetches into 1 call, got %d", workers, calls.Load())
	}
	for i := range workers {
		if errs[i] != nil {
			t.Fatalf("worker %d failed: %v", i, errs[i])
		}
		p := pages[i]
		if p.PageID != "home" || p.AppVersion != "0.9.0" || p.ETag == "" || p.PageVersion == "" {
			t.Fatalf("worker %d missing stamped metadata: %+v", i, p)
		}
		if len(p.Snippets) != 1 || p.Snippets[0].SnippetID != "home.snippet_0" {
			t.Fatalf("expected EnsureComponentIDs to stamp deterministic snippet ID, got %+v", p.Snippets)
		}
	}

	// Subsequent hit must not call fetcher again
	hit, err := repo.GetScreen(ctx, "home")
	if err != nil || hit.ETag != pages[0].ETag || calls.Load() != 1 {
		t.Fatalf("expected cache hit without refetch, calls=%d err=%v", calls.Load(), err)
	}
}

func TestUpstreamCachedScreenRepository_ScopesAndManifestO1AndInvalidations(t *testing.T) {
	now := time.Date(2026, 11, 25, 10, 0, 0, 0, time.UTC)
	var mu sync.Mutex
	clock := func() time.Time {
		mu.Lock()
		defer mu.Unlock()
		return now
	}
	advance := func(d time.Duration) {
		mu.Lock()
		now = now.Add(d)
		mu.Unlock()
	}

	var fetchCalls atomic.Int32
	versionTag := atomic.Int32{}
	versionTag.Store(1)

	repo := cache.NewUpstreamCachedScreenRepository(
		func(ctx context.Context, screenID string) (snippets.PageResponse, error) {
			fetchCalls.Add(1)
			tenant, _ := ctx.Value(tenantCtxKey{}).(string)
			return makeTestPage(screenID, fmt.Sprintf("%s-%s-v%d", tenant, screenID, versionTag.Load())), nil
		},
		cache.WithTTL(time.Minute),
		cache.WithAppVersion("0.9.0"),
		cache.WithClock(clock),
		cache.WithScopeKeyFunc(func(ctx context.Context) string {
			v, _ := ctx.Value(tenantCtxKey{}).(string)
			return v
		}),
	)

	repo.RegisterScreen("home", "core", "public")
	repo.RegisterScreen("pay-home", "fintech")
	repo.RegisterScreen("pay-cards", "fintech")
	repo.RegisterScreen("  ", "ignored")

	ctxA := context.WithValue(context.Background(), tenantCtxKey{}, "tenantA")
	ctxB := context.WithValue(context.Background(), tenantCtxKey{}, "tenantB")

	ids, err := repo.ListScreenIDs(ctxA)
	if err != nil || len(ids) != 3 || ids[0] != "home" || ids[1] != "pay-cards" || ids[2] != "pay-home" {
		t.Fatalf("unexpected ListScreenIDs: %v (err=%v)", ids, err)
	}

	// First BuildManifest fetches the 3 uncached screens for tenantA
	m1, err := repo.BuildManifest(ctxA, "")
	if err != nil {
		t.Fatalf("BuildManifest(tenantA) failed: %v", err)
	}
	if m1.AppVersion != "0.9.0" || m1.SduiSchemaVersion != snippets.DefaultSduiSchemaVersion || len(m1.Screens) != 3 {
		t.Fatalf("unexpected manifest m1: %+v", m1)
	}
	if fetchCalls.Load() != 3 {
		t.Fatalf("expected 3 initial fetches for manifest, got %d", fetchCalls.Load())
	}

	// Second BuildManifest is O(1) with ZERO fetches
	m2, err := repo.BuildManifest(ctxA, "")
	if err != nil || fetchCalls.Load() != 3 {
		t.Fatalf("expected O(1) BuildManifest without refetches, calls=%d err=%v", fetchCalls.Load(), err)
	}
	if m2.Screens["home"].ETag != m1.Screens["home"].ETag {
		t.Fatalf("manifest ETag mismatch on cached hit: %s vs %s", m2.Screens["home"].ETag, m1.Screens["home"].ETag)
	}

	// Separate scope (tenantB) gets distinct content and does not collide with tenantA
	mB, err := repo.BuildManifest(ctxB, "1.2.0")
	if err != nil {
		t.Fatalf("BuildManifest(tenantB) failed: %v", err)
	}
	if mB.AppVersion != "1.2.0" || mB.Screens["home"].ETag == m1.Screens["home"].ETag {
		t.Fatalf("expected tenantB distinct ETag & appVersion, got %+v", mB)
	}
	if fetchCalls.Load() != 6 {
		t.Fatalf("expected 6 total fetches across both tenants, got %d", fetchCalls.Load())
	}

	// Invalidate single screen bumps generation and refetches only that screen
	genBefore := repo.ManifestGeneration()
	versionTag.Store(2)
	repo.Invalidate("home")
	if repo.ManifestGeneration() <= genBefore {
		t.Fatal("expected Invalidate to increment ManifestGeneration")
	}
	m3, err := repo.BuildManifest(ctxA, "")
	if err != nil {
		t.Fatalf("BuildManifest after Invalidate(home): %v", err)
	}
	if fetchCalls.Load() != 7 {
		t.Fatalf("expected only 1 refetch after Invalidate(home), got total=%d", fetchCalls.Load())
	}
	if m3.Screens["home"].ETag == m1.Screens["home"].ETag {
		t.Fatal("expected updated ETag for invalidated home screen")
	}
	if m3.Screens["pay-home"].ETag != m1.Screens["pay-home"].ETag {
		t.Fatal("expected untouched pay-home screen to keep cached ETag")
	}

	// InvalidatePrefix("pay-") evicts pay-home and pay-cards only
	genBefore = repo.ManifestGeneration()
	versionTag.Store(3)
	repo.InvalidatePrefix("pay-")
	if repo.ManifestGeneration() <= genBefore {
		t.Fatal("expected InvalidatePrefix to increment ManifestGeneration")
	}
	m4, err := repo.BuildManifest(ctxA, "")
	if err != nil {
		t.Fatalf("BuildManifest after InvalidatePrefix: %v", err)
	}
	if fetchCalls.Load() != 9 {
		t.Fatalf("expected 2 refetches after InvalidatePrefix(pay-), got total=%d", fetchCalls.Load())
	}
	if m4.Screens["pay-home"].ETag == m3.Screens["pay-home"].ETag || m4.Screens["home"].ETag != m3.Screens["home"].ETag {
		t.Fatal("expected only pay-* screens invalidated")
	}

	// InvalidateTag("fintech") evicts pay-home and pay-cards again
	genBefore = repo.ManifestGeneration()
	versionTag.Store(4)
	repo.InvalidateTag("fintech")
	if repo.ManifestGeneration() <= genBefore {
		t.Fatal("expected InvalidateTag to increment ManifestGeneration")
	}
	if _, err := repo.BuildManifest(ctxA, ""); err != nil {
		t.Fatalf("BuildManifest after InvalidateTag: %v", err)
	}
	if fetchCalls.Load() != 11 {
		t.Fatalf("expected 2 refetches after InvalidateTag(fintech), got total=%d", fetchCalls.Load())
	}

	// InvalidateAll clears everything
	genBefore = repo.ManifestGeneration()
	repo.InvalidateAll()
	if repo.ManifestGeneration() <= genBefore {
		t.Fatal("expected InvalidateAll to increment ManifestGeneration")
	}
	if _, err := repo.BuildManifest(ctxA, ""); err != nil {
		t.Fatalf("BuildManifest after InvalidateAll: %v", err)
	}
	if fetchCalls.Load() != 14 {
		t.Fatalf("expected 3 refetches after InvalidateAll, got total=%d", fetchCalls.Load())
	}

	// TTL expiry causes refetch on next GetScreen / BuildManifest
	advance(2 * time.Minute)
	if _, err := repo.GetScreen(ctxA, "home"); err != nil {
		t.Fatalf("GetScreen after TTL expiry: %v", err)
	}
	if fetchCalls.Load() != 15 {
		t.Fatalf("expected refetch after TTL expiry, got %d", fetchCalls.Load())
	}

	// SetKnownScreens replaces registry cleanly
	repo.SetKnownScreens([]string{"home", "fresh-home"})
	ids2, err := repo.ListScreenIDs(ctxA)
	if err != nil || len(ids2) != 2 || ids2[0] != "fresh-home" || ids2[1] != "home" {
		t.Fatalf("unexpected ListScreenIDs after SetKnownScreens: %v (err=%v)", ids2, err)
	}
}

func TestUpstreamCachedScreenRepository_LRUBoundsAndErrors(t *testing.T) {
	ctx := context.Background()
	now := time.Now()
	clock := func() time.Time { return now }

	var calls atomic.Int32
	repo := cache.NewUpstreamCachedScreenRepository(
		func(_ context.Context, screenID string) (snippets.PageResponse, error) {
			calls.Add(1)
			if screenID == "fail" {
				return snippets.PageResponse{}, errors.New("upstream 503")
			}
			if screenID == "bad-marshal" {
				return snippets.PageResponse{
					PageID:   "bad-marshal",
					Snippets: []snippets.SnippetEnvelope{{SnippetID: "s1", Data: make(chan int)}},
				}, nil
			}
			return makeTestPage(screenID, "Title "+screenID), nil
		},
		cache.WithMaxEntries(2),
		cache.WithTTL(time.Minute),
		cache.WithClock(clock),
	)

	// Error cases: empty ID, nil fetcher, cancelled ctx, upstream error, unmarshalable page
	if _, err := repo.GetScreen(ctx, "   "); !errors.Is(err, cache.ErrEmptyScreenID) {
		t.Fatalf("expected ErrEmptyScreenID, got %v", err)
	}
	nilRepo := cache.NewUpstreamCachedScreenRepository(nil)
	if _, err := nilRepo.GetScreen(ctx, "home"); !errors.Is(err, cache.ErrNilFetcher) {
		t.Fatalf("expected ErrNilFetcher, got %v", err)
	}

	cancelledCtx, cancel := context.WithCancel(ctx)
	cancel()
	if _, err := repo.GetScreen(cancelledCtx, "home"); !errors.Is(err, context.Canceled) {
		t.Fatalf("expected context.Canceled on GetScreen, got %v", err)
	}
	if _, err := repo.ListScreenIDs(cancelledCtx); !errors.Is(err, context.Canceled) {
		t.Fatalf("expected context.Canceled on ListScreenIDs, got %v", err)
	}
	if _, err := repo.BuildManifest(cancelledCtx, ""); !errors.Is(err, context.Canceled) {
		t.Fatalf("expected context.Canceled on BuildManifest, got %v", err)
	}

	if _, err := repo.GetScreen(ctx, "fail"); err == nil {
		t.Fatal("expected upstream error")
	}
	if _, err := repo.GetScreen(ctx, "bad-marshal"); err == nil {
		t.Fatal("expected ETag computation error for unmarshalable page")
	}

	// Bounded LRU eviction with maxEntries=2:
	// Fill s1, s2; touch s1; insert s3 -> s2 evicted, s1 remains cached
	if _, err := repo.GetScreen(ctx, "s1"); err != nil {
		t.Fatalf("GetScreen(s1): %v", err)
	}
	if _, err := repo.GetScreen(ctx, "s2"); err != nil {
		t.Fatalf("GetScreen(s2): %v", err)
	}
	// ListScreenIDs with no knownScreens returns cached IDs in scope
	cachedIDs, err := repo.ListScreenIDs(ctx)
	if err != nil || len(cachedIDs) != 2 || cachedIDs[0] != "s1" || cachedIDs[1] != "s2" {
		t.Fatalf("expected cached IDs [s1, s2], got %v (err=%v)", cachedIDs, err)
	}

	// Touch s1 so s2 becomes LRU
	if _, err := repo.GetScreen(ctx, "s1"); err != nil {
		t.Fatalf("touch s1: %v", err)
	}
	callsBefore := calls.Load()
	if _, err := repo.GetScreen(ctx, "s3"); err != nil {
		t.Fatalf("GetScreen(s3): %v", err)
	}
	// s1 should still be a hit
	if _, err := repo.GetScreen(ctx, "s1"); err != nil {
		t.Fatalf("s1 should remain cached: %v", err)
	}
	if calls.Load() != callsBefore+1 {
		t.Fatalf("expected s1 hit after s3 insert, calls=%d want=%d", calls.Load(), callsBefore+1)
	}
	// s2 was evicted so fetching it causes a refetch
	if _, err := repo.GetScreen(ctx, "s2"); err != nil {
		t.Fatalf("GetScreen(s2) refetch: %v", err)
	}
	if calls.Load() != callsBefore+2 {
		t.Fatalf("expected s2 miss after LRU eviction, calls=%d want=%d", calls.Load(), callsBefore+2)
	}

	// BuildManifest when knownScreens > maxEntries still succeeds cleanly
	repo.SetKnownScreens([]string{"s1", "s2", "s3"})
	m, err := repo.BuildManifest(ctx, "0.9.0")
	if err != nil || len(m.Screens) != 3 {
		t.Fatalf("BuildManifest with knownScreens > maxEntries failed: %+v (err=%v)", m, err)
	}

	// Mid-flight caller cancellation unblocks caller immediately
	blockCh := make(chan struct{})
	slowRepo := cache.NewUpstreamCachedScreenRepository(func(_ context.Context, id string) (snippets.PageResponse, error) {
		<-blockCh
		return makeTestPage(id, "Slow"), nil
	})
	midCtx, midCancel := context.WithCancel(ctx)
	go func() {
		time.Sleep(15 * time.Millisecond)
		midCancel()
	}()
	if _, err := slowRepo.GetScreen(midCtx, "slow"); !errors.Is(err, context.Canceled) {
		t.Fatalf("expected context.Canceled on mid-flight cancel, got %v", err)
	}
	close(blockCh)
}
