package nz.eloque.quits.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExchangeRateTest {
    @Test
    fun converts_and_rounds_to_target_minor_units() {
        val rate = ExchangeRate(USD, EUR, 0.9)
        assertEquals(Money(900, EUR), rate.convert(usd(1000))) // 10.00 -> 9.00
        assertEquals(Money(1799, EUR), rate.convert(usd(1999))) // 17.991 -> 17.99
    }

    @Test
    fun converts_across_different_decimal_digits() {
        val rate = ExchangeRate(JPY, USD, 0.0067)
        assertEquals(usd(670), rate.convert(Money(1000, JPY))) // 1000 JPY -> 6.70 USD
    }

    @Test
    fun rejects_a_mismatched_source_currency() {
        assertFailsWith<IllegalArgumentException> {
            ExchangeRate(USD, EUR, 1.0).convert(Money(1, EUR))
        }
    }
}
