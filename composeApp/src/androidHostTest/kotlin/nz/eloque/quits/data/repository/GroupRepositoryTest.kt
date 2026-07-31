package nz.eloque.quits.data.repository

import kotlinx.coroutines.test.runTest
import nz.eloque.quits.data.db.ExpenseEntity
import nz.eloque.quits.data.db.ExpensePayerEntity
import nz.eloque.quits.data.db.ExpenseSplitEntity
import nz.eloque.quits.data.db.inMemoryDatabase
import nz.eloque.quits.data.db.meta
import nz.eloque.quits.domain.Category
import nz.eloque.quits.domain.CategoryId
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
    fun itemized_split_round_trips_with_items() =
        runTest {
            repo.saveGroup(sampleGroup())
            val itemized =
                Expense(
                    ExpenseId("e-items"),
                    "Groceries",
                    listOf(Payment(a, Money(10000, usd))),
                    Split.Itemized(
                        listOf(
                            Split.Itemized.Item("Pasta", Money(3000, usd), setOf(a)),
                            Split.Itemized.Item("Wine", Money(7000, usd), setOf(a, b, c)),
                        ),
                    ),
                )
            repo.upsertExpense(GroupId("g"), itemized)

            val loaded = repo.load(GroupId("g"))!!.expenses.first { it.id == ExpenseId("e-items") }
            val split = loaded.split
            assertTrue(split is Split.Itemized)
            assertEquals(2, split.items.size)
            assertEquals("Pasta", split.items[0].label)
            assertEquals(Money(3000, usd), split.items[0].amount)
            assertEquals(setOf(a), split.items[0].participants)
            assertEquals(setOf(a, b, c), split.items[1].participants)
            // Shares derived from the reconstructed items match the original.
            assertEquals(itemized.shares, loaded.shares)
        }

    @Test
    fun unknown_split_type_degrades_to_exact_and_is_marked_unsupported() =
        runTest {
            repo.saveGroup(sampleGroup())
            // An expense synced from a newer version, with a split type this build doesn't recognise.
            db.expenseDao().save(
                ExpenseEntity("e-future", "g", "Future", 3000, "USD", 1.0, null, 10, 0, null, "FUTURE_KIND", "EXPENSE", meta()),
                listOf(ExpensePayerEntity("e-future:payer:0", "e-future", "a", 3000)),
                listOf(
                    ExpenseSplitEntity("e-future:a", "e-future", "a", 2000),
                    ExpenseSplitEntity("e-future:b", "e-future", "b", 1000),
                ),
                emptyList(),
                emptyList(),
            )

            val loaded = repo.load(GroupId("g"))!!.expenses.first { it.id == ExpenseId("e-future") }
            // Rebuilt from the stored shares as Exact — no throw — and flagged read-only.
            assertTrue(loaded.split is Split.Exact)
            assertFalse(loaded.splitSupported)
            assertEquals(Money(2000, usd), loaded.owedBy(a))
            assertEquals(Money(1000, usd), loaded.owedBy(b))
        }

    @Test
    fun custom_categories_round_trip_and_delete() =
        runTest {
            repo.saveGroup(sampleGroup())
            repo.upsertCategory(GroupId("g"), Category(CategoryId("cat1"), "Snacks", "fastfood", 0xFF4CAF50))

            val loaded = repo.load(GroupId("g"))!!.categories
            assertEquals(1, loaded.size)
            assertEquals("Snacks", loaded.first().name)
            assertEquals("fastfood", loaded.first().icon)

            repo.deleteCategory(CategoryId("cat1"))
            assertTrue(repo.load(GroupId("g"))!!.categories.isEmpty())
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
                sampleGroup().expenses.first().let {
                    Expense(it.id, it.title, it.payments, it.split, categoryId = CategoryId("food"), note = "split dinner")
                },
                spentAt = 555L,
            )

            val stored = db.expenseDao().byId("e-equal")!!
            assertEquals("food", stored.expense.categoryId)
            assertEquals(555L, stored.expense.spentAt)
            assertEquals("split dinner", stored.expense.note)
            assertTrue(stored.expense.sync.dirty)
            assertEquals("dev-1", stored.expense.sync.deviceId)
            assertEquals(1000L, stored.expense.sync.updatedAt)
        }

    @Test
    fun clearing_category_and_note_persists_the_clear() =
        runTest {
            repo.saveGroup(sampleGroup())
            val base = sampleGroup().expenses.first()
            repo.upsertExpense(
                GroupId("g"),
                Expense(base.id, base.title, base.payments, base.split, categoryId = CategoryId("food"), note = "split dinner"),
            )

            repo.upsertExpense(
                GroupId("g"),
                Expense(base.id, base.title, base.payments, base.split, categoryId = null, note = null),
            )

            val stored = db.expenseDao().byId("e-equal")!!
            assertNull(stored.expense.categoryId)
            assertNull(stored.expense.note)
        }

    @Test
    fun category_and_note_survive_round_trip() =
        runTest {
            repo.saveGroup(sampleGroup())
            val base = sampleGroup().expenses.first()
            repo.upsertExpense(
                GroupId("g"),
                Expense(base.id, base.title, base.payments, base.split, categoryId = CategoryId("food"), note = "split dinner"),
            )

            val loaded = repo.load(GroupId("g"))!!.expenses.first { it.id == base.id }
            assertEquals(CategoryId("food"), loaded.categoryId)
            assertEquals("split dinner", loaded.note)
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

    @Test
    fun leaving_removes_the_group_and_all_its_data() =
        runTest {
            persist(sampleGroup())
            repo.leaveGroup(GroupId("g"))

            assertNull(repo.load(GroupId("g")))
            assertTrue(db.groupDao().all().isEmpty())
            assertTrue(db.expenseDao().forGroup("g").isEmpty())
            assertTrue(db.settlementDao().forGroup("g").isEmpty())
        }
}
