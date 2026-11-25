package dev.abeta.kmp.core.platform

import platform.Foundation.NSLocale
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.currentLocale

internal actual fun configureAppleCurrencyFormatter(
    formatter: NSNumberFormatter,
    currencyCode: String,
    fractionDigits: Int,
) {
    formatter.numberStyle = NSNumberFormatterCurrencyStyle
    formatter.locale = NSLocale.currentLocale
    formatter.currencyCode = currencyCode.uppercase()
    formatter.minimumFractionDigits = fractionDigits.toULong()
    formatter.maximumFractionDigits = fractionDigits.toULong()
}
