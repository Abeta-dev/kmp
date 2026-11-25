# JetBrains Junie & Android Studio AI Guidelines (`abeta-dev/kmp`)

This repository contains:
- **`kmp/`**: Gradle 9.7.0 / JDK 21 / Kotlin 2.4.21 / Compose Multiplatform 1.12.2 SDUI, Wearable & Room 3 Database Kit (**11 publishable library modules** + `kmp-bom` + `dependencyManagement` = **13 Maven Central artifacts** at **`0.7.0`** + `storybook` + `sample-app` + `platforms/`).
- **`bff/`**: Go 1.26.0 Clean Architecture SDUI Backend-for-Frontend (`cmd/bff`, `internal/{domain,usecase,handler,infra}`, 8 exportable `pkg/sdui/*` packages at **`bff@v0.9.0`**).

## Opening in Android Studio / IntelliJ IDEA
1. Open the **`kmp/`** directory as a Gradle project (or the repo root if editing both `kmp/` and `bff/`).
2. Configure **Gradle JDK = 21** (`Settings -> Build, Execution, Deployment -> Build Tools -> Gradle`).
3. Keep `abeta.kmpLocalBuild=true` in `kmp/gradle.properties` for composite local module development; switch to `-Pabeta.kmpLocalBuild=false` when testing Maven BOM resolution.
4. For Android & Wear OS host integration, copy the drop-in templates under `kmp/platforms/android/` and `kmp/platforms/wearos/` into your Android app module (`compileSdk >= 35`, `ANDROID_HOME` set).

## Canonical Rules & Owners
- Full contract: [AGENTS.md](../AGENTS.md)
- Module Code Owner personas: [agents/owners/](../agents/owners/) (17 per-module owners matching `.github/CODEOWNERS`)
- Skills: [skills/](../skills/) (`abeta-kmp-kit`, `abeta-kmp-usage`, `abeta-kmp-demo`, `abeta-kmp-platforms`, `abeta-kmp-atomic`, `abeta-kmp-actions`, `abeta-kmp-bff`, `abeta-kmp-architecture`, `abeta-kmp-module-review`, `abeta-kmp-cicd`)

## Non-Negotiable Architecture Checks
- **Dependency Rule**: `L3 -> L2 -> L1 -> L0`; Layer 1 (`design-tokens`, `sdui-contracts`, `ui-atoms`) NEVER imports `core`, `action-engine`, `network`, or `sdui-layout`; `watch-companion` NEVER imports Compose UI; `bff/internal/domain` NEVER imports Gin/`net/http`.
- **SOLID, Clean Code & KISS**: Avoid monolithic files (`<= 750 LOC` production target), use OCP registries (`ActionRegistry`, `SnippetRegistry`), and inject dependencies via constructors/Koin.
- **Formatting, Lint & ABI**: Run `(cd kmp && ./gradlew spotlessApply detekt apiCheck)` (or `apiDump` on intentional public API changes). Never hardcode dependency versions inside module `build.gradle.kts`.

## Verification Commands
```bash
./scripts/check_brand_hygiene.sh
./scripts/check_architecture_and_owners.sh
./scripts/verify_all.sh
```
