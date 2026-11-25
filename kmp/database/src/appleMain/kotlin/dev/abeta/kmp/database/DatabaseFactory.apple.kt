@file:OptIn(ExperimentalForeignApi::class)
@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.database

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import dev.abeta.kmp.core.coroutines.ioDispatcher
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

internal actual fun createRawAbetaDatabaseBuilder(config: AbetaDatabaseConfig): RoomDatabase.Builder<AbetaDatabase> =
    if (config.inMemory) {
        Room.inMemoryDatabaseBuilder<AbetaDatabase>()
    } else {
        Room.databaseBuilder<AbetaDatabase>(
            name = resolveAppleDatabasePath(config.databaseName),
        )
    }

actual fun createAbetaDatabaseBuilder(config: AbetaDatabaseConfig): RoomDatabase.Builder<AbetaDatabase> =
    createRawAbetaDatabaseBuilder(config).apply {
        addMigrations(AbetaDatabase.MIGRATION_1_2)
        setDriver(BundledSQLiteDriver())
        setQueryCoroutineContext(config.queryCoroutineContext ?: ioDispatcher)
    }

internal fun resolveAppleDatabasePath(databaseName: String): String {
    val trimmed = databaseName.trim()
    val fullPath =
        if (trimmed.startsWith("/")) {
            trimmed
        } else {
            requireSafeRelativeDatabaseName(trimmed)
            val appSupportDir =
                NSSearchPathForDirectoriesInDomains(
                    directory = NSApplicationSupportDirectory,
                    domainMask = NSUserDomainMask,
                    expandTilde = true,
                ).firstOrNull() as? String
                    ?: "${NSHomeDirectory()}/Library/Application Support"
            "$appSupportDir/abeta-kmp/$trimmed"
        }
    val parentDir = fullPath.substringBeforeLast('/', missingDelimiterValue = "")
    if (parentDir.isNotEmpty()) {
        NSFileManager.defaultManager.createDirectoryAtPath(
            path = parentDir,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    }
    return fullPath
}
