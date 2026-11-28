---
name: abeta-kmp-architecture
description: Enforce Clean Architecture, the Uncle Bob Dependency Rule, SOLID principles, Atomic/Action separation, and Gradle BOM/Version Catalog discipline in abeta-dev/kmp. Use for architecture reviews, PR audits, adding modules, or updating dependencies.
---

# Abeta KMP Architecture & SOLID Reviewer

Use this skill when auditing PRs, adding new Gradle modules, updating dependencies, or verifying architectural compliance across `abeta-dev/kmp`.

> **Mandatory Grounding Order**: `AGENTS.md` → `docs/architecture.md` → `docs/flows/*.md` → `docs/patterns/*.md` → `docs/codegraph/INDEX.md` (`./scripts/generate_codegraph.py --query <symbol>`) → Source Reads Only for Verification.

## 1. Uncle Bob Dependency Rule Checklist

Verify every `build.gradle.kts` dependency and Kotlin/Go import across supported targets (native **`android` `.aar`** via AGP `9.5.3` `compileSdk = 37` / `minSdk = 24` across all 11 publishable modules, `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `wasmJs`, plus `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64` on headless/contract modules):

- **Layer 0**:
  - `core`: zero project dependencies (owns primitives + `AbetaAndroidContext` & native OS/Voice/Desktop bridges/ports).
  - `state` (`SessionStore`, `DataStoreKeyValueStore`, `createDiskKeyValueStore`, Android `SharedPreferences`/DataStore actuals, `PreferenceStore`, `createSecurePersistentKeyValueStore(namespace)`, `FeatureFlagProvider`, `OfflineMutationQueue`): depends only on `core` (no design tokens).
  - `analytics`: depends only on `core`.
  - `database` (Room 3 `3.0.3` schema v2 w/ `MIGRATION_1_2`, `AbetaDatabase`, `AbetaLocalDatabase`, `DatabaseKeyValueStore` opt-in `ObservableKeyValueStore` adapter, native `androidMain` builder, `cached_screens`, `offline_mutations`, `key_value_records`, `sqlite-bundled:2.7.1`, `sqlite-web:2.7.1`, KSP `2.3.14` schema exports `1.json` & `2.json`): depends only on `core`.
- **Layer 1**:
  - `design-tokens` (all tokens + `ResponsiveTokens.kt` exposed via `ThemeTokens.adaptiveMetrics`) & `sdui-contracts` (**29 Wire Atoms** incl. `MediaPagerAtom`, `CountdownAtom` w/ `expires_at_epoch_ms`, & 12 `ExtendedAtoms.kt` primitives, **23 `SnippetTypes`** incl. `swipe_deck`, `search_bar`, `chip_group`, `nudge_bar`, `marquee_strip`, `order_tracker`, `composed_block` w/ **36 `ComposedNodeKind`s**, `custom_slot`, & `graph_canvas`, **6 `SnippetDeltaOp`s**, `ScreenManifestResponse`, `AdaptiveContracts.kt`, `OsPermissionKind`, `ScreenLayoutMode`, `MotionContracts.kt` w/ **7 Ambient `MotionLoopEffect`s**, `FloatingCartSpec` free-delivery threshold nudges, `PaginationSpec`, `OverlayPresentation`, `TabItemSpec.title: String`): zero project dependencies (never import `core`).
  - `ui-atoms` (**31 stateless Compose atoms** incl. `UiMediaPager`, ISO/IEC 18004 `UiQrCode`, `UiStepper`, `UiCountdown`, `UiRollingCounter`, & 12 extended atoms, `UiCelebrationOverlay`, `UiHapticFeedback.kt`, `MotionModifier.kt` + `MotionLoopModifiers.kt` incl. **7 Ambient `MotionLoopEffect`s**, `Modifier.pressBounce`, `Modifier.shakeOnError` honoring `reducedMotion`, `compose.uiTest`): depends only on `design-tokens` and `sdui-contracts`. Never imports `core`, `action-engine`, `network`, or `sdui-layout`.
- **Layer 2**:
  - `action-engine` (**18 `ActionTypes`**, bounded `DispatchBudget`): depends only on `core` and `sdui-contracts` (never `network`, `ui-atoms`, `sdui-layout`, or `watch-companion`).
  - `network` (`HttpClientFactory`, `KtorHttpTransport`: HTTP/2 ALPN, Brotli, `304`, `KtorSseTransport`, `CsrfTokenProvider` + `403` retry, `NetworkConfig.appVersion`, bearer refresh, validated `sha256/` `CertificatePinMatcher` for JVM/OkHttp & Apple Darwin `NSURLSession` on iOS, macOS, and watchOS): depends only on `core`.
- **Layer 3**:
  - `sdui-layout`: orchestrates Layers 0–2 while staying Room-free by default (opt-in Room 3 via `DatabaseKeyValueStore`): `LayoutController` + SSE delta patching, `SduiLayoutViewModel`, `PlatformBridgeBundle.createPlatformDefault()`, `PlatformBridgeOutcomeExecutor`, `AdaptiveScreenContainers.kt`, **23 snippet renderers** incl. `ComposedBlock` w/ **36 `ComposedNodeKind`s**, `CustomSlot`, & `GraphCanvas`, `FloatingCartDockHost`, `UiCelebrationOverlay`, `CachedScreenRepository` (`VERSION_GATED`, `304`, `/manifest`), `RemoteBffAnalyticsTracker`, `OfflineActionCoordinator`, `KtorImageRenderer` (w/ Android `BitmapFactory`), `createSduiKoinModule`, `SduiHostedRoot`, iOS `createSduiViewController` + `AbetaSduiKit.xcframework`, macOS Cocoa `launchMacOsSduiWindow`, `SduiScreenComposeTest`.
  - `watch-companion`: headless Layer 3 wearable module (`WatchGlanceModels.kt`, `WatchScreenMapper`, `RemoteWatchPageSource` w/ ETag/304 cache, `WatchRemoteApiCallExecutor`, `WatchCompanionController`, `createWatchCompanionModule`, `createAppleWatchCompanionHost`, `AbetaWatchKit.xcframework`, native Android/Wear `.aar`). Must depend ONLY on `core`, `state`, `analytics`, `sdui-contracts`, `action-engine`, `network` (zero Compose UI or `ui-atoms`/`sdui-layout`; opt-in Room 3 via `database`).
  - `storybook`: **20 interactive stories across 5 categories** (8 `UI ATOMS` stories covering all **31** atoms, **All 23 Snippets**, **Live 7-in-1 Super-App Demos**, `ResponsiveAndBridgesStory` w/ `Voice & Desktop OS Bridges`, `DiscoveryAndSearchStory`, **Animation & Nudges Lab**, **Atomic Snippet Crafter**, **End-to-End App Journeys**, `LiveBffStory`, `launchStorybookMacOsWindow`, `AbetaStorybookKit.xcframework`, `StorybookComposeTest`).
  - `sample-app`: **7-in-1 Super-App Showcase Launcher** (**Abeta Fresh**, **Abeta Bites**, **Abeta Pulse**, **Abeta Mart**, **Abeta Spark**, **Abeta Trade**, **Abeta Pay**) + core reference screens + `watch-status`, `--headless`, `--verticals` & `--bff-url`.
  - `kmp/platforms/`: Drop-in host templates (`android/`, `wearos/`, `apple/`, `web/`, `README.md`; Android & Wear OS require an Android app module with `ANDROID_HOME` / Android SDK configured).
- **Go BFF (`bff/` — `bff@v0.9.0`)**: `internal/handler` -> `internal/usecase` -> `internal/domain` <- `internal/infra` (**12 built-in screens**, **8 exportable `pkg/sdui/*` packages** incl. `cache` & `server`, **18 actions**; checked by `bff/internal/domain/arch_test.go`).

## 2. SOLID & Atomic/Action Separation Checklist

- **SRP**: Atoms render UI only; decoders decode JSON only; handlers return typed `ActionOutcome`; BFF handlers only adapt HTTP.
- **OCP**: New actions register via `ActionDecoderRegistry` + `ActionRegistry`; new snippets register via `SnippetRegistry` (`SnippetRenderer`).
- **DIP**: `LayoutController` depends on `ScreenRepository` and `PlatformBridgeBundle` (`core` OS ports); `WatchCompanionController` depends on `WatchPageSource` and `core` ports; `action-engine` depends on `ApiCallExecutor`; `ui-atoms` depends on `UiImageRenderer` and `LocalUiHapticFeedback`.

## 3. Gradle BOM, Version Catalog, ABI & Docs Checklist

- External versions live **only** in `kmp/dependencyManagement/gradle/libs.versions.toml`.
- Internal `io.github.abeta-dev` versions (`0.8.0`; Kotlin package namespace `dev.abeta.kmp.*`) live **only** in `kmp/dependencyManagement/gradle/internal.versions.toml`.
- Any new publishable module must be registered in:
  1. `include(":<module>")` in `kmp/settings.gradle.kts`
  2. `moduleMappings` in `kmp/settings.gradle.kts` (`abeta.kmpLocalBuild` substitution)
  3. `publishableModules` in `kmp/build.gradle.kts`
  4. `kmp/dependencyManagement/gradle/internal.versions.toml`
  5. `kmp/kmp-bom/build.gradle.kts`
- Binary Compatibility Validator (`apiCheck` / `apiDump`) guards all **11 publishable KMP library modules** (`core`, `state`, `analytics`, `database`, `design-tokens`, `sdui-contracts`, `ui-atoms`, `action-engine`, `network`, `sdui-layout`, `watch-companion`) via `kmp/<module>/api/jvm/<module>.api`.
- Dokka v2 HTML generation runs via `./gradlew :core:dokkaGenerateHtml` or `./gradlew dokkaGenerateHtml`.

## 4. Hygiene, CodeGraph & Parity Gate

- Run `./scripts/check_brand_hygiene.sh` and `./scripts/generate_codegraph.py --check`.
- Confirm `kmp/sdui-contracts` / `kmp/action-engine` changes keep parity with `bff/pkg/sdui/*` (`bff@v0.9.0`) and all **11 golden fixtures** (`bff/testdata/golden/{home_screen,form_screen,action_batch,containers_screen,store_home,interactive_screen,nudges_and_composed_screen,extended_atoms_screen,express_molecules_and_analytics_screen,streaming_and_versioned_screen,spatial_custom_and_graph_screen}.json`).
- Run `./scripts/verify_all.sh` before approving.
