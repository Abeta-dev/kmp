package dev.abeta.kmp.core.result

import dev.abeta.kmp.core.error.AppError
import dev.abeta.kmp.core.error.AppErrorException
import dev.abeta.kmp.core.error.isRetryable
import dev.abeta.kmp.core.resilience.BackoffPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay

/**
 * Recovers from an [AppResult.Failure] by mapping its [AppError] to a fallback [AppResult.Success] value.
 */
inline fun <T> AppResult<T>.recover(transform: (AppError) -> T): AppResult<T> =
    when (this) {
        is AppResult.Success -> this
        is AppResult.Failure -> AppResult.Success(transform(error))
    }

/**
 * Recovers from an [AppResult.Failure] using a block that may throw, mapped cleanly to [AppError].
 */
inline fun <T> AppResult<T>.recoverCatching(
    mapError: (Throwable) -> AppError = ::defaultThrowableToAppError,
    transform: (AppError) -> T,
): AppResult<T> =
    when (this) {
        is AppResult.Success -> this
        is AppResult.Failure -> runCatchingResult(mapError) { transform(error) }
    }

/**
 * Returns the encapsulated value if [AppResult.Success], or [defaultValue] otherwise.
 */
fun <T> AppResult<T>.getOrDefault(defaultValue: T): T =
    when (this) {
        is AppResult.Success -> value
        is AppResult.Failure -> defaultValue
    }

/**
 * Returns the encapsulated value if [AppResult.Success], or computes a fallback from [AppError].
 */
inline fun <T> AppResult<T>.getOrElse(onFailure: (AppError) -> T): T =
    when (this) {
        is AppResult.Success -> value
        is AppResult.Failure -> onFailure(error)
    }

/**
 * Combines two independent [AppResult] instances using [combine], short-circuiting on the first failure.
 */
inline fun <A, B, R> AppResult<A>.zip(
    other: AppResult<B>,
    combine: (A, B) -> R,
): AppResult<R> =
    when (this) {
        is AppResult.Failure -> {
            this
        }

        is AppResult.Success -> {
            when (other) {
                is AppResult.Success -> AppResult.Success(combine(value, other.value))
                is AppResult.Failure -> other
            }
        }
    }

/**
 * Collects a list of [AppResult] values into a single [AppResult] containing all values,
 * or returns the first encountered [AppResult.Failure].
 */
fun <T> combineResults(results: List<AppResult<T>>): AppResult<List<T>> {
    val values = ArrayList<T>(results.size)
    for (result in results) {
        when (result) {
            is AppResult.Success -> values.add(result.value)
            is AppResult.Failure -> return result
        }
    }
    return AppResult.Success(values)
}

/**
 * Converts a standard Kotlin [Result] into an [AppResult].
 */
fun <T> Result<T>.toAppResult(mapError: (Throwable) -> AppError = ::defaultThrowableToAppError): AppResult<T> =
    fold(
        onSuccess = { AppResult.Success(it) },
        onFailure = { throwable ->
            if (throwable is CancellationException) {
                throw throwable
            }
            AppResult.Failure(mapError(throwable))
        },
    )

/**
 * Converts this [AppResult] into a standard Kotlin [Result], wrapping failures via [AppErrorException].
 */
fun <T> AppResult<T>.toKotlinResult(): Result<T> =
    when (this) {
        is AppResult.Success -> Result.success(value)
        is AppResult.Failure -> Result.failure(AppErrorException(error))
    }

/**
 * Executes [block] with retries governed by [policy] whenever the result is a failure passing [shouldRetry].
 */
suspend fun <T> retryResult(
    policy: BackoffPolicy = BackoffPolicy(),
    shouldRetry: (AppError) -> Boolean = { it.isRetryable },
    delayBlock: suspend (Long) -> Unit = { delayMillis -> delay(delayMillis) },
    block: suspend (attempt: Int) -> AppResult<T>,
): AppResult<T> {
    var attempt = 0
    while (true) {
        val result = block(attempt)
        if (result is AppResult.Success) {
            return result
        }
        val error = (result as AppResult.Failure).error
        val canRetry = attempt < policy.maxRetries && shouldRetry(error)
        if (!canRetry) {
            return result
        }
        val backoffDelay = policy.delayMillisForAttempt(attempt)
        if (backoffDelay > 0L) {
            delayBlock(backoffDelay)
        }
        attempt++
    }
}
