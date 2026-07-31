package nz.eloque.quits.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StatisticsTest {
    private val a = mid("a")
    private val b = mid("b")
    private val c = mid("c")
    private val members = listOf(Member(a, "Alice"), Member(b, "Bob"), Member(c, "Carol"))

    @Test
    fun total_and_breakdowns_reconcile_and_sort() {
        val group =
            Group(
                GroupId("g"),
                "Trip",
                USD,
                members,
                expenses =
                    listOf(
                        Expense(
                            ExpenseId("e1"),
                            "Dinner",
                            listOf(Payment(a, usd(3000))),
                            Split.Equal(listOf(a, b, c)),
                            categoryId = CategoryId("food"),
                        ),
                        Expense(
                            ExpenseId("e2"),
                            "Taxi",
                            listOf(Payment(b, usd(1200))),
                            Split.Equal(listOf(a, b)),
                            categoryId = CategoryId("transport"),
                        ),
                        Expense(
                            ExpenseId("e3"),
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
                expenses =
                    listOf(
                        Expense(ExpenseId("e1"), "Dinner", listOf(Payment(a, usd(3000))), Split.Equal(listOf(a, b, c))),
                        Expense(
                            ExpenseId("i1"),
                            "Refund",
                            listOf(Payment(a, usd(9000))),
                            Split.Equal(listOf(a, b, c)),
                            kind = EntryKind.INCOME,
                        ),
                    ),
            )
        val s = group.spending()
        // Only the 30.00 expense counts; the income is ignored entirely.
        assertEquals(usd(3000), s.total)
        assertEquals(1, s.byCategory.size)
    }

    @Test
    fun uncategorized_expenses_group_under_null() {
        val group =
            Group(
                GroupId("g"),
                "Trip",
                USD,
                members,
                expenses = listOf(Expense(ExpenseId("e"), "x", listOf(Payment(a, usd(1000))), Split.Equal(listOf(a, b)))),
            )

        val s = group.spending()

        assertEquals(listOf<CategoryId?>(null), s.byCategory.map { it.categoryId })
        assertEquals(usd(1000), s.byCategory.first().amount)
    }
}
