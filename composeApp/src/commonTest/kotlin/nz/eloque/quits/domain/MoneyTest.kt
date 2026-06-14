package nz.eloque.quits.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MoneyTest {
    @Test
    fun decimal_string_respects_currency_digits() {
        assertEquals("19.99", usd(1999).toDecimalString())
        assertEquals("0.05", usd(5).toDecimalString())
        assertEquals("-5.00", usd(-500).toDecimalString())
        assertEquals("100", Money(100, JPY).toDecimalString())
    }

    @Test
    fun arithmetic_within_currency() {
        assertEquals(usd(300), usd(100) + usd(200))
        assertEquals(usd(-100), usd(100) - usd(200))
        assertTrue(usd(200) > usd(100))
    }

    @Test
    fun mixing_currencies_is_rejected() {
        assertFailsWith<IllegalArgumentException> { usd(1) + Money(1, EUR) }
    }

    @Test
    fun parse_reads_decimal_strings_per_currency() {
        assertEquals(usd(1999), Money.parse("19.99", USD))
        assertEquals(usd(500), Money.parse("5", USD))
        assertEquals(usd(50), Money.parse("0.50", USD))
        assertEquals(usd(-250), Money.parse("-2.50", USD))
        assertEquals(Money(100, JPY), Money.parse("100", JPY))
    }

    @Test
    fun currency_code_validation() {
        assertTrue(Currency.isValidCode("USD"))
        assertTrue(Currency.isValidCode("eur"))
        assertTrue(Currency.isValidCode(" gbp "))
        assertEquals(false, Currency.isValidCode("US"))
        assertEquals(false, Currency.isValidCode("EURO"))
        assertEquals(false, Currency.isValidCode("US1"))
        assertEquals(false, Currency.isValidCode(""))
        assertEquals(USD, Currency.parse("usd"))
        assertEquals(null, Currency.parse("dollars"))
    }

    @Test
    fun constructing_an_invalid_currency_is_rejected() {
        assertFailsWith<IllegalArgumentException> { Currency.of("US") }
        assertFailsWith<IllegalArgumentException> { Currency("EURO", 2) }
    }

    @Test
    fun parse_rejects_invalid_or_overprecise_input() {
        assertEquals(null, Money.parse("", USD))
        assertEquals(null, Money.parse("abc", USD))
        assertEquals(null, Money.parse("1.999", USD)) // too many fraction digits
        assertEquals(null, Money.parse("1.5", JPY)) // JPY has no minor units
        assertEquals(null, Money.parse("1.2.3", USD))
    }
}
