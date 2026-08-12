package nz.eloque.quits.domain

import nz.eloque.quits.domain.Currency.Companion.isValidCode
import nz.eloque.quits.domain.Currency.Companion.of
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
        /** Builds a currency from [code], normalizing case; throws if it isn't a valid code. */
        fun of(code: String): Currency {
            val c = code.trim().uppercase()
            return Currency(c, CurrencyCatalog.decimalDigits(c))
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
    fun toDecimalString(format: NumberFormatSymbols = LocaleNumberFormat): String {
        val scale = pow10(currency.decimalDigits)
        val sign = if (minorUnits < 0) "-" else ""
        val absUnits = abs(minorUnits)
        val whole = absUnits / scale
        if (currency.decimalDigits == 0) return "$sign$whole"
        val frac = (absUnits % scale).toString().padStart(currency.decimalDigits, '0')
        return "$sign$whole${format.decimalSeparator()}$frac"
    }

    private fun requireSameCurrency(other: Money) {
        require(currency == other.currency) {
            "cannot combine ${currency.code} with ${other.currency.code}; convert via FX first"
        }
    }

    companion object {
        fun zero(currency: Currency): Money = Money(0, currency)

        /**
         * Parses a decimal amount (e.g. "19.99", "1,234.56", "1.234,56") into minor units of [currency],
         * respecting [format]'s locale separators (default: platform locale). Null if [input] isn't a
         * valid, correctly-scaled number for [currency].
         */
        fun parse(
            input: String,
            currency: Currency,
            format: NumberFormatSymbols = LocaleNumberFormat,
        ): Money? {
            val trimmed = input.trim()
            if (trimmed.isEmpty()) return null
            val negative = trimmed.startsWith("-")
            val body = trimmed.trimStart('+', '-')
            if (body.isEmpty()) return null

            val digits = currency.decimalDigits
            val decimalCandidates = setOf(format.decimalSeparator())
            val groupingSep = format.groupingSeparator()
            val allowed = decimalCandidates + setOfNotNull(groupingSep)
            if (body.any { !it.isDigit() && it !in allowed }) return null

            val lastDecimalIndex = body.indexOfLast { it in decimalCandidates }
            var wholeRaw = body
            var fracStr = ""
            if (lastDecimalIndex >= 0) {
                val candidateFrac = body.substring(lastDecimalIndex + 1)
                val looksLikeFraction =
                    candidateFrac.isNotEmpty() && candidateFrac.length <= digits && candidateFrac.all { it.isDigit() }
                if (looksLikeFraction) {
                    wholeRaw = body.substring(0, lastDecimalIndex)
                    fracStr = candidateFrac
                }
            }

            val wholeStr =
                if (groupingSep != null && wholeRaw.contains(groupingSep)) {
                    val groups = wholeRaw.split(groupingSep)
                    val leadingGroupOk = groups.first().isNotEmpty() && groups.first().length <= 3
                    val restAreTriples = groups.drop(1).all { it.length == 3 }
                    if (!leadingGroupOk || !restAreTriples) return null
                    groups.joinToString("")
                } else {
                    wholeRaw
                }
            if (wholeStr.isEmpty() || wholeStr.any { !it.isDigit() }) return null

            val whole = wholeStr.toLongOrNull() ?: return null
            val frac = fracStr.padEnd(digits, '0').ifEmpty { "0" }.toLong()
            val minor = whole * pow10(digits) + frac
            return Money(if (negative) -minor else minor, currency)
        }
    }
}
