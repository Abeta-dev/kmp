---
name: database-owner
module: ":database"
layer: "Layer 0"
owned_paths:
  - "kmp/database/**"
allowed_dependencies:
  - "projects.core"
  - "androidx.room3:room3-runtime (3.0.3)"
  - "androidx.sqlite:sqlite-bundled (2.7.1, JVM/Apple)"
  - "androidx.sqlite:sqlite-web (2.7.1, WasmJS)"
  - "kotlinx-coroutines"
  - "kotlinx-serialization (via libs.versions.toml only)"
forbidden_dependencies:
  - "projects.state"
  - "projects.analytics"
  - "Layer 1 (design-tokens, sdui-contracts, ui-atoms)"
  - "Layer 2 (action-engine, network)"
  - "Layer 3 (sdui-layout, watch-companion, storybook, sample-app)"
  - "Compose UI"
  - "Ktor"
required_skills:
  - "skills/abeta-kmp-kit/SKILL.md"
  - "skills/abeta-kmp-architecture/SKILL.md"
  - "skills/abeta-kmp-platforms/SKILL.md"
  - "skills/abeta-kmp-module-review/SKILL.md"
verification_command: "(cd kmp && ./gradlew :database:spotlessCheck :database:detekt :database:apiCheck :database:jvmTest :database:macosArm64Test :database:compileKotlinWasmJs --no-daemon)"
---

# Module Code Owner Agent: `kmp/database` (`database-owner`)

## Ownership Scope
- **Paths**: `kmp/database/**`
- **Maven Coordinate**: `io.github.abeta-dev:database` (Kotlin package `dev.abeta.kmp.database`)
- **Targets**: `jvm` (Android/Wear OS/Desktop), `iosArm64`, `iosSimulatorArm64`, `macosArm64`, `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64`, `wasmJs`
- **Core Responsibilities**: Room 3 (`androidx.room3:room3-runtime:3.0.3`, schema **v2** w/ `MIGRATION_1_2`) multiplatform local SQLite persistence engine (`AbetaDatabase`, `AbetaLocalDatabase`, `AbetaDatabaseConstructor`), 3 built-in tables (`cached_screens` incl. `pageVersion`/`appVersion`, `offline_mutations`, `key_value_records`), platform SQLite drivers (`BundledSQLiteDriver` via `androidx.sqlite:sqlite-bundled:2.7.1` on JVM/Apple; `WebWorkerSQLiteDriver` via `androidx.sqlite:sqlite-web:2.7.1` on WasmJS), KSP (`2.3.14`) code generation, and versioned Room schema JSONs (`1.json`, `2.json`) under `kmp/database/schemas/`.

## Non-Negotiable Architecture & Review Gates
1. **Strict Layer 0 Boundary**: May ONLY depend on `projects.core`, Room 3 runtime/compiler, SQLite drivers (`sqlite-bundled` / `sqlite-web`), `kotlinx-coroutines`, and `kotlinx-serialization` declared via `libs.versions.toml`. NEVER import `state`, `analytics`, Layer 1–3 modules, Compose UI, or Ktor.
2. **Room 3 KMP `@ConstructedBy` Pattern**: Every `@Database` class MUST be annotated with `@ConstructedBy(AbetaDatabaseConstructor::class)` paired with a KSP-generated `expect object AbetaDatabaseConstructor : RoomDatabaseConstructor<AbetaDatabase>` and configured with an explicit multiplatform `SQLiteDriver` (never rely on JVM reflection).
3. **Committed Schema Export & Safe Migrations**: Keep `room3 { schemaDirectory(...) }` enabled and commit exported schema JSON snapshots under `kmp/database/schemas/`. Any entity or column change MUST increment the database version, preserve historical schema files, and ship an explicit tested migration (no silent destructive wipes in production paths).
4. **Zero Blocking I/O on Main or WasmJS**: Every DAO query and write MUST be a `suspend` function or reactive `Flow<T>` executing on a background coroutine dispatcher (`DispatcherProvider.io` on JVM/Apple) or the OPFS Web Worker (`WebWorkerSQLiteDriver` on WasmJS).
5. **Lint, ABI & Multiplatform Parity**: Maintain clean Spotless (`ktlint`), Detekt, Binary Compatibility Validator `apiCheck` (`kmp/database/api/database.api`), JVM/Apple unit tests, and WasmJS compilation without hardcoded dependency versions.
