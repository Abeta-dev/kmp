# Go BFF Guide

The `bff/` service is a **Go 1.26.0** Server-Driven UI Backend-for-Frontend (`github.com/abeta-dev/kmp/bff@v0.9.0`, `./cmd/bff`) built with **Clean Architecture**, **Gin `v1.12.0`**, and **`github.com/abeta-dev/go-libs@v0.4.0`**.

---

## Directory Structure

```text
bff/
├── cmd/bff/             # Entrypoint (main.go), go-libs/env config, http.Server.Protocols (HTTP/1.1 + h2c + TLS 1.2+ HTTP/2), graceful shutdown
├── internal/domain/     # Pure ports (ScreenRepository, ActionExecutor, TokenCatalogProvider, AnalyticsStore) + arch_test.go
├── internal/usecase/    # ScreenService, ActionService, CatalogService, AnalyticsService, stream delta builders
├── internal/handler/    # Gin router (router.go), SSE stream handler (stream.go), pkg/sdui/server middleware wiring, OpenAPI 3.1/Scalar (openapi.go)
├── internal/infra/      # InMemoryScreenRepository (12 built-in screens), DefaultActionExecutor (18 actions), StaticTokenCatalogProvider, InMemoryAnalyticsStore, 11 golden tests
├── pkg/sdui/            # 8 exportable packages (bff@v0.9.0): actions (18 types), analytics, atoms (29 wire atoms incl. MediaPagerAtom & CountdownAtom.ExpiresAtEpochMs + 7 loops), cache (UpstreamCachedScreenRepository), molecules, server (fail-closed CORS, private/no-cache ETag, pooled Compression, per-router JWT, HTTPUpstreamFetcher, StreamProvider), snippets (23 types incl. composed_block w/ 36 ComposedNodeKinds, custom_slot, graph_canvas, 6 SnippetDeltaOps), theming
└── testdata/golden/     # 11 fixtures: home_screen.json, form_screen.json, action_batch.json, containers_screen.json, store_home.json, interactive_screen.json, nudges_and_composed_screen.json, extended_atoms_screen.json, express_molecules_and_analytics_screen.json, streaming_and_versioned_screen.json, spatial_custom_and_graph_screen.json
```

---

## Endpoints & `ginmw.Envelope` (`bff/internal/handler/router.go`)

| Method | Path | Description |
| :--- | :--- | :--- |
| `GET` | `/healthz` | Liveness probe (`health.LivenessHandler`) |
| `GET` | `/readyz` | Readiness probe (`screen_repository` check) |
| `GET` | `/metrics` | RED metrics endpoint (`ginmw.MetricsEndpoint`, Prometheus text or `?format=json`) |
| `GET` | `/openapi.json` | OpenAPI `3.1.0` JSON specification (`openapi.go`) |
| `GET` | `/docs` | Interactive Scalar API Reference UI (`@scalar/api-reference`) |
| `GET` | `/api/v1/screens` | Lists all **12 built-in screens** (`catalog`, `containers`, `form-demo`, `fresh-home`, `home`, `pay-cards`, `pay-home`, `pay-receipt`, `pay-transfer`, `profile`, `store-home`, `watch-status`) with ETag/304 |
| `GET` | `/api/v1/manifest` | Returns `ScreenManifestResponse` (SHA-256 screen versions, `app_version`, `sdui_schema_version`) with ETag/304 |
| `GET` | `/api/v1/screens/manifest` | Canonical manifest route returning `ScreenManifestResponse` with ETag/304 |
| `GET` | `/api/v1/screens/:screenId` | Returns `snippets.PageResponse` wrapped in `ginmw.Envelope` with ETag/304 |
| `GET` | `/api/v1/streams/:streamId` | Uncompressed SSE delta stream (`Last-Event-ID` replay & `204` catch-up) |
| `POST` | `/api/v1/actions/execute` | Validates & executes an `actions.ActionTrigger` across all **18 Action Types** |
| `POST` | `/api/v1/orders/express` | Places an **Abeta Fresh** 8-Min Express order returning `trigger_feedback` (`celebration`) + `show_toast` follow-ups |
| `POST` | `/api/v1/pay/transfer` | Executes an **Abeta Pay** P2P transfer with settlement receipt follow-ups |
| `GET` | `/api/v1/catalog/tokens` | Returns `domain.TokenCatalog` with ETag/304 |
| `POST` | `/api/v1/analytics/events` | Ingests batched SDUI client analytics events |
| `GET` | `/api/v1/analytics/summary` | Returns aggregated analytics telemetry summary |

All `/api/v1/*` JSON responses are wrapped in `ginmw.Envelope`:

```json
{
  "success": true,
  "data": { ... }
}
```

---

## Production Middleware & Transport Features (`pkg/sdui/server` & `pkg/sdui/cache`)

- **Pooled Compression (`server.CompressionMiddleware()`)**: Negotiates `br` (`github.com/andybalholm/brotli`) or `gzip` using `sync.Pool`, sets `Vary: Accept-Encoding`, weakens strong `ETag`s to `W/"..."`, and bypasses `text/event-stream` (SSE), `/metrics`, `HEAD`, no-body status codes, and oversized payloads (`COMPRESSION_ENABLED=true`).
- **Version-Aware ETag Caching (`server.ETagMiddleware()`)**: Defaults to `Cache-Control: private, no-cache`, honors `page.ETag` and `X-App-Version`/`X-Page-Version`/`X-Client-App-Version`, and returns `304 Not Modified` on `If-None-Match` (`ETAG_ENABLED=true`).
- **Fail-Closed CORS & Per-Router JWT (`server.CORSMiddleware`, `server.JWTAuthMiddleware`)**: `CORS_ALLOWED_ORIGINS` and `TRUSTED_PROXIES` default to empty (fail-closed); `AUTH_REQUIRED=true` requires a 32+ character `JWT_SECRET` protecting `/api/v1/*` per router without mutating global `ginmw` state.
- **Upstream Caching (`cache.UpstreamCachedScreenRepository`)**: `singleflight` fetch coalescing, bounded LRU + TTL, prefix/tag/all invalidation, and O(1) `BuildManifest`.
- **HTTP/2 (`h2c` & TLS 1.2+)**: `http.Server.Protocols` enables HTTP/1.1, unencrypted HTTP/2 (`h2c`), and TLS 1.2+ HTTP/2 when `TLS_CERT_FILE` and `TLS_KEY_FILE` are provided.
- **Optional Global Rate Limit**: `ginmw.GlobalRateLimit()` enabled via `RATE_LIMIT_ENABLED=true` (default `false`).

---

## Commands

```bash
cd bff
go vet ./...
go test -race -cover ./...
go run ./cmd/bff
```
