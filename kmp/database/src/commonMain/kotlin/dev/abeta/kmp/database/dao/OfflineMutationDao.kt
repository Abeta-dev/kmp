package dev.abeta.kmp.database.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import dev.abeta.kmp.database.entities.OfflineMutationEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room 3 DAO managing the durable FIFO offline mutation outbox in `offline_mutations`.
 */
@Dao
interface OfflineMutationDao {
    /** Inserts or updates a queued [OfflineMutationEntity]. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: OfflineMutationEntity)

    /** Returns all pending mutations in deterministic FIFO order (`createdAtEpochMillis`, `rowid`). */
    @Query("SELECT * FROM offline_mutations ORDER BY createdAtEpochMillis ASC, rowid ASC")
    suspend fun listAllOrdered(): List<OfflineMutationEntity>

    /** Emits reactive FIFO snapshots of all pending mutations. */
    @Query("SELECT * FROM offline_mutations ORDER BY createdAtEpochMillis ASC, rowid ASC")
    fun observeAllOrdered(): Flow<List<OfflineMutationEntity>>

    /** Emits the reactive count of currently queued offline mutations. */
    @Query("SELECT COUNT(*) FROM offline_mutations")
    fun observePendingCount(): Flow<Int>

    /** Increments [OfflineMutationEntity.retryCount] and stores the latest [error] diagnostic for [id]. */
    @Query(
        """
        UPDATE offline_mutations
        SET retryCount = retryCount + 1, lastError = :error
        WHERE id = :id
        """,
    )
    suspend fun incrementRetry(
        id: String,
        error: String? = null,
    ): Int

    /** Removes a single queued mutation by [id] and returns the affected row count. */
    @Query("DELETE FROM offline_mutations WHERE id = :id")
    suspend fun deleteById(id: String): Int

    /** Purges dead-letter mutations whose `retryCount >= maxRetries` and returns the deleted row count. */
    @Query("DELETE FROM offline_mutations WHERE retryCount >= :maxRetries")
    suspend fun deleteExceededRetries(maxRetries: Int): Int

    /**
     * Retains at most the newest [maxSize] mutations and deletes older overflow rows.
     *
     * Returns the number of evicted rows.
     */
    @Query(
        """
        DELETE FROM offline_mutations
        WHERE id NOT IN (
            SELECT id FROM offline_mutations
            ORDER BY createdAtEpochMillis DESC, rowid DESC
            LIMIT MAX(:maxSize, 0)
        )
        """,
    )
    suspend fun trimToMaxSize(maxSize: Int): Int

    /** Deletes all queued offline mutations. */
    @Query("DELETE FROM offline_mutations")
    suspend fun clearAll()
}
