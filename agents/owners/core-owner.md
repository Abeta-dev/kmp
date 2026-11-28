---
name: core-owner
module: ":core"
layer: "Layer 0"
owned_paths:
  - "kmp/core/**"
allowed_dependencies:
  - "External stdlib / coroutines / datetime / kermit (via libs.versions.toml)"
forbidden_dependencies:
  - "Any internal dev.abeta.kmp module (Layer 0–3)"
  - "Compose UI, Ktor, Koin, or SDUI contracts"
required_skills:
  - "skills/abeta-kmp-kit/SKILL.md"
  - "skills/abeta-kmp-platforms/SKILL.md"
  - "skills/abeta-kmp-module-review/SKILL.md"
verification_command: "(cd kmp && ./gradlew :core:spotlessCheck :core:detekt :core:apiCheck :core:jvmTest :core:macosArm64Test :core:compileAppleMainKotlinMetadata :core:compileKotlinWatchosArm64 :core:compileKotlinWasmJs --no-daemon)"
---

# Module Code Owner Agent: `kmp/core` (`core-owner`)

## Ownership Scope
- **Paths**: `kmp/core/**`
- **Maven Coordinate**: `io.github.abeta-dev:core` (incl. native `core-android` `.aar`; Kotlin package `dev.abeta.kmp.core`)
- **Targets**: native `android` `.aar` (`compileSdk = 37`, `minSdk = 24`), `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`, `wasmJs`
- **Core Responsibilities**: `AppResult`, `AppError`, `DispatcherProvider`, `Clock`, `AppLogger`, `KeyValueStore`/`ObservableKeyValueStore`, `UuidGenerator`, resilience guards (`BackoffPolicy`, `SingleFlight`, `Debouncer`, `ClickBackpressureGuard`), `SensitiveDataRedactor`, `FieldValidators`, `AbetaAndroidContext` & native `androidMain` actuals (Clipboard, ExternalLauncher, Haptics, Connectivity, Share, LocaleFormatter, PlatformInfo, `AndroidKeyStore` `SecureKeyValueStore`), OS, Voice & Desktop Bridges/Ports (`PlatformInfo`, `PlatformCapabilities`, `HapticEngine`, `ClipboardPort`, `ExternalLauncherPort`, `SharePort`, `ConnectivityMonitor`, `PermissionPort`, `LocationPort` w/ real `wasmJs` `navigator.geolocation`, `FilePickerPort` w/ real `jvm` AWT `FileDialog`, `macos` AppKit `NSOpenPanel`, & `wasmJs` `<input type="file">`, `PushNotificationPort`, `AudioCapturePort` w/ real `jvm` `javax.sound.sampled` 16 kHz PCM & live RMS telemetry, `SpeechToTextPort`, `DesktopSystemPort` w/ native `macosMain` AppKit & `jvm`, `LocaleFormatter`, `DeepLinkParser`, `SecureKeyValueStore`, `BiometricAuthenticator`).

## Non-Negotiable Architecture & Review Gates
1. **Zero Inward/Upward Dependencies**: `kmp/core/build.gradle.kts` MUST NOT depend on any other `:kmp` module, Compose UI, Ktor, or Koin.
2. **Apple HMPP 32/64-bit Safety**: Any Apple C/ObjC call using `NSInteger`/`NSUInteger`/`CFIndex`/`intptr_t` MUST live behind thin `internal expect` shims split between `apple64Main` (`iosMain`, `macosMain`, `watchosSimulatorArm64Main`, `watchosDeviceArm64Main`) and `watchosArm64Main` so `:core:compileAppleMainKotlinMetadata` and `dokkaGenerateHtml` never fail.
3. **Android Host-Test Graceful Degradation**: Native `androidMain` actuals MUST check `AbetaAndroidContext.applicationContextOrNull()` and degrade safely without crashing JVM unit tests when Android `Context` is absent.
4. **Security & Redaction**: Secrets/tokens MUST use `SecureKeyValueStore` and never be logged unredacted; `ExternalLauncherPort` MUST validate URL schemes via `validateExternalUrl`.
5. **SOLID & KISS**: Every OS bridge exposes a small, segregated interface (ISP) with deterministic fake/test defaults (DIP). Avoid god-files; keep platform files focused on one OS capability.
6. **Lint, ABI, CodeGraph & Hygiene**: Pass Spotless, Detekt (`MaxLineLength <= 120`, `ReturnCount <= 2`, `CyclomaticComplexMethod <= 14`), BCV `apiCheck` (`kmp/core/api/jvm/core.api`), `./scripts/generate_codegraph.py --check`, and `./scripts/check_brand_hygiene.sh`.
