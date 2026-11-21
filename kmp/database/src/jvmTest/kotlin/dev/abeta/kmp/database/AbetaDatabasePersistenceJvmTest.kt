package dev.abeta.kmp.database

import dev.abeta.kmp.core.time.MutableClock
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

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
}
