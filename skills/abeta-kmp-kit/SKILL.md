---
name: abeta-kmp-kit
description: Umbrella router and workflow guide for the abeta-dev/kmp monorepo (Kotlin Multiplatform SDUI Kit, Room 3 database, watch-companion, Go 1.26.0 SDUI BFF, Gradle BOM, and verification gates). Use when navigating the repository, building cross-stack features, configuring io.github.abeta-dev:bom, or verifying changes.
---

# Abeta KMP Kit (`abeta-dev/kmp`)

Use this skill as the primary entry point for tasks spanning `kmp/` (**11 publishable library modules** including Room 3 `database` schema v2, plus `kmp-bom` and `dependencyManagement` = **13 Maven Central artifacts** at **`0.7.0`**) and `bff/` (Go 1.26.0 SDUI BFF, **`bff@v0.9.0`**).

## Target & Host Glue Map

- **Headless / Contract Modules** (`core`, `state`, `analytics`, `database`, `sdui-contracts`, `action-engine`, `network`, `watch-companion`): `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`, `wasmJs`.
- **Compose UI Modules** (`design-tokens`, `ui-atoms`, `sdui-layout`) & `storybook`: `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `wasmJs`.
- **`sample-app`**: JVM Desktop & `--headless` / `--verticals` CLI.
- **Drop-in Host Glue (`kmp/platforms/`)**: `android/AndroidPlatformBridgeFactory.kt`, `android/AndroidDatabaseFactory.kt`, `wearos/WearCompanionHostActivity.kt`, `apple/AbetaAppleHosts.swift`, `apple/Package.template.swift`, `web/README.md`, `README.md` (Android & Wear OS templates require an Android app module with `ANDROID_HOME` / Android SDK configured).

## Skill Routing (10 Skills + 17 Per-Module Code Owners)

| Domain | Load Skill | Target Directories |
| :--- | :--- | :--- |
| Repo usage, Gradle BOM & OCP extensions | `skills/abeta-kmp-usage/SKILL.md` | Consumer apps, `kmp/kmp-bom`, `kmp/dependencyManagement` |
| Demo & reference modules (7-in-1 Showcase, 20 stories) | `skills/abeta-kmp-demo/SKILL.md` | `kmp/sample-app`, `kmp/storybook`, `bff/internal/infra` |
| Multi-OS platforms & Room 3 local database | `skills/abeta-kmp-platforms/SKILL.md` | `kmp/core`, `kmp/database`, `kmp/network`, `kmp/watch-companion`, `kmp/platforms` |
| Design tokens, responsive/wire DTOs, stateless Compose atoms | `skills/abeta-kmp-atomic/SKILL.md` | `kmp/design-tokens`, `kmp/sdui-contracts`, `kmp/ui-atoms` |
| SDUI action decoders, handlers, OS outcomes, `ActionRegistry` | `skills/abeta-kmp-actions/SKILL.md` | `kmp/action-engine`, `bff/pkg/sdui/actions` |
| Go BFF screens, snippets, `go-libs` handlers | `skills/abeta-kmp-bff/SKILL.md` | `bff/cmd/bff`, `bff/internal`, `bff/pkg/sdui` |
| Dependency Rule, SOLID, Gradle BOM & PR reviews | `skills/abeta-kmp-architecture/SKILL.md` | `kmp/kmp-bom`, `kmp/dependencyManagement`, `kmp/*/build.gradle.kts` |
| Per-module Code Owner PR reviews (17 owners) | `skills/abeta-kmp-module-review/SKILL.md` | `agents/owners/*`, `.github/CODEOWNERS` |
| CI/CD workflows, security & SemVer releases | `skills/abeta-kmp-cicd/SKILL.md` | `.github/workflows/*`, `scripts/*` |

## Monorepo Map

- **Layer 0**: `kmp/core` (primitives + OS/Voice/Desktop bridges/ports), `kmp/state` (`SessionStore`, `DataStoreKeyValueStore`, `createDiskKeyValueStore`, `PreferenceStore`, `createSecurePersistentKeyValueStore(namespace)`, `FeatureFlagProvider`, `OfflineMutationQueue`), `kmp/analytics`, `kmp/database` (Room 3 `3.0.3` schema v2 w/ `MIGRATION_1_2`, `AbetaDatabase`, `AbetaLocalDatabase`, `cached_screens`, `offline_mutations`, `key_value_records`, `sqlite-bundled:2.7.1`, `sqlite-web:2.7.1`, KSP `2.3.14`)
- **Layer 1**: `kmp/design-tokens` (all tokens + `ResponsiveTokens.kt` via `ThemeTokens.adaptiveMetrics`), `kmp/sdui-contracts` (**29 wire atoms** incl. `MediaPagerAtom`, `CountdownAtom` w/ `expires_at_epoch_ms`, & 12 `ExtendedAtoms.kt` primitives, **23 `SnippetTypes`** incl. `swipe_deck`, `search_bar`, `chip_group`, `nudge_bar`, `marquee_strip`, `order_tracker`, `composed_block` w/ **36 `ComposedNodeKind`s**, `custom_slot`, & `graph_canvas`, **6 `SnippetDeltaOp`s**, `ScreenManifestResponse`, `AdaptiveContracts.kt`, `MotionContracts.kt` w/ **7 Ambient `MotionLoopEffect`s**, `FloatingCartSpec` threshold nudges, `PaginationSpec`, `OverlayPresentation`), `kmp/ui-atoms` (**31 stateless Compose atoms** incl. `UiMediaPager`, ISO/IEC 18004 `UiQrCode`, `UiStepper`, `UiCountdown`, `UiRollingCounter`, & 12 extended atoms, `UiCelebrationOverlay`, `UiHapticFeedback.kt`, `MotionModifier.kt` + `MotionLoopModifiers.kt`, `compose.uiTest`)
- **Layer 2**: `kmp/action-engine` (**18 `ActionTypes`**, bounded `DispatchBudget`), `kmp/network` (`HttpClientFactory`, `KtorHttpTransport`: HTTP/2 ALPN, Brotli `br`, `304`, `KtorSseTransport`, `CsrfTokenProvider` + `403` retry, `NetworkConfig.appVersion`, bearer refresh, validated `sha256/` `CertificatePinMatcher` for JVM/OkHttp & Apple Darwin `NSURLSession`)
- **Layer 3**: `kmp/sdui-layout` (`LayoutController` + SSE delta patching, `SduiLayoutViewModel`, `PlatformBridgeBundle`, `PlatformBridgeOutcomeExecutor`, `AdaptiveScreenContainers.kt`, **23 snippet renderers** incl. `ComposedBlock` w/ **36 `ComposedNodeKind`s**, `CustomSlot`, `GraphCanvas`, `FloatingCartDockHost`, `UiCelebrationOverlay`, `CachedScreenRepository` (`VERSION_GATED`, `304`, `/manifest`), `RemoteBffAnalyticsTracker`, `OfflineActionCoordinator`, `KtorImageRenderer`, `createSduiKoinModule`, `SduiHostedRoot`, `AbetaSduiKit.xcframework`, macOS `launchMacOsSduiWindow`), `kmp/watch-companion` (headless wearable controller `WatchCompanionController`, `AbetaWatchKit.xcframework`), `kmp/storybook` (**20 stories across 5 categories** incl. 8 `UI ATOMS` stories covering all **31** atoms, `"All 23 Snippets"`, Voice & Desktop OS Bridges, Live 7-in-1 Super-App Demos, Animation & Nudges Lab, Atomic Snippet Crafter, End-to-End App Journeys, `AbetaStorybookKit.xcframework`), `kmp/sample-app` (**7-in-1 Super-App Showcase Launcher** + core screens, `--headless`, `--verticals` & `--bff-url`)
- **BOM & Catalog**: `kmp/kmp-bom` (`io.github.abeta-dev:bom:0.7.0`), `kmp/dependencyManagement` (`io.github.abeta-dev:dependencies:0.7.0`) — Kotlin package namespace remains `dev.abeta.kmp.*`
- **Go BFF (`bff@v0.9.0`)**: `bff/cmd/bff` (HTTP/1.1 + `h2c` + TLS 1.2+ HTTP/2), `bff/internal/{domain,usecase,handler,infra}` (**12 built-in screens**: `home`, `catalog`, `containers`, `profile`, `form-demo`, `fresh-home`, `store-home`, `watch-status`, `pay-home`, `pay-transfer`, `pay-cards`, `pay-receipt`; live routes incl. `/manifest`, `/screens/manifest`, `/streams/:streamId`, `/pay/transfer`, `/analytics/*`), **8 exportable packages** under `bff/pkg/sdui/{actions,analytics,atoms,cache,molecules,server,snippets,theming}`, `bff/testdata/golden/` (**11 fixtures**)

## Non-Negotiable Rules

1. **Brand & Path Hygiene**: Use `abeta-dev` / `io.github.abeta-dev` (Maven) / `dev.abeta.kmp` (Kotlin packages) exclusively; never commit machine-specific absolute paths.
2. **Dependency Rule**: Dependencies point strictly inward (`Layer 3 -> Layer 2 -> Layer 1 -> Layer 0`; `handler -> usecase -> domain <- infra`).
3. **1:1 Wire Parity**: Keep `kmp/sdui-contracts` and `bff/pkg/sdui/*` synchronized via `v1` `snake_case` JSON and all **11 golden fixtures** (`bff/testdata/golden/{home_screen,form_screen,action_batch,containers_screen,store_home,interactive_screen,nudges_and_composed_screen,extended_atoms_screen,express_molecules_and_analytics_screen,streaming_and_versioned_screen,spatial_custom_and_graph_screen}.json`).
4. **BOM, Catalog & ABI Discipline**: Declare external versions in `kmp/dependencyManagement/gradle/libs.versions.toml`, internal versions in `internal.versions.toml`, and update `.api` baselines with `./gradlew apiDump` across all **11 publishable library modules**.
5. **Local Substitution**: `abeta.kmpLocalBuild=true` in `kmp/gradle.properties` substitutes `io.github.abeta-dev:*` coordinates with local `:module` projects; test external resolution with `-Pabeta.kmpLocalBuild=false`.

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
