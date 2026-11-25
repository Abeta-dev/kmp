---
name: storybook-owner
module: ":storybook"
layer: "Layer 3 (Explorer)"
owned_paths:
  - "kmp/storybook/**"
allowed_dependencies:
  - "Layer 0–3 library modules (core, state, analytics, database, design-tokens, sdui-contracts, ui-atoms, action-engine, network, sdui-layout)"
forbidden_dependencies:
  - "projects.sampleApp"
required_skills:
  - "skills/abeta-kmp-demo/SKILL.md"
  - "skills/abeta-kmp-atomic/SKILL.md"
  - "skills/abeta-kmp-module-review/SKILL.md"
verification_command: "(cd kmp && ./gradlew :storybook:spotlessCheck :storybook:detekt :storybook:jvmTest :storybook:macosArm64Test :storybook:compileKotlinIosSimulatorArm64 :storybook:compileKotlinWasmJs --no-daemon)"
---

# Module Code Owner Agent: `kmp/storybook` (`storybook-owner`)

## Ownership Scope
- **Paths**: `kmp/storybook/**`
- **Targets**: `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `wasmJs` (+ `AbetaStorybookKit.xcframework` & production WasmJS webpack)
- **Core Responsibilities**: **Live 7-in-1 Super-App Demos** (`super-app-demos` — all 7 multi-screen verticals incl. **Abeta Pay** + Watch playable offline in WasmJS/Desktop with deeper checkout/tracker/chat/quotes/biometric-pay/OTP/QR workflows & `?story=&app=` deep linking), complete **31-Atom Gallery** (8 interactive `UI ATOMS` stories covering all 31 stateless atoms incl. `UiMediaPager` & ISO/IEC 18004 `UiQrCode`), snippet (**All 23 Snippets**, incl. `custom_slot` & `graph_canvas`), responsive form-factor, **Voice & Desktop OS Bridges** (`ResponsiveAndBridgesStory`), Discovery/Search/Swipe (`DiscoveryAndSearchStory`), **Animation & Nudges Lab** (`animation-nudges`), **Atomic Snippet Crafter** (`snippet-crafter`), **End-to-End App Journeys** (`e2e-journeys`), and Live BFF explorer (**20 stories across 5 categories**: `LIVE_DEMOS`, `FOUNDATIONS`, `ATOMS`, `SNIPPETS`, `PLAYGROUND`), responsive `CompactStoryStrip`/`StorySidebar` shell, `fontScale` / dark-mode / reduced-motion / preset switcher, macOS `launchStorybookMacOsWindow`, iOS `StorybookViewController`.

## Non-Negotiable Architecture & Review Gates
1. **Complete Visual Coverage**: Every new atom in `ui-atoms` (**31** atoms), snippet in `sdui-layout` (**23** types), or BFF screen in `bff/` MUST have a corresponding interactive story or entry in `LiveBffStory` (`BFF_SCREENS`).
2. **Adaptive & Accessible Exploration (`/impeccable`)**: Verify stories at Phone, Tablet/iPad, Desktop, and Watch widths, both Light and Dark appearances, `reducedMotion = true/false`, and `fontScale = 1.3f` without clipping.
3. **KISS & Separation**: Keep each story category in its own file (`IntroductionStory`, `FoundationsStories`, `AtomStories`, `ControlsAndDataAtomsStory`, `ExtendedAtomSelectionStory`, `ExtendedAtomCommerceStory`, `ExtendedAtomTelemetryStory`, `SnippetsStory`, `MotionAndStoreStory`, `ResponsiveAndBridgesStory`, `DiscoveryAndSearchStory`, `AnimationNudgesStory`, `SnippetCrafterStory`, `EndToEndJourneysStory`, `LiveBffStory`, `SuperAppDemosStory`).
4. **Tests & Builds**: Keep `StorybookCatalogTest` and `StorybookComposeTest` green, plus Desktop JVM, WasmJS webpack (`wasmJsBrowserProductionWebpack`), and `AbetaStorybookKit.xcframework`.
