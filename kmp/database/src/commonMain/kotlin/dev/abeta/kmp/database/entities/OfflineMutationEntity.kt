package dev.abeta.kmp.database.entities

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Durable SQLite entity representing a queued offline mutation waiting to synchronize with a backend.
 */
@Entity(
    tableName = "offline_mutations",
    indices = [
        Index(value = ["createdAtEpochMillis"]),
        Index(value = ["retryCount"]),
    ],
)
data class OfflineMutationEntity(
    @PrimaryKey
    val id: String,
    val mutationType: String,
    val payloadJson: String,
    val createdAtEpochMillis: Long,
    val retryCount: Int = 0,
    val lastError: String? = null,
)
