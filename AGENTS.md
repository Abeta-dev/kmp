# AGENTS.md — AI Coding Agent Guide for `abeta-dev/kmp`

This file provides routing, architectural invariants, and verification gates for autonomous coding agents working in `abeta-dev/kmp`.

---

## 1. Agent Skill & Code Owner Routing Table

Load the matching `SKILL.md` and per-module Code Owner persona (`agents/owners/<module>-owner.md`) before planning or editing code in any area:

| Task Domain | Skill Path | Persona / Code Owner Reference |
| :--- | :--- | :--- |
| Monorepo navigation, cross-stack features, BOM setup | [skills/abeta-kmp-kit/SKILL.md](skills/abeta-kmp-kit/SKILL.md) | [agents/kmp-architect.md](agents/kmp-architect.md) |
| Repo usage, onboarding & `io.github.abeta-dev:bom` consumption | [skills/abeta-kmp-usage/SKILL.md](skills/abeta-kmp-usage/SKILL.md) | [agents/owners/bom-catalog-owner.md](agents/owners/bom-catalog-owner.md) |
| Demo & reference modules (`sample-app` 7-in-1 Super-App Showcase, `storybook` 20 stories / 30 atoms) | [skills/abeta-kmp-demo/SKILL.md](skills/abeta-kmp-demo/SKILL.md) | [agents/owners/sample-app-owner.md](agents/owners/sample-app-owner.md), [agents/owners/storybook-owner.md](agents/owners/storybook-owner.md) |
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
     - Drop-in reference host glue under `kmp/platforms/` (`android/AndroidPlatformBridgeFactory.kt`, `android/AndroidDatabaseFactory.kt`, `wearos/WearCompanionHostActivity.kt`, `apple/AbetaAppleHosts.swift`, `apple/Package.template.swift`, `web/README.md`, `README.md`); Android & Wear OS Gradle templates require an Android app module with `ANDROID_HOME` / Android SDK configured.
   - **Layer 0** (`core`, `state`, `analytics`, `database`): zero dependencies on Layers 1–3. `core` owns OS bridges & ports (`PlatformCapabilities`/`currentPlatformCapabilities()`, `PlatformKind.{WATCHOS, WEAR_OS, LINUX_NATIVE, WINDOWS_NATIVE}`, `HapticEngine`/`HapticStyle`, `ClipboardPort`, `ExternalLauncherPort`, `SharePort`/`ShareRequest`, `ConnectivityMonitor`/`ConnectivityStatus`, `PermissionPort`, `LocationPort`, `FilePickerPort`, `PushNotificationPort`, `LocaleFormatter` (`formatCurrencyCents`, `currentLocaleTag`), `DeepLinkParser`/`DeepLinkTarget`, `SecureKeyValueStore` (`AppleKeychain` `SecItem*` w/ fallback, JVM AES-256-GCM + `Preferences`, WasmJS `sessionStorage`), `BiometricAuthenticator` (`LAContext` on Apple)). `state` (`SessionStore`, `PreferenceStore`, `createSecurePersistentKeyValueStore(namespace)` in `SettingsKeyValueStore.kt`, `FeatureFlagProvider`, `OfflineMutationQueue`), `analytics`, and `database` (Room 3 `3.0.3` KMP SQLite database: `AbetaDatabase`, `AbetaLocalDatabase`, `cached_screens`, `offline_mutations`, `key_value_records`, `sqlite-bundled:2.7.1`, `sqlite-web:2.7.1`, KSP `2.3.14`, schema exports under `kmp/database/schemas/` — see [docs/local-database-room3.md](docs/local-database-room3.md)) depend only on `core`.
   - **Layer 1** (`design-tokens`, `sdui-contracts`, `ui-atoms`): `design-tokens` owns all design tokens including `ResponsiveTokens.kt` (`WindowWidthClass`, `WindowHeightClass`, `DeviceFormFactor`, `AdaptiveLayoutMetrics`, `resolveAdaptiveLayoutMetrics`, `LocalAdaptiveLayoutMetrics`, `ProvideAdaptiveLayoutMetrics`) exposed via `ThemeTokens.adaptiveMetrics`. `sdui-contracts` owns all pure `@Serializable` wire DTOs (**29 Wire Atoms** incl. `StepperAtom`, `CountdownAtom`, & `ExtendedAtoms.kt`, **21 `SnippetTypes`** incl. `product_card`, `InteractiveSnippetContracts.kt`: `swipe_deck`/`search_bar`/`chip_group`, and `NudgeAndComposedContracts.kt`: `nudge_bar`/`NudgeBarSnippetData`, `marquee_strip`/`MarqueeStripSnippetData`, `order_tracker`/`OrderTrackerSnippetData`, **`composed_block`**/`ComposedBlockSnippetData` + **36 `ComposedNodeKind`s**, `OsPermissionKind`, `ScreenLayoutMode`, `AdaptiveContracts.kt`, `PageResponse.adaptiveLayout`, `CarouselSnippetData.itemWidthByWidth`, `GridSnippetData.columnsByWidth`, `MotionContracts.kt` + `AtomEnums.kt` with **7 Ambient `MotionLoopEffect`s** (`pulse`, `shimmer`, `wiggle`, `float`, `heartbeat`, `glow_ring`, `marquee`), `FloatingCartSpec` with free-delivery threshold nudge fields (`freeDeliveryThresholdCents`, `nudgePrefix`, `unlockedLabel`), `PaginationSpec`, `OverlayPresentation`, `TabItemSpec.title: String`; neither depends on other KMP modules). `ui-atoms` (**31 stateless Compose atoms** incl. `UiSurfaceCard`, `UiSwitch`, `UiCheckbox`, `UiSlider`, `UiAvatar`, `UiSkeleton`, animated `UiStepper`, live urgency `UiCountdown`, odometer `UiRollingCounter`, plus 12 extended atoms `UiRadioGroup`, `UiSelect`, `UiOtpInput`, `UiChip`, `UiSegmentedControl`, `UiRating`, `UiPrice`, `UiBadge`, `UiAlertBanner`, `UiStatTile`, `UiKeyValueRow`, `UiQrCode`, `UiCelebrationOverlay`, `UiHapticFeedback.kt`, `MotionModifier.kt` + `MotionLoopModifiers.kt` incl. **7 Ambient `MotionLoopEffect`s**, `Modifier.pressBounce`, `Modifier.shakeOnError` honoring `ThemeTokens.motion.reducedMotion`, `UiAtomsComposeTest`, `ExtendedAtomsComposeTest`) depends only on `design-tokens` and `sdui-contracts`. Layer 1 must NEVER depend on `core`, `action-engine`, `network`, or `sdui-layout`.
   - **Layer 2** (`action-engine`, `network`): `action-engine` (**18 built-in `ActionTypes`** adding `open_url` / `OpenUrlPayload`, `share` / `SharePayload`, `request_permission` / `RequestPermissionPayload`, `biometric_auth` / `BiometricAuthPayload` alongside `trigger_feedback` / `TriggerFeedbackPayload`, plus `ActionOutcome.{OpenUrlRequested, ShareRequested, PermissionRequested, BiometricAuthRequested, FeedbackTriggered}`) depends only on `core` and `sdui-contracts`; `network` (`HttpClientFactory`, `KtorHttpTransport` with HTTP/2 ALPN, Brotli `br`, bearer refresh, and `CertificatePinMatcher` shared SPKI/cert pinning for JVM/OkHttp `CertificatePinner` & Apple Darwin `NSURLSession` `PlatformHttpEngine.apple.kt` across iOS, macOS, and watchOS) depends only on `core`. Neither may depend on `ui-atoms`, `sdui-layout`, `watch-companion`, `sample-app`, `storybook`, or each other.
   - **Layer 3** (`sdui-layout`, `watch-companion`, `storybook`, `sample-app`): `sdui-layout` orchestrates Layer 0–2 modules (`LayoutController` with `observeConnectivity`, `handleDeepLink`, `isOffline`, `errorShakeSequence`, `errorShakeTargetId`, `dismissFeedback()`, `collectAllEnvelopes`, `PlatformBridgeBundle`, `PlatformBridgeOutcomeExecutor`, `AdaptiveScreenContainers.kt` (`AdaptiveReadyViewport`, `SplitPaneBody`, `OfflineStatusBanner`), **21 snippet renderers** via `BuiltInSnippetModule` incl. `NudgeBarSnippetRenderer`, `MarqueeStripSnippetRenderer`, `OrderTrackerSnippetRenderer`, and **`ComposedBlockSnippetRenderer`** Atomic Snippet Molding Engine with **36 `ComposedNodeKind`s** (depth <= 6), `FloatingCartDockHost` with live free-delivery threshold nudge progress & unlock bounce + `PageCartCalculator`, `UiCelebrationOverlay`, `ModalBottomSheet` & `AlertDialog`, `PullToRefreshBox`, `SkeletonScreenPlaceholder`, `loadMore()`, `CachedScreenRepository`, `OfflineActionCoordinator`, `KtorImageRenderer`, `createSduiKoinModule` (`platformBridges`, `certificatePins`), `SduiHostRoot.kt` (`SduiHostedRoot`), iOS `createSduiViewController` + `AbetaSduiKit.xcframework`, macOS Cocoa `launchMacOsSduiWindow` (`SduiMacOsWindow.kt`), `SduiScreenComposeTest`); `watch-companion` provides headless wearable orchestration (`WatchGlanceModels.kt`, `WatchScreenMapper`, `RemoteWatchPageSource`, `WatchRemoteApiCallExecutor`, `WatchCompanionController`, `createWatchCompanionModule`, `createAppleWatchCompanionHost`, `AbetaWatchKit.xcframework` for `watchosArm64`/`watchosSimulatorArm64`/`watchosDeviceArm64`, depending only on `core`, `state`, `analytics`, `sdui-contracts`, `action-engine`, `network`—no Compose UI); `storybook` provides **20 interactive stories across 5 categories** (`LIVE_DEMOS`, `FOUNDATIONS`, `ATOMS` (8 stories covering all 30 atoms), `SNIPPETS`, `PLAYGROUND`, featuring **Live 7-in-1 Super-App Demos** `super-app-demos` with zero-backend offline BFF parity & `?story=&app=` URL deep links, `"All 21 Snippets"`, `ResponsiveAndBridgesStory`, `DiscoveryAndSearchStory`, **Animation & Nudges Lab** `animation-nudges`, **Atomic Snippet Crafter** `snippet-crafter`, and **End-to-End App Journeys** `e2e-journeys`, responsive `CompactStoryStrip`/`StorySidebar` shell, Built-in Demo + Remote BFF mode with 7-app Super-App slugs & `"watch-status"` in `LiveBffStory`, `launchStorybookMacOsWindow`, `AbetaStorybookKit.xcframework`, `StorybookComposeTest`); `sample-app` binds concrete implementations (`CachedScreenRepository`, `OfflineActionCoordinator`, `KtorImageRenderer`, `PlatformBridgeBundle` + `observeConnectivity`, **7-in-1 Super-App Showcase Launcher** featuring **Abeta Fresh**, **Abeta Bites**, **Abeta Pulse**, **Abeta Mart**, **Abeta Spark**, **Abeta Trade**, and **Abeta Pay** enriched with `marquee_strip`, urgency `nudge_bar` + `CountdownAtom`, animated `order_tracker` `#BTS-4091`/`#PAY-3081`, molded `composed_block` cards, and `FloatingCartSpec` free-delivery threshold nudges, plus `SampleWatchScreen` for `"watch-status"`, responsive `columnsByWidth` & `adaptiveLayout` on `SampleStoreScreen`/`SampleStoreCatalog`, `OfflineApiCallExecutor`, `--headless`, `--verticals`, `--bff-url`).
   - **Go BFF (`bff/`)**: strict Clean Architecture (`internal/handler` -> `internal/usecase` -> `internal/domain` <- `internal/infra`), with `CompressionMiddleware()` (`br`/`gzip`), `ETagMiddleware()` (`304`), `http.Server.Protocols` (`h2c` + TLS HTTP/2), `/openapi.json` & `/docs` Scalar UI, `POST /api/v1/orders/express`, optional `ginmw.GlobalRateLimit()` & `ginmw.AuthMiddleware()` (`JWT_SECRET`), `pkg/sdui/{actions,atoms,snippets,theming}` (`bff@v0.6.0`) holding pure v1 wire types (**29 Wire Atoms** incl. `extended_atoms.go`, **21 Snippet Types** incl. `swipe_deck`, `search_bar`, `chip_group`, `nudge_bar`, `marquee_strip`, `order_tracker`, and **`composed_block`** with **36 `ComposedNodeKind`s** + server-molded recipe helpers, **18 Action Types**, `MotionSpec` with **7 Ambient `MotionLoopEffect`s**, `FloatingCartSpec` with free-delivery threshold nudges, `ResponsiveColumnsSpec`, `ResponsiveIntSpec`, `AdaptivePageSpec`, `ScreenLayoutMode`), core screens (`catalog`, `containers`, `form-demo`, `home`, `profile`, `store-home`, `watch-status`) plus **7-in-1 Super-App Showcase** screens (`fresh-home`, `bites-home`, `pulse-home`, `mart-home`, `spark-deck`, `trade-home`, `pay-home`), updated `DefaultActionExecutor`, and `./cmd/bff` as entrypoint.
2. **Atomic / Action Separation**:
   - `sdui-contracts` defines only pure serializable wire references (`ActionRef`, `ValidationRuleContract`, `TrackingContract`).
   - `ui-atoms` renders **31 stateless Compose atoms** (`UiText`, `UiIcon`, `UiTag`, `UiButton`, `UiImage`, `UiTextField`, `UiDivider`, `UiSpacer`, `UiProgress`, `UiSurfaceCard`, `UiSwitch`, `UiCheckbox`, `UiSlider`, `UiAvatar`, `UiSkeleton`, `UiStepper`, `UiCountdown`, `UiRollingCounter`, `UiRadioGroup`, `UiSelect`, `UiOtpInput`, `UiChip`, `UiSegmentedControl`, `UiRating`, `UiPrice`, `UiBadge`, `UiAlertBanner`, `UiStatTile`, `UiKeyValueRow`, `UiQrCode`), plus `UiCelebrationOverlay`, `UiHapticFeedback.kt`, and `MotionModifier.kt` + `MotionLoopModifiers.kt` (**7 Ambient `MotionLoopEffect`s**, `Modifier.pressBounce`, `Modifier.shakeOnError`), and emits `ActionRef` callbacks (`onAction: (ActionRef) -> Unit`). It NEVER imports `core`, `ActionHandler`, `ActionDispatcher`, or Ktor.
   - `action-engine` implements Open/Closed (`OCP`) registration via `ActionPayloadDecoder` / `ActionDecoderRegistry`, `ActionHandler` / `ActionRegistry`, and `DefaultActionDispatcher`. No monolithic `when (actionType)` switch inside UI or core layers.
3. **Gradle BOM, Catalog & ABI Discipline**:
   - Declare all external dependency versions in `kmp/dependencyManagement/gradle/libs.versions.toml` and internal coordinates in `kmp/dependencyManagement/gradle/internal.versions.toml`.
   - Never hardcode dependency versions inside module `build.gradle.kts` files.
   - Register every publishable module in `kmp/kmp-bom/build.gradle.kts`, `kmp/build.gradle.kts` (`publishableModules`), and `kmp/settings.gradle.kts` (`moduleMappings` for `abeta.kmpLocalBuild`).
   - Maintain public ABI descriptors via Binary Compatibility Validator (`./gradlew apiDump` / `apiCheck`) across all **11 publishable KMP library modules** (`core`, `state`, `analytics`, `database`, `design-tokens`, `sdui-contracts`, `ui-atoms`, `action-engine`, `network`, `sdui-layout`, `watch-companion`). See [docs/publishing-and-hosting.md](docs/publishing-and-hosting.md) for Maven Central, Cloudsmith Open Source, SwiftPM (`Abeta-dev/kmp-spm`), and Go proxy releases.
4. **Zero Legacy Brand Leaks & Zero Machine-Specific Paths**:
   - Strictly use `abeta-dev`, `github.com/abeta-dev/kmp`, and `dev.abeta.kmp`.
   - Never introduce machine-specific absolute paths (e.g., home directory paths) into committed code, docs, scripts, or workflows.
   - Run `./scripts/check_brand_hygiene.sh` before concluding any task.
5. **1:1 Wire Protocol Parity**:
   - Any schema change in `kmp/sdui-contracts` or `kmp/action-engine` (`ActionTypes`, payloads) MUST have a matching change in `bff/pkg/sdui/*` and golden JSON parity tests in `bff/testdata/golden/` (`home_screen.json`, `form_screen.json`, `action_batch.json`, `containers_screen.json`, `store_home.json`, `interactive_screen.json`, `nudges_and_composed_screen.json`, `extended_atoms_screen.json`).

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
