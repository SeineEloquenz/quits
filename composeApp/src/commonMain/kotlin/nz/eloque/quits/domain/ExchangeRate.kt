package nz.eloque.quits.domain

import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.roundToLong

/**
 * Converts money from [from] to [to] at [rate] (units of [to] per one unit of [from]). FX uses
 * floating-point rates; within-currency arithmetic stays exact integer minor units.
 */
data class ExchangeRate(
    val from: Currency,
    val to: Currency,
    val rate: Double,
) {
    fun convert(money: Money): Money {
        require(money.currency == from) {
            "rate is ${from.code}->${to.code}, cannot convert ${money.currency.code}"
        }
        if (from == to && rate == 1.0) return money
        val value = money.minorUnits.toDouble() / pow10(from.decimalDigits)
        return Money((value * rate * pow10(to.decimalDigits)).roundToLong(), to)
    }

    /** Label like "1 JPY = 0.006268 USD", with four significant digits. */
    fun label(format: NumberFormatSymbols = LocaleNumberFormat): String {
        val digits = (3 - floor(log10(rate)).toInt()).coerceIn(0, 12)
        var value = formatScaled((rate * pow10(digits)).roundToLong(), digits, format)
        if (digits > 0) value = value.trimEnd('0').trimEnd(format.decimalSeparator())
        return "1 ${from.code} = $value ${to.code}"
    }

    companion object {
        fun identity(currency: Currency) = ExchangeRate(currency, currency, 1.0)
    }
}

/** Domain port for live rates. The Frankfurter adapter and on-device caching live in infrastructure. */
interface FxRateProvider {
    suspend fun rate(
        from: Currency,
        to: Currency,
    ): ExchangeRate
}
