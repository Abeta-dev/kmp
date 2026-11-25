package dev.abeta.kmp.core.security

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JvmSecureStoreTest {
    @Test
    fun fileBackedSecureStoreHandlesLargePayloadsLongKeysAndReportsSoftwareEncryption() {
        val tempRoot = Files.createTempDirectory("abeta-sec-test")
        try {
            val store = JvmSecureStore(serviceName = "abeta.test.service", baseDirectoryOverride = tempRoot)
            assertFalse(store.isHardwareOrOsEncrypted)

            val longKey = "user_session_key_" + "k".repeat(120)
            val largePayload = "secret-chunk-".repeat(2000) // > 25 KB
            store.putString(longKey, largePayload)
            store.putString("short", "val-1")

            assertEquals(largePayload, store.getString(longKey))
            assertEquals("val-1", store.getString("short"))
            assertEquals(setOf(longKey, "short"), store.keys())
            assertTrue(store.contains(longKey))

            // Second instance sharing the same directory reads the same master key & values.
            val reopened = JvmSecureStore(serviceName = "abeta.test.service", baseDirectoryOverride = tempRoot)
            assertEquals(largePayload, reopened.getString(longKey))
            assertEquals(setOf(longKey, "short"), reopened.keys())

            // Verify POSIX 0600 permissions when supported by host filesystem.
            val serviceDir = tempRoot.resolve("abeta.test.service")
            val masterKey = serviceDir.resolve("master.key")
            assertTrue(Files.exists(masterKey))
            runCatching {
                val perms = PosixFilePermissions.toString(Files.getPosixFilePermissions(masterKey))
                assertEquals("rw-------", perms)
            }

            reopened.remove("short")
            assertNull(reopened.getString("short"))
            reopened.clear()
            assertTrue(reopened.keys().isEmpty())
            assertNull(reopened.getString(longKey))
        } finally {
            tempRoot.toFile().deleteRecursively()
        }
    }
}
