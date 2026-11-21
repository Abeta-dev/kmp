package dev.abeta.kmp.database.entities

import androidx.room3.Entity
import androidx.room3.Index

/**
 * Durable SQLite entity storing a namespaced key-value string entry with optional TTL expiration.
 */
@Entity(
    tableName = "key_value_records",
    primaryKeys = ["namespace", "recordKey"],
    indices = [
        Index(value = ["expiresAtEpochMillis"]),
    ],
)
data class KeyValueRecordEntity(
    val namespace: String,
    val recordKey: String,
    val valueText: String,
    val updatedAtEpochMillis: Long,
    val expiresAtEpochMillis: Long? = null,
)
