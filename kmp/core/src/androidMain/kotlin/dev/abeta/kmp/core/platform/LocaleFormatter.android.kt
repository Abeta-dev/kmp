package dev.abeta.kmp.core.platform

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Formats minor currency units using Android [NumberFormat] and the active default [Locale]. */
actual fun formatCurrencyCents(
    amountCents: Long,
    currencyCode: String,
): String {
    val normalizedCode = currencyCode.uppercase()
    val digits = currencyMinorUnitDigits(normalizedCode)
    return runCatching {
        val formatter = NumberFormat.getCurrencyInstance(Locale.getDefault())
        runCatching {
            formatter.currency = Currency.getInstance(normalizedCode)
        }
        formatter.minimumFractionDigits = digits
        formatter.maximumFractionDigits = digits
        formatter.format(BigDecimal.valueOf(amountCents, digits))
    }.getOrElse {
        formatCurrencyMinorUnitsFallback(amountCents, normalizedCode)
    }
}

/** Returns the active Android BCP-47 locale tag. */
actual fun currentLocaleTag(): String = Locale.getDefault().toLanguageTag().ifBlank { "en-US" }
