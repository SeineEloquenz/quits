package nz.eloque.quits.domain

expect object CurrencyCatalog {
    /** Active ISO-4217 codes known to the platform. */
    fun codes(): List<String>

    /** Localized display name for [code]; falls back to [code]. */
    fun nameOf(code: String): String

    /** How many minor-unit digits [code] uses (2 for most, 0 for JPY, 3 for BHD…), per the
     * platform's own currency data; falls back to 2 for a code the platform doesn't recognize. */
    fun decimalDigits(code: String): Int

    /** Localized symbol for [code] (e.g. "€", "US$"), per the platform's own currency data;
     * null if the platform has none. */
    fun symbolOf(code: String): String?
}

object Currencies {
    val all: List<Currency> by lazy {
        CurrencyCatalog
            .codes()
            .filter { Currency.isValidCode(it) }
            .map { Currency.of(it) }
            .distinct()
            .sortedBy { it.code }
    }

    fun displayName(currency: Currency): String = CurrencyCatalog.nameOf(currency.code)

    /** Localized symbol for [currency], falling back to its ISO code if none exists. */
    fun symbol(currency: Currency): String = CurrencyCatalog.symbolOf(currency.code) ?: currency.code

    fun search(query: String): List<Currency> {
        val q = query.trim()
        if (q.isEmpty()) return all
        return all.filter { it.code.contains(q, ignoreCase = true) || displayName(it).contains(q, ignoreCase = true) }
    }
}
