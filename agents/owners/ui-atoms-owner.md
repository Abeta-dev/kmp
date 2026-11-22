---
name: ui-atoms-owner
module: ":ui-atoms"
layer: "Layer 1"
owned_paths:
  - "kmp/ui-atoms/**"
allowed_dependencies:
  - "projects.designTokens"
  - "projects.sduiContracts"
forbidden_dependencies:
  - "projects.core"
  - "projects.state"
  - "projects.analytics"
  - "projects.actionEngine"
  - "projects.network"
  - "projects.sduiLayout"
  - "projects.watchCompanion"
required_skills:
  - "skills/abeta-kmp-atomic/SKILL.md"
  - "skills/abeta-kmp-module-review/SKILL.md"
verification_command: "(cd kmp && ./gradlew :ui-atoms:spotlessCheck :ui-atoms:detekt :ui-atoms:apiCheck :ui-atoms:jvmTest :ui-atoms:macosArm64Test :ui-atoms:compileKotlinWasmJs --no-daemon)"
---

# Module Code Owner Agent: `kmp/ui-atoms` (`ui-atoms-owner`)

## Ownership Scope
- **Paths**: `kmp/ui-atoms/**`
- **Maven Coordinate**: `dev.abeta.kmp:ui-atoms`
- **Targets**: `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `wasmJs`
- **Core Responsibilities**: **18 stateless Compose Multiplatform atoms** (`UiText`, `UiIcon`, `UiTag`, `UiButton`, `UiImage`, `UiTextField`, `UiDivider`, `UiSpacer`, `UiProgress`, `UiSurfaceCard`, `UiSwitch`, `UiCheckbox`, `UiSlider`, `UiAvatar`, `UiSkeleton`, `UiStepper`, live urgency `UiCountdown`, odometer `UiRollingCounter`), `UiCelebrationOverlay`, `UiHapticFeedback.kt`, `MotionModifier.kt` + `MotionLoopModifiers.kt` (`applyMotionSpec`, **7 Ambient `MotionLoopEffect`s**, `Modifier.pressBounce`, `Modifier.shakeOnError`), `LayoutConfigModifier`, `TokenResolvers`.

## Non-Negotiable Architecture & Review Gates
1. **Strict Atomic / Action Separation**: May ONLY depend on `design-tokens` and `sdui-contracts`. NEVER import `core`, `ActionHandler`, `ActionDispatcher`, `LayoutController`, or Ktor. Emit pure `ActionRef` via `onAction: (ActionRef) -> Unit` and haptics via `LocalUiHapticFeedback`.
2. **Native iOS & Android Craft Floor (`/impeccable`)**:
   - Every interactive control (`UiButton`, clickable `UiIcon`, `UiSwitch`, `UiCheckbox`, `UiStepper`, `UiSlider`) MUST meet `ThemeTokens.sizing.minTouchTarget` (`48.dp`, satisfying both iOS `44 pt` and Android `48 dp` minimums).
   - Provide `Role`, `contentDescription`, and disabled/loading semantics for VoiceOver & TalkBack.
   - NEVER hardcode raw hex colors or fixed `px` typography — resolve every visual token from `ThemeTokens`.
   - Every animation (`applyMotionSpec`, all 7 ambient `MotionLoopEffect`s, `pressBounce`, `shakeOnError`, `UiStepper`, `UiCountdown`, `UiRollingCounter`, `UiButton`, `UiCelebrationOverlay`) MUST honor `ThemeTokens.motion.reducedMotion`.
3. **Stateless & Composable (SRP/KISS)**: Keep atoms stateless (hoist values/overrides via parameters) and keep each atom in its own focused file.
4. **Tests, Lint & ABI**: Cover semantic behavior in `UiAtomsComposeTest` (`compose.uiTest`), pass Spotless, Detekt, and BCV `apiCheck` (`kmp/ui-atoms/api/ui-atoms.api`).
