---
name: bff-owner
module: "bff"
layer: "Go 1.26.0 SDUI BFF (Clean Architecture)"
owned_paths:
  - "bff/**"
allowed_dependencies:
  - "internal/handler -> internal/usecase -> internal/domain <- internal/infra; shared pkg/sdui/*"
  - "github.com/abeta-dev/go-libs, github.com/gin-gonic/gin, github.com/andybalholm/brotli"
forbidden_dependencies:
  - "internal/domain importing gin, net/http, infra, or handler packages (enforced by arch_test.go)"
required_skills:
  - "skills/abeta-kmp-bff/SKILL.md"
  - "skills/abeta-kmp-actions/SKILL.md"
  - "skills/abeta-kmp-module-review/SKILL.md"
verification_command: "(cd bff && test -z \"$(gofmt -l .)\" && go vet ./... && go test -count=1 -race -cover ./...)"
---

# Module Code Owner Agent: Go 1.26.0 SDUI BFF (`bff-owner`)

## Ownership Scope
- **Paths**: `bff/**` (`cmd/bff`, `internal/{domain,usecase,handler,infra}`, **8 exportable packages** under `pkg/sdui/{actions,analytics,atoms,cache,molecules,server,snippets,theming}`, `testdata/golden/`)
- **Core Responsibilities**: Clean Architecture Go SDUI BFF (`bff@v0.9.0`) serving **12 built-in screens** in `InMemoryScreenRepository` (`home`, `catalog`, `containers`, `profile`, `form-demo`, `fresh-home`, `store-home`, `watch-status`, `pay-home`, `pay-transfer`, `pay-cards`, `pay-receipt`), **18 actions**, `GET /api/v1/manifest` & `/api/v1/screens/manifest`, `GET /api/v1/streams/:streamId` (SSE w/ `Last-Event-ID` & `204` catch-up), `POST /api/v1/orders/express`, `POST /api/v1/pay/transfer`, `POST /api/v1/analytics/events` & `GET /api/v1/analytics/summary`, `/openapi.json` & `/docs` Scalar UI, plus reusable `pkg/sdui/cache` (`UpstreamCachedScreenRepository`) & `pkg/sdui/server` (fail-closed `CORSMiddleware`, `private, no-cache` `ETagMiddleware`, pooled `CompressionMiddleware`, per-router `JWTAuthMiddleware`, `HTTPUpstreamFetcher`, `StreamProvider`).

## Non-Negotiable Architecture & Review Gates
1. **Strict Go Clean Architecture**: `internal/domain` MUST remain pure Go with zero Gin/HTTP/infra imports (`internal/domain/arch_test.go`).
2. **1:1 KMP Wire Parity**: Every atom (**29** incl. `MediaPagerAtom`, `CountdownAtom.ExpiresAtEpochMs`, & 12 `extended_atoms.go`), snippet (**23** incl. `composed_block` with **36 `ComposedNodeKind`s**, `custom_slot`, & `graph_canvas`), **6 `SnippetDeltaOp`s**, action (**18**), `MotionSpec` (**7 Ambient `MotionLoopEffect`s**), `FloatingCartSpec` (free-delivery threshold nudge fields), `AdaptivePageSpec`, and `ginmw.Envelope` field (`bff@v0.9.0`) MUST match `kmp/sdui-contracts` and `kmp/action-engine` 1:1 and pass all **11 golden fixtures** (`home_screen.json`, `form_screen.json`, `action_batch.json`, `containers_screen.json`, `store_home.json`, `interactive_screen.json`, `nudges_and_composed_screen.json`, `extended_atoms_screen.json`, `express_molecules_and_analytics_screen.json`, `streaming_and_versioned_screen.json`, `spatial_custom_and_graph_screen.json`).
3. **SRP & KISS File Hygiene**: Keep each screen builder in its own `screen_<name>.go` file under `internal/infra/`, keep `pkg/sdui/snippets` builders partitioned by responsibility, and keep OpenAPI specs synchronized with live routes.
4. **Security & Performance**: Preserve CSP headers on `/docs`, fail-closed `CORS_ALLOWED_ORIGINS` & `TRUSTED_PROXIES`, fail-closed `AUTH_REQUIRED=true` / 32+ char `JWT_SECRET` per-router check, `ginmw.Envelope`, pooled compression, and `private, no-cache` ETag behavior.
5. **Quality Gate**: `gofmt -l .` empty, `go vet ./...` zero warnings, `go test -count=1 -race -cover ./...` passing with `>= 90%` statement coverage on implementation packages.
