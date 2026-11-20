# Gradle BOM & Version Catalog Guide

`abeta-dev/kmp` publishes both a **Java Platform Bill of Materials (`dev.abeta.kmp:bom`)** and a **Gradle Version Catalog (`dev.abeta.kmp:dependencies`)** to ensure every consumer and subproject resolves aligned, conflict-free versions.

---

## Published Coordinates & Supported Targets

| Artifact | Coordinate | Source Module | Purpose |
| :--- | :--- | :--- | :--- |
| **BOM** | `dev.abeta.kmp:bom:0.1.0` | `:kmp-bom` (`kmp/kmp-bom/build.gradle.kts`) | Aligns versions across all **10 publishable `dev.abeta.kmp:*` library modules** + `dependencies` |
| **Version Catalog** | `dev.abeta.kmp:dependencies:0.1.0` | `:dependencyManagement` (`kmp/dependencyManagement/build.gradle.kts`) | Exposes external toolchain & library versions (`libs.versions.toml`) |

### Modules Included in `dev.abeta.kmp:bom`

From `kmp/kmp-bom/build.gradle.kts`:

- **Layer 0**: `dev.abeta.kmp:core`, `dev.abeta.kmp:state`, `dev.abeta.kmp:analytics`, `dev.abeta.kmp:dependencies`
- **Layer 1**: `dev.abeta.kmp:design-tokens`, `dev.abeta.kmp:sdui-contracts`, `dev.abeta.kmp:ui-atoms`
- **Layer 2**: `dev.abeta.kmp:action-engine`, `dev.abeta.kmp:network`
- **Layer 3**: `dev.abeta.kmp:sdui-layout`, `dev.abeta.kmp:watch-companion`

> Note: `:sample-app` and `:storybook` are explorer/demo applications and are intentionally **not** part of `publishableModules` or the BOM.

### Supported Multiplatform Targets & Apple XCFrameworks

- **Headless / Contract Publishable Modules** (`core`, `state`, `analytics`, `sdui-contracts`, `action-engine`, `network`, `watch-companion`):
  - `jvm` (Java 21), `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`, `wasmJs`.
- **Compose UI Modules** (`design-tokens`, `ui-atoms`, `sdui-layout`) and **`:storybook`**:
  - `jvm` (Java 21), `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `wasmJs`.
- **`:sample-app`**: JVM Desktop & `--headless` CLI.
- **Drop-in Platform Glue (`kmp/platforms/`)**: Reference Android (`android/AndroidPlatformBridgeFactory.kt`), Wear OS (`wearos/WearCompanionHostActivity.kt`), and Apple SwiftUI (`apple/AbetaAppleHosts.swift`) hosts (`kmp/platforms/README.md`). Android & Wear OS templates require an Android app module with `ANDROID_HOME` / Android SDK configured.

`sdui-layout`, `watch-companion`, and `storybook` configure Apple XCFrameworks:

```bash
cd kmp
./gradlew :sdui-layout:assembleAbetaSduiKitXCFramework
./gradlew :watch-companion:assembleAbetaWatchKitXCFramework
./gradlew :storybook:assembleAbetaStorybookKitXCFramework
```

---

## Consuming in an External KMP Project

### 1. Configure Repositories & Catalog (`settings.gradle.kts`)

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven {
            url = uri("https://maven.pkg.github.com/abeta-dev/kmp")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_USER")).orNull
                password = providers.gradleProperty("gpr.token")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
            }
        }
    }

    versionCatalogs {
        create("abetaLibs") {
            from("dev.abeta.kmp:dependencies:0.1.0")
        }
    }
}
```

### 2. Import the BOM & Select Only the Modules You Need (`build.gradle.kts`)

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project.dependencies.platform("dev.abeta.kmp:bom:0.1.0"))

            // Pick your required layers—no version numbers needed
            implementation("dev.abeta.kmp:design-tokens")
            implementation("dev.abeta.kmp:sdui-contracts")
            implementation("dev.abeta.kmp:ui-atoms")
            implementation("dev.abeta.kmp:action-engine")
            implementation("dev.abeta.kmp:network")
            implementation("dev.abeta.kmp:sdui-layout")
            // Or for watchOS / Wear OS headless companions:
            // implementation("dev.abeta.kmp:watch-companion")
        }
    }
}
```

---

## Internal Monorepo Catalog Structure

Inside `kmp/dependencyManagement/gradle/`:

1. **`libs.versions.toml`** (published as `dev.abeta.kmp:dependencies`):
   - Core Toolchain: Kotlin `2.4.21`, Compose Multiplatform `1.12.2`, Compose Material3 `1.12.0-alpha03`, Compose Lifecycle `2.11.0`, Compose Navigation `2.10.0`
   - KotlinX: Coroutines `1.11.0`, Serialization `1.13.0`, DateTime `0.8.0`, Collections Immutable `0.5.2`
   - Networking, DI, Compression & Persistence: Ktor `3.6.1` (`ktor-bom`, `ktor-common` bundle, `ktor-client-auth`, `ktor-client-encoding`, `ktor-client-okhttp`, `ktor-client-darwin`, `ktor-client-js`, `ktor-client-mock`), Brotli `0.1.2` (`org.brotli:dec`), Koin `4.2.2` (`koin-core`), Kermit `2.2.0`, Multiplatform Settings `1.3.0`, Turbine `1.2.1`, `compose.ui.test`
   - Quality, Docs & Publishing Plugins: Spotless `8.11.0`, Detekt `2.0.0-alpha.6`, Dokka `2.3.0`, Vanniktech Maven Publish `0.37.0`, Binary Compatibility Validator `0.18.2`
2. **`internal.versions.toml`** (used internally by `:kmp-bom` and inter-module references):
   - Defines `kmpBom`, `kmpDependencies`, and per-module version refs (`0.1.0`) across all **10 publishable library modules** (`core`, `state`, `analytics`, `designTokens`, `sduiContracts`, `uiAtoms`, `actionEngine`, `network`, `sduiLayout`, `watchCompanion`) under the `internal` catalog alias.

---

## ABI Validation (`Binary Compatibility Validator`) & Dokka v2

### Binary Compatibility Validator (`0.18.2`)
Applied to all **10 publishable KMP library modules** (`core`, `state`, `analytics`, `design-tokens`, `sdui-contracts`, `ui-atoms`, `action-engine`, `network`, `sdui-layout`, `watch-companion`). Golden `.api` descriptors live under `kmp/<module>/api/<module>.api` (including `kmp/watch-companion/api/watch-companion.api`).

```bash
cd kmp

# Verify public ABI matches committed .api files (also runs during ./gradlew check)
./gradlew apiCheck

# Regenerate .api descriptors after an intentional public API change
./gradlew apiDump
```

### Dokka v2 HTML Documentation (`2.3.0`)
Applied to all KMP and JVM subprojects using Dokka v2 tasks:

```bash
cd kmp

# Generate HTML docs for a single module (e.g., core)
./gradlew :core:dokkaGenerateHtml

# Generate HTML docs across all KMP/JVM modules
./gradlew dokkaGenerateHtml
```

---

## Local Build Substitution (`abeta.kmpLocalBuild`)

`kmp/gradle.properties` sets `abeta.kmpLocalBuild=true` by default. In `kmp/settings.gradle.kts`:

- When `abeta.kmpLocalBuild=true`, Gradle intercepts every `dev.abeta.kmp:<module>` coordinate across all subprojects and substitutes it with the local `project(":<module>")`.
- This means `:sample-app`, `:storybook`, `:watch-companion`, or `:sdui-layout` can declare `implementation(internal.kmp.uiAtoms)` or Maven coordinates while always compiling against local workspace edits.
- To verify resolution against published Maven artifacts instead of local projects:

```bash
cd kmp
./gradlew check -Pabeta.kmpLocalBuild=false
```

---

## Adding a New Publishable Module Checklist

If a new KMP library module is introduced:

1. Add `include(":<module>")` to `kmp/settings.gradle.kts`.
2. Add `"$group:<module>" to ":<module>"` to `moduleMappings` in `kmp/settings.gradle.kts`.
3. Add `"<module>"` to `publishableModules` in `kmp/build.gradle.kts`.
4. Add its version and coordinate to `kmp/dependencyManagement/gradle/internal.versions.toml`.
5. Add `api(internal.kmp.<camelCaseModule>)` to `kmp/kmp-bom/build.gradle.kts`.
6. Run `(cd kmp && ./gradlew apiDump)` to generate its initial `.api` descriptor.
