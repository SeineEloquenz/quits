package nz.eloque.quits.ui.components

import nz.eloque.quits.domain.EUR
import nz.eloque.quits.domain.USD
import nz.eloque.quits.domain.enUs
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AmountFieldValidationTest {
    @Test
    fun blank_input_is_not_flagged_as_invalid() {
        assertTrue(isValidAmountInput("", USD, format = enUs))
        assertTrue(isValidAmountInput("   ", USD, format = enUs))
    }

    @Test
    fun unknown_currency_makes_nonblank_input_invalid() {
        assertFalse(isValidAmountInput("19.99", null, format = enUs))
    }

    @Test
    fun unparseable_input_is_invalid() {
        assertFalse(isValidAmountInput("abc", USD, format = enUs))
        assertFalse(isValidAmountInput("1.999", USD, format = enUs)) // too many fraction digits for USD
    }

    @Test
    fun valid_positive_amount_passes() {
        assertTrue(isValidAmountInput("19.99", USD, format = enUs))
        assertTrue(isValidAmountInput("1,234.56", USD, format = enUs))
    }

    @Test
    fun zero_or_negative_is_invalid_when_positive_required() {
        assertFalse(isValidAmountInput("0", EUR, format = enUs))
        assertFalse(isValidAmountInput("-5.00", EUR, format = enUs))
    }

    @Test
    fun zero_or_negative_is_allowed_when_positive_not_required() {
        assertTrue(isValidAmountInput("0", EUR, requirePositive = false, format = enUs))
        assertTrue(isValidAmountInput("-5.00", EUR, requirePositive = false, format = enUs))
    }
}
