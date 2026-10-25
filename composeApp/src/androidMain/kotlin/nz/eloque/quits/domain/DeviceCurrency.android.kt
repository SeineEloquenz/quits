package nz.eloque.quits.domain

import java.util.Locale

actual fun deviceCurrencyCode(): String? = runCatching { java.util.Currency.getInstance(Locale.getDefault())?.currencyCode }.getOrNull()
