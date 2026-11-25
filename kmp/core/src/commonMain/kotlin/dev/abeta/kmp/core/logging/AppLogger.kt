package dev.abeta.kmp.core.logging

import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import dev.abeta.kmp.core.security.SensitiveDataRedactor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Severity levels supported by [AppLogger].
 */
enum class LogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

/**
 * Platform-agnostic structured logging port.
 */
interface AppLogger {
    /** Emits a log entry at [level] with [tag], [message], and optional [throwable]. */
    fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
    )

    /** Logs a [LogLevel.VERBOSE] message. */
    fun verbose(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        log(LogLevel.VERBOSE, tag, message, throwable)
    }

    /** Logs a [LogLevel.DEBUG] message. */
    fun debug(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        log(LogLevel.DEBUG, tag, message, throwable)
    }

    /** Logs a [LogLevel.INFO] message. */
    fun info(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        log(LogLevel.INFO, tag, message, throwable)
    }

    /** Logs a [LogLevel.WARN] message. */
    fun warn(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        log(LogLevel.WARN, tag, message, throwable)
    }

    /** Logs a [LogLevel.ERROR] message. */
    fun error(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        log(LogLevel.ERROR, tag, message, throwable)
    }
}

/**
 * [AppLogger] implementation backed by Touchlab Kermit.
 */
class KermitAppLogger(
    private val minLevel: LogLevel = LogLevel.VERBOSE,
    private val delegate: Logger = Logger,
) : AppLogger {
    override fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        if (level.ordinal < minLevel.ordinal) {
            return
        }
        val severity = level.toKermitSeverity()
        if (delegate.config.minSeverity <= severity) {
            delegate.log(
                severity = severity,
                tag = tag,
                throwable = throwable,
                message = message,
            )
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KermitAppLogger) return false
        return minLevel == other.minLevel &&
            delegate.tag == other.delegate.tag &&
            delegate.config.minSeverity == other.delegate.config.minSeverity
    }

    override fun hashCode(): Int {
        var result = minLevel.hashCode()
        result = HASH_MULTIPLIER * result + delegate.tag.hashCode()
        result = HASH_MULTIPLIER * result + delegate.config.minSeverity.hashCode()
        return result
    }

    private fun LogLevel.toKermitSeverity(): Severity =
        when (this) {
            LogLevel.VERBOSE -> Severity.Verbose
            LogLevel.DEBUG -> Severity.Debug
            LogLevel.INFO -> Severity.Info
            LogLevel.WARN -> Severity.Warn
            LogLevel.ERROR -> Severity.Error
        }

    private companion object {
        const val HASH_MULTIPLIER = 31
    }
}

/**
 * [AppLogger] decorator that fans out every log entry to multiple downstream [loggers].
 */
class CompositeAppLogger(
    private val loggers: List<AppLogger>,
) : AppLogger {
    constructor(vararg loggers: AppLogger) : this(loggers.toList())

    override fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        loggers.forEach { logger -> logger.log(level, tag, message, throwable) }
    }
}

/**
 * [AppLogger] decorator that redacts sensitive credentials and PII patterns before forwarding logs.
 */
class RedactingAppLogger(
    private val delegate: AppLogger,
    private val redactor: SensitiveDataRedactor = SensitiveDataRedactor.Default,
) : AppLogger {
    override fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        delegate.log(
            level = level,
            tag = tag,
            message = redactor.redactText(message),
            throwable = throwable,
        )
    }
}

/**
 * [AppLogger] implementation that silently discards all log entries.
 */
class NoOpAppLogger : AppLogger {
    override fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) = Unit
}

/**
 * Captured log record produced by [RecordingAppLogger].
 */
data class LogEntry(
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null,
)

/**
 * Thread-safe in-memory [AppLogger] that records emitted entries for verification in tests.
 */
class RecordingAppLogger(
    private val minLevel: LogLevel = LogLevel.VERBOSE,
) : AppLogger {
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())

    /** Snapshot of all recorded log entries in insertion order. */
    val entries: List<LogEntry>
        get() = _entries.value

    override fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable?,
    ) {
        if (level.ordinal < minLevel.ordinal) {
            return
        }
        val entry =
            LogEntry(
                level = level,
                tag = tag,
                message = message,
                throwable = throwable,
            )
        _entries.update { current -> current + entry }
    }

    /** Clears all previously recorded log entries. */
    fun clear() {
        _entries.value = emptyList()
    }
}
