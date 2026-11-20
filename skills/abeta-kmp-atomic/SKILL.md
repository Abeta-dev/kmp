---
name: abeta-kmp-atomic
description: Author design tokens, responsive layout metrics, pure SDUI v1 wire contracts, and stateless Compose Multiplatform UI atoms in abeta-dev/kmp without leaking core ports, actions, navigation, or network dependencies. Use when modifying design-tokens, sdui-contracts, or ui-atoms.
---

# Abeta KMP Atomic Design System

Follow these rules whenever creating or editing design tokens, wire DTOs, or stateless Compose atoms in Layer 1 (`design-tokens`, `sdui-contracts`, `ui-atoms`).

## Layer Isolation Rules

| Module | Allowed | Forbidden |
| :--- | :--- | :--- |
| `design-tokens` | Owns all immutable design tokens (`ColorTokens`, `DesignPalette`, `SpacingTokens`, `CornerRadiusTokens`, `StrokeWidthTokens`, `TypographyTokens`, `ElevationTokens`, `MotionTokens`, `OpacityTokens`, `SizingTokens`, `ThemePreset`, `ColorAccessibility`, `DesignTheme`, `ThemeTokens`, plus `ResponsiveTokens.kt`: `WindowWidthClass`, `WindowHeightClass`, `DeviceFormFactor`, `AdaptiveLayoutMetrics`, `resolveAdaptiveLayoutMetrics`, `LocalAdaptiveLayoutMetrics`, `ProvideAdaptiveLayoutMetrics` via `ThemeTokens.adaptiveMetrics`) | `core` or any other KMP project dependency |
| `sdui-contracts` | Pure `@Serializable` DTOs (`snake_case` `@SerialName`): `PageResponse` (`PaginationSpec`, `pull_to_refresh`, `floating_cart` / `FloatingCartSpec`, `adaptive_layout` / `AdaptivePageSpec`), `SnippetEnvelope` (with optional `motion`), **14 `SnippetTypes`** (incl. `product_card`, `carousel` with `itemWidthByWidth`, `grid` with `columnsByWidth`, `tabs` with `TabItemSpec.title: String`, `accordion`), **15 Wire Atoms** (incl. `StepperAtom`, `SwitchAtom`, `CheckboxAtom`, `SliderAtom`, `AvatarAtom`, `SkeletonAtom` with `SkeletonKind`), `AdaptiveContracts.kt` (`ResponsiveColumnsSpec`, `ResponsiveIntSpec`, `AdaptivePageSpec`), `MotionContracts.kt` + `AtomEnums.kt` (`MotionSpec`, `MotionTransition`, `MotionDurationToken`, `MotionLoopEffect`, `FeedbackEffect`, `OsPermissionKind`, `ScreenLayoutMode`, `OverlayPresentation`), `LayoutConfig`, `ColorTokenRef`, `TypographyTokenRef`, `ActionRef`, `ValidationRuleContract`, `TrackingContract`, `SduiJson` | `core`, Compose UI, Ktor, `action-engine`, `network`, `sdui-layout`, executable logic |
| `ui-atoms` | **16 stateless `@Composable` atoms** (`UiText`, `UiIcon`, `UiTag`, `UiButton`, `UiImage`, `UiTextField`, `UiDivider`, `UiSpacer`, `UiProgress`, `UiSurfaceCard`, `UiSwitch`, `UiCheckbox`, `UiSlider`, `UiAvatar`, `UiSkeleton`, animated `UiStepper` morphing `ADD` <-> `- qty +` with haptic hooks), `UiCelebrationOverlay` (`UiCelebrationBurst.kt`), `UiHapticFeedback.kt` (`UiHapticKind`, `LocalUiHapticFeedback`, `rememberComposeHapticFallback`), `MotionModifier.kt` (`applyMotionSpec`, `Modifier.shakeOnError`, `resolveMotionDurationMs`, `resolveMotionDelayMs`, `resolveEnterTransition`, `resolveExitTransition`) honoring `ThemeTokens.motion.reducedMotion`, `UiImageRenderer` / `LocalImageRenderer`, `TokenResolvers`, `LayoutConfigModifier`, `compose.uiTest` semantic tests (`UiAtomsComposeTest`) | `core`, `action-engine`, `network`, `sdui-layout`, Ktor, navigation controllers, network coroutines |

> Note: `state` (Layer 0) owns `SessionStore`, `PreferenceStore`, `createSecurePersistentKeyValueStore`, `FeatureFlagProvider`, and `OfflineMutationQueue`—never put design tokens in `state`.

## Authoring Workflow

1. **Design Tokens (`kmp/design-tokens`)**: Define semantic tokens and responsive metrics (`ThemeTokens.adaptiveMetrics` from `ResponsiveTokens.kt`); keep `bff/pkg/sdui/theming` aligned 1:1.
2. **Wire Contracts (`kmp/sdui-contracts`)**:
   - Annotate DTOs with `@Serializable` and `snake_case` `@SerialName` (`TabItemSpec.title` is `String` matching Go BFF `TabItemSpec.Title`).
   - Use `AdaptiveContracts.kt` (`ResponsiveColumnsSpec`, `ResponsiveIntSpec`, `AdaptivePageSpec`), `ScreenLayoutMode`, `OsPermissionKind`, `ColorTokenRef`, `TypographyTokenRef`, and `MotionSpec`.
   - Emit actions solely via `ActionRef` (`id`, `type`, `payload`, `validations`, `secondary_actions`) on fields like `on_click`, `on_change`, `on_value_change`, `on_submit`, `on_increment`, and `on_decrement`.
   - Update `bff/pkg/sdui/*` and verify `GoldenFixturesParityTest` + `bff/testdata/golden/{home_screen,form_screen,action_batch,containers_screen,store_home}.json`.
3. **Stateless Compose Atoms (`kmp/ui-atoms`)**:
   - Accept immutable wire contracts + `onAction: (ActionRef) -> Unit` + `modifier: Modifier = Modifier`.
   - Trigger tactile feedback purely through `LocalUiHapticFeedback` (`UiHapticKind`) without importing `core.platform.HapticEngine`.
   - Resolve styling via `TokenResolvers`, `LayoutConfigModifier`, and `MotionModifier.kt` (including `Modifier.shakeOnError`), honoring `ThemeTokens.motion.reducedMotion`.
   - Add `compose.uiTest` semantic tests in `UiAtomsComposeTest` and preview stories in `:storybook` (`ControlsAndDataAtomsStory`, `MotionAndStoreStory`, `ResponsiveAndBridgesStory`, `"All 14 Snippets"`).

## Anti-Patterns to Reject

- Importing `dev.abeta.kmp.core.*`, `dev.abeta.kmp.actions.*`, `dev.abeta.kmp.network.*`, or `io.ktor.*` in any Layer 1 module.
- Writing `when (action.type)` inside `@Composable` atoms.

## Verification

```bash
(cd kmp && ./gradlew :design-tokens:check :sdui-contracts:check :ui-atoms:check spotlessCheck detekt apiCheck)
```
