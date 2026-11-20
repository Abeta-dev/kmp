package dev.abeta.kmp.core.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InMemoryKeyValueStoreTest {
    @Test
    fun `put get contains remove keys and clear behave consistently`() {
        val store: KeyValueStore = InMemoryKeyValueStore(initialValues = mapOf("seed" to "1"))

        assertTrue(store.contains("seed"))
        assertEquals("1", store.getString("seed"))
        assertEquals(setOf("seed"), store.keys())

        store.putString("token", "abc")
        assertTrue(store.contains("token"))
        assertEquals("abc", store.getString("token"))
        assertEquals(setOf("seed", "token"), store.keys())

        store.putString("token", "xyz")
        assertEquals("xyz", store.getString("token"))

        store.remove("seed")
        assertFalse(store.contains("seed"))
        assertNull(store.getString("seed"))
        assertEquals(setOf("token"), store.keys())

        store.clear()
        assertFalse(store.contains("token"))
        assertTrue(store.keys().isEmpty())
    }
}
