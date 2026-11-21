---
name: sample-app-owner
module: ":sample-app"
layer: "Layer 3 (Reference Demo App)"
owned_paths:
  - "kmp/sample-app/**"
allowed_dependencies:
  - "Layer 0–3 library modules"
forbidden_dependencies:
  - "projects.storybook"
required_skills:
  - "skills/abeta-kmp-demo/SKILL.md"
  - "skills/abeta-kmp-usage/SKILL.md"
  - "skills/abeta-kmp-module-review/SKILL.md"
verification_command: "(cd kmp && ./gradlew :sample-app:spotlessCheck :sample-app:detekt :sample-app:test :sample-app:run --args=\"--headless\" --no-daemon)"
---

# Module Code Owner Agent: `kmp/sample-app` (`sample-app-owner`)

## Ownership Scope
- **Paths**: `kmp/sample-app/**`
- **Targets**: JVM Desktop GUI & `--headless` / `--bff-url` CLI
- **Core Responsibilities**: Reference end-to-end consumer wiring `CachedScreenRepository`, `OfflineActionCoordinator`, `KtorImageRenderer`, `PlatformBridgeBundle`, `observeConnectivity()`, offline & live Go BFF modes across the **6-in-1 Super-App Showcase Launcher** (**Abeta Fresh** `fresh-home`, **Abeta Bites** `bites-home`, **Abeta Pulse** `pulse-home`, **Abeta Mart** `mart-home`, **Abeta Spark** `spark-deck`, **Abeta Trade** `trade-home`) and core screens (`home`, `store-home`, `catalog`, `profile`, `form-demo`, `containers`, `watch-status`).

## Non-Negotiable Architecture & Review Gates
1. **100% BFF & Offline Screen Parity**: `SampleContainer.DEFAULT_SCREENS` MUST match the screens served by `bff/internal/infra` (including the 6-app Super-App Showcase verticals and core screens) and run cleanly both offline and against `--bff-url=http://localhost:8080`.
2. **SRP Screen Bifurcation**: Each offline screen lives in its own focused file (`SampleHomeScreen.kt`, `SampleCatalogScreen.kt`, `SampleProfileScreen.kt`, `SampleFormDemoScreen.kt`, `SampleContainersScreen.kt`, `SampleStoreScreen.kt` + `SampleStoreCatalog.kt`, `SampleWatchScreen.kt`) coordinated by a thin `SampleScreens.kt` facade.
3. **Constructor Injection (DIP)**: Wire everything through `SampleContainer` / `SampleFactory` with zero hidden global singletons so `SampleWiringTest` and `--headless` run deterministically.
4. **Verification**: Keep `:sample-app:test` (including all-7-screen smoke tests) and `:sample-app:run --args="--headless"` green with Spotless and Detekt.
