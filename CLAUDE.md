# CLAUDE.md — Claude Code Instructions for `abeta-dev/kmp`

This repository is **Abeta KMP Kit (`abeta-dev/kmp`)**, a monorepo combining a layered Kotlin Multiplatform + Compose Multiplatform SDUI, Wearable & Room 3 Database Kit (`kmp/`, **11 publishable library modules** plus `kmp-bom` and `dependencyManagement`) and a Go 1.26.0 SDUI BFF (`bff/`).

Read [AGENTS.md](AGENTS.md) for the complete architectural contract. Below is the quick operational checklist for every session.

---

## Skill & Module Code Owner Routing

Before editing code in a domain, load the corresponding skill (10 skills total) and the responsible Code Owner persona in `agents/owners/` (17 per-module owners matching `.github/CODEOWNERS`):

- **Monorepo / Cross-Cutting**: [skills/abeta-kmp-kit/SKILL.md](skills/abeta-kmp-kit/SKILL.md)
- **Repo Usage & Onboarding (`io.github.abeta-dev:bom`)**: [skills/abeta-kmp-usage/SKILL.md](skills/abeta-kmp-usage/SKILL.md)
- **Demo & Reference Modules (`sample-app`, `storybook`)**: [skills/abeta-kmp-demo/SKILL.md](skills/abeta-kmp-demo/SKILL.md)
- **Multi-OS Platforms (Android, Wear OS, iOS, watchOS, macOS, Web)**: [skills/abeta-kmp-platforms/SKILL.md](skills/abeta-kmp-platforms/SKILL.md)
- **Tokens, Wire DTOs & Stateless Atoms**: [skills/abeta-kmp-atomic/SKILL.md](skills/abeta-kmp-atomic/SKILL.md)
- **OCP Action Engine (KMP & Go)**: [skills/abeta-kmp-actions/SKILL.md](skills/abeta-kmp-actions/SKILL.md)
- **Go 1.26.0 SDUI BFF**: [skills/abeta-kmp-bff/SKILL.md](skills/abeta-kmp-bff/SKILL.md)
- **Architecture & BOM Review**: [skills/abeta-kmp-architecture/SKILL.md](skills/abeta-kmp-architecture/SKILL.md)
- **Per-Module Code Owner PR Reviews**: [skills/abeta-kmp-module-review/SKILL.md](skills/abeta-kmp-module-review/SKILL.md)
- **CI/CD Workflows & Releases**: [skills/abeta-kmp-cicd/SKILL.md](skills/abeta-kmp-cicd/SKILL.md)

Native Claude Code helpers live in `.claude/settings.json`, `.claude/commands/` (`/review-module`, `/verify-all`, `/add-sdui-feature`, `/impeccable-audit`), and `.claude/agents/`.

---

## Key Architecture & Style Constraints

- **Group & Brand**: Maven group `io.github.abeta-dev` (`0.7.0` — 11 publishable libs + `bom` + `dependencies` = 13 Maven Central artifacts), Kotlin package `dev.abeta.kmp`, Go module `github.com/abeta-dev/kmp/bff` (`bff@v0.9.0`). Never commit legacy brand names or local absolute filesystem paths.
- **KMP Targets & Host Glue**:
  - Headless/contract publishable modules (`core`, `state`, `analytics`, `database`, `sdui-contracts`, `action-engine`, `network`, `watch-companion`): `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`, `wasmJs`.
  - Compose UI modules (`design-tokens`, `ui-atoms`, `sdui-layout`) and `storybook`: `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `wasmJs`.
  - `sample-app`: JVM Desktop & `--headless` / `--verticals` CLI.
  - Drop-in reference host glue under `kmp/platforms/` (`android/{AndroidPlatformBridgeFactory,AbetaAndroidHostActivity,AndroidDatabaseFactory}.kt`, `wearos/{WearCompanionHostActivity,WearSduiRenderer}.kt`, `apple/AbetaAppleHosts.swift` & `apple/Sources/{AbetaSduiSwiftUI,AbetaWatchSwiftUI}/`, `web/README.md`, `README.md` — Android & Wear OS templates require an Android app module with `ANDROID_HOME` / Android SDK configured).
- **Layer Boundaries**:
  - `Layer 0`: `core` (primitives + OS/Voice/Desktop bridges/ports), `state` (`SessionStore`, `DataStoreKeyValueStore`, `createDiskKeyValueStore`, `PreferenceStore`, `createSecurePersistentKeyValueStore(namespace)`, `FeatureFlagProvider`, `OfflineMutationQueue`), `analytics`, `database` (Room 3 `3.0.3` KMP SQLite DB: `AbetaDatabase` schema **v2** w/ `MIGRATION_1_2`, `cached_screens` incl. `pageVersion`/`appVersion`, `offline_mutations`, `key_value_records`)
  - `Layer 1`: `design-tokens` (all tokens + `ResponsiveTokens.kt` exposed via `ThemeTokens.adaptiveMetrics`) & `sdui-contracts` (**29 Wire Atoms** incl. `StepperAtom`, `CountdownAtom` w/ `expires_at_epoch_ms`, `MediaPagerAtom`, & 12 `ExtendedAtoms.kt`, `AdaptiveContracts.kt`, `OsPermissionKind`, `ScreenLayoutMode`, `MotionContracts.kt` + `AtomEnums.kt` with **7 Ambient `MotionLoopEffect`s**, **23 `SnippetTypes`** incl. `product_card`, `swipe_deck`, `search_bar`, `chip_group`, `nudge_bar`, `marquee_strip`, `order_tracker`, **`composed_block`** Atomic Snippet Molding Engine with **36 `ComposedNodeKind`s**, `custom_slot`, and `graph_canvas`, **6 `SnippetDeltaOp`s**, `ScreenManifestResponse`, `FloatingCartSpec` with free-delivery threshold nudge fields, `PaginationSpec`, `pull_to_refresh`, `OverlayPresentation`, `TabItemSpec.title: String`) -> `ui-atoms` (**31 stateless Compose atoms** incl. `UiStepper`, `UiCountdown`, `UiRollingCounter`, `UiMediaPager`, `UiRadioGroup`, `UiSelect`, `UiOtpInput`, `UiChip`, `UiSegmentedControl`, `UiRating`, `UiPrice`, `UiBadge`, `UiAlertBanner`, `UiStatTile`, `UiKeyValueRow`, ISO/IEC 18004 `UiQrCode`, plus `UiCelebrationOverlay`, `UiHapticFeedback.kt`, `MotionModifier.kt` + `MotionLoopModifiers.kt` incl. **7 Ambient `MotionLoopEffect`s**, `Modifier.pressBounce`, `Modifier.shakeOnError` honoring `ThemeTokens.motion.reducedMotion`; zero `core`, `action-engine`, `network`, or `sdui-layout` imports)
  - `Layer 2`: `action-engine` (`core` + `sdui-contracts`, **18 `ActionTypes`** incl. `open_url`, `share`, `request_permission`, `biometric_auth`, `trigger_feedback` + `ActionOutcome.*`, bounded `DispatchBudget`), `network` (`core` + Ktor 3.6.1 HTTP/2 ALPN, Brotli `br`, `If-None-Match`/`304`, `KtorSseTransport`, `CsrfTokenProvider` + single `403` retry, `NetworkConfig.appVersion` -> `X-Client-App-Version`, bearer refresh, validated `sha256/` `CertificatePinMatcher` JVM/OkHttp + Apple Darwin `NSURLSession` pinning across iOS, macOS, and watchOS)
  - `Layer 3`: `sdui-layout` (**23 snippet renderers** via `BuiltInSnippetModule` incl. `NudgeBar`, `MarqueeStrip`, `OrderTracker`, **`ComposedBlock`** w/ **36 `ComposedNodeKind`s**, `CustomSlot` + `CustomSlotRegistry`, and `GraphCanvas`, `PlatformBridgeBundle`, `PlatformBridgeOutcomeExecutor`, `AdaptiveScreenContainers.kt`, `LayoutController` + SSE delta patching, `SduiLayoutViewModel` + `SavedState`, `FloatingCartDockHost` w/ free-delivery threshold nudge, `UiCelebrationOverlay`, rich SDUI `ModalBottomSheet` & `AlertDialog`, `PullToRefreshBox`, `SkeletonScreenPlaceholder`, `loadMore()`, `CachedScreenRepository` (`VERSION_GATED`, `304`, `/manifest`), `RemoteBffAnalyticsTracker`, `OfflineActionCoordinator`, `KtorImageRenderer`, `createSduiKoinModule`, `SduiHostedRoot`, iOS `createSduiViewController` + `AbetaSduiKit.xcframework`, macOS Cocoa `launchMacOsSduiWindow`), `watch-companion` (headless wearable companion + `AbetaWatchKit.xcframework`), `storybook` (**20 stories across 5 categories** featuring **Live 7-in-1 Super-App Demos** `super-app-demos`, 8 dedicated `UI ATOMS` stories covering all **31** atoms, `"All 23 Snippets"`, `ResponsiveAndBridgesStory` w/ `Voice & Desktop OS Bridges`, `DiscoveryAndSearchStory`, **Animation & Nudges Lab** `animation-nudges`, **Atomic Snippet Crafter** `snippet-crafter`, **End-to-End App Journeys** `e2e-journeys`, and `LiveBffStory`, Desktop + WasmJS + macOS + `AbetaStorybookKit.xcframework`), `sample-app` (**7-in-1 Super-App Showcase Launcher** (`Abeta Fresh`, `Abeta Bites`, `Abeta Pulse`, `Abeta Mart`, `Abeta Spark`, `Abeta Trade`, `Abeta Pay`) + core reference screens + `watch-status`, `--headless`, `--verticals`, `--bff-url`)
  - `Go BFF` (`bff@v0.9.0`): `cmd/bff` (`http.Server.Protocols` HTTP/1.1 + `h2c` + TLS 1.2+ HTTP/2), `internal/{domain,usecase,handler,infra}` (**12 built-in screens**: `home`, `catalog`, `containers`, `profile`, `form-demo`, `fresh-home`, `store-home`, `watch-status`, `pay-home`, `pay-transfer`, `pay-cards`, `pay-receipt`; live routes `/healthz`, `/readyz`, `/metrics`, `/openapi.json`, `/docs`, `/api/v1/screens`, `/api/v1/manifest`, `/api/v1/screens/manifest`, `/api/v1/screens/:screenId`, `/api/v1/streams/:streamId`, `/api/v1/actions/execute`, `/api/v1/orders/express`, `/api/v1/pay/transfer`, `/api/v1/catalog/tokens`, `/api/v1/analytics/events`, `/api/v1/analytics/summary`), **8 exportable packages** under `pkg/sdui/{actions,analytics,atoms,cache,molecules,server,snippets,theming}` (**29 Wire Atoms**, **23 Snippet Types** incl. `composed_block` w/ **36 `ComposedNodeKind`s**, `custom_slot`, `graph_canvas`, **6 `SnippetDeltaOp`s**, **18 Action Types**, **7 Ambient `MotionLoopEffect`s**, `UpstreamCachedScreenRepository`, fail-closed `CORSMiddleware`, `private, no-cache` `ETagMiddleware`, pooled `CompressionMiddleware`, per-router `JWTAuthMiddleware`, `HTTPUpstreamFetcher`, `StreamProvider`), `testdata/golden/` (**11** golden parity fixtures)
- **Formatting, ABI & Static Checks**:
  - KMP enforces **Spotless (`ktlint`)**, **Detekt**, and **Binary Compatibility Validator (`apiCheck`)** across all **11 publishable library modules** (`./gradlew spotlessCheck detekt apiCheck`).
  - Go enforces `gofmt`, `go vet ./...`, and `go test -race -cover ./...`.
- **Dependency Management**:
  - All versions live in `kmp/dependencyManagement/gradle/libs.versions.toml` and `kmp/dependencyManagement/gradle/internal.versions.toml`.
  - Local substitution is controlled by `abeta.kmpLocalBuild=true` in `kmp/gradle.properties`.

---

## Essential Commands

```bash
# Brand & path hygiene
./scripts/check_brand_hygiene.sh

# KMP full check (Spotless + Detekt + apiCheck + unit/golden/compose.uiTest tests)
(cd kmp && ./gradlew check)

# Sample app headless verification (home, form-demo, containers, store-home + celebration)
(cd kmp && ./gradlew :sample-app:run --args="--headless")

# Storybook (Desktop JVM & WasmJS Browser)
(cd kmp && ./gradlew :storybook:run)
(cd kmp && ./gradlew :storybook:wasmJsBrowserDevelopmentRun)

# Package Apple XCFrameworks (macOS)
(cd kmp && ./gradlew :sdui-layout:assembleAbetaSduiKitXCFramework :watch-companion:assembleAbetaWatchKitXCFramework :storybook:assembleAbetaStorybookKitXCFramework)

# Binary Compatibility Validator dump & Dokka v2 HTML docs
(cd kmp && ./gradlew apiDump)
(cd kmp && ./gradlew :core:dokkaGenerateHtml) # or ./gradlew dokkaGenerateHtml

# Auto-format KMP Kotlin & Gradle scripts
(cd kmp && ./gradlew spotlessApply)

# Go BFF checks
(cd bff && go vet ./... && go test -race -cover ./...)

# Complete verification pipeline
./scripts/verify_all.sh
```
