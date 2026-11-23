# Testing & Quality Gates

Every pull request and release in `abeta-dev/kmp` is guarded by automated formatting, static analysis, ABI checks, Compose UI semantic tests, unit/golden parity tests, and brand/path hygiene checks.

---

## Verification Matrix

| Gate | Scope | Command |
| :--- | :--- | :--- |
| **Brand & Path Hygiene** | Entire repo | `./scripts/check_brand_hygiene.sh` |
| **Architecture, Wire Parity & Module Owners** | Uncle Bob Dependency Rule, BOM/Catalog/ABI, 18 actions / 21 snippets parity, 17 Code Owners, SOLID/KISS LOC check | `./scripts/check_architecture_and_owners.sh` |
| **Spotless (`ktlint`)** | `kmp/` Kotlin & `.gradle.kts` | `(cd kmp && ./gradlew spotlessCheck)` |
| **Detekt** | `kmp/` static analysis | `(cd kmp && ./gradlew detekt)` |
| **Binary Compatibility Validator** | Public ABI of **11 publishable KMP modules** (incl. `database` & `watch-companion`) | `(cd kmp && ./gradlew apiCheck)` (update via `apiDump`) |
| **KMP Full Check** | `kmp/` compile, Spotless, Detekt, `apiCheck`, unit, golden & `compose.uiTest` semantic tests (`UiAtomsComposeTest`, `ExtendedAtomsComposeTest`, `SduiScreenComposeTest`, `StorybookComposeTest`, `WatchCompanionControllerTest`) | `(cd kmp && ./gradlew check)` |
| **Dokka v2 HTML Docs** | Module or project API documentation | `(cd kmp && ./gradlew :core:dokkaGenerateHtml)` / `(cd kmp && ./gradlew dokkaGenerateHtml)` |
| **Sample App Headless** | End-to-end KMP headless verification across core screens and all **7-in-1 Super-App Showcase** verticals | `(cd kmp && ./gradlew :sample-app:run --args="--headless")` & `--args="--headless --verticals"` |
| **Storybook Desktop & WasmJS** | **20-story** interactive component (all 30 UI atoms), motion, quick-commerce, Live 7-in-1 Super-App Demos, responsive breakpoint, OS bridge, Snippet Crafter & token explorer | `(cd kmp && ./gradlew :storybook:run)` or `:storybook:wasmJsBrowserDevelopmentRun` |
| **Apple XCFrameworks** | `AbetaSduiKit` (`iosArm64`, `iosSimulatorArm64`), `AbetaWatchKit` (`watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`), and `AbetaStorybookKit` (`iosArm64`, `iosSimulatorArm64`) | `(cd kmp && ./gradlew :sdui-layout:assembleAbetaSduiKitXCFramework :watch-companion:assembleAbetaWatchKitXCFramework :storybook:assembleAbetaStorybookKitXCFramework)` |
| **Go Vet** | `bff/` static checks | `(cd bff && go vet ./...)` |
| **Go Race & Coverage Tests** | `bff/` unit, arch, middleware, & golden JSON parity tests | `(cd bff && go test -race -cover ./...)` |
| **All-in-One Gate** | Hygiene + KMP + `--headless` + Go BFF | `./scripts/verify_all.sh` |

---

## CI Workflows (`.github/workflows/`)

- **`kmp-ci.yml`**: Runs on Java 21 with Gradle 9.7.0 caching (`./gradlew check`).
- **`bff-ci.yml`**: Runs on Go 1.26.0 (`gofmt`, `go vet ./...`, and `go test -race -coverprofile=coverage.out ./...`).
- **`quality-security.yml`**: Runs `./scripts/check_brand_hygiene.sh`, `./scripts/check_architecture_and_owners.sh`, Spotless/Detekt verification, and gitleaks secret scanning.
- **`module-owner-architecture-review.yml`**: Runs on PRs (`pull_request` / `workflow_dispatch`), validates Dependency Rule, BOM/ABI, KMP<->BFF wire parity, Spotless/Detekt/`apiCheck`/Go `arch_test`, publishes a per-module Code Owner PR review checklist comment matching `.github/CODEOWNERS` + `agents/owners/*`, and supports optional Claude/Gemini/OpenAI diff review.
- **`wiki-sync.yml`**: Publishes `wiki/` to the repository's GitHub Wiki on pushes to `main`.
- **`release.yml`**: Validates full suite on `v*` / `bff/v*` SemVer tags and publishes `io.github.abeta-dev` Maven artifacts (13 artifacts across 11 KMP libs + BOM + Catalog), Apple XCFrameworks (`Abeta-dev/kmp-spm`), Go proxy warmup (`bff@v0.5.0`), & GitHub Release.

---

## Golden Parity Discipline

Both `bff/internal/infra/golden_test.go` (plus `golden_nudges_test.go` and `golden_extended_atoms_test.go`) and `kmp/sdui-contracts/src/jvmTest/kotlin/dev/abeta/kmp/contracts/GoldenFixturesParityTest.kt` (plus `SduiScreenComposeTest`) validate against **8 fixtures** in `bff/testdata/golden/`:

- `home_screen.json`
- `form_screen.json`
- `action_batch.json`
- `containers_screen.json`
- `store_home.json`
- `interactive_screen.json`
- `nudges_and_composed_screen.json`
- `extended_atoms_screen.json`

Any schema update in `kmp/sdui-contracts`, `kmp/action-engine`, or `bff/pkg/sdui/*` must keep all **8 golden fixtures** passing on both sides.
