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
- **Core Responsibilities**: Drop-in native host templates for Android Phone/Tablet/Foldable (`AndroidPlatformBridgeFactory.kt`, `AndroidDatabaseFactory.kt`, `AbetaAndroidHostActivity.kt` calling `AbetaAndroidContext.initialize(applicationContext)` on top of native `*-android` `.aar` modules at `0.8.0`), Wear OS (`WearCompanionHostActivity.kt`, `WearSduiRenderer.kt`), Apple iOS/iPadOS/watchOS (`AbetaAppleHosts.swift` + importable SwiftPM source targets `Sources/AbetaSduiSwiftUI/` & `Sources/AbetaWatchSwiftUI/` powering 4-product `Abeta-dev/kmp-spm` `0.8.0`, verified with Swift 5 & 6 `-typecheck` via `verify_spm_package.sh`), and Web COOP/COEP (`web/README.md`).

## Non-Negotiable Architecture & Review Gates
1. **Native Platform Conformance (`/impeccable` `ios.md` & `android.md`)**:
   - **Android**: Call `AbetaAndroidContext.initialize(applicationContext)` once at startup so native `androidMain` actuals activate; enforce edge-to-edge (`enableEdgeToEdge()`), predictive Back (`OnBackPressedCallback`), Material 3 / dark mode & reduced-motion propagation, `AndroidKeyStore` AES-GCM, `BiometricPrompt`, `ConnectivityManager`, `VibrationEffect`.
   - **Wear OS**: `ScalingLazyColumn` + `ScalingLazyListState`, `PositionIndicator`, `Vignette`, `TimeText`, disabled state on non-actionable `Chip`s, `WearHapticEngine`.
   - **Apple (iOS/iPadOS/watchOS)**: SwiftUI `NavigationStack` / safe-area awareness, Dynamic Type text styles, semantic colors, Swift 5 & 6 strict concurrency cleanliness (`verify_spm_package.sh`), `AbetaSduiKit.xcframework` & `AbetaWatchKit.xcframework` lifecycle cleanup (`holder.close()`).
2. **Thin Host Glue (KISS)**: Native templates MUST ONLY bind OS capabilities to `PlatformBridgeBundle` / `SduiScreen` / `WatchCompanionController`—never duplicate SDUI rendering or BFF parsing logic.
3. **Honest SDK & Verification Discipline**: All 11 publishable KMP modules compile native `-android` `.aar` targets against Android SDK 37 (`compileSdk = 37`, `minSdk = 24`); `kmp/platforms/{android,wearos}` app host templates remain copy-in reference files outside `settings.gradle.kts`, and SwiftPM sources must pass `./scripts/generate_codegraph.py --check` and `verify_spm_package.sh`.
