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
}
