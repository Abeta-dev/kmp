---
name: design-tokens-owner
module: ":design-tokens"
layer: "Layer 1"
owned_paths:
  - "kmp/design-tokens/**"
allowed_dependencies:
  - "Compose Multiplatform runtime/foundation/material3 (via libs.versions.toml)"
forbidden_dependencies:
  - "Any internal dev.abeta.kmp module (including core, state, sdui-contracts, ui-atoms, action-engine, network, sdui-layout)"
required_skills:
  - "skills/abeta-kmp-atomic/SKILL.md"
  - "skills/abeta-kmp-module-review/SKILL.md"
verification_command: "(cd kmp && ./gradlew :design-tokens:spotlessCheck :design-tokens:detekt :design-tokens:apiCheck :design-tokens:jvmTest :design-tokens:macosArm64Test :design-tokens:compileKotlinWasmJs --no-daemon)"
---

# Module Code Owner Agent: `kmp/design-tokens` (`design-tokens-owner`)

## Ownership Scope
- **Paths**: `kmp/design-tokens/**`
- **Maven Coordinate**: `io.github.abeta-dev:design-tokens` (Kotlin package `dev.abeta.kmp.tokens`)
- **Targets**: `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `wasmJs`
- **Core Responsibilities**: Single source of truth for all design tokens (`ColorTokens`, `DesignPalette`, `SpacingTokens`, `CornerRadiusTokens`, `StrokeWidthTokens`, `TypographyTokens`, `ElevationTokens`, `MotionTokens`, `OpacityTokens`, `SizingTokens`, `ThemePreset`, `ColorAccessibility`, `ResponsiveTokens.kt` / `AdaptiveLayoutMetrics`, `DesignTheme`, `ThemeTokens`).

## Non-Negotiable Architecture & Review Gates
1. **Zero Internal Module Dependencies**: `design-tokens` MUST depend on ZERO other KMP modules.
2. **Accessibility & Contrast (`/impeccable` + WCAG AA)**: Every light and dark color preset MUST pass `ColorAccessibility` contrast verification (`>= 4.5:1` body text, `>= 3.0:1` large text/UI controls); typography MUST use `sp` units (never fixed `px`).
3. **Reduced Motion & Dark Mode First-Class**: `DesignTheme` MUST propagate `darkTheme` and `reducedMotion` cleanly across `MotionTokens` (`ReducedMotionTokens` vs `DefaultMotionTokens`) and semantic `ColorTokens`.
4. **Adaptive Breakpoints**: `ResponsiveTokens.kt` (`WindowWidthClass`, `WindowHeightClass`, `DeviceFormFactor`, `resolveAdaptiveLayoutMetrics`) MUST remain pure, deterministic, and unit-tested across Watch, Phone, Tablet/iPad, Foldable, and Desktop widths.
5. **Lint & ABI**: Pass Spotless, Detekt, BCV `apiCheck` (`kmp/design-tokens/api/design-tokens.api`), and `bff/pkg/sdui/theming` token parity checks.
