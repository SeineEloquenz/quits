package nz.eloque.quits.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SplitTest {
    private val a = mid("a")
    private val b = mid("b")
    private val c = mid("c")

    @Test
    fun equal_split_sums_exactly_via_largest_remainder() {
        val shares = Split.Equal(listOf(a, b, c)).divide(usd(10000)) // 100.00 / 3
        assertEquals(usd(10000), shares.values.reduce { x, y -> x + y })
        assertEquals(usd(3334), shares[a]) // extra cent to the lowest id, deterministically
        assertEquals(usd(3333), shares[b])
        assertEquals(usd(3333), shares[c])
    }

    @Test
    fun shares_split_is_weighted() {
        val shares = Split.Shares(mapOf(a to 2L, b to 1L)).divide(usd(300))
        assertEquals(usd(200), shares[a])
        assertEquals(usd(100), shares[b])
    }

    @Test
    fun percentage_must_sum_to_100() {
        assertFailsWith<IllegalArgumentException> { Split.Percentage(mapOf(a to 60, b to 30)) }
        val shares = Split.Percentage(mapOf(a to 60, b to 40)).divide(usd(1000))
        assertEquals(usd(600), shares[a])
        assertEquals(usd(400), shares[b])
    }

    @Test
    fun exact_must_equal_total() {
        assertFailsWith<IllegalArgumentException> {
            Split.Exact(mapOf(a to usd(100), b to usd(100))).divide(usd(300))
        }
        val shares = Split.Exact(mapOf(a to usd(100), b to usd(200))).divide(usd(300))
        assertEquals(usd(100), shares[a])
        assertEquals(usd(200), shares[b])
    }

    @Test
    fun itemized_sums_per_member_across_items() {
        // Alice's item (30), Bob's item (20), and a shared item (50) split between all three.
        val split =
            Split.Itemized(
                listOf(
                    Split.Itemized.Item("Pasta", usd(3000), setOf(a)),
                    Split.Itemized.Item("Steak", usd(2000), setOf(b)),
                    Split.Itemized.Item("Wine", usd(5000), setOf(a, b, c)),
                ),
            )
        val shares = split.divide(usd(10000))
        assertEquals(usd(10000), shares.values.reduce { x, y -> x + y })
        // Wine 50.00 / 3 = 16.67 (extra cent to lowest id a), 16.67 to b, 16.66 to c.
        assertEquals(usd(3000 + 1667), shares[a])
        assertEquals(usd(2000 + 1667), shares[b])
        assertEquals(usd(1666), shares[c])
    }

    @Test
    fun itemized_must_equal_total() {
        assertFailsWith<IllegalArgumentException> {
            Split.Itemized(listOf(Split.Itemized.Item("x", usd(100), setOf(a)))).divide(usd(300))
        }
    }

    @Test
    fun itemized_item_needs_participants_and_positive_amount() {
        assertFailsWith<IllegalArgumentException> { Split.Itemized.Item("x", usd(100), emptySet()) }
        assertFailsWith<IllegalArgumentException> { Split.Itemized.Item("x", usd(0), setOf(a)) }
    }
}
