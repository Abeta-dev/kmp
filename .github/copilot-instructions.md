# GitHub Copilot Instructions — `abeta-dev/kmp`

You are working on **Abeta KMP Kit (`abeta-dev/kmp`)**, a monorepo combining a 4-layer Kotlin Multiplatform + Compose Multiplatform SDUI, Wearable & Room 3 Database Kit (`kmp/`, **11** publishable modules incl. native `-android` `.aar` targets via AGP `9.5.3` + BOM + Version Catalog = **13** Maven Central artifacts at **`0.8.0`**) with a Go 1.26.0 Clean Architecture SDUI BFF (`bff/` — **`bff@v0.9.0`**).

Always follow [AGENTS.md](../AGENTS.md), the per-module Code Owner personas in `agents/owners/`, and the skills in `skills/`.

## 0. Mandatory Grounding Before Generation
Before generating or modifying code, follow this exact 6-step analysis order:
```text
AGENTS.md → Architecture Docs (docs/architecture.md) → Flow Docs (docs/flows/*.md) → Pattern Docs (docs/patterns/*.md) → CodeGraph Symbols (docs/codegraph/INDEX.md & symbols.json / ./scripts/generate_codegraph.py --query <symbol>) → Source Reads Only for Verification
```

## Core Architectural Invariants
- **Uncle Bob Dependency Rule**: `Layer 3 -> Layer 2 -> Layer 1 -> Layer 0`; Go `internal/handler -> internal/usecase -> internal/domain <- internal/infra`. `sdui-layout` stays Room-free by default (opt-in Room 3 via `DatabaseKeyValueStore` in `io.github.abeta-dev:database`).
- **Atomic / Action Separation**: `design-tokens`, `sdui-contracts`, and `ui-atoms` MUST NEVER import `core`, `action-engine`, `network`, or `sdui-layout`. UI atoms emit pure `ActionRef` callbacks; actions plug into `ActionDecoderRegistry` + `ActionRegistry` (OCP).
- **Headless Wearable**: `watch-companion` MUST remain 100% Compose-free.
- **BOM, Catalog & ABI**: Declare all dependency versions in `kmp/dependencyManagement/gradle/{libs,internal}.versions.toml`. Keep BCV `.api` baselines (`api/jvm/<module>.api`) synced across all **11** publishable modules.
- **1:1 Wire Parity**: `kmp/sdui-contracts` + `kmp/action-engine` (`29` wire atoms, `31` UI atoms, `23` snippets incl. `composed_block` w/ `36` `ComposedNodeKind`s, `custom_slot`, & `graph_canvas`, `6` `SnippetDeltaOp`s, `18` actions, `7` motion loops) MUST match `bff/pkg/sdui/*` (`bff@v0.9.0`) and all **11** golden fixtures under `bff/testdata/golden/`.
- **SOLID, Clean Code & KISS**: Keep files cohesive (`<= 750 LOC`), avoid duplicated logic, honor `Detekt`, `Spotless`, `gofmt`, `go vet`, and native iOS/Android craft (48dp touch targets, safe-area/IME insets, dark mode, reduced motion, accessible roles & live regions).
- **Brand & Path Hygiene**: Use `io.github.abeta-dev` (Maven) / `dev.abeta.kmp` (Kotlin packages) / `github.com/abeta-dev/kmp` (Go) only; zero legacy brand names or local absolute paths.

## Mandatory Verification
```bash
./scripts/check_brand_hygiene.sh
./scripts/check_architecture_and_owners.sh
./scripts/generate_codegraph.py --check
./scripts/verify_all.sh
```
