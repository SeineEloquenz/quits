package nz.eloque.quits.domain

expect object CurrencyCatalog {
    /** Active ISO-4217 codes known to the platform. */
    fun codes(): List<String>

    /** Localized display name for [code]; falls back to [code]. */
    fun nameOf(code: String): String
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

    fun search(query: String): List<Currency> {
        val q = query.trim()
        if (q.isEmpty()) return all
        return all.filter { it.code.contains(q, ignoreCase = true) || displayName(it).contains(q, ignoreCase = true) }
    }
}
