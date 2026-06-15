package nz.eloque.quits.domain

import platform.Foundation.NSLocale
import platform.Foundation.commonISOCurrencyCodes
import platform.Foundation.currentLocale
import platform.Foundation.localizedStringForCurrencyCode

actual object CurrencyCatalog {
    actual fun codes(): List<String> = NSLocale.commonISOCurrencyCodes.filterIsInstance<String>()

    actual fun nameOf(code: String): String = NSLocale.currentLocale.localizedStringForCurrencyCode(code) ?: code
}
