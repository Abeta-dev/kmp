package dev.abeta.kmp.core.coroutines

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DispatcherProviderTest {
    @Test
    fun `default dispatcher provider exposes non-null io and default dispatchers`() =
        runTest {
            val provider: DispatcherProvider = DefaultDispatcherProvider()

            assertEquals(ioDispatcher, provider.io)
            assertEquals(Dispatchers.Default, provider.default)

            val result =
                withContext(provider.io) {
                    "executed-on-io"
                }
            assertEquals("executed-on-io", result)
            assertNotNull(ioDispatcher)
        }
}
