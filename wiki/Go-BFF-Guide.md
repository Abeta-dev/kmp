# Go BFF Guide

The `bff/` service is a **Go 1.26.0** Server-Driven UI Backend-for-Frontend (`github.com/abeta-dev/kmp/bff@v0.5.0`, `./cmd/bff`) built with **Clean Architecture**, **Gin `v1.12.0`**, and **`github.com/abeta-dev/go-libs@v0.4.0`**.

---

## Directory Structure

```text
bff/
├── cmd/bff/             # Entrypoint (main.go), go-libs/env config, http.Server.Protocols (HTTP/1.1 + h2c + TLS HTTP/2), graceful shutdown
├── internal/domain/     # Pure ports (ScreenRepository, ActionExecutor, TokenCatalogProvider) + arch_test.go
├── internal/usecase/    # ScreenService, ActionService, CatalogService
├── internal/handler/    # Gin router (router.go), CompressionMiddleware (compression.go), ETagMiddleware (caching.go), OpenAPI 3.1/Scalar (openapi.go)
├── internal/infra/      # InMemoryScreenRepository (core + Abeta Pay screens), DefaultActionExecutor (18 actions), StaticTokenCatalogProvider, 8 golden tests
├── pkg/sdui/            # actions (18 types), atoms (28 wire atoms incl. CountdownAtom & 12 extended_atoms.go primitives + MotionSpec w/ 7 loops), snippets (21 types incl. swipe_deck, search_bar, chip_group, nudge_bar, marquee_strip, order_tracker, composed_block w/ 29 ComposedNodeKinds + FloatingCartSpec threshold nudges + AdaptivePageSpec), theming
└── testdata/golden/     # 8 fixtures: home_screen.json, form_screen.json, action_batch.json, containers_screen.json, store_home.json, interactive_screen.json, nudges_and_composed_screen.json, extended_atoms_screen.json
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
| `GET` | `/api/v1/screens` | Lists available screen IDs (`catalog`, `containers`, `form-demo`, `home`, `pay-cards`, `pay-home`, `pay-receipt`, `pay-transfer`, `profile`, `store-home`, `watch-status`) with ETag/304 |
| `GET` | `/api/v1/screens/:screenId` | Returns `snippets.PageResponse` (including `adaptive_layout`, responsive `columns_by_width` on `store-home`, and `watch-status`) wrapped in `ginmw.Envelope` with ETag/304 |
| `POST` | `/api/v1/actions/execute` | Validates & executes an `actions.ActionTrigger` across all **18 Action Types** |
| `POST` | `/api/v1/orders/express` | Places an **Abeta Fresh** 8-Min Express order returning `trigger_feedback` (`celebration`) + `show_toast` follow-ups (`action_executor.go`) |
| `GET` | `/api/v1/catalog/tokens` | Returns `domain.TokenCatalog` with ETag/304 |

All `/api/v1/*` responses are wrapped in `ginmw.Envelope`:

```json
{
  "success": true,
  "data": { ... }
}
```

---

## Production Middleware & Transport Features

- **Negotiated Compression (`CompressionMiddleware()`)**: Compresses responses with `br` (`github.com/andybalholm/brotli`) or `gzip` based on `Accept-Encoding`, sets `Vary: Accept-Encoding`, weakens strong `ETag`s to `W/"..."`, and skips `/metrics`, `HEAD`, and no-body status codes (`COMPRESSION_ENABLED=true` by default).
- **SHA-256 ETag Caching (`ETagMiddleware()`)**: Sets `ETag` and `Cache-Control: public, max-age=30` on `GET /api/v1/screens`, `/api/v1/screens/:screenId`, and `/api/v1/catalog/tokens`, honoring `If-None-Match` with `304 Not Modified` (`ETAG_ENABLED=true` by default).
- **HTTP/2 (`h2c` & TLS)**: `http.Server.Protocols` enables HTTP/1.1, unencrypted HTTP/2 (`h2c`), and TLS HTTP/2 when `TLS_CERT_FILE` and `TLS_KEY_FILE` are provided.
- **Optional Global Rate Limit**: `ginmw.GlobalRateLimit()` enabled via `RATE_LIMIT_ENABLED=true` (default `false`).
- **Optional JWT Auth**: `ginmw.AuthMiddleware()` enabled via `AUTH_REQUIRED=true` (requires non-empty `JWT_SECRET`) protecting `/api/v1/*` while leaving `/healthz`, `/readyz`, `/metrics`, `/openapi.json`, and `/docs` open.

---

## Commands

```bash
cd bff
go vet ./...
go test -race -cover ./...
go run ./cmd/bff
```
