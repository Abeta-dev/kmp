# Multiplatform Local Database Guide (Room 3 + SQLite)

`io.github.abeta-dev:database` (`kmp/database/`, Kotlin package `dev.abeta.kmp.database`) is a **Layer 0** Kotlin Multiplatform local persistence module backed by **AndroidX Room 3 (`androidx.room3:room3-runtime:3.0.3`)**, **SQLite Bundled (`androidx.sqlite:sqlite-bundled:2.7.1`)**, **SQLite Web (`androidx.sqlite:sqlite-web:2.7.1`)**, and **KSP (`2.3.14`)**.

It provides offline-first structured storage across **Android, Wear OS, JVM Desktop (macOS / Windows / Linux), iOS / iPadOS, macOS Native, watchOS, and Web (`wasmJs` OPFS Web Worker)** while preserving strict **Uncle Bob Layer 0 purity** (depends only on `:core` — zero Compose UI, Ktor, or Layer 1–3 dependencies).

---

## 1. Toolchain & Artifact Coordinates

All versions are centralized in [`kmp/dependencyManagement/gradle/libs.versions.toml`](../kmp/dependencyManagement/gradle/libs.versions.toml) and aligned via `io.github.abeta-dev:bom:0.3.0`:

| Component | Maven Coordinate / Plugin | Version |
| :--- | :--- | :--- |
| **Abeta Database Module** | `io.github.abeta-dev:database` | `0.3.0` (aligned by `io.github.abeta-dev:bom:0.3.0`) |
| **Room 3 Runtime** | `androidx.room3:room3-runtime` | `3.0.3` |
| **Room 3 KSP Compiler** | `androidx.room3:room3-compiler` | `3.0.3` |
| **Room 3 Gradle Plugin** | `androidx.room3` | `3.0.3` |
| **SQLite Native / JVM Driver** | `androidx.sqlite:sqlite-bundled` | `2.7.1` |
| **SQLite Browser Driver (WasmJS)** | `androidx.sqlite:sqlite-web` | `2.7.1` |
| **KSP Compiler Plugin** | `com.google.devtools.ksp` | `2.3.14` |

---

## 2. Multi-OS Target & SQLite Driver Matrix

Every KMP target uses an explicit `SQLiteDriver` compiled or bundled for that OS so SQL behavior, pragma support, WAL mode, and JSON/indexing semantics remain consistent across platforms:

| Platform / OS | KMP Target(s) | SQLite Driver Artifact | Runtime Driver / Storage Backend | Host Reference / Setup |
| :--- | :--- | :--- | :--- | :--- |
| **Android & Wear OS** | `jvm` (in Android host app) | `androidx.sqlite:sqlite-bundled:2.7.1` | `BundledSQLiteDriver()` backed by `context.getDatabasePath(name)` | [`kmp/platforms/android/AndroidDatabaseFactory.kt`](../kmp/platforms/android/AndroidDatabaseFactory.kt) |
| **JVM Desktop (macOS, Windows, Linux)** | `jvm` (Java 21) | `androidx.sqlite:sqlite-bundled:2.7.1` | `BundledSQLiteDriver()` backed by file path or `:memory:` | Built-in `jvmMain` factory in `kmp/database` |
| **iOS & iPadOS** | `iosArm64`, `iosSimulatorArm64` | `androidx.sqlite:sqlite-bundled:2.7.1` | `BundledSQLiteDriver()` in app `NSDocumentDirectory` / `NSLibraryDirectory` | Built-in `appleMain` factory in `kmp/database` |
| **macOS Native** | `macosArm64` | `androidx.sqlite:sqlite-bundled:2.7.1` | `BundledSQLiteDriver()` in app support / local file path | Built-in `appleMain` factory in `kmp/database` |
| **Apple Watch (`watchOS`)** | `watchosArm64`, `watchosSimulatorArm64`, `watchosDeviceArm64` | `androidx.sqlite:sqlite-bundled:2.7.1` | `BundledSQLiteDriver()` in watchOS sandbox container | Built-in `appleMain` factory in `kmp/database` |
| **Web (`WasmJS` Browser)** | `wasmJs` | `androidx.sqlite:sqlite-web:2.7.1` | `WebWorkerSQLiteDriver` executing SQLite WASM inside an OPFS Web Worker | [`kmp/platforms/web/README.md`](../kmp/platforms/web/README.md) (COOP/COEP headers) |

---

## 3. Built-In Database Architecture & Tables

`io.github.abeta-dev:database` exposes **`AbetaDatabase`** (the `@Database` Room 3 class using `@ConstructedBy(AbetaDatabaseConstructor::class)`) and **`AbetaLocalDatabase`** (the high-level facade wrapping DAOs, coroutine dispatchers, and lifecycle management).

### Built-In Schema (`version = 1`)

Exported schema snapshot: [`kmp/database/schemas/dev.abeta.kmp.database.AbetaDatabase/1.json`](../kmp/database/schemas/dev.abeta.kmp.database.AbetaDatabase/1.json).

| Table Name | Primary Key & Columns | Indexed Columns | Typical Use in SDUI Apps |
| :--- | :--- | :--- | :--- |
| **`cached_screens`** | PK(`screenId`): `screenId`, `payloadJson`, `etag` (nullable), `cachedAtEpochMillis`, `expiresAtEpochMillis` (nullable) | `expiresAtEpochMillis` | Instant offline rendering, TTL pruning, and ETag revalidation (`304 Not Modified`) for cached SDUI `PageResponse` snapshots. |
| **`offline_mutations`** | PK(`id`): `id`, `mutationType`, `payloadJson`, `createdAtEpochMillis`, `retryCount`, `lastError` (nullable) | `createdAtEpochMillis`, `retryCount` | Durable FIFO queue of pending offline client actions/API mutations across process restarts until connectivity returns. |
| **`key_value_records`** | Composite PK(`namespace`, `recordKey`): `namespace`, `recordKey`, `valueText`, `updatedAtEpochMillis`, `expiresAtEpochMillis` (nullable) | `expiresAtEpochMillis` | Namespaced structured key-value records with TTL expiration for preferences, feature flags, and lightweight reactive state. |

### Why `@ConstructedBy` Is Mandatory in Room 3 KMP

Unlike Android-only Room 2 (which used JVM reflection to instantiate generated `*_Impl` classes), Room 3 KMP eliminates reflection across Native and WasmJS:

```kotlin
@Database(
    entities = [
        CachedScreenEntity::class,
        OfflineMutationEntity::class,
        KeyValueRecordEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(AbetaDatabaseConstructor::class)
public abstract class AbetaDatabase : RoomDatabase() {
    // DAOs for cached_screens, offline_mutations, and key_value_records
}

@Suppress("KotlinNoActualForExpect")
public expect object AbetaDatabaseConstructor : RoomDatabaseConstructor<AbetaDatabase> {
    override fun initialize(): AbetaDatabase
}
```

KSP (`2.3.14`) runs `androidx.room3:room3-compiler:3.0.3` across all 8 KMP compilation targets (`kspJvm`, `kspIosArm64`, `kspIosSimulatorArm64`, `kspMacosArm64`, `kspWatchosArm64`, `kspWatchosSimulatorArm64`, `kspWatchosDeviceArm64`, `kspWasmJs`) to generate every platform's `actual object AbetaDatabaseConstructor` and DAO implementations at compile time.

---

## 4. Schema Export & Safe Migrations

[`kmp/database/build.gradle.kts`](../kmp/database/build.gradle.kts) configures the Room 3 Gradle plugin to export versioned schema JSON files automatically:

```kotlin
room3 {
    schemaDirectory("$projectDir/schemas")
}
```

### Schema Governance Rules

1. **Always Commit Exported Schemas**: Commit every generated JSON file under `kmp/database/schemas/dev.abeta.kmp.database.AbetaDatabase/<version>.json`.
2. **Never Edit Shipped Schema Versions**: When adding columns, indices, or tables, increment `@Database(version = N + 1)` and keep prior `<version>.json` files untouched for migration tests.
3. **Provide Explicit Migrations**: Register explicit `Migration(from, to)` steps (or Room 3 `@AutoMigration` specs) when changing schemas; never rely on destructive fallback wipes in production consumer configurations.
4. **Update Public ABI**: If public database APIs or models change, regenerate `kmp/database/api/database.api` via `(cd kmp && ./gradlew :database:apiDump --no-daemon)`.

---

## 5. Consuming `io.github.abeta-dev:database` with the Gradle BOM

Because `io.github.abeta-dev:database` is part of `io.github.abeta-dev:bom`, consumers do not specify individual module versions.

### Step 1: Configure Repositories (`settings.gradle.kts`)

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        // Optional — anonymous public snapshots via Cloudsmith Open Source:
        // maven("https://dl.cloudsmith.io/public/abeta-dev/kmp/maven/")
    }
    versionCatalogs {
        create("abetaLibs") {
            from("io.github.abeta-dev:dependencies:0.3.0")
        }
    }
}
```

### Step 2: Add `io.github.abeta-dev:database` (`build.gradle.kts`)

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project.dependencies.platform("io.github.abeta-dev:bom:0.3.0"))
            implementation("io.github.abeta-dev:core")
            implementation("io.github.abeta-dev:database")
            // Optional SDUI & networking modules aligned by the same BOM:
            // implementation("io.github.abeta-dev:sdui-layout")
        }
    }
}
```

> **Transitive Drivers**: `io.github.abeta-dev:database` declares `androidx.room3:room3-runtime` in `commonMain`, `androidx.sqlite:sqlite-bundled` in `jvmMain` & `appleMain`, and `androidx.sqlite:sqlite-web` in `wasmJsMain` as `api` dependencies, so consumers automatically receive the matching SQLite driver for every target.

---

## 6. Platform-Specific Host Integration

### A. Android & Wear OS (`kmp/platforms/android/AndroidDatabaseFactory.kt`)

Android and Wear OS apps construct `AbetaDatabase` using `ApplicationContext` so SQLite files live in the app's private database directory (`context.getDatabasePath(config.databaseName)`). Copy or adapt [`kmp/platforms/android/AndroidDatabaseFactory.kt`](../kmp/platforms/android/AndroidDatabaseFactory.kt) into your Android host module:

```kotlin
val localDb = AndroidDatabaseFactory.createLocalDatabase(
    context = applicationContext,
    config = AbetaDatabaseConfig(databaseName = "abeta_kmp.db"),
)
```

- Uses `Room.databaseBuilder<AbetaDatabase>(context = appContext, name = dbFile.absolutePath)` (or `Room.inMemoryDatabaseBuilder` when `config.inMemory = true`)
- Attaches `BundledSQLiteDriver()` so SQLite version and extensions match iOS/macOS/JVM identically regardless of Android API level
- Configures coroutine dispatchers off `Dispatchers.Main` (`ioDispatcher`)

### B. JVM Desktop & Apple (`iOS`, `macOS`, `watchOS`)

Use `createAbetaLocalDatabase(...)` for file-backed storage or in-memory test databases (`BundledSQLiteDriver()`):

```kotlin
val localDb = createAbetaLocalDatabase(
    config = AbetaDatabaseConfig(databaseName = "abeta_kmp.db"),
)
```

- **JVM Desktop**: Opens a local file path (or `:memory:` when `inMemory = true` in unit tests).
- **Apple (`iOS`, `macOS`, `watchOS`)**: Resolves the sandboxed app directory and opens SQLite via `BundledSQLiteDriver()` without requiring CocoaPods or external system SQLite bridges.

### C. Web (`WasmJS`) — OPFS Web Worker & Required HTTP Headers

Room 3 on `wasmJs` persists data in the browser's **Origin Private File System (OPFS)** via `androidx.sqlite:sqlite-web:2.7.1` (`WebWorkerSQLiteDriver`), which uses `SharedArrayBuffer` and synchronous OPFS access handles inside a dedicated Web Worker.

Register the worker driver once at startup, then call `createAbetaLocalDatabase(...)`:

```kotlin
registerAbetaWebDriverFactory {
    WebWorkerSQLiteDriver(Worker("./sqlite-worker.js"))
}
val localDb = createAbetaLocalDatabase(
    config = AbetaDatabaseConfig(databaseName = "abeta_kmp.db"),
)
```

Your web server / CDN (and local dev server) **MUST** serve the host page and worker assets with Cross-Origin Isolation headers:

```http
Cross-Origin-Opener-Policy: same-origin
Cross-Origin-Embedder-Policy: require-corp
```

Without these two headers, browsers block `SharedArrayBuffer` and OPFS worker initialization. See [`kmp/platforms/web/README.md`](../kmp/platforms/web/README.md) for full Webpack devServer, Nginx, Cloudflare, and Caddy header snippets.

---

## 7. Verification & Code Owner Gate

Module Code Owner: [`agents/owners/database-owner.md`](../agents/owners/database-owner.md) (`@abeta-dev/kmp-database-owners` in [`.github/CODEOWNERS`](../.github/CODEOWNERS)).

```bash
# Run full database module verification (formatting, detekt, ABI check, JVM/macOS tests, WasmJS compile)
(cd kmp && ./gradlew :database:spotlessCheck :database:detekt :database:apiCheck :database:jvmTest :database:macosArm64Test :database:compileKotlinWasmJs --no-daemon)

# Verify Layer 0 dependency isolation, BOM/Catalog registration, and owner coverage
./scripts/check_architecture_and_owners.sh
```

See also:
- [Gradle BOM & Version Catalog Guide](gradle-bom-guide.md)
- [Open Package Hosting & Release Publishing Guide](publishing-and-hosting.md)
- [Architecture & Dependency Rule](architecture.md)
