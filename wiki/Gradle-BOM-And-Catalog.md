# Gradle BOM & Catalog

`abeta-dev/kmp` uses two Gradle artifacts on Maven Central (Kotlin package namespace remains `dev.abeta.kmp.*`) to standardize versions across internal modules and external consumers:

1. **`io.github.abeta-dev:bom:0.3.0`** (`kmp/kmp-bom/build.gradle.kts`) — Java Platform BOM (`allowDependencies()` enabled) constraining all **11 publishable `io.github.abeta-dev:*` library modules** (including Room 3 `database`) + `dependencies` (13 Maven artifacts total).
2. **`io.github.abeta-dev:dependencies:0.3.0`** (`kmp/dependencyManagement/build.gradle.kts`) — Gradle Version Catalog generated from `kmp/dependencyManagement/gradle/libs.versions.toml`.

---

## Supported KMP Targets, Host Glue & Apple XCFrameworks

- **Headless / Contract Publishable Modules** (`core`, `state`, `analytics`, `database`, `sdui-contracts`, `action-engine`, `network`, `watch-companion`):
  - `jvm` (Java 21), `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`, `wasmJs`.
- **Compose UI Modules** (`design-tokens`, `ui-atoms`, `sdui-layout`) and **`:storybook`**:
  - `jvm` (Java 21), `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `wasmJs`.
- **`:sample-app`**: JVM Desktop & `--headless` CLI.
- **Drop-in Platform Glue (`kmp/platforms/`)**: Android (`android/AndroidPlatformBridgeFactory.kt`, `android/AndroidDatabaseFactory.kt`), Wear OS (`wearos/WearCompanionHostActivity.kt`), Apple (`apple/AbetaAppleHosts.swift`, `apple/Package.template.swift`), and Web COOP/COEP (`web/README.md`), documented in `kmp/platforms/README.md`. Android & Wear OS Gradle templates require an Android app module with `ANDROID_HOME` / Android SDK configured.

`sdui-layout`, `watch-companion`, and `storybook` build Apple XCFrameworks:

```bash
cd kmp
./gradlew :sdui-layout:assembleAbetaSduiKitXCFramework
./gradlew :watch-companion:assembleAbetaWatchKitXCFramework
./gradlew :storybook:assembleAbetaStorybookKitXCFramework
```

---

## Consumer Setup (Zero Auth Required)

Stable releases publish to **Maven Central** (`mavenCentral()`) and snapshots/mirrors publish to **Cloudsmith Open Source** (`dl.cloudsmith.io`) — completely free and open to download without consumer tokens. Apple XCFrameworks (`AbetaSduiKit`, `AbetaWatchKit`) and verified SwiftPM `Package.swift` manifests attach to GitHub Releases and mirror to `Abeta-dev/kmp-spm` (`from: "0.3.0"`); the Go BFF (`github.com/abeta-dev/kmp/bff@v0.5.0`) publishes automatically to `proxy.golang.org` on `bff/v*` tags. See `docs/publishing-and-hosting.md` and `docs/local-database-room3.md` in the repository for complete setup playbooks.

### `settings.gradle.kts`

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        // Optional — anonymous public snapshots / mirror via Cloudsmith Open Source:
        // maven("https://dl.cloudsmith.io/public/abeta-dev/kmp/maven/")
    }
    versionCatalogs {
        create("abetaLibs") {
            from("io.github.abeta-dev:dependencies:0.3.0")
        }
    }
}
```

### `build.gradle.kts`

```kotlin
commonMain.dependencies {
    implementation(project.dependencies.platform("io.github.abeta-dev:bom:0.3.0"))
    implementation("io.github.abeta-dev:database") // Optional Room 3 KMP SQLite local DB
    implementation("io.github.abeta-dev:design-tokens")
    implementation("io.github.abeta-dev:sdui-contracts")
    implementation("io.github.abeta-dev:ui-atoms")
    implementation("io.github.abeta-dev:action-engine")
    implementation("io.github.abeta-dev:network")
    implementation("io.github.abeta-dev:sdui-layout")
    // Optional headless wearable companion:
    // implementation("io.github.abeta-dev:watch-companion")
}
```

---

## Monorepo Local Substitution (`abeta.kmpLocalBuild`)

In `kmp/gradle.properties`:

```properties
abeta.kmpLocalBuild=true
```

When `true` (default), `kmp/settings.gradle.kts` substitutes all `io.github.abeta-dev:*` coordinates (including `database` and `watch-companion`) with their corresponding local Gradle projects (`:core`, `:database`, `:design-tokens`, `:watch-companion`, `:kmp-bom`, etc.).

To test resolution against published Maven artifacts:

```bash
cd kmp
./gradlew check -Pabeta.kmpLocalBuild=false
```

---

## Binary Compatibility Validator & Dokka v2

- **Binary Compatibility Validator (`0.18.2`)**: Applied to all **11 publishable KMP library modules** (`core`, `state`, `analytics`, `database`, `design-tokens`, `sdui-contracts`, `ui-atoms`, `action-engine`, `network`, `sdui-layout`, `watch-companion`) with `.api` baselines under `kmp/<module>/api/<module>.api` (including `kmp/database/api/database.api` and `kmp/watch-companion/api/watch-companion.api`). Run `./gradlew apiCheck` to verify ABI compatibility (automatically part of `check`) or `./gradlew apiDump` after intentional public API updates.
- **Dokka v2 HTML (`2.3.0`)**: Run `./gradlew :core:dokkaGenerateHtml` for a single module or `./gradlew dokkaGenerateHtml` across all KMP/JVM subprojects.

---

## Catalog Rules for Contributors

- External library/plugin versions belong exclusively in `kmp/dependencyManagement/gradle/libs.versions.toml`.
- Internal module versions belong exclusively in `kmp/dependencyManagement/gradle/internal.versions.toml`.
- Module `build.gradle.kts` files should reference `libs.*` or `internal.*` aliases—never inline version strings.
