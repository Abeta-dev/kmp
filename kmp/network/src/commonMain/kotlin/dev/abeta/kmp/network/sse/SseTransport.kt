package dev.abeta.kmp.network.sse

import dev.abeta.kmp.core.result.AppResult
import kotlinx.coroutines.flow.Flow

/**
 * Single Server-Sent Event decoded from a `text/event-stream` response.
 *
 * @property id Optional last event ID (`id:` field) carried by this event.
 * @property event Event type name (`event:` field), defaulting to [DEFAULT_EVENT_NAME] (`"message"`).
 * @property data Event payload formed by joining all `data:` lines with `\n`.
 * @property retryMillis Optional server-provided reconnection delay (`retry:` field) in milliseconds.
 */
data class SseEvent(
    val id: String? = null,
    val event: String = DEFAULT_EVENT_NAME,
    val data: String = "",
    val retryMillis: Long? = null,
) {
    companion object {
        const val DEFAULT_EVENT_NAME: String = "message"
    }
}

/**
 * Bounded exponential-backoff reconnection policy for [SseTransport] streams.
 */
data class SseReconnectPolicy(
    val enabled: Boolean = true,
    val maxReconnectAttempts: Int = DEFAULT_MAX_RECONNECT_ATTEMPTS,
    val initialDelayMillis: Long = DEFAULT_INITIAL_DELAY_MILLIS,
    val maxDelayMillis: Long = DEFAULT_MAX_DELAY_MILLIS,
) {
    init {
        require(maxReconnectAttempts >= 0) { "maxReconnectAttempts must be >= 0." }
        require(initialDelayMillis >= 0L) { "initialDelayMillis must be >= 0." }
        require(maxDelayMillis >= initialDelayMillis) { "maxDelayMillis must be >= initialDelayMillis." }
    }

    companion object {
        const val DEFAULT_MAX_RECONNECT_ATTEMPTS: Int = 5
        const val DEFAULT_INITIAL_DELAY_MILLIS: Long = 1_000L
        const val DEFAULT_MAX_DELAY_MILLIS: Long = 30_000L
    }
}

/**
 * Dependency-inverted streaming Server-Sent Events (SSE) transport returning a cold [Flow]
 * of typed [AppResult] items without leaking Ktor or engine exceptions.
 */
interface SseTransport {
    /**
     * Connects to an SSE endpoint at [path] and streams parsed [SseEvent] items incrementally.
     */
    fun connect(
        path: String,
        headers: Map<String, String> = emptyMap(),
        queryParams: Map<String, String> = emptyMap(),
        lastEventId: String? = null,
        reconnectPolicy: SseReconnectPolicy = SseReconnectPolicy(),
    ): Flow<AppResult<SseEvent>>
}

/**
 * Pure WHATWG Server-Sent Events line-by-line parser.
 *
 * Supports `event:`, multi-line `data:` joined by `\n`, `id:` (ignoring values containing U+0000),
 * `retry:` (non-negative ASCII digits), `:comment` lines, single leading space stripping after `:`,
 * CRLF endings, blank-line event dispatch, and [flush] at stream EOF.
 */
class SseLineParser(
    initialLastEventId: String? = null,
) {
    private var seenFirstLine: Boolean = false
    private var eventName: String? = null
    private val dataLines: MutableList<String> = mutableListOf()
    private var pendingRetryMillis: Long? = null

    /** Most recently observed valid `id:` field value (persists across events per WHATWG spec). */
    var lastEventId: String? = initialLastEventId
        private set

    /** Most recently observed valid `retry:` value in milliseconds, if any. */
    var lastRetryMillis: Long? = null
        private set

    /**
     * Feeds a single line (without `\n`; any trailing `\r` is stripped) into the parser.
     *
     * @return a completed [SseEvent] when a blank delimiter line dispatches buffered data, or `null`.
     */
    fun parseLine(rawLine: String): SseEvent? {
        val normalized = normalizeLine(rawLine)
        return if (normalized.isEmpty()) {
            dispatchEvent()
        } else {
            if (!normalized.startsWith(':')) {
                processFieldLine(normalized)
            }
            null
        }
    }

    /**
     * Dispatches any buffered event data at stream EOF and resets the per-event buffer.
     *
     * @return the pending [SseEvent] if at least one `data:` line was buffered, or `null`.
     */
    fun flush(): SseEvent? = dispatchEvent()

    private fun normalizeLine(rawLine: String): String {
        var line = rawLine
        if (!seenFirstLine) {
            seenFirstLine = true
            if (line.startsWith(UTF8_BOM)) {
                line = line.substring(1)
            }
        }
        return if (line.endsWith('\r')) line.dropLast(1) else line
    }

    private fun processFieldLine(line: String) {
        val colonIndex = line.indexOf(':')
        val fieldName: String
        val fieldValue: String
        if (colonIndex >= 0) {
            fieldName = line.substring(0, colonIndex)
            val rawValue = line.substring(colonIndex + 1)
            fieldValue = if (rawValue.startsWith(' ')) rawValue.substring(1) else rawValue
        } else {
            fieldName = line
            fieldValue = ""
        }
        applyField(fieldName, fieldValue)
    }

    private fun applyField(
        fieldName: String,
        fieldValue: String,
    ) {
        when (fieldName) {
            FIELD_EVENT -> {
                eventName = fieldValue
            }

            FIELD_DATA -> {
                dataLines.add(fieldValue)
            }

            FIELD_ID -> {
                if (NULL_CHAR !in fieldValue) {
                    lastEventId = fieldValue.ifEmpty { null }
                }
            }

            FIELD_RETRY -> {
                parseNonNegativeAsciiLong(fieldValue)?.let { parsed ->
                    pendingRetryMillis = parsed
                    lastRetryMillis = parsed
                }
            }
        }
    }

    private fun dispatchEvent(): SseEvent? {
        val resolvedEventName = eventName?.takeIf { it.isNotEmpty() } ?: SseEvent.DEFAULT_EVENT_NAME
        val retry = pendingRetryMillis
        eventName = null
        pendingRetryMillis = null

        return if (dataLines.isEmpty()) {
            null
        } else {
            val payload = dataLines.joinToString(separator = "\n")
            dataLines.clear()
            SseEvent(
                id = lastEventId,
                event = resolvedEventName,
                data = payload,
                retryMillis = retry,
            )
        }
    }

    companion object {
        private const val UTF8_BOM: Char = '\uFEFF'
        private const val NULL_CHAR: Char = '\u0000'
        private const val FIELD_EVENT: String = "event"
        private const val FIELD_DATA: String = "data"
        private const val FIELD_ID: String = "id"
        private const val FIELD_RETRY: String = "retry"
    }
}

private fun parseNonNegativeAsciiLong(raw: String): Long? {
    val isDigits = raw.isNotEmpty() && raw.all { it in '0'..'9' }
    return if (isDigits) raw.toLongOrNull() else null
}
