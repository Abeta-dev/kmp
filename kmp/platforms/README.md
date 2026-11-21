# Platform Host Bridges (`kmp/platforms/`)

Reference OS host glue, local persistence helpers, and native distribution templates for targets whose proprietary toolkits or browser headers reside outside pure Kotlin/Compose Multiplatform Gradle compilation on non-Android/non-macOS machines:

- **`android/`**: Android phone, tablet & foldable `FragmentActivity` bridge, host & persistence templates:
  - `AndroidPlatformBridgeFactory.kt` covering `VibrationEffect` haptics, `ClipboardManager`, `Intent.ACTION_VIEW`, chooser share, `ConnectivityManager` callbacks, AES-GCM `AndroidKeyStore`, `BiometricPrompt`, `ActivityResultContracts` runtime permissions & document picker, and push token hook.
  - `AbetaAndroidHostActivity.kt` demonstrating `enableEdgeToEdge()`, predictive Back `OnBackPressedCallback`, dark-mode & reduced-motion `DesignTheme` propagation, deep links, and `SduiScreen`.
  - `AndroidDatabaseFactory.kt` wiring **Room 3** with `BundledSQLiteDriver` for Android phone/tablet/foldable and Wear OS hosts.
  - Requires Android SDK (`ANDROID_HOME`, `compileSdk >= 35`) inside your Android Gradle consumer module.
- **`wearos/`**: Android Wear OS `ComponentActivity` host (`WearCompanionHostActivity.kt`) wiring `:watch-companion`'s `WatchCompanionController` + `WatchGlancePage` into Wear Compose Material (`ScalingLazyColumn`, `PositionIndicator`, `Vignette`, `TimeText`, disabled non-actionable `Chip`s, and `WearHapticEngine`). Pairs with `android/AndroidDatabaseFactory.kt` for Room 3 + `BundledSQLiteDriver` offline persistence. Requires Android SDK (`ANDROID_HOME`) + Wear Compose dependencies.
- **`apple/`**: SwiftUI reference hosts and Direct-URL Swift Package Manager templates:
  - `AbetaAppleHosts.swift` for **iOS / iPadOS** (`AbetaIosRootAppView`, `AbetaIosSduiScreenView` wrapping `AbetaSduiKit` with `.onOpenURL` and safe-area support), **macOS Cocoa** (`launchMacOsSduiWindow`), and **watchOS** (`AbetaWatchViewModel` + `AbetaWatchSduiView` wrapping `AbetaWatchKit` `createAppleWatchCompanionHost` with Dynamic Type & semantic colors).
  - `Package.template.swift` showing the Direct-URL SwiftPM binary XCFramework manifest generated per release by `scripts/package_xcframeworks.sh`, verified by `scripts/verify_spm_package.sh`, uploaded to GitHub Releases, and synced to the SwiftPM mirror repository (`AbetaSduiKit` for iOS/iPadOS 15+, `AbetaWatchKit` for watchOS 9+).
- **`web/`**: WasmJS browser persistence guide (`web/README.md`) documenting `WebWorkerSQLiteDriver` backed by the Origin Private File System (OPFS) and the required cross-origin isolation HTTP headers (`Cross-Origin-Opener-Policy: same-origin` and `Cross-Origin-Embedder-Policy: require-corp`).
