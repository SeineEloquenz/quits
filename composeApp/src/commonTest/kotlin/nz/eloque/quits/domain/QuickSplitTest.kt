package nz.eloque.quits.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QuickSplitTest {
    private val a = mid("a")
    private val b = mid("b")
    private val c = mid("c")
    private val people = listOf(Member(a, "A"), Member(b, "B"), Member(c, "C"))

    @Test
    fun rejects_a_bill_referencing_someone_outside_the_split() {
        assertFailsWith<IllegalArgumentException> {
            QuickSplit(people.take(2), Bill(listOf(Payment(a, usd(300))), Split.Equal(listOf(a, b, c))))
        }
    }

    @Test
    fun rejects_duplicate_people() {
        assertFailsWith<IllegalArgumentException> {
            QuickSplit(people + Member(a, "A again"), Bill(listOf(Payment(a, usd(300))), Split.Equal(listOf(a))))
        }
    }

    @Test
    fun people_without_a_part_in_the_bill_are_settled() {
        val split = QuickSplit(people, Bill(listOf(Payment(a, usd(300))), Split.Equal(listOf(a, b))))
        assertEquals(usd(0), split.balances.of(c))
        assertEquals(listOf(Transfer(b, a, usd(150))), split.transfers)
    }

    @Test
    fun transfers_settle_every_balance() {
        val bill =
            Bill(
                listOf(Payment(a, usd(1000)), Payment(b, usd(1)), Payment(c, usd(500))),
                Split.Shares(mapOf(a to 1L, b to 3L, c to 2L)),
            )
        val split = QuickSplit(people, bill)
        val settled = split.balances.net.mapValues { it.value.minorUnits }.toMutableMap()
        split.transfers.forEach {
            settled[it.from] = settled.getValue(it.from) + it.amount.minorUnits
            settled[it.to] = settled.getValue(it.to) - it.amount.minorUnits
        }
        assertEquals(mapOf(a to 0L, b to 0L, c to 0L), settled)
    }

    @Test
    fun matches_group_balances_for_the_same_expense() {
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
            val entry = Entry(EntryId("e"), "x", payments, split)
            val group = Group(GroupId("g"), "G", USD, people, listOf(entry))
            assertEquals(group.balances(), QuickSplit(people, entry.bill).balances, "split $split")
        }
    }
}
