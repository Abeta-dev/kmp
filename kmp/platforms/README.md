# Platform Host Bridges (`kmp/platforms/`)

Reference OS host glue, importable SwiftUI packages, modular Wear OS renderers, local persistence helpers, and native distribution templates for targets whose proprietary toolkits or browser headers reside outside pure Kotlin/Compose Multiplatform Gradle compilation on non-Android/non-macOS machines:

- **`android/`**: Android phone, tablet & foldable `FragmentActivity` bridge, host & persistence templates:
  - `build.gradle.template.kts` demonstrating Google's modern `com.android.kotlin.multiplatform.library` plugin (`android { namespace = "dev.abeta.kmp.android"; compileSdk = 37; minSdk = 24 }` on AGP 9.5.3), `io.github.abeta-dev:bom:0.8.0`, official Google AndroidX KMP `androidx.lifecycle:*`, `androidx.savedstate`, and `androidx.datastore:datastore-preferences-core`.
  - `AndroidPlatformBridgeFactory.kt` covering `VibrationEffect` haptics, `ClipboardManager`, `Intent.ACTION_VIEW`, chooser share, `ConnectivityManager` callbacks, AES-GCM `AndroidKeyStore`, `BiometricPrompt`, `ActivityResultContracts` runtime permissions & document picker, and push token hook.
  - `AbetaAndroidHostActivity.kt` demonstrating `enableEdgeToEdge()`, predictive Back `OnBackPressedCallback`, lifecycle-aware `collectAsStateWithLifecycle()`, dark-mode & reduced-motion `DesignTheme` propagation, deep links, and `SduiScreen`.
  - `AndroidDatabaseFactory.kt` wiring **Room 3** with `BundledSQLiteDriver` for Android phone/tablet/foldable and Wear OS hosts.
  - Requires Android SDK (`ANDROID_HOME`, `compileSdk >= 35`) inside your Android Gradle consumer module.
- **`wearos/`**: Modular Android Wear OS Compose renderer + Tile/Glance mapper:
  - `WearSduiRenderer.kt` providing reusable Wear Compose components (`WearSduiScreen`, `WearGlanceContent`, `WearMetricChip`, `WearActionCardChip`, `WearOfflineChip`, `WearToastChip`) alongside `WearTileSnapshot` and `WearTileSnapshotMapper.map(page)` for Wear OS Tiles, Jetpack Glance widgets, and watch-face complications.
  - `WearCompanionHostActivity.kt` wiring `:watch-companion`'s `WatchCompanionController` + `WearHapticEngine` cleanly into `WearSduiScreen`.
  - Pairs with `android/AndroidDatabaseFactory.kt` for Room 3 + `BundledSQLiteDriver` offline persistence. Requires Android SDK (`ANDROID_HOME`) + Wear Compose dependencies.
- **`apple/`**: Ready-made importable SwiftPM SwiftUI packages, single-file reference hosts, and Direct-URL Swift Package Manager templates:
  - `Sources/AbetaWatchSwiftUI/AbetaWatchSwiftUI.swift` (SwiftPM library **`AbetaWatchSwiftUI`** for watchOS 9+): exports `AbetaWatchTheme`, `@MainActor AbetaWatchViewModel` (with `AsyncStream` observation, `refresh()`, `refreshAsync()`, `loadScreen(_:)`, `trigger(_:)`, `dismissToast()`), `AbetaWatchSduiView`, `AbetaWatchMetricRow`, and `AbetaWatchActionCardView` wrapping `AbetaWatchKit`.
  - `Sources/AbetaSduiSwiftUI/AbetaSduiSwiftUI.swift` (SwiftPM library **`AbetaSduiSwiftUI`** for iOS/iPadOS 15+): exports `AbetaIosSduiScreenView` (`UIViewControllerRepresentable`) and `AbetaIosRootAppView` (`NavigationStack` + `.onOpenURL` deep-link routing) wrapping `AbetaSduiKit`.
  - `AbetaAppleHosts.swift`: combined single-file reference copy for teams vendoring Swift source manually.
  - `Package.template.swift`: 4-product Direct-URL SwiftPM manifest (`AbetaSduiKit`, `AbetaWatchKit`, `AbetaWatchSwiftUI`, `AbetaSduiSwiftUI`) generated per release by `scripts/package_xcframeworks.sh`, verified by `scripts/verify_spm_package.sh`, uploaded to GitHub Releases (alongside `AbetaSwiftUI-Sources-<TAG>.zip`), and synced with `Sources/` to `https://github.com/Abeta-dev/kmp-spm`.
- **`web/`**: WasmJS browser persistence guide (`web/README.md`) documenting `WebWorkerSQLiteDriver` backed by the Origin Private File System (OPFS) and the required cross-origin isolation HTTP headers (`Cross-Origin-Opener-Policy: same-origin` and `Cross-Origin-Embedder-Policy: require-corp`).

---

## Quick Usage Snippets

### watchOS SwiftUI (`import AbetaWatchSwiftUI`)

Add `https://github.com/Abeta-dev/kmp-spm` in Xcode and link **`AbetaWatchSwiftUI`** to your watchOS 9+ target:

```swift
import SwiftUI
import AbetaWatchSwiftUI

@main
struct MyWatchApp: App {
    private let theme = AbetaWatchTheme(
        accentColor: .mint,
        cardCornerRadius: 12
    )

    var body: some Scene {
        WindowGroup {
            AbetaWatchSduiView(
                bffBaseUrl: "https://bff.example.com",
                initialScreenId: "watch-status",
                theme: theme
            )
        }
    }
}
```

### iOS / iPadOS SwiftUI (`import AbetaSduiSwiftUI`)

Link **`AbetaSduiSwiftUI`** to your iOS/iPadOS 15+ target for deep-link-aware SDUI hosting:

```swift
import SwiftUI
import AbetaSduiSwiftUI

@main
struct MyIosApp: App {
    var body: some Scene {
        WindowGroup {
            AbetaIosRootAppView(
                initialScreenId: "store-home",
                bffBaseUrl: "https://bff.example.com"
            )
        }
    }
}
```

### Wear OS Compose & Tile / Glance Mapper (`WearSduiRenderer` + `WearTileSnapshotMapper`)

```kotlin
import dev.abeta.kmp.platforms.wearos.WearSduiScreen
import dev.abeta.kmp.platforms.wearos.WearTileSnapshotMapper
import dev.abeta.kmp.watch.WatchCompanionController
import dev.abeta.kmp.watch.WatchGlancePage

// 1. Inside a Wear OS Compose Activity / Screen:
@Composable
fun DeliveryWatchScreen(controller: WatchCompanionController) {
    WearSduiScreen(controller = controller)
}

// 2. Inside a Wear OS TileService, Glance widget, or Complication provider:
fun buildTileData(page: WatchGlancePage) {
    val snapshot = WearTileSnapshotMapper.map(page)
    val headline = snapshot.primaryMetric?.let { "${it.label}: ${it.value}" } ?: snapshot.title
    val secondarySlots = snapshot.secondaryMetrics // up to 2 secondary glance metrics
    val tapDeepLinkUri = snapshot.primaryActionDeepLinkUri // e.g. "abeta://screens/watch-status?action=watch_reorder"
}
```
