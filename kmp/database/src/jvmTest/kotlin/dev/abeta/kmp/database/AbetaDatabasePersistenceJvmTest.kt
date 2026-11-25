package dev.abeta.kmp.database

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dev.abeta.kmp.core.time.MutableClock
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AbetaDatabasePersistenceJvmTest {
    @Test
    fun fileBackedDatabasePersistsAcrossCloseAndReopen() =
        runTest {
            val tempDir = Files.createTempDirectory("abeta-room3-persistence").toFile()
            val dbFile = File(tempDir, "abeta_roundtrip.db")
            val clock = MutableClock(500_000L)

            try {
                val config = AbetaDatabaseConfig(databaseName = dbFile.absolutePath, inMemory = false)
                createAbetaLocalDatabase(config = config, clock = clock).let { writer ->
                    try {
                        seedPersistenceRecords(writer)
                    } finally {
                        writer.close()
                    }
                }

                createAbetaLocalDatabase(config = config, clock = clock).let { reader ->
                    try {
                        verifyPersistedRecords(reader)
                    } finally {
                        reader.close()
                    }
                }
            } finally {
                tempDir.deleteRecursively()
            }
        }

    private suspend fun seedPersistenceRecords(writer: AbetaLocalDatabase) {
        writer.putScreenCache(
            screenId = "catalog",
            payloadJson = """{"screen":"catalog","items":[1,2,3]}""",
            etag = "\"cat-42\"",
            ttlMillis = 60_000L,
        )
        writer.enqueueMutation(
            mutationType = "order_express",
            payloadJson = """{"sku":"milk-1l","qty":2}""",
            id = "mut-persist-1",
        )
        writer.markMutationRetry(id = "mut-persist-1", error = "offline")
        writer.putValue(namespace = "flags", key = "checkout_v2", value = "true")
    }

    private suspend fun verifyPersistedRecords(reader: AbetaLocalDatabase) {
        val cached = reader.getFreshScreenCache("catalog")
        assertNotNull(cached)
        assertEquals("""{"screen":"catalog","items":[1,2,3]}""", cached.payloadJson)
        assertEquals("\"cat-42\"", cached.etag)
        assertEquals(560_000L, cached.expiresAtEpochMillis)

        val mutations = reader.pendingMutations()
        assertEquals(1, mutations.size)
        assertEquals("mut-persist-1", mutations.first().id)
        assertEquals("order_express", mutations.first().mutationType)
        assertEquals(1, mutations.first().retryCount)
        assertEquals("offline", mutations.first().lastError)

        assertEquals("true", reader.getValue("flags", "checkout_v2"))
    }

    @Test
    fun migrationFromVersion1To2PreservesExistingRowsAndAddsVersionColumns() =
        runTest {
            val tempDir = Files.createTempDirectory("abeta-room3-migration").toFile()
            val dbFile = File(tempDir, "abeta_v1.db")
            try {
                seedVersion1Schema(dbFile)
                val localDb =
                    createAbetaLocalDatabase(
                        config = AbetaDatabaseConfig(databaseName = dbFile.absolutePath, inMemory = false),
                        clock = MutableClock(2_000L),
                    )
                try {
                    val migrated = localDb.getFreshScreenCache("legacy")
                    assertNotNull(migrated)
                    assertEquals("""{"ok":true}""", migrated.payloadJson)
                    assertNull(migrated.pageVersion)
                    assertNull(migrated.appVersion)

                    localDb.putScreenCache(
                        screenId = "v2-screen",
                        payloadJson = """{"v":2}""",
                        pageVersion = "2.0",
                        appVersion = "0.7.0",
                    )
                    val fresh = localDb.getFreshScreenCache("v2-screen")
                    assertNotNull(fresh)
                    assertEquals("2.0", fresh.pageVersion)
                    assertEquals("0.7.0", fresh.appVersion)
                } finally {
                    localDb.close()
                }
            } finally {
                tempDir.deleteRecursively()
            }
        }

    private fun seedVersion1Schema(dbFile: File) {
        val connection = BundledSQLiteDriver().open(dbFile.absolutePath)
        try {
            connection.execSQL("PRAGMA user_version = 1")
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `cached_screens` (" +
                    "`screenId` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, `etag` TEXT, " +
                    "`cachedAtEpochMillis` INTEGER NOT NULL, `expiresAtEpochMillis` INTEGER, " +
                    "PRIMARY KEY(`screenId`))",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_cached_screens_expiresAtEpochMillis` " +
                    "ON `cached_screens` (`expiresAtEpochMillis`)",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `offline_mutations` (" +
                    "`id` TEXT NOT NULL, `mutationType` TEXT NOT NULL, `payloadJson` TEXT NOT NULL, " +
                    "`createdAtEpochMillis` INTEGER NOT NULL, `retryCount` INTEGER NOT NULL, " +
                    "`lastError` TEXT, PRIMARY KEY(`id`))",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_offline_mutations_createdAtEpochMillis` " +
                    "ON `offline_mutations` (`createdAtEpochMillis`)",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_offline_mutations_retryCount` " +
                    "ON `offline_mutations` (`retryCount`)",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `key_value_records` (" +
                    "`namespace` TEXT NOT NULL, `recordKey` TEXT NOT NULL, `valueText` TEXT NOT NULL, " +
                    "`updatedAtEpochMillis` INTEGER NOT NULL, `expiresAtEpochMillis` INTEGER, " +
                    "PRIMARY KEY(`namespace`, `recordKey`))",
            )
            connection.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_key_value_records_expiresAtEpochMillis` " +
                    "ON `key_value_records` (`expiresAtEpochMillis`)",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)",
            )
            connection.execSQL(
                "INSERT OR REPLACE INTO room_master_table (id, identity_hash) " +
                    "VALUES(42, 'e1e244018e7080771352b6f30a0a5980')",
            )
            connection.execSQL(
                "INSERT INTO cached_screens (screenId, payloadJson, etag, cachedAtEpochMillis, " +
                    "expiresAtEpochMillis) VALUES ('legacy', '{\"ok\":true}', '\"v1\"', 1000, NULL)",
            )
        } finally {
            connection.close()
        }
    }

    @Test
    fun jvmDatabasePathRejectsParentTraversalInRelativeDatabaseName() {
        assertFailsWith<IllegalArgumentException> {
            resolveJvmDatabasePath("../escape.db")
        }
    }
}
