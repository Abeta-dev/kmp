package dev.abeta.kmp.network.client

import dev.abeta.kmp.core.error.AppError
import dev.abeta.kmp.core.error.ConflictError
import dev.abeta.kmp.core.error.ForbiddenError
import dev.abeta.kmp.core.error.NetworkError
import dev.abeta.kmp.core.error.NotFoundError
import dev.abeta.kmp.core.error.RateLimitError
import dev.abeta.kmp.core.error.TimeoutError
import dev.abeta.kmp.core.error.UnauthorizedError
import dev.abeta.kmp.core.time.Clock
import dev.abeta.kmp.core.time.SystemClock
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val SECONDS_PER_DAY = 86_400L
private const val MAX_HOUR = 23
private const val MAX_MINUTE_OR_SECOND = 59
private const val MAX_DAY_OF_MONTH = 31
private const val MIN_HTTP_YEAR = 1970
private const val MONTHS_SHIFT_THRESHOLD = 2
private const val MARCH_MONTH_OFFSET = 3
private const val JAN_FEB_MONTH_OFFSET = 9
private const val ERA_YEARS = 400L
private const val ERA_DAYS = 146_097L
private const val EPOCH_DAYS_OFFSET = 719_468L
private const val DAYS_PER_YEAR = 365L
private const val LEAP_CYCLE_4 = 4L
private const val LEAP_CYCLE_100 = 100L
private const val DOY_MULT = 153L
private const val DOY_DIV = 5L
private const val TWO_DIGIT_YEAR_PIVOT = 70
private const val CENTURY_1900 = 1900
private const val CENTURY_2000 = 2000

private val HTTP_MONTHS: Map<String, Int> =
    mapOf(
        "jan" to 1,
        "feb" to 2,
        "mar" to 3,
        "apr" to 4,
        "may" to 5,
        "jun" to 6,
        "jul" to 7,
        "aug" to 8,
        "sep" to 9,
        "oct" to 10,
        "nov" to 11,
        "dec" to 12,
    )

// RFC 9110 IMF-fixdate: "Wed, 21 Oct 2015 07:28:00 GMT"
private val IMF_FIXDATE_REGEX =
    Regex(
        "^[A-Za-z]{3},\\s+(\\d{2})\\s+([A-Za-z]{3})\\s+(\\d{4})\\s+(\\d{2}):(\\d{2}):(\\d{2})\\s+GMT$",
        RegexOption.IGNORE_CASE,
    )

// Obsolete RFC 850: "Sunday, 06-Nov-94 08:49:37 GMT"
private val RFC850_DATE_REGEX =
    Regex(
        "^[A-Za-z]+,\\s+(\\d{2})-([A-Za-z]{3})-(\\d{2})\\s+(\\d{2}):(\\d{2}):(\\d{2})\\s+GMT$",
        RegexOption.IGNORE_CASE,
    )

// Obsolete ANSI C asctime(): "Sun Nov  6 08:49:37 1994"
private val ASCTIME_DATE_REGEX =
    Regex(
        "^[A-Za-z]{3}\\s+([A-Za-z]{3})\\s+(\\d{1,2})\\s+(\\d{2}):(\\d{2}):(\\d{2})\\s+(\\d{4})$",
        RegexOption.IGNORE_CASE,
    )

internal fun HttpRequestBuilder.applyBody(
    json: Json,
    body: HttpRequestBody?,
) {
    if (body == null) return
    contentType(ContentType.Application.Json)
    when (body) {
        is HttpRequestBody.JsonValue -> {
            setBody(json.encodeToString(JsonElement.serializer(), body.element))
        }

        is HttpRequestBody.Serialized<*> -> {
            @Suppress("UNCHECKED_CAST")
            val typed = body as HttpRequestBody.Serialized<Any?>
            setBody(json.encodeToString(typed.serializer, typed.value))
        }
    }
}

internal fun elapsedSince(
    clock: Clock,
    startedAt: Long,
): Long = (clock.nowEpochMillis() - startedAt).coerceAtLeast(0L)

internal fun Headers.toHeaderMap(): Map<String, String> =
    buildMap {
        entries().forEach { (name, values) ->
            if (values.isNotEmpty()) {
                put(name, values.joinToString(separator = ", "))
            }
        }
    }

internal fun extractRetryAfterMillis(
    headers: Headers,
    nowEpochMillis: Long = SystemClock().nowEpochMillis(),
): Long? {
    val raw = headers[HttpHeaders.RetryAfter]?.trim()?.takeIf { it.isNotEmpty() }
    val deltaSeconds = raw?.toLongOrNull()
    return when {
        raw == null -> null
        deltaSeconds != null -> deltaSeconds.coerceAtLeast(0L) * MILLIS_PER_SECOND
        else -> parseHttpDateToEpochMillis(raw)?.let { target -> (target - nowEpochMillis).coerceAtLeast(0L) }
    }
}

internal fun parseHttpDateToEpochMillis(raw: String): Long? = HttpDateParser.parseToEpochMillis(raw)

private object HttpDateParser {
    @Suppress("MagicNumber")
    fun parseToEpochMillis(raw: String): Long? {
        val trimmed = raw.trim()
        val imf = IMF_FIXDATE_REGEX.matchEntire(trimmed)
        val rfc850 = if (imf == null) RFC850_DATE_REGEX.matchEntire(trimmed) else null
        val asctime = if (imf == null && rfc850 == null) ASCTIME_DATE_REGEX.matchEntire(trimmed) else null
        return when {
            imf != null -> {
                buildUtcEpochMillis(
                    year = imf.groupValues[3].toIntOrNull(),
                    monthName = imf.groupValues[2],
                    day = imf.groupValues[1].toIntOrNull(),
                    hour = imf.groupValues[4].toIntOrNull(),
                    minute = imf.groupValues[5].toIntOrNull(),
                    second = imf.groupValues[6].toIntOrNull(),
                )
            }

            rfc850 != null -> {
                val yy = rfc850.groupValues[3].toIntOrNull()
                val year = yy?.let { if (it >= TWO_DIGIT_YEAR_PIVOT) CENTURY_1900 + it else CENTURY_2000 + it }
                buildUtcEpochMillis(
                    year = year,
                    monthName = rfc850.groupValues[2],
                    day = rfc850.groupValues[1].toIntOrNull(),
                    hour = rfc850.groupValues[4].toIntOrNull(),
                    minute = rfc850.groupValues[5].toIntOrNull(),
                    second = rfc850.groupValues[6].toIntOrNull(),
                )
            }

            asctime != null -> {
                buildUtcEpochMillis(
                    year = asctime.groupValues[6].toIntOrNull(),
                    monthName = asctime.groupValues[1],
                    day = asctime.groupValues[2].toIntOrNull(),
                    hour = asctime.groupValues[3].toIntOrNull(),
                    minute = asctime.groupValues[4].toIntOrNull(),
                    second = asctime.groupValues[5].toIntOrNull(),
                )
            }

            else -> {
                null
            }
        }
    }

    @Suppress("LongParameterList")
    private fun buildUtcEpochMillis(
        year: Int?,
        monthName: String,
        day: Int?,
        hour: Int?,
        minute: Int?,
        second: Int?,
    ): Long? {
        val month = HTTP_MONTHS[monthName.lowercase()]
        val valid =
            year != null &&
                month != null &&
                day != null &&
                hour != null &&
                minute != null &&
                second != null &&
                year >= MIN_HTTP_YEAR &&
                day in 1..MAX_DAY_OF_MONTH &&
                hour in 0..MAX_HOUR &&
                minute in 0..MAX_MINUTE_OR_SECOND &&
                second in 0..MAX_MINUTE_OR_SECOND
        if (!valid) return null
        val days = daysFromCivil(year = year, month = month, day = day)
        val totalSeconds =
            days * SECONDS_PER_DAY +
                hour.toLong() * SECONDS_PER_HOUR +
                minute.toLong() * SECONDS_PER_MINUTE +
                second.toLong()
        return totalSeconds * MILLIS_PER_SECOND
    }

    private fun daysFromCivil(
        year: Int,
        month: Int,
        day: Int,
    ): Long {
        val adjustedYear = (year - if (month <= MONTHS_SHIFT_THRESHOLD) 1 else 0).toLong()
        val era = (if (adjustedYear >= 0L) adjustedYear else adjustedYear - (ERA_YEARS - 1L)) / ERA_YEARS
        val yearOfEra = adjustedYear - era * ERA_YEARS
        val shiftedMonth =
            if (month > MONTHS_SHIFT_THRESHOLD) month - MARCH_MONTH_OFFSET else month + JAN_FEB_MONTH_OFFSET
        val dayOfYear = (DOY_MULT * shiftedMonth + 2L) / DOY_DIV + day - 1L
        val dayOfEra = yearOfEra * DAYS_PER_YEAR + yearOfEra / LEAP_CYCLE_4 - yearOfEra / LEAP_CYCLE_100 + dayOfYear
        return era * ERA_DAYS + dayOfEra - EPOCH_DAYS_OFFSET
    }
}

internal fun mapTransportThrowable(
    path: String,
    throwable: Throwable,
): AppError =
    when (throwable) {
        is HttpRequestTimeoutException -> {
            TimeoutError(message = throwable.message ?: "Request timed out", cause = throwable)
        }

        is ConnectTimeoutException -> {
            TimeoutError(message = throwable.message ?: "Connect timed out", cause = throwable)
        }

        is SocketTimeoutException -> {
            TimeoutError(message = throwable.message ?: "Socket timed out", cause = throwable)
        }

        else -> {
            NetworkError(
                statusCode = null,
                message = throwable.message ?: "Network request failed for $path",
                cause = throwable,
            )
        }
    }

internal fun mapStatusError(
    status: HttpStatusCode,
    url: String,
    retryAfterMillis: Long?,
): AppError =
    when (status) {
        HttpStatusCode.Unauthorized -> {
            UnauthorizedError(message = "Unauthorized HTTP 401 for $url")
        }

        HttpStatusCode.Forbidden -> {
            ForbiddenError(resource = url, message = "Forbidden HTTP 403 for $url")
        }

        HttpStatusCode.NotFound -> {
            NotFoundError(resource = url, message = "HTTP 404 Not Found for $url")
        }

        HttpStatusCode.Conflict -> {
            ConflictError(resource = url, message = "HTTP 409 Conflict for $url")
        }

        HttpStatusCode.TooManyRequests -> {
            RateLimitError(retryAfterMillis = retryAfterMillis, message = "HTTP 429 Rate Limited for $url")
        }

        HttpStatusCode.RequestTimeout, HttpStatusCode.GatewayTimeout -> {
            TimeoutError(message = "HTTP ${status.value} Timeout for $url")
        }

        else -> {
            NetworkError(
                statusCode = status.value,
                message = "HTTP ${status.value} (${status.description}) for $url",
            )
        }
    }

internal fun HttpMethodType.toKtorMethod(): HttpMethod =
    when (this) {
        HttpMethodType.GET -> HttpMethod.Get
        HttpMethodType.POST -> HttpMethod.Post
        HttpMethodType.PUT -> HttpMethod.Put
        HttpMethodType.PATCH -> HttpMethod.Patch
        HttpMethodType.DELETE -> HttpMethod.Delete
    }

internal fun Map<String, String>.containsHeaderIgnoreCase(name: String): Boolean {
    val found = extractHeaderIgnoreCase(this, name)
    return found != null
}

internal fun Map<String, String>.withHeaderOverride(
    name: String,
    value: String,
): Map<String, String> = filterKeys { !it.equals(name, ignoreCase = true) } + (name to value)

internal fun extractHeaderIgnoreCase(
    headers: Map<String, String>,
    name: String,
): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
