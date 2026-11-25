package dev.abeta.kmp.database

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

internal actual suspend fun executeMigrationSql(
    connection: SQLiteConnection,
    sql: String,
) {
    connection.execSQL(sql)
}
