# Abeta KMP Kit — CodeGraph Symbol & Dependency Index

> **Auto-generated deterministically** by `./scripts/generate_codegraph.py` (schema `1.0`).
> Do not hand-edit. Refresh with `./scripts/generate_codegraph.py` and verify in CI with `./scripts/generate_codegraph.py --check`.

## Quick Symbol Query (Step 5 Grounding)

Query any Kotlin, Go, or Swift public symbol in milliseconds without grepping the repository:

```bash
./scripts/generate_codegraph.py --query CachedScreenRepository
./scripts/generate_codegraph.py --query LayoutController
./scripts/generate_codegraph.py --query UpstreamCachedScreenRepository
./scripts/generate_codegraph.py --query AbetaWatchViewModel
```

## Index Summary

| Dimension | Count |
| :--- | ---: |
| KMP Modules (`kmp/*/build.gradle.kts`) | 15 |
| Go BFF Packages (`bff/{cmd,internal,pkg}`) | 13 |
| SwiftPM Targets (`kmp/platforms/apple/Sources/*`) | 2 |
| Kotlin Public Symbols (382 prod files) | 722 |
| Go Exported Symbols (52 prod files) | 637 |
| Swift Public Symbols (2 prod files) | 21 |
| **Total Indexed Symbols** | **1380** |

## KMP Module Dependency Graph

| Layer | Module | Targets | Internal Dependencies | Public Symbols |
| :--- | :--- | :--- | :--- | ---: |
| Layer 0 | `analytics` | `androidLibrary`, `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`, `wasmJs` | `core` | 14 |
| Layer 0 | `core` | `androidLibrary`, `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`, `wasmJs` | — | 217 |
| Layer 0 | `database` | `androidLibrary`, `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`, `wasmJs` | `core` | 25 |
| Layer 0 | `state` | `androidLibrary`, `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`, `wasmJs` | `core` | 58 |
| Layer 1 | `design-tokens` | `androidLibrary`, `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `wasmJs` | — | 36 |
| Layer 1 | `sdui-contracts` | `androidLibrary`, `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`, `wasmJs` | — | 106 |
| Layer 1 | `ui-atoms` | `androidLibrary`, `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `wasmJs` | `design-tokens`, `sdui-contracts` | 88 |
| Layer 2 | `action-engine` | `androidLibrary`, `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`, `wasmJs` | `core`, `sdui-contracts` | 36 |
| Layer 2 | `network` | `androidLibrary`, `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`, `wasmJs` | `core` | 32 |
| Layer 3 | `sample-app` | `jvm` | `action-engine`, `analytics`, `core`, `design-tokens`, `kmp-bom`, `network`, `sdui-contracts`, `sdui-layout`, `state`, `ui-atoms` | 9 |
| Layer 3 | `sdui-layout` | `androidLibrary`, `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `wasmJs` | `action-engine`, `analytics`, `core`, `design-tokens`, `network`, `sdui-contracts`, `state`, `ui-atoms` | 38 |
| Layer 3 | `storybook` | `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `wasmJs` | `action-engine`, `analytics`, `core`, `design-tokens`, `network`, `sdui-contracts`, `sdui-layout`, `ui-atoms` | 28 |
| Layer 3 | `watch-companion` | `androidLibrary`, `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`, `wasmJs` | `action-engine`, `analytics`, `core`, `network`, `sdui-contracts`, `state` | 16 |
| BOM | `dependencyManagement` | — | — | 0 |
| BOM | `kmp-bom` | — | constrains: `action-engine`, `analytics`, `core`, `database`, `dependencyManagement`, `design-tokens`, `network`, `sdui-contracts`, `sdui-layout`, `state`, `ui-atoms`, `watch-companion` | 0 |

## Platform Reference Hosts & SwiftPM Targets

| Host / Target | Language | Source Root | Public Symbols |
| :--- | :--- | :--- | ---: |
| `platforms/android` | Kotlin | `kmp/platforms/android/` | 8 |
| `platforms/wearos` | Kotlin | `kmp/platforms/wearos/` | 11 |
| `AbetaSduiSwiftUI` | Swift | `kmp/platforms/apple/Sources/AbetaSduiSwiftUI/` | 5 |
| `AbetaWatchSwiftUI` | Swift | `kmp/platforms/apple/Sources/AbetaWatchSwiftUI/` | 16 |

## Go BFF Package Dependency Graph

| Layer | Package (`bff/...`) | Internal Imports | Files | Exported Symbols |
| :--- | :--- | :--- | ---: | ---: |
| `cmd` | `cmd/bff` | `internal/handler`, `internal/infra`, `internal/usecase` | 1 | 0 |
| `handler` | `internal/handler` | `internal/domain`, `internal/usecase`, `pkg/sdui/actions`, `pkg/sdui/analytics`, `pkg/sdui/atoms`, `pkg/sdui/server`, `pkg/sdui/snippets` | 6 | 4 |
| `usecase` | `internal/usecase` | `internal/domain`, `pkg/sdui/actions`, `pkg/sdui/analytics`, `pkg/sdui/snippets` | 3 | 18 |
| `domain` | `internal/domain` | `pkg/sdui/actions`, `pkg/sdui/analytics`, `pkg/sdui/snippets`, `pkg/sdui/theming` | 1 | 8 |
| `infra` | `internal/infra` | `internal/domain`, `pkg/sdui/actions`, `pkg/sdui/analytics`, `pkg/sdui/atoms`, `pkg/sdui/molecules`, `pkg/sdui/snippets`, `pkg/sdui/theming` | 13 | 15 |
| `pkg` | `pkg/sdui/actions` | — | 1 | 45 |
| `pkg` | `pkg/sdui/analytics` | — | 1 | 5 |
| `pkg` | `pkg/sdui/atoms` | `pkg/sdui/actions`, `pkg/sdui/theming` | 2 | 70 |
| `pkg` | `pkg/sdui/cache` | `pkg/sdui/snippets` | 1 | 24 |
| `pkg` | `pkg/sdui/molecules` | `pkg/sdui/actions`, `pkg/sdui/atoms`, `pkg/sdui/snippets`, `pkg/sdui/theming` | 2 | 16 |
| `pkg` | `pkg/sdui/server` | `pkg/sdui/snippets` | 5 | 23 |
| `pkg` | `pkg/sdui/snippets` | `pkg/sdui/actions`, `pkg/sdui/atoms`, `pkg/sdui/theming` | 15 | 391 |
| `pkg` | `pkg/sdui/theming` | — | 1 | 18 |

## Key Entry-Point Symbols by Layer

### Layer 0 — Core, State, Analytics & Database

| Module / Package | Kind | Symbol | Location |
| :--- | :--- | :--- | :--- |
| `core` | `sealed interface` | `AppResult` | `kmp/core/src/commonMain/kotlin/dev/abeta/kmp/core/result/AppResult.kt:11` |
| `core` | `sealed interface` | `AppError` | `kmp/core/src/commonMain/kotlin/dev/abeta/kmp/core/error/AppError.kt:23` |
| `core` | `data class` | `PlatformCapabilities` | `kmp/core/src/commonMain/kotlin/dev/abeta/kmp/core/platform/PlatformCapabilities.kt:6` |
| `core` | `interface` | `SecureKeyValueStore` | `kmp/core/src/commonMain/kotlin/dev/abeta/kmp/core/security/SecureStorage.kt:13` |
| `core` | `interface` | `BiometricAuthenticator` | `kmp/core/src/commonMain/kotlin/dev/abeta/kmp/core/security/BiometricAuth.kt:47` |
| `core` | `object` | `DeepLinkParser` | `kmp/core/src/commonMain/kotlin/dev/abeta/kmp/core/platform/DeepLink.kt:23` |
| `state` | `interface` | `SessionStore` | `kmp/state/src/commonMain/kotlin/dev/abeta/kmp/state/session/SessionStore.kt:21` |
| `state` | `class` | `OfflineMutationQueue` | `kmp/state/src/commonMain/kotlin/dev/abeta/kmp/state/sync/OfflineMutationQueue.kt:58` |
| `analytics` | `interface` | `AnalyticsTracker` | `kmp/analytics/src/commonMain/kotlin/dev/abeta/kmp/analytics/AnalyticsTracker.kt:6` |
| `database` | `abstract class` | `AbetaDatabase` | `kmp/database/src/commonMain/kotlin/dev/abeta/kmp/database/AbetaDatabase.kt:32` |
| `database` | `class` | `AbetaLocalDatabase` | `kmp/database/src/commonMain/kotlin/dev/abeta/kmp/database/AbetaLocalDatabase.kt:21` |

### Layer 1 — Design Tokens, Wire Contracts & Stateless UI Atoms

| Module / Package | Kind | Symbol | Location |
| :--- | :--- | :--- | :--- |
| `design-tokens` | `composable fun` | `DesignTheme` | `kmp/design-tokens/src/commonMain/kotlin/dev/abeta/kmp/tokens/DesignTheme.kt:144` |
| `design-tokens` | `object` | `ThemeTokens` | `kmp/design-tokens/src/commonMain/kotlin/dev/abeta/kmp/tokens/DesignTheme.kt:87` |
| `design-tokens` | `data class` | `AdaptiveLayoutMetrics` | `kmp/design-tokens/src/commonMain/kotlin/dev/abeta/kmp/tokens/ResponsiveTokens.kt:47` |
| `sdui-contracts` | `data class` | `PageResponse` | `kmp/sdui-contracts/src/commonMain/kotlin/dev/abeta/kmp/contracts/SnippetContracts.kt:119` |
| `sdui-contracts` | `data class` | `SnippetEnvelope` | `kmp/sdui-contracts/src/commonMain/kotlin/dev/abeta/kmp/contracts/SnippetContracts.kt:40` |
| `sdui-contracts` | `object` | `SnippetTypes` | `kmp/sdui-contracts/src/commonMain/kotlin/dev/abeta/kmp/contracts/SnippetContracts.kt:10` |
| `sdui-contracts` | `enum class` | `ComposedNodeKind` | `kmp/sdui-contracts/src/commonMain/kotlin/dev/abeta/kmp/contracts/NudgeAndComposedContracts.kt:108` |
| `sdui-contracts` | `enum class` | `SnippetDeltaOp` | `kmp/sdui-contracts/src/commonMain/kotlin/dev/abeta/kmp/contracts/StreamingAndManifestContracts.kt:51` |
| `sdui-contracts` | `data class` | `ActionRef` | `kmp/sdui-contracts/src/commonMain/kotlin/dev/abeta/kmp/contracts/ActionContracts.kt:25` |
| `sdui-contracts` | `data class` | `MotionSpec` | `kmp/sdui-contracts/src/commonMain/kotlin/dev/abeta/kmp/contracts/MotionContracts.kt:12` |
| `ui-atoms` | `composable fun` | `UiButton` | `kmp/ui-atoms/src/commonMain/kotlin/dev/abeta/kmp/atoms/atoms/UiButton.kt:65` |
| `ui-atoms` | `composable fun` | `UiQrCode` | `kmp/ui-atoms/src/commonMain/kotlin/dev/abeta/kmp/atoms/atoms/UiQrCode.kt:62` |
| `ui-atoms` | `composable fun` | `UiMediaPager` | `kmp/ui-atoms/src/commonMain/kotlin/dev/abeta/kmp/atoms/atoms/UiMediaPager.kt:69` |
| `ui-atoms` | `composable fun` | `UiCelebrationOverlay` | `kmp/ui-atoms/src/commonMain/kotlin/dev/abeta/kmp/atoms/atoms/UiCelebrationBurst.kt:95` |

### Layer 2 — Action Engine & Network Client

| Module / Package | Kind | Symbol | Location |
| :--- | :--- | :--- | :--- |
| `action-engine` | `object` | `ActionTypes` | `kmp/action-engine/src/commonMain/kotlin/dev/abeta/kmp/actions/model/ActionPayloads.kt:18` |
| `action-engine` | `class` | `ActionDecoderRegistry` | `kmp/action-engine/src/commonMain/kotlin/dev/abeta/kmp/actions/codec/ActionPayloadDecoder.kt:67` |
| `action-engine` | `class` | `ActionRegistry` | `kmp/action-engine/src/commonMain/kotlin/dev/abeta/kmp/actions/handler/ActionHandler.kt:178` |
| `action-engine` | `class` | `DefaultActionDispatcher` | `kmp/action-engine/src/commonMain/kotlin/dev/abeta/kmp/actions/handler/NavigationAndNetworkActionHandlers.kt:117` |
| `action-engine` | `sealed interface` | `ActionOutcome` | `kmp/action-engine/src/commonMain/kotlin/dev/abeta/kmp/actions/handler/ActionHandler.kt:43` |
| `network` | `data class` | `NetworkConfig` | `kmp/network/src/commonMain/kotlin/dev/abeta/kmp/network/config/NetworkConfig.kt:36` |
| `network` | `object` | `HttpClientFactory` | `kmp/network/src/commonMain/kotlin/dev/abeta/kmp/network/client/HttpClientFactory.kt:100` |
| `network` | `class` | `KtorHttpTransport` | `kmp/network/src/commonMain/kotlin/dev/abeta/kmp/network/client/KtorHttpTransport.kt:37` |
| `network` | `class` | `KtorSseTransport` | `kmp/network/src/commonMain/kotlin/dev/abeta/kmp/network/sse/KtorSseTransport.kt:55` |

### Layer 3 — SDUI Layout, Watch Companion, Storybook & Sample App

| Module / Package | Kind | Symbol | Location |
| :--- | :--- | :--- | :--- |
| `sdui-layout` | `class` | `LayoutController` | `kmp/sdui-layout/src/commonMain/kotlin/dev/abeta/kmp/layout/engine/LayoutController.kt:84` |
| `sdui-layout` | `class` | `SduiLayoutViewModel` | `kmp/sdui-layout/src/commonMain/kotlin/dev/abeta/kmp/layout/engine/SduiLayoutViewModel.kt:45` |
| `sdui-layout` | `class` | `CachedScreenRepository` | `kmp/sdui-layout/src/commonMain/kotlin/dev/abeta/kmp/layout/repository/CachedScreenRepository.kt:45` |
| `sdui-layout` | `object` | `BuiltInSnippetModule` | `kmp/sdui-layout/src/commonMain/kotlin/dev/abeta/kmp/layout/snippet/BuiltInSnippetModule.kt:10` |
| `sdui-layout` | `class` | `SnippetRegistry` | `kmp/sdui-layout/src/commonMain/kotlin/dev/abeta/kmp/layout/snippet/SnippetRegistry.kt:198` |
| `sdui-layout` | `class` | `CustomSlotRegistry` | `kmp/sdui-layout/src/commonMain/kotlin/dev/abeta/kmp/layout/snippet/CustomSlotRegistry.kt:62` |
| `sdui-layout` | `data class` | `PlatformBridgeBundle` | `kmp/sdui-layout/src/commonMain/kotlin/dev/abeta/kmp/layout/platform/PlatformBridgeBundle.kt:61` |
| `sdui-layout` | `composable fun` | `SduiHostedRoot` | `kmp/sdui-layout/src/commonMain/kotlin/dev/abeta/kmp/layout/ui/SduiHostRoot.kt:55` |
| `sdui-layout` | `fun` | `createSduiKoinModule` | `kmp/sdui-layout/src/commonMain/kotlin/dev/abeta/kmp/layout/di/SduiKoinModule.kt:86` |
| `watch-companion` | `class` | `WatchCompanionController` | `kmp/watch-companion/src/commonMain/kotlin/dev/abeta/kmp/watch/WatchCompanionController.kt:41` |
| `watch-companion` | `class` | `WatchScreenMapper` | `kmp/watch-companion/src/commonMain/kotlin/dev/abeta/kmp/watch/WatchScreenMapper.kt:40` |
| `storybook` | `composable fun` | `StorybookApp` | `kmp/storybook/src/commonMain/kotlin/dev/abeta/kmp/storybook/StorybookApp.kt:106` |

### Platform Reference Hosts & SwiftPM Targets

| Module / Package | Kind | Symbol | Location |
| :--- | :--- | :--- | :--- |
| `platforms/android` | `class` | `AbetaAndroidHostActivity` | `kmp/platforms/android/AbetaAndroidHostActivity.kt:61` |
| `platforms/android` | `object` | `AndroidPlatformBridgeFactory` | `kmp/platforms/android/AndroidPlatformBridgeFactory.kt:79` |
| `platforms/android` | `object` | `AndroidDatabaseFactory` | `kmp/platforms/android/AndroidDatabaseFactory.kt:30` |
| `platforms/wearos` | `class` | `WearCompanionHostActivity` | `kmp/platforms/wearos/WearCompanionHostActivity.kt:52` |
| `AbetaSduiSwiftUI` | `public struct` | `AbetaIosSduiScreenView` | `kmp/platforms/apple/Sources/AbetaSduiSwiftUI/AbetaSduiSwiftUI.swift:10` |
| `AbetaSduiSwiftUI` | `public struct` | `AbetaIosRootAppView` | `kmp/platforms/apple/Sources/AbetaSduiSwiftUI/AbetaSduiSwiftUI.swift:36` |
| `AbetaWatchSwiftUI` | `public final class` | `AbetaWatchViewModel` | `kmp/platforms/apple/Sources/AbetaWatchSwiftUI/AbetaWatchSwiftUI.swift:138` |
| `AbetaWatchSwiftUI` | `public struct` | `AbetaWatchSduiView` | `kmp/platforms/apple/Sources/AbetaWatchSwiftUI/AbetaWatchSwiftUI.swift:280` |

### Go BFF — Clean Architecture & Public `pkg/sdui/*` SDK

| Module / Package | Kind | Symbol | Location |
| :--- | :--- | :--- | :--- |
| `internal/handler` | `func` | `NewRouter` | `bff/internal/handler/router.go:55` |
| `internal/usecase` | `struct` | `ScreenService` | `bff/internal/usecase/services.go:35` |
| `internal/usecase` | `struct` | `ActionService` | `bff/internal/usecase/services.go:164` |
| `internal/domain` | `interface` | `ScreenRepository` | `bff/internal/domain/ports.go:45` |
| `internal/infra` | `struct` | `InMemoryScreenRepository` | `bff/internal/infra/screen_repo.go:17` |
| `internal/infra` | `struct` | `DefaultActionExecutor` | `bff/internal/infra/action_executor.go:23` |
| `pkg/sdui/cache` | `struct` | `UpstreamCachedScreenRepository` | `bff/pkg/sdui/cache/cache.go:146` |
| `pkg/sdui/server` | `func` | `CORSMiddleware` | `bff/pkg/sdui/server/cors.go:62` |
| `pkg/sdui/server` | `func` | `ETagMiddleware` | `bff/pkg/sdui/server/etag.go:47` |
| `pkg/sdui/server` | `func` | `CompressionMiddleware` | `bff/pkg/sdui/server/compression.go:160` |
| `pkg/sdui/server` | `func` | `JWTAuthMiddleware` | `bff/pkg/sdui/server/auth.go:18` |
| `pkg/sdui/snippets` | `struct` | `PageResponse` | `bff/pkg/sdui/snippets/snippets.go:144` |
| `pkg/sdui/actions` | `struct` | `ActionTrigger` | `bff/pkg/sdui/actions/actions.go:52` |
