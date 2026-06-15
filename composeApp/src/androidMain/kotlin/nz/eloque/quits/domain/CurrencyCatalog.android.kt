package nz.eloque.quits.domain

import java.util.Locale
import java.util.Currency as JavaCurrency

actual object CurrencyCatalog {
    actual fun codes(): List<String> =
        JavaCurrency
            .getAvailableCurrencies()
            // Drop non-money entries (precious metals, "no currency", test codes) which report -1.
            .filter { it.defaultFractionDigits >= 0 }
            .map { it.currencyCode }

    actual fun nameOf(code: String): String =
        runCatching { JavaCurrency.getInstance(code).getDisplayName(Locale.getDefault()) }.getOrDefault(code)
}
