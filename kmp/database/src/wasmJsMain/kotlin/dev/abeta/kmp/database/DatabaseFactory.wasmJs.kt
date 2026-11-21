@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.database

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteDriver
import dev.abeta.kmp.core.identity.RandomUuidGenerator
import dev.abeta.kmp.core.identity.UuidGenerator
import dev.abeta.kmp.core.time.Clock
import dev.abeta.kmp.core.time.SystemClock

private var registeredDriverFactory: (() -> SQLiteDriver)? = null

/**
 * Registers the browser [SQLiteDriver] factory used by [createAbetaDatabaseBuilder] on WasmJS.
 *
 * Call this once during web app bootstrap (for example
 * `registerAbetaWebDriverFactory { WebWorkerSQLiteDriver(Worker("./sqlite-worker.js")) }`)
 * so shared multiplatform code can call [createAbetaDatabaseBuilder] without platform-specific wiring.
 */
fun registerAbetaWebDriverFactory(factory: () -> SQLiteDriver) {
    registeredDriverFactory = factory
}

/** Clears any previously registered browser [SQLiteDriver] factory. */
fun clearAbetaWebDriverFactory() {
    registeredDriverFactory = null
}

internal actual fun createRawAbetaDatabaseBuilder(config: AbetaDatabaseConfig): RoomDatabase.Builder<AbetaDatabase> =
    if (config.inMemory) {
        Room.inMemoryDatabaseBuilder<AbetaDatabase>()
    } else {
        Room.databaseBuilder<AbetaDatabase>(
            name = config.databaseName,
        )
    }

actual fun createAbetaDatabaseBuilder(config: AbetaDatabaseConfig): RoomDatabase.Builder<AbetaDatabase> {
    val factory =
        checkNotNull(registeredDriverFactory) {
            "No SQLite Web Worker driver factory registered for WasmJS. " +
                "Call registerAbetaWebDriverFactory { WebWorkerSQLiteDriver(Worker(\"./sqlite-worker.js\")) } " +
                "at startup, or call createAbetaWebDatabase(driver, config) / " +
                "createAbetaDatabase(driver, config) directly."
        }
    return createAbetaWebDatabaseBuilder(driver = factory(), config = config)
}

/**
 * Creates a [RoomDatabase.Builder] for [AbetaDatabase] backed by a browser [SQLiteDriver]
 * (typically `WebWorkerSQLiteDriver(worker)` from `androidx.sqlite.driver.web`).
 */
fun createAbetaWebDatabaseBuilder(
    driver: SQLiteDriver,
    config: AbetaDatabaseConfig = AbetaDatabaseConfig(),
): RoomDatabase.Builder<AbetaDatabase> =
    createAbetaDatabaseBuilder(
        driver = driver,
        config = config,
    )

/**
 * Builds an [AbetaDatabase] backed by a browser [SQLiteDriver] (typically `WebWorkerSQLiteDriver`).
 */
fun createAbetaWebDatabase(
    driver: SQLiteDriver,
    config: AbetaDatabaseConfig = AbetaDatabaseConfig(),
): AbetaDatabase = createAbetaWebDatabaseBuilder(driver = driver, config = config).build()

/**
 * Builds a high-level [AbetaLocalDatabase] facade backed by a browser [SQLiteDriver]
 * (typically `WebWorkerSQLiteDriver`).
 */
fun createAbetaWebLocalDatabase(
    driver: SQLiteDriver,
    config: AbetaDatabaseConfig = AbetaDatabaseConfig(),
    clock: Clock = SystemClock(),
    uuidGenerator: UuidGenerator = RandomUuidGenerator(),
): AbetaLocalDatabase =
    AbetaLocalDatabase(
        database = createAbetaWebDatabase(driver = driver, config = config),
        clock = clock,
        uuidGenerator = uuidGenerator,
    )
