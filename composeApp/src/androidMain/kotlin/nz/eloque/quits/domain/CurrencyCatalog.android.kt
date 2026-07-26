package nz.eloque.quits.domain

import java.util.Locale
import java.util.Currency as JavaCurrency

actual object CurrencyCatalog {
    actual fun codes(): List<String> =
        Locale
            .getAvailableLocales()
            .mapNotNull { locale -> runCatching { JavaCurrency.getInstance(locale) }.getOrNull() }
            .filter { it.defaultFractionDigits >= 0 }
            .map { it.currencyCode }
            .distinct()

    actual fun nameOf(code: String): String =
        runCatching { JavaCurrency.getInstance(code).getDisplayName(Locale.getDefault()) }.getOrDefault(code)

    actual fun decimalDigits(code: String): Int {
        val digits = runCatching { JavaCurrency.getInstance(code).defaultFractionDigits }.getOrDefault(2)
        return if (digits < 0) 2 else digits
    }

    actual fun symbolOf(code: String): String? =
        runCatching { JavaCurrency.getInstance(code).getSymbol(Locale.getDefault()) }.getOrNull()
}
