@file:OptIn(ExperimentalWasmJsInterop::class)

package dev.abeta.kmp.core.platform

private const val CENTS_PER_UNIT = 100.0
private const val CENTS_SCALE_INT = 100L
private const val FRACTION_DIGITS = 2

@JsFun(
    "(localeTag, currencyCode, amount) => { try { " +
        "return new Intl.NumberFormat(localeTag, { style: 'currency', currency: currencyCode }).format(amount); " +
        "} catch (e) { return ''; } }",
)
private external fun jsFormatCurrency(
    localeTag: String,
    currencyCode: String,
    amount: Double,
): String

@JsFun("() => (typeof navigator !== 'undefined' && navigator.language) ? navigator.language : 'en-US'")
private external fun jsLocaleTag(): String

/** Formats minor currency units via browser `Intl.NumberFormat` on WasmJS. */
actual fun formatCurrencyCents(
    amountCents: Long,
    currencyCode: String,
): String {
    val locale = currentLocaleTag()
    val formatted = jsFormatCurrency(locale, currencyCode.uppercase(), amountCents.toDouble() / CENTS_PER_UNIT)
    if (formatted.isNotEmpty()) return formatted

    val absCents = kotlin.math.abs(amountCents)
    val whole = absCents / CENTS_SCALE_INT
    val remainder = (absCents % CENTS_SCALE_INT).toString().padStart(FRACTION_DIGITS, '0')
    val sign = if (amountCents < 0L) "-" else ""
    return "$sign${currencyCode.uppercase()} $whole.$remainder"
}

/** Returns the browser BCP-47 locale tag on WasmJS. */
actual fun currentLocaleTag(): String = jsLocaleTag()
