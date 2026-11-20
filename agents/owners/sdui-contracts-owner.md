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
- **Maven Coordinate**: `dev.abeta.kmp:sdui-contracts`
- **Targets**: `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`, `wasmJs`
- **Core Responsibilities**: Pure `@Serializable` SDUI v1 wire DTOs (**15 Wire Atoms**, **14 `SnippetTypes`**, `PageResponse`, `AdaptiveContracts.kt`, `MotionContracts.kt`, `AtomEnums.kt`, `FloatingCartSpec`, `PaginationSpec`, `OverlayPresentation`, `ActionRef`, `ValidationRuleContract`, `TrackingContract`, `SduiJson`).

## Non-Negotiable Architecture & Review Gates
1. **Pure Wire Data Only**: ZERO Compose imports, ZERO `core` imports, ZERO action handlers, ZERO network clients. Every class is immutable `@Serializable` data.
2. **1:1 Go BFF Wire Parity**: EVERY field, `@SerialName` (`snake_case`), enum literal, and snippet type MUST match `bff/pkg/sdui/{atoms,snippets,actions,theming}` 1:1 and pass all 5 golden fixtures in `bff/testdata/golden/{home_screen,form_screen,action_batch,containers_screen,store_home}.json`.
3. **Backward Compatibility**: New fields MUST have safe default values (`null`, `emptyList()`, `false`) so older servers and golden fixtures deserialize without error (`SduiJson` with `ignoreUnknownKeys = true`).
4. **SOLID & KISS**: Keep contract files partitioned by responsibility (atoms, snippets, adaptive, motion, enums, actions ref).
5. **Lint & ABI**: Pass Spotless, Detekt, BCV `apiCheck` (`kmp/sdui-contracts/api/sdui-contracts.api`), and `ContractsSerializationTest`.
