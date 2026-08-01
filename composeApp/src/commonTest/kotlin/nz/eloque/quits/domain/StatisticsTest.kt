@file:OptIn(ExperimentalTime::class)

package nz.eloque.quits.domain

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

class StatisticsTest {
    private val a = mid("a")
    private val b = mid("b")
    private val c = mid("c")
    private val members = listOf(Member(a, "Alice"), Member(b, "Bob"), Member(c, "Carol"))

    private fun millisAt(
        year: Int,
        month: Int,
        day: Int,
    ): Long = LocalDate(year, month, day).atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

    @Test
    fun total_and_breakdowns_reconcile_and_sort() {
        val group =
            Group(
                GroupId("g"),
                "Trip",
                USD,
                members,
                entries =
                    listOf(
                        Entry(
                            EntryId("e1"),
                            "Dinner",
                            listOf(Payment(a, usd(3000))),
                            Split.Equal(listOf(a, b, c)),
                            categoryId = CategoryId("food"),
                        ),
                        Entry(
                            EntryId("e2"),
                            "Taxi",
                            listOf(Payment(b, usd(1200))),
                            Split.Equal(listOf(a, b)),
                            categoryId = CategoryId("transport"),
                        ),
                        Entry(
                            EntryId("e3"),
                            "Snacks",
                            listOf(Payment(c, usd(900))),
                            Split.Equal(listOf(a, b, c)),
                            categoryId = CategoryId("food"),
                        ),
                    ),
            )

        val s = group.spending()

        assertEquals(usd(5100), s.total)
        // Food 3000+900, Transport 1200 — largest first.
        assertEquals(
            listOf(CategoryId("food") to usd(3900), CategoryId("transport") to usd(1200)),
            s.byCategory.map { it.categoryId to it.amount },
        )
        // a=1000+600+300, b=1000+600+300, c=1000+0+300 → both breakdowns reconcile to the total.
        assertEquals(usd(5100), s.byMember.fold(Money.zero(USD)) { acc, m -> acc + m.amount })
        assertEquals(usd(1300), s.byMember.first { it.member == c }.amount)
        assertTrue(s.byMember.zipWithNext().all { (x, y) -> x.amount.minorUnits >= y.amount.minorUnits })
    }

    @Test
    fun spending_excludes_income() {
        val group =
            Group(
                GroupId("g"),
                "Trip",
                USD,
                members,
                entries =
                    listOf(
                        Entry(EntryId("e1"), "Dinner", listOf(Payment(a, usd(3000))), Split.Equal(listOf(a, b, c))),
                        Entry(
                            EntryId("i1"),
                            "Refund",
                            listOf(Payment(a, usd(9000))),
                            Split.Equal(listOf(a, b, c)),
                            kind = EntryKind.INCOME,
                        ),
                    ),
            )
        val s = group.spending()
        // Only the 30.00 entry counts; the income is ignored entirely.
        assertEquals(usd(3000), s.total)
        assertEquals(1, s.byCategory.size)
    }

    @Test
    fun spend_over_time_buckets_by_month_and_fills_gaps() {
        val group =
            Group(
                GroupId("g"),
                "Trip",
                USD,
                members,
                entries =
                    listOf(
                        Entry(EntryId("e1"), "a", listOf(Payment(a, usd(1000))), Split.Equal(listOf(a)), spentAt = millisAt(2026, 1, 10)),
                        Entry(EntryId("e2"), "b", listOf(Payment(a, usd(500))), Split.Equal(listOf(a)), spentAt = millisAt(2026, 1, 20)),
                        Entry(EntryId("e3"), "c", listOf(Payment(a, usd(700))), Split.Equal(listOf(a)), spentAt = millisAt(2026, 3, 5)),
                    ),
            )

        val buckets = group.spendOverTime(SpendPeriod.MONTH)

        // Jan (summed), Feb filled with zero, Mar — continuous timeline, first-of-month starts.
        assertEquals(listOf(LocalDate(2026, 1, 1), LocalDate(2026, 2, 1), LocalDate(2026, 3, 1)), buckets.map { it.start })
        assertEquals(listOf(usd(1500), usd(0), usd(700)), buckets.map { it.amount })
        // Income is excluded from the timeline like every other spending view.
        val withIncome =
            Group(
                GroupId("g"),
                "Trip",
                USD,
                members,
                entries =
                    group.entries +
                        Entry(
                            EntryId("i1"),
                            "refund",
                            listOf(Payment(a, usd(9999))),
                            Split.Equal(listOf(a)),
                            spentAt = millisAt(2026, 1, 15),
                            kind = EntryKind.INCOME,
                        ),
            )
        assertEquals(buckets.map { it.amount }, withIncome.spendOverTime(SpendPeriod.MONTH).map { it.amount })
    }

    @Test
    fun spend_over_time_buckets_by_week_from_monday() {
        val group =
            Group(
                GroupId("g"),
                "Trip",
                USD,
                members,
                entries =
                    listOf(
                        // 2026-01-05 is a Monday; the 7th falls in the same week.
                        Entry(EntryId("e1"), "a", listOf(Payment(a, usd(1000))), Split.Equal(listOf(a)), spentAt = millisAt(2026, 1, 5)),
                        Entry(EntryId("e2"), "b", listOf(Payment(a, usd(500))), Split.Equal(listOf(a)), spentAt = millisAt(2026, 1, 7)),
                        Entry(EntryId("e3"), "c", listOf(Payment(a, usd(700))), Split.Equal(listOf(a)), spentAt = millisAt(2026, 1, 19)),
                    ),
            )

        val buckets = group.spendOverTime(SpendPeriod.WEEK)

        assertEquals(listOf(LocalDate(2026, 1, 5), LocalDate(2026, 1, 12), LocalDate(2026, 1, 19)), buckets.map { it.start })
        assertEquals(listOf(usd(1500), usd(0), usd(700)), buckets.map { it.amount })
    }

    @Test
    fun spend_over_time_is_empty_without_expenses() {
        val group = Group(GroupId("g"), "Trip", USD, members)
        assertTrue(group.spendOverTime(SpendPeriod.MONTH).isEmpty())
    }

    @Test
    fun spend_over_time_within_one_period_is_a_single_bucket() {
        // A group with all spend in one week/month yields one bucket — the UI hides the chart then.
        val group =
            Group(
                GroupId("g"),
                "Trip",
                USD,
                members,
                entries =
                    listOf(
                        Entry(EntryId("e1"), "a", listOf(Payment(a, usd(1000))), Split.Equal(listOf(a)), spentAt = millisAt(2026, 1, 5)),
                        Entry(EntryId("e2"), "b", listOf(Payment(a, usd(500))), Split.Equal(listOf(a)), spentAt = millisAt(2026, 1, 7)),
                    ),
            )
        assertEquals(1, group.spendOverTime(SpendPeriod.WEEK).size)
        assertEquals(1, group.spendOverTime(SpendPeriod.MONTH).size)
    }

    @Test
    fun uncategorized_expenses_group_under_null() {
        val group =
            Group(
                GroupId("g"),
                "Trip",
                USD,
                members,
                entries = listOf(Entry(EntryId("e"), "x", listOf(Payment(a, usd(1000))), Split.Equal(listOf(a, b)))),
            )

        val s = group.spending()

        assertEquals(listOf<CategoryId?>(null), s.byCategory.map { it.categoryId })
        assertEquals(usd(1000), s.byCategory.first().amount)
    }
}
