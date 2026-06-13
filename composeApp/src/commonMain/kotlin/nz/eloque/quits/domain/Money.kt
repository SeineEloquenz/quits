package nz.eloque.quits.domain

import kotlin.math.abs

/** An ISO-4217 currency: its code and how many minor-unit digits it has (2 for most, 0 for JPY…). */
data class Currency(
    val code: String,
    val decimalDigits: Int,
) {
    companion object {
        // Currencies whose minor-unit digit count isn't the default of 2.
        private val nonDefaultDigits =
            mapOf(
                "JPY" to 0,
                "KRW" to 0,
                "VND" to 0,
                "CLP" to 0,
                "ISK" to 0,
                "BHD" to 3,
                "KWD" to 3,
                "OMR" to 3,
                "TND" to 3,
                "JOD" to 3,
            )

        fun of(code: String): Currency {
            val c = code.uppercase()
            return Currency(c, nonDefaultDigits[c] ?: 2)
        }
    }
}

internal fun pow10(n: Int): Long {
    var result = 1L
    repeat(n) { result *= 10 }
    return result
}

/** A monetary amount stored as integer minor units of a [currency] — never a float. */
data class Money(
    val minorUnits: Long,
    val currency: Currency,
) : Comparable<Money> {
    val isZero: Boolean get() = minorUnits == 0L
    val isPositive: Boolean get() = minorUnits > 0L
    val isNegative: Boolean get() = minorUnits < 0L

    operator fun plus(other: Money): Money {
        requireSameCurrency(other)
        return copy(minorUnits = minorUnits + other.minorUnits)
    }

    operator fun minus(other: Money): Money {
        requireSameCurrency(other)
        return copy(minorUnits = minorUnits - other.minorUnits)
    }

    operator fun unaryMinus(): Money = copy(minorUnits = -minorUnits)

    override fun compareTo(other: Money): Int {
        requireSameCurrency(other)
        return minorUnits.compareTo(other.minorUnits)
    }

    /** Decimal representation, e.g. 1999 USD -> "19.99", 100 JPY -> "100". */
    fun toDecimalString(): String {
        val scale = pow10(currency.decimalDigits)
        val sign = if (minorUnits < 0) "-" else ""
        val absUnits = abs(minorUnits)
        val whole = absUnits / scale
        if (currency.decimalDigits == 0) return "$sign$whole"
        val frac = (absUnits % scale).toString().padStart(currency.decimalDigits, '0')
        return "$sign$whole.$frac"
    }

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "cannot combine ${currency.code} with ${other.currency.code}; convert via FX first"
        }
    }

    companion object {
        fun zero(currency: Currency): Money = Money(0, currency)
    }
}
