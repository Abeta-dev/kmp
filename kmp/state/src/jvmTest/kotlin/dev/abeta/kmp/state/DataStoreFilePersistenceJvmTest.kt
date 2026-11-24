package dev.abeta.kmp.state

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import dev.abeta.kmp.state.storage.DataStoreKeyValueStore
import dev.abeta.kmp.state.storage.createDataStoreKeyValueStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toPath
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class DataStoreFilePersistenceJvmTest {
    @Test
    fun `file-backed DataStoreKeyValueStore persists atomically across reopen`() =
        runBlocking {
            val tempDir = Files.createTempDirectory("abeta-datastore-persistence").toFile()
            val prefsFile = File(tempDir, "abeta_roundtrip.preferences_pb")
            try {
                val writerJob = SupervisorJob()
                val writerScope = CoroutineScope(writerJob + Dispatchers.IO)
                val writerDataStore =
                    PreferenceDataStoreFactory.createWithPath(
                        scope = writerScope,
                        produceFile = { prefsFile.absolutePath.toPath() },
                    )
                val writer = DataStoreKeyValueStore(dataStore = writerDataStore, scope = writerScope)
                writer.putString("theme", "midnight")
                writer.putString("ephemeral", "temp")
                writer.remove("ephemeral")
                writer.awaitSync()
                writerJob.cancelAndJoin()

                val readerJob = SupervisorJob()
                val readerScope = CoroutineScope(readerJob + Dispatchers.IO)
                val readerDataStore =
                    PreferenceDataStoreFactory.createWithPath(
                        scope = readerScope,
                        produceFile = { prefsFile.absolutePath.toPath() },
                    )
                val reader = DataStoreKeyValueStore(dataStore = readerDataStore, scope = readerScope)
                reader.awaitSync()

                assertEquals("midnight", reader.getString("theme"))
                assertNull(reader.getString("ephemeral"))
                readerJob.cancelAndJoin()
            } finally {
                tempDir.deleteRecursively()
            }
        }

    @Test
    fun `createDataStoreKeyValueStore returns singleton per namespace on JVM`() =
        runBlocking {
            val namespace = "abeta_jvm_factory_test_${System.nanoTime()}"
            val first = createDataStoreKeyValueStore(namespace)
            val second = createDataStoreKeyValueStore(namespace)
            assertSame(first, second)
            first.putString("flag", "enabled")
            assertEquals("enabled", second.getString("flag"))
            first.clear()
            (first as? DataStoreKeyValueStore)?.awaitSync()
            assertNull(second.getString("flag"))
            val homeDir = System.getProperty("user.home")?.takeIf { it.isNotBlank() }
            if (homeDir != null) {
                File(homeDir, ".abeta-kmp/datastore/$namespace.preferences_pb").delete()
            }
        }
}
