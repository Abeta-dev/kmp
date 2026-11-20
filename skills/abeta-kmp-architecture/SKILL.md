---
name: abeta-kmp-architecture
description: Enforce Clean Architecture, the Uncle Bob Dependency Rule, SOLID principles, Atomic/Action separation, and Gradle BOM/Version Catalog discipline in abeta-dev/kmp. Use for architecture reviews, PR audits, adding modules, or updating dependencies.
---

# Abeta KMP Architecture & SOLID Reviewer

Use this skill when auditing PRs, adding new Gradle modules, updating dependencies, or verifying architectural compliance across `abeta-dev/kmp`.

## 1. Uncle Bob Dependency Rule Checklist

Verify every `build.gradle.kts` dependency and Kotlin/Go import across supported targets (`jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `wasmJs`, plus `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64` on headless/contract modules):

- **Layer 0**:
  - `core`: zero project dependencies (owns primitives + OS bridges/ports: `PlatformCapabilities`/`currentPlatformCapabilities()`, `PlatformKind.{WATCHOS, WEAR_OS, LINUX_NATIVE, WINDOWS_NATIVE}`, `HapticEngine`, `ClipboardPort`, `ExternalLauncherPort`, `SharePort`, `ConnectivityMonitor`, `PermissionPort`, `LocationPort`, `FilePickerPort`, `PushNotificationPort`, `LocaleFormatter`, `DeepLinkParser`, `SecureKeyValueStore`, `BiometricAuthenticator`).
  - `state` (`SessionStore`, `PreferenceStore`, `createSecurePersistentKeyValueStore(namespace)` in `SettingsKeyValueStore.kt`, `FeatureFlagProvider`, `OfflineMutationQueue`): depends only on `core` (no design tokens).
  - `analytics`: depends only on `core`.
- **Layer 1**:
  - `design-tokens` (all tokens + `ResponsiveTokens.kt` exposed via `ThemeTokens.adaptiveMetrics`) & `sdui-contracts` (**15 Wire Atoms**, **14 `SnippetTypes`**, `AdaptiveContracts.kt`, `OsPermissionKind`, `ScreenLayoutMode`, `MotionContracts.kt`, `FloatingCartSpec`, `PaginationSpec`, `OverlayPresentation`, `TabItemSpec.title: String`): zero project dependencies (never import `core`).
  - `ui-atoms` (**16 stateless Compose atoms** incl. `UiStepper` haptic hooks, `UiCelebrationOverlay`, `UiHapticFeedback.kt`, `MotionModifier.kt` incl. `Modifier.shakeOnError` honoring `reducedMotion`, `compose.uiTest`): depends only on `design-tokens` and `sdui-contracts`. Never imports `core`, `action-engine`, `network`, or `sdui-layout`.
- **Layer 2**:
  - `action-engine` (**18 `ActionTypes`** adding `open_url`, `share`, `request_permission`, `biometric_auth` + `ActionOutcome.{OpenUrlRequested, ShareRequested, PermissionRequested, BiometricAuthRequested}` alongside `trigger_feedback` / `FeedbackTriggered`): depends only on `core` and `sdui-contracts` (never `network`, `ui-atoms`, `sdui-layout`, or `watch-companion`).
  - `network` (`HttpClientFactory`, `KtorHttpTransport`: HTTP/2 ALPN, Brotli, bearer refresh, `CertificatePinMatcher` shared SPKI/cert pinning for JVM/OkHttp `CertificatePinner` & Apple Darwin `NSURLSession` `PlatformHttpEngine.apple.kt` on iOS, macOS, and watchOS): depends only on `core`.
- **Layer 3**:
  - `sdui-layout`: orchestrates Layers 0–2 (`LayoutController` with `observeConnectivity`, `handleDeepLink`, `isOffline`, `errorShakeSequence`, `errorShakeTargetId`, `dismissFeedback()`, `collectAllEnvelopes`, `PlatformBridgeBundle`, `PlatformBridgeOutcomeExecutor`, `AdaptiveScreenContainers.kt` (`AdaptiveReadyViewport`, `SplitPaneBody`, `OfflineStatusBanner`), **14 snippet renderers** incl. responsive `Grid`/`Carousel`, `FloatingCartDockHost`, `UiCelebrationOverlay`, `CachedScreenRepository`, `OfflineActionCoordinator`, `KtorImageRenderer`, `createSduiKoinModule` (`platformBridges`, `certificatePins`), `SduiHostRoot.kt` (`SduiHostedRoot`), iOS `createSduiViewController` + `AbetaSduiKit.xcframework`, macOS Cocoa `launchMacOsSduiWindow` (`SduiMacOsWindow.kt`), `SduiScreenComposeTest`).
  - `watch-companion`: headless Layer 3 wearable module (`WatchGlanceModels.kt`, `WatchScreenMapper`, `RemoteWatchPageSource`, `WatchRemoteApiCallExecutor`, `WatchCompanionController`, `createWatchCompanionModule`, `createAppleWatchCompanionHost`, `AbetaWatchKit.xcframework`). Must depend ONLY on `core`, `state`, `analytics`, `sdui-contracts`, `action-engine`, `network` (zero Compose UI or `ui-atoms`/`sdui-layout`).
  - `storybook`: **12 interactive stories across 4 categories** adding `ResponsiveAndBridgesStory`, `CompactStoryStrip`/`StorySidebar`, `"watch-status"` in `LiveBffStory`, `launchStorybookMacOsWindow`, `AbetaStorybookKit.xcframework`, `StorybookComposeTest`.
  - `sample-app`: **7 screens** (`SampleContainer.DEFAULT_SCREENS` incl. `watch-status` via `SampleWatchScreen`), responsive `SampleStoreScreen`/`SampleStoreCatalog`, `PlatformBridgeBundle` + `observeConnectivity`, `--headless` & `--bff-url`.
  - `kmp/platforms/`: Drop-in host templates (`android/`, `wearos/`, `apple/`, `README.md`; Android & Wear OS require an Android app module with `ANDROID_HOME` / Android SDK configured).
- **Go BFF (`bff/`)**: `internal/handler` (`CompressionMiddleware`, `ETagMiddleware`, `/openapi.json`, `/docs`, `POST /api/v1/orders/express`, optional rate limit & JWT auth) -> `internal/usecase` -> `internal/domain` <- `internal/infra` (**7 screens** incl. `store-home` with `columns_by_width` + `adaptive_layout` & `watch-status`, `DefaultActionExecutor` for **18 actions**; checked by `bff/internal/domain/arch_test.go`).

## 2. SOLID & Atomic/Action Separation Checklist

- **SRP**: Atoms render UI only; decoders decode JSON only; handlers return typed `ActionOutcome`; BFF handlers only adapt HTTP.
- **OCP**: New actions register via `ActionDecoderRegistry` + `ActionRegistry`; new snippets register via `SnippetRegistry` (`SnippetRenderer`).
- **DIP**: `LayoutController` depends on `ScreenRepository` and `PlatformBridgeBundle` (`core` OS ports); `WatchCompanionController` depends on `WatchPageSource` and `core` ports; `action-engine` depends on `ApiCallExecutor`; `ui-atoms` depends on `UiImageRenderer` and `LocalUiHapticFeedback`.

## 3. Gradle BOM, Version Catalog, ABI & Docs Checklist

- External versions live **only** in `kmp/dependencyManagement/gradle/libs.versions.toml`.
- Internal `dev.abeta.kmp` versions live **only** in `kmp/dependencyManagement/gradle/internal.versions.toml`.
- Any new publishable module must be registered in:
  1. `include(":<module>")` in `kmp/settings.gradle.kts`
  2. `moduleMappings` in `kmp/settings.gradle.kts` (`abeta.kmpLocalBuild` substitution)
  3. `publishableModules` in `kmp/build.gradle.kts`
  4. `kmp/dependencyManagement/gradle/internal.versions.toml`
  5. `kmp/kmp-bom/build.gradle.kts`
- Binary Compatibility Validator (`apiCheck` / `apiDump`) guards all **10 publishable KMP library modules** (`core`, `state`, `analytics`, `design-tokens`, `sdui-contracts`, `ui-atoms`, `action-engine`, `network`, `sdui-layout`, `watch-companion`) via `kmp/<module>/api/<module>.api`.
- Dokka v2 HTML generation runs via `./gradlew :core:dokkaGenerateHtml` or `./gradlew dokkaGenerateHtml`.

## 4. Hygiene & Parity Gate

- Run `./scripts/check_brand_hygiene.sh`.
- Confirm `kmp/sdui-contracts` / `kmp/action-engine` changes keep parity with `bff/pkg/sdui/*` and `bff/testdata/golden/{home_screen,form_screen,action_batch,containers_screen,store_home}.json`.
- Run `./scripts/verify_all.sh` before approving.
