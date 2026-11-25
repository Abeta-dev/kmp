package dev.abeta.kmp.platforms.android

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.abeta.kmp.core.coroutines.ioDispatcher
import dev.abeta.kmp.core.identity.RandomUuidGenerator
import dev.abeta.kmp.core.identity.UuidGenerator
import dev.abeta.kmp.core.time.Clock
import dev.abeta.kmp.core.time.SystemClock
import dev.abeta.kmp.database.AbetaDatabase
import dev.abeta.kmp.database.AbetaDatabaseConfig
import dev.abeta.kmp.database.AbetaLocalDatabase

/**
 * Drop-in Android and Wear OS factory creating a Room 3 [AbetaDatabase] backed by [BundledSQLiteDriver]
 * inside a host Android Gradle app (`compileSdk >= 35`).
 *
 * Copy into your Android / Wear OS host application module alongside:
 * - `implementation(platform("io.github.abeta-dev:bom:<version>"))`
 * - `implementation("io.github.abeta-dev:database")`
 * - `implementation("androidx.sqlite:sqlite-bundled:2.7.1")`
 */
object AndroidDatabaseFactory {
    /**
     * Builds a configured [RoomDatabase.Builder] bound to [context]'s application database directory
     * (or an in-memory database when [AbetaDatabaseConfig.inMemory] is `true`).
     */
    fun createBuilder(
        context: Context,
        config: AbetaDatabaseConfig = AbetaDatabaseConfig(),
    ): RoomDatabase.Builder<AbetaDatabase> {
        val appContext = context.applicationContext
        val builder =
            if (config.inMemory) {
                Room.inMemoryDatabaseBuilder<AbetaDatabase>(context = appContext)
            } else {
                val dbFile = appContext.getDatabasePath(config.databaseName)
                dbFile.parentFile?.mkdirs()
                Room.databaseBuilder<AbetaDatabase>(
                    context = appContext,
                    name = dbFile.absolutePath,
                )
            }
        return builder
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(config.queryCoroutineContext ?: ioDispatcher)
    }

    /** Creates a ready-to-use [AbetaDatabase] instance for Android or Wear OS. */
    fun createDatabase(
        context: Context,
        config: AbetaDatabaseConfig = AbetaDatabaseConfig(),
    ): AbetaDatabase = createBuilder(context = context, config = config).build()

    /** Creates the high-level [AbetaLocalDatabase] facade for Android or Wear OS. */
    fun createLocalDatabase(
        context: Context,
        config: AbetaDatabaseConfig = AbetaDatabaseConfig(),
        clock: Clock = SystemClock(),
        uuidGenerator: UuidGenerator = RandomUuidGenerator(),
    ): AbetaLocalDatabase =
        AbetaLocalDatabase(
            database = createDatabase(context = context, config = config),
            clock = clock,
            uuidGenerator = uuidGenerator,
        )
}
