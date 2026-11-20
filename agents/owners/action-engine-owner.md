---
name: action-engine-owner
module: ":action-engine"
layer: "Layer 2"
owned_paths:
  - "kmp/action-engine/**"
allowed_dependencies:
  - "projects.core"
  - "projects.sduiContracts"
forbidden_dependencies:
  - "projects.designTokens"
  - "projects.uiAtoms"
  - "projects.network"
  - "projects.sduiLayout"
  - "projects.watchCompanion"
  - "Compose UI"
required_skills:
  - "skills/abeta-kmp-actions/SKILL.md"
  - "skills/abeta-kmp-architecture/SKILL.md"
  - "skills/abeta-kmp-module-review/SKILL.md"
verification_command: "(cd kmp && ./gradlew :action-engine:spotlessCheck :action-engine:detekt :action-engine:apiCheck :action-engine:jvmTest :action-engine:macosArm64Test :action-engine:compileKotlinWatchosArm64 :action-engine:compileKotlinWasmJs --no-daemon)"
---

# Module Code Owner Agent: `kmp/action-engine` (`action-engine-owner`)

## Ownership Scope
- **Paths**: `kmp/action-engine/**`
- **Maven Coordinate**: `dev.abeta.kmp:action-engine`
- **Targets**: `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`, `wasmJs`
- **Core Responsibilities**: **18 built-in `ActionTypes`**, `ActionPayloadDecoder` / `ActionDecoderRegistry`, `ActionHandler` / `ActionRegistry`, `DefaultActionDispatcher`, `ActionValidator`, `ActionOutcome`, `ApiCallExecutor` port.

## Non-Negotiable Architecture & Review Gates
1. **Strict Layer 2 Boundary**: May ONLY depend on `projects.core` and `projects.sduiContracts`. ZERO dependencies on `network`, `ui-atoms`, `sdui-layout`, `watch-companion`, or Compose UI.
2. **Open/Closed Principle (OCP)**: Every new action MUST be added via a typed payload + `ActionPayloadDecoder` + `ActionHandler` registered in `ActionDecoderRegistry` / `ActionRegistry` — NEVER add monolithic `when (actionType)` switches inside UI or `DefaultActionDispatcher`.
3. **Security Allowlisting**: Unknown or unregistered `actionType` strings MUST fail explicitly (`UnsupportedAction` / `AppError`) instead of silently executing arbitrary URLs or reflection.
4. **1:1 Go BFF Parity**: Every constant in `ActionTypes` and payload field MUST match `bff/pkg/sdui/actions/actions.go` and `bff/internal/infra/action_executor.go`.
5. **SOLID, KISS, Lint & ABI**: Keep handlers bifurcated by domain (`NavigationAndNetwork`, `UiAndState`, `OsBridge`), enforce Spotless, Detekt, BCV `apiCheck`, and `ActionsEngineTest`.
