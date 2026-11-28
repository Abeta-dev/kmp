# JetBrains Junie & Android Studio AI Guidelines (`abeta-dev/kmp`)

This repository contains:
- **`kmp/`**: Gradle 9.7.0 / JDK 21 / Kotlin 2.4.21 / Compose Multiplatform 1.12.2 / AGP `9.5.3` SDUI, Wearable & Room 3 Database Kit (**11 publishable library modules** with native `-android` `.aar` targets + `kmp-bom` + `dependencyManagement` = **13 Maven Central artifacts** at **`0.8.0`** + `storybook` + `sample-app` + `platforms/`).
- **`bff/`**: Go 1.26.0 Clean Architecture SDUI Backend-for-Frontend (`cmd/bff`, `internal/{domain,usecase,handler,infra}`, 8 exportable `pkg/sdui/*` packages at **`bff@v0.9.0`**).

## 0. Mandatory Grounding Before Generation
Always follow this exact 6-step order before planning or editing code:
```text
AGENTS.md → Architecture Docs (docs/architecture.md) → Flow Docs (docs/flows/*.md) → Pattern Docs (docs/patterns/*.md) → CodeGraph Symbols (docs/codegraph/INDEX.md & symbols.json / ./scripts/generate_codegraph.py --query <symbol>) → Source Reads Only for Verification
```

## Opening in Android Studio / IntelliJ IDEA
1. Open the **`kmp/`** directory as a Gradle project (or the repo root if editing both `kmp/` and `bff/`).
2. Configure **Gradle JDK = 21** (`Settings -> Build, Execution, Deployment -> Build Tools -> Gradle`) and Android SDK **API 37** (`compileSdk = 37`, `minSdk = 24`).
3. Keep `abeta.kmpLocalBuild=true` in `kmp/gradle.properties` for composite local module development; switch to `-Pabeta.kmpLocalBuild=false` when testing Maven BOM resolution.
4. All **11 publishable KMP modules** ship native `-android` `.aar` artifacts directly; call `AbetaAndroidContext.initialize(applicationContext)` once at app startup (see reference host glue under `kmp/platforms/android/` and `kmp/platforms/wearos/`).

## Canonical Rules & Owners
- Full contract: [AGENTS.md](../AGENTS.md)
- Module Code Owner personas: [agents/owners/](../agents/owners/) (17 per-module owners matching `.github/CODEOWNERS`)
- Skills: [skills/](../skills/) (`abeta-kmp-kit`, `abeta-kmp-usage`, `abeta-kmp-demo`, `abeta-kmp-platforms`, `abeta-kmp-atomic`, `abeta-kmp-actions`, `abeta-kmp-bff`, `abeta-kmp-architecture`, `abeta-kmp-module-review`, `abeta-kmp-cicd`)

## Non-Negotiable Architecture Checks
- **Dependency Rule**: `L3 -> L2 -> L1 -> L0`; Layer 1 (`design-tokens`, `sdui-contracts`, `ui-atoms`) NEVER imports `core`, `action-engine`, `network`, or `sdui-layout`; `sdui-layout` stays Room-free by default (opt-in Room 3 via `DatabaseKeyValueStore`); `watch-companion` NEVER imports Compose UI; `bff/internal/domain` NEVER imports Gin/`net/http`.
- **SOLID, Clean Code & KISS**: Avoid monolithic files (`<= 750 LOC` production target), use OCP registries (`ActionRegistry`, `SnippetRegistry`), and inject dependencies via constructors/Koin.
- **Formatting, Lint, ABI & CodeGraph**: Run `(cd kmp && ./gradlew spotlessApply detekt apiCheck)` (or `apiDump` on intentional public API changes) and `./scripts/generate_codegraph.py --check`. Never hardcode dependency versions inside module `build.gradle.kts`.

## Verification Commands
```bash
./scripts/check_brand_hygiene.sh
./scripts/check_architecture_and_owners.sh
./scripts/generate_codegraph.py --check
./scripts/verify_all.sh
```
