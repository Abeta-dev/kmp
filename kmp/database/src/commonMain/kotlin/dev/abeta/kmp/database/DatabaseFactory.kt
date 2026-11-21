package dev.abeta.kmp.database

import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabase.Builder
import androidx.sqlite.SQLiteDriver
import dev.abeta.kmp.core.identity.RandomUuidGenerator
import dev.abeta.kmp.core.identity.UuidGenerator
import dev.abeta.kmp.core.time.Clock
import dev.abeta.kmp.core.time.SystemClock

internal expect fun createRawAbetaDatabaseBuilder(config: AbetaDatabaseConfig): Builder<AbetaDatabase>

/**
 * Creates a platform-configured [RoomDatabase.Builder] for [AbetaDatabase].
 *
 * - On JVM and Apple targets (`iOS`, `macOS`, `watchOS`), attaches `BundledSQLiteDriver`.
 * - On WasmJS, attaches a `WebWorkerSQLiteDriver` created from the factory registered via
 *   `registerAbetaWebDriverFactory`; for direct driver injection, use `createAbetaWebDatabaseBuilder`.
 */
expect fun createAbetaDatabaseBuilder(config: AbetaDatabaseConfig = AbetaDatabaseConfig()): Builder<AbetaDatabase>

/**
 * Creates a [RoomDatabase.Builder] for [AbetaDatabase] backed by a caller-supplied [driver].
 *
 * Works across JVM, Apple, and WasmJS targets (including custom or test [SQLiteDriver] implementations).
 */
fun createAbetaDatabaseBuilder(
    driver: SQLiteDriver,
    config: AbetaDatabaseConfig = AbetaDatabaseConfig(),
): Builder<AbetaDatabase> =
    createRawAbetaDatabaseBuilder(config).apply {
        setDriver(driver)
        config.queryCoroutineContext?.let(::setQueryCoroutineContext)
    }

/** Builds an [AbetaDatabase] using the default platform SQLite driver for [config]. */
fun createAbetaDatabase(config: AbetaDatabaseConfig = AbetaDatabaseConfig()): AbetaDatabase {
    val builder = createAbetaDatabaseBuilder(config)
    return builder.build()
}

/** Builds an [AbetaDatabase] backed by an explicit [driver] and [config]. */
fun createAbetaDatabase(
    driver: SQLiteDriver,
    config: AbetaDatabaseConfig = AbetaDatabaseConfig(),
): AbetaDatabase = createAbetaDatabaseBuilder(driver = driver, config = config).build()

/** Builds a high-level [AbetaLocalDatabase] facade using the default platform driver for [config]. */
fun createAbetaLocalDatabase(
    config: AbetaDatabaseConfig = AbetaDatabaseConfig(),
    clock: Clock = SystemClock(),
    uuidGenerator: UuidGenerator = RandomUuidGenerator(),
): AbetaLocalDatabase =
    AbetaLocalDatabase(
        database = createAbetaDatabase(config),
        clock = clock,
        uuidGenerator = uuidGenerator,
    )

/** Builds a high-level [AbetaLocalDatabase] facade backed by an explicit [driver] and [config]. */
fun createAbetaLocalDatabase(
    driver: SQLiteDriver,
    config: AbetaDatabaseConfig = AbetaDatabaseConfig(),
    clock: Clock = SystemClock(),
    uuidGenerator: UuidGenerator = RandomUuidGenerator(),
): AbetaLocalDatabase =
    AbetaLocalDatabase(
        database = createAbetaDatabase(driver = driver, config = config),
        clock = clock,
        uuidGenerator = uuidGenerator,
    )
