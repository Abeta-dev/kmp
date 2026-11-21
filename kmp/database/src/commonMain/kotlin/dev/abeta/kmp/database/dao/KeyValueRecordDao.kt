package dev.abeta.kmp.database.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import dev.abeta.kmp.database.entities.KeyValueRecordEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room 3 DAO managing namespaced key-value records with optional expiration in `key_value_records`.
 */
@Dao
interface KeyValueRecordDao {
    /** Inserts or replaces a [KeyValueRecordEntity] scoped by `(namespace, recordKey)`. */
    @Upsert
    suspend fun upsert(record: KeyValueRecordEntity)

    /** Returns the full record for [namespace] and [key], or `null` if absent. */
    @Query(
        """
        SELECT * FROM key_value_records
        WHERE namespace = :namespace AND recordKey = :key
        LIMIT 1
        """,
    )
    suspend fun get(
        namespace: String,
        key: String,
    ): KeyValueRecordEntity?

    /** Emits reactive updates to the stored value text for [namespace] and [key], or `null` if absent. */
    @Query(
        """
        SELECT valueText FROM key_value_records
        WHERE namespace = :namespace AND recordKey = :key
        LIMIT 1
        """,
    )
    fun observe(
        namespace: String,
        key: String,
    ): Flow<String?>

    /** Lists all stored keys in [namespace], ordered lexicographically. */
    @Query(
        """
        SELECT recordKey FROM key_value_records
        WHERE namespace = :namespace
        ORDER BY recordKey ASC
        """,
    )
    suspend fun listKeys(namespace: String): List<String>

    /** Deletes a single key in [namespace] and returns the affected row count. */
    @Query(
        """
        DELETE FROM key_value_records
        WHERE namespace = :namespace AND recordKey = :key
        """,
    )
    suspend fun delete(
        namespace: String,
        key: String,
    ): Int

    /** Deletes every record stored under [namespace] and returns the affected row count. */
    @Query("DELETE FROM key_value_records WHERE namespace = :namespace")
    suspend fun deleteNamespace(namespace: String): Int

    /** Deletes all records whose [KeyValueRecordEntity.expiresAtEpochMillis] is at or before [nowEpochMillis]. */
    @Query(
        """
        DELETE FROM key_value_records
        WHERE expiresAtEpochMillis IS NOT NULL AND expiresAtEpochMillis <= :nowEpochMillis
        """,
    )
    suspend fun deleteExpired(nowEpochMillis: Long): Int
}
