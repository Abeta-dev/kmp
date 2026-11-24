package dev.abeta.kmp.state

import app.cash.turbine.test
import dev.abeta.kmp.state.storage.FileKeyValueStore
import dev.abeta.kmp.state.storage.createDiskKeyValueStore
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileKeyValueStoreJvmTest {
    @Test
    fun `persists values across fresh FileKeyValueStore instances`() {
        val tempDir = Files.createTempDirectory("abeta-file-kv-restart").toFile()
        try {
            val writer = FileKeyValueStore(directoryPath = tempDir.absolutePath, namespace = "screens")
            writer.putString("screen:/home", """{"slug":"home","title":"Welcome"}""")
            writer.putString("session/token?v=1&mode=dark", "tok-xyz")
            writer.putString("ephemeral", "delete-me")
            writer.remove("ephemeral")

            val reopened = FileKeyValueStore(directoryPath = tempDir.absolutePath, namespace = "screens")
            assertEquals("""{"slug":"home","title":"Welcome"}""", reopened.getString("screen:/home"))
            assertEquals("tok-xyz", reopened.getString("session/token?v=1&mode=dark"))
            assertNull(reopened.getString("ephemeral"))
            assertTrue(reopened.contains("screen:/home"))
            assertFalse(reopened.contains("ephemeral"))
            assertEquals(setOf("screen:/home", "session/token?v=1&mode=dark"), reopened.keys())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `persists payloads larger than 64 KB without JVM Preferences 8 KB limit`() {
        val tempDir = Files.createTempDirectory("abeta-file-kv-large").toFile()
        try {
            val largeItems =
                (1..600).joinToString(",") { idx ->
                    """{"id":"item-$idx","title":"Abeta Product #$idx","description":"${"x".repeat(120)}"}"""
                }
            val largeJson = """{"slug":"fresh-home","items":[$largeItems]}"""
            assertTrue(
                largeJson.toByteArray(Charsets.UTF_8).size > 64 * 1024,
                "Expected test JSON payload to exceed 64 KB",
            )

            val writer =
                createDiskKeyValueStore(
                    namespace = "sdui_cache",
                    directoryPath = tempDir.absolutePath,
                )
            writer.putString("screen:fresh-home", largeJson)

            val reader =
                createDiskKeyValueStore(
                    namespace = "sdui_cache",
                    directoryPath = tempDir.absolutePath,
                )
            assertEquals(largeJson, reader.getString("screen:fresh-home"))
            assertTrue(reader.contains("screen:fresh-home"))
            assertEquals(setOf("screen:fresh-home"), reader.keys())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `isolates namespaces cleanly on disk during reads writes and clear`() {
        val tempDir = Files.createTempDirectory("abeta-file-kv-ns").toFile()
        try {
            val alpha = FileKeyValueStore(directoryPath = tempDir.absolutePath, namespace = "alpha")
            val beta = FileKeyValueStore(directoryPath = tempDir.absolutePath, namespace = "beta")

            alpha.putString("shared_key", "alpha-value")
            alpha.putString("alpha_only", "1")
            beta.putString("shared_key", "beta-value")
            beta.putString("beta_only", "2")

            assertEquals("alpha-value", alpha.getString("shared_key"))
            assertEquals("beta-value", beta.getString("shared_key"))
            assertEquals(setOf("shared_key", "alpha_only"), alpha.keys())
            assertEquals(setOf("shared_key", "beta_only"), beta.keys())

            alpha.clear()
            assertTrue(alpha.keys().isEmpty())
            assertNull(alpha.getString("shared_key"))
            assertEquals("beta-value", beta.getString("shared_key"))
            assertEquals(setOf("shared_key", "beta_only"), beta.keys())

            val reopenedAlpha = FileKeyValueStore(directoryPath = tempDir.absolutePath, namespace = "alpha")
            val reopenedBeta = FileKeyValueStore(directoryPath = tempDir.absolutePath, namespace = "beta")
            assertTrue(reopenedAlpha.keys().isEmpty())
            assertEquals("beta-value", reopenedBeta.getString("shared_key"))
            assertEquals("2", reopenedBeta.getString("beta_only"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `observeKey emits initial value updates removals and clear distinctly`() =
        runTest {
            val tempDir = Files.createTempDirectory("abeta-file-kv-observe").toFile()
            try {
                val store = FileKeyValueStore(directoryPath = tempDir.absolutePath, namespace = "reactive")

                store.observeKey("theme").test {
                    assertNull(awaitItem())

                    store.putString("theme", "dark")
                    assertEquals("dark", awaitItem())

                    // Duplicate value must not emit a redundant event.
                    store.putString("theme", "dark")
                    store.putString("unrelated", "ignored")
                    expectNoEvents()

                    store.putString("theme", "light")
                    assertEquals("light", awaitItem())

                    store.remove("theme")
                    assertNull(awaitItem())

                    store.putString("theme", "emerald")
                    assertEquals("emerald", awaitItem())

                    store.clear()
                    assertNull(awaitItem())

                    cancelAndIgnoreRemainingEvents()
                }
            } finally {
                tempDir.deleteRecursively()
            }
        }

    @Test
    fun `recovers gracefully from corrupted index and missing entry files`() {
        val tempDir = Files.createTempDirectory("abeta-file-kv-corrupt").toFile()
        try {
            val nsDir = File(tempDir, "corrupt_ns").apply { mkdirs() }
            File(nsDir, "index.json").writeText("{not valid json!!!")

            val recovered = FileKeyValueStore(directoryPath = tempDir.absolutePath, namespace = "corrupt_ns")
            assertTrue(recovered.keys().isEmpty())
            recovered.putString("good", "value-1")
            recovered.putString("missing_target", "value-2")

            // Remove one entry file behind the store's back and verify graceful reload.
            val entryFiles = nsDir.listFiles { file -> file.name.endsWith(".entry") }.orEmpty()
            assertEquals(2, entryFiles.size)
            val victim = entryFiles.first { it.readText() == "value-2" }
            assertTrue(victim.delete())

            val reloaded = FileKeyValueStore(directoryPath = tempDir.absolutePath, namespace = "corrupt_ns")
            assertEquals("value-1", reloaded.getString("good"))
            assertNull(reloaded.getString("missing_target"))
            assertEquals(setOf("good"), reloaded.keys())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `supports concurrent reads and writes safely across threads`() {
        val tempDir = Files.createTempDirectory("abeta-file-kv-concurrent").toFile()
        val executor = Executors.newFixedThreadPool(4)
        try {
            val store = FileKeyValueStore(directoryPath = tempDir.absolutePath, namespace = "threaded")
            val workers = 4
            val keysPerWorker = 20
            val latch = CountDownLatch(workers)
            val errors = CopyOnWriteArrayList<Throwable>()

            for (worker in 0 until workers) {
                executor.submit {
                    try {
                        for (i in 0 until keysPerWorker) {
                            val key = "w${worker}_k$i"
                            val value = "payload_${worker}_$i"
                            store.putString(key, value)
                            assertEquals(value, store.getString(key))
                        }
                    } catch (t: Throwable) {
                        errors.add(t)
                    } finally {
                        latch.countDown()
                    }
                }
            }

            assertTrue(latch.await(15, TimeUnit.SECONDS))
            assertTrue(errors.isEmpty(), "Unexpected concurrent errors: $errors")
            assertEquals(workers * keysPerWorker, store.keys().size)

            val reopened = FileKeyValueStore(directoryPath = tempDir.absolutePath, namespace = "threaded")
            assertEquals(workers * keysPerWorker, reopened.keys().size)
        } finally {
            executor.shutdownNow()
            tempDir.deleteRecursively()
        }
    }
}
