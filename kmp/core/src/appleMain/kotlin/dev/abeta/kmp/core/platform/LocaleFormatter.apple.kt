package dev.abeta.kmp.core.platform

import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier

private const val CENTS_PER_UNIT = 100.0
private const val CENTS_SCALE_INT = 100L
private const val FRACTION_DIGITS = 2

internal expect fun configureAppleCurrencyFormatter(
    formatter: NSNumberFormatter,
    currencyCode: String,
)

/** Formats minor currency units using [NSNumberFormatter] and the host Apple [NSLocale]. */
actual fun formatCurrencyCents(
    amountCents: Long,
    currencyCode: String,
): String {
    val formatter =
        NSNumberFormatter().apply {
            configureAppleCurrencyFormatter(this, currencyCode)
        }
    val formatted = formatter.stringFromNumber(NSNumber(double = amountCents.toDouble() / CENTS_PER_UNIT))
    if (formatted != null) return formatted

    val absCents = kotlin.math.abs(amountCents)
    val whole = absCents / CENTS_SCALE_INT
    val remainder = (absCents % CENTS_SCALE_INT).toString().padStart(FRACTION_DIGITS, '0')
    val sign = if (amountCents < 0L) "-" else ""
    return "$sign${currencyCode.uppercase()} $whole.$remainder"
}

/** Returns the active Apple BCP-47 locale identifier normalized with hyphens. */
actual fun currentLocaleTag(): String =
    NSLocale.currentLocale.localeIdentifier
        .replace('_', '-')
        .ifBlank { "en-US" }
