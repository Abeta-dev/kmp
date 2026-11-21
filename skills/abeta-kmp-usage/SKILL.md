---
name: abeta-kmp-usage
description: End-to-end onboarding and consumption guide for abeta-dev/kmp. Use when integrating dev.abeta.kmp:bom and dev.abeta.kmp:dependencies into a consumer app, wiring SDUI screens, connecting to the Go 1.26.0 BFF, registering custom atoms/snippets/actions, or configuring local vs published builds.
---

# Abeta KMP Kit — How To Use This Repo Skill (`abeta-kmp-usage`)

Use this skill when onboarding to `abeta-dev/kmp`, consuming `dev.abeta.kmp:*` modules in a new or existing KMP/Android/iOS/Desktop/Web/Watch app, or running the full KMP + Go BFF stack locally.

## 1. Prerequisites & First 5 Minutes

- **JDK 21** + **Gradle 9.7.0** (bundled via `kmp/gradlew`)
- **Go 1.26.0** (for `bff/`)
- **Xcode** (optional, macOS only for iOS/macOS/watchOS XCFrameworks)
- **Android SDK (`ANDROID_HOME`, `compileSdk >= 35`)** (optional, only for copying `kmp/platforms/{android,wearos}` into your host Android app)

```bash
# Verify the entire monorepo works out of the box
./scripts/verify_all.sh

# Terminal 1: Start the Go 1.26.0 SDUI BFF (HTTP/1.1 + h2c + Brotli/gzip + ETag + OpenAPI/Scalar)
(cd bff && go run ./cmd/bff)
# Inspect OpenAPI 3.1 + Scalar interactive docs: http://localhost:8080/docs

# Terminal 2: Launch the Compose Desktop Sample App connected to the live Go BFF
(cd kmp && ./gradlew :sample-app:run --args="--bff-url=http://localhost:8080")

# Or launch the 12-story interactive Storybook (Desktop or WasmJS Browser)
(cd kmp && ./gradlew :storybook:run)
(cd kmp && ./gradlew :storybook:wasmJsBrowserDevelopmentRun)
```

## 2. Consuming via Gradle BOM & Version Catalog (Zero Auth Required)

Stable releases across all **11 publishable KMP library modules** (plus `bom` and `dependencies`) publish to **Maven Central** (`mavenCentral()`), and snapshots/mirrors publish to **Cloudsmith Open Source** (`https://dl.cloudsmith.io/public/abeta-dev/kmp/maven/`). See `docs/publishing-and-hosting.md` for Maven Central, Cloudsmith, Apple SwiftPM (`https://github.com/Abeta-dev/kmp-spm`), and Go `proxy.golang.org` setup, and `docs/local-database-room3.md` for Room 3 local SQLite persistence.

In your consumer project's `settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        // Optional — anonymous public snapshots / mirror via Cloudsmith Open Source:
        // maven("https://dl.cloudsmith.io/public/abeta-dev/kmp/maven/")
    }
    versionCatalogs {
        create("abeta") {
            from("dev.abeta.kmp:dependencies:0.1.0")
        }
    }
}
```

In your module's `build.gradle.kts`:
```kotlin
dependencies {
    implementation(platform("dev.abeta.kmp:bom:0.1.0"))
    // Pick only the layers you need (versions aligned by the BOM):
    implementation("dev.abeta.kmp:core")
    implementation("dev.abeta.kmp:database") // Optional Room 3 KMP SQLite local DB
    implementation("dev.abeta.kmp:design-tokens")
    implementation("dev.abeta.kmp:sdui-contracts")
    implementation("dev.abeta.kmp:ui-atoms")
    implementation("dev.abeta.kmp:action-engine")
    implementation("dev.abeta.kmp:network")
    implementation("dev.abeta.kmp:sdui-layout")
    // For Apple Watch / Wear OS companion apps (headless, zero Compose UI):
    // implementation("dev.abeta.kmp:watch-companion")
}
```

> **Local Monorepo Toggle**: Inside this repository, `abeta.kmpLocalBuild=true` in `kmp/gradle.properties` transparently substitutes `dev.abeta.kmp:*` Maven coordinates with local Gradle `:module` projects. Pass `-Pabeta.kmpLocalBuild=false` to verify published coordinates.

## 3. Minimal Wiring in Your App (Koin + `SduiHostedRoot` / `SduiScreen`)

### Option A — One-line Hosted Root (iOS, macOS, Desktop, Web, Android)
```kotlin
SduiHostedRoot(
    screenId = "store-home",
    bffBaseUrl = "https://bff.example.com", // omit/null for instant offline preview
)
```

### Option B — Full DI Control (`createSduiKoinModule`)
```kotlin
val koinApp = koinApplication {
    modules(
        createSduiKoinModule(
            baseUrl = "https://bff.example.com",
            scope = coroutineScope,
            platformBridges = PlatformBridgeBundle.createPlatformDefault(), // or AndroidPlatformBridgeFactory.create(...)
            certificatePins = mapOf("bff.example.com" to listOf("sha256/YOUR_SPKI_PIN=")),
        ),
    )
}
val controller = koinApp.koin.get<LayoutController>()
val snippetRegistry = koinApp.koin.get<SnippetRegistry>()
```
Render inside `DesignTheme(darkTheme = ..., reducedMotion = ...)` with `SduiScreen(...)`, and start `controller.observeConnectivity()` in a `LaunchedEffect` to auto-flush queued offline actions on reconnect.

## 4. Extending Safely (Open/Closed Principle)

- **Add a Custom Action** (without touching `action-engine` internals):
  1. Define a `@Serializable` class implementing `ActionPayload`.
  2. Register its `ActionPayloadDecoder` in `ActionDecoderRegistry`.
  3. Register its `ActionHandler` in `ActionRegistry`.
  4. Mirror the action type & payload in `bff/pkg/sdui/actions/` and `bff/internal/infra/action_executor.go`.
- **Add a Custom Snippet Renderer** (without touching `SduiScreen`):
  1. Register a `SnippetRenderer` in `SnippetRegistry` for your custom `snippet_type`.
  2. Emit matching `SnippetEnvelope` JSON from your Go BFF screen builder.
- **Swap Image Loader or Analytics Vendor (DIP)**:
  - Provide `LocalImageRenderer` for Coil/Kamel/custom caching.
  - Pass your `AnalyticsTracker` implementation into `CompositeAnalyticsTracker` / `SanitizingAnalyticsTracker`.

## 5. Related Skills & Guides
- Local Room 3 SQLite database guide: `docs/local-database-room3.md`
- Open package hosting & release playbook: `docs/publishing-and-hosting.md`
- Demo walkthroughs: `skills/abeta-kmp-demo/SKILL.md`
- Native Android/Wear/Apple/Web host integration: `skills/abeta-kmp-platforms/SKILL.md`
- CI/CD & release gates: `skills/abeta-kmp-cicd/SKILL.md`
- Per-module Code Owner review: `skills/abeta-kmp-module-review/SKILL.md`
