package dev.abeta.kmp.core.time

import kotlin.test.Test
import kotlin.test.assertTrue

class SystemClockTest {
    @Test
    fun `system clock returns positive monotonic epoch millis`() {
        val clock: Clock = SystemClock()
        val first = clock.nowEpochMillis()
        val second = clock.nowEpochMillis()

        assertTrue(first > 0L)
        assertTrue(second >= first)
    }
}
