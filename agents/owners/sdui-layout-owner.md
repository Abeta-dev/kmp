---
name: sdui-layout-owner
module: ":sdui-layout"
layer: "Layer 3"
owned_paths:
  - "kmp/sdui-layout/**"
allowed_dependencies:
  - "projects.core, projects.state, projects.analytics"
  - "projects.designTokens, projects.sduiContracts, projects.uiAtoms"
  - "projects.actionEngine, projects.network"
forbidden_dependencies:
  - "projects.watchCompanion, projects.storybook, projects.sampleApp"
required_skills:
  - "skills/abeta-kmp-kit/SKILL.md"
  - "skills/abeta-kmp-atomic/SKILL.md"
  - "skills/abeta-kmp-actions/SKILL.md"
  - "skills/abeta-kmp-module-review/SKILL.md"
verification_command: "(cd kmp && ./gradlew :sdui-layout:spotlessCheck :sdui-layout:detekt :sdui-layout:apiCheck :sdui-layout:jvmTest :sdui-layout:macosArm64Test :sdui-layout:compileKotlinIosSimulatorArm64 :sdui-layout:compileKotlinWasmJs --no-daemon)"
---

# Module Code Owner Agent: `kmp/sdui-layout` (`sdui-layout-owner`)

## Ownership Scope
- **Paths**: `kmp/sdui-layout/**`
- **Maven Coordinate**: `dev.abeta.kmp:sdui-layout`
- **Targets**: `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `wasmJs` (+ `AbetaSduiKit.xcframework`)
- **Core Responsibilities**: `LayoutController`, `LayoutStateHelpers`, `PlatformBridgeBundle`, `PlatformBridgeOutcomeExecutor`, `AdaptiveScreenContainers`, `SduiScreen`, `SduiHostRoot`, `FloatingCartDockHost` (with live free-delivery threshold nudge progress & unlock bounce) + `PageCartCalculator`, `OverlayHost`, **21 built-in snippet renderers** via `SnippetRegistry` / `BuiltInSnippetModule` (including `SwipeDeckSnippetRenderer`, `SearchBarSnippetRenderer`, `ChipGroupSnippetRenderer`, `NudgeBarSnippetRenderer`, `MarqueeStripSnippetRenderer`, `OrderTrackerSnippetRenderer`, and **`ComposedBlockSnippetRenderer`** Atomic Snippet Molding Engine up to depth 6), `CachedScreenRepository`, `OfflineActionCoordinator`, `KtorImageRenderer`, `createSduiKoinModule`, iOS `createSduiViewController`, macOS `launchMacOsSduiWindow`.

## Non-Negotiable Architecture & Review Gates
1. **OCP Snippet & Action Orchestration**: Register all snippets through `SnippetRegistry` and execute all 18 actions via `ActionDispatcher` + `PlatformBridgeOutcomeExecutor`. Keep snippet renderers bifurcated by domain (never one giant file).
2. **Native Safe Area, IME, Accessibility & Motion (`/impeccable`)**:
   - Honor `WindowInsets.safeDrawing`, `statusBarsPadding()`, `navigationBarsPadding()`, and `imePadding()`.
   - Annotate `OfflineStatusBanner` and `ToastBanner` with `LiveRegionMode.Polite` semantics.
   - Propagate OS dark mode and `prefersReducedMotion` in `SduiHostedRoot`.
3. **Offline & Connectivity Resilience**: `LayoutController.observeConnectivity()` must surface `isOffline` cleanly and auto-flush `OfflineActionCoordinator` on reconnect.
4. **SOLID, KISS & Tests**: Keep `LayoutController` focused on orchestration (delegate state seeding/validation/reduction helpers), maintain `SduiScreenComposeTest` + `LayoutControllerBridgesTest` + `SduiKoinModuleTest`.
5. **Lint & ABI**: Pass Spotless, Detekt, BCV `apiCheck` (`kmp/sdui-layout/api/sdui-layout.api`), and Apple/WasmJS compilation.
