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
}
