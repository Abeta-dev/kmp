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
- **Paths**: `bff/**` (`cmd/bff`, `internal/{domain,usecase,handler,infra}`, `pkg/sdui/{actions,atoms,snippets,theming}`, `testdata/golden/`)
- **Core Responsibilities**: Clean Architecture Go SDUI BFF serving core screens (`home`, `store-home`, `catalog`, `profile`, `form-demo`, `containers`, `watch-status`) plus the **7-in-1 Super-App Showcase** (`fresh-home` **Abeta Fresh**, `bites-home` **Abeta Bites**, `pulse-home` **Abeta Pulse**, `mart-home` **Abeta Mart**, `spark-deck` **Abeta Spark**, `trade-home` **Abeta Trade**, `pay-home` **Abeta Pay**), **18 actions**, `POST /api/v1/orders/express`, `/openapi.json` & `/docs` Scalar UI, Brotli/gzip compression, SHA-256 `ETag`/`304`, HTTP/1.1 + `h2c` + TLS HTTP/2, optional rate limiting & fail-closed JWT auth.

## Non-Negotiable Architecture & Review Gates
1. **Strict Go Clean Architecture**: `internal/domain` MUST remain pure Go with zero Gin/HTTP/infra imports (`internal/domain/arch_test.go`).
2. **1:1 KMP Wire Parity**: Every atom (**28** incl. `extended_atoms.go`), snippet (**21** incl. `swipe_deck`, `search_bar`, `chip_group`, `nudge_bar`, `marquee_strip`, `order_tracker`, and **`composed_block`** with **29 `ComposedNodeKind`s** + server-molded recipe helpers), action (**18**), `MotionSpec` (**7 Ambient `MotionLoopEffect`s**), `FloatingCartSpec` (free-delivery threshold nudge fields), `AdaptivePageSpec`, and `ginmw.Envelope` field (`bff@v0.5.0`) MUST match `kmp/sdui-contracts` and `kmp/action-engine` 1:1 and pass all **8 golden fixtures** (`home_screen.json`, `form_screen.json`, `action_batch.json`, `containers_screen.json`, `store_home.json`, `interactive_screen.json`, `nudges_and_composed_screen.json`, `extended_atoms_screen.json`).
3. **SRP & KISS File Hygiene**: Keep each screen builder in its own `screen_<name>.go` file under `internal/infra/`, keep `pkg/sdui/snippets` builders partitioned by responsibility, and keep OpenAPI specs synchronized with live routes.
4. **Security & Performance**: Preserve CSP headers on `/docs`, fail-closed `AUTH_REQUIRED=true` / `JWT_SECRET` check, `ginmw.Envelope`, compression, and ETag behavior.
5. **Quality Gate**: `gofmt -l .` empty, `go vet ./...` zero warnings, `go test -count=1 -race -cover ./...` passing with `>= 90%` statement coverage on implementation packages.
