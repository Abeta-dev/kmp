# CLAUDE.md — Claude Code Instructions for `abeta-dev/kmp`

This repository is **Abeta KMP Kit (`abeta-dev/kmp`)**, a monorepo combining a layered Kotlin Multiplatform + Compose Multiplatform SDUI Kit (`kmp/`, **10 publishable library modules** plus `kmp-bom` and `dependencyManagement`) and a Go 1.26.0 SDUI BFF (`bff/`).

Read [AGENTS.md](AGENTS.md) for the complete architectural contract. Below is the quick operational checklist for every session.

---

## Skill & Module Code Owner Routing

Before editing code in a domain, load the corresponding skill (10 skills total) and the responsible Code Owner persona in `agents/owners/` (16 per-module owners matching `.github/CODEOWNERS`):

- **Monorepo / Cross-Cutting**: [skills/abeta-kmp-kit/SKILL.md](skills/abeta-kmp-kit/SKILL.md)
- **Repo Usage & Onboarding (`dev.abeta.kmp:bom`)**: [skills/abeta-kmp-usage/SKILL.md](skills/abeta-kmp-usage/SKILL.md)
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

- **Group & Brand**: `dev.abeta.kmp` / `github.com/abeta-dev/kmp`. Never commit legacy brand names or local absolute filesystem paths.
- **KMP Targets & Host Glue**:
  - Headless/contract publishable modules (`core`, `state`, `analytics`, `sdui-contracts`, `action-engine`, `network`, `watch-companion`): `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`, `wasmJs`.
  - Compose UI modules (`design-tokens`, `ui-atoms`, `sdui-layout`) and `storybook`: `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `wasmJs`.
  - `sample-app`: JVM Desktop & `--headless` CLI.
  - Drop-in reference host glue under `kmp/platforms/` (`android/AndroidPlatformBridgeFactory.kt`, `wearos/WearCompanionHostActivity.kt`, `apple/AbetaAppleHosts.swift`, `README.md` — Android & Wear OS templates require an Android app module with `ANDROID_HOME` / Android SDK configured).
- **Layer Boundaries**:
  - `Layer 0`: `core` (primitives + OS bridges/ports: `PlatformCapabilities`/`currentPlatformCapabilities()`, `PlatformKind.{WATCHOS, WEAR_OS, LINUX_NATIVE, WINDOWS_NATIVE}`, `HapticEngine`, `ClipboardPort`, `ExternalLauncherPort`, `SharePort`, `ConnectivityMonitor`, `PermissionPort`, `LocationPort`, `FilePickerPort`, `PushNotificationPort`, `LocaleFormatter`, `DeepLinkParser`, `SecureKeyValueStore`, `BiometricAuthenticator`), `state` (`SessionStore`, `PreferenceStore`, `createSecurePersistentKeyValueStore(namespace)` in `SettingsKeyValueStore.kt`, `FeatureFlagProvider`, `OfflineMutationQueue`), `analytics`
  - `Layer 1`: `design-tokens` (all tokens + `ResponsiveTokens.kt` exposed via `ThemeTokens.adaptiveMetrics`) & `sdui-contracts` (15 Wire Atoms incl. `StepperAtom`, `AdaptiveContracts.kt` (`ResponsiveColumnsSpec`, `ResponsiveIntSpec`, `AdaptivePageSpec`), `OsPermissionKind`, `ScreenLayoutMode`, `PageResponse.adaptiveLayout`, `CarouselSnippetData.itemWidthByWidth`, `GridSnippetData.columnsByWidth`, `MotionContracts.kt` + `AtomEnums.kt`, **17 `SnippetTypes`** incl. `product_card`, `swipe_deck`, `search_bar`, `chip_group` in `InteractiveSnippetContracts.kt`, `FloatingCartSpec`, `PaginationSpec`, `pull_to_refresh`, `OverlayPresentation`, `TabItemSpec.title: String`) -> `ui-atoms` (16 stateless Compose atoms incl. `UiStepper` haptic hooks, `UiCelebrationOverlay`, `UiHapticFeedback.kt`, `MotionModifier.kt` incl. `Modifier.shakeOnError` honoring `ThemeTokens.motion.reducedMotion`, `compose.uiTest`; zero `core`, `action-engine`, `network`, or `sdui-layout` imports)
  - `Layer 2`: `action-engine` (`core` + `sdui-contracts`, **18 `ActionTypes`** adding `open_url`, `share`, `request_permission`, `biometric_auth` + `ActionOutcome.{OpenUrlRequested, ShareRequested, PermissionRequested, BiometricAuthRequested}` alongside `trigger_feedback` / `FeedbackTriggered`), `network` (`core` + Ktor 3.6.1 HTTP/2 ALPN, Brotli `br`, bearer refresh, `CertificatePinMatcher` JVM/OkHttp + Apple Darwin `NSURLSession` pinning across iOS, macOS, and watchOS)
  - `Layer 3`: `sdui-layout` (**17 snippet renderers** via `BuiltInSnippetModule` + responsive `ContainerSnippetRenderers.kt` + `ProductCardSnippetRenderer.kt` + `SwipeDeckSnippetRenderer.kt` + `SearchAndChipSnippetRenderers.kt`, `PlatformBridgeBundle`, `PlatformBridgeOutcomeExecutor`, `AdaptiveScreenContainers.kt` (`AdaptiveReadyViewport`, `SplitPaneBody`, `OfflineStatusBanner`), `LayoutController` (`observeConnectivity`, `handleDeepLink`, `isOffline`, `errorShakeSequence`, `errorShakeTargetId`, `dismissFeedback()`, `collectAllEnvelopes`), `FloatingCartDockHost` + `PageCartCalculator`, `UiCelebrationOverlay`, `ModalBottomSheet` & `AlertDialog`, `PullToRefreshBox`, `SkeletonScreenPlaceholder`, `loadMore()`, `CachedScreenRepository`, `OfflineActionCoordinator`, `KtorImageRenderer`, `createSduiKoinModule` (`platformBridges`, `certificatePins`), `SduiHostRoot.kt` (`SduiHostedRoot`), iOS `createSduiViewController` + `AbetaSduiKit.xcframework`, macOS Cocoa `launchMacOsSduiWindow` (`SduiMacOsWindow.kt`), `SduiScreenComposeTest`), `watch-companion` (headless wearable companion: `WatchGlanceModels.kt`, `WatchScreenMapper`, `RemoteWatchPageSource`, `WatchRemoteApiCallExecutor`, `WatchCompanionController`, `createWatchCompanionModule`, `createAppleWatchCompanionHost`, `AbetaWatchKit.xcframework`), `storybook` (**13 stories across 4 categories** including `"All 17 Snippets"`, `ResponsiveAndBridgesStory` / `StoryCatalog.STORY_RESPONSIVE_BRIDGES`, and `DiscoveryAndSearchStory` / `StoryCatalog.STORY_DISCOVERY_SEARCH`, responsive `CompactStoryStrip`/`StorySidebar` shell, 6-app Super-App slugs & `"watch-status"` in `LiveBffStory`, macOS `launchStorybookMacOsWindow`, Desktop + WasmJS + `AbetaStorybookKit.xcframework`, `StorybookComposeTest`), `sample-app` (`CachedScreenRepository`, `OfflineActionCoordinator`, `KtorImageRenderer`, `PlatformBridgeBundle` + `observeConnectivity`, 6-in-1 Super-App Showcase Launcher (`Abeta Fresh`, `Abeta Bites`, `Abeta Pulse`, `Abeta Mart`, `Abeta Spark`, `Abeta Trade`) + `SampleWatchScreen` (`"watch-status"`), responsive `columnsByWidth` & `adaptiveLayout`, `OfflineApiCallExecutor`, `--headless`, `--bff-url`)
  - `Go BFF`: `cmd/bff` (`http.Server.Protocols` HTTP/1.1 + `h2c` + TLS HTTP/2), `internal/{domain,usecase,handler,infra}` (`CompressionMiddleware`, `ETagMiddleware`, `/openapi.json`, `/docs` Scalar UI, `POST /api/v1/orders/express`, `action_executor.go` covering **18 Action Types**, optional rate limit & JWT auth, core screens + 6-in-1 Super-App Showcase screens `fresh-home`, `bites-home`, `pulse-home`, `mart-home`, `spark-deck`, `trade-home`), `pkg/sdui/{actions,atoms,snippets,theming}` (**17 Snippet Types** incl. `swipe_deck`, `search_bar`, `chip_group`, `ResponsiveColumnsSpec`, `ResponsiveIntSpec`, `AdaptivePageSpec`, `ScreenLayoutMode`), `testdata/golden/`
- **Formatting, ABI & Static Checks**:
  - KMP enforces **Spotless (`ktlint`)**, **Detekt**, and **Binary Compatibility Validator (`apiCheck`)** across all **10 publishable library modules** (`./gradlew spotlessCheck detekt apiCheck`).
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
