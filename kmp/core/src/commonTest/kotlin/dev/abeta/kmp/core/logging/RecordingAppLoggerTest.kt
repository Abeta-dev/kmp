package dev.abeta.kmp.core.logging

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecordingAppLoggerTest {
    @Test
    fun `records entries at or above minLevel and supports clear`() {
        val logger = RecordingAppLogger(minLevel = LogLevel.INFO)
        val boom = IllegalStateException("boom")

        logger.verbose("Net", "ignored verbose")
        logger.debug("Net", "ignored debug")
        logger.info("Net", "connected")
        logger.warn("Net", "slow response")
        logger.error("Net", "request failed", boom)

        assertEquals(3, logger.entries.size)
        assertEquals(LogEntry(LogLevel.INFO, "Net", "connected", null), logger.entries[0])
        assertEquals(LogEntry(LogLevel.WARN, "Net", "slow response", null), logger.entries[1])
        assertEquals(LogEntry(LogLevel.ERROR, "Net", "request failed", boom), logger.entries[2])

        logger.clear()
        assertTrue(logger.entries.isEmpty())
    }

    @Test
    fun `no-op logger accepts all log levels without throwing`() {
        val logger: AppLogger = NoOpAppLogger()
        logger.verbose("Tag", "v")
        logger.debug("Tag", "d")
        logger.info("Tag", "i")
        logger.warn("Tag", "w")
        logger.error("Tag", "e", RuntimeException("ignored"))
    }
}
