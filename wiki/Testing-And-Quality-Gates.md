# Testing & Quality Gates

Every pull request and release in `abeta-dev/kmp` is guarded by automated formatting, static analysis, ABI checks, Compose UI semantic tests, unit/golden parity tests, and brand/path hygiene checks.

---

## Verification Matrix

| Gate | Scope | Command |
| :--- | :--- | :--- |
| **Brand & Path Hygiene** | Entire repo | `./scripts/check_brand_hygiene.sh` |
| **Architecture, Wire Parity & Module Owners** | Uncle Bob Dependency Rule, BOM/Catalog/ABI, 18 actions / 23 snippets parity, 17 Code Owners, SOLID/KISS LOC check | `./scripts/check_architecture_and_owners.sh` |
| **CodeGraph Symbol Freshness** | Deterministic Kotlin, Go & Swift symbol index (`docs/codegraph/{INDEX.md,symbols.json}`) backing Step 5 of Mandatory Grounding | `./scripts/generate_codegraph.py --check` (refresh via `./scripts/generate_codegraph.py`) |
| **Spotless (`ktlint`)** | `kmp/` Kotlin & `.gradle.kts` | `(cd kmp && ./gradlew spotlessCheck)` |
| **Detekt** | `kmp/` static analysis | `(cd kmp && ./gradlew detekt)` |
| **Binary Compatibility Validator** | Public ABI of **11 publishable KMP modules** (`kmp/<mod>/api/jvm/<mod>.api`, incl. `database` & `watch-companion`) | `(cd kmp && ./gradlew apiCheck)` (update via `apiDump`) |
| **KMP Full Check** | `kmp/` compile (incl. native `-android` `.aar` SDK 37 targets), Spotless, Detekt, `apiCheck`, unit, golden & `compose.uiTest` semantic tests (`UiAtomsComposeTest`, `ExtendedAtomsComposeTest`, `SduiScreenComposeTest`, `StorybookComposeTest`, `WatchCompanionControllerTest`) | `(cd kmp && ./gradlew check)` |
| **Dokka v2 HTML Docs** | Module or project API documentation | `(cd kmp && ./gradlew :core:dokkaGenerateHtml)` / `(cd kmp && ./gradlew dokkaGenerateHtml)` |
| **Sample App Headless** | End-to-end KMP headless verification across core screens and all **7-in-1 Super-App Showcase** verticals | `(cd kmp && ./gradlew :sample-app:run --args="--headless")` & `--args="--headless --verticals"` |
| **Storybook Desktop & WasmJS** | **20-story** interactive component (all 31 UI atoms), All 23 Snippets, motion, quick-commerce, Live 7-in-1 Super-App Demos, responsive breakpoint, Voice & Desktop OS bridge, Snippet Crafter & token explorer | `(cd kmp && ./gradlew :storybook:run)` or `:storybook:wasmJsBrowserDevelopmentRun` |
| **Apple XCFrameworks** | `AbetaSduiKit` (`iosArm64`, `iosSimulatorArm64`), `AbetaWatchKit` (`watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`), and `AbetaStorybookKit` (`iosArm64`, `iosSimulatorArm64`) | `(cd kmp && ./gradlew :sdui-layout:assembleAbetaSduiKitXCFramework :watch-companion:assembleAbetaWatchKitXCFramework :storybook:assembleAbetaStorybookKitXCFramework)` |
| **Go Vet** | `bff/` static checks | `(cd bff && go vet ./...)` |
| **Go Race & Coverage Tests** | `bff/` unit, arch, middleware, & golden JSON parity tests | `(cd bff && go test -race -cover ./...)` |
| **All-in-One Gate** | Hygiene + KMP + `--headless` + Go BFF | `./scripts/verify_all.sh` |

---

## CI Workflows (`.github/workflows/`)

- **`kmp-ci.yml`**: Provisions Java 21 + Android SDK 37 (`compileSdk = 37`) with Gradle 9.7.0 caching (`./gradlew check`) covering native `-android` `.aar` builds alongside JVM & WasmJS, plus the **`macos-apple-verify`** job compiling iOS/watchOS simulator frameworks, running `macosArm64Test`, and validating Swift 5 & 6 `-typecheck` via `scripts/verify_spm_package.sh`.
- **`bff-ci.yml`**: Runs on Go 1.26.0 (`gofmt`, `go vet ./...`, and `go test -race -coverprofile=coverage.out ./...`).
- **`quality-security.yml`**: Runs `./scripts/check_brand_hygiene.sh`, `./scripts/check_architecture_and_owners.sh`, `./scripts/generate_codegraph.py --check`, Spotless/Detekt verification (with Android SDK 37), and gitleaks secret scanning.
- **`module-owner-architecture-review.yml`**: Runs on PRs (`pull_request` / `workflow_dispatch`), validates Dependency Rule, BOM/ABI, KMP<->BFF wire parity, CodeGraph freshness (`--check`), Spotless/Detekt/`apiCheck`/Go `arch_test`, publishes a per-module Code Owner PR review checklist comment matching `.github/CODEOWNERS` + `agents/owners/*`, and supports optional Claude/Gemini/OpenAI diff review.
- **`wiki-sync.yml`**: Publishes `wiki/` to the repository's GitHub Wiki on pushes to `main`.
- **`release.yml`**: Validates full suite (with Android SDK 37) on `v*` / `bff/v*` SemVer tags and publishes `io.github.abeta-dev` Maven artifacts (**`0.8.0`** — 13 artifacts across 11 KMP libs incl. native `*-android` `.aar` targets + BOM + Catalog, gated by `vars.ENABLE_MAVEN_CENTRAL=true`), 4-product Apple SwiftPM mirror (`Abeta-dev/kmp-spm` `0.8.0`: `AbetaSduiKit`, `AbetaWatchKit`, `AbetaSduiSwiftUI`, `AbetaWatchSwiftUI`), Go proxy warmup (`bff@v0.9.0`), & GitHub Release.

---

## Golden Parity Discipline

Both `bff/internal/infra/golden*_test.go` and `kmp/sdui-contracts/src/jvmTest/kotlin/dev/abeta/kmp/contracts/GoldenFixturesParityTest.kt` (plus `SduiScreenComposeTest`) validate against **11 fixtures** in `bff/testdata/golden/`:

- `home_screen.json`
- `form_screen.json`
- `action_batch.json`
- `containers_screen.json`
- `store_home.json`
- `interactive_screen.json`
- `nudges_and_composed_screen.json`
- `extended_atoms_screen.json`
- `express_molecules_and_analytics_screen.json`
- `streaming_and_versioned_screen.json`
- `spatial_custom_and_graph_screen.json`

Any schema update in `kmp/sdui-contracts`, `kmp/action-engine`, or `bff/pkg/sdui/*` must keep all **11 golden fixtures** passing on both sides.
