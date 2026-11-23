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
| `sdui-contracts` | Pure `@Serializable` DTOs (`snake_case` `@SerialName`): `PageResponse` (`PaginationSpec`, `pull_to_refresh`, `floating_cart` / `FloatingCartSpec` with free-delivery threshold nudges, `adaptive_layout` / `AdaptivePageSpec`), `SnippetEnvelope` (with optional `motion`), **21 `SnippetTypes`** (incl. `product_card`, `swipe_deck`, `search_bar`, `chip_group`, `nudge_bar`, `marquee_strip`, `order_tracker`, and **`composed_block`** with **29 `ComposedNodeKind`s**), **28 Wire Atoms** (incl. `StepperAtom`, `CountdownAtom`, & 12 `ExtendedAtoms.kt` primitives), `AdaptiveContracts.kt`, `MotionContracts.kt` + `AtomEnums.kt` (**7 Ambient `MotionLoopEffect`s**, `FeedbackEffect`, `OsPermissionKind`, `ScreenLayoutMode`, `OverlayPresentation`), `LayoutConfig`, `ColorTokenRef`, `TypographyTokenRef`, `ActionRef`, `ValidationRuleContract`, `TrackingContract`, `SduiJson` | `core`, Compose UI, Ktor, `action-engine`, `network`, `sdui-layout`, executable logic |
| `ui-atoms` | **30 stateless `@Composable` atoms** (`UiText`, `UiIcon`, `UiTag`, `UiButton`, `UiImage`, `UiTextField`, `UiDivider`, `UiSpacer`, `UiProgress`, `UiSurfaceCard`, `UiSwitch`, `UiCheckbox`, `UiSlider`, `UiAvatar`, `UiSkeleton`, animated `UiStepper`, live urgency `UiCountdown`, odometer `UiRollingCounter`, plus 12 extended atoms `UiRadioGroup`, `UiSelect`, `UiOtpInput`, `UiChip`, `UiSegmentedControl`, `UiRating`, `UiPrice`, `UiBadge`, `UiAlertBanner`, `UiStatTile`, `UiKeyValueRow`, `UiQrCode`), `UiCelebrationOverlay`, `UiHapticFeedback.kt`, `MotionModifier.kt` + `MotionLoopModifiers.kt` (`applyMotionSpec`, **7 Ambient `MotionLoopEffect`s**, `Modifier.pressBounce`, `Modifier.shakeOnError`) honoring `ThemeTokens.motion.reducedMotion`, `UiImageRenderer` / `LocalImageRenderer`, `TokenResolvers`, `LayoutConfigModifier`, `compose.uiTest` semantic tests (`UiAtomsComposeTest`, `ExtendedAtomsComposeTest`) | `core`, `action-engine`, `network`, `sdui-layout`, Ktor, navigation controllers, network coroutines |

> Note: `state` (Layer 0) owns `SessionStore`, `PreferenceStore`, `createSecurePersistentKeyValueStore`, `FeatureFlagProvider`, and `OfflineMutationQueue`—never put design tokens in `state`.

## Authoring Workflow

1. **Design Tokens (`kmp/design-tokens`)**: Define semantic tokens and responsive metrics (`ThemeTokens.adaptiveMetrics` from `ResponsiveTokens.kt`); keep `bff/pkg/sdui/theming` aligned 1:1.
2. **Wire Contracts (`kmp/sdui-contracts`)**:
   - Annotate DTOs with `@Serializable` and `snake_case` `@SerialName` (`TabItemSpec.title` is `String` matching Go BFF `TabItemSpec.Title`).
   - Use `AdaptiveContracts.kt` (`ResponsiveColumnsSpec`, `ResponsiveIntSpec`, `AdaptivePageSpec`), `ScreenLayoutMode`, `OsPermissionKind`, `ColorTokenRef`, `TypographyTokenRef`, and `MotionSpec`.
   - Emit actions solely via `ActionRef` (`id`, `type`, `payload`, `validations`, `secondary_actions`) on fields like `on_click`, `on_change`, `on_value_change`, `on_submit`, `on_increment`, `on_decrement`, `on_complete`, `on_remove`, `on_rate`, `on_action`, `on_dismiss`, and `on_expire`.
   - Update `bff/pkg/sdui/*` (`bff@v0.5.0`) and verify `GoldenFixturesParityTest` + all **8 golden fixtures** (`bff/testdata/golden/{home_screen,form_screen,action_batch,containers_screen,store_home,interactive_screen,nudges_and_composed_screen,extended_atoms_screen}.json`).
3. **Stateless Compose Atoms (`kmp/ui-atoms`)**:
   - Accept immutable wire contracts + `onAction: (ActionRef) -> Unit` + `modifier: Modifier = Modifier`.
   - Trigger tactile feedback purely through `LocalUiHapticFeedback` (`UiHapticKind`) without importing `core.platform.HapticEngine`.
   - Resolve styling via `TokenResolvers`, `LayoutConfigModifier`, and `MotionModifier.kt` + `MotionLoopModifiers.kt` (including `Modifier.pressBounce` & `Modifier.shakeOnError`), honoring `ThemeTokens.motion.reducedMotion`.
   - Add `compose.uiTest` semantic tests in `UiAtomsComposeTest` / `ExtendedAtomsComposeTest` and preview stories in `:storybook` (8 `UI ATOMS` stories covering all 30 atoms, `"All 21 Snippets"`, `MotionAndStoreStory`, `ResponsiveAndBridgesStory`, `AnimationNudgesStory`, `SnippetCrafterStory`).

## Anti-Patterns to Reject

- Importing `dev.abeta.kmp.core.*`, `dev.abeta.kmp.actions.*`, `dev.abeta.kmp.network.*`, or `io.ktor.*` in any Layer 1 module.
- Writing `when (action.type)` inside `@Composable` atoms.

## Verification

```bash
(cd kmp && ./gradlew :design-tokens:check :sdui-contracts:check :ui-atoms:check spotlessCheck detekt apiCheck)
```
