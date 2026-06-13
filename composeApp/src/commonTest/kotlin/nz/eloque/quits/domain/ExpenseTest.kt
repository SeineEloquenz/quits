package nz.eloque.quits.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ExpenseTest {
    private val a = mid("a")
    private val b = mid("b")

    @Test
    fun derives_shares_and_totals_from_payments_and_split() {
        val expense =
            Expense(
                ExpenseId("e1"),
                "Dinner",
                listOf(Payment(a, usd(3000))),
                Split.Equal(listOf(a, b)),
            )
        assertEquals(usd(3000), expense.total)
        assertEquals(usd(1500), expense.owedBy(a))
        assertEquals(usd(1500), expense.owedBy(b))
        assertEquals(usd(3000), expense.paidBy(a))
        assertEquals(usd(0), expense.paidBy(b))
    }

    @Test
    fun rejects_payments_in_mixed_currencies() {
        assertFailsWith<IllegalArgumentException> {
            Expense(
                ExpenseId("e"),
                "x",
                listOf(Payment(a, usd(100)), Payment(b, Money(100, EUR))),
                Split.Equal(listOf(a, b)),
            )
        }
    }
}
