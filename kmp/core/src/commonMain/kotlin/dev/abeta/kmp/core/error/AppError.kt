package dev.abeta.kmp.core.error

/**
 * High-level semantic failure classification for cross-layer routing, retry policies, and UI states.
 */
enum class ErrorCategory {
    NETWORK,
    OFFLINE,
    TIMEOUT,
    RATE_LIMIT,
    AUTH,
    VALIDATION,
    SERIALIZATION,
    NOT_FOUND,
    CONFLICT,
    CANCELLED,
    UNEXPECTED,
}

/**
 * Typed domain error hierarchy shared across layers without leaking transport or SDK exceptions.
 */
sealed interface AppError {
    /** Stable machine-readable error code. */
    val code: String

    /** Human-readable diagnostic description. */
    val message: String

    /** Optional underlying cause for logging or debugging. */
    val cause: Throwable?
}

/**
 * Semantic [ErrorCategory] corresponding to this [AppError].
 */
val AppError.category: ErrorCategory
    get() =
        when (this) {
            is NetworkError -> ErrorCategory.NETWORK
            is OfflineError -> ErrorCategory.OFFLINE
            is TimeoutError -> ErrorCategory.TIMEOUT
            is RateLimitError -> ErrorCategory.RATE_LIMIT
            is UnauthorizedError, is ForbiddenError -> ErrorCategory.AUTH
            is ValidationError -> ErrorCategory.VALIDATION
            is SerializationError -> ErrorCategory.SERIALIZATION
            is NotFoundError -> ErrorCategory.NOT_FOUND
            is ConflictError -> ErrorCategory.CONFLICT
            is CancelledError -> ErrorCategory.CANCELLED
            is UnexpectedError -> ErrorCategory.UNEXPECTED
        }

/**
 * Returns `true` when the failure is typically transient and safe to retry with backoff.
 */
val AppError.isRetryable: Boolean
    get() =
        when (this) {
            is NetworkError -> statusCode == null || statusCode >= HTTP_SERVER_ERROR_MIN || statusCode == HTTP_TIMEOUT

            is TimeoutError, is RateLimitError, is OfflineError -> true

            is ValidationError,
            is SerializationError,
            is NotFoundError,
            is UnauthorizedError,
            is ForbiddenError,
            is ConflictError,
            is CancelledError,
            is UnexpectedError,
            -> false
        }

private const val HTTP_TIMEOUT = 408
private const val HTTP_SERVER_ERROR_MIN = 500

/**
 * Represents a transport, connectivity, or HTTP failure.
 */
data class NetworkError(
    val statusCode: Int? = null,
    override val message: String,
    override val code: String = DEFAULT_CODE,
    override val cause: Throwable? = null,
) : AppError {
    companion object {
        const val DEFAULT_CODE: String = "NETWORK_ERROR"
    }
}

/**
 * Represents a failure caused by no active network connection or offline mode.
 */
data class OfflineError(
    override val message: String = "No network connectivity available.",
    override val code: String = DEFAULT_CODE,
    override val cause: Throwable? = null,
) : AppError {
    companion object {
        const val DEFAULT_CODE: String = "OFFLINE_ERROR"
    }
}

/**
 * Represents an operation exceeding its allotted execution or network deadline.
 */
data class TimeoutError(
    val timeoutMillis: Long? = null,
    override val message: String,
    override val code: String = DEFAULT_CODE,
    override val cause: Throwable? = null,
) : AppError {
    companion object {
        const val DEFAULT_CODE: String = "TIMEOUT_ERROR"
    }
}

/**
 * Represents HTTP 429 or upstream throttling with an optional backoff hint in milliseconds.
 */
data class RateLimitError(
    val retryAfterMillis: Long? = null,
    override val message: String,
    override val code: String = DEFAULT_CODE,
    override val cause: Throwable? = null,
) : AppError {
    companion object {
        const val DEFAULT_CODE: String = "RATE_LIMITED"
    }
}

/**
 * Represents a client-side or contract validation failure.
 */
data class ValidationError(
    val field: String? = null,
    override val message: String,
    override val code: String = DEFAULT_CODE,
    override val cause: Throwable? = null,
) : AppError {
    companion object {
        const val DEFAULT_CODE: String = "VALIDATION_ERROR"
    }
}

/**
 * Represents a failure while encoding or decoding a payload.
 */
data class SerializationError(
    override val message: String,
    override val code: String = DEFAULT_CODE,
    override val cause: Throwable? = null,
) : AppError {
    companion object {
        const val DEFAULT_CODE: String = "SERIALIZATION_ERROR"
    }
}

/**
 * Represents a missing resource, handler, or registry entry.
 */
data class NotFoundError(
    val resource: String? = null,
    override val message: String,
    override val code: String = DEFAULT_CODE,
    override val cause: Throwable? = null,
) : AppError {
    companion object {
        const val DEFAULT_CODE: String = "NOT_FOUND"
    }
}

/**
 * Represents a state conflict such as HTTP 409 or duplicate entity creation.
 */
data class ConflictError(
    val resource: String? = null,
    override val message: String,
    override val code: String = DEFAULT_CODE,
    override val cause: Throwable? = null,
) : AppError {
    companion object {
        const val DEFAULT_CODE: String = "CONFLICT"
    }
}

/**
 * Represents an authentication failure (unauthenticated identity or expired credentials).
 */
data class UnauthorizedError(
    override val message: String,
    override val code: String = DEFAULT_CODE,
    override val cause: Throwable? = null,
) : AppError {
    companion object {
        const val DEFAULT_CODE: String = "UNAUTHORIZED"
    }
}

/**
 * Represents an authorization failure where an authenticated principal lacks permission.
 */
data class ForbiddenError(
    val resource: String? = null,
    override val message: String,
    override val code: String = DEFAULT_CODE,
    override val cause: Throwable? = null,
) : AppError {
    companion object {
        const val DEFAULT_CODE: String = "FORBIDDEN"
    }
}

/**
 * Represents a deliberately cancelled operation at domain boundaries.
 */
data class CancelledError(
    override val message: String = "Operation was cancelled.",
    override val code: String = DEFAULT_CODE,
    override val cause: Throwable? = null,
) : AppError {
    companion object {
        const val DEFAULT_CODE: String = "CANCELLED"
    }
}

/**
 * Fallback error for uncategorized unexpected failures.
 */
data class UnexpectedError(
    override val message: String,
    override val code: String = DEFAULT_CODE,
    override val cause: Throwable? = null,
) : AppError {
    companion object {
        const val DEFAULT_CODE: String = "UNEXPECTED_ERROR"
    }
}

/**
 * Exception bridge wrapping a domain [AppError] when interoperating with exception-based APIs.
 */
class AppErrorException(
    val appError: AppError,
) : RuntimeException("[${appError.code}] ${appError.message}", appError.cause)

/**
 * Wraps this [AppError] inside an [AppErrorException] for interop layers.
 */
fun AppError.toException(): AppErrorException = AppErrorException(this)
