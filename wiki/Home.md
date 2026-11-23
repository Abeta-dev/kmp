# Welcome to the `abeta-dev/kmp` Wiki

**Abeta KMP Kit (`dev.abeta.kmp`)** is an open-source Server-Driven UI (SDUI) monorepo combining:

- **`kmp/`**: A 4-layer **Kotlin Multiplatform + Compose Multiplatform** SDUI UI, Wearable & Local Database Kit (**11 publishable library modules** including Room 3 `database`, plus `kmp-bom` and `dependencyManagement`, `Kotlin 2.4.21`, `Compose Multiplatform 1.12.2`, `Ktor 3.6.1`, `Koin 4.2.2`, `Gradle 9.7.0`, `JDK 21`):
  - Headless/contract modules (`core`, `state`, `analytics`, `database`, `sdui-contracts`, `action-engine`, `network`, `watch-companion`) target `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`, and `wasmJs`.
  - Compose UI modules (`design-tokens`, `ui-atoms`, `sdui-layout`) and `storybook` target `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, and `wasmJs`.
  - Drop-in reference host glue lives in `kmp/platforms/` (`android/`, `wearos/`, `apple/`, `web/`, `README.md`; Android & Wear OS templates require an Android app module with `ANDROID_HOME` / Android SDK configured).
- **`bff/`**: A **Go 1.26.0** Clean Architecture SDUI Backend-for-Frontend (`github.com/abeta-dev/kmp/bff@v0.5.0`, `./cmd/bff`) powered by **Gin `v1.12.0`** and **`github.com/abeta-dev/go-libs@v0.4.0`**, with Brotli/gzip compression, SHA-256 ETag caching, `h2c`/TLS HTTP/2, OpenAPI 3.1 + Scalar docs, and optional rate limiting / JWT auth.

---

## Core Engineering Pillars

1. **Uncle Bob Dependency Rule**: Dependencies strictly point inward (`Layer 3 -> Layer 2 -> Layer 1 -> Layer 0`).
2. **Atomic / Action Separation**: Design tokens (`design-tokens`), pure wire contracts (`sdui-contracts`), and stateless Compose atoms (`ui-atoms`) never import `core`, action handlers, navigation controllers, or HTTP clients.
3. **Open/Closed Action Engine**: All **18 built-in actions** (adding `open_url`, `share`, `request_permission`, `biometric_auth` alongside `trigger_feedback`)—plus custom feature actions—plug into `ActionDecoderRegistry` + `ActionRegistry` without modifying UI atoms or `DefaultActionDispatcher`.
4. **Gradle BOM & Catalog Discipline**: Consumers align versions with `io.github.abeta-dev:bom:0.3.0` and `io.github.abeta-dev:dependencies:0.3.0` (Kotlin package namespace `dev.abeta.kmp.*`), enforced via Binary Compatibility Validator (`apiCheck`) across all **11 publishable library modules**.
5. **1:1 Wire Protocol Parity**: `kmp/sdui-contracts` and `bff/pkg/sdui/{actions,atoms,snippets,theming}` (`bff@v0.5.0`) share the exact `v1` JSON schema (**28 Wire Atoms**, **30 Stateless Compose UI Atoms**, **21 Canonical SnippetTypes** incl. `composed_block` with **29 `ComposedNodeKind`s**, **18 ActionTypes**, **7 Ambient `MotionLoopEffect`s**) verified against **8 golden fixtures** in `bff/testdata/golden/`.

---

## Wiki Navigation

| Page | Summary |
| :--- | :--- |
| [Architecture Overview](Architecture-Overview) | 4-layer KMP structure, targets, Core OS ports, Room 3 `database`, `watch-companion`, adaptive screens, Koin, Apple XCFrameworks, Clean Architecture boundaries |
| [Atomic Design System](Atomic-Design-System) | `design-tokens` (incl. `ResponsiveTokens`), `sdui-contracts` (28 wire atoms, 21 snippets incl. `composed_block` w/ 29 `ComposedNodeKind`s, `AdaptiveContracts`, `MotionSpec` w/ 7 loops), 30 stateless `ui-atoms` + haptic/bounce/shake modifiers, and 20-story `storybook` |
| [Action Separation & Registry](Action-Separation-And-Registry) | Decoupling UI atoms from side effects with `ActionRef`, **18 `ActionTypes`**, `PlatformBridgeBundle`, `PlatformBridgeOutcomeExecutor`, and `DefaultActionDispatcher` |
| [Gradle BOM & Catalog](Gradle-BOM-And-Catalog) | Importing `io.github.abeta-dev:bom:0.3.0` + `io.github.abeta-dev:dependencies:0.3.0` (11 library modules incl. `database` & `watch-companion`), `abeta.kmpLocalBuild`, BCV (`apiDump`/`apiCheck`), Dokka v2 |
| [Go BFF Guide](Go-BFF-Guide) | Building SDUI screens in `bff/` (`bff@v0.5.0`, core + Abeta Pay & 7-in-1 Super-App Showcase screens, **18 actions**, `POST /api/v1/orders/express`, compression, ETag, `h2c`/HTTP/2, OpenAPI 3.1/Scalar) |
| [Testing & Quality Gates](Testing-And-Quality-Gates) | Spotless, Detekt, `compose.uiTest`, `apiCheck`, `dokkaGenerateHtml`, `--headless`/`--verticals`, Apple XCFrameworks, Go race tests, 17 Code Owners, and 8 golden fixtures |
