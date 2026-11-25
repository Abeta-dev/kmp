@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.abeta.kmp.core.platform

private const val ZERO_DECIMAL_DIVISOR = 1.0
private const val TWO_DECIMAL_DIVISOR = 100.0
private const val THREE_DECIMAL_DIVISOR = 1000.0
private const val THREE_FRACTION_DIGITS = 3

@JsFun(
    "(localeTag, currencyCode, amount, fractionDigits) => { try { " +
        "return new Intl.NumberFormat(localeTag, { style: 'currency', currency: currencyCode, " +
        "minimumFractionDigits: fractionDigits, maximumFractionDigits: fractionDigits }).format(amount); " +
        "} catch (e) { return ''; } }",
)
private external fun jsFormatCurrency(
    localeTag: String,
    currencyCode: String,
    amount: Double,
    fractionDigits: Int,
): String

@JsFun("() => (typeof navigator !== 'undefined' && navigator.language) ? navigator.language : 'en-US'")
private external fun jsLocaleTag(): String

/** Formats minor currency units via browser `Intl.NumberFormat` on WasmJS. */
actual fun formatCurrencyCents(
    amountCents: Long,
    currencyCode: String,
): String {
    val normalizedCode = currencyCode.uppercase()
    val digits = currencyMinorUnitDigits(normalizedCode)
    val divisor =
        when (digits) {
            0 -> ZERO_DECIMAL_DIVISOR
            THREE_FRACTION_DIGITS -> THREE_DECIMAL_DIVISOR
            else -> TWO_DECIMAL_DIVISOR
        }
    val locale = currentLocaleTag()
    val formatted = jsFormatCurrency(locale, normalizedCode, amountCents.toDouble() / divisor, digits)
    if (formatted.isNotEmpty()) return formatted
    return formatCurrencyMinorUnitsFallback(amountMinorUnits = amountCents, currencyCode = normalizedCode)
}

/** Returns the browser BCP-47 locale tag on WasmJS. */
actual fun currentLocaleTag(): String = jsLocaleTag()
