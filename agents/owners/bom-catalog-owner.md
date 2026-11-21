---
name: bom-catalog-owner
module: ":kmp-bom & :dependencyManagement"
layer: "Build Platform & Catalog"
owned_paths:
  - "kmp/kmp-bom/**"
  - "kmp/dependencyManagement/**"
  - "kmp/settings.gradle.kts"
  - "kmp/build.gradle.kts"
  - "kmp/gradle.properties"
  - "kmp/detekt.yml"
allowed_dependencies:
  - "Centralized version catalogs only (libs.versions.toml, internal.versions.toml)"
forbidden_dependencies:
  - "Hardcoded dependency version strings inside any module build.gradle.kts"
required_skills:
  - "skills/abeta-kmp-architecture/SKILL.md"
  - "skills/abeta-kmp-cicd/SKILL.md"
  - "skills/abeta-kmp-module-review/SKILL.md"
verification_command: "./scripts/check_architecture_and_owners.sh && (cd kmp && ./gradlew spotlessCheck apiCheck --no-daemon)"
---

# Module Code Owner Agent: BOM, Catalog & Root Gradle (`bom-catalog-owner`)

## Ownership Scope
- **Paths**: `kmp/kmp-bom/**`, `kmp/dependencyManagement/**`, `kmp/settings.gradle.kts`, `kmp/build.gradle.kts`, `kmp/gradle.properties`, `kmp/detekt.yml`
- **Maven Coordinates**: `dev.abeta.kmp:bom`, `dev.abeta.kmp:dependencies`

## Non-Negotiable Architecture & Review Gates
1. **Single Version Source of Truth**: Every external library & plugin version (including Room 3 `3.0.3`, SQLite `2.7.1`, and KSP `2.3.14`) lives in `kmp/dependencyManagement/gradle/libs.versions.toml`; every internal module coordinate lives in `kmp/dependencyManagement/gradle/internal.versions.toml`. Zero hardcoded versions in module build scripts.
2. **4-Way Registration Invariant (11 Publishable KMP Library Modules + BOM + Catalog = 13 Gradle Modules)**: Every publishable KMP library module (`core`, `state`, `analytics`, `database`, `design-tokens`, `sdui-contracts`, `ui-atoms`, `action-engine`, `network`, `sdui-layout`, `watch-companion`) MUST be registered identically in:
   1. `kmp/dependencyManagement/gradle/internal.versions.toml`
   2. `kmp/kmp-bom/build.gradle.kts`
   3. `kmp/build.gradle.kts` (`publishableModules`)
   4. `kmp/settings.gradle.kts` (`include(...)` & `moduleMappings` for `abeta.kmpLocalBuild`)
   plus maintain a checked-in `api/<module>.api` descriptor validated by `apiCheck`.
3. **Cross-Host Build Determinism**: Keep `build.gradle.kts` clean on macOS (including hosts without Xcode iOS/watchOS simulator runtimes), Linux CI (`ubuntu-latest`), and Windows without machine-specific paths.
4. **Open Registry Publishing Hygiene**: Keep `com.vanniktech.maven.publish` Central Portal (`publishToMavenCentral(automaticRelease = true)` + conditional GPG `signAllPublications()`), Cloudsmith Open Source (`publishAllPublicationsToCloudsmithRepository`), optional `CustomMaven`, and `-Pabeta.publishVersion` overrides working without breaking credential-free `./gradlew publishToMavenLocal` (see `docs/publishing-and-hosting.md` & `docs/local-database-room3.md`).
5. **Quality Gates**: Preserve Spotless (`ktlint`), Detekt (`detekt.yml`), BCV (`apiCheck`), and Dokka v2 wiring across all subprojects.
