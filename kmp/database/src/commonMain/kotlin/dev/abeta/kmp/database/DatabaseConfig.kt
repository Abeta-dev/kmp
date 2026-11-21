@file:Suppress("ktlint:standard:filename", "MatchingDeclarationName")

package dev.abeta.kmp.database

import kotlin.coroutines.CoroutineContext

/**
 * Configuration options for opening an [AbetaDatabase] instance.
 *
 * @property databaseName File name (or OPFS/store identifier on WasmJS) used when [inMemory] is `false`.
 * @property inMemory When `true`, opens a transient in-memory SQLite database for tests or ephemeral sessions.
 * @property queryCoroutineContext Optional coroutine context passed to Room's query executor.
 */
data class AbetaDatabaseConfig(
    val databaseName: String = DEFAULT_DB_NAME,
    val inMemory: Boolean = false,
    val queryCoroutineContext: CoroutineContext? = null,
) {
    init {
        if (!inMemory) {
            require(databaseName.isNotBlank()) { "Database name must not be blank when inMemory is false." }
        }
    }

    companion object {
        /** Default SQLite file name used by persistent [AbetaDatabase] instances. */
        const val DEFAULT_DB_NAME: String = "abeta_kmp.db"
    }
}
