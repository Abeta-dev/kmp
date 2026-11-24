# abeta-dev/kmp

[![Live Portal](https://img.shields.io/badge/Live_Portal-GitHub_Pages-0ea5e9?logo=githubpages&logoColor=white)](https://abeta-dev.github.io/kmp/)
[![Live WasmJS Storybook](https://img.shields.io/badge/Live_Storybook-Compose_WasmJS-10b981?logo=kotlin&logoColor=white)](https://abeta-dev.github.io/kmp/storybook/)
[![KMP CI](https://github.com/abeta-dev/kmp/actions/workflows/kmp-ci.yml/badge.svg)](https://github.com/abeta-dev/kmp/actions/workflows/kmp-ci.yml)
[![BFF CI](https://github.com/abeta-dev/kmp/actions/workflows/bff-ci.yml/badge.svg)](https://github.com/abeta-dev/kmp/actions/workflows/bff-ci.yml)
[![Quality & Security](https://github.com/abeta-dev/kmp/actions/workflows/quality-security.yml/badge.svg)](https://github.com/abeta-dev/kmp/actions/workflows/quality-security.yml)
[![Deploy GitHub Pages](https://github.com/abeta-dev/kmp/actions/workflows/pages.yml/badge.svg)](https://github.com/abeta-dev/kmp/actions/workflows/pages.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

**Abeta KMP Kit (`dev.abeta.kmp`)** is an open-source, Server-Driven UI (SDUI) monorepo pairing a layered **Kotlin Multiplatform + Compose Multiplatform UI, Wearable & SDUI Kit (`kmp/`)** (**11 publishable library modules** plus `kmp-bom` and `dependencyManagement`) with a **Go 1.26.0 Clean Architecture SDUI Backend-for-Frontend (`bff/`)**.

Designed around **Clean Architecture**, the **Uncle Bob Dependency Rule**, **SOLID principles**, and strict **Atomic / Action decoupling**, the repository shares a 1:1 versioned JSON wire contract (`v1` — **29 Wire Atoms** incl. `RadioGroupAtom`, `SelectAtom`, `OtpInputAtom`, `ChipAtom`, `SegmentedControlAtom`, `RatingAtom`, `PriceAtom`, `BadgeAtom`, `AlertBannerAtom`, `StatTileAtom`, `KeyValueRowAtom`, `QrCodeAtom`, `CountdownAtom`, **31 Stateless Compose UI Atoms**, **21 Canonical Snippet Types** adding `nudge_bar`, `marquee_strip`, `order_tracker`, and the **`composed_block` Atomic Snippet Molding Engine** with **36 `ComposedNodeKind`s**, **18 Built-in Action Types**, declarative **`MotionSpec` (7 Ambient `MotionLoopEffect`s) / `FeedbackEffect`**, **`FloatingCartSpec` Free-Delivery Threshold Nudge**, and **responsive `AdaptivePageSpec`**) between the Go BFF and KMP client modules—powering a **7-in-1 Super-App Showcase Launcher** (**Abeta Fresh**, **Abeta Bites**, **Abeta Pulse**, **Abeta Mart**, **Abeta Spark**, **Abeta Trade**, **Abeta Pay**) and a **20-story End-to-End Storybook** (featuring **Live 7-in-1 Super-App Demos**, a complete **30-Atom Gallery**, **Animation & Nudges Lab**, **Atomic Snippet Crafter**, and **End-to-End App Journeys**)—managed through a published **Gradle BOM (`io.github.abeta-dev:bom`)**, **Gradle Version Catalog (`io.github.abeta-dev:dependencies`)**, and **Binary Compatibility Validator (`apiCheck`)**.

> **Live Portal & Interactive WasmJS Storybook**: Explore [**abeta-dev.github.io/kmp**](https://abeta-dev.github.io/kmp/) · [**Launch Live Compose WasmJS Storybook →**](https://abeta-dev.github.io/kmp/storybook/) · [**Dokka v2 API Reference**](https://abeta-dev.github.io/kmp/api/core/).
> **AI / LLM Documentation (`llmstxt.org`)**: Ingest [`llms.txt`](llms.txt) (concise documentation & skill index) or [`llms-full.txt`](llms-full.txt) (self-contained full technical reference).
> **Abeta SDUI Platform (3-Repo Ecosystem)**: **Abeta KMP Kit** (`Abeta-dev/kmp`, this repo) · [**Abeta Layout Engine**](https://github.com/Abeta-dev/layout-engine) ([`llms.txt`](https://raw.githubusercontent.com/Abeta-dev/layout-engine/main/llms.txt)) · [**Abeta SDUI BFF**](https://github.com/Abeta-dev/sdui-bff) ([`llms.txt`](https://raw.githubusercontent.com/Abeta-dev/sdui-bff/main/llms.txt)).

---

## Visual Showcase (Live Compose Multiplatform Renders)

Pixel-accurate Retina captures rendered directly from `kmp/sample-app` and `kmp/storybook` (full 19-screen gallery across the **7-in-1 Super-App Showcase** & core screens, with regeneration commands in [docs/demo-screenshots.md](docs/demo-screenshots.md)):

| Abeta Fresh Express — Desktop Split-Pane (`store-home`) | Interactive Storybook — All 21 Snippets (Dark Mode) |
| :---: | :---: |
| ![Abeta Fresh Desktop Split-Pane](docs/assets/screenshots/demo-store-home-desktop.png) | ![Storybook 21 Snippets Dark](docs/assets/screenshots/storybook-snippets-dark.png) |

| Phone Light (`412dp` 2-Col Grid) | Phone Dark (`412dp`) | Watch Glance (`watch-status`) |
| :---: | :---: | :---: |
| ![Phone Light](docs/assets/screenshots/demo-store-home-phone.png) | ![Phone Dark](docs/assets/screenshots/demo-store-home-dark.png) | ![Watch Status](docs/assets/screenshots/demo-watch-status.png) |

| Storybook — Motion & Quick-Commerce | Storybook — Responsive Breakpoints & OS Bridges |
| :---: | :---: |
| ![Storybook Motion Store](docs/assets/screenshots/storybook-motion-store.png) | ![Storybook Responsive Bridges](docs/assets/screenshots/storybook-responsive-bridges.png) |

---

## Architecture Overview

Every module belongs to a single architectural layer. Dependencies strictly point **inward** toward pure abstractions and wire contracts—never outward toward frameworks, transports, or concrete handlers.

```mermaid
flowchart TB
    subgraph BFF["Go 1.26.0 SDUI BFF (bff/)"]
        BFF_H["internal/handler (Gin + Compression/ETag + OpenAPI/Scalar + /orders/express + go-libs ginmw)"]
        BFF_U["internal/usecase (ScreenService, ActionService, CatalogService)"]
        BFF_D["internal/domain (Ports & Entities)"]
        BFF_I["internal/infra (Core + 7-in-1 Super-App Showcase Screens, DefaultActionExecutor, StaticTokenCatalogProvider, 9 Golden Fixtures)"]
        BFF_P["pkg/sdui/{actions,atoms,snippets,theming} (18 Action Types, 29 Wire Atoms, 21 Snippets incl. composed_block w/ 29 ComposedNodeKinds, 7 Motion Loops, AdaptivePageSpec)"]
        BFF_H --> BFF_U --> BFF_D
        BFF_I -. "implements" .-> BFF_D
        BFF_U --> BFF_P
        BFF_I --> BFF_P
    end

    subgraph KMP["Kotlin Multiplatform Kit (kmp/)"]
        L3["Layer 3: sdui-layout (21 Snippets incl. Atomic Snippet Molding Engine, LayoutController, PlatformBridgeBundle, AdaptiveScreenContainers, Koin, AbetaSduiKit, macOS Cocoa) | watch-companion (Headless WatchKit/Wear OS + AbetaWatchKit) | storybook (20 Stories incl. 8 UI Atom Stories, Live 7-in-1 Super-App Demos, Nudges Lab, Snippet Crafter & E2E Journeys) | sample-app (7-in-1 Super-App Showcase)"]
        L2["Layer 2: action-engine (18 ActionTypes, ActionDecoderRegistry, ActionRegistry, DefaultActionDispatcher) | network (Ktor HTTP/2 ALPN, Brotli, Bearer Refresh, CertificatePinMatcher JVM + Apple)"]
        L1["Layer 1: ui-atoms (30 Stateless Compose Atoms incl. 12 Extended Atoms, UiCountdown & UiRollingCounter + 7 MotionLoopEffects + pressBounce + shakeOnError + UiHapticFeedback + UiCelebrationOverlay) -> design-tokens (incl. ResponsiveTokens) & sdui-contracts (29 Wire Atoms, 21 Snippets, MotionSpec, AdaptiveContracts)"]
        L0["Layer 0: core (Primitives + OS Bridges/Ports, Keychain/AES-GCM, Biometrics, DeepLinkParser, LocaleFormatter) | state (SessionStore, PreferenceStore, SecurePersistentKeyValueStore, FeatureFlagProvider, OfflineMutationQueue) | analytics | database (Room 3.0.3 KMP SQLite, Bundled & Web OPFS)"]

        L3 --> L2
        L3 --> L1
        L3 --> L0
        L2 --> L0
        L2 --> L1
    end

    BFF_P <-- "ginmw.Envelope + JSON v1 Wire\n(PageResponse, AdaptivePageSpec, FloatingCartSpec Nudge, 21 Snippets, 18 Actions)" --> L3
```

---

## Module Matrix

### Supported Multiplatform Targets & Host Glue (`kmp/`)

- **Headless / Contract Publishable Modules** (`core`, `state`, `analytics`, `database`, `sdui-contracts`, `action-engine`, `network`, `watch-companion`): `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`, `wasmJs`.
- **Compose UI Modules** (`design-tokens`, `ui-atoms`, `sdui-layout`) and **`storybook`**: `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `wasmJs`.
- **`sample-app`**: JVM Desktop & `--headless` CLI.
- **Drop-in Reference Host Glue (`kmp/platforms/`)**:
  - `kmp/platforms/android/AndroidPlatformBridgeFactory.kt` & `kmp/platforms/android/AndroidDatabaseFactory.kt` — Android `ComponentActivity` / `FragmentActivity` bridge implementations (`VibrationEffect`, `ClipboardManager`, `Intent.ACTION_VIEW`, chooser share, `ConnectivityManager`, AES-GCM `AndroidKeyStore`, `BiometricPrompt`, `ActivityResultContracts`, push token hook, deep links, `SduiScreen`) plus Room 3 `AbetaDatabase` Android factory.
  - `kmp/platforms/wearos/WearCompanionHostActivity.kt` — Android Wear OS `ComponentActivity` wiring `:watch-companion`'s `WatchCompanionController` + `WatchGlancePage` into Wear Compose Material.
  - `kmp/platforms/apple/AbetaAppleHosts.swift` & `kmp/platforms/apple/Package.template.swift` — SwiftUI reference hosts for iOS/iPadOS (`AbetaSduiKit`), macOS (`launchMacOsSduiWindow`), watchOS (`AbetaWatchKit` `createAppleWatchCompanionHost`), and SwiftPM release manifest template.
  - `kmp/platforms/web/README.md` — Browser COOP/COEP Cross-Origin Isolation header guide for Room 3 `wasmJs` OPFS Web Worker persistence.
  - `kmp/platforms/README.md` — Notes that Android & Wear OS templates require an Android app module with `ANDROID_HOME` / Android SDK configured.

### KMP Modules (`kmp/`)

| Layer | Module | Maven Coordinate | Responsibility |
| :--- | :--- | :--- | :--- |
| **Catalog** | `dependencyManagement` | `io.github.abeta-dev:dependencies:0.5.0` | Centralized Gradle Version Catalog (`libs.versions.toml` & `internal.versions.toml`) |
| **Platform** | `kmp-bom` | `io.github.abeta-dev:bom:0.5.0` | Java Platform BOM aligning all **11 publishable `io.github.abeta-dev:*` modules** (Kotlin package `dev.abeta.kmp.*`) + `dependencies` |
| **Layer 0** | `core` | `io.github.abeta-dev:core:0.5.0` | `AppResult`, `AppError` (`ErrorCategory`, `isRetryable`, `TimeoutError`, `RateLimitError`), `DispatcherProvider`, `Clock`, `AppLogger`/`RedactingAppLogger`, `KeyValueStore`/`ObservableKeyValueStore`, `UuidGenerator`, `BackoffPolicy`, `SingleFlight`, `Debouncer`, `ClickBackpressureGuard`, `SensitiveDataRedactor`, `FieldValidators`, `PlatformInfo` (`PlatformKind.{WATCHOS, WEAR_OS, LINUX_NATIVE, WINDOWS_NATIVE}`), `PlatformCapabilities`/`currentPlatformCapabilities()`, `HapticEngine`/`HapticStyle`, `ClipboardPort`, `ExternalLauncherPort`, `SharePort`/`ShareRequest`, `ConnectivityMonitor`/`ConnectivityStatus`, `PermissionPort`, `LocationPort`, `FilePickerPort`, `PushNotificationPort`, `LocaleFormatter` (`formatCurrencyCents`, `currentLocaleTag`), `DeepLinkParser`/`DeepLinkTarget`, `SecureKeyValueStore` (`AppleKeychain` `SecItem*` w/ fallback, JVM AES-256-GCM + `Preferences`, WasmJS `sessionStorage`), `BiometricAuthenticator` (`LAContext` on Apple) |
| **Layer 0** | `state` | `io.github.abeta-dev:state:0.5.0` | Reactive `SessionStore` (`PersistentSessionStore`, `SessionLifecycleManager`), observable & schema-versioned `PreferenceStore` (`multiplatform-settings`), `createSecurePersistentKeyValueStore(namespace)` in `SettingsKeyValueStore.kt`, `FeatureFlagProvider`, and `OfflineMutationQueue` |
| **Layer 0** | `analytics` | `io.github.abeta-dev:analytics:0.5.0` | `AnalyticsEvent`, `AnalyticsTracker`, `CompositeAnalyticsTracker`, `LoggerAnalyticsTracker`, `RecordingAnalyticsTracker`, `SanitizingAnalyticsTracker`, `DeduplicatingAnalyticsTracker`, `EnrichingAnalyticsTracker`, `BufferedAnalyticsTracker` |
| **Layer 0** | `database` | `io.github.abeta-dev:database:0.5.0` | Room 3 (`3.0.3`) KMP local SQLite database (`AbetaDatabase`, `AbetaLocalDatabase`, `cached_screens`, `offline_mutations`, `key_value_records`, `sqlite-bundled:2.7.1` on JVM/Android/Apple, `sqlite-web:2.7.1` OPFS Web Worker on WasmJS, KSP `2.3.14` schema export under `kmp/database/schemas/`; see [docs/local-database-room3.md](docs/local-database-room3.md)) |
| **Layer 1** | `design-tokens` | `io.github.abeta-dev:design-tokens:0.5.0` | Owns all design tokens: `ColorTokens`, `DesignPalette`, `SpacingTokens`, `CornerRadiusTokens`, `StrokeWidthTokens`, `TypographyTokens`, `ElevationTokens`, `MotionTokens`, `OpacityTokens`, `SizingTokens`, `ThemePreset`, `ColorAccessibility`, `DesignTheme`, `ThemeTokens`, and `ResponsiveTokens.kt` (`WindowWidthClass`, `WindowHeightClass`, `DeviceFormFactor`, `AdaptiveLayoutMetrics`, `resolveAdaptiveLayoutMetrics`, `LocalAdaptiveLayoutMetrics`, `ProvideAdaptiveLayoutMetrics`) exposed via `ThemeTokens.adaptiveMetrics` |
| **Layer 1** | `sdui-contracts` | `io.github.abeta-dev:sdui-contracts:0.5.0` | Pure `@Serializable` wire DTOs (`PageResponse` with `PaginationSpec`, `pullToRefresh`, `floatingCart` / `FloatingCartSpec` with free-delivery threshold nudge fields, and `adaptiveLayout` / `AdaptivePageSpec`, `SnippetEnvelope` with optional `motion`, **21 `SnippetTypes`** incl. `product_card`, `swipe_deck`, `search_bar`, `chip_group`, plus `NudgeAndComposedContracts.kt`: `nudge_bar` / `NudgeBarSnippetData`, `marquee_strip` / `MarqueeStripSnippetData`, `order_tracker` / `OrderTrackerSnippetData`, and **`composed_block`** / `ComposedBlockSnippetData` + **36 `ComposedNodeKind`s**, **29 Wire Atoms** incl. `ExtendedAtoms.kt` (`RadioGroupAtom`, `SelectAtom`, `OtpInputAtom`, `ChipAtom`, `SegmentedControlAtom`, `RatingAtom`, `PriceAtom`, `BadgeAtom`, `AlertBannerAtom`, `StatTileAtom`, `KeyValueRowAtom`, `QrCodeAtom`), `AdaptiveContracts.kt`, `MotionContracts.kt` + `AtomEnums.kt` with **7 Ambient `MotionLoopEffect`s** (`pulse`, `shimmer`, `wiggle`, `float`, `heartbeat`, `glow_ring`, `marquee`), `FeedbackEffect`, `OsPermissionKind`, `ScreenLayoutMode`, `OverlayPresentation`, `TabItemSpec.title: String`, `LayoutConfig`, `ColorTokenRef`, `TypographyTokenRef`, `ActionRef`, `ValidationRuleContract`, `TrackingContract`, `SduiJson`) |
| **Layer 1** | `ui-atoms` | `io.github.abeta-dev:ui-atoms:0.5.0` | **31 stateless Compose Multiplatform atoms** (`UiText`, `UiIcon`, `UiTag`, `UiButton`, `UiImage`, `UiTextField`, `UiDivider`, `UiSpacer`, `UiProgress`, `UiSurfaceCard`, `UiSwitch`, `UiCheckbox`, `UiSlider`, `UiAvatar`, `UiSkeleton`, animated `UiStepper`, live urgency `UiCountdown`, odometer `UiRollingCounter`, plus 12 extended atoms: `UiRadioGroup`, `UiSelect`, `UiOtpInput`, `UiChip`, `UiSegmentedControl`, `UiRating`, `UiPrice`, `UiBadge`, `UiAlertBanner`, `UiStatTile`, `UiKeyValueRow`, `UiQrCode`), `UiCelebrationOverlay` (`UiCelebrationBurst.kt`), `UiHapticFeedback.kt`, `MotionModifier.kt` + `MotionLoopModifiers.kt` (`applyMotionSpec`, **7 Ambient `MotionLoopEffect`s**, `Modifier.pressBounce`, `Modifier.shakeOnError`) honoring `ThemeTokens.motion.reducedMotion`, `UiImageRenderer`/`LocalImageRenderer`, `LayoutConfigModifier`, `TokenResolvers`, `compose.uiTest` semantic tests (`UiAtomsComposeTest`, `ExtendedAtomsComposeTest`) |
| **Layer 2** | `action-engine` | `io.github.abeta-dev:action-engine:0.5.0` | OCP `ActionTypes` (**18 built-in types** adding `open_url` / `OpenUrlPayload`, `share` / `SharePayload`, `request_permission` / `RequestPermissionPayload`, `biometric_auth` / `BiometricAuthPayload` alongside `trigger_feedback` / `TriggerFeedbackPayload`), `ActionPayloadDecoder`, `ActionDecoderRegistry`, `ActionHandler`, `ActionOutcome` (`OpenUrlRequested`, `ShareRequested`, `PermissionRequested`, `BiometricAuthRequested`, `FeedbackTriggered`, `OverlayRequested`), `ApiCallExecutor`, `ActionRegistry`, `DefaultActionDispatcher`, `ActionValidator` |
| **Layer 2** | `network` | `io.github.abeta-dev:network:0.5.0` | DIP-compliant Ktor 3.6.1 client (`HttpTransport`, `KtorHttpTransport`, `HttpClientFactory`, `NetworkConfig`, `NetworkPorts`) with HTTP/2 ALPN, Brotli (`br`) decompression (`org.brotli:dec`), bearer token refresh (`AuthTokenProvider`), and `CertificatePinMatcher` shared SPKI/cert pinning for JVM/OkHttp (`CertificatePinner`) and Apple Darwin `NSURLSession` (`PlatformHttpEngine.apple.kt`) across iOS, macOS, and watchOS |
| **Layer 3** | `sdui-layout` | `io.github.abeta-dev:sdui-layout:0.5.0` | SDUI orchestrator (`LayoutController` with `observeConnectivity`, `handleDeepLink`, `isOffline`, `errorShakeSequence`, `errorShakeTargetId`, `dismissFeedback()`, `collectAllEnvelopes`), `PlatformBridgeBundle`, `PlatformBridgeOutcomeExecutor`, `AdaptiveScreenContainers.kt` (`AdaptiveReadyViewport`, `SplitPaneBody`, `OfflineStatusBanner`), **21 snippet renderers** via `BuiltInSnippetModule` (including `NudgeBarSnippetRenderer`, `MarqueeStripSnippetRenderer`, `OrderTrackerSnippetRenderer`, and **`ComposedBlockSnippetRenderer`** Atomic Snippet Molding Engine with **36 `ComposedNodeKind`s**), `FloatingCartDockHost` (with live free-delivery threshold nudge progress & unlock bounce) + `PageCartCalculator`, `UiCelebrationOverlay`, `ModalBottomSheet` & `AlertDialog`, `PullToRefreshBox`, `SkeletonScreenPlaceholder`, `loadMore()`, `CachedScreenRepository`, `OfflineActionCoordinator`, `KtorImageRenderer`, `createSduiKoinModule` (`platformBridges`, `certificatePins`), `SduiHostRoot.kt` (`SduiHostedRoot`), iOS `createSduiViewController` + `AbetaSduiKit.xcframework`, macOS native Cocoa `launchMacOsSduiWindow` (`SduiMacOsWindow.kt`), `SduiScreenComposeTest` |
| **Layer 3** | `watch-companion` | `io.github.abeta-dev:watch-companion:0.5.0` | Headless wearable SDUI companion module (`WatchGlanceModels.kt`, `WatchScreenMapper`, `RemoteWatchPageSource`, `WatchRemoteApiCallExecutor`, `WatchCompanionController`, `createWatchCompanionModule`, `createAppleWatchCompanionHost`) assembling `AbetaWatchKit.xcframework` (`watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`) and supporting Wear OS JVM hosts |
| **Explorer** | `storybook` | *(Unpublished Explorer)* | Interactive Compose Multiplatform Storybook (**20 stories across 5 categories**: `LIVE_DEMOS`, `FOUNDATIONS`, `ATOMS` (8 stories covering all 30 atoms), `SNIPPETS`, `PLAYGROUND`, featuring **Live 7-in-1 Super-App Demos** `super-app-demos` with multi-step checkout/tracker/chat/quotes/biometric-pay workflows & `?story=<id>&app=<screenId>` deep linking, `"All 21 Snippets"`, `ResponsiveAndBridgesStory`, `DiscoveryAndSearchStory`, **Animation & Nudges Lab** `animation-nudges`, **Atomic Snippet Crafter** `snippet-crafter`, and **End-to-End App Journeys** `e2e-journeys`), responsive `CompactStoryStrip`/`StorySidebar` shell, `MotionAndStoreStory`, Built-in Demo + Remote Go BFF toggle with 7-app Super-App slugs & `"watch-status"` in `LiveBffStory`, `fontScale` controls, Desktop JVM, WasmJS Browser, macOS `launchStorybookMacOsWindow`, `StorybookViewController` + `AbetaStorybookKit.xcframework`, `StorybookComposeTest` |
| **App** | `sample-app` | *(Unpublished Demo)* | Runnable Compose Desktop & `--headless` / `--verticals` CLI demo featuring the **7-in-1 Super-App Showcase Launcher** (**Abeta Fresh**, **Abeta Bites**, **Abeta Pulse**, **Abeta Mart**, **Abeta Spark**, **Abeta Trade**, **Abeta Pay**) enriched with live `marquee_strip`, urgency `nudge_bar` + `CountdownAtom`, animated `order_tracker` (`#BTS-4091`, `#PAY-3081`), molded `composed_block` cards, biometric P2P transfer, and `FloatingCartSpec` free-delivery threshold nudges, alongside core screens (`home`, `store-home`, `catalog`, `profile`, `form-demo`, `containers`, `watch-status`), `PlatformBridgeBundle` + `observeConnectivity`, `OfflineApiCallExecutor`, and `--bff-url` |

### Go BFF (`bff/`)

| Package | Responsibility |
| :--- | :--- |
| `cmd/bff` | Application entrypoint (`go run ./cmd/bff`), env config (`go-libs/env`), `http.Server.Protocols` (`HTTP/1.1`, `h2c`, TLS HTTP/2), graceful shutdown (`go-libs/shutdown`) |
| `internal/domain` | Pure Go ports (`ScreenRepository`, `ActionExecutor`, `TokenCatalogProvider`) and domain models (zero Gin/HTTP imports, enforced by `arch_test.go`) |
| `internal/usecase` | `ScreenService`, `ActionService` (including declarative `ValidationRule` enforcement), and `CatalogService` |
| `internal/handler` | Gin router (`NewRouter` in `router.go` adding `POST /api/v1/orders/express`) with `github.com/abeta-dev/go-libs@v0.4.0` (`ginmw`), `CompressionMiddleware()` (`br` via `github.com/andybalholm/brotli` + `gzip`), `ETagMiddleware()` (SHA-256 `ETag` + `304`), OpenAPI 3.1 (`openapi.go` at `GET /openapi.json`) & Scalar UI (`GET /docs`), optional `ginmw.GlobalRateLimit()`, optional `ginmw.AuthMiddleware()` (`JWT_SECRET`), `/healthz`, `/readyz`, `/metrics`, and `/api/v1/*` |
| `internal/infra` | Concrete adapters: `InMemoryScreenRepository` (core screens `catalog`, `containers`, `form-demo`, `home`, `profile`, `store-home`, `watch-status` + **7-in-1 Super-App Showcase** screens `fresh-home`, `bites-home`, `pulse-home`, `mart-home`, `spark-deck`, `trade-home`, `pay-home`), `DefaultActionExecutor` (`action_executor.go` handling all 18 actions incl. `open_url`, `share`, `request_permission`, `biometric_auth`, `trigger_feedback`, & `POST /api/v1/orders/express` returning `celebration` + `show_toast` follow-ups), `StaticTokenCatalogProvider`, plus **9 golden parity tests** (`home_screen.json`, `form_screen.json`, `action_batch.json`, `containers_screen.json`, `store_home.json`, `interactive_screen.json`, `nudges_and_composed_screen.json`, `extended_atoms_screen.json`) |
| `pkg/sdui/{actions,atoms,snippets,theming}` | Shared v1 SDUI wire types & builders (`bff@v0.7.0` — **29 Wire Atoms** incl. `extended_atoms.go`, **21 Snippet Types** incl. `product_card`, `swipe_deck`, `search_bar`, `chip_group`, `nudge_bar`, `marquee_strip`, `order_tracker`, and **`composed_block`** with **36 `ComposedNodeKind`s** + server-molded recipe helpers, **18 Action Types**, `MotionSpec` with **7 Ambient `MotionLoopEffect`s**, `FloatingCartSpec` with free-delivery threshold nudges, `PaginationSpec`, `PullToRefresh`, `OverlayPresentation`, `ResponsiveColumnsSpec`, `ResponsiveIntSpec`, `AdaptivePageSpec`, `ScreenLayoutMode`, `TabItemSpec.Title` as `string`) matching `kmp/sdui-contracts`, `kmp/action-engine`, and `kmp/design-tokens` 1:1 |

---

## Gradle BOM Quick Start

Consumers align all `io.github.abeta-dev` Maven Central modules (Kotlin package namespace `dev.abeta.kmp.*`) through the published BOM (`io.github.abeta-dev:bom:0.5.0`) and optional version catalog (`io.github.abeta-dev:dependencies:0.5.0`) — downloadable anonymously with **zero credentials required** via **Maven Central** (stable) or **Cloudsmith Open Source** (snapshots & mirrors).

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        // Optional — anonymous public snapshots / mirror via Cloudsmith Open Source:
        // maven("https://dl.cloudsmith.io/public/abeta-dev/kmp/maven/")
    }
    versionCatalogs {
        create("abetaKmp") {
            from("io.github.abeta-dev:dependencies:0.5.0")
        }
    }
}
```

```kotlin
// build.gradle.kts (commonMain)
dependencies {
    implementation(platform("io.github.abeta-dev:bom:0.5.0"))
    implementation("io.github.abeta-dev:database") // Optional Room 3 KMP local SQLite DB
    implementation("io.github.abeta-dev:design-tokens")
    implementation("io.github.abeta-dev:sdui-contracts")
    implementation("io.github.abeta-dev:ui-atoms")
    implementation("io.github.abeta-dev:action-engine")
    implementation("io.github.abeta-dev:network")
    implementation("io.github.abeta-dev:sdui-layout")
    // Optional headless wearable companion:
    // implementation("io.github.abeta-dev:watch-companion")
}
```

Inside this monorepo, `abeta.kmpLocalBuild=true` (set in `kmp/gradle.properties`) automatically substitutes `io.github.abeta-dev:*` module coordinates with local Gradle project references. Pass `-Pabeta.kmpLocalBuild=false` to resolve against published artifacts instead. See [docs/gradle-bom-guide.md](docs/gradle-bom-guide.md) and [docs/publishing-and-hosting.md](docs/publishing-and-hosting.md) for Maven Central, Cloudsmith Open Source, SwiftPM XCFrameworks, and Go BFF (`proxy.golang.org`) publishing details.

---

## Quick Start

### Prerequisites

- **JDK 21** (Gradle 9.7.0 wrapper included under `kmp/`)
- **Go 1.26.0** (for `bff/`)
- **Optional Android / Wear OS consumers**: An Android app module with `ANDROID_HOME` / Android SDK configured if integrating the reference host templates under `kmp/platforms/android` or `kmp/platforms/wearos`

### Run the Go SDUI BFF

```bash
cd bff
go vet ./...
go test -race -cover ./...
go run ./cmd/bff
# Interactive API docs at http://localhost:8080/docs (OpenAPI 3.1 at /openapi.json)
```

### Build, Verify, Docs & Run Showcase (`kmp/`)

```bash
cd kmp

# Format, static analysis, ABI apiCheck, unit, golden & compose.uiTest suites
./gradlew check

# Binary Compatibility Validator baseline regeneration (when public APIs change)
./gradlew apiDump

# Dokka v2 HTML API documentation (single module or all modules)
./gradlew :core:dokkaGenerateHtml
./gradlew dokkaGenerateHtml

# Headless smoke verification (covers home, form-demo, containers, and store-home + celebration)
./gradlew :sample-app:run --args="--headless"

# Launch Desktop Showcase connected to a running BFF
./gradlew :sample-app:run --args="--bff-url http://localhost:8080"

# Launch Interactive Storybook (Desktop JVM or WasmJS Browser)
./gradlew :storybook:run
./gradlew :storybook:wasmJsBrowserDevelopmentRun

# Package Apple XCFrameworks (macOS)
./gradlew :sdui-layout:assembleAbetaSduiKitXCFramework \
  :watch-companion:assembleAbetaWatchKitXCFramework \
  :storybook:assembleAbetaStorybookKitXCFramework
```

### Run Full Monorepo Verification

```bash
./scripts/verify_all.sh
```

---

## Documentation, Wikis, Skills, Code Owners & Multi-IDE Support

- **AI / LLM Context & Abeta SDUI Platform**:
  - [`llms.txt`](llms.txt) — `llmstxt.org`-compliant documentation, skill, and Code Owner index
  - [`llms-full.txt`](llms-full.txt) — Self-contained technical reference for LLM/AI ingestion (architecture, 11 KMP modules, Wire Protocol `v1`, installation, showcase & commands)
  - Sibling repositories: [`Abeta-dev/layout-engine`](https://github.com/Abeta-dev/layout-engine) ([`llms.txt`](https://raw.githubusercontent.com/Abeta-dev/layout-engine/main/llms.txt)) | [`Abeta-dev/sdui-bff`](https://github.com/Abeta-dev/sdui-bff) ([`llms.txt`](https://raw.githubusercontent.com/Abeta-dev/sdui-bff/main/llms.txt))
- **Technical Documentation (`docs/`)**:
  - [Architecture & Dependency Rule](docs/architecture.md)
  - [Atomic & Action Separation](docs/atomic-and-action-separation.md)
  - [Gradle BOM & Version Catalog Guide](docs/gradle-bom-guide.md)
  - [Multiplatform Local Database Guide (Room 3 + SQLite)](docs/local-database-room3.md)
  - [Open Package Hosting & Release Publishing Guide](docs/publishing-and-hosting.md)
  - [SDUI Wire Protocol (`v1`)](docs/sdui-wire-protocol.md)
  - [Go BFF Integration Guide](docs/bff-integration.md)
  - [Per-Module Code Owners & CI/CD Governance](docs/module-owners-and-cicd.md)
  - [Demo & Storybook Visual Showcase (Screenshots)](docs/demo-screenshots.md)
  - [IDE & Multi-Model AI Setup (Claude, Gemini, Antigravity, Cursor, Android Studio, Copilot)](docs/ide-and-ai-setup.md)
- **GitHub Wiki Pages (`wiki/`)**:
  - [Wiki Home](wiki/Home.md) | [Sidebar](wiki/_Sidebar.md)
- **Agent Skills (`skills/` — 10 Skills)**:
  - [abeta-kmp-kit](skills/abeta-kmp-kit/SKILL.md) — Umbrella workflow router
  - [abeta-kmp-usage](skills/abeta-kmp-usage/SKILL.md) — Repo onboarding, Gradle BOM & OCP extensions
  - [abeta-kmp-demo](skills/abeta-kmp-demo/SKILL.md) — 7-in-1 Super-App Showcase Demo & 20-story End-to-End Storybook reference guide
  - [abeta-kmp-platforms](skills/abeta-kmp-platforms/SKILL.md) — Android, Wear OS, iOS/iPadOS, watchOS, macOS, Desktop & WasmJS hosts & OS bridges
  - [abeta-kmp-atomic](skills/abeta-kmp-atomic/SKILL.md) — Tokens, responsive/wire DTOs & stateless Compose atoms
  - [abeta-kmp-actions](skills/abeta-kmp-actions/SKILL.md) — OCP action decoders, handlers, OS bridge outcomes & registry
  - [abeta-kmp-bff](skills/abeta-kmp-bff/SKILL.md) — Go BFF screens, snippets & golden parity
  - [abeta-kmp-architecture](skills/abeta-kmp-architecture/SKILL.md) — SOLID, Dependency Rule & BOM enforcement
  - [abeta-kmp-module-review](skills/abeta-kmp-module-review/SKILL.md) — Per-module Code Owner PR reviews
  - [abeta-kmp-cicd](skills/abeta-kmp-cicd/SKILL.md) — CI/CD workflows, automated PR owner gates & SemVer releases
- **Agent Personas & Per-Module Code Owners (`agents/` + `.github/CODEOWNERS`)**:
  - Core personas: [kmp-architect](agents/kmp-architect.md) | [atomic-designer](agents/atomic-designer.md) | [action-engine-engineer](agents/action-engine-engineer.md) | [bff-go-engineer](agents/bff-go-engineer.md) | [solid-reviewer](agents/solid-reviewer.md)
  - **17 Per-Module Code Owner Agents**: [`agents/owners/`](agents/owners/) paired with [`.github/CODEOWNERS`](.github/CODEOWNERS) and enforced in CI by [`module-owner-architecture-review.yml`](.github/workflows/module-owner-architecture-review.yml)
- **Multi-IDE & Multi-Model Configs**:
  - `AGENTS.md` | `CLAUDE.md` + `.claude/` | `GEMINI.md` + `.gemini/` + `.agent/` | `.cursor/rules/` | `.junie/guidelines.md` + `.aiignore` (Android Studio / JetBrains) | `.github/copilot-instructions.md` + `.github/instructions/`
- **Community & Governance**:
  - [Contributing](CONTRIBUTING.md) | [Code of Conduct](CODE_OF_CONDUCT.md) | [Security Policy](SECURITY.md) | [Changelog](CHANGELOG.md) | [License](LICENSE)
