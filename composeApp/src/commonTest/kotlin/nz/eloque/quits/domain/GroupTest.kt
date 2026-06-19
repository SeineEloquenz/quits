package nz.eloque.quits.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupTest {
    private val a = mid("a")
    private val b = mid("b")
    private val c = mid("c")
    private val members = listOf(Member(a, "A"), Member(b, "B"), Member(c, "C"))

    private fun dinnerGroup(): Group {
        // A pays 30.00, split equally among A/B/C → each owes 10.00.
        val expense =
            Expense(
                ExpenseId("e1"),
                "Dinner",
                listOf(Payment(a, usd(3000))),
                Split.Equal(listOf(a, b, c)),
            )
        return Group(GroupId("g"), "Trip", USD, members, listOf(expense))
    }

    @Test
    fun balances_net_to_zero() {
        val balances = dinnerGroup().balances()
        assertEquals(usd(2000), balances.of(a))
        assertEquals(usd(-1000), balances.of(b))
        assertEquals(usd(-1000), balances.of(c))
        assertEquals(0L, balances.net.values.sumOf { it.minorUnits })
    }

    @Test
    fun settle_up_clears_every_debt() {
        val balances = dinnerGroup().balances()
        val transfers = balances.simplify()

        assertEquals(2, transfers.size)
        assertTrue(transfers.all { it.to == a })

        // Applying the transfers must zero everyone out.
        val net = balances.net.mapValues { it.value.minorUnits }.toMutableMap()
        for (t in transfers) {
            net[t.from] = net.getValue(t.from) + t.amount.minorUnits
            net[t.to] = net.getValue(t.to) - t.amount.minorUnits
        }
        assertTrue(net.values.all { it == 0L })
    }

    @Test
    fun balances_convert_foreign_expenses_to_base() {
        // A pays 100.00 EUR split equally A/B; base USD at 1.1 → B owes A 55.00 USD.
        val expense =
            Expense(
                ExpenseId("e"),
                "Hotel",
                listOf(Payment(a, Money(10000, EUR))),
                Split.Equal(listOf(a, b)),
                rateToBase = 1.1,
            )
        val group = Group(GroupId("g"), "g", USD, listOf(Member(a, "A"), Member(b, "B")), listOf(expense))
        val balances = group.balances()
        assertEquals(usd(5500), balances.of(a))
        assertEquals(usd(-5500), balances.of(b))
    }

    @Test
    fun references_reports_members_used_by_expenses_or_settlements() {
        val group =
            Group(
                GroupId("g"),
                "Trip",
                USD,
                members,
                listOf(
                    Expense(ExpenseId("e1"), "Dinner", listOf(Payment(a, usd(3000))), Split.Equal(listOf(a, b))),
                ),
                listOf(Settlement(SettlementId("s1"), b, c, usd(500))),
            )
        // a pays, b shares and settles, c receives the settlement → all referenced.
        assertTrue(group.references(a))
        assertTrue(group.references(b))
        assertTrue(group.references(c))
        // an unreferenced member is removable.
        val d = mid("d")
        val withSpare = Group(GroupId("g"), "Trip", USD, members + Member(d, "D"), group.expenses, group.settlements)
        assertFalse(withSpare.references(d))
    }

    @Test
    fun rejects_expenses_referencing_unknown_members() {
        val expense =
            Expense(
                ExpenseId("e"),
                "x",
                listOf(Payment(mid("ghost"), usd(100))),
                Split.Equal(listOf(mid("ghost"))),
            )
        assertFailsWith<IllegalArgumentException> {
            Group(GroupId("g"), "g", USD, members, listOf(expense))
        }
    }
}
