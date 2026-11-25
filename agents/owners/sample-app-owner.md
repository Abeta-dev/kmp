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
- **Targets**: JVM Desktop GUI & `--headless` / `--verticals` / `--bff-url` CLI
- **Core Responsibilities**: Reference end-to-end consumer wiring `CachedScreenRepository`, `OfflineActionCoordinator`, `KtorImageRenderer`, `PlatformBridgeBundle`, `observeConnectivity()`, offline & live Go BFF modes across the **7-in-1 Super-App Showcase Launcher** (**Abeta Fresh** `fresh-home`, **Abeta Bites** `bites-home`/`bites-restaurant`/`bites-order-track`, **Abeta Pulse** `pulse-home`, **Abeta Mart** `mart-home`, **Abeta Spark** `spark-deck`, **Abeta Trade** `trade-home`, **Abeta Pay** `pay-home`/`pay-transfer`/`pay-cards`/`pay-receipt`) showcasing Wave 6 express primitives (`marquee_strip`, urgency `nudge_bar` + `CountdownAtom` & `WIGGLE`/`HEARTBEAT` loops, animated 4-stage `order_tracker` `#BTS-4091`/`#PAY-3081`, server-molded `composed_block` loyalty/MOQ/virtual-card recipes, biometric P2P transfer, and `FloatingCartSpec` free-delivery threshold nudges) and core screens (`home`, `store-home`, `catalog`, `profile`, `form-demo`, `containers`, `watch-status`).

## Non-Negotiable Architecture & Review Gates
1. **100% BFF & Offline Screen Parity**: `SampleContainer.DEFAULT_SCREENS` MUST stay aligned with the 12 built-in screens served by `bff/internal/infra` plus the full 7-in-1 Super-App Showcase verticals, running cleanly both offline and against `--bff-url=http://localhost:8080`.
2. **SRP Screen Bifurcation**: Each offline screen and primitive group lives in its own focused file (`SampleHomeScreen.kt`, `SampleCatalogScreen.kt`, `SampleProfileScreen.kt`, `SampleFormDemoScreen.kt`, `SampleContainersScreen.kt`, `SampleStoreScreen.kt` + `SampleStoreCatalog.kt`, `SampleVerticalsCommerce.kt`, `SampleVerticalsDiscovery.kt`, `SampleNudgeAndTrackerPrimitives.kt`, `SampleComposedBlockRecipes.kt`, `SampleWatchScreen.kt`) coordinated by a thin `SampleScreens.kt` facade.
3. **Constructor Injection (DIP)**: Wire everything through `SampleContainer` / `SampleFactory` with zero hidden global singletons so `SampleWiringTest`, `--headless`, and `--headless --verticals` run deterministically.
4. **Verification**: Keep `:sample-app:test` (including all-screen & Wave 6 primitive smoke tests), `:sample-app:run --args="--headless"`, and `:sample-app:run --args="--headless --verticals"` green with Spotless and Detekt.
