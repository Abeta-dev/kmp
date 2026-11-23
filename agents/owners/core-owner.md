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
- **Maven Coordinate**: `io.github.abeta-dev:core` (Kotlin package `dev.abeta.kmp.core`)
- **Targets**: `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`, `wasmJs`
- **Core Responsibilities**: `AppResult`, `AppError`, `DispatcherProvider`, `Clock`, `AppLogger`, `KeyValueStore`, `UuidGenerator`, resilience guards (`BackoffPolicy`, `SingleFlight`, `Debouncer`, `ClickBackpressureGuard`), `SensitiveDataRedactor`, `FieldValidators`, OS Bridges & Ports (`PlatformInfo`, `PlatformCapabilities`, `HapticEngine`, `ClipboardPort`, `ExternalLauncherPort`, `SharePort`, `ConnectivityMonitor`, `PermissionPort`, `LocationPort`, `FilePickerPort`, `PushNotificationPort`, `LocaleFormatter`, `DeepLinkParser`, `SecureKeyValueStore`, `BiometricAuthenticator`).

## Non-Negotiable Architecture & Review Gates
1. **Zero Inward/Upward Dependencies**: `kmp/core/build.gradle.kts` MUST NOT depend on any other `:kmp` module, Compose UI, Ktor, or Koin.
2. **Apple HMPP 32/64-bit Safety**: Any Apple C/ObjC call using `NSInteger`/`NSUInteger`/`CFIndex`/`intptr_t` MUST live behind thin `internal expect` shims split between `apple64Main` (`iosMain`, `macosMain`, `watchosSimulatorArm64Main`, `watchosDeviceArm64Main`) and `watchosArm64Main` so `:core:compileAppleMainKotlinMetadata` and `dokkaGenerateHtml` never fail.
3. **Security & Redaction**: Secrets/tokens MUST use `SecureKeyValueStore` and never be logged unredacted; `ExternalLauncherPort` MUST validate URL schemes via `validateExternalUrl`.
4. **SOLID & KISS**: Every OS bridge exposes a small, segregated interface (ISP) with deterministic fake/test defaults (DIP). Avoid god-files; keep platform files focused on one OS capability.
5. **Lint, ABI & Hygiene**: Pass Spotless, Detekt (`MaxLineLength <= 120`, `ReturnCount <= 2`, `CyclomaticComplexMethod <= 14`), BCV `apiCheck` (`kmp/core/api/core.api`), and `./scripts/check_brand_hygiene.sh`.
