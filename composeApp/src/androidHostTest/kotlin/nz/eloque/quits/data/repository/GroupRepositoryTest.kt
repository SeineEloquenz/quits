package nz.eloque.quits.data.repository

import kotlinx.coroutines.test.runTest
import nz.eloque.quits.data.db.inMemoryDatabase
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.Expense
import nz.eloque.quits.domain.ExpenseId
import nz.eloque.quits.domain.Group
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.Member
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.domain.Money
import nz.eloque.quits.domain.Payment
import nz.eloque.quits.domain.Settlement
import nz.eloque.quits.domain.SettlementId
import nz.eloque.quits.domain.Split
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GroupRepositoryTest {
    private val db = inMemoryDatabase()
    private val repo = GroupRepository(db, deviceId = "dev-1", now = { 1000L })

    @AfterTest fun tearDown() = db.close()

    private val usd = Currency.of("USD")
    private val eur = Currency.of("EUR")
    private val a = MemberId("a")
    private val b = MemberId("b")
    private val c = MemberId("c")

    /** A group that exercises every split type, two currencies, and multiple payers. */
    private fun sampleGroup(): Group {
        val equal =
            Expense(
                ExpenseId("e-equal"),
                "Dinner",
                listOf(Payment(a, Money(2000, usd)), Payment(b, Money(1000, usd))),
                Split.Equal(listOf(a, b, c)),
            )
        val exact =
            Expense(
                ExpenseId("e-exact"),
                "Hotel",
                listOf(Payment(a, Money(10000, eur))),
                Split.Exact(mapOf(a to Money(6000, eur), b to Money(4000, eur))),
                rateToBase = 1.1,
            )
        val shares =
            Expense(
                ExpenseId("e-shares"),
                "Taxi",
                listOf(Payment(c, Money(600, usd))),
                Split.Shares(mapOf(a to 2L, b to 1L)),
            )
        val percentage =
            Expense(
                ExpenseId("e-pct"),
                "Snacks",
                listOf(Payment(b, Money(1000, usd))),
                Split.Percentage(mapOf(a to 50, b to 50)),
            )
        return Group(
            GroupId("g"),
            "Trip",
            usd,
            listOf(Member(a, "Alice"), Member(b, "Bob"), Member(c, "Carol")),
            listOf(equal, exact, shares, percentage),
            listOf(Settlement(SettlementId("s"), from = b, to = a, amount = Money(500, usd))),
        )
    }

    private suspend fun persist(group: Group) {
        repo.saveGroup(group)
        group.expenses.forEachIndexed { i, e -> repo.upsertExpense(group.id, e, spentAt = 100L + i) }
        group.settlements.forEach { repo.upsertSettlement(group.id, it, paidAt = 200L) }
    }

    @Test
    fun aggregate_round_trips_and_balances_match() =
        runTest {
            val original = sampleGroup()
            persist(original)

            val loaded = repo.load(GroupId("g"))!!

            assertEquals(listOf("Alice", "Bob", "Carol"), loaded.members.map { it.name }.sorted())
            assertEquals(4, loaded.expenses.size)
            // The end-to-end proof: reconstructed splits/payers/rates yield identical balances.
            assertEquals(original.balances().net, loaded.balances().net)
        }

    @Test
    fun every_split_type_reconstructs() =
        runTest {
            persist(sampleGroup())
            val byId = repo.load(GroupId("g"))!!.expenses.associateBy { it.id }

            assertTrue(byId.getValue(ExpenseId("e-equal")).split is Split.Equal)
            assertTrue(byId.getValue(ExpenseId("e-exact")).split is Split.Exact)
            assertTrue(byId.getValue(ExpenseId("e-shares")).split is Split.Shares)
            assertTrue(byId.getValue(ExpenseId("e-pct")).split is Split.Percentage)
        }

    @Test
    fun multiple_payers_survive_round_trip() =
        runTest {
            persist(sampleGroup())
            val dinner = repo.load(GroupId("g"))!!.expenses.first { it.id == ExpenseId("e-equal") }
            assertEquals(2, dinner.payments.size)
            assertEquals(Money(3000, usd), dinner.total)
        }

    @Test
    fun writes_persist_display_metadata_and_dirty_sync_state() =
        runTest {
            repo.saveGroup(sampleGroup())
            repo.upsertExpense(
                GroupId("g"),
                sampleGroup().expenses.first(),
                spentAt = 555L,
                category = "food",
                note = "split dinner",
            )

            val stored = db.expenseDao().byId("e-equal")!!
            assertEquals("food", stored.expense.category)
            assertEquals(555L, stored.expense.spentAt)
            assertEquals("split dinner", stored.expense.note)
            assertTrue(stored.expense.sync.dirty)
            assertEquals("dev-1", stored.expense.sync.deviceId)
            assertEquals(1000L, stored.expense.sync.updatedAt)
        }

    @Test
    fun load_returns_null_for_unknown_group() =
        runTest {
            assertNull(repo.load(GroupId("nope")))
        }

    @Test
    fun rename_member_updates_the_name() =
        runTest {
            persist(sampleGroup())
            repo.renameMember(a, "Alicia")
            val loaded = repo.load(GroupId("g"))!!
            assertEquals("Alicia", loaded.members.first { it.id == a }.name)
        }

    @Test
    fun cannot_remove_a_referenced_member() =
        runTest {
            persist(sampleGroup()) // a, b, c are all in expenses
            assertFalse(repo.removeMember(GroupId("g"), a))
            assertTrue(repo.load(GroupId("g"))!!.members.any { it.id == a })
        }

    @Test
    fun removes_an_unreferenced_member() =
        runTest {
            persist(sampleGroup())
            val d = MemberId("d")
            repo.addMember(GroupId("g"), Member(d, "Dave"))
            assertTrue(repo.removeMember(GroupId("g"), d))
            assertFalse(repo.load(GroupId("g"))!!.members.any { it.id == d })
        }
}
