package nz.eloque.quits.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BillTest {
    private val a = mid("a")
    private val b = mid("b")
    private val c = mid("c")

    @Test
    fun derives_shares_and_totals_from_payments_and_split() {
        val bill = Bill(listOf(Payment(a, usd(3000))), Split.Equal(listOf(a, b)))
        assertEquals(usd(3000), bill.total)
        assertEquals(usd(1500), bill.shareOf(a))
        assertEquals(usd(1500), bill.shareOf(b))
        assertEquals(usd(3000), bill.paymentsBy(a))
        assertEquals(usd(0), bill.paymentsBy(b))
    }

    @Test
    fun rejects_no_payments() {
        assertFailsWith<IllegalArgumentException> { Bill(emptyList(), Split.Equal(listOf(a))) }
    }

    @Test
    fun rejects_payments_in_mixed_currencies() {
        assertFailsWith<IllegalArgumentException> {
            Bill(listOf(Payment(a, usd(100)), Payment(b, Money(100, EUR))), Split.Equal(listOf(a, b)))
        }
    }

    @Test
    fun rejects_exact_amounts_not_matching_total() {
        assertFailsWith<IllegalArgumentException> {
            Bill(listOf(Payment(a, usd(1000))), Split.Exact(mapOf(a to usd(500), b to usd(400))))
        }
    }

    @Test
    fun net_is_paid_minus_share_and_includes_payers_without_share() {
        val bill = Bill(listOf(Payment(a, usd(1000)), Payment(b, usd(200))), Split.Equal(listOf(b, c)))
        assertEquals(mapOf(a to usd(1000), b to usd(-400), c to usd(-600)), bill.net())
    }

    @Test
    fun net_sums_to_zero_for_every_split_kind() {
        val payments = listOf(Payment(a, usd(701)), Payment(c, usd(300)))
        val splits =
            listOf(
                Split.Equal(listOf(a, b, c)),
                Split.Shares(mapOf(a to 2L, b to 1L, c to 4L)),
                Split.Percentage(mapOf(a to 33, b to 33, c to 34)),
                Split.Exact(mapOf(a to usd(1), b to usd(500), c to usd(500))),
                Split.Itemized(
                    listOf(
                        Split.Itemized.Item("Pizza", usd(801), setOf(a, b, c)),
                        Split.Itemized.Item("Wine", usd(200), setOf(b)),
                    ),
                ),
            )
        splits.forEach { split ->
            assertEquals(0L, Bill(payments, split).net().values.sumOf { it.minorUnits }, "split $split")
        }
    }
}
