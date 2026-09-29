package nz.eloque.quits.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EntryTest {
    private val a = mid("a")
    private val b = mid("b")

    @Test
    fun exposes_its_bill() {
        val payments = listOf(Payment(a, usd(3000)))
        val split = Split.Equal(listOf(a, b))
        val entry = Entry(EntryId("e1"), "Dinner", payments, split)
        assertEquals(Bill(payments, split), entry.bill)
        assertEquals(usd(3000), entry.total)
        assertEquals(usd(1500), entry.shareOf(b))
        assertEquals(usd(3000), entry.paymentsBy(a))
    }

    @Test
    fun rejects_an_invalid_bill() {
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
