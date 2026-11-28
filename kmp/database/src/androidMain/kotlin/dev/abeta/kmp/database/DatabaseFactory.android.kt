@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.database

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.abeta.kmp.core.coroutines.ioDispatcher
import dev.abeta.kmp.core.platform.AbetaAndroidContext
import java.io.File

internal actual fun createRawAbetaDatabaseBuilder(config: AbetaDatabaseConfig): RoomDatabase.Builder<AbetaDatabase> {
    if (config.inMemory) {
        return Room.inMemoryDatabaseBuilder<AbetaDatabase>()
    }
    val context = AbetaAndroidContext.applicationContextOrNull()
    return if (context != null) {
        Room.databaseBuilder<AbetaDatabase>(
            context = context,
            name = resolveAndroidDatabasePath(context = context, databaseName = config.databaseName),
        )
    } else {
        Room.databaseBuilder<AbetaDatabase>(
            name = resolveAndroidHostFallbackDatabasePath(databaseName = config.databaseName),
        )
    }
}

actual fun createAbetaDatabaseBuilder(config: AbetaDatabaseConfig): RoomDatabase.Builder<AbetaDatabase> =
    createRawAbetaDatabaseBuilder(config).apply {
        addMigrations(AbetaDatabase.MIGRATION_1_2)
        setDriver(BundledSQLiteDriver())
        setQueryCoroutineContext(config.queryCoroutineContext ?: ioDispatcher)
    }

internal fun resolveAndroidDatabasePath(
    context: Context,
    databaseName: String,
): String =
    resolveValidatedDatabaseFile(
        baseRoot = File(context.filesDir, "abeta-kmp"),
        databaseName = databaseName,
    ).absolutePath

internal fun resolveAndroidHostFallbackDatabasePath(databaseName: String): String =
    resolveValidatedDatabaseFile(
        baseRoot = File(System.getProperty("java.io.tmpdir", "."), "abeta-kmp/db"),
        databaseName = databaseName,
    ).absolutePath

private fun resolveValidatedDatabaseFile(
    baseRoot: File,
    databaseName: String,
): File {
    val trimmed = databaseName.trim()
    val candidate = File(trimmed)
    val dbFile =
        if (candidate.isAbsolute) {
            candidate
        } else {
            requireSafeRelativeDatabaseName(trimmed)
            val normalizedBase = baseRoot.canonicalFile
            val resolved = File(normalizedBase, trimmed).canonicalFile
            val basePrefix = normalizedBase.path.trimEnd(File.separatorChar) + File.separator
            require(resolved.path.startsWith(basePrefix)) {
                "Relative databaseName escapes base directory: $databaseName"
            }
            resolved
        }
    dbFile.parentFile?.mkdirs()
    return dbFile
}
