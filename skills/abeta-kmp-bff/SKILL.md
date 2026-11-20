---
name: abeta-kmp-bff
description: Build and test Go 1.26.0 SDUI BFF screens, snippets, handlers, and v1 wire schemas in bff/ using Clean Architecture (cmd/bff, internal/{domain,usecase,handler,infra}), pkg/sdui/*, and github.com/abeta-dev/go-libs@v0.4.0. Use when modifying the Go backend or adding SDUI endpoints.
---

# Abeta Go SDUI BFF (`bff/`)

Use this skill when developing or reviewing Go 1.26.0 code under `bff/`.

## Clean Architecture Boundaries

```text
bff/
├── cmd/bff/             # Entrypoint (main.go), go-libs/env config, http.Server.Protocols (HTTP/1.1 + h2c + TLS HTTP/2), graceful shutdown
├── internal/domain/     # Pure ports (ScreenRepository, ActionExecutor, TokenCatalogProvider) + arch_test.go
├── internal/usecase/    # ScreenService, ActionService, CatalogService
├── internal/handler/    # Gin router (router.go), CompressionMiddleware (compression.go), ETagMiddleware (caching.go), OpenAPI 3.1/Scalar (openapi.go)
├── internal/infra/      # InMemoryScreenRepository (7 screens), DefaultActionExecutor (18 actions), StaticTokenCatalogProvider, golden_test.go
├── pkg/sdui/            # actions (18 types incl. open_url, share, request_permission, biometric_auth, trigger_feedback), atoms (15 wire atoms incl. StepperAtom + MotionSpec), snippets (14 types incl. product_card + ResponsiveColumnsSpec/ResponsiveIntSpec/AdaptivePageSpec/ScreenLayoutMode + FloatingCartSpec + PaginationSpec), theming
└── testdata/golden/     # home_screen.json, form_screen.json, action_batch.json, containers_screen.json, store_home.json
```

- `internal/domain`: **Zero** HTTP/Gin imports (guarded by `arch_test.go`).
- `internal/usecase`: Validates inputs (`ActionService` enforces `ValidationRule` against `FormValues`) and calls `domain` ports.
- `internal/infra`: Concrete adapters (`InMemoryScreenRepository` serving **7 screens**: `"catalog"`, `"containers"`, `"form-demo"`, `"home"`, `"profile"`, `"store-home"` with `columns_by_width` + `adaptive_layout`, and `"watch-status"`, plus `DefaultActionExecutor` handling all **18 Action Types** & `POST /api/v1/orders/express` returning `celebration` + `show_toast` follow-ups).
- `internal/handler`: Gin engine with `ginmw` middlewares and custom production handlers:
  - `CompressionMiddleware()`: Negotiated `br` (via `github.com/andybalholm/brotli`) + `gzip`, `Vary: Accept-Encoding`, weakens strong `ETag`s (`COMPRESSION_ENABLED=true`).
  - `ETagMiddleware()`: SHA-256 `ETag` + `Cache-Control: public, max-age=30` + `304 Not Modified` on `If-None-Match` (`ETAG_ENABLED=true`).
  - Optional `ginmw.GlobalRateLimit()` (`RATE_LIMIT_ENABLED=true`) and `ginmw.AuthMiddleware()` (`AUTH_REQUIRED=true` + `JWT_SECRET`).
  - Routes (`router.go`, documented in `openapi.go`):
    - `GET /healthz`, `GET /readyz`, `GET /metrics`
    - `GET /openapi.json` (OpenAPI 3.1), `GET /docs` (Scalar API Reference UI)
    - `GET /api/v1/screens`, `GET /api/v1/screens/:screenId`, `POST /api/v1/actions/execute`, `POST /api/v1/orders/express`, `GET /api/v1/catalog/tokens` (all `/api/v1/*` respond with `ginmw.Envelope`)

## SDUI Authoring Rules

1. Use `pkg/sdui/{actions,atoms,snippets,theming}` typed constructors—never raw `map[string]any` for standard contracts:
   - **15 Wire Atoms** incl. `StepperAtom` + `MotionSpec` on `ButtonAtom`, `TagAtom`, `SnippetEnvelope`, `BottomBarSpec`, and `FloatingCartSpec`.
   - **14 Standard Snippets** (incl. `product_card` / `ProductCardSnippetData`, `tabs` with `TabItemSpec.Title` as `string`, `grid` with `ColumnsByWidth`, `carousel` with `ItemWidthByWidth`), `AdaptivePageSpec` (`ScreenLayoutMode`: `"single_column"`, `"centered"`, `"split_pane"`), `FloatingCartSpec`, `PaginationSpec`, `PageResponse.PullToRefresh`, and **18 Action Types** (`open_url` / `OpenURLPayload`, `share` / `SharePayload`, `request_permission` / `RequestPermissionPayload`, `biometric_auth` / `BiometricAuthPayload`, `trigger_feedback` / `TriggerFeedbackPayload`).
2. Keep `snake_case` JSON tags 1:1 with `kmp/sdui-contracts` and `kmp/action-engine`.
3. Add or update golden parity tests (`bff/testdata/golden/{home_screen,form_screen,action_batch,containers_screen,store_home}.json` + `bff/internal/infra/golden_test.go`).

## Verification Commands

```bash
cd bff
gofmt -l .
go vet ./...
go test -race -cover ./...
```
