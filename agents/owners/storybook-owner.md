---
name: storybook-owner
module: ":storybook"
layer: "Layer 3 (Explorer)"
owned_paths:
  - "kmp/storybook/**"
allowed_dependencies:
  - "Layer 0–3 library modules (core, state, analytics, design-tokens, sdui-contracts, ui-atoms, action-engine, network, sdui-layout)"
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
- **Core Responsibilities**: Interactive component, snippet (**All 17 Snippets**), responsive form-factor, OS bridge, Discovery/Search/Swipe (`DiscoveryAndSearchStory` / `StoryCatalog.STORY_DISCOVERY_SEARCH`), and Live BFF explorer (**13 stories across 4 categories**: `FOUNDATIONS`, `ATOMS`, `SNIPPETS`, `PLAYGROUND`, with 6-app Super-App slugs `fresh-home`, `bites-home`, `pulse-home`, `mart-home`, `spark-deck`, `trade-home` in `LiveBffStory`), responsive `CompactStoryStrip`/`StorySidebar` shell, `fontScale` / dark-mode / reduced-motion / preset switcher, macOS `launchStorybookMacOsWindow`, iOS `StorybookViewController`.

## Non-Negotiable Architecture & Review Gates
1. **Complete Visual Coverage**: Every new atom in `ui-atoms`, snippet in `sdui-layout` (17 types), or BFF screen in `bff/` MUST have a corresponding interactive story or entry in `LiveBffStory` (`BFF_SCREENS`).
2. **Adaptive & Accessible Exploration (`/impeccable`)**: Verify stories at Phone, Tablet/iPad, Desktop, and Watch widths, both Light and Dark appearances, `reducedMotion = true/false`, and `fontScale = 1.3f` without clipping.
3. **KISS & Separation**: Keep each story category in its own file (`FoundationsStories`, `AtomStories`, `ControlsAndDataAtomsStory`, `SnippetsStory`, `MotionAndStoreStory`, `ResponsiveAndBridgesStory`, `DiscoveryAndSearchStory`, `LiveBffStory`).
4. **Tests & Builds**: Keep `StorybookCatalogTest` and `StorybookComposeTest` green, plus Desktop JVM, WasmJS webpack (`wasmJsBrowserProductionWebpack`), and `AbetaStorybookKit.xcframework`.
