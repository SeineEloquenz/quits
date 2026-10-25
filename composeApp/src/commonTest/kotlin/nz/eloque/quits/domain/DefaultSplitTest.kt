package nz.eloque.quits.domain

import nz.eloque.quits.domain.DefaultSplit.Kind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DefaultSplitTest {
    private val a = mid("a")
    private val b = mid("b")
    private val c = mid("c")

    @Test
    fun round_trips_through_from() {
        val members = listOf(a, b, c)
        listOf(
            Split.Equal(listOf(a, c)),
            Split.Shares(mapOf(a to 2L, b to 1L)),
            Split.Percentage(mapOf(a to 60, b to 40)),
        ).forEach { split -> assertEquals(split, DefaultSplit.from(split, members)!!.splitFor(members)) }
        assertNull(DefaultSplit.from(Split.Exact(mapOf(a to usd(100))), members))
    }

    @Test
    fun a_newcomer_is_included_and_an_excluded_member_stays_out() {
        val equal = DefaultSplit(Kind.EQUAL, mapOf(a to 1L, b to 0L))
        assertEquals(Split.Equal(listOf(a, c)), equal.splitFor(listOf(a, b, c)))

        val shares = DefaultSplit(Kind.SHARES, mapOf(a to 2L, b to 0L))
        assertEquals(Split.Shares(mapOf(a to 2L, c to 1L)), shares.splitFor(listOf(a, b, c)))
    }

    @Test
    fun removed_members_are_ignored() {
        val shares = DefaultSplit(Kind.SHARES, mapOf(a to 2L, b to 1L, c to 5L))
        assertEquals(Split.Shares(mapOf(a to 2L, b to 1L)), shares.splitFor(listOf(a, b)))
    }

    @Test
    fun percentage_falls_back_to_equal_once_it_no_longer_covers_the_members() {
        val percent = DefaultSplit(Kind.PERCENTAGE, mapOf(a to 60L, b to 40L))
        assertEquals(Split.Equal(listOf(a, b, c)), percent.splitFor(listOf(a, b, c)))
        assertEquals(Split.Equal(listOf(a)), percent.splitFor(listOf(a)))
    }

    @Test
    fun excluding_everyone_falls_back_to_equal() {
        assertEquals(Split.Equal(listOf(a, b)), DefaultSplit(Kind.EQUAL, mapOf(a to 0L, b to 0L)).splitFor(listOf(a, b)))
        assertEquals(Split.Equal(listOf(a, b)), DefaultSplit(Kind.SHARES, mapOf(a to 0L, b to 0L)).splitFor(listOf(a, b)))
    }
}
