package dev.abeta.kmp.core.platform

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
