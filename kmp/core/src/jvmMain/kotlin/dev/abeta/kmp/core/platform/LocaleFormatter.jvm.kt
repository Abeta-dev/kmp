package dev.abeta.kmp.core.platform

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

private const val CENTS_SCALE = 2

/** Formats minor currency units using JVM [NumberFormat] and the host default [Locale]. */
actual fun formatCurrencyCents(
    amountCents: Long,
    currencyCode: String,
): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.getDefault())
    runCatching {
        formatter.currency = Currency.getInstance(currencyCode.uppercase())
    }
    return formatter.format(BigDecimal.valueOf(amountCents, CENTS_SCALE))
}

/** Returns the active JVM BCP-47 locale tag. */
actual fun currentLocaleTag(): String = Locale.getDefault().toLanguageTag()
