package nz.eloque.quits.domain

/** The ISO 4217 code of the device region's currency, or null when the platform can't tell. */
expect fun deviceCurrencyCode(): String?

/** The device region's currency, falling back to [fallback] when unknown or unsupported. */
fun deviceCurrency(fallback: String = "USD"): Currency =
    deviceCurrencyCode()?.let { runCatching { Currency.of(it) }.getOrNull() } ?: Currency.of(fallback)
