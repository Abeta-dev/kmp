@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.database

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.abeta.kmp.core.coroutines.ioDispatcher
import java.io.File

internal actual fun createRawAbetaDatabaseBuilder(config: AbetaDatabaseConfig): RoomDatabase.Builder<AbetaDatabase> =
    if (config.inMemory) {
        Room.inMemoryDatabaseBuilder<AbetaDatabase>()
    } else {
        Room.databaseBuilder<AbetaDatabase>(
            name = resolveJvmDatabasePath(config.databaseName),
        )
    }

actual fun createAbetaDatabaseBuilder(config: AbetaDatabaseConfig): RoomDatabase.Builder<AbetaDatabase> =
    createRawAbetaDatabaseBuilder(config).apply {
        setDriver(BundledSQLiteDriver())
        setQueryCoroutineContext(config.queryCoroutineContext ?: ioDispatcher)
    }

private fun resolveJvmDatabasePath(databaseName: String): String {
    val trimmed = databaseName.trim()
    val candidate = File(trimmed)
    val dbFile =
        if (candidate.isAbsolute) {
            candidate
        } else {
            val baseRoot =
                System
                    .getProperty("user.home")
                    ?.takeIf { it.isNotBlank() }
                    ?.let { File(it, ".abeta-kmp/db") }
                    ?: File(System.getProperty("java.io.tmpdir", "."), "abeta-kmp/db")
            File(baseRoot, trimmed)
        }
    dbFile.parentFile?.mkdirs()
    return dbFile.absolutePath
}
