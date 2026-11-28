# Changelog

All notable changes to **Abeta KMP Kit (`abeta-dev/kmp`)** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [0.8.0] - 2026-11-28

> Go BFF remains at **`bff@v0.9.0`** (zero breaking wire changes).

### Added

- **Native Android KMP `.aar` Targets Across All 11 Publishable Libraries (`com.android.kotlin.multiplatform.library` `9.5.3`)**:
  - Enabled the modern Google Android KMP Library plugin (`compileSdk = 37`, `minSdk = 24`) across all **11 publishable KMP modules** (`io.github.abeta-dev:<module>-android:0.8.0` aligned via `io.github.abeta-dev:bom:0.8.0`).
  - Added thread-safe `AbetaAndroidContext` (`AbetaAndroidContext.initialize(context)`) in `core` alongside native `androidMain` actuals for `ClipboardPort`, `ExternalLauncherPort`, `HapticEngine`, `ConnectivityMonitor`, `SharePort`, `LocaleFormatter`, `PlatformInfo`, `PlatformCapabilities`, `AndroidKeyStore`-backed `SecureKeyValueStore` (with restricted AES-256-GCM file fallback), `SharedPreferences`/DataStore/file stores in `state`, Room 3 Android builder (`BundledSQLiteDriver` + `MIGRATION_1_2`) in `database`, OkHttp SPKI pinning (`PlatformHttpEngine.android.kt`) in `network`, and `BitmapFactory` image decoding (`KtorImageRenderer.android.kt`) in `sdui-layout`.
- **Deferred OS Bridge Ports (`core`, `sdui-layout`)**:
  - Added native `macosMain` AppKit `DesktopSystemPort` (`NSApp` dock badge, `NSWorkspace` System Settings deep links, `requestUserAttention`).
  - Implemented real multiplatform `FilePickerPort` (`jvm` AWT `FileDialog`, `macos` AppKit `NSOpenPanel`, `wasmJs` `<input type="file">`), real `AudioCapturePort` (`jvm` `javax.sound.sampled` `TargetDataLine` 16 kHz PCM with live RMS telemetry), and real `LocationPort` (`wasmJs` `navigator.geolocation`), wired into `PlatformBridgeBundle.createPlatformDefault()`.
- **Room 3 `DatabaseKeyValueStore` Adapter (`database`)**:
  - Added `DatabaseKeyValueStore` (`ObservableKeyValueStore` implementation over Room 3 `key_value_records`) in `io.github.abeta-dev:database`, providing opt-in Room 3 SQLite persistence for `CachedScreenRepository`, `OfflineMutationQueue`, and `PersistentSessionStore` while keeping `sdui-layout` Room-free by default.
- **Mandatory Grounding Before Generation & Deterministic CodeGraph (`docs/flows/`, `docs/patterns/`, `docs/codegraph/`, `scripts/generate_codegraph.py`)**:
  - Established a repository-wide 6-step grounding workflow across every IDE/AI entrypoint (`AGENTS.md`, `GEMINI.md`, `CLAUDE.md`, `.cursor/rules/`, `.github/copilot-instructions.md`, `.junie/guidelines.md`, `.agent/rules/`): `AGENTS.md` → `docs/architecture.md` → `docs/flows/*.md` (4 end-to-end runtime flow guides) → `docs/patterns/*.md` (4 architectural pattern guides) → deterministic CodeGraph symbol indexer (`scripts/generate_codegraph.py` + `docs/codegraph/{symbols.json,INDEX.md}`, queried via `--query <symbol>` and enforced in CI via `--check`) → targeted source reads only for verification.

### CI & Hardening

- **Android SDK 37 & Apple Verification Gates (`.github/workflows/`)**:
  - Provisioned Android SDK 37 across CI workflows to compile, test, and validate all native `-android` `.aar` targets alongside JVM, Apple, and WasmJS.
  - Added `macos-apple-verify` CI job compiling iOS and watchOS simulator frameworks, executing `macosArm64Test`, and validating Swift 5 & 6 `-typecheck` via `scripts/verify_spm_package.sh`.

---

## [0.7.0] / [bff/v0.9.0] - 2026-11-25

### Added

- **Exportable Go SDUI Server & Cache Packages (`bff/pkg/sdui/cache`, `bff/pkg/sdui/server` — `bff@v0.9.0`)**:
  - Added pure transport-agnostic `pkg/sdui/cache` (`UpstreamCachedScreenRepository` with `singleflight` fetch coalescing, bounded LRU + TTL, prefix/tag/all invalidation, and O(1) `BuildManifest`).
  - Added production-hardened `pkg/sdui/server`: fail-closed `CORSMiddleware` (empty allowlist blocks cross-origin by default, explicit wildcard subdomain matching), `ETagMiddleware` (`Cache-Control: private, no-cache` by default, honoring `page.ETag` plus `X-App-Version`/`X-Page-Version`/`X-Client-App-Version`), `sync.Pool`-backed `CompressionMiddleware` (Brotli `br` + `gzip` with streaming bypass for SSE & oversized payloads), per-router immutable-key `JWTAuthMiddleware` (HS256, fail-closed, never mutating global `ginmw` state), `HTTPUpstreamFetcher` (conditional `If-None-Match` / `304`), and `StreamProvider` for SSE delta streams.
  - Added live routes `GET /api/v1/manifest` (alias alongside `GET /api/v1/screens/manifest`), `POST /api/v1/pay/transfer`, and `GET /api/v1/streams/:streamId` (`Last-Event-ID` cursor replay + `204 No Content` catch-up).
  - Updated `bff/.env.example` and `cmd/bff` with fail-closed `CORS_ALLOWED_ORIGINS`, `TRUSTED_PROXIES`, minimum 32-char `JWT_SECRET` enforcement when `AUTH_REQUIRED=true`, and TLS 1.2+ enforcement.
- **Real ISO/IEC 18004 QR Code Encoder (`ui-atoms`)**:
  - Added pure-Kotlin `QrCodeEncoder.kt` supporting byte-mode QR Versions 1–10 (ECC Level M, Galois-field Reed-Solomon error correction, 8-mask penalty evaluation, finder/alignment/timing/format/version modules) powering `UiQrCode` across all KMP targets.
- **Room 3 Schema v2 Migration (`database`)**:
  - Bumped `AbetaDatabase.SCHEMA_VERSION` from `1` to `2`, added `pageVersion` and `appVersion` columns to `cached_screens`, exported `kmp/database/schemas/dev.abeta.kmp.database.AbetaDatabase/2.json`, and shipped explicit `AbetaDatabase.MIGRATION_1_2` wired across JVM, Apple, and Android factories.
- **Absolute Countdown Expiry & Schema Versioning (`sdui-contracts`, `ui-atoms`, `bff`)**:
  - Added optional `expires_at_epoch_ms` (`expiresAtEpochMs`) to `CountdownAtom` (1:1 KMP/Go parity) so `UiCountdown` stays drift-free across recompositions, caching, and pause/resume, plus `sdui_schema_version` on `ScreenManifestResponse` and `NetworkConfig.appVersion` (`X-Client-App-Version` header on same-origin requests).

### Security & Hardening

- **Cross-Platform Secure Storage, Auth & Network Guards (`core`, `state`, `network`, `action-engine`, `analytics`, `sdui-layout`)**:
  - Replaced JVM `Preferences`-backed secure store with `0600`/`0700` POSIX-restricted atomic file-backed AES-256-GCM storage (`SecureStorage.jvm.kt`) removing the 8 KB Preferences cap; synchronized Apple Keychain access with `NSLock` (`SecureStorage.apple.kt`); hardened `SensitiveDataMasker`, `DeepLinkParser`, and `ExternalLauncherPort` scheme allowlists.
  - Enforced `sha256/` prefix validation on `NetworkConfig.certificatePins`, restricted default/auth/app-version headers to same-origin requests, hardened SSE reconnect & timeout handling in `KtorSseTransport`, and added bounded `DispatchBudget` (`DEFAULT_MAX_TOTAL_ACTIONS = 64`) & sequential state-mutation threading in `DefaultActionDispatcher`.
  - Added multiplatform `StateLock` (`jvm`/`apple`/`wasmJs`) across `SessionStore`, `DataStoreKeyValueStore`, and `OfflineMutationQueue`, plus serialized persistence/flush safety in `BufferedAnalyticsTracker`.
  - Hardened `CachedScreenRepository` & `RemoteWatchPageSource` version-gated `If-None-Match` (`304`) / `ETag` / `appVersion` / `sduiSchemaVersion` validation, `LayoutController` delta patching & resilience (`LayoutDeltaJsonHelpers.kt`), and `WatchScreenMapper` projection coverage.
- **CI, Supply-Chain & Release Hardening**:
  - Verified all **11** golden JSON parity fixtures (`bff/testdata/golden/`), enforced `vars.ENABLE_MAVEN_CENTRAL=true` gating and 4-product SwiftPM packaging (`AbetaSduiKit`, `AbetaWatchKit`, `AbetaSduiSwiftUI`, `AbetaWatchSwiftUI`) via `scripts/package_xcframeworks.sh`.

### Fixed

- **Apple SwiftUI SPM Compilation & Retain-Cycle Fixes (`kmp/platforms/apple/`)**:
  - Guarded `AbetaWatchSwiftUI` with `#if canImport(SwiftUI) && os(watchOS)`, fixed Swift 6 concurrency isolation (`@unchecked Sendable` boxes, `nonisolated static func defaultResolveScreenId`), eliminated callback retain cycles via lock-protected `WatchObservationLifecycle` / `WatchCancelHandle` teardown, and ensured root `http`/`https` deep links return `nil` instead of the host domain.
- **Android & Wear OS Native Host Bridges (`kmp/platforms/android/`, `kmp/platforms/wearos/`)**:
  - Wired `WearCompanionHostActivity` with Android-native `AndroidSharedPreferencesKeyValueStore`, `AndroidKeyStoreSecureStore`, `AndroidConnectivityMonitor`, `AndroidExternalLauncher`, `AndroidSharePort`, and `WearHapticEngine` instead of falling back to JVM Desktop AWT/`Preferences`.
  - Hardened `AndroidPlatformBridgeFactory` (`AndroidKeyStore` key invalidation recovery, `FilePickerPort` metadata resolution, `BiometricPrompt` cancellation handling, and `AndroidDatabaseFactory` `MIGRATION_1_2` registration).

---

## [0.6.0] / [bff/v0.8.0] - 2026-11-25

### Added

- **Universal `custom_slot` Host Bridge & Built-In `graph_canvas` Spatial Snippet (`sdui-contracts`, `sdui-layout`, `storybook`, `bff` — 23/23 `SnippetTypes` Parity)**:
  - Added `SnippetTypes.CUSTOM_SLOT` (`"custom_slot"`) & `CustomSlotSnippetData` plus `CustomSlotContext`, `CustomSlotRenderer`, `CustomSlotRegistry`, `LocalCustomSlotRegistry`, `CustomSlotSnippetRenderer` (with automatic `fallback` snippet rendering when unregistered), and optional `customSlotRegistry` param on `SduiScreen`.
  - Added `SnippetTypes.GRAPH_CANVAS` (`"graph_canvas"`), `GraphCanvasSnippetData`, `GraphNodeSpec`, `GraphEdgeSpec`, `GraphEdgeStyle` (`bezier`, `straight`, `dashed`), and interactive `GraphCanvasSnippetRenderer` supporting Bezier/straight/dashed edge rendering, pan/zoom controls, draggable nodes, and node tap/move `ActionRef` events.
  - Mirrored 1:1 in Go BFF (`bff@v0.8.0`, `bff/pkg/sdui/snippets/spatial_and_slot.go`, `component_ids.go`, OpenAPI schemas) with Golden Fixture #11 (`bff/testdata/golden/spatial_custom_and_graph_screen.json`) and live interactive demos in Storybook (`All 23 Snippets`).
- **Voice, Speech-to-Text & Desktop OS Ports (`core`, `sdui-layout`, `storybook`)**:
  - Added `AudioCapturePort`, `SpeechToTextPort`, and `DesktopSystemPort` (tray status, OS settings deep-link, taskbar badge count, user attention) with `Unavailable*`, `Fake*`, `Recording*`, and JVM desktop implementations in `kmp/core`.
  - Wired all three ports into `PlatformBridgeBundle` & `createSduiKoinModule(...)` with backwards-compatible defaults, and added an interactive `Voice & Desktop OS Bridges` card to Storybook `ResponsiveAndBridgesStory`.
- **Importable SwiftPM Watch/iOS UI Package & Modular Wear OS Renderer (`platforms`, `kmp-spm`)**:
  - Added directly importable Swift source targets `AbetaWatchSwiftUI` (`AbetaWatchSduiView`, `AbetaWatchViewModel`, `AbetaWatchMetricRow`, `AbetaWatchActionCardView`) and `AbetaSduiSwiftUI` (`AbetaIosSduiScreenView`, `AbetaIosRootAppView`) under `kmp/platforms/apple/Sources/`, automatically packaged by `scripts/package_xcframeworks.sh` and synced to `Abeta-dev/kmp-spm` as a 4-product SwiftPM package.
  - Added modular `WearSduiRenderer.kt` (`WearSduiScreen`, `WearGlanceContent`, `WearMetricChip`, `WearActionCardChip`, `WearTileSnapshotMapper`) for Wear OS Compose & Tile/Glance hosts.

---

## [0.5.0] / [bff/v0.7.0] - 2026-11-25

### Added

- **Version-Gated Caching, HTTP `304 Not Modified` & `/manifest` Handshake (`network`, `sdui-contracts`, `sdui-layout`, `bff`)**:
  - Added `HttpResponseEnvelope<T>` (preserving status code, response headers, and `etag`), `ConditionalHttpResult<T>` (`Modified` / `NotModified`), and backwards-compatible `requestWithResponse` / `requestConditional` + `safeGetWithResponse` / `safeGetConditional` on `HttpTransport` & `KtorHttpTransport`.
  - Extended `PageResponse` with optional `page_version`, `app_version`, `etag`, and `stream_spec`, and added `ScreenVersionMeta` & `ScreenManifestResponse` in `kmp/sdui-contracts` and `bff/pkg/sdui/snippets`.
  - Added `ConditionalScreenRepository`, `RemoteScreenManifestRepository`, `ScreenCachePolicy.VERSION_GATED`, `If-None-Match` (`304 Not Modified`) revalidation, `/api/v1/screens/manifest` handshake, and `appVersion` cache busting in `CachedScreenRepository`.
  - Added `GET /api/v1/screens/manifest` (ETag + `304` enabled, deterministic SHA-256 screen versions) and `APP_VERSION` stamping in Go BFF (`bff@v0.7.0`).
- **Zero-KSP Lightweight Persistent Disk `KeyValueStore` (`state`, `sdui-layout`)**:
  - Added `createDiskKeyValueStore(namespace, directoryPath)` backed by atomic `FileKeyValueStore` on JVM Desktop (no 8 KB `Preferences` cap, safe for multi-hundred-KB SDUI JSON), `UserDefaultsKeyValueStore` (namespace-isolated) on Apple, and `WebStorageKeyValueStore` (`localStorage` with in-memory fallback) on WasmJS.
  - Added `useDiskCacheStore` option to `createSduiKoinModule(...)`.
- **Streaming SSE Transport & Live SDUI Delta Patches (`network`, `sdui-contracts`, `sdui-layout`, `bff`)**:
  - Added `SseEvent`, `SseReconnectPolicy`, `SseLineParser`, `SseTransport`, and `KtorSseTransport` (`Last-Event-ID` bounded exponential-backoff reconnect) in `kmp/network`.
  - Added `SseStreamSpec`, `SnippetDeltaOp` (`append_text`, `patch_snippet_data`, `replace_snippet`, `insert_snippet`, `remove_snippet`, `update_page_meta`), and `SnippetDeltaPatch` with 1:1 KMP/Go parity + Golden Fixture #10 (`bff/testdata/golden/streaming_and_versioned_screen.json`).
  - Added `applySnippetDeltaPatch(page, patch)`, `LayoutUiState.Ready.isStreaming`, and `LayoutController.applyDeltaPatch` / `observeStream` / `stopStream` (with automatic stream lifecycle for `PageResponse.streamSpec`).
  - Added uncompressed real-time SSE endpoint `GET /api/v1/streams/:streamId` (honoring `Last-Event-ID`) in Go BFF with OpenAPI schemas.
- **Loopback CSRF Token & Single `403` Retry Interceptor (`network`, `sdui-layout`)**:
  - Added `CsrfTokenProvider` (`X-CSRF-Token` on `POST`/`PUT`/`PATCH`/`DELETE`), single refresh-and-retry-once on HTTP `403` in `KtorHttpTransport`, WasmJS `MetaTagCsrfTokenProvider`, and `csrfTokenProvider` parameter on `createSduiKoinModule(...)`.

---

## [0.4.1] - 2026-11-24

### Fixed

- **Card Hover & Ripple Clipping (`ui-atoms`, `sdui-layout`)**: Moved `.clickable` ahead of inner `LayoutConfig.padding` in `UiSurfaceCard` and `ComposedBlockSnippetRenderer` so hover and ripple overlays fill the full rounded card bounds cleanly instead of painting inside the inner padding.
- **Commerce Product Card Grid & Stepper Ergonomics (`ui-atoms`, `sdui-layout`)**:
  - Deduplicated redundant `"ADD"` labels in `UiStepper` so narrow 2-column quick-commerce tiles never wrap `"ADD"` vertically (`AD / D`) or duplicate `"ADD"` next to the stepper control.
  - Upgraded `UiPrice` to `FlowRow` for clean wrapping inside compact cards.
  - Stacked the price row cleanly above a right-aligned `UiStepper` in `ProductCardSnippetRenderer` with single-line title/subtitle/rating defaults so 2-column product grids maintain uniform heights with zero price-to-button collision.
- **Swipe Deck Peek Cards (`sdui-layout`)**: Enforced uniform card minimum height (`388dp`) and suppressed interest tags / excess bio lines on non-interactive peek cards in `SwipeDeckComponents` so background stack edges stay crisp with zero sliced chips.
- **7-in-1 Super-App Showcase & Storybook Shell (`storybook`)**:
  - Added 6dp inset margins to Express product tile overlay badges, increased bottom placeholder clearance in `StorybookDemoImageRenderer`, and moved tile peek clicks to the media box so tapping variant chips (`500g ▼` / `450g ▼`) directly opens the SDUI Variant Picker `ModalBottomSheet`.
  - Added `textColor = ColorTokenRef("text.on_brand")` on the `brand.primary` banner badge (`SPOTLIGHT`) for WCAG contrast.
  - Switched the `SuperAppDemoControlBar` app switcher to `FlowRow` so all 8 apps (`Abeta Fresh` through `Abeta Watch`) remain visible without clipping, and added `clipToBounds()` + ellipsis truncation on `ActionConsoleBar`.

---

## [0.4.0] / [bff/v0.6.0] - 2026-11-24

### Added

- **Google Official AndroidX KMP Compliance (`state`, `sdui-layout`, `platforms`)**:
  - Upgraded to official Google Maven KMP libraries across all 8 targets (`jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchos*`, `wasmJs`): `androidx.datastore:datastore-preferences-core:1.3.0`, `androidx.lifecycle:lifecycle-viewmodel:2.11.1`, `androidx.lifecycle:lifecycle-viewmodel-compose:2.11.1`, `androidx.lifecycle:lifecycle-runtime-compose:2.11.1`, and `androidx.savedstate:savedstate:1.5.0`.
  - Added `DataStoreKeyValueStore` + `createDataStoreKeyValueStore(namespace)` in `kmp/state` backed by atomic `PreferenceDataStoreFactory.createWithPath` (JVM/Apple `NSFileManager`/WasmJS `WebSessionStorage`).
  - Added `SduiLayoutViewModel` (`androidx.lifecycle.ViewModel` + `SavedState` snapshot export/restore) and switched `SduiHostRoot`, `storybook`, `sample-app`, and Android/Wear host templates to `collectAsStateWithLifecycle()`.
  - Standardized `applyDefaultHierarchyTemplate()` across all KMP library modules, added Google's modern `com.android.kotlin.multiplatform.library` template (`kmp/platforms/android/build.gradle.template.kts`), and added Swift Concurrency `AsyncStream` helpers in `AbetaAppleHosts.swift`.
- **Express Quick-Commerce Atomic → Molecular → Compound Engine (Wave A)**:
  - Added 29th Wire Atom `MediaPagerAtom` & 31st Stateless Compose UI Atom `UiMediaPager` (swipeable `HorizontalPager` with draw-phase pill dot indicators and reduced-motion-safe auto-advance).
  - Extended `ComposedNodeKind` from 29 to **36 kinds** (`scroll_row`, `grid`, `text_field`, `switch`, `checkbox`, `slider`, `media_pager`) plus per-child 9-position `box_alignment` for badge-over-image overlays.
  - Added `SnippetEnvelope.sticky` (`LazyColumn` `stickyHeader` for pinned search/filter bars) and rich SDUI `ModalBottomSheet` body rendering via `OpenDialogPayload.snippets` & `sheet_screen_id`.
  - Added Go Server-Side Molecule Library (`bff/pkg/sdui/molecules`): `ExpressProductTile`, `CategoryGridSection`, `ImpulseUpsellRail`, `DeliveryInstructionsCard`, `VariantPickerSheetAction`, `CouponApplyCard`, `CheckoutBillSummary` (100% test coverage) and Golden Fixture #9 (`express_molecules_and_analytics_screen.json`).
- **Universal Component IDs & End-to-End BFF Analytics Pipeline (Wave B)**:
  - Added `ActionRef.tracking`, `ComposedNode.tracking` (deduplicated node impression), `ComposedNode.click_tracking`, wired `ButtonAtom.tracking` propagation in `UiButton`, and added deterministic `snippets.EnsureComponentIDs(&page)` on all served BFF screens.
  - Automatic `page_open`, `snippet_impression`, `node_impression`, and `interaction_click` emission in `LayoutController`, plus batched HTTP shipper `RemoteBffAnalyticsTracker` (`BufferedAnalyticsTracker` + `HttpTransport`) in `kmp/sdui-layout`.
  - Added `bff/pkg/sdui/analytics`, `POST /api/v1/analytics/events`, `GET /api/v1/analytics/summary`, and a live **Analytics Event Stream** telemetry panel in Storybook `SuperAppDemosStory`.
- **Unified `CI Quality Gate` Workflow**:
  - Added `.github/workflows/ci-quality-gate.yml` (`CI Quality Gate`) mirroring `Abeta-dev/go-libs` for strict branch protection with zero stuck path-filtered PRs.

---

## [0.3.0] / [bff/v0.5.0] - 2026-11-23

### Added

- **12 New Production UI Atoms (30 Stateless Compose UI Atoms / 28 Wire Atoms / 29 `ComposedNodeKind`s)**:
  - **Selection & Form Input Atoms (`sdui-contracts`, `ui-atoms`, `bff/pkg/sdui/atoms`)**: `RadioGroupAtom`/`UiRadioGroup`, `SelectAtom`/`UiSelect`, `OtpInputAtom`/`UiOtpInput` (masked PIN + `onComplete`), `ChipAtom`/`UiChip`, `SegmentedControlAtom`/`UiSegmentedControl` (draw-phase sliding pill).
  - **Commerce, Fintech & Telemetry Atoms**: `RatingAtom`/`UiRating` (vector half-stars + interactive tap-to-rate), `PriceAtom`/`UiPrice` (tabular sale price, strikethrough MRP, discount pill), `BadgeAtom`/`UiBadge` (`99+` overflow + live pulsing dot), `AlertBannerAtom`/`UiAlertBanner` (4 semantic variants with CTA & dismiss), `StatTileAtom`/`UiStatTile` (`▲`/`▼` KPI delta), `KeyValueRowAtom`/`UiKeyValueRow` (checkout bill / receipt row), and `QrCodeAtom`/`UiQrCode` (deterministic pure-Compose 21x21 Canvas QR matrix).
  - **Atomic Snippet Molding Engine (`sdui-layout` + `bff/pkg/sdui/snippets`)**: Extended `composed_block` to **29 `ComposedNodeKind`s** (`ComposedBlockExtendedLeafs.kt` + Go `recipes_composed.go`) with automatic `LayoutFormSeeder` binding and 8th golden parity fixture (`bff/testdata/golden/extended_atoms_screen.json`).
- **20-Story Interactive Storybook (`kmp/storybook`)**:
  - Expanded `UI ATOMS` to **8 dedicated stories** (`atom-selection-otp`, `atom-commerce-fintech`, `atom-feedback-telemetry`) covering all 30 stateless atoms with live wire JSON inspection.
  - Upgraded all **7 live Super-App demos** (`Abeta Fresh`, `Abeta Bites`, `Abeta Pulse`, `Abeta Mart`, `Abeta Spark`, `Abeta Trade`, `Abeta Pay`) to render real molded QR passes, 4-digit PIN entry, delivery/speed selectors, ratings, prices, KPI stat tiles, alert banners, and receipt breakdowns.

### Fixed

- **`UiSurfaceCard` Clickable Hit-Area & Ripple Clipping**: Applied `.clickable(...)` inside the rounded clip and before inner card padding so hover/press ripples on clickable `UiSurfaceCard` and `UiStatTile` components span the full rounded surface cleanly.

---

## [0.2.0] / [bff/v0.4.0] - 2026-11-23

### Added

- **Maven Central Publication (`io.github.abeta-dev`)**: Verified `io.github.abeta-dev` namespace on Sonatype Central Portal and published all 13 Gradle modules (`io.github.abeta-dev:bom:0.2.0`, `dependencies`, and 11 KMP libraries incl. Room 3 `database` and `watch-companion`) while preserving `dev.abeta.kmp.*` Kotlin source packages.
- **7th Super-App Vertical (`Abeta Pay`) & Deep Multi-Step Workflows**: Added `Abeta Pay` (`pay-home`, `pay-transfer`, `pay-cards`, `pay-receipt`) across KMP Storybook, `sample-app`, and Go BFF (`bff/v0.4.0`), plus 30 interactive multi-step demo pages across all 7 apps live in browser WasmJS Storybook.
- **Zero-Jitter Compose WasmJS Smoothness**: Replaced infinite transition resets with monotonic `withInfiniteAnimationFrameNanos` marquee scrolling, pure `graphicsLayer` dual-layer `UiRollingCounter`, cached `extractProductCards(page)`, isolated `formValues` recomposition scopes, per-page `LazyListState` keying, and zero-network `StorybookDemoImageRenderer`.

---

## [0.1.0] - 2026-11-20

### Added

- **Monorepo Foundation (`abeta-dev/kmp`)**:
  - Apache-2.0 licensing, root governance files (`README.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `SECURITY.md`), and AI agent guides (`AGENTS.md`, `CLAUDE.md`).
  - Gradle 9.7.0 + Java 21 + Kotlin 2.4.21 + Compose Multiplatform 1.12.2 build setup under `kmp/` covering **10 publishable KMP library modules** (`core`, `state`, `analytics`, `design-tokens`, `sdui-contracts`, `ui-atoms`, `action-engine`, `network`, `sdui-layout`, `watch-companion`):
    - Headless/contract publishable modules (`core`, `state`, `analytics`, `sdui-contracts`, `action-engine`, `network`, `watch-companion`) target `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`, and `wasmJs`.
    - Compose UI modules (`design-tokens`, `ui-atoms`, `sdui-layout`) and `storybook` target `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, and `wasmJs`.
    - Drop-in reference host glue under `kmp/platforms/` (`android/AndroidPlatformBridgeFactory.kt`, `wearos/WearCompanionHostActivity.kt`, `apple/AbetaAppleHosts.swift`, `README.md`; Android & Wear OS Gradle templates require an Android app module with `ANDROID_HOME` / Android SDK configured).
  - `dev.abeta.kmp:bom:0.1.0` (`kmp-bom`) Java Platform BOM and `dev.abeta.kmp:dependencies:0.1.0` (`dependencyManagement`) Gradle Version Catalog with `abeta.kmpLocalBuild` project substitution.
  - Binary Compatibility Validator (`0.18.2`, `apiDump` / `apiCheck` across all 10 publishable KMP modules including `kmp/watch-companion/api/watch-companion.api`) and Dokka v2 HTML generation (`2.3.0`, `dokkaGenerateHtml` / `:core:dokkaGenerateHtml`).
- **Layered KMP SDUI & Platform Architecture**:
  - **Layer 0**: `core` (`AppResult`, `AppError` with `ErrorCategory`/`isRetryable`/`TimeoutError`/`RateLimitError`, `DispatcherProvider`, `Clock`/`MutableClock`/`ElapsedTimer`, `AppLogger`/`RedactingAppLogger`/`CompositeAppLogger`, `KeyValueStore`/`ObservableKeyValueStore`/`NamespacedKeyValueStore`, `UuidGenerator`, `BackoffPolicy`, `SingleFlight`, `Debouncer`, `ClickBackpressureGuard`, `SensitiveDataRedactor`, `FieldValidators`, `PlatformInfo` with `PlatformKind.{WATCHOS, WEAR_OS, LINUX_NATIVE, WINDOWS_NATIVE}`, `PlatformCapabilities`/`currentPlatformCapabilities()`, `HapticEngine`/`HapticStyle`, `ClipboardPort`, `ExternalLauncherPort`, `SharePort`/`ShareRequest`, `ConnectivityMonitor`/`ConnectivityStatus`, `PermissionPort`, `LocationPort`, `FilePickerPort`, `PushNotificationPort`, `LocaleFormatter` (`formatCurrencyCents`, `currentLocaleTag`), `DeepLinkParser`/`DeepLinkTarget`, `SecureKeyValueStore` (`AppleKeychain` `SecItem*` w/ fallback, JVM AES-256-GCM + `Preferences`, WasmJS `sessionStorage`), `BiometricAuthenticator` (`LAContext` on Apple)), `state` (`SessionStore`/`PersistentSessionStore`/`SessionLifecycleManager`, reactive `PreferenceStore` with `VersionedPreferenceEnvelope` & `multiplatform-settings`, `createSecurePersistentKeyValueStore(namespace)` in `SettingsKeyValueStore.kt`, `FeatureFlagProvider`/`LayeredFeatureFlagProvider`, `OfflineMutationQueue`), `analytics` (`AnalyticsTracker`, `CompositeAnalyticsTracker`, `LoggerAnalyticsTracker`, `RecordingAnalyticsTracker`, `SanitizingAnalyticsTracker`, `DeduplicatingAnalyticsTracker`, `EnrichingAnalyticsTracker`, `BufferedAnalyticsTracker`).
  - **Layer 1**: `design-tokens` (`ColorTokens`, `DesignPalette`, `SpacingTokens`, `CornerRadiusTokens`, `StrokeWidthTokens`, `TypographyTokens`, `ElevationTokens`, `MotionTokens`, `OpacityTokens`, `SizingTokens`, `ThemePreset`, `ColorAccessibility`, `DesignTheme`, `ThemeTokens`, and `ResponsiveTokens.kt` with `WindowWidthClass`, `WindowHeightClass`, `DeviceFormFactor`, `AdaptiveLayoutMetrics`, `resolveAdaptiveLayoutMetrics`, `LocalAdaptiveLayoutMetrics`, `ProvideAdaptiveLayoutMetrics` exposed via `ThemeTokens.adaptiveMetrics`), `sdui-contracts` (`PageResponse` with `PaginationSpec`, `pullToRefresh`, `floatingCart` / `FloatingCartSpec`, and `adaptiveLayout` / `AdaptivePageSpec`, `SnippetEnvelope` with optional `motion`, 14 `SnippetTypes` including `carousel` with `itemWidthByWidth`, `grid` with `columnsByWidth`, `tabs` with `TabItemSpec.title: String`, `accordion`, `product_card` / `ProductCardSnippetData`, 15 Wire Atoms including `SwitchAtom`, `CheckboxAtom`, `SliderAtom`, `AvatarAtom`, `SkeletonAtom` with `SkeletonKind`, and `StepperAtom`, `AdaptiveContracts.kt` with `ResponsiveColumnsSpec`, `ResponsiveIntSpec`, `AdaptivePageSpec`, `MotionContracts.kt` + `AtomEnums.kt` with `MotionSpec`, `MotionTransition`, `MotionDurationToken`, `MotionLoopEffect`, `FeedbackEffect`, `OsPermissionKind`, `ScreenLayoutMode`, `OverlayPresentation`, `LayoutConfig`, `ColorTokenRef`, `TypographyTokenRef`, `ActionRef`, `ValidationRuleContract`, `TrackingContract`, `SduiJson`), `ui-atoms` (16 stateless Compose atoms `UiText`, `UiIcon`, `UiTag`, `UiButton`, `UiImage`, `UiTextField`, `UiDivider`, `UiSpacer`, `UiProgress`, `UiSurfaceCard`, `UiSwitch`, `UiCheckbox`, `UiSlider`, `UiAvatar`, `UiSkeleton`, and animated `UiStepper` morphing `ADD` <-> `- qty +` with haptic hooks, `UiCelebrationOverlay` in `UiCelebrationBurst.kt`, `UiHapticFeedback.kt` with `UiHapticKind`, `LocalUiHapticFeedback`, `rememberComposeHapticFallback`, `MotionModifier.kt` with `applyMotionSpec`/`Modifier.shakeOnError`/`resolveMotionDurationMs`/`resolveMotionDelayMs`/`resolveEnterTransition`/`resolveExitTransition` honoring `ThemeTokens.motion.reducedMotion`, `UiImageRenderer`/`LocalImageRenderer`, `TokenResolvers`, `LayoutConfigModifier`, and `compose.uiTest` semantic tests in `UiAtomsComposeTest`).
  - **Layer 2**: `action-engine` (**18 built-in `ActionTypes`** adding `open_url` / `OpenUrlPayload`, `share` / `SharePayload`, `request_permission` / `RequestPermissionPayload`, `biometric_auth` / `BiometricAuthPayload` alongside `trigger_feedback` / `TriggerFeedbackPayload`, `ActionDecoderRegistry`, `ActionRegistry`, `DefaultActionDispatcher`, `ActionValidator`, `ActionOutcome.{OpenUrlRequested, ShareRequested, PermissionRequested, BiometricAuthRequested, FeedbackTriggered, OverlayRequested}`, `ApiCallExecutor` port), `network` (`HttpTransport`, `KtorHttpTransport`, `HttpClientFactory`, `NetworkConfig` with HTTP/2 ALPN, Brotli (`br`) decompression via `ktor-client-okhttp` + `org.brotli:dec`, bearer token refresh via `AuthTokenProvider`, and `CertificatePinMatcher` shared SPKI/cert pinning for JVM/OkHttp `CertificatePinner` and Apple Darwin `NSURLSession` `PlatformHttpEngine.apple.kt` across iOS, macOS, and watchOS).
  - **Layer 3**: `sdui-layout` (`LayoutController` with `observeConnectivity`, `handleDeepLink`, `isOffline`, `errorShakeSequence`, `errorShakeTargetId`, `dismissFeedback()`, and recursive form/product seeding via `collectAllEnvelopes`, `PlatformBridgeBundle`, `PlatformBridgeOutcomeExecutor`, `AdaptiveScreenContainers.kt` with `AdaptiveReadyViewport`, `SplitPaneBody`, `OfflineStatusBanner`, `SnippetRegistry`, `BuiltInSnippetModule` + responsive `ContainerSnippetRenderers.kt` + `ProductCardSnippetRenderer.kt` for all 14 snippet renderers, `SduiScreen` with `SnippetLazyList` `animateItem()`, `TabsSnippetRenderer` `AnimatedContent`, `AccordionSnippetRenderer` animated chevron, `FloatingCartDockHost` in `FloatingCartDock.kt` + `PageCartCalculator.kt` live subtotal & `cart_bounce` spring, `UiCelebrationOverlay` handling `FeedbackEffect.CELEBRATION`, `OverlayHost` with `ModalBottomSheet` & `AlertDialog`, `PullToRefreshBox`, `SkeletonScreenPlaceholder`, pagination `loadMore()`, `CachedScreenRepository` with `CACHE_FIRST`/`NETWORK_FIRST`/`STALE_WHILE_REVALIDATE`, `OfflineActionCoordinator`, `KtorImageRenderer`, `createSduiKoinModule` with `platformBridges` & `certificatePins`, `SduiHostRoot.kt` (`SduiHostedRoot`), iOS `createSduiViewController` + `AbetaSduiKit.xcframework`, macOS native Cocoa `launchMacOsSduiWindow` in `SduiMacOsWindow.kt`, `SduiScreenComposeTest`), `watch-companion` (headless wearable module with `WatchGlanceModels.kt`, `WatchScreenMapper`, `RemoteWatchPageSource`, `WatchRemoteApiCallExecutor`, `WatchCompanionController`, `createWatchCompanionModule`, `createAppleWatchCompanionHost`, and `AbetaWatchKit.xcframework` for `watchosArm64`/`watchosSimulatorArm64`/`watchosDeviceArm64`), `storybook` (**12 interactive stories across `FOUNDATIONS`, `ATOMS`, `SNIPPETS`, `PLAYGROUND`** adding `ResponsiveAndBridgesStory` / `StoryCatalog.STORY_RESPONSIVE_BRIDGES`, responsive `CompactStoryStrip`/`StorySidebar` shell, `ControlsAndDataAtomsStory`, `"All 14 Snippets"`, `MotionAndStoreStory`, `LiveBffStory` with `"store-home"` & `"watch-status"`, `fontScale` controls, Desktop JVM + WasmJS + macOS `launchStorybookMacOsWindow`, `StorybookViewController` + `AbetaStorybookKit.xcframework`, `StorybookComposeTest`), and `sample-app` (Compose Desktop + `--headless` across `home`, `form-demo`, `containers`, and `store-home` + order celebration + `--bff-url`, wired with `CachedScreenRepository`, `OfflineActionCoordinator`, `KtorImageRenderer`, `PlatformBridgeBundle` + `observeConnectivity`, `"containers"`, `"store-home"` **Abeta Fresh** 8-Min Express with responsive `columnsByWidth` & `adaptiveLayout` in `SampleStoreScreen.kt` + `SampleStoreCatalog.kt`, `SampleWatchScreen` for `"watch-status"`, `OfflineApiCallExecutor` handling `POST /api/v1/orders/express`, and `SampleContainer.DEFAULT_SCREENS` covering **7 screens**).
- **Go 1.26.0 SDUI BFF (`bff/`)**:
  - Entrypoint `./cmd/bff` using Clean Architecture (`internal/domain`, `internal/usecase`, `internal/handler`, `internal/infra`) powered by `github.com/abeta-dev/go-libs@v0.4.0` (`ginmw`, `env`, `logger`, `health`, `apperror`, `shutdown`) and Gin `v1.12.0`.
  - Production features: `CompressionMiddleware()` (`br` via `github.com/andybalholm/brotli` + `gzip`), `ETagMiddleware()` (SHA-256 `ETag` + `304 Not Modified`), `http.Server.Protocols` (`HTTP/1.1`, `h2c`, TLS HTTP/2), OpenAPI 3.1 (`GET /openapi.json`) & Scalar UI (`GET /docs`), optional `ginmw.GlobalRateLimit()`, optional `ginmw.AuthMiddleware()` (`JWT_SECRET`).
  - Endpoints `/healthz`, `/readyz`, `/metrics`, `/openapi.json`, `/docs`, `/api/v1/screens` (**7 screens**: `catalog`, `containers`, `form-demo`, `home`, `profile`, `store-home` with `columns_by_width` + `adaptive_layout`, `watch-status`), `/api/v1/screens/:screenId`, `/api/v1/actions/execute`, `/api/v1/orders/express` (`POST` in `router.go`, `openapi.go`, and `action_executor.go` returning `trigger_feedback` `celebration` + `show_toast` follow-ups), `/api/v1/catalog/tokens` wrapped in `ginmw.Envelope`.
  - Shared `pkg/sdui/{actions,atoms,snippets,theming}` v1 schema packages (15 Wire Atoms, 14 Snippet Types, **18 Action Types** adding `open_url` / `OpenURLPayload`, `share` / `SharePayload`, `request_permission` / `RequestPermissionPayload`, `biometric_auth` / `BiometricAuthPayload`, `MotionSpec` + `FeedbackEffect`, `FloatingCartSpec`, `ResponsiveColumnsSpec`, `ResponsiveIntSpec`, `AdaptivePageSpec`, `ScreenLayoutMode`) with 5 golden JSON parity fixtures (`home_screen.json`, `form_screen.json`, `action_batch.json`, `containers_screen.json`, `store_home.json`).
- **Documentation, Wiki, Skills, Per-Module Code Owners, Multi-IDE Support & CI**:
  - Technical guides in `docs/` (including `docs/module-owners-and-cicd.md` and `docs/ide-and-ai-setup.md`), GitHub Wiki pages in `wiki/`, **10 Agent Skills** in `skills/` (adding `abeta-kmp-cicd`, `abeta-kmp-usage`, `abeta-kmp-demo`, `abeta-kmp-platforms`, `abeta-kmp-module-review`), 5 core personas in `agents/`, and **16 Per-Module Code Owner Agents** in `agents/owners/` paired with `.github/CODEOWNERS`.
  - Universal multi-model & multi-IDE configs: `AGENTS.md`, `CLAUDE.md` + `.claude/`, `GEMINI.md` + `.gemini/` + `.geminiignore`, `.agent/` (Antigravity), `.cursor/rules/` + `.cursorignore`, `.junie/guidelines.md` + `.aiignore` (Android Studio / JetBrains), and `.github/copilot-instructions.md` + `.github/instructions/`.
  - GitHub Actions CI/CD workflows (`.github/workflows/`) adding `module-owner-architecture-review.yml` (deterministic Uncle Bob Dependency Rule, BOM/ABI, 1:1 wire parity, SOLID/KISS LOC gate, PR module-owner checklists & optional Claude/Gemini/OpenAI review) alongside KMP checks, Go BFF tests, quality/security hygiene, wiki synchronization, and SemVer releases.
  - Local verification & hygiene scripts in `scripts/` (`check_brand_hygiene.sh`, `check_architecture_and_owners.sh`, `verify_all.sh`).
