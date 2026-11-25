# AGENTS.md — AI Coding Agent Guide for `abeta-dev/kmp`

This file provides routing, architectural invariants, and verification gates for autonomous coding agents working in `abeta-dev/kmp`.

---

## 1. Agent Skill & Code Owner Routing Table

Load the matching `SKILL.md` and per-module Code Owner persona (`agents/owners/<module>-owner.md`) before planning or editing code in any area:

| Task Domain | Skill Path | Persona / Code Owner Reference |
| :--- | :--- | :--- |
| Monorepo navigation, cross-stack features, BOM setup | [skills/abeta-kmp-kit/SKILL.md](skills/abeta-kmp-kit/SKILL.md) | [agents/kmp-architect.md](agents/kmp-architect.md) |
| Repo usage, onboarding & `io.github.abeta-dev:bom` consumption | [skills/abeta-kmp-usage/SKILL.md](skills/abeta-kmp-usage/SKILL.md) | [agents/owners/bom-catalog-owner.md](agents/owners/bom-catalog-owner.md) |
| Demo & reference modules (`sample-app` 7-in-1 Super-App Showcase, `storybook` 20 stories / 31 atoms) | [skills/abeta-kmp-demo/SKILL.md](skills/abeta-kmp-demo/SKILL.md) | [agents/owners/sample-app-owner.md](agents/owners/sample-app-owner.md), [agents/owners/storybook-owner.md](agents/owners/storybook-owner.md) |
| Multi-OS platforms (Android, Wear OS, iOS, watchOS, macOS, Web) | [skills/abeta-kmp-platforms/SKILL.md](skills/abeta-kmp-platforms/SKILL.md) | [agents/owners/platforms-owner.md](agents/owners/platforms-owner.md), [agents/owners/core-owner.md](agents/owners/core-owner.md), [agents/owners/watch-companion-owner.md](agents/owners/watch-companion-owner.md) |
| Multiplatform local database (Room 3.0.3, SQLite drivers, schemas) | [skills/abeta-kmp-platforms/SKILL.md](skills/abeta-kmp-platforms/SKILL.md) | [agents/owners/database-owner.md](agents/owners/database-owner.md) |
| Design tokens, wire DTOs, stateless Compose atoms | [skills/abeta-kmp-atomic/SKILL.md](skills/abeta-kmp-atomic/SKILL.md) | [agents/atomic-designer.md](agents/atomic-designer.md), [agents/owners/ui-atoms-owner.md](agents/owners/ui-atoms-owner.md) |
| SDUI action decoders, handlers, `ActionRegistry`, dispatcher | [skills/abeta-kmp-actions/SKILL.md](skills/abeta-kmp-actions/SKILL.md) | [agents/action-engine-engineer.md](agents/action-engine-engineer.md), [agents/owners/action-engine-owner.md](agents/owners/action-engine-owner.md) |
| Go 1.26.0 SDUI BFF screens, `pkg/sdui/*`, `go-libs` handlers | [skills/abeta-kmp-bff/SKILL.md](skills/abeta-kmp-bff/SKILL.md) | [agents/bff-go-engineer.md](agents/bff-go-engineer.md), [agents/owners/bff-owner.md](agents/owners/bff-owner.md) |
| Architecture audits, PR review, Dependency Rule & BOM checks | [skills/abeta-kmp-architecture/SKILL.md](skills/abeta-kmp-architecture/SKILL.md) | [agents/solid-reviewer.md](agents/solid-reviewer.md) |
| Per-module Code Owner PR reviews (all 17 module owners) | [skills/abeta-kmp-module-review/SKILL.md](skills/abeta-kmp-module-review/SKILL.md) | [agents/owners/](agents/owners/) + [.github/CODEOWNERS](.github/CODEOWNERS) |
| CI/CD workflows, security gates & SemVer releases | [skills/abeta-kmp-cicd/SKILL.md](skills/abeta-kmp-cicd/SKILL.md) | [agents/owners/cicd-platform-owner.md](agents/owners/cicd-platform-owner.md) |

### Multi-IDE & Multi-Model Entrypoints
- **Claude Code**: `CLAUDE.md`, `.claude/settings.json`, `.claude/commands/`, `.claude/agents/`
- **Gemini / Antigravity**: `GEMINI.md`, `.gemini/settings.json`, `.geminiignore`, `.agent/rules/`, `.agent/workflows/`, `.antigravityignore`
- **Cursor**: `.cursor/rules/00-monorepo-core.mdc` .. `50-cicd-owners-skills.mdc`, `.cursorignore`
- **Android Studio / JetBrains Junie & AI**: `.junie/guidelines.md`, `.aiignore`, `GEMINI.md`
- **GitHub Copilot & Codex**: `.github/copilot-instructions.md`, `.github/instructions/`, `AGENTS.md` (see [docs/ide-and-ai-setup.md](docs/ide-and-ai-setup.md))

---

## 2. Architectural Truth Gates (Non-Negotiable)

All changes MUST satisfy these invariants:

1. **Uncle Bob Dependency Rule (Strict Layering)**:
   - **Supported KMP Targets & Reference Host Glue**:
     - Headless/contract publishable modules (`core`, `state`, `analytics`, `database`, `sdui-contracts`, `action-engine`, `network`, `watch-companion`): `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`, and `wasmJs`.
     - Compose UI modules (`design-tokens`, `ui-atoms`, `sdui-layout`) and `storybook`: `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, and `wasmJs`.
     - `sample-app`: JVM Desktop & `--headless` CLI.
     - Published Maven Central artifacts (`io.github.abeta-dev:*:0.7.0`, **11** library modules + `bom` + `dependencies` = **13** artifacts) target `jvm`, `ios*`, `macosArm64`, `watchos*`, and `wasmJs` (with guarded JVM AWT/file fallbacks). Drop-in reference host glue lives under `kmp/platforms/` (`android/{AndroidPlatformBridgeFactory,AbetaAndroidHostActivity,AndroidDatabaseFactory}.kt`, `wearos/{WearCompanionHostActivity,WearSduiRenderer}.kt`, `apple/AbetaAppleHosts.swift` & `apple/Sources/{AbetaSduiSwiftUI,AbetaWatchSwiftUI}/`, `web/README.md`, `README.md`); Android & Wear OS Gradle templates require an Android app module with `ANDROID_HOME` / Android SDK configured.
   - **Layer 0** (`core`, `state`, `analytics`, `database`): zero dependencies on Layers 1–3. `core` owns OS, Voice & Desktop bridges/ports (`PlatformCapabilities`/`currentPlatformCapabilities()`, `PlatformKind.{WATCHOS, WEAR_OS, LINUX_NATIVE, WINDOWS_NATIVE}`, `HapticEngine`/`HapticStyle`, `ClipboardPort`, `ExternalLauncherPort`, `SharePort`/`ShareRequest`, `ConnectivityMonitor`/`ConnectivityStatus`, `PermissionPort`, `LocationPort`, `FilePickerPort`, `PushNotificationPort`, `AudioCapturePort`, `SpeechToTextPort`, `DesktopSystemPort`, `LocaleFormatter` (`formatCurrencyCents`, `currentLocaleTag`), `DeepLinkParser`/`DeepLinkTarget`, `SecureKeyValueStore` (`AppleKeychain` `SecItem*` w/ `NSLock` & fallback, JVM `0600`/`0700` file-backed AES-256-GCM, WasmJS `sessionStorage`), `BiometricAuthenticator` (`LAContext` on Apple)). `state` (`SessionStore`, `DataStoreKeyValueStore`, `createDiskKeyValueStore`, `PreferenceStore`, `createSecurePersistentKeyValueStore(namespace)`, `FeatureFlagProvider`, `OfflineMutationQueue`), `analytics`, and `database` (Room 3 `3.0.3` KMP SQLite database: `AbetaDatabase` schema **v2** w/ `MIGRATION_1_2`, `AbetaLocalDatabase`, `cached_screens` incl. `pageVersion`/`appVersion`, `offline_mutations`, `key_value_records`, `sqlite-bundled:2.7.1`, `sqlite-web:2.7.1`, KSP `2.3.14`, schema exports `1.json` & `2.json` under `kmp/database/schemas/` — see [docs/local-database-room3.md](docs/local-database-room3.md)) depend only on `core`.
   - **Layer 1** (`design-tokens`, `sdui-contracts`, `ui-atoms`): `design-tokens` owns all design tokens including `ResponsiveTokens.kt` (`WindowWidthClass`, `WindowHeightClass`, `DeviceFormFactor`, `AdaptiveLayoutMetrics`, `resolveAdaptiveLayoutMetrics`, `LocalAdaptiveLayoutMetrics`, `ProvideAdaptiveLayoutMetrics`) exposed via `ThemeTokens.adaptiveMetrics`. `sdui-contracts` owns all pure `@Serializable` wire DTOs (**29 Wire Atoms** incl. `StepperAtom`, `CountdownAtom` w/ `expires_at_epoch_ms`, `MediaPagerAtom`, & 12 `ExtendedAtoms.kt` primitives, **23 `SnippetTypes`** incl. `product_card`, `swipe_deck`/`search_bar`/`chip_group`, `nudge_bar`/`marquee_strip`/`order_tracker`, **`composed_block`** + **36 `ComposedNodeKind`s**, `custom_slot`, and `graph_canvas`, **6 `SnippetDeltaOp`s** (`append_text`, `patch_snippet_data`, `replace_snippet`, `insert_snippet`, `remove_snippet`, `update_page_meta`), `ScreenManifestResponse` w/ `sdui_schema_version`, `OsPermissionKind`, `ScreenLayoutMode`, `AdaptiveContracts.kt`, `PageResponse` versioning/streaming/adaptive fields, `MotionContracts.kt` + `AtomEnums.kt` with **7 Ambient `MotionLoopEffect`s** (`pulse`, `shimmer`, `wiggle`, `float`, `heartbeat`, `glow_ring`, `marquee` + `none`), `FloatingCartSpec` with free-delivery threshold nudge fields, `PaginationSpec`, `OverlayPresentation`, `TabItemSpec.title: String`; neither depends on other KMP modules). `ui-atoms` (**31 stateless Compose atoms** incl. `UiSurfaceCard`, `UiSwitch`, `UiCheckbox`, `UiSlider`, `UiAvatar`, `UiSkeleton`, animated `UiStepper`, live urgency `UiCountdown`, odometer `UiRollingCounter`, `UiMediaPager`, plus 12 extended atoms `UiRadioGroup`, `UiSelect`, `UiOtpInput`, `UiChip`, `UiSegmentedControl`, `UiRating`, `UiPrice`, `UiBadge`, `UiAlertBanner`, `UiStatTile`, `UiKeyValueRow`, real ISO/IEC 18004 `UiQrCode`, plus `UiCelebrationOverlay`, `UiHapticFeedback.kt`, `MotionModifier.kt` + `MotionLoopModifiers.kt` incl. **7 Ambient `MotionLoopEffect`s**, `Modifier.pressBounce`, `Modifier.shakeOnError` honoring `ThemeTokens.motion.reducedMotion`) depends only on `design-tokens` and `sdui-contracts`. Layer 1 must NEVER depend on `core`, `action-engine`, `network`, or `sdui-layout`.
   - **Layer 2** (`action-engine`, `network`): `action-engine` (**18 built-in `ActionTypes`** incl. `open_url`, `share`, `request_permission`, `biometric_auth`, `trigger_feedback` + `ActionOutcome.*`, bounded `DispatchBudget`) depends only on `core` and `sdui-contracts`; `network` (`HttpClientFactory`, `KtorHttpTransport` with HTTP/2 ALPN, Brotli `br`, `If-None-Match`/`304`, `SseTransport`/`KtorSseTransport`, `CsrfTokenProvider` + single `403` retry, `NetworkConfig.appVersion` -> `X-Client-App-Version`, bearer refresh, and validated `sha256/` `CertificatePinMatcher` for JVM/OkHttp & Apple Darwin `NSURLSession` across iOS, macOS, and watchOS) depends only on `core`. Neither may depend on `ui-atoms`, `sdui-layout`, `watch-companion`, `sample-app`, `storybook`, or each other.
   - **Layer 3** (`sdui-layout`, `watch-companion`, `storybook`, `sample-app`): `sdui-layout` orchestrates Layer 0–2 modules (`LayoutController` with `observeConnectivity`, `handleDeepLink`, `isOffline`, `applyDeltaPatch`/`observeStream`, `errorShakeSequence`, `dismissFeedback()`, `SduiLayoutViewModel` + `SavedState`, `PlatformBridgeBundle`, `PlatformBridgeOutcomeExecutor`, `AdaptiveScreenContainers.kt`, **23 snippet renderers** via `BuiltInSnippetModule` incl. `NudgeBar`, `MarqueeStrip`, `OrderTracker`, **`ComposedBlock`** w/ **36 `ComposedNodeKind`s**, `CustomSlot` + `CustomSlotRegistry`, and `GraphCanvas`, `FloatingCartDockHost` w/ free-delivery threshold nudge + `PageCartCalculator`, `UiCelebrationOverlay`, rich SDUI `ModalBottomSheet` & `AlertDialog`, `PullToRefreshBox`, `SkeletonScreenPlaceholder`, `loadMore()`, `CachedScreenRepository` (`VERSION_GATED`, `304`, `/manifest`), `RemoteBffAnalyticsTracker`, `OfflineActionCoordinator`, `KtorImageRenderer`, `createSduiKoinModule`, `SduiHostedRoot`, iOS `createSduiViewController` + `AbetaSduiKit.xcframework`, macOS Cocoa `launchMacOsSduiWindow`, `SduiScreenComposeTest`); `watch-companion` provides headless wearable orchestration (`WatchGlanceModels.kt`, `WatchScreenMapper`, `RemoteWatchPageSource` w/ ETag/304 cache, `WatchRemoteApiCallExecutor`, `WatchCompanionController`, `createWatchCompanionModule`, `createAppleWatchCompanionHost`, `AbetaWatchKit.xcframework`, depending only on `core`, `state`, `analytics`, `database`, `sdui-contracts`, `action-engine`, `network`—no Compose UI); `storybook` provides **20 interactive stories across 5 categories** (`LIVE_DEMOS`, `FOUNDATIONS`, `ATOMS` — 8 stories covering all **31** atoms, `SNIPPETS` — `"All 23 Snippets"`, `PLAYGROUND`, featuring **Live 7-in-1 Super-App Demos** `super-app-demos`, `ResponsiveAndBridgesStory` w/ `Voice & Desktop OS Bridges`, `DiscoveryAndSearchStory`, **Animation & Nudges Lab** `animation-nudges`, **Atomic Snippet Crafter** `snippet-crafter`, **End-to-End App Journeys** `e2e-journeys`, and `LiveBffStory`, `launchStorybookMacOsWindow`, `AbetaStorybookKit.xcframework`, `StorybookComposeTest`); `sample-app` binds concrete implementations (**7-in-1 Super-App Showcase Launcher** featuring **Abeta Fresh**, **Abeta Bites**, **Abeta Pulse**, **Abeta Mart**, **Abeta Spark**, **Abeta Trade**, and **Abeta Pay** + core reference screens + `watch-status`, `--headless`, `--verticals`, `--bff-url`).
   - **Go BFF (`bff/` — `bff@v0.9.0`)**: strict Clean Architecture (`internal/handler` -> `internal/usecase` -> `internal/domain` <- `internal/infra`), exporting **8 packages** under `pkg/sdui/`: `actions` (**18 Action Types**), `analytics`, `atoms` (**29 Wire Atoms** incl. `MediaPagerAtom` & `CountdownAtom.ExpiresAtEpochMs`, **7 Ambient `MotionLoopEffect`s**), `cache` (`UpstreamCachedScreenRepository` w/ `singleflight`, prefix/tag/all invalidation, O(1) `BuildManifest`), `molecules`, `server` (fail-closed `CORSMiddleware`, `private, no-cache` `ETagMiddleware` honoring `page.ETag` & version headers, pooled `CompressionMiddleware`, per-router `JWTAuthMiddleware`, `HTTPUpstreamFetcher`, `StreamProvider`), `snippets` (**23 Snippet Types** incl. `composed_block` w/ **36 `ComposedNodeKind`s**, `custom_slot`, `graph_canvas`, **6 `SnippetDeltaOp`s**), and `theming`. Serves **12 built-in screens** in `InMemoryScreenRepository` (`home`, `catalog`, `containers`, `profile`, `form-demo`, `fresh-home`, `store-home`, `watch-status`, `pay-home`, `pay-transfer`, `pay-cards`, `pay-receipt`) across live routes `/healthz`, `/readyz`, `/metrics`, `/openapi.json`, `/docs`, `/api/v1/screens`, `/api/v1/manifest`, `/api/v1/screens/manifest`, `/api/v1/screens/:screenId`, `/api/v1/streams/:streamId` (SSE w/ `Last-Event-ID` & `204` catch-up), `POST /api/v1/actions/execute`, `POST /api/v1/orders/express`, `POST /api/v1/pay/transfer`, `GET /api/v1/catalog/tokens`, `POST /api/v1/analytics/events`, and `GET /api/v1/analytics/summary`.
2. **Atomic / Action Separation**:
   - `sdui-contracts` defines only pure serializable wire references (`ActionRef`, `ValidationRuleContract`, `TrackingContract`).
   - `ui-atoms` renders **31 stateless Compose atoms** (`UiText`, `UiIcon`, `UiTag`, `UiButton`, `UiImage`, `UiTextField`, `UiDivider`, `UiSpacer`, `UiProgress`, `UiSurfaceCard`, `UiSwitch`, `UiCheckbox`, `UiSlider`, `UiAvatar`, `UiSkeleton`, `UiStepper`, `UiCountdown`, `UiRollingCounter`, `UiMediaPager`, `UiRadioGroup`, `UiSelect`, `UiOtpInput`, `UiChip`, `UiSegmentedControl`, `UiRating`, `UiPrice`, `UiBadge`, `UiAlertBanner`, `UiStatTile`, `UiKeyValueRow`, ISO/IEC 18004 `UiQrCode`), plus `UiCelebrationOverlay`, `UiHapticFeedback.kt`, and `MotionModifier.kt` + `MotionLoopModifiers.kt` (**7 Ambient `MotionLoopEffect`s**, `Modifier.pressBounce`, `Modifier.shakeOnError`), and emits `ActionRef` callbacks (`onAction: (ActionRef) -> Unit`). It NEVER imports `core`, `ActionHandler`, `ActionDispatcher`, or Ktor.
   - `action-engine` implements Open/Closed (`OCP`) registration via `ActionPayloadDecoder` / `ActionDecoderRegistry`, `ActionHandler` / `ActionRegistry`, and `DefaultActionDispatcher`. No monolithic `when (actionType)` switch inside UI or core layers.
3. **Gradle BOM, Catalog & ABI Discipline**:
   - Declare all external dependency versions in `kmp/dependencyManagement/gradle/libs.versions.toml` and internal coordinates in `kmp/dependencyManagement/gradle/internal.versions.toml`.
   - Never hardcode dependency versions inside module `build.gradle.kts` files.
   - Register every publishable module in `kmp/kmp-bom/build.gradle.kts`, `kmp/build.gradle.kts` (`publishableModules`), and `kmp/settings.gradle.kts` (`moduleMappings` for `abeta.kmpLocalBuild`).
   - Maintain public ABI descriptors via Binary Compatibility Validator (`./gradlew apiDump` / `apiCheck`) across all **11 publishable KMP library modules** (`core`, `state`, `analytics`, `database`, `design-tokens`, `sdui-contracts`, `ui-atoms`, `action-engine`, `network`, `sdui-layout`, `watch-companion` + `bom` + `dependencies` = **13 Maven Central artifacts**). See [docs/publishing-and-hosting.md](docs/publishing-and-hosting.md) for Maven Central (`io.github.abeta-dev:*:0.7.0`), Cloudsmith Open Source, 4-product SwiftPM (`Abeta-dev/kmp-spm` `0.7.0`: `AbetaSduiKit`, `AbetaWatchKit`, `AbetaSduiSwiftUI`, `AbetaWatchSwiftUI`), and Go proxy (`bff@v0.9.0`) releases.
4. **Zero Legacy Brand Leaks & Zero Machine-Specific Paths**:
   - Strictly use `abeta-dev`, `io.github.abeta-dev` (Maven), `github.com/abeta-dev/kmp`, and `dev.abeta.kmp` (Kotlin packages).
   - Never introduce machine-specific absolute paths (e.g., home directory paths) into committed code, docs, scripts, or workflows.
   - Run `./scripts/check_brand_hygiene.sh` before concluding any task.
5. **1:1 Wire Protocol Parity**:
   - Any schema change in `kmp/sdui-contracts` or `kmp/action-engine` (`ActionTypes`, payloads) MUST have a matching change in `bff/pkg/sdui/*` and all **11** golden JSON parity tests in `bff/testdata/golden/` (`home_screen.json`, `form_screen.json`, `action_batch.json`, `containers_screen.json`, `store_home.json`, `interactive_screen.json`, `nudges_and_composed_screen.json`, `extended_atoms_screen.json`, `express_molecules_and_analytics_screen.json`, `streaming_and_versioned_screen.json`, `spatial_custom_and_graph_screen.json`).

---

## 3. Verification Commands

Run only the checks relevant to your changes—or the full suite before release/PRs:

```bash
# 1. Brand hygiene & architecture / module-owner / wire-parity gates
./scripts/check_brand_hygiene.sh
./scripts/check_architecture_and_owners.sh

# 2. KMP formatting, static analysis, ABI apiCheck, compilation & tests (Java 21, Gradle 9.7.0)
(cd kmp && ./gradlew check)

# 3. Sample app headless smoke check (home, form-demo, containers, store-home + celebration)
(cd kmp && ./gradlew :sample-app:run --args="--headless")

# 4. Binary Compatibility Validator dump & Dokka v2 HTML docs
(cd kmp && ./gradlew apiDump)
(cd kmp && ./gradlew :core:dokkaGenerateHtml) # or ./gradlew dokkaGenerateHtml

# 5. Storybook (Desktop JVM & WasmJS) and Apple XCFrameworks
(cd kmp && ./gradlew :storybook:run)
(cd kmp && ./gradlew :storybook:wasmJsBrowserDevelopmentRun)
(cd kmp && ./gradlew :sdui-layout:assembleAbetaSduiKitXCFramework :watch-companion:assembleAbetaWatchKitXCFramework :storybook:assembleAbetaStorybookKitXCFramework)

# 6. Go BFF vet & race-enabled unit/golden tests (Go 1.26.0)
(cd bff && go vet ./... && go test -race -cover ./...)

# 7. Full monorepo gate
./scripts/verify_all.sh
```

> **Honesty Rule**: Never claim a build, lint, or test suite passed unless you executed it after your final edit and directly observed its passing output.
