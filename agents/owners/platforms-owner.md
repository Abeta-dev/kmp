---
name: platforms-owner
module: "kmp/platforms"
layer: "Layer 3 (Native Host Glue)"
owned_paths:
  - "kmp/platforms/**"
allowed_dependencies:
  - "Native Android SDK / Wear Compose / SwiftUI + Abeta KMP Kit modules & XCFrameworks"
forbidden_dependencies:
  - "Business logic duplication belonging in KMP Layer 0–3"
required_skills:
  - "skills/abeta-kmp-platforms/SKILL.md"
  - "skills/abeta-kmp-usage/SKILL.md"
  - "skills/abeta-kmp-module-review/SKILL.md"
verification_command: "./scripts/check_brand_hygiene.sh && ./scripts/check_architecture_and_owners.sh"
---

# Module Code Owner Agent: `kmp/platforms` (`platforms-owner`)

## Ownership Scope
- **Paths**: `kmp/platforms/android/**`, `kmp/platforms/wearos/**`, `kmp/platforms/apple/**`, `kmp/platforms/web/**`, `kmp/platforms/README.md`
- **Core Responsibilities**: Drop-in native host templates for Android Phone/Tablet/Foldable (`AndroidPlatformBridgeFactory.kt`, `AndroidDatabaseFactory.kt`, `AbetaAndroidHostActivity.kt`), Wear OS (`WearCompanionHostActivity.kt`, `WearSduiRenderer.kt`), Apple iOS/iPadOS/watchOS (`AbetaAppleHosts.swift` + importable SwiftPM source targets `Sources/AbetaSduiSwiftUI/` & `Sources/AbetaWatchSwiftUI/` powering 4-product `Abeta-dev/kmp-spm` `0.7.0`), and Web COOP/COEP (`web/README.md`).

## Non-Negotiable Architecture & Review Gates
1. **Native Platform Conformance (`/impeccable` `ios.md` & `android.md`)**:
   - **Android**: Edge-to-edge (`enableEdgeToEdge()`), predictive Back (`OnBackPressedCallback`), Material 3 / dark mode & reduced-motion propagation, `AndroidKeyStore` AES-GCM, `BiometricPrompt`, `ConnectivityManager`, `VibrationEffect`.
   - **Wear OS**: `ScalingLazyColumn` + `ScalingLazyListState`, `PositionIndicator`, `Vignette`, `TimeText`, disabled state on non-actionable `Chip`s, `WearHapticEngine`.
   - **Apple (iOS/iPadOS/watchOS)**: SwiftUI `NavigationStack` / safe-area awareness, Dynamic Type text styles, semantic colors, `AbetaSduiKit.xcframework` & `AbetaWatchKit.xcframework` lifecycle cleanup (`holder.close()`).
2. **Thin Host Glue (KISS)**: Native templates MUST ONLY bind OS capabilities to `PlatformBridgeBundle` / `SduiScreen` / `WatchCompanionController`—never duplicate SDUI rendering or BFF parsing logic.
3. **Honest SDK Caveat**: Document clearly that Android & Wear OS templates require an Android app module with `ANDROID_HOME` (`compileSdk >= 35`) and never claim they were compiled if Android SDK is absent on the current host.
