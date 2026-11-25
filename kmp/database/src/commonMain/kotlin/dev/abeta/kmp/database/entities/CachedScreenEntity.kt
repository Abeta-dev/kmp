package dev.abeta.kmp.database.entities

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

/**
 * Durable SQLite entity storing serialized SDUI screen payloads and optional HTTP ETag / TTL metadata.
 */
@Entity(
    tableName = "cached_screens",
    indices = [
        Index(value = ["expiresAtEpochMillis"]),
    ],
)
data class CachedScreenEntity(
    @PrimaryKey
    val screenId: String,
    val payloadJson: String,
    val etag: String? = null,
    val cachedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long? = null,
    val pageVersion: String? = null,
    val appVersion: String? = null,
)
