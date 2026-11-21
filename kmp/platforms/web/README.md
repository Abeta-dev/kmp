# Web (WasmJS) Room 3 + SQLite Setup (`dev.abeta.kmp:database`)

`dev.abeta.kmp:database` compiles for `wasmJs` with **Room 3 (`androidx.room3:room3-runtime:3.0.3`)** and **SQLite Web (`androidx.sqlite:sqlite-web:2.7.1`)**, using `WebWorkerSQLiteDriver` to run SQLite off the browser main thread.

## 1. Required Cross-Origin Isolation Headers (OPFS / SharedArrayBuffer)

Browser-side SQLite persistence over **Origin Private File System (OPFS)** requires cross-origin isolation (`SharedArrayBuffer` and synchronous OPFS access handles inside a dedicated Web Worker). Serve your HTML entrypoint and worker bundle with both headers:

```http
Cross-Origin-Opener-Policy: same-origin
Cross-Origin-Embedder-Policy: require-corp
```

### Webpack Dev Server (`webpack.config.d/coop-coep.js`)

For Kotlin/Wasm Gradle dev servers (`wasmJsBrowserDevelopmentRun`), drop a config snippet in `webpack.config.d/`:

```js
config.devServer = config.devServer || {};
config.devServer.headers = {
  ...(config.devServer.headers || {}),
  "Cross-Origin-Opener-Policy": "same-origin",
  "Cross-Origin-Embedder-Policy": "require-corp",
};
```

### Production Reverse Proxy (Nginx / Cloudflare / Caddy)

Ensure both headers are attached to the HTML shell, `.wasm` binaries, and the SQLite Web Worker script:

```nginx
add_header Cross-Origin-Opener-Policy "same-origin" always;
add_header Cross-Origin-Embedder-Policy "require-corp" always;
```

## 2. Host Dependencies (`build.gradle.kts`)

Your WasmJS host app needs `dev.abeta.kmp:database` plus `kotlinx-browser` (to construct `org.w3c.dom.Worker` for `WebWorkerSQLiteDriver`, since `androidx.sqlite:sqlite-web:2.7.1` declares `kotlinx-browser` on its runtime classpath):

```kotlin
kotlin {
    wasmJs {
        browser()
    }
    sourceSets {
        wasmJsMain.dependencies {
            implementation(platform("dev.abeta.kmp:bom:<version>"))
            implementation("dev.abeta.kmp:database")
            implementation("org.jetbrains.kotlinx:kotlinx-browser:<version>")
        }
    }
}
```

## 3. Wiring `WebWorkerSQLiteDriver` in Kotlin/Wasm

Point a browser `Worker` at your SQLite WASM worker script (for example `@sqlite.org/sqlite-wasm` or the `androidx.sqlite` worker bundle hosted at `./sqlite-worker.js`) and connect it in either of two ways:

### Option A: Register once at startup (works with shared `createAbetaLocalDatabase()`)

```kotlin
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import dev.abeta.kmp.database.AbetaDatabaseConfig
import dev.abeta.kmp.database.createAbetaLocalDatabase
import dev.abeta.kmp.database.registerAbetaWebDriverFactory
import org.w3c.dom.Worker

fun initWebDatabase() {
    registerAbetaWebDriverFactory {
        WebWorkerSQLiteDriver(Worker("./sqlite-worker.js"))
    }
}

// Anywhere in shared commonMain or wasmJsMain code:
val localDb = createAbetaLocalDatabase(
    config = AbetaDatabaseConfig(databaseName = "abeta_kmp.db"),
)
```

### Option B: Pass the driver directly

```kotlin
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import dev.abeta.kmp.database.AbetaDatabaseConfig
import dev.abeta.kmp.database.createAbetaWebLocalDatabase
import org.w3c.dom.Worker

val localDb = createAbetaWebLocalDatabase(
    driver = WebWorkerSQLiteDriver(Worker("./sqlite-worker.js")),
    config = AbetaDatabaseConfig(databaseName = "abeta_kmp.db"),
)
```

## 4. Storage Notes & Fallbacks

- **Persistent OPFS storage**: Uses `config.inMemory = false` and `config.databaseName = "abeta_kmp.db"` inside a dedicated Web Worker with COOP/COEP enabled.
- **Ephemeral in-memory mode**: Set `AbetaDatabaseConfig(inMemory = true)` for private browsing contexts or environments where OPFS is unavailable.
- **Lifecycle**: Call `localDb.close()` on teardown so SQLite releases OPFS file locks cleanly.
