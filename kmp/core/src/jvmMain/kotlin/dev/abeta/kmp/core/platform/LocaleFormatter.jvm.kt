package dev.abeta.kmp.core.platform

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Formats minor currency units using JVM [NumberFormat] and the host default [Locale]. */
actual fun formatCurrencyCents(
    amountCents: Long,
    currencyCode: String,
): String {
    val normalizedCode = currencyCode.uppercase()
    val digits = currencyMinorUnitDigits(normalizedCode)
    val formatter = NumberFormat.getCurrencyInstance(Locale.getDefault())
    runCatching {
        formatter.currency = Currency.getInstance(normalizedCode)
    }
    formatter.minimumFractionDigits = digits
    formatter.maximumFractionDigits = digits
    return formatter.format(BigDecimal.valueOf(amountCents, digits))
}

/** Returns the active JVM BCP-47 locale tag. */
actual fun currentLocaleTag(): String = Locale.getDefault().toLanguageTag()
