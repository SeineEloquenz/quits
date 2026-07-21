package nz.eloque.quits.domain

import kotlin.math.abs

/** An ISO-4217 currency: its code and how many minor-unit digits it has (2 for most, 0 for JPY…). */
data class Currency(
    val code: String,
    val decimalDigits: Int,
) {
    init {
        require(isValidCode(code)) { "invalid currency code: '$code'" }
    }

    companion object {
        // Currencies whose minor-unit digit count isn't the default of 2 (per ISO 4217).
        private val nonDefaultDigits =
            mapOf(
                // 0 decimal places
                "BIF" to 0,
                "CLP" to 0,
                "DJF" to 0,
                "GNF" to 0,
                "ISK" to 0,
                "JPY" to 0,
                "KMF" to 0,
                "KRW" to 0,
                "PYG" to 0,
                "RWF" to 0,
                "UGX" to 0,
                "VND" to 0,
                "VUV" to 0,
                "XAF" to 0,
                "XOF" to 0,
                "XPF" to 0,
                // 3 decimal places
                "BHD" to 3,
                "IQD" to 3,
                "JOD" to 3,
                "KWD" to 3,
                "LYD" to 3,
                "OMR" to 3,
                "TND" to 3,
            )

        /** Builds a currency from [code], normalizing case; throws if it isn't a valid code. */
        fun of(code: String): Currency {
            val c = code.trim().uppercase()
            return Currency(c, nonDefaultDigits[c] ?: 2)
        }

        /** A well-formed ISO-4217-style code: exactly three letters (e.g. USD, EUR, JPY). */
        fun isValidCode(code: String): Boolean {
            val c = code.trim()
            return c.length == 3 && c.all { it.isLetter() }
        }

        /** [of] the code if it is [isValidCode], otherwise null — for validating untrusted input. */
        fun parse(code: String): Currency? = if (isValidCode(code)) of(code) else null
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

        /**
         * Parses a decimal string (e.g. "19.99") into minor units of [currency], or null if it is
         * not a valid number or has more fraction digits than the currency allows.
         */
        fun parse(
            input: String,
            currency: Currency,
        ): Money? {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return null
            val negative = trimmed.startsWith("-")
            val body = trimmed.trimStart('+', '-')
            val parts = body.split('.', ',')
            if (parts.size > 2) return null
            val wholeStr = parts[0].ifEmpty { "0" }
            val fracStr = parts.getOrElse(1) { "" }
            if (wholeStr.any { !it.isDigit() } || fracStr.any { !it.isDigit() }) return null
            val digits = currency.decimalDigits
            if (fracStr.length > digits) return null
            val whole = wholeStr.toLongOrNull() ?: return null
            val frac = fracStr.padEnd(digits, '0').ifEmpty { "0" }.toLong()
            val minor = whole * pow10(digits) + frac
            return Money(if (negative) -minor else minor, currency)
        }
    }
}
