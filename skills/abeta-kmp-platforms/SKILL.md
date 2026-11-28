---
name: abeta-kmp-platforms
description: Multi-OS platform guide for Android, Wear OS, iOS/iPadOS, watchOS, macOS, Desktop JVM (Windows/Linux), and WasmJS Web in abeta-dev/kmp. Covers Core OS bridges, Room 3 local SQLite drivers (sqlite-bundled & sqlite-web OPFS), Apple HMPP apple64Main vs watchosArm64Main split, Darwin/OkHttp TLS pinning, adaptive form factors, and /impeccable native UI guidelines.
---

# Abeta KMP Kit — Multi-OS Platforms & Bridges Skill (`abeta-kmp-platforms`)

Use this skill when working on platform-specific OS bridges (`kmp/core`, `kmp/network`), multiplatform Room 3 local SQLite databases (`kmp/database`), wearable companion apps (`kmp/watch-companion`), native host glue (`kmp/platforms/`), or adaptive form-factor UI (`kmp/design-tokens`, `kmp/sdui-layout`).

> **Mandatory Grounding Order**: `AGENTS.md` → `docs/architecture.md` → `docs/flows/wearable-and-apple-host-flow.md` → `docs/patterns/kmp-multiplatform-hmpp-and-abi-pattern.md` → `docs/codegraph/INDEX.md` (`./scripts/generate_codegraph.py --query <symbol>`) → Source Reads Only for Verification.

## 1. Platform & Target Matrix (`v0.8.0`)

| Platform / Form Factor | KMP Target / Artifact | Entrypoint / Host Reference | Core OS Bridges & Local Storage |
| :--- | :--- | :--- | :--- |
| **iOS & iPadOS** | `iosArm64`, `iosSimulatorArm64` (`AbetaSduiKit.xcframework` + `AbetaSduiSwiftUI`) | `SduiViewControllerKt.createSduiViewController` + `kmp/platforms/apple/Sources/AbetaSduiSwiftUI/` + SwiftPM `Abeta-dev/kmp-spm` (`0.8.0`) | `UIImpactFeedbackGenerator`, `UIPasteboard`, `UIApplication.openURL`, `UIActivityViewController`, `nw_path_monitor`, `AppleKeychain` (`SecItem*` w/ `NSLock`), `LAContext`, Darwin TLS SPKI pinning, Room 3 (`BundledSQLiteDriver`, schema v2 + `DatabaseKeyValueStore`) |
| **Apple Watch (`watchOS`)** | `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64` (`AbetaWatchKit.xcframework` + `AbetaWatchSwiftUI`) | `WatchAppleFactoryKt.createAppleWatchCompanionHost` + `AbetaWatchSduiView` (`kmp/platforms/apple/Sources/AbetaWatchSwiftUI/`) | `WKInterfaceDevice.playHaptic`, `nw_path_monitor`, `AppleKeychain` w/ `NSLock`, Darwin TLS pinning, headless `WatchCompanionController`, Room 3 (`BundledSQLiteDriver`, schema v2) |
| **macOS Desktop** | `macosArm64` (native Cocoa) + `jvm` | `launchMacOsSduiWindow` (`SduiMacOsWindow.kt`), `launchStorybookMacOsWindow` | `NSHapticFeedbackManager`, `NSPasteboard`, `NSWorkspace`, native AppKit `DesktopSystemPort` (`NSApp` dock badge / System Settings / attention), AppKit `NSOpenPanel` `FilePickerPort`, `nw_path_monitor`, `AppleKeychain`, `LAContext`, Darwin TLS pinning, Room 3 (`BundledSQLiteDriver`) |
| **Android Phone/Tablet/Foldable** | Native `android` `.aar` across all 11 publishable modules (`com.android.kotlin.multiplatform.library` `9.5.3`, `compileSdk = 37`, `minSdk = 24`) | `AbetaAndroidContext.initialize(applicationContext)` + optional `kmp/platforms/android/{AbetaAndroidHostActivity,AndroidPlatformBridgeFactory,AndroidDatabaseFactory}.kt` | Built-in `androidMain` actuals: `VibrationEffect`, `ClipboardManager`, `Intent.ACTION_VIEW`, chooser share, `ConnectivityManager`, `AndroidKeyStore` AES-GCM, `SharedPreferences`/DataStore, OkHttp SPKI pinning, `BitmapFactory`, Room 3 `androidMain` builder (`BundledSQLiteDriver` + `MIGRATION_1_2` + `DatabaseKeyValueStore`) |
| **Android Wear OS** | Native `android` `.aar` (`io.github.abeta-dev:watch-companion-android:0.8.0`) | `AbetaAndroidContext.initialize(applicationContext)` + `kmp/platforms/wearos/{WearCompanionHostActivity,WearSduiRenderer}.kt` | `WearHapticEngine`, native `androidMain` `SharedPreferences`/`AndroidKeyStore`/`ConnectivityManager` bridges, `WearSduiScreen`, `ScalingLazyColumn`, `PositionIndicator`, `Vignette`, `TimeText`, `WatchCompanionController`, Room 3 (`BundledSQLiteDriver`) |
| **Windows & Linux Desktop** | `jvm` (Java 21 Compose Desktop) | `kmp/sample-app` & `kmp/storybook` (`./gradlew :sample-app:run`) | Guarded AWT Clipboard/DesktopSystemPort, AWT `FileDialog` `FilePickerPort`, `javax.sound.sampled` 16 kHz PCM `AudioCapturePort` w/ live RMS telemetry, `Desktop.browse`, `NetworkInterface` probe, `0600`/`0700` file-backed AES-256-GCM, OkHttp HTTP/2 + Brotli + `CertificatePinner`, Room 3 (`BundledSQLiteDriver`) |
| **Web (WasmJS)** | `wasmJs` (Browser + Skiko) | `:storybook:wasmJsBrowserDevelopmentRun` / `wasmJsBrowserProductionWebpack` + `kmp/platforms/web/README.md` | `navigator.vibrate`, Clipboard API, `window.open`, `navigator.share`, `navigator.geolocation` `LocationPort`, `<input type="file">` `FilePickerPort`, `window.onLine`, `sessionStorage`, Room 3 (`WebWorkerSQLiteDriver` OPFS + COOP/COEP headers) |

## 2. Critical Apple HMPP Invariant (`apple64Main` vs `watchosArm64Main`)

Because `watchosArm64` uses Apple's 32-bit `arm64_32` ABI while `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosSimulatorArm64`, and `watchosDeviceArm64` are 64-bit LP64, **any Apple C/ObjC call using `NSInteger`, `NSUInteger`, `CFIndex`, or `intptr_t` MUST NOT live in `appleMain`** or HMPP metadata compilation (`compileAppleMainKotlinMetadata` / `dokkaGenerateHtml`) will fail.

Follow the established pattern in `kmp/core` and `kmp/network`:
1. Keep shared Apple Kotlin code in `appleMain` (`ConnectivityMonitor.apple.kt` uses `dispatch_queue_create`, `PlatformHttpEngine.apple.kt` uses `kotlin.io.encoding.Base64`).
2. Declare minimal `internal expect fun` shims in `appleMain` for bit-width-varying CInterop calls (`NSNumberFormatterCurrencyStyle`, `NSUTF8StringEncoding`, `CFDictionaryCreateMutable`, `LAContext.evaluatePolicy`, `SecTrustGetCertificateCount`, `NSURLSessionAuthChallenge*`).
3. Provide matching `internal actual fun` implementations in `apple64Main` (shared by `iosMain`, `macosMain`, `watchosSimulatorArm64Main`, `watchosDeviceArm64Main`) and `watchosArm64Main`.

## 3. Native Craft Checklist (`/impeccable` iOS + Android)

- **Touch Targets**: `>= 48.dp` on Android / `>= 44 pt` on iOS (`ThemeTokens.sizing.minTouchTarget` enforced on `UiButton`, clickable `UiIcon`, `UiStepper`, `UiSwitch`, `UiCheckbox`).
- **Safe Areas & Edge-to-Edge**: `SduiScreen` applies `WindowInsets.safeDrawing`, `statusBarsPadding()`, `navigationBarsPadding()`, and `imePadding()`.
- **System Appearances**: `SduiHostedRoot` and native host templates propagate OS Dark Mode and OS Reduce Motion into `DesignTheme(darkTheme, reducedMotion)`.
- **Accessibility**: Semantic `Role`, `contentDescription`, `sp` typography, and `LiveRegionMode.Polite` on offline & toast banners.
- **Adaptivity**: Breakpoints via `ResponsiveTokens.kt` (`COMPACT`, `MEDIUM`, `EXPANDED`, `LARGE`) drive `columnsByWidth`, `itemWidthByWidth`, and `SplitPaneBody`.

## 4. Local Database (Room 3.0.3) & SwiftPM References

- **Room 3 Multiplatform SQLite (`io.github.abeta-dev:database:0.8.0`)**: Uses `androidx.room3:room3-runtime:3.0.3` (schema **v2** w/ `MIGRATION_1_2`), `DatabaseKeyValueStore` (`ObservableKeyValueStore` opt-in adapter keeping `sdui-layout` Room-free by default), native `androidMain` builder (`AbetaAndroidContext`), `androidx.sqlite:sqlite-bundled:2.7.1` (Android/JVM/Apple), `androidx.sqlite:sqlite-web:2.7.1` (WasmJS OPFS Web Worker), and KSP (`2.3.14` across 9 targets incl. `kspAndroid`) with committed schema exports (`1.json`, `2.json`) in `kmp/database/schemas/`. Always follow [`docs/local-database-room3.md`](../../docs/local-database-room3.md) and [`agents/owners/database-owner.md`](../../agents/owners/database-owner.md).
- **WasmJS Browser Headers**: Web deployments using `WebWorkerSQLiteDriver` MUST serve `Cross-Origin-Opener-Policy: same-origin` and `Cross-Origin-Embedder-Policy: require-corp` (see `kmp/platforms/web/README.md`).
- **SwiftPM & Multi-Registry Distribution**: See [`docs/publishing-and-hosting.md`](../../docs/publishing-and-hosting.md) for Maven Central (`vars.ENABLE_MAVEN_CENTRAL=true`), Cloudsmith Open Source, and 4-product `Abeta-dev/kmp-spm` (`0.8.0`: `AbetaSduiKit`, `AbetaWatchKit`, `AbetaSduiSwiftUI`, `AbetaWatchSwiftUI`) Xcode integration.
