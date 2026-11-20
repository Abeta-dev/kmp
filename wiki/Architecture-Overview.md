# Architecture Overview

`abeta-dev/kmp` enforces a strict **4-layer Clean Architecture** across `kmp/` and a **Clean Architecture** layout (`internal/handler` -> `internal/usecase` -> `internal/domain` <- `internal/infra`) inside `bff/`.

---

## Layer Topology

```mermaid
flowchart TB
    L3["Layer 3: sdui-layout, watch-companion, storybook, sample-app"]
    L2["Layer 2: action-engine, network"]
    L1["Layer 1: ui-atoms -> design-tokens & sdui-contracts"]
    L0["Layer 0: core, state, analytics"]

    L3 --> L2
    L3 --> L1
    L3 --> L0
    L2 --> L0
    L2 --> L1
```

> Note: `watch-companion` is a headless Layer 3 module depending only on `core`, `state`, `analytics`, `sdui-contracts`, `action-engine`, and `network` (zero Compose UI or `ui-atoms`/`sdui-layout` dependencies).

### Supported Multiplatform Targets & Reference Host Glue

- **Headless / Contract Publishable Modules** (`core`, `state`, `analytics`, `sdui-contracts`, `action-engine`, `network`, `watch-companion`): `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`, and `wasmJs`.
- **Compose UI Modules** (`design-tokens`, `ui-atoms`, `sdui-layout`) and **`storybook`**: `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, and `wasmJs`.
- **`sample-app`**: `jvm` Desktop & `--headless` CLI.
- **Apple XCFrameworks**: `AbetaSduiKit.xcframework` (`sdui-layout`), `AbetaWatchKit.xcframework` (`watch-companion`), and `AbetaStorybookKit.xcframework` (`storybook`).
- **Drop-in Host Glue (`kmp/platforms/`)**: `android/AndroidPlatformBridgeFactory.kt`, `wearos/WearCompanionHostActivity.kt`, `apple/AbetaAppleHosts.swift`, and `README.md` (Android & Wear OS templates require an Android app module with `ANDROID_HOME` / Android SDK configured).

### Layer Responsibilities

- **Layer 0 (`core`, `state`, `analytics`)**:
  - `core`: `AppResult` (+ combinators), `AppError` (`ErrorCategory`, `isRetryable`, `TimeoutError`, `RateLimitError`, etc.), `DispatcherProvider`, `Clock` / `MutableClock` / `ElapsedTimer`, `AppLogger` / `RedactingAppLogger` / `CompositeAppLogger`, `KeyValueStore` / `ObservableKeyValueStore` / `NamespacedKeyValueStore`, `UuidGenerator`, `BackoffPolicy`, `SingleFlight`, `Debouncer`, `ClickBackpressureGuard`, `SensitiveDataRedactor`, `FieldValidators`, `PlatformInfo` (`PlatformKind.{WATCHOS, WEAR_OS, LINUX_NATIVE, WINDOWS_NATIVE}`), `PlatformCapabilities` / `currentPlatformCapabilities()`, `HapticEngine` / `HapticStyle`, `ClipboardPort`, `ExternalLauncherPort`, `SharePort` / `ShareRequest`, `ConnectivityMonitor` / `ConnectivityStatus`, `PermissionPort`, `LocationPort`, `FilePickerPort`, `PushNotificationPort`, `LocaleFormatter` (`formatCurrencyCents`, `currentLocaleTag`), `DeepLinkParser` / `DeepLinkTarget`, `SecureKeyValueStore` (`AppleKeychain` `SecItem*` w/ fallback, JVM AES-256-GCM + `Preferences`, WasmJS `sessionStorage`), `BiometricAuthenticator` (`LAContext` on Apple). Zero project dependencies.
  - `state`: Reactive `SessionStore` (`InMemorySessionStore`, `PersistentSessionStore`, `SessionLifecycleManager`), typed & observable `PreferenceStore` (`VersionedPreferenceEnvelope` backed by `multiplatform-settings`), `createSecurePersistentKeyValueStore(namespace)` in `SettingsKeyValueStore.kt`, `FeatureFlagProvider` (`LayeredFeatureFlagProvider`, `PersistentFeatureFlagStore`), and `OfflineMutationQueue`. Depends only on `core` (design tokens live in `design-tokens`).
  - `analytics`: `AnalyticsEvent`, `AnalyticsTracker`, `CompositeAnalyticsTracker`, `LoggerAnalyticsTracker`, `RecordingAnalyticsTracker`, `SanitizingAnalyticsTracker`, `DeduplicatingAnalyticsTracker`, `EnrichingAnalyticsTracker`, and `BufferedAnalyticsTracker`. Depends only on `core`.
- **Layer 1 (`design-tokens`, `sdui-contracts`, `ui-atoms`)**:
  - `design-tokens`: Owns all immutable design tokens (`ColorTokens`, `DesignPalette`, `SpacingTokens`, `CornerRadiusTokens`, `StrokeWidthTokens`, `TypographyTokens`, `ElevationTokens`, `MotionTokens`, `OpacityTokens`, `SizingTokens`, `ThemePreset`, `ColorAccessibility`, `DesignTheme`, `ThemeTokens`, plus `ResponsiveTokens.kt`: `WindowWidthClass`, `WindowHeightClass`, `DeviceFormFactor`, `AdaptiveLayoutMetrics`, `resolveAdaptiveLayoutMetrics`, `LocalAdaptiveLayoutMetrics`, `ProvideAdaptiveLayoutMetrics`, exposed via `ThemeTokens.adaptiveMetrics`). Zero project dependencies.
  - `sdui-contracts`: Pure `@Serializable` v1 wire DTOs (`PageResponse` with `PaginationSpec`, `pullToRefresh`, `floatingCart` / `FloatingCartSpec`, and `adaptiveLayout` / `AdaptivePageSpec`; `SnippetEnvelope` with optional `motion`; **14 `SnippetTypes`** incl. `product_card` / `ProductCardSnippetData`, `CarouselSnippetData.itemWidthByWidth`, `GridSnippetData.columnsByWidth`; **15 Wire Atoms** incl. `StepperAtom`; `AdaptiveContracts.kt` (`ResponsiveColumnsSpec`, `ResponsiveIntSpec`, `AdaptivePageSpec`); `MotionContracts.kt` + `AtomEnums.kt` (`MotionSpec`, `MotionTransition`, `MotionDurationToken`, `MotionLoopEffect`, `FeedbackEffect`, `OsPermissionKind`, `ScreenLayoutMode`, `OverlayPresentation`); `LayoutConfig`, `ColorTokenRef`, `TypographyTokenRef`, `ActionRef`, `ValidationRuleContract`, `TrackingContract`, `SduiJson`; `TabItemSpec.title: String`). Zero `core`, Compose, or Ktor imports.
  - `ui-atoms`: **16 stateless Compose Multiplatform atoms** (`UiText`, `UiIcon`, `UiTag`, `UiButton`, `UiImage`, `UiTextField`, `UiDivider`, `UiSpacer`, `UiProgress`, `UiSurfaceCard`, `UiSwitch`, `UiCheckbox`, `UiSlider`, `UiAvatar`, `UiSkeleton`, animated `UiStepper` morphing `ADD` <-> `- qty +` with haptic hooks), `UiCelebrationOverlay` (`UiCelebrationBurst.kt`), `UiHapticFeedback.kt` (`UiHapticKind`, `LocalUiHapticFeedback`, `rememberComposeHapticFallback`), `MotionModifier.kt` (`applyMotionSpec`, `Modifier.shakeOnError`, `resolveMotionDurationMs`, `resolveMotionDelayMs`, `resolveEnterTransition`, `resolveExitTransition`) honoring `ThemeTokens.motion.reducedMotion`, `UiImageRenderer` / `LocalImageRenderer`, `LayoutConfigModifier`, `TokenResolvers`, plus `compose.uiTest` semantic tests (`UiAtomsComposeTest`). Depends only on `design-tokens` and `sdui-contracts`.
- **Layer 2 (`action-engine`, `network`)**:
  - `action-engine`: OCP **18 `ActionTypes`** (adding `open_url` (`OpenUrlPayload`), `share` (`SharePayload`), `request_permission` (`RequestPermissionPayload`), `biometric_auth` (`BiometricAuthPayload`) alongside `trigger_feedback` (`TriggerFeedbackPayload`), plus `ActionOutcome.{OpenUrlRequested, ShareRequested, PermissionRequested, BiometricAuthRequested, FeedbackTriggered, OverlayRequested}`), `ActionDecoderRegistry`, `ActionRegistry`, `DefaultActionDispatcher`, `ActionValidator`, and `ApiCallExecutor` port. Depends only on `core` and `sdui-contracts`.
  - `network`: Ktor `3.6.1` DIP client (`HttpTransport`, `KtorHttpTransport`, `HttpClientFactory`, `NetworkConfig`) with HTTP/2 ALPN (`ktor-client-okhttp` on JVM, `Darwin` on Apple, `Js` on Wasm), Brotli (`br`) decompression (`org.brotli:dec`), bearer token refresh (`AuthTokenProvider`), and `CertificatePinMatcher` shared SPKI/cert pinning for JVM/OkHttp (`CertificatePinner`) and Apple Darwin `NSURLSession` (`PlatformHttpEngine.apple.kt`) across iOS, macOS, and watchOS. Depends only on `core`.
- **Layer 3 (`sdui-layout`, `watch-companion`, `storybook`, `sample-app`)**:
  - `sdui-layout`: Orchestrates `LayoutController` (`observeConnectivity`, `handleDeepLink`, `isOffline`, `errorShakeSequence`, `errorShakeTargetId`, `dismissFeedback()`, `collectAllEnvelopes`), `PlatformBridgeBundle`, `PlatformBridgeOutcomeExecutor`, `AdaptiveScreenContainers.kt` (`AdaptiveReadyViewport`, `SplitPaneBody`, `OfflineStatusBanner`), `SnippetRegistry` (**14 snippet renderers** via `BuiltInSnippetModule` + responsive `ContainerSnippetRenderers.kt` (`Grid`/`Carousel`) + `ProductCardSnippetRenderer.kt`), `SduiScreen`, `FloatingCartDockHost`, `UiCelebrationOverlay`, `OverlayHost` (`ModalBottomSheet` & `AlertDialog`), `CachedScreenRepository`, `OfflineActionCoordinator`, `KtorImageRenderer`, `createSduiKoinModule` (`platformBridges`, `certificatePins`), `SduiHostRoot.kt` (`SduiHostedRoot`), iOS `createSduiViewController` + `AbetaSduiKit.xcframework`, macOS Cocoa `launchMacOsSduiWindow` (`SduiMacOsWindow.kt`), and `SduiScreenComposeTest`.
  - `watch-companion`: Headless wearable module (`WatchGlanceModels.kt`, `WatchScreenMapper`, `RemoteWatchPageSource`, `WatchRemoteApiCallExecutor`, `WatchCompanionController`, `createWatchCompanionModule`, `createAppleWatchCompanionHost`, `AbetaWatchKit.xcframework`).
  - `storybook`: Unpublished interactive Compose Multiplatform explorer featuring **12 stories across 4 categories** (`FOUNDATIONS`, `ATOMS`, `SNIPPETS`, `PLAYGROUND`) adding `ResponsiveAndBridgesStory` (`StoryCatalog.STORY_RESPONSIVE_BRIDGES`), responsive `CompactStoryStrip`/`StorySidebar` shell, `"All 14 Snippets"`, `MotionAndStoreStory`, `"store-home"` & `"watch-status"` in `LiveBffStory`, `fontScale` controls, Desktop JVM, WasmJS, macOS `launchStorybookMacOsWindow`, `StorybookViewController` + `AbetaStorybookKit.xcframework`, and `StorybookComposeTest`.
  - `sample-app`: Unpublished Compose Desktop & `--headless` / `--bff-url` demo composition root wired with `CachedScreenRepository`, `OfflineActionCoordinator`, `KtorImageRenderer`, `PlatformBridgeBundle` + `observeConnectivity`, `"containers"`, `"store-home"` (**Abeta Fresh** 8-Min Express with responsive `columnsByWidth` & `adaptiveLayout` in `SampleStoreScreen.kt` + `SampleStoreCatalog.kt`), `SampleWatchScreen` (`"watch-status"`), `OfflineApiCallExecutor` handling `POST /api/v1/orders/express`, and `SampleContainer.DEFAULT_SCREENS` covering **7 screens** (`home`, `store-home`, `catalog`, `profile`, `form-demo`, `containers`, `watch-status`).

---

## Key Architectural Rules

1. **No upward imports**: Layer 1 must never import `core`, `action-engine`, `network`, or `sdui-layout`.
2. **No sibling coupling in Layer 2**: `action-engine` and `network` do not depend on each other; `sdui-layout` and `watch-companion` bridge them via `RemoteApiCallExecutor` / `WatchRemoteApiCallExecutor`.
3. **Host binds concretes**: Concrete analytics trackers, OS bridge ports (`PlatformBridgeBundle`), certificate pins, navigation listeners, and BFF base URLs are wired at the composition root (`sample-app`, `createSduiKoinModule`, `createWatchCompanionModule`, `kmp/platforms/*`, or consumer app).
4. **ABI & Docs Hygiene**: All **10 publishable KMP library modules** validate binary compatibility via `apiCheck` (`apiDump`) and generate Dokka v2 HTML via `dokkaGenerateHtml`.

See `docs/architecture.md` in the repository for the complete dependency matrix.
