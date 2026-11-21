# abeta-dev/kmp

[![KMP CI](https://github.com/abeta-dev/kmp/actions/workflows/kmp-ci.yml/badge.svg)](https://github.com/abeta-dev/kmp/actions/workflows/kmp-ci.yml)
[![BFF CI](https://github.com/abeta-dev/kmp/actions/workflows/bff-ci.yml/badge.svg)](https://github.com/abeta-dev/kmp/actions/workflows/bff-ci.yml)
[![Quality & Security](https://github.com/abeta-dev/kmp/actions/workflows/quality-security.yml/badge.svg)](https://github.com/abeta-dev/kmp/actions/workflows/quality-security.yml)
[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)

**Abeta KMP Kit (`dev.abeta.kmp`)** is an open-source, Server-Driven UI (SDUI) monorepo pairing a layered **Kotlin Multiplatform + Compose Multiplatform UI, Wearable & SDUI Kit (`kmp/`)** (**11 publishable library modules** plus `kmp-bom` and `dependencyManagement`) with a **Go 1.26.0 Clean Architecture SDUI Backend-for-Frontend (`bff/`)**.

Designed around **Clean Architecture**, the **Uncle Bob Dependency Rule**, **SOLID principles**, and strict **Atomic / Action decoupling**, the repository shares a 1:1 versioned JSON wire contract (`v1` — **15 Wire Atoms**, **17 Standard Snippet Types** incl. `swipe_deck`, `search_bar`, and `chip_group`, **18 Built-in Action Types**, declarative **`MotionSpec` / `FeedbackEffect`**, and **responsive `AdaptivePageSpec`**) between the Go BFF and KMP client modules—powering a **6-in-1 Super-App Showcase Launcher** (**Abeta Fresh**, **Abeta Bites**, **Abeta Pulse**, **Abeta Mart**, **Abeta Spark**, **Abeta Trade**) and a **13-story Storybook**—managed through a published **Gradle BOM (`dev.abeta.kmp:bom`)**, **Gradle Version Catalog (`dev.abeta.kmp:dependencies`)**, and **Binary Compatibility Validator (`apiCheck`)**.

> **AI / LLM Documentation (`llmstxt.org`)**: Ingest [`llms.txt`](llms.txt) (concise documentation & skill index) or [`llms-full.txt`](llms-full.txt) (self-contained full technical reference).
> **Abeta SDUI Platform (3-Repo Ecosystem)**: **Abeta KMP Kit** (`Abeta-dev/kmp`, this repo) · [**Abeta Layout Engine**](https://github.com/Abeta-dev/layout-engine) ([`llms.txt`](https://raw.githubusercontent.com/Abeta-dev/layout-engine/main/llms.txt)) · [**Abeta SDUI BFF**](https://github.com/Abeta-dev/sdui-bff) ([`llms.txt`](https://raw.githubusercontent.com/Abeta-dev/sdui-bff/main/llms.txt)).

---

## Visual Showcase (Live Compose Multiplatform Renders)

Pixel-accurate Retina captures rendered directly from `kmp/sample-app` and `kmp/storybook` (full 19-screen gallery including all **6-in-1 Super-App Showcase verticals** & regeneration commands in [docs/demo-screenshots.md](docs/demo-screenshots.md)):

| Abeta Fresh Express — Desktop Split-Pane (`store-home`) | Interactive Storybook — All 17 Snippets (Dark Mode) |
| :---: | :---: |
| ![Abeta Fresh Desktop Split-Pane](docs/assets/screenshots/demo-store-home-desktop.png) | ![Storybook 17 Snippets Dark](docs/assets/screenshots/storybook-snippets-dark.png) |

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
        BFF_I["internal/infra (Core + 6-in-1 Super-App Showcase Screens, DefaultActionExecutor, StaticTokenCatalogProvider, Golden Fixtures)"]
        BFF_P["pkg/sdui/{actions,atoms,snippets,theming} (18 Action Types, 15 Atoms, 17 Snippets, AdaptivePageSpec)"]
        BFF_H --> BFF_U --> BFF_D
        BFF_I -. "implements" .-> BFF_D
        BFF_U --> BFF_P
        BFF_I --> BFF_P
    end

    subgraph KMP["Kotlin Multiplatform Kit (kmp/)"]
        L3["Layer 3: sdui-layout (17 Snippets, LayoutController, PlatformBridgeBundle, AdaptiveScreenContainers, Koin, AbetaSduiKit, macOS Cocoa) | watch-companion (Headless WatchKit/Wear OS + AbetaWatchKit) | storybook (13 Stories, Desktop/WasmJS/macOS/AbetaStorybookKit) | sample-app (6-in-1 Super-App Showcase)"]
        L2["Layer 2: action-engine (18 ActionTypes, ActionDecoderRegistry, ActionRegistry, DefaultActionDispatcher) | network (Ktor HTTP/2 ALPN, Brotli, Bearer Refresh, CertificatePinMatcher JVM + Apple)"]
        L1["Layer 1: ui-atoms (16 Stateless Compose Atoms + MotionModifier + shakeOnError + UiHapticFeedback + UiCelebrationOverlay) -> design-tokens (incl. ResponsiveTokens) & sdui-contracts (15 Wire Atoms, 17 Snippets, MotionSpec, AdaptiveContracts)"]
        L0["Layer 0: core (Primitives + OS Bridges/Ports, Keychain/AES-GCM, Biometrics, DeepLinkParser, LocaleFormatter) | state (SessionStore, PreferenceStore, SecurePersistentKeyValueStore, FeatureFlagProvider, OfflineMutationQueue) | analytics | database (Room 3.0.3 KMP SQLite, Bundled & Web OPFS)"]

        L3 --> L2
        L3 --> L1
        L3 --> L0
        L2 --> L0
        L2 --> L1
    end

    BFF_P <-- "ginmw.Envelope + JSON v1 Wire\n(PageResponse, AdaptivePageSpec, FloatingCartSpec, 17 Snippets, 18 Actions)" --> L3
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
| **Catalog** | `dependencyManagement` | `dev.abeta.kmp:dependencies:0.1.0` | Centralized Gradle Version Catalog (`libs.versions.toml` & `internal.versions.toml`) |
| **Platform** | `kmp-bom` | `dev.abeta.kmp:bom:0.1.0` | Java Platform BOM aligning all **11 publishable `dev.abeta.kmp:*` modules** + `dependencies` |
| **Layer 0** | `core` | `dev.abeta.kmp:core:0.1.0` | `AppResult`, `AppError` (`ErrorCategory`, `isRetryable`, `TimeoutError`, `RateLimitError`), `DispatcherProvider`, `Clock`, `AppLogger`/`RedactingAppLogger`, `KeyValueStore`/`ObservableKeyValueStore`, `UuidGenerator`, `BackoffPolicy`, `SingleFlight`, `Debouncer`, `ClickBackpressureGuard`, `SensitiveDataRedactor`, `FieldValidators`, `PlatformInfo` (`PlatformKind.{WATCHOS, WEAR_OS, LINUX_NATIVE, WINDOWS_NATIVE}`), `PlatformCapabilities`/`currentPlatformCapabilities()`, `HapticEngine`/`HapticStyle`, `ClipboardPort`, `ExternalLauncherPort`, `SharePort`/`ShareRequest`, `ConnectivityMonitor`/`ConnectivityStatus`, `PermissionPort`, `LocationPort`, `FilePickerPort`, `PushNotificationPort`, `LocaleFormatter` (`formatCurrencyCents`, `currentLocaleTag`), `DeepLinkParser`/`DeepLinkTarget`, `SecureKeyValueStore` (`AppleKeychain` `SecItem*` w/ fallback, JVM AES-256-GCM + `Preferences`, WasmJS `sessionStorage`), `BiometricAuthenticator` (`LAContext` on Apple) |
| **Layer 0** | `state` | `dev.abeta.kmp:state:0.1.0` | Reactive `SessionStore` (`PersistentSessionStore`, `SessionLifecycleManager`), observable & schema-versioned `PreferenceStore` (`multiplatform-settings`), `createSecurePersistentKeyValueStore(namespace)` in `SettingsKeyValueStore.kt`, `FeatureFlagProvider`, and `OfflineMutationQueue` |
| **Layer 0** | `analytics` | `dev.abeta.kmp:analytics:0.1.0` | `AnalyticsEvent`, `AnalyticsTracker`, `CompositeAnalyticsTracker`, `LoggerAnalyticsTracker`, `RecordingAnalyticsTracker`, `SanitizingAnalyticsTracker`, `DeduplicatingAnalyticsTracker`, `EnrichingAnalyticsTracker`, `BufferedAnalyticsTracker` |
| **Layer 0** | `database` | `dev.abeta.kmp:database:0.1.0` | Room 3 (`3.0.3`) KMP local SQLite database (`AbetaDatabase`, `AbetaLocalDatabase`, `cached_screens`, `offline_mutations`, `key_value_records`, `sqlite-bundled:2.7.1` on JVM/Android/Apple, `sqlite-web:2.7.1` OPFS Web Worker on WasmJS, KSP `2.3.14` schema export under `kmp/database/schemas/`; see [docs/local-database-room3.md](docs/local-database-room3.md)) |
| **Layer 1** | `design-tokens` | `dev.abeta.kmp:design-tokens:0.1.0` | Owns all design tokens: `ColorTokens`, `DesignPalette`, `SpacingTokens`, `CornerRadiusTokens`, `StrokeWidthTokens`, `TypographyTokens`, `ElevationTokens`, `MotionTokens`, `OpacityTokens`, `SizingTokens`, `ThemePreset`, `ColorAccessibility`, `DesignTheme`, `ThemeTokens`, and `ResponsiveTokens.kt` (`WindowWidthClass`, `WindowHeightClass`, `DeviceFormFactor`, `AdaptiveLayoutMetrics`, `resolveAdaptiveLayoutMetrics`, `LocalAdaptiveLayoutMetrics`, `ProvideAdaptiveLayoutMetrics`) exposed via `ThemeTokens.adaptiveMetrics` |
| **Layer 1** | `sdui-contracts` | `dev.abeta.kmp:sdui-contracts:0.1.0` | Pure `@Serializable` wire DTOs (`PageResponse` with `PaginationSpec`, `pullToRefresh`, `floatingCart` / `FloatingCartSpec`, and `adaptiveLayout` / `AdaptivePageSpec`, `SnippetEnvelope` with optional `motion`, **17 `SnippetTypes`** incl. `product_card` / `ProductCardSnippetData`, `swipe_deck` / `SwipeDeckSnippetData`, `search_bar` / `SearchBarSnippetData`, `chip_group` / `ChipGroupSnippetData`, `CarouselSnippetData.itemWidthByWidth`, `GridSnippetData.columnsByWidth`, **15 Wire Atoms** incl. `StepperAtom`, `AdaptiveContracts.kt` (`ResponsiveColumnsSpec`, `ResponsiveIntSpec`, `AdaptivePageSpec`), `MotionContracts.kt` + `AtomEnums.kt` (`MotionSpec`, `MotionTransition`, `MotionDurationToken`, `MotionLoopEffect`, `FeedbackEffect`, `OsPermissionKind`, `ScreenLayoutMode`), `OverlayPresentation`, `TabItemSpec.title: String`, `LayoutConfig`, `ColorTokenRef`, `TypographyTokenRef`, `ActionRef`, `ValidationRuleContract`, `TrackingContract`, `SduiJson`) |
| **Layer 1** | `ui-atoms` | `dev.abeta.kmp:ui-atoms:0.1.0` | **16 stateless Compose Multiplatform atoms** (`UiText`, `UiIcon`, `UiTag`, `UiButton`, `UiImage`, `UiTextField`, `UiDivider`, `UiSpacer`, `UiProgress`, `UiSurfaceCard`, `UiSwitch`, `UiCheckbox`, `UiSlider`, `UiAvatar`, `UiSkeleton`, animated `UiStepper` morphing `ADD` <-> `- qty +` with haptic hooks), `UiCelebrationOverlay` (`UiCelebrationBurst.kt`), `UiHapticFeedback.kt` (`UiHapticKind`, `LocalUiHapticFeedback`, `rememberComposeHapticFallback`), `MotionModifier.kt` (`applyMotionSpec`, `Modifier.shakeOnError`, `resolveMotionDurationMs`, `resolveMotionDelayMs`, `resolveEnterTransition`, `resolveExitTransition`) honoring `ThemeTokens.motion.reducedMotion`, `UiImageRenderer`/`LocalImageRenderer`, `LayoutConfigModifier`, `TokenResolvers`, `compose.uiTest` semantic tests (`UiAtomsComposeTest`) |
| **Layer 2** | `action-engine` | `dev.abeta.kmp:action-engine:0.1.0` | OCP `ActionTypes` (**18 built-in types** adding `open_url` / `OpenUrlPayload`, `share` / `SharePayload`, `request_permission` / `RequestPermissionPayload`, `biometric_auth` / `BiometricAuthPayload` alongside `trigger_feedback` / `TriggerFeedbackPayload`), `ActionPayloadDecoder`, `ActionDecoderRegistry`, `ActionHandler`, `ActionOutcome` (`OpenUrlRequested`, `ShareRequested`, `PermissionRequested`, `BiometricAuthRequested`, `FeedbackTriggered`, `OverlayRequested`), `ApiCallExecutor`, `ActionRegistry`, `DefaultActionDispatcher`, `ActionValidator` |
| **Layer 2** | `network` | `dev.abeta.kmp:network:0.1.0` | DIP-compliant Ktor 3.6.1 client (`HttpTransport`, `KtorHttpTransport`, `HttpClientFactory`, `NetworkConfig`, `NetworkPorts`) with HTTP/2 ALPN, Brotli (`br`) decompression (`org.brotli:dec`), bearer token refresh (`AuthTokenProvider`), and `CertificatePinMatcher` shared SPKI/cert pinning for JVM/OkHttp (`CertificatePinner`) and Apple Darwin `NSURLSession` (`PlatformHttpEngine.apple.kt`) across iOS, macOS, and watchOS |
| **Layer 3** | `sdui-layout` | `dev.abeta.kmp:sdui-layout:0.1.0` | SDUI orchestrator (`LayoutController` with `observeConnectivity`, `handleDeepLink`, `isOffline`, `errorShakeSequence`, `errorShakeTargetId`, `dismissFeedback()`, `collectAllEnvelopes`), `PlatformBridgeBundle`, `PlatformBridgeOutcomeExecutor`, `AdaptiveScreenContainers.kt` (`AdaptiveReadyViewport`, `SplitPaneBody`, `OfflineStatusBanner`), **17 snippet renderers** via `BuiltInSnippetModule` + responsive `ContainerSnippetRenderers.kt` (`Grid`/`Carousel`) + `ProductCardSnippetRenderer.kt` + `SwipeDeckSnippetRenderer.kt` + `SearchAndChipSnippetRenderers.kt`, `FloatingCartDockHost` + `PageCartCalculator`, `UiCelebrationOverlay`, `ModalBottomSheet` & `AlertDialog`, `PullToRefreshBox`, `SkeletonScreenPlaceholder`, `loadMore()`, `CachedScreenRepository`, `OfflineActionCoordinator`, `KtorImageRenderer`, `createSduiKoinModule` (`platformBridges`, `certificatePins`), `SduiHostRoot.kt` (`SduiHostedRoot`), iOS `createSduiViewController` + `AbetaSduiKit.xcframework`, macOS native Cocoa `launchMacOsSduiWindow` (`SduiMacOsWindow.kt`), `SduiScreenComposeTest` |
| **Layer 3** | `watch-companion` | `dev.abeta.kmp:watch-companion:0.1.0` | Headless wearable SDUI companion module (`WatchGlanceModels.kt`, `WatchScreenMapper`, `RemoteWatchPageSource`, `WatchRemoteApiCallExecutor`, `WatchCompanionController`, `createWatchCompanionModule`, `createAppleWatchCompanionHost`) assembling `AbetaWatchKit.xcframework` (`watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`) and supporting Wear OS JVM hosts |
| **Explorer** | `storybook` | *(Unpublished Explorer)* | Interactive Compose Multiplatform Storybook (**13 stories across 4 categories**: `FOUNDATIONS`, `ATOMS`, `SNIPPETS`, `PLAYGROUND`, including `"All 17 Snippets"`, `ResponsiveAndBridgesStory` / `StoryCatalog.STORY_RESPONSIVE_BRIDGES`, and `DiscoveryAndSearchStory` / `StoryCatalog.STORY_DISCOVERY_SEARCH`), responsive `CompactStoryStrip`/`StorySidebar` shell, `MotionAndStoreStory`, 6-app Super-App slugs (`fresh-home`, `bites-home`, `pulse-home`, `mart-home`, `spark-deck`, `trade-home`) & `"watch-status"` in `LiveBffStory`, `fontScale` controls, Desktop JVM, WasmJS Browser, macOS `launchStorybookMacOsWindow`, `StorybookViewController` + `AbetaStorybookKit.xcframework`, `StorybookComposeTest` |
| **App** | `sample-app` | *(Unpublished Demo)* | Runnable Compose Desktop & `--headless` CLI demo featuring the **6-in-1 Super-App Showcase Launcher** (**Abeta Fresh**, **Abeta Bites**, **Abeta Pulse**, **Abeta Mart**, **Abeta Spark**, **Abeta Trade**) alongside core screens (`home`, `store-home`, `catalog`, `profile`, `form-demo`, `containers`, `watch-status`), `PlatformBridgeBundle` + `observeConnectivity`, `OfflineApiCallExecutor`, `--headless`, and `--bff-url` |

### Go BFF (`bff/`)

| Package | Responsibility |
| :--- | :--- |
| `cmd/bff` | Application entrypoint (`go run ./cmd/bff`), env config (`go-libs/env`), `http.Server.Protocols` (`HTTP/1.1`, `h2c`, TLS HTTP/2), graceful shutdown (`go-libs/shutdown`) |
| `internal/domain` | Pure Go ports (`ScreenRepository`, `ActionExecutor`, `TokenCatalogProvider`) and domain models (zero Gin/HTTP imports, enforced by `arch_test.go`) |
| `internal/usecase` | `ScreenService`, `ActionService` (including declarative `ValidationRule` enforcement), and `CatalogService` |
| `internal/handler` | Gin router (`NewRouter` in `router.go` adding `POST /api/v1/orders/express`) with `github.com/abeta-dev/go-libs@v0.4.0` (`ginmw`), `CompressionMiddleware()` (`br` via `github.com/andybalholm/brotli` + `gzip`), `ETagMiddleware()` (SHA-256 `ETag` + `304`), OpenAPI 3.1 (`openapi.go` at `GET /openapi.json`) & Scalar UI (`GET /docs`), optional `ginmw.GlobalRateLimit()`, optional `ginmw.AuthMiddleware()` (`JWT_SECRET`), `/healthz`, `/readyz`, `/metrics`, and `/api/v1/*` |
| `internal/infra` | Concrete adapters: `InMemoryScreenRepository` (core screens `catalog`, `containers`, `form-demo`, `home`, `profile`, `store-home`, `watch-status` + 6-in-1 Super-App Showcase screens `fresh-home`, `bites-home`, `pulse-home`, `mart-home`, `spark-deck`, `trade-home`), `DefaultActionExecutor` (`action_executor.go` handling all 18 actions incl. `open_url`, `share`, `request_permission`, `biometric_auth`, `trigger_feedback`, & `POST /api/v1/orders/express` returning `celebration` + `show_toast` follow-ups), `StaticTokenCatalogProvider`, plus golden parity tests (`home_screen.json`, `form_screen.json`, `action_batch.json`, `containers_screen.json`, `store_home.json`) |
| `pkg/sdui/{actions,atoms,snippets,theming}` | Shared v1 SDUI wire types & builders (**15 Wire Atoms** incl. `StepperAtom`, **17 Snippet Types** incl. `product_card`, `swipe_deck`, `search_bar`, `chip_group`, **18 Action Types** adding `open_url`, `share`, `request_permission`, `biometric_auth`, `MotionSpec`, `FloatingCartSpec`, `PaginationSpec`, `PullToRefresh`, `OverlayPresentation`, `ResponsiveColumnsSpec`, `ResponsiveIntSpec`, `AdaptivePageSpec`, `ScreenLayoutMode`, `TabItemSpec.Title` as `string`) matching `kmp/sdui-contracts`, `kmp/action-engine`, and `kmp/design-tokens` 1:1 |

---

## Gradle BOM Quick Start

Consumers align all `dev.abeta.kmp` modules through the published BOM (`dev.abeta.kmp:bom:0.1.0`) and optional version catalog (`dev.abeta.kmp:dependencies:0.1.0`) — downloadable anonymously with **zero credentials required** via **Maven Central** (stable) or **Cloudsmith Open Source** (snapshots & mirrors).

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
            from("dev.abeta.kmp:dependencies:0.1.0")
        }
    }
}
```

```kotlin
// build.gradle.kts (commonMain)
dependencies {
    implementation(platform("dev.abeta.kmp:bom:0.1.0"))
    implementation("dev.abeta.kmp:database") // Optional Room 3 KMP local SQLite DB
    implementation("dev.abeta.kmp:design-tokens")
    implementation("dev.abeta.kmp:sdui-contracts")
    implementation("dev.abeta.kmp:ui-atoms")
    implementation("dev.abeta.kmp:action-engine")
    implementation("dev.abeta.kmp:network")
    implementation("dev.abeta.kmp:sdui-layout")
    // Optional headless wearable companion:
    // implementation("dev.abeta.kmp:watch-companion")
}
```

Inside this monorepo, `abeta.kmpLocalBuild=true` (set in `kmp/gradle.properties`) automatically substitutes `dev.abeta.kmp:*` module coordinates with local Gradle project references. Pass `-Pabeta.kmpLocalBuild=false` to resolve against published artifacts instead. See [docs/gradle-bom-guide.md](docs/gradle-bom-guide.md) and [docs/publishing-and-hosting.md](docs/publishing-and-hosting.md) for Maven Central, Cloudsmith Open Source, SwiftPM XCFrameworks, and Go BFF (`proxy.golang.org`) publishing details.

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
  - [abeta-kmp-demo](skills/abeta-kmp-demo/SKILL.md) — 6-in-1 Super-App Showcase Demo & 13-story Storybook reference guide
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
