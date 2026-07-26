package nz.eloque.quits.domain

import platform.Foundation.NSLocale
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.commonISOCurrencyCodes
import platform.Foundation.currentLocale
import platform.Foundation.localizedStringForCurrencyCode

actual object CurrencyCatalog {
    actual fun codes(): List<String> = NSLocale.commonISOCurrencyCodes.filterIsInstance<String>()

    actual fun nameOf(code: String): String = NSLocale.currentLocale.localizedStringForCurrencyCode(code) ?: code

    actual fun decimalDigits(code: String): Int {
        val formatter =
            NSNumberFormatter().apply {
                numberStyle = NSNumberFormatterCurrencyStyle
                currencyCode = code
            }
        return formatter.maximumFractionDigits.toInt()
    }

    actual fun symbolOf(code: String): String? {
        val formatter =
            NSNumberFormatter().apply {
                numberStyle = NSNumberFormatterCurrencyStyle
                currencyCode = code
            }
        return formatter.currencySymbol?.ifBlank { null }
    }
}
