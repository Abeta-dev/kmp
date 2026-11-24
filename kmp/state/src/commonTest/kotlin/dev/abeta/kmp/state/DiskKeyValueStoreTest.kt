package dev.abeta.kmp.state

import app.cash.turbine.test
import dev.abeta.kmp.state.storage.createDiskKeyValueStore
import dev.abeta.kmp.state.storage.sanitizeDiskNamespace
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DiskKeyValueStoreTest {
    @Test
    fun `sanitizeDiskNamespace normalizes unsafe characters and traversal patterns`() {
        assertEquals("abeta_disk_cache", sanitizeDiskNamespace(""))
        assertEquals("abeta_disk_cache", sanitizeDiskNamespace("   "))
        assertEquals("abeta_disk_cache", sanitizeDiskNamespace(".."))
        assertEquals("abeta_disk_cache", sanitizeDiskNamespace("..."))
        assertEquals("sdui.screens-v1", sanitizeDiskNamespace(" sdui.screens-v1 "))
        assertEquals("user_session_1", sanitizeDiskNamespace("user/session:1"))
        assertEquals("_etc_passwd", sanitizeDiskNamespace("../etc/passwd"))
        assertEquals("a_b", sanitizeDiskNamespace("a..b"))
    }

    @Test
    fun `createDiskKeyValueStore supports CRUD and large payloads`() {
        val store = createDiskKeyValueStore(namespace = "abeta_disk_common_crud")
        store.clear()
        try {
            assertNull(store.getString("missing"))
            assertFalse(store.contains("missing"))
            assertTrue(store.keys().isEmpty())

            val largePayload = """{"slug":"bites-home","body":"${"A".repeat(70_000)}"}"""
            store.putString("screen:bites", largePayload)
            store.putString("locale", "en-US")
            store.putString("empty", "")

            assertEquals(largePayload, store.getString("screen:bites"))
            assertEquals("en-US", store.getString("locale"))
            assertEquals("", store.getString("empty"))
            assertTrue(store.contains("screen:bites"))
            assertTrue(store.contains("empty"))
            assertEquals(setOf("screen:bites", "locale", "empty"), store.keys())

            store.putString("locale", "fr-FR")
            assertEquals("fr-FR", store.getString("locale"))

            store.remove("locale")
            assertNull(store.getString("locale"))
            assertFalse(store.contains("locale"))
            assertEquals(setOf("screen:bites", "empty"), store.keys())

            store.clear()
            assertTrue(store.keys().isEmpty())
            assertNull(store.getString("screen:bites"))
        } finally {
            store.clear()
        }
    }

    @Test
    fun `createDiskKeyValueStore isolates namespaces on clear and keys`() {
        val storeA = createDiskKeyValueStore(namespace = "abeta_disk_common_iso_a")
        val storeB = createDiskKeyValueStore(namespace = "abeta_disk_common_iso_b")
        storeA.clear()
        storeB.clear()
        try {
            storeA.putString("token", "alpha-token")
            storeA.putString("only_a", "a")
            storeB.putString("token", "beta-token")
            storeB.putString("only_b", "b")

            assertEquals("alpha-token", storeA.getString("token"))
            assertEquals("beta-token", storeB.getString("token"))
            assertEquals(setOf("token", "only_a"), storeA.keys())
            assertEquals(setOf("token", "only_b"), storeB.keys())

            storeA.clear()
            assertTrue(storeA.keys().isEmpty())
            assertNull(storeA.getString("token"))
            assertEquals("beta-token", storeB.getString("token"))
            assertEquals(setOf("token", "only_b"), storeB.keys())
        } finally {
            storeA.clear()
            storeB.clear()
        }
    }

    @Test
    fun `createDiskKeyValueStore observeKey reacts distinctly to updates remove and clear`() =
        runTest {
            val store = createDiskKeyValueStore(namespace = "abeta_disk_common_obs")
            store.clear()
            try {
                store.observeKey("cart_badge").test {
                    assertNull(awaitItem())

                    store.putString("cart_badge", "1")
                    assertEquals("1", awaitItem())

                    // Identical write or unrelated key must not re-emit.
                    store.putString("cart_badge", "1")
                    store.putString("other", "x")
                    expectNoEvents()

                    store.putString("cart_badge", "3")
                    assertEquals("3", awaitItem())

                    store.remove("cart_badge")
                    assertNull(awaitItem())

                    store.putString("cart_badge", "5")
                    assertEquals("5", awaitItem())

                    store.clear()
                    assertNull(awaitItem())

                    cancelAndIgnoreRemainingEvents()
                }
            } finally {
                store.clear()
            }
        }
}
