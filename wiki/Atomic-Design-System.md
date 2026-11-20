# Atomic Design System

The `abeta-dev/kmp` UI system splits styling, wire contracts, and Compose rendering across **three isolated Layer 1 modules**:

| Module | Role | Project Dependencies |
| :--- | :--- | :--- |
| `design-tokens` | Owns all design tokens (`ColorTokens`, `DesignPalette`, `SpacingTokens`, `CornerRadiusTokens`, `StrokeWidthTokens`, `TypographyTokens`, `ElevationTokens`, `MotionTokens`, `OpacityTokens`, `SizingTokens`, `ThemePreset`, `ColorAccessibility`, `DesignTheme`, `ThemeTokens`, plus `ResponsiveTokens.kt`: `WindowWidthClass`, `WindowHeightClass`, `DeviceFormFactor`, `AdaptiveLayoutMetrics`, `resolveAdaptiveLayoutMetrics`, `LocalAdaptiveLayoutMetrics`, `ProvideAdaptiveLayoutMetrics` via `ThemeTokens.adaptiveMetrics`) | None |
| `sdui-contracts` | Pure `@Serializable` SDUI v1 wire DTOs (`PageResponse` with `PaginationSpec`, `pullToRefresh`, `floatingCart` / `FloatingCartSpec`, and `adaptiveLayout` / `AdaptivePageSpec`, `SnippetEnvelope` with optional `motion`, **14 `SnippetTypes`**, **15 Wire Atoms**, `AdaptiveContracts.kt`, `MotionContracts.kt` + `AtomEnums.kt`, `OsPermissionKind`, `ScreenLayoutMode`, `OverlayPresentation`, `LayoutConfig`, `ColorTokenRef`, `TypographyTokenRef`, `ActionRef`, `SduiJson`) | None |
| `ui-atoms` | **16 stateless Compose Multiplatform UI atoms** (`UiText`, `UiIcon`, `UiTag`, `UiButton`, `UiImage`, `UiTextField`, `UiDivider`, `UiSpacer`, `UiProgress`, `UiSurfaceCard`, `UiSwitch`, `UiCheckbox`, `UiSlider`, `UiAvatar`, `UiSkeleton`, animated `UiStepper` morphing `ADD` <-> `- qty +` with haptic hooks), `UiCelebrationOverlay` (`UiCelebrationBurst.kt`), `UiHapticFeedback.kt` (`UiHapticKind`, `LocalUiHapticFeedback`, `rememberComposeHapticFallback`), `MotionModifier.kt` (`applyMotionSpec`, `Modifier.shakeOnError`, `resolveMotionDurationMs`, `resolveMotionDelayMs`, `resolveEnterTransition`, `resolveExitTransition`), `UiImageRenderer` / `LocalImageRenderer`, `TokenResolvers`, `LayoutConfigModifier` | `design-tokens`, `sdui-contracts` |

> Note: `state` (Layer 0) handles reactive `SessionStore`, `PreferenceStore`, `createSecurePersistentKeyValueStore`, `FeatureFlagProvider`, and `OfflineMutationQueue`—NOT design tokens. See `:storybook` (`./gradlew :storybook:run` or `:storybook:wasmJsBrowserDevelopmentRun`) for the **12-story** interactive token, atom, motion, adaptive breakpoint, and snippet explorer.

---

## Design Token & Responsive Breakpoint Discipline (`design-tokens`)

- Wire payloads use `ColorTokenRef` (`token`, `alpha`, `fallback_hex`, `hex`) and `TypographyTokenRef` (`token`, `font_weight`, `letter_spacing`).
- `TokenResolvers` in `ui-atoms` resolves semantic keys (e.g., `"text.primary"`, `"surface.primary"`, `"title.large"`) against `DesignTheme` / `ThemeTokens` (`CompositionLocal`), enabling light/dark palettes, `ThemePreset` switching, and `reducedMotion` without changing BFF payloads.
- `ResponsiveTokens.kt` classifies viewports into `WindowWidthClass` (`COMPACT`, `MEDIUM`, `EXPANDED`), `WindowHeightClass`, and `DeviceFormFactor` (`WATCH`, `PHONE`, `TABLET`, `DESKTOP_WIDE`), providing `ThemeTokens.adaptiveMetrics` via `ProvideAdaptiveLayoutMetrics` / `LocalAdaptiveLayoutMetrics`.

---

## Pure Wire Contracts (`sdui-contracts`)

- **15 Wire Atoms**:
  - Display & Media: `TextAtom`, `IconAtom`, `TagAtom`, `ButtonAtom`, `ImageAtom`, `AvatarAtom`
  - Form Inputs & Controls: `TextFieldAtom`, `SwitchAtom`, `CheckboxAtom`, `SliderAtom`, `StepperAtom`
  - Layout & Feedback: `DividerAtom`, `SpacerAtom`, `ProgressAtom`, `SkeletonAtom` (`SkeletonKind`: `"text"` | `"rectangle"` | `"circle"`)
- **Responsive & Permission Contracts (`AdaptiveContracts.kt`, `AtomEnums.kt`)**:
  - `ScreenLayoutMode` (`"single_column"`, `"centered"`, `"split_pane"`), `AdaptivePageSpec` (`layout_mode`, `max_width_dp`, `split_pane_enabled`, `secondary_snippet_ids`), `ResponsiveColumnsSpec` (`GridSnippetData.columnsByWidth`), `ResponsiveIntSpec` (`CarouselSnippetData.itemWidthByWidth`), and `OsPermissionKind` (`"location"`, `"notifications"`, `"camera"`, `"storage"`).
- **Motion & Feedback Contracts (`MotionContracts.kt`, `AtomEnums.kt`)**:
  - `MotionSpec` (`enter`, `exit`, `duration_token`, `duration_ms`, `stagger_ms`, `delay_ms`, `loop_effect`), `MotionTransition` (`none`, `fade_in`, `slide_up`, `slide_down`, `scale_in`, `spring_bounce`), `MotionDurationToken` (`instant`, `fast`, `normal`, `slow`), `MotionLoopEffect` (`none`, `pulse`, `shimmer`), `FeedbackEffect` (`celebration`, `cart_bounce`, `shake_error`, `haptic_tick`), and optional `motion` field on `ButtonAtom`, `TagAtom`, `SnippetEnvelope`, `BottomBarSpec`, and `FloatingCartSpec`.
- **14 Standard `SnippetTypes`**:
  - Core 10: `header_v1` (with `avatar`, `skeletons`), `banner_v1`, `metric_card`, `card_v1` (with `avatar`, `skeletons`), `action_row`, `form_v1` (with `fields`, `switches`, `checkboxes`, `sliders`), `empty_state`, `divider`, `spacer`, `product_card` (`ProductCardSnippetData` with `stepper: StepperAtom` and `unit_price_cents`)
  - Containers 4: `carousel` (`CarouselSnippetData` with `itemWidthByWidth`), `grid` (`GridSnippetData` with `columnsByWidth`), `tabs` (`TabsSnippetData`, where `TabItemSpec.title` is `string` matching Go BFF `TabItemSpec.Title`), `accordion` (`AccordionSnippetData`)
- **Page & Overlay Extensions**: `FloatingCartSpec` (`PageResponse.floatingCart`), `PaginationSpec` (`next_cursor`, `has_more`, `load_more_action`), `PageResponse.pull_to_refresh`, `PageResponse.adaptive_layout`, `OverlayPresentation` (`"dialog"` | `"bottom_sheet"`).
- **Shared Box Styling**: `LayoutConfig` (`padding`, `margin`, `bg_color`, `corner_radius`, `border`, `fill_max_width`).

---

## Stateless Compose Atoms (`ui-atoms`) & `storybook`

`ui-atoms` renders all **15 wire atoms** plus `UiSurfaceCard` (**16 stateless Compose atoms total**), plus `UiCelebrationOverlay` (`UiCelebrationBurst.kt`), `UiHapticFeedback.kt` (`LocalUiHapticFeedback`, `UiHapticKind`, `rememberComposeHapticFallback`), and `MotionModifier.kt` (`applyMotionSpec`, `Modifier.shakeOnError`, `resolveMotionDurationMs`, `resolveMotionDelayMs`, `resolveEnterTransition`, `resolveExitTransition`) honoring `ThemeTokens.motion.reducedMotion`, verified with `compose.uiTest` semantic tests in `UiAtomsComposeTest`:

```kotlin
@Composable
fun UiButton(
    atom: ButtonAtom,
    onAction: (ActionRef) -> Unit,
    modifier: Modifier = Modifier,
)
```

1. **Stateless**: Accept immutable contract models; avoid internal business state or network coroutines.
2. **Pure Intent & Haptic Emission**: Emit `ActionRef` via `onAction` (`on_click`, `on_change`, `on_value_change`, `on_submit`, `on_increment`, `on_decrement`) and abstract `UiHapticKind` via `LocalUiHapticFeedback`—never import `core`, `action-engine`, or `network`.
3. **Token-Driven Styling**: Resolve colors, typography, spacing, borders, and corner radii through `TokenResolvers` and `LayoutConfigModifier`.
4. **Interactive Explorer (`storybook`)**: Features **12 stories across 4 categories** (`FOUNDATIONS`, `ATOMS`, `SNIPPETS`, `PLAYGROUND`)—adding `ResponsiveAndBridgesStory` (`StoryCatalog.STORY_RESPONSIVE_BRIDGES`), responsive `CompactStoryStrip`/`StorySidebar` shell, `ControlsAndDataAtomsStory`, `"All 14 Snippets"`, `MotionAndStoreStory`, `LiveBffStory` (with `"store-home"` & `"watch-status"`), `fontScale` controls (`A-`, `1.0x`, `A+`), `launchStorybookMacOsWindow`, `StorybookViewController` (`AbetaStorybookKit.xcframework`), and `StorybookComposeTest`.
