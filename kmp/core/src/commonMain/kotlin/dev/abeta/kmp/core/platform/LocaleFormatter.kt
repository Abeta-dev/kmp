package dev.abeta.kmp.core.platform

private const val ZERO_FRACTION_DIGITS = 0
private const val DEFAULT_FRACTION_DIGITS = 2
private const val THREE_FRACTION_DIGITS = 3
private const val SCALE_ZERO = 1L
private const val SCALE_TWO = 100L
private const val SCALE_THREE = 1000L
private const val ABS_LONG_MIN_TEXT = "9223372036854775808"

private val ZERO_DECIMAL_CURRENCIES = setOf("JPY", "KRW", "VND", "CLP", "ISK")
private val THREE_DECIMAL_CURRENCIES = setOf("KWD", "BHD", "JOD", "OMR", "TND")

internal fun currencyMinorUnitDigits(currencyCode: String): Int =
    when (currencyCode.uppercase()) {
        in ZERO_DECIMAL_CURRENCIES -> ZERO_FRACTION_DIGITS
        in THREE_DECIMAL_CURRENCIES -> THREE_FRACTION_DIGITS
        else -> DEFAULT_FRACTION_DIGITS
    }

internal fun formatPlainMinorUnitsDecimal(
    amountMinorUnits: Long,
    fractionDigits: Int,
): String {
    val sign = if (amountMinorUnits < 0L) "-" else ""
    val scale =
        when (fractionDigits) {
            ZERO_FRACTION_DIGITS -> SCALE_ZERO
            THREE_FRACTION_DIGITS -> SCALE_THREE
            else -> SCALE_TWO
        }
    val rawWhole = amountMinorUnits / scale
    val rawRemainder = amountMinorUnits % scale
    val wholeText =
        if (rawWhole == Long.MIN_VALUE) {
            ABS_LONG_MIN_TEXT
        } else {
            kotlin.math.abs(rawWhole).toString()
        }
    if (fractionDigits <= 0) {
        return "$sign$wholeText"
    }
    val absRemainder =
        kotlin.math
            .abs(rawRemainder)
            .toString()
            .padStart(fractionDigits, '0')
    return "$sign$wholeText.$absRemainder"
}

internal fun formatCurrencyMinorUnitsFallback(
    amountMinorUnits: Long,
    currencyCode: String,
): String {
    val normalizedCode = currencyCode.uppercase()
    val digits = currencyMinorUnitDigits(normalizedCode)
    val decimal = formatPlainMinorUnitsDecimal(amountMinorUnits, digits)
    val sign = if (decimal.startsWith("-")) "-" else ""
    val unsignedDecimal = decimal.removePrefix("-")
    return "$sign$normalizedCode $unsignedDecimal"
}

/**
 * Formats integer minor currency units (e.g. cents) using the host OS locale & ISO-4217 currency code.
 */
expect fun formatCurrencyCents(
    amountCents: Long,
    currencyCode: String = "USD",
): String

/**
 * Returns the active OS BCP-47 locale tag (e.g. `"en-US"`, `"en-IN"`).
 */
expect fun currentLocaleTag(): String
