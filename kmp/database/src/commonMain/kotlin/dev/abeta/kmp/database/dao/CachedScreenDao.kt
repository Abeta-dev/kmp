package dev.abeta.kmp.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import dev.abeta.kmp.database.entities.CachedScreenEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room 3 DAO managing durable cached SDUI screen documents in `cached_screens`.
 */
@Dao
interface CachedScreenDao {
    /** Inserts or replaces a [CachedScreenEntity] by primary key (`screenId`). */
    @Upsert
    suspend fun upsert(entity: CachedScreenEntity)

    /** Returns the cached screen row for [screenId], or `null` if not stored. */
    @Query("SELECT * FROM cached_screens WHERE screenId = :screenId LIMIT 1")
    suspend fun getById(screenId: String): CachedScreenEntity?

    /** Emits reactive updates whenever the row for [screenId] changes in SQLite. */
    @Query("SELECT * FROM cached_screens WHERE screenId = :screenId LIMIT 1")
    fun observeById(screenId: String): Flow<CachedScreenEntity?>

    /** Deletes the cached screen row for [screenId] and returns the affected row count. */
    @Query("DELETE FROM cached_screens WHERE screenId = :screenId")
    suspend fun deleteById(screenId: String): Int

    /** Deletes all entries whose [CachedScreenEntity.expiresAtEpochMillis] is at or before [nowEpochMillis]. */
    @Query(
        """
        DELETE FROM cached_screens
        WHERE expiresAtEpochMillis IS NOT NULL AND expiresAtEpochMillis <= :nowEpochMillis
        """,
    )
    suspend fun deleteExpired(nowEpochMillis: Long): Int

    /** Deletes every cached screen entry. */
    @Query("DELETE FROM cached_screens")
    suspend fun clearAll()

    /** Returns the total number of stored screen cache rows. */
    @Query("SELECT COUNT(*) FROM cached_screens")
    suspend fun count(): Int
}
