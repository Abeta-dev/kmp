package dev.abeta.kmp.core.platform

import platform.Foundation.NSDecimalNumber
import platform.Foundation.NSLocale
import platform.Foundation.NSNumberFormatter
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier

internal expect fun configureAppleCurrencyFormatter(
    formatter: NSNumberFormatter,
    currencyCode: String,
    fractionDigits: Int,
)

/** Formats minor currency units using [NSNumberFormatter] and the host Apple [NSLocale]. */
actual fun formatCurrencyCents(
    amountCents: Long,
    currencyCode: String,
): String {
    val normalizedCode = currencyCode.uppercase()
    val digits = currencyMinorUnitDigits(normalizedCode)
    val formatter =
        NSNumberFormatter().apply {
            configureAppleCurrencyFormatter(
                formatter = this,
                currencyCode = normalizedCode,
                fractionDigits = digits,
            )
        }
    val decimalNumber = NSDecimalNumber.decimalNumberWithString(formatPlainMinorUnitsDecimal(amountCents, digits))
    val formatted = formatter.stringFromNumber(decimalNumber)
    if (formatted != null) return formatted
    return formatCurrencyMinorUnitsFallback(amountMinorUnits = amountCents, currencyCode = normalizedCode)
}

/** Returns the active Apple BCP-47 locale identifier normalized with hyphens. */
actual fun currentLocaleTag(): String =
    NSLocale.currentLocale.localeIdentifier
        .replace('_', '-')
        .ifBlank { "en-US" }
