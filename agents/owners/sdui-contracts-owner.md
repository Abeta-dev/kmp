---
name: sdui-contracts-owner
module: ":sdui-contracts"
layer: "Layer 1"
owned_paths:
  - "kmp/sdui-contracts/**"
allowed_dependencies:
  - "kotlinx-serialization-json (via libs.versions.toml)"
forbidden_dependencies:
  - "Any internal dev.abeta.kmp module (including core, design-tokens, ui-atoms, action-engine, network, sdui-layout)"
  - "Compose UI, Ktor, or executable side-effect logic"
required_skills:
  - "skills/abeta-kmp-atomic/SKILL.md"
  - "skills/abeta-kmp-bff/SKILL.md"
  - "skills/abeta-kmp-module-review/SKILL.md"
verification_command: "(cd kmp && ./gradlew :sdui-contracts:spotlessCheck :sdui-contracts:detekt :sdui-contracts:apiCheck :sdui-contracts:jvmTest :sdui-contracts:macosArm64Test :sdui-contracts:compileKotlinWatchosArm64 :sdui-contracts:compileKotlinWasmJs --no-daemon)"
---

# Module Code Owner Agent: `kmp/sdui-contracts` (`sdui-contracts-owner`)

## Ownership Scope
- **Paths**: `kmp/sdui-contracts/**`
- **Maven Coordinate**: `io.github.abeta-dev:sdui-contracts` (Kotlin package `dev.abeta.kmp.contracts`)
- **Targets**: `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`, `wasmJs`
- **Core Responsibilities**: Pure `@Serializable` SDUI v1 wire DTOs (**29 Wire Atoms** incl. `StepperAtom`, `CountdownAtom` w/ `expires_at_epoch_ms`, `MediaPagerAtom`, plus 12 `ExtendedAtoms.kt` primitives, **23 `SnippetTypes`** incl. `product_card`, `swipe_deck`/`search_bar`/`chip_group`, `nudge_bar`/`marquee_strip`/`order_tracker`, **`composed_block`** + **36 `ComposedNodeKind`s**, `custom_slot`, & `graph_canvas`, **6 `SnippetDeltaOp`s** (`append_text`, `patch_snippet_data`, `replace_snippet`, `insert_snippet`, `remove_snippet`, `update_page_meta`), `PageResponse` versioning/streaming fields, `ScreenManifestResponse` w/ `sdui_schema_version`, `AdaptiveContracts.kt`, `MotionContracts.kt`, `AtomEnums.kt` with **7 Ambient `MotionLoopEffect`s**, `FloatingCartSpec` with free-delivery threshold nudge fields, `PaginationSpec`, `OverlayPresentation`, `ActionRef`, `ValidationRuleContract`, `TrackingContract`, `SduiJson`).

## Non-Negotiable Architecture & Review Gates
1. **Pure Wire Data Only**: ZERO Compose imports, ZERO `core` imports, ZERO action handlers, ZERO network clients. Every class is immutable `@Serializable` data.
2. **1:1 Go BFF Wire Parity**: EVERY field, `@SerialName` (`snake_case`), enum literal, and snippet type MUST match `bff/pkg/sdui/*` (`bff@v0.9.0`) 1:1 and pass all **11** golden fixtures in `bff/testdata/golden/{home_screen,form_screen,action_batch,containers_screen,store_home,interactive_screen,nudges_and_composed_screen,extended_atoms_screen,express_molecules_and_analytics_screen,streaming_and_versioned_screen,spatial_custom_and_graph_screen}.json`.
3. **Backward Compatibility**: New fields MUST have safe default values (`null`, `emptyList()`, `false`) so older servers and golden fixtures deserialize without error (`SduiJson` with `ignoreUnknownKeys = true`).
4. **SOLID & KISS**: Keep contract files partitioned by responsibility (atoms, snippets, adaptive, motion, enums, actions ref).
5. **Lint & ABI**: Pass Spotless, Detekt, BCV `apiCheck` (`kmp/sdui-contracts/api/sdui-contracts.api`), and `ContractsSerializationTest`.
