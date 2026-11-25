package dev.abeta.kmp.database

import androidx.sqlite.SQLiteConnection

internal expect suspend fun executeMigrationSql(
    connection: SQLiteConnection,
    sql: String,
)
