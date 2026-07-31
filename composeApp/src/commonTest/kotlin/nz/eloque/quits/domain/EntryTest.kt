package nz.eloque.quits.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EntryTest {
    private val a = mid("a")
    private val b = mid("b")

    @Test
    fun derives_shares_and_totals_from_payments_and_split() {
        val entry =
            Entry(
                EntryId("e1"),
                "Dinner",
                listOf(Payment(a, usd(3000))),
                Split.Equal(listOf(a, b)),
            )
        assertEquals(usd(3000), entry.total)
        assertEquals(usd(1500), entry.shareOf(a))
        assertEquals(usd(1500), entry.shareOf(b))
        assertEquals(usd(3000), entry.paymentsBy(a))
        assertEquals(usd(0), entry.paymentsBy(b))
    }

    @Test
    fun rejects_payments_in_mixed_currencies() {
        assertFailsWith<IllegalArgumentException> {
            Entry(
                EntryId("e"),
                "x",
                listOf(Payment(a, usd(100)), Payment(b, Money(100, EUR))),
                Split.Equal(listOf(a, b)),
            )
        }
    }
}
