# Gradle BOM & Catalog

`abeta-dev/kmp` uses two Gradle artifacts to standardize versions across internal modules and external consumers:

1. **`dev.abeta.kmp:bom:0.1.0`** (`kmp/kmp-bom/build.gradle.kts`) — Java Platform BOM (`allowDependencies()` enabled) constraining all **10 publishable `dev.abeta.kmp:*` library modules** + `dependencies`.
2. **`dev.abeta.kmp:dependencies:0.1.0`** (`kmp/dependencyManagement/build.gradle.kts`) — Gradle Version Catalog generated from `kmp/dependencyManagement/gradle/libs.versions.toml`.

---

## Supported KMP Targets, Host Glue & Apple XCFrameworks

- **Headless / Contract Publishable Modules** (`core`, `state`, `analytics`, `sdui-contracts`, `action-engine`, `network`, `watch-companion`):
  - `jvm` (Java 21), `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`, `wasmJs`.
- **Compose UI Modules** (`design-tokens`, `ui-atoms`, `sdui-layout`) and **`:storybook`**:
  - `jvm` (Java 21), `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `wasmJs`.
- **`:sample-app`**: JVM Desktop & `--headless` CLI.
- **Drop-in Platform Glue (`kmp/platforms/`)**: Android (`android/AndroidPlatformBridgeFactory.kt`), Wear OS (`wearos/WearCompanionHostActivity.kt`), and Apple (`apple/AbetaAppleHosts.swift`), documented in `kmp/platforms/README.md`. Android & Wear OS Gradle templates require an Android app module with `ANDROID_HOME` / Android SDK configured.

`sdui-layout`, `watch-companion`, and `storybook` build Apple XCFrameworks:

```bash
cd kmp
./gradlew :sdui-layout:assembleAbetaSduiKitXCFramework
./gradlew :watch-companion:assembleAbetaWatchKitXCFramework
./gradlew :storybook:assembleAbetaStorybookKitXCFramework
```

---

## Consumer Setup

### `settings.gradle.kts`

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.github.com/abeta-dev/kmp")
    }
    versionCatalogs {
        create("abetaLibs") {
            from("dev.abeta.kmp:dependencies:0.1.0")
        }
    }
}
```

### `build.gradle.kts`

```kotlin
commonMain.dependencies {
    implementation(project.dependencies.platform("dev.abeta.kmp:bom:0.1.0"))
    implementation("dev.abeta.kmp:design-tokens")
    implementation("dev.abeta.kmp:sdui-contracts")
    implementation("dev.abeta.kmp:ui-atoms")
    implementation("dev.abeta.kmp:action-engine")
    implementation("dev.abeta.kmp:network")
    implementation("dev.abeta.kmp:sdui-layout")
    // Optional headless wearable companion:
    // implementation("dev.abeta.kmp:watch-companion")
}
```

---

## Monorepo Local Substitution (`abeta.kmpLocalBuild`)

In `kmp/gradle.properties`:

```properties
abeta.kmpLocalBuild=true
```

When `true` (default), `kmp/settings.gradle.kts` substitutes all `dev.abeta.kmp:*` coordinates (including `watch-companion`) with their corresponding local Gradle projects (`:core`, `:design-tokens`, `:watch-companion`, `:kmp-bom`, etc.).

To test resolution against published Maven artifacts:

```bash
cd kmp
./gradlew check -Pabeta.kmpLocalBuild=false
```

---

## Binary Compatibility Validator & Dokka v2

- **Binary Compatibility Validator (`0.18.2`)**: Applied to all **10 publishable KMP library modules** (`core`, `state`, `analytics`, `design-tokens`, `sdui-contracts`, `ui-atoms`, `action-engine`, `network`, `sdui-layout`, `watch-companion`) with `.api` baselines under `kmp/<module>/api/<module>.api` (including `kmp/watch-companion/api/watch-companion.api`). Run `./gradlew apiCheck` to verify ABI compatibility (automatically part of `check`) or `./gradlew apiDump` after intentional public API updates.
- **Dokka v2 HTML (`2.3.0`)**: Run `./gradlew :core:dokkaGenerateHtml` for a single module or `./gradlew dokkaGenerateHtml` across all KMP/JVM subprojects.

---

## Catalog Rules for Contributors

- External library/plugin versions belong exclusively in `kmp/dependencyManagement/gradle/libs.versions.toml`.
- Internal module versions belong exclusively in `kmp/dependencyManagement/gradle/internal.versions.toml`.
- Module `build.gradle.kts` files should reference `libs.*` or `internal.*` aliases—never inline version strings.
