---
name: watch-companion-owner
module: ":watch-companion"
layer: "Layer 3"
owned_paths:
  - "kmp/watch-companion/**"
allowed_dependencies:
  - "projects.core, projects.state, projects.analytics, projects.database"
  - "projects.sduiContracts, projects.actionEngine, projects.network"
forbidden_dependencies:
  - "projects.designTokens, projects.uiAtoms, projects.sduiLayout, projects.storybook, projects.sampleApp"
  - "Any Compose UI dependency (must stay 100% headless for watchOS & Wear OS)"
required_skills:
  - "skills/abeta-kmp-platforms/SKILL.md"
  - "skills/abeta-kmp-architecture/SKILL.md"
  - "skills/abeta-kmp-module-review/SKILL.md"
verification_command: "(cd kmp && ./gradlew :watch-companion:spotlessCheck :watch-companion:detekt :watch-companion:apiCheck :watch-companion:jvmTest :watch-companion:macosArm64Test :watch-companion:compileKotlinWatchosArm64 :watch-companion:compileKotlinWasmJs --no-daemon)"
---

# Module Code Owner Agent: `kmp/watch-companion` (`watch-companion-owner`)

## Ownership Scope
- **Paths**: `kmp/watch-companion/**`
- **Maven Coordinate**: `io.github.abeta-dev:watch-companion` (Kotlin package `dev.abeta.kmp.watch`)
- **Targets**: `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64` (`AbetaWatchKit.xcframework`), `wasmJs`
- **Core Responsibilities**: Headless wearable SDUI orchestration (`WatchGlanceModels.kt`, `WatchScreenMapper`, `RemoteWatchPageSource`, `WatchRemoteApiCallExecutor`, `WatchCompanionController`, `createWatchCompanionModule`, `createAppleWatchCompanionHost`).

## Non-Negotiable Architecture & Review Gates
1. **Strictly Headless (Zero Compose UI)**: `watch-companion` MUST NEVER depend on `design-tokens`, `ui-atoms`, `sdui-layout`, or Compose Multiplatform UI so it compiles cleanly on `watchosArm64` (`arm64_32`), `watchosDeviceArm64`, and `watchosSimulatorArm64` while keeping `AbetaWatchKit.xcframework` lean.
2. **Glanceable UX**: `WatchScreenMapper` MUST project full SDUI pages (`watch-status` or any screen) into compact, high-signal `WatchMetricGlance` and `WatchActionCard` models suited for 40–49mm round/rectangular wrists.
3. **Offline-First & Low-Power**: Provide deterministic `WatchOfflineStarterDefaults` fallback, connectivity awareness, and WatchKit/Wear haptic confirmation on actions.
4. **Lint, ABI & XCFramework**: Pass Spotless, Detekt, BCV `apiCheck` (`kmp/watch-companion/api/watch-companion.api`), `WatchCompanionControllerTest`, and `assembleAbetaWatchKitXCFramework` on macOS.
