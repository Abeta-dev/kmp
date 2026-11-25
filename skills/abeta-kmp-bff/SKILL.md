---
name: abeta-kmp-bff
description: Build and test Go 1.26.0 SDUI BFF screens, snippets, handlers, and v1 wire schemas in bff/ using Clean Architecture (cmd/bff, internal/{domain,usecase,handler,infra}), pkg/sdui/*, and github.com/abeta-dev/go-libs@v0.4.0. Use when modifying the Go backend or adding SDUI endpoints.
---

# Abeta Go SDUI BFF (`bff/`)

Use this skill when developing or reviewing Go 1.26.0 code under `bff/`.

## Clean Architecture Boundaries

```text
bff/
├── cmd/bff/             # Entrypoint (main.go), go-libs/env config, http.Server.Protocols (HTTP/1.1 + h2c + TLS 1.2+ HTTP/2), graceful shutdown
├── internal/domain/     # Pure ports (ScreenRepository, ActionExecutor, TokenCatalogProvider, AnalyticsStore) + arch_test.go
├── internal/usecase/    # ScreenService, ActionService, CatalogService, AnalyticsService, stream delta builders
├── internal/handler/    # Gin router (router.go), SSE stream handler (stream.go), pkg/sdui/server middleware wiring, OpenAPI 3.1/Scalar (openapi.go)
├── internal/infra/      # InMemoryScreenRepository (12 built-in screens), DefaultActionExecutor (18 actions), StaticTokenCatalogProvider, InMemoryAnalyticsStore, 11 golden tests
├── pkg/sdui/            # bff@v0.9.0 (8 exportable packages): actions (18 types), analytics, atoms (29 wire atoms incl. MediaPagerAtom & CountdownAtom.ExpiresAtEpochMs + 7 loops), cache (UpstreamCachedScreenRepository w/ singleflight & O(1) BuildManifest), molecules, server (fail-closed CORSMiddleware, private/no-cache ETagMiddleware, pooled CompressionMiddleware, per-router JWTAuthMiddleware, HTTPUpstreamFetcher, StreamProvider), snippets (23 types incl. composed_block w/ 36 ComposedNodeKinds, custom_slot, graph_canvas, 6 SnippetDeltaOps), theming
└── testdata/golden/     # 11 fixtures: home_screen.json, form_screen.json, action_batch.json, containers_screen.json, store_home.json, interactive_screen.json, nudges_and_composed_screen.json, extended_atoms_screen.json, express_molecules_and_analytics_screen.json, streaming_and_versioned_screen.json, spatial_custom_and_graph_screen.json
```

- `internal/domain`: **Zero** HTTP/Gin imports (guarded by `arch_test.go`).
- `internal/usecase`: Validates inputs (`ActionService` enforces `ValidationRule` against `FormValues`), builds manifests & SSE delta patches, and calls `domain` ports.
- `internal/infra`: Concrete adapters (`InMemoryScreenRepository` serving **12 built-in screens**: `home`, `catalog`, `containers`, `profile`, `form-demo`, `fresh-home`, `store-home`, `watch-status`, `pay-home`, `pay-transfer`, `pay-cards`, `pay-receipt`; `DefaultActionExecutor` handling all **18 Action Types**, `POST /api/v1/orders/express`, & `POST /api/v1/pay/transfer`; `StaticTokenCatalogProvider`; `InMemoryAnalyticsStore`).
- `internal/handler`: Gin engine with `pkg/sdui/server` + `ginmw` middlewares:
  - `server.CompressionMiddleware()`: Pooled `br` (`github.com/andybalholm/brotli`) + `gzip`, `Vary: Accept-Encoding`, weakens strong `ETag`s, bypasses SSE (`COMPRESSION_ENABLED=true`).
  - `server.ETagMiddleware()`: Defaults to `Cache-Control: private, no-cache`, honors `page.ETag` and `X-App-Version`/`X-Page-Version`/`X-Client-App-Version`, returns `304 Not Modified` on `If-None-Match` (`ETAG_ENABLED=true`).
  - Fail-closed `server.CORSMiddleware` (`CORS_ALLOWED_ORIGINS` empty by default), optional `ginmw.GlobalRateLimit()` (`RATE_LIMIT_ENABLED=true`), and per-router `server.JWTAuthMiddleware` (`AUTH_REQUIRED=true` + 32+ char `JWT_SECRET`).
  - Routes (`router.go`, documented in `openapi.go`):
    - `GET /healthz`, `GET /readyz`, `GET /metrics`, `GET /openapi.json`, `GET /docs`
    - `GET /api/v1/screens`, `GET /api/v1/manifest`, `GET /api/v1/screens/manifest`, `GET /api/v1/screens/:screenId`, `GET /api/v1/streams/:streamId` (SSE w/ `Last-Event-ID` & `204` catch-up), `POST /api/v1/actions/execute`, `POST /api/v1/orders/express`, `POST /api/v1/pay/transfer`, `GET /api/v1/catalog/tokens`, `POST /api/v1/analytics/events`, `GET /api/v1/analytics/summary`

## SDUI Authoring Rules

1. Use `pkg/sdui/{actions,analytics,atoms,cache,molecules,server,snippets,theming}` (`bff@v0.9.0`) typed constructors—never raw `map[string]any` for standard contracts:
   - **29 Wire Atoms** incl. `StepperAtom`, `CountdownAtom` (`ExpiresAtEpochMs`), `MediaPagerAtom`, & 12 `extended_atoms.go` primitives + `MotionSpec` (**7 Ambient `MotionLoopEffect`s**).
   - **23 Canonical Snippets** (incl. `product_card`, `swipe_deck`, `search_bar`, `chip_group`, `nudge_bar`, `marquee_strip`, `order_tracker`, **`composed_block`** with **36 `ComposedNodeKind`s**, `custom_slot`, & `graph_canvas`), **6 `SnippetDeltaOp`s**, `AdaptivePageSpec`, `FloatingCartSpec` (with free-delivery threshold nudges), `PaginationSpec`, `PageResponse.PullToRefresh`, and **18 Action Types**.
2. Keep `snake_case` JSON tags 1:1 with `kmp/sdui-contracts` and `kmp/action-engine` (and serialize string-only values in `tracking.properties`).
3. Add or update all **11 golden parity fixtures** (`bff/testdata/golden/{home_screen,form_screen,action_batch,containers_screen,store_home,interactive_screen,nudges_and_composed_screen,extended_atoms_screen,express_molecules_and_analytics_screen,streaming_and_versioned_screen,spatial_custom_and_graph_screen}.json` + `bff/internal/infra/golden*_test.go`).

## Verification Commands

```bash
cd bff
gofmt -l .
go vet ./...
go test -race -cover ./...
```
