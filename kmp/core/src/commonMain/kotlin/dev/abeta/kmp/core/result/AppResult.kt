package dev.abeta.kmp.core.result

import dev.abeta.kmp.core.error.AppError
import dev.abeta.kmp.core.error.AppErrorException
import dev.abeta.kmp.core.error.UnexpectedError
import kotlinx.coroutines.CancellationException

/**
 * Functional outcome type representing either a [Success] value or a typed [Failure].
 */
sealed interface AppResult<out T> {
    /** Indicates a successful computation holding [value]. */
    data class Success<out T>(
        val value: T,
    ) : AppResult<T>

    /** Indicates a failed computation holding an [AppError]. */
    data class Failure(
        val error: AppError,
    ) : AppResult<Nothing>
}

/**
 * Transforms the value of a [AppResult.Success] using [transform], leaving [AppResult.Failure] untouched.
 */
inline fun <T, R> AppResult<T>.map(transform: (T) -> R): AppResult<R> =
    when (this) {
        is AppResult.Success -> AppResult.Success(transform(value))
        is AppResult.Failure -> this
    }

/**
 * Transforms the [AppError] of an [AppResult.Failure] while preserving [AppResult.Success].
 */
inline fun <T> AppResult<T>.mapError(transform: (AppError) -> AppError): AppResult<T> =
    when (this) {
        is AppResult.Success -> this
        is AppResult.Failure -> AppResult.Failure(transform(error))
    }

/**
 * Chains another [AppResult]-returning computation when this result is [AppResult.Success].
 */
inline fun <T, R> AppResult<T>.flatMap(transform: (T) -> AppResult<R>): AppResult<R> =
    when (this) {
        is AppResult.Success -> transform(value)
        is AppResult.Failure -> this
    }

/**
 * Collapses both branches of an [AppResult] into a single value of type [R].
 */
inline fun <T, R> AppResult<T>.fold(
    onSuccess: (T) -> R,
    onFailure: (AppError) -> R,
): R =
    when (this) {
        is AppResult.Success -> onSuccess(value)
        is AppResult.Failure -> onFailure(error)
    }

/**
 * Invokes [action] when this result is [AppResult.Success] and returns the original [AppResult].
 */
inline fun <T> AppResult<T>.onSuccess(action: (T) -> Unit): AppResult<T> {
    if (this is AppResult.Success) {
        action(value)
    }
    return this
}

/**
 * Invokes [action] when this result is [AppResult.Failure] and returns the original [AppResult].
 */
inline fun <T> AppResult<T>.onFailure(action: (AppError) -> Unit): AppResult<T> {
    if (this is AppResult.Failure) {
        action(error)
    }
    return this
}

/**
 * Returns the encapsulated value if [AppResult.Success], or `null` otherwise.
 */
fun <T> AppResult<T>.getOrNull(): T? =
    when (this) {
        is AppResult.Success -> value
        is AppResult.Failure -> null
    }

/**
 * Returns the encapsulated [AppError] if [AppResult.Failure], or `null` otherwise.
 */
fun <T> AppResult<T>.errorOrNull(): AppError? =
    when (this) {
        is AppResult.Success -> null
        is AppResult.Failure -> error
    }

/**
 * Executes [block], wrapping its return value in [AppResult.Success] or mapping non-cancellation
 * throwables into [AppResult.Failure] via [mapError].
 *
 * [CancellationException] is always rethrown to preserve structured concurrency.
 */
@Suppress("TooGenericExceptionCaught")
inline fun <T> runCatchingResult(
    mapError: (Throwable) -> AppError = ::defaultThrowableToAppError,
    block: () -> T,
): AppResult<T> =
    try {
        AppResult.Success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        AppResult.Failure(mapError(throwable))
    }

@PublishedApi
internal fun defaultThrowableToAppError(throwable: Throwable): AppError =
    when (throwable) {
        is AppErrorException -> {
            throwable.appError
        }

        else -> {
            UnexpectedError(
                message = throwable.message ?: throwable::class.simpleName ?: "Unexpected error",
                cause = throwable,
            )
        }
    }
