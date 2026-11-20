---
name: abeta-kmp-kit
description: Umbrella router and workflow guide for the abeta-dev/kmp monorepo (Kotlin Multiplatform SDUI Kit, watch-companion, Go 1.26.0 SDUI BFF, Gradle BOM, and verification gates). Use when navigating the repository, building cross-stack features, configuring dev.abeta.kmp:bom, or verifying changes.
---

# Abeta KMP Kit (`abeta-dev/kmp`)

Use this skill as the primary entry point for tasks spanning `kmp/` (**10 publishable library modules** plus `kmp-bom` and `dependencyManagement`) and `bff/` (Go 1.26.0 SDUI BFF).

## Target & Host Glue Map

- **Headless / Contract Modules** (`core`, `state`, `analytics`, `sdui-contracts`, `action-engine`, `network`, `watch-companion`): `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`, `wasmJs`.
- **Compose UI Modules** (`design-tokens`, `ui-atoms`, `sdui-layout`) & `storybook`: `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `wasmJs`.
- **`sample-app`**: JVM Desktop & `--headless` CLI.
- **Drop-in Host Glue (`kmp/platforms/`)**: `android/AndroidPlatformBridgeFactory.kt`, `wearos/WearCompanionHostActivity.kt`, `apple/AbetaAppleHosts.swift`, `README.md` (Android & Wear OS templates require an Android app module with `ANDROID_HOME` / Android SDK configured).

## Skill Routing (10 Skills + 16 Per-Module Code Owners)

| Domain | Load Skill | Target Directories |
| :--- | :--- | :--- |
| Repo usage, Gradle BOM & OCP extensions | `skills/abeta-kmp-usage/SKILL.md` | Consumer apps, `kmp/kmp-bom`, `kmp/dependencyManagement` |
| Demo & reference modules (7 screens, 12 stories) | `skills/abeta-kmp-demo/SKILL.md` | `kmp/sample-app`, `kmp/storybook`, `bff/internal/infra` |
| Multi-OS platforms (Android, Wear, Apple, Desktop, Web) | `skills/abeta-kmp-platforms/SKILL.md` | `kmp/core`, `kmp/network`, `kmp/watch-companion`, `kmp/platforms` |
| Design tokens, responsive/wire DTOs, stateless Compose atoms | `skills/abeta-kmp-atomic/SKILL.md` | `kmp/design-tokens`, `kmp/sdui-contracts`, `kmp/ui-atoms` |
| SDUI action decoders, handlers, OS outcomes, `ActionRegistry` | `skills/abeta-kmp-actions/SKILL.md` | `kmp/action-engine`, `bff/pkg/sdui/actions` |
| Go BFF screens, snippets, `go-libs` handlers | `skills/abeta-kmp-bff/SKILL.md` | `bff/cmd/bff`, `bff/internal`, `bff/pkg/sdui` |
| Dependency Rule, SOLID, Gradle BOM & PR reviews | `skills/abeta-kmp-architecture/SKILL.md` | `kmp/kmp-bom`, `kmp/dependencyManagement`, `kmp/*/build.gradle.kts` |
| Per-module Code Owner PR reviews (16 owners) | `skills/abeta-kmp-module-review/SKILL.md` | `agents/owners/*`, `.github/CODEOWNERS` |
| CI/CD workflows, security & SemVer releases | `skills/abeta-kmp-cicd/SKILL.md` | `.github/workflows/*`, `scripts/*` |

## Monorepo Map

- **Layer 0**: `kmp/core` (primitives + OS bridges/ports: `PlatformCapabilities`/`currentPlatformCapabilities()`, `PlatformKind.{WATCHOS, WEAR_OS, LINUX_NATIVE, WINDOWS_NATIVE}`, `HapticEngine`, `ClipboardPort`, `ExternalLauncherPort`, `SharePort`, `ConnectivityMonitor`, `PermissionPort`, `LocationPort`, `FilePickerPort`, `PushNotificationPort`, `LocaleFormatter`, `DeepLinkParser`, `SecureKeyValueStore`, `BiometricAuthenticator`), `kmp/state` (`SessionStore`, `PreferenceStore`, `createSecurePersistentKeyValueStore(namespace)` in `SettingsKeyValueStore.kt`, `FeatureFlagProvider`, `OfflineMutationQueue`), `kmp/analytics`
- **Layer 1**: `kmp/design-tokens` (all tokens + `ResponsiveTokens.kt` via `ThemeTokens.adaptiveMetrics`), `kmp/sdui-contracts` (**15 wire atoms**, **14 `SnippetTypes`**, `AdaptiveContracts.kt` (`ResponsiveColumnsSpec`, `ResponsiveIntSpec`, `AdaptivePageSpec`), `OsPermissionKind`, `ScreenLayoutMode`, `PageResponse.adaptiveLayout`, `CarouselSnippetData.itemWidthByWidth`, `GridSnippetData.columnsByWidth`, `MotionContracts.kt`, `FloatingCartSpec`, `PaginationSpec`, `OverlayPresentation`), `kmp/ui-atoms` (**16 stateless Compose atoms** incl. `UiStepper` haptic hooks, `UiCelebrationOverlay`, `UiHapticFeedback.kt`, `MotionModifier.kt` incl. `Modifier.shakeOnError` honoring `reducedMotion`, `compose.uiTest`)
- **Layer 2**: `kmp/action-engine` (**18 `ActionTypes`** adding `open_url`, `share`, `request_permission`, `biometric_auth` + `ActionOutcome.{OpenUrlRequested, ShareRequested, PermissionRequested, BiometricAuthRequested}` alongside `trigger_feedback` / `FeedbackTriggered`), `kmp/network` (`HttpClientFactory`, `KtorHttpTransport`: HTTP/2 ALPN, Brotli `br`, bearer refresh, `CertificatePinMatcher` for JVM/OkHttp & Apple Darwin `NSURLSession` across iOS, macOS, and watchOS)
- **Layer 3**: `kmp/sdui-layout` (`LayoutController` (`observeConnectivity`, `handleDeepLink`, `isOffline`, `errorShakeSequence`, `errorShakeTargetId`, `dismissFeedback()`, `collectAllEnvelopes`), `PlatformBridgeBundle`, `PlatformBridgeOutcomeExecutor`, `AdaptiveScreenContainers.kt`, **14 snippet renderers**, `FloatingCartDockHost`, `UiCelebrationOverlay`, `CachedScreenRepository`, `OfflineActionCoordinator`, `KtorImageRenderer`, `createSduiKoinModule` (`platformBridges`, `certificatePins`), `SduiHostRoot.kt` (`SduiHostedRoot`), iOS `createSduiViewController` + `AbetaSduiKit.xcframework`, macOS `launchMacOsSduiWindow`), `kmp/watch-companion` (headless wearable controller `WatchCompanionController`, `WatchGlanceModels.kt`, `WatchScreenMapper`, `RemoteWatchPageSource`, `WatchRemoteApiCallExecutor`, `createWatchCompanionModule`, `createAppleWatchCompanionHost`, `AbetaWatchKit.xcframework`), `kmp/storybook` (**12 stories across 4 categories** adding `ResponsiveAndBridgesStory`, `CompactStoryStrip`/`StorySidebar`, `"watch-status"` in `LiveBffStory`, `launchStorybookMacOsWindow`, `AbetaStorybookKit.xcframework`), `kmp/sample-app` (**7 screens** in `SampleContainer.DEFAULT_SCREENS`, `SampleWatchScreen`, responsive `SampleStoreScreen`/`SampleStoreCatalog`, `PlatformBridgeBundle` + `observeConnectivity`, `--headless` & `--bff-url`)
- **BOM & Catalog**: `kmp/kmp-bom` (`dev.abeta.kmp:bom:0.1.0`), `kmp/dependencyManagement` (`dev.abeta.kmp:dependencies:0.1.0`)
- **Go BFF**: `bff/cmd/bff` (HTTP/1.1 + `h2c` + TLS HTTP/2), `bff/internal/{domain,usecase,handler,infra}` (`CompressionMiddleware`, `ETagMiddleware`, `/openapi.json`, `/docs`, `POST /api/v1/orders/express`, `action_executor.go` covering **18 actions**, optional rate limit & JWT auth, **7 screens** incl. `store-home` & `watch-status`), `bff/pkg/sdui/{actions,atoms,snippets,theming}`, `bff/testdata/golden/` (**5 fixtures**)

## Non-Negotiable Rules

1. **Brand & Path Hygiene**: Use `abeta-dev` / `dev.abeta.kmp` exclusively; never commit machine-specific absolute paths.
2. **Dependency Rule**: Dependencies point strictly inward (`Layer 3 -> Layer 2 -> Layer 1 -> Layer 0`; `handler -> usecase -> domain <- infra`).
3. **1:1 Wire Parity**: Keep `kmp/sdui-contracts` and `bff/pkg/sdui/*` synchronized via `v1` `snake_case` JSON and `bff/testdata/golden/{home_screen,form_screen,action_batch,containers_screen,store_home}.json`.
4. **BOM, Catalog & ABI Discipline**: Declare external versions in `kmp/dependencyManagement/gradle/libs.versions.toml`, internal versions in `internal.versions.toml`, and update `.api` baselines with `./gradlew apiDump` across all **10 publishable library modules**.
5. **Local Substitution**: `abeta.kmpLocalBuild=true` in `kmp/gradle.properties` substitutes `dev.abeta.kmp:*` coordinates with local `:module` projects; test external resolution with `-Pabeta.kmpLocalBuild=false`.

## Verification Commands

```bash
./scripts/check_brand_hygiene.sh
(cd kmp && ./gradlew check)
(cd kmp && ./gradlew :sample-app:run --args="--headless")
(cd kmp && ./gradlew :storybook:run) # or :storybook:wasmJsBrowserDevelopmentRun
(cd kmp && ./gradlew :sdui-layout:assembleAbetaSduiKitXCFramework :watch-companion:assembleAbetaWatchKitXCFramework :storybook:assembleAbetaStorybookKitXCFramework)
(cd kmp && ./gradlew :core:dokkaGenerateHtml) # or dokkaGenerateHtml
(cd bff && go vet ./... && go test -race -cover ./...)
./scripts/verify_all.sh
```
