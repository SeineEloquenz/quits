package nz.eloque.quits.domain

// Browsers expose no locale-to-currency mapping.
actual fun deviceCurrencyCode(): String? = null
