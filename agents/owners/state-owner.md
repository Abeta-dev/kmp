---
name: state-owner
module: ":state"
layer: "Layer 0"
owned_paths:
  - "kmp/state/**"
allowed_dependencies:
  - "projects.core"
forbidden_dependencies:
  - "projects.analytics"
  - "Layer 1 (design-tokens, sdui-contracts, ui-atoms)"
  - "Layer 2 (action-engine, network)"
  - "Layer 3 (sdui-layout, watch-companion, storybook, sample-app)"
required_skills:
  - "skills/abeta-kmp-kit/SKILL.md"
  - "skills/abeta-kmp-architecture/SKILL.md"
  - "skills/abeta-kmp-module-review/SKILL.md"
verification_command: "(cd kmp && ./gradlew :state:spotlessCheck :state:detekt :state:apiCheck :state:jvmTest :state:macosArm64Test :state:compileKotlinWatchosArm64 :state:compileKotlinWasmJs --no-daemon)"
---

# Module Code Owner Agent: `kmp/state` (`state-owner`)

## Ownership Scope
- **Paths**: `kmp/state/**`
- **Maven Coordinate**: `io.github.abeta-dev:state` (incl. native `state-android` `.aar`; Kotlin package `dev.abeta.kmp.state`)
- **Targets**: native `android` `.aar` (`compileSdk = 37`, `minSdk = 24`), `jvm`, `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`, `wasmJs`
- **Core Responsibilities**: Thread-safe `SessionStore` / `PersistentSessionStore` / `SessionLifecycleManager`, `DataStoreKeyValueStore` (`androidx.datastore:datastore-preferences-core:1.3.0`), `createDiskKeyValueStore`, native `androidMain` `SharedPreferences`/DataStore/file/`StateLock` actuals (wired via `AbetaAndroidContext`), reactive schema-versioned `PreferenceStore`, `createSecurePersistentKeyValueStore(namespace)`, `FeatureFlagProvider`, and `OfflineMutationQueue`.

## Non-Negotiable Architecture & Review Gates
1. **Strict Layer 0 Boundary**: May ONLY depend on `projects.core`. NEVER host design tokens, Compose UI, SDUI DTOs, Room 3 (`projects.database`), or HTTP clients here.
2. **Thread-Safe Reactivity**: Expose read-only `StateFlow` snapshots externally and keep mutations atomic/deterministic under concurrent coroutines.
3. **Persistence & Migration Safety**: Any persisted preference schema change MUST preserve `VersionedPreferenceEnvelope` compatibility and default safely on corrupt/unknown payloads.
4. **SOLID & KISS**: Separate storage ports (`KeyValueStore` / `ObservableKeyValueStore` / `SecureKeyValueStore`) from domain state policies (DIP) so callers can plug in `DataStoreKeyValueStore`, disk stores, Android `SharedPreferences`, or Room 3 `DatabaseKeyValueStore` interchangeably; keep `OfflineMutationQueue` pure and transport-agnostic.
5. **Lint, ABI & CodeGraph**: Pass Spotless, Detekt, BCV `apiCheck` (`kmp/state/api/jvm/state.api`), `./scripts/generate_codegraph.py --check`, and multiplatform tests.
