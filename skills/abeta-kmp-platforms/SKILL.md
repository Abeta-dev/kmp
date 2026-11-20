---
name: abeta-kmp-platforms
description: Multi-OS platform guide for Android, Wear OS, iOS/iPadOS, watchOS, macOS, Desktop JVM (Windows/Linux), and WasmJS Web in abeta-dev/kmp. Covers Core OS bridges, Apple HMPP apple64Main vs watchosArm64Main split, Darwin/OkHttp TLS pinning, adaptive form factors, and /impeccable native UI guidelines.
---

# Abeta KMP Kit — Multi-OS Platforms & Bridges Skill (`abeta-kmp-platforms`)

Use this skill when working on platform-specific OS bridges (`kmp/core`, `kmp/network`), wearable companion apps (`kmp/watch-companion`), native host glue (`kmp/platforms/`), or adaptive form-factor UI (`kmp/design-tokens`, `kmp/sdui-layout`).

## 1. Platform & Target Matrix

| Platform / Form Factor | KMP Target / Artifact | Entrypoint / Host Reference | Core OS Bridges |
| :--- | :--- | :--- | :--- |
| **iOS & iPadOS** | `iosArm64`, `iosSimulatorArm64` (`AbetaSduiKit.xcframework`) | `SduiViewControllerKt.createSduiViewController` + `kmp/platforms/apple/AbetaAppleHosts.swift` | `UIImpactFeedbackGenerator`, `UIPasteboard`, `UIApplication.openURL`, `UIActivityViewController`, `nw_path_monitor`, `AppleKeychain` (`SecItem*`), `LAContext`, Darwin TLS SPKI pinning |
| **Apple Watch (`watchOS`)** | `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64` (`AbetaWatchKit.xcframework`) | `WatchAppleFactoryKt.createAppleWatchCompanionHost` + `AbetaWatchSduiView` (`kmp/platforms/apple/AbetaAppleHosts.swift`) | `WKInterfaceDevice.playHaptic`, `nw_path_monitor`, `AppleKeychain`, Darwin TLS pinning, headless `WatchCompanionController` |
| **macOS Desktop** | `macosArm64` (native Cocoa) + `jvm` | `launchMacOsSduiWindow` (`SduiMacOsWindow.kt`), `launchStorybookMacOsWindow` | `NSHapticFeedbackManager`, `NSPasteboard`, `NSWorkspace`, `nw_path_monitor`, `AppleKeychain`, `LAContext`, Darwin TLS pinning |
| **Android Phone/Tablet/Foldable** | `jvm` / Android host module (`compileSdk >= 35`, `ANDROID_HOME`) | `kmp/platforms/android/AbetaAndroidHostActivity.kt` + `AndroidPlatformBridgeFactory.kt` | `VibrationEffect`, `ClipboardManager`, `Intent.ACTION_VIEW`, chooser share, `ConnectivityManager`, AES-GCM `AndroidKeyStore`, `BiometricPrompt`, `enableEdgeToEdge()`, predictive Back |
| **Android Wear OS** | `jvm` / Wear OS host module (`androidx.wear.compose`) | `kmp/platforms/wearos/WearCompanionHostActivity.kt` + `:watch-companion` | `WearHapticEngine`, `ScalingLazyColumn`, `PositionIndicator`, `Vignette`, `TimeText`, `WatchCompanionController` |
| **Windows & Linux Desktop** | `jvm` (Java 21 Compose Desktop) | `kmp/sample-app` & `kmp/storybook` (`./gradlew :sample-app:run`) | AWT Clipboard (headless-safe), `Desktop.browse`, `NetworkInterface` probe, AES-256-GCM + `Preferences`, OkHttp HTTP/2 + Brotli + `CertificatePinner` |
| **Web (WasmJS)** | `wasmJs` (Browser + Skiko) | `:storybook:wasmJsBrowserDevelopmentRun` / `wasmJsBrowserProductionWebpack` | `navigator.vibrate`, Clipboard API, `window.open`, `navigator.share`, `window.onLine`, `sessionStorage` |

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
