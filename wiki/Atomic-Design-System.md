# Atomic Design System

The `abeta-dev/kmp` UI system splits styling, wire contracts, and Compose rendering across **three isolated Layer 1 modules**:

| Module | Role | Project Dependencies |
| :--- | :--- | :--- |
| `design-tokens` | Owns all design tokens (`ColorTokens`, `DesignPalette`, `SpacingTokens`, `CornerRadiusTokens`, `StrokeWidthTokens`, `TypographyTokens`, `ElevationTokens`, `MotionTokens`, `OpacityTokens`, `SizingTokens`, `ThemePreset`, `ColorAccessibility`, `DesignTheme`, `ThemeTokens`, plus `ResponsiveTokens.kt`: `WindowWidthClass`, `WindowHeightClass`, `DeviceFormFactor`, `AdaptiveLayoutMetrics`, `resolveAdaptiveLayoutMetrics`, `LocalAdaptiveLayoutMetrics`, `ProvideAdaptiveLayoutMetrics` via `ThemeTokens.adaptiveMetrics`) | None |
| `sdui-contracts` | Pure `@Serializable` SDUI v1 wire DTOs (`PageResponse` with `PaginationSpec`, `pullToRefresh`, `floatingCart` / `FloatingCartSpec` with free-delivery threshold nudges, and `adaptiveLayout` / `AdaptivePageSpec`, `SnippetEnvelope` with optional `motion`, **21 `SnippetTypes`** incl. `composed_block` with **29 `ComposedNodeKind`s**, **28 Wire Atoms**, `AdaptiveContracts.kt`, `MotionContracts.kt` + `AtomEnums.kt` with **7 Ambient `MotionLoopEffect`s**, `OsPermissionKind`, `ScreenLayoutMode`, `OverlayPresentation`, `LayoutConfig`, `ColorTokenRef`, `TypographyTokenRef`, `ActionRef`, `SduiJson`) | None |
| `ui-atoms` | **30 stateless Compose Multiplatform UI atoms** (`UiText`, `UiIcon`, `UiTag`, `UiButton`, `UiImage`, `UiTextField`, `UiDivider`, `UiSpacer`, `UiProgress`, `UiSurfaceCard`, `UiSwitch`, `UiCheckbox`, `UiSlider`, `UiAvatar`, `UiSkeleton`, animated `UiStepper`, live urgency `UiCountdown`, odometer `UiRollingCounter`, plus 12 extended atoms `UiRadioGroup`, `UiSelect`, `UiOtpInput`, `UiChip`, `UiSegmentedControl`, `UiRating`, `UiPrice`, `UiBadge`, `UiAlertBanner`, `UiStatTile`, `UiKeyValueRow`, `UiQrCode`), `UiCelebrationOverlay` (`UiCelebrationBurst.kt`), `UiHapticFeedback.kt`, `MotionModifier.kt` + `MotionLoopModifiers.kt` (`applyMotionSpec`, **7 Ambient `MotionLoopEffect`s**, `Modifier.pressBounce`, `Modifier.shakeOnError`), `UiImageRenderer` / `LocalImageRenderer`, `TokenResolvers`, `LayoutConfigModifier` | `design-tokens`, `sdui-contracts` |

> Note: `state` (Layer 0) handles reactive `SessionStore`, `PreferenceStore`, `createSecurePersistentKeyValueStore`, `FeatureFlagProvider`, and `OfflineMutationQueue`—NOT design tokens. See `:storybook` (`./gradlew :storybook:run` or `:storybook:wasmJsBrowserDevelopmentRun`) for the **20-story** interactive token, 30-atom, motion, adaptive breakpoint, Super-App demo, and 21-snippet explorer.

---

## Design Token & Responsive Breakpoint Discipline (`design-tokens`)

- Wire payloads use `ColorTokenRef` (`token`, `alpha`, `fallback_hex`, `hex`) and `TypographyTokenRef` (`token`, `font_weight`, `letter_spacing`).
- `TokenResolvers` in `ui-atoms` resolves semantic keys (e.g., `"text.primary"`, `"surface.primary"`, `"title.large"`) against `DesignTheme` / `ThemeTokens` (`CompositionLocal`), enabling light/dark palettes, `ThemePreset` switching, and `reducedMotion` without changing BFF payloads.
- `ResponsiveTokens.kt` classifies viewports into `WindowWidthClass` (`COMPACT`, `MEDIUM`, `EXPANDED`), `WindowHeightClass`, and `DeviceFormFactor` (`WATCH`, `PHONE`, `TABLET`, `DESKTOP_WIDE`), providing `ThemeTokens.adaptiveMetrics` via `ProvideAdaptiveLayoutMetrics` / `LocalAdaptiveLayoutMetrics`.

---

## Pure Wire Contracts (`sdui-contracts`)

- **28 Wire Atoms** (`Atoms.kt` + `ExtendedAtoms.kt`):
  - Display & Media: `TextAtom`, `IconAtom`, `TagAtom`, `ButtonAtom`, `ImageAtom`, `AvatarAtom`, `PriceAtom`, `RatingAtom`, `BadgeAtom`, `AlertBannerAtom`, `StatTileAtom`, `KeyValueRowAtom`, `QrCodeAtom`
  - Form Inputs & Selection Controls: `TextFieldAtom`, `SwitchAtom`, `CheckboxAtom`, `SliderAtom`, `StepperAtom`, `RadioGroupAtom`, `SelectAtom`, `OtpInputAtom`, `ChipAtom`, `SegmentedControlAtom`
  - Layout, Timing & Feedback: `DividerAtom`, `SpacerAtom`, `ProgressAtom`, `SkeletonAtom` (`SkeletonKind`: `"text"` | `"rectangle"` | `"circle"`), `CountdownAtom`
- **Responsive & Permission Contracts (`AdaptiveContracts.kt`, `AtomEnums.kt`)**:
  - `ScreenLayoutMode` (`"single_column"`, `"centered"`, `"split_pane"`), `AdaptivePageSpec` (`layout_mode`, `max_width_dp`, `split_pane_enabled`, `secondary_snippet_ids`), `ResponsiveColumnsSpec` (`GridSnippetData.columnsByWidth`), `ResponsiveIntSpec` (`CarouselSnippetData.itemWidthByWidth`), and `OsPermissionKind` (`"location"`, `"notifications"`, `"camera"`, `"storage"`).
- **Motion & Feedback Contracts (`MotionContracts.kt`, `AtomEnums.kt`)**:
  - `MotionSpec` (`enter`, `exit`, `duration_token`, `duration_ms`, `stagger_ms`, `delay_ms`, `loop_effect`), `MotionTransition` (`none`, `fade_in`, `slide_up`, `slide_down`, `scale_in`, `spring_bounce`), `MotionDurationToken` (`instant`, `fast`, `normal`, `slow`), **7 Ambient `MotionLoopEffect`s** (`pulse`, `shimmer`, `wiggle`, `float`, `heartbeat`, `glow_ring`, `marquee`, plus `none`), `FeedbackEffect` (`celebration`, `cart_bounce`, `shake_error`, `haptic_tick`), and optional `motion` field on `ButtonAtom`, `TagAtom`, `BadgeAtom`, `CountdownAtom`, `ComposedNode`, `SnippetEnvelope`, `BottomBarSpec`, and `FloatingCartSpec`.
- **21 Canonical `SnippetTypes`**:
  - Core 10: `header_v1` (with `avatar`, `skeletons`), `banner_v1`, `metric_card`, `card_v1` (with `avatar`, `skeletons`), `action_row`, `form_v1` (with `fields`, `switches`, `checkboxes`, `sliders`), `empty_state`, `divider`, `spacer`, `product_card` (`ProductCardSnippetData` with `stepper: StepperAtom` and `unit_price_cents`)
  - Containers 4: `carousel` (`CarouselSnippetData` with `itemWidthByWidth`), `grid` (`GridSnippetData` with `columnsByWidth`), `tabs` (`TabsSnippetData`, where `TabItemSpec.title` is `string` matching Go BFF `TabItemSpec.Title`), `accordion` (`AccordionSnippetData`)
  - Interactive Discovery 3: `swipe_deck`, `search_bar`, `chip_group`
  - Express Nudges & Molding 4: `nudge_bar`, `marquee_strip`, `order_tracker`, and **`composed_block`** (Atomic Snippet Molding Engine supporting **29 `ComposedNodeKind`s** up to depth 6)
- **Page & Overlay Extensions**: `FloatingCartSpec` (`PageResponse.floatingCart` with free-delivery threshold nudge fields), `PaginationSpec` (`next_cursor`, `has_more`, `load_more_action`), `PageResponse.pull_to_refresh`, `PageResponse.adaptive_layout`, `OverlayPresentation` (`"dialog"` | `"bottom_sheet"`).
- **Shared Box Styling**: `LayoutConfig` (`padding`, `margin`, `bg_color`, `corner_radius`, `border`, `fill_max_width`).

---

## Stateless Compose Atoms (`ui-atoms`) & `storybook`

`ui-atoms` renders all **28 wire atoms** plus `UiSurfaceCard` and `UiRollingCounter` (**30 stateless Compose atoms total**), plus `UiCelebrationOverlay` (`UiCelebrationBurst.kt`), `UiHapticFeedback.kt` (`LocalUiHapticFeedback`, `UiHapticKind`, `rememberComposeHapticFallback`), and `MotionModifier.kt` + `MotionLoopModifiers.kt` (`applyMotionSpec`, **7 Ambient `MotionLoopEffect`s**, `Modifier.pressBounce`, `Modifier.shakeOnError`) honoring `ThemeTokens.motion.reducedMotion`, verified with `compose.uiTest` semantic tests in `UiAtomsComposeTest` and `ExtendedAtomsComposeTest`:

```kotlin
@Composable
fun UiButton(
    atom: ButtonAtom,
    onAction: (ActionRef) -> Unit,
    modifier: Modifier = Modifier,
)
```

1. **Stateless**: Accept immutable contract models; avoid internal business state or network coroutines.
2. **Pure Intent & Haptic Emission**: Emit `ActionRef` via `onAction` (`on_click`, `on_change`, `on_value_change`, `on_submit`, `on_increment`, `on_decrement`, `on_complete`, `on_remove`, `on_rate`, `on_action`, `on_dismiss`, `on_expire`) and abstract `UiHapticKind` via `LocalUiHapticFeedback`—never import `core`, `action-engine`, or `network`.
3. **Token-Driven Styling**: Resolve colors, typography, spacing, borders, and corner radii through `TokenResolvers` and `LayoutConfigModifier`.
4. **Interactive Explorer (`storybook`)**: Features **20 stories across 5 categories** (`LIVE_DEMOS`, `FOUNDATIONS`, `ATOMS` — 8 dedicated stories covering all 30 atoms, `SNIPPETS`, `PLAYGROUND`), including **Live 7-in-1 Super-App Demos** (`super-app-demos`), `"All 21 Snippets"`, `MotionAndStoreStory`, `ResponsiveAndBridgesStory`, `DiscoveryAndSearchStory`, **Animation & Nudges Lab** (`animation-nudges`), **Atomic Snippet Crafter** (`snippet-crafter`), **End-to-End App Journeys** (`e2e-journeys`), and `LiveBffStory`, responsive `CompactStoryStrip`/`StorySidebar` shell, `fontScale` controls (`A-`, `1.0x`, `A+`), `launchStorybookMacOsWindow`, `StorybookViewController` (`AbetaStorybookKit.xcframework`), and `StorybookComposeTest`.
