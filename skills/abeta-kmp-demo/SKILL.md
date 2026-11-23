---
name: abeta-kmp-demo
description: Reference guide for the abeta-dev/kmp demo & explorer modules (sample-app 7-in-1 Super-App Showcase, 20-story End-to-End storybook, Go BFF screens, and kmp/platforms native hosts). Use when running demos, inspecting reference wiring patterns, testing BFF-to-KMP parity, or copying demo patterns into a production app.
---

# Abeta KMP Kit — Demo & Reference Modules Skill (`abeta-kmp-demo`)

Use this skill to explore, run, test, or adapt the repository's reference implementations across `kmp/sample-app`, `kmp/storybook`, `bff/`, and `kmp/platforms/`.

## 1. End-to-End 7-Screen BFF <-> KMP Demo Mapping

Every screen in `bff/internal/infra/` is mapped 1:1 to `kmp/sample-app` (`SampleContainer.DEFAULT_SCREENS`) and `kmp/storybook` (`LiveBffStory.BFF_SCREENS`):

| Screen ID | Go BFF Builder (`bff/internal/infra/`) | KMP Offline Reference (`kmp/sample-app/`) | Showcased Capabilities |
| :--- | :--- | :--- | :--- |
| `home` | `screen_home.go` | `SampleHomeScreen.kt` | Hero header, banner, metrics, action cards, navigation, analytics impressions, golden `home_screen.json` |
| `store-home` | `screen_store_home.go` | `SampleStoreScreen.kt` + `SampleStoreCatalog.kt` | **Abeta Fresh** 8-Min Express quick-commerce: `ProductCardSnippetRenderer`, animated `UiStepper`, live `FloatingCartDockHost`, `columns_by_width`, `adaptive_layout`, `POST /api/v1/orders/express` -> `celebration` burst + toast, golden `store_home.json` |
| `catalog` | `screen_catalog.go` | `SampleCatalogScreen.kt` | Interactive token/atom catalog showcase & navigation routes |
| `profile` | `screen_profile.go` | `SampleProfileScreen.kt` | Avatar, session/preference actions, OS bridges (`open_url`, `share`, `biometric_auth`, `request_permission`) |
| `form-demo` | `screen_form_demo.go` | `SampleFormDemoScreen.kt` | Declarative `ValidationRule` client+server enforcement, `Modifier.shakeOnError`, `save_state`, golden `form_screen.json` |
| `containers` | `screen_containers.go` | `SampleContainersScreen.kt` | Nested `carousel`, `grid`, `tabs`, `accordion`, `UiSwitch`, `UiCheckbox`, `UiSlider`, golden `containers_screen.json` |
| `watch-status` | `screen_watch_status.go` | `SampleWatchScreen.kt` (+ `:watch-companion`) | Glanceable delivery ETA, live cart/order metrics, compact watch actions consumed by `WatchScreenMapper` & `WatchCompanionController` |

## 2. Running Every Demo Mode

```bash
# 1. Sample App — Offline Desktop GUI (all 7 screens + dark/light + theme presets + form-factor switcher)
(cd kmp && ./gradlew :sample-app:run)

# 2. Sample App — Headless CI Smoke Verification (verifies home, form-demo, containers, store-home + celebration, watch-status)
(cd kmp && ./gradlew :sample-app:run --args="--headless")

# 3. Sample App — Live Go BFF Mode (real HTTP/2 + Brotli + ETag + /api/v1/orders/express)
(cd bff && go run ./cmd/bff) &
(cd kmp && ./gradlew :sample-app:run --args="--bff-url=http://localhost:8080")

# 4. Storybook — Desktop JVM (20 interactive stories across LIVE_DEMOS, FOUNDATIONS, ATOMS, SNIPPETS, PLAYGROUND)
(cd kmp && ./gradlew :storybook:run)

# 5. Storybook — WasmJS Browser Web App (includes all 7 Live Super-App Demos playable zero-backend + 8 UI Atom stories covering all 30 atoms)
(cd kmp && ./gradlew :storybook:wasmJsBrowserDevelopmentRun)

# 6. Export fresh Retina PNG screenshots for Demo, Super-App Verticals, & Storybook -> docs/assets/screenshots/
(cd kmp && ./gradlew :sample-app:run --args="--capture-screenshots=../docs/assets/screenshots")
(cd kmp && ./gradlew :sample-app:run --args="--capture-verticals=../docs/assets/screenshots")
(cd kmp && ./gradlew :storybook:run --args="--capture-screenshots=../docs/assets/screenshots")
# Gallery: docs/demo-screenshots.md
```

## 3. Storybook 20-Story Reference Map (`kmp/storybook/`)

- **LIVE_DEMOS**: Live 7-in-1 Super-App Demos — **Abeta Fresh**, **Abeta Bites**, **Abeta Pulse**, **Abeta Mart**, **Abeta Spark**, **Abeta Trade**, **Abeta Pay** & **Watch** with zero-backend offline parity & `?story=&app=` URL deep links (`SuperAppDemosStory.kt`)
- **FOUNDATIONS**: Introduction (`IntroductionStory.kt`), Design Tokens — Color Palette, WCAG Contrast, Typography & Theme Presets (`FoundationsStories.kt`)
- **ATOMS (8 stories covering all 30 atoms)**: Button, Text & Tag, TextField, Icons/Progress/Media, Controls/Avatar/Skeleton (`AtomStories.kt`, `ControlsAndDataAtomsStory.kt`), Selection/Chips/OTP (`ExtendedAtomSelectionStory.kt`: `UiRadioGroup`, `UiSelect`, `UiOtpInput`, `UiChip`, `UiSegmentedControl`), Commerce/Pricing/QR (`ExtendedAtomCommerceStory.kt`: `UiPrice`, `UiRating`, `UiKeyValueRow`, `UiQrCode`, `UiStepper`), Badges/Alerts/Telemetry (`ExtendedAtomTelemetryStory.kt`: `UiBadge`, `UiAlertBanner`, `UiStatTile`, `UiCountdown`, `UiRollingCounter`)
- **SNIPPETS**: All 21 Snippets Gallery (`SnippetsStory.kt`)
- **PLAYGROUND**: Interactive Action Dispatcher Sandbox (`PlaygroundStory.kt`), Motion & Abeta Fresh Store (`MotionAndStoreStory.kt`), Live Go BFF Explorer with Built-in Demo + Remote BFF toggle across all 7 Super-App slugs (`LiveBffStory.kt`), Responsive Form Factors & OS Bridges (`ResponsiveAndBridgesStory.kt`), Discovery/Search/Swipe (`DiscoveryAndSearchStory.kt`), Animation & Nudges Lab (`AnimationNudgesStory.kt`), Atomic Snippet Crafter (`SnippetCrafterStory.kt`), End-to-End App Journeys (`EndToEndJourneysStory.kt`)

## 4. Copying Reference Patterns Into Your App

When adopting `abeta-dev/kmp` in a real product app, copy directly from these clean reference blueprints:
1. **DI & Offline-First Wiring**: `kmp/sample-app/src/main/kotlin/dev/abeta/kmp/sample/SampleApp.kt` (`SampleFactory.create` wiring `CachedScreenRepository`, `OfflineActionCoordinator`, `KtorImageRenderer`, `PlatformBridgeBundle`, `observeConnectivity()`).
2. **Android Phone/Tablet/Foldable**: `kmp/platforms/android/AbetaAndroidHostActivity.kt` + `AndroidPlatformBridgeFactory.kt`.
3. **Android Wear OS**: `kmp/platforms/wearos/WearCompanionHostActivity.kt`.
4. **iOS / iPadOS & Apple Watch**: `kmp/platforms/apple/AbetaAppleHosts.swift`.
5. **macOS Native Cocoa Window**: `kmp/sdui-layout/src/macosMain/kotlin/dev/abeta/kmp/layout/macos/SduiMacOsWindow.kt` (`launchMacOsSduiWindow`).
