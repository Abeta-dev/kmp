package dev.abeta.kmp.database

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import dev.abeta.kmp.database.dao.CachedScreenDao
import dev.abeta.kmp.database.dao.KeyValueRecordDao
import dev.abeta.kmp.database.dao.OfflineMutationDao
import dev.abeta.kmp.database.entities.CachedScreenEntity
import dev.abeta.kmp.database.entities.KeyValueRecordEntity
import dev.abeta.kmp.database.entities.OfflineMutationEntity

/**
 * Room 3 Kotlin Multiplatform local SQLite database for Abeta KMP Kit.
 *
 * Stores durable SDUI screen cache payloads, FIFO offline mutations, and namespaced key-value entries
 * across JVM, Android, iOS, macOS, watchOS, and WasmJS targets.
 */
@Database(
    entities = [
        CachedScreenEntity::class,
        OfflineMutationEntity::class,
        KeyValueRecordEntity::class,
    ],
    version = AbetaDatabase.SCHEMA_VERSION,
    exportSchema = true,
)
@ConstructedBy(AbetaDatabaseConstructor::class)
abstract class AbetaDatabase : RoomDatabase() {
    /** Returns the DAO managing cached SDUI screens. */
    abstract fun cachedScreenDao(): CachedScreenDao

    /** Returns the DAO managing the FIFO offline mutation outbox. */
    abstract fun offlineMutationDao(): OfflineMutationDao

    /** Returns the DAO managing namespaced key-value records. */
    abstract fun keyValueRecordDao(): KeyValueRecordDao

    companion object {
        /** Current exported Room 3 schema version. */
        const val SCHEMA_VERSION: Int = 1
    }
}

/**
 * Room 3 KSP-generated constructor bridging multiplatform [AbetaDatabase] instantiation
 * without JVM reflection.
 */
@Suppress("NO_ACTUAL_FOR_EXPECT", "KotlinNoActualForExpect")
internal expect object AbetaDatabaseConstructor : RoomDatabaseConstructor<AbetaDatabase> {
    override fun initialize(): AbetaDatabase
}
