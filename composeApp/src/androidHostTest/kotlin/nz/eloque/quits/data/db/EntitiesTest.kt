package nz.eloque.quits.data.db

import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EntitiesTest {
    private val db = inMemoryDatabase()

    @AfterTest fun tearDown() = db.close()

    private suspend fun seedGroup(id: String = "g") {
        db.groupDao().upsert(GroupEntity(id, "Trip", "USD", "JOIN42", meta()))
    }

    @Test
    fun group_and_members_round_trip() =
        runTest {
            seedGroup()
            db.memberDao().upsert(
                listOf(
                    MemberEntity("m1", "g", "Alice", 0xFF0000, meta()),
                    MemberEntity("m2", "g", "Bob", null, meta()),
                ),
            )
            assertEquals("Trip", db.groupDao().byId("g")?.name)
            val members = db.memberDao().forGroup("g")
            assertEquals(listOf("Alice", "Bob"), members.map { it.name }.sorted())
        }

    @Test
    fun deleting_a_group_cascades_to_children() =
        runTest {
            seedGroup()
            db.memberDao().upsert(listOf(MemberEntity("m1", "g", "Alice", null, meta())))
            db.expenseDao().save(
                ExpenseEntity("e", "g", "Dinner", 3000, "USD", 1.0, null, 10, null, "EQUAL", meta()),
                listOf(ExpensePayerEntity("p", "e", "m1", 3000)),
                listOf(ExpenseSplitEntity("s", "e", "m1", 3000)),
            )

            db.groupDao().delete("g")

            assertNull(db.groupDao().byId("g"))
            assertTrue(db.memberDao().forGroup("g").isEmpty())
            assertNull(db.expenseDao().byId("e")) // expense + its payer/split lines gone
        }

    @Test
    fun expense_with_payers_and_splits_round_trips() =
        runTest {
            seedGroup()
            db.expenseDao().save(
                ExpenseEntity("e", "g", "Hotel", 10000, "EUR", 1.1, "lodging", 5, "two nights", "SHARES", meta()),
                listOf(
                    ExpensePayerEntity("p1", "e", "m1", 6000),
                    ExpensePayerEntity("p2", "e", "m2", 4000),
                ),
                listOf(
                    ExpenseSplitEntity("s1", "e", "m1", 5000, weight = 1.0),
                    ExpenseSplitEntity("s2", "e", "m2", 5000, weight = 1.0),
                ),
            )

            val loaded = db.expenseDao().byId("e")!!
            assertEquals("Hotel", loaded.expense.title)
            assertEquals(10000, loaded.payers.sumOf { it.amountMinor })
            assertEquals(2, loaded.splits.size)
        }

    @Test
    fun saving_an_expense_replaces_old_lines() =
        runTest {
            seedGroup()
            val base = ExpenseEntity("e", "g", "x", 100, "USD", 1.0, null, 0, null, "EQUAL", meta())
            db.expenseDao().save(
                base,
                listOf(ExpensePayerEntity("p1", "e", "m1", 100)),
                listOf(ExpenseSplitEntity("s1", "e", "m1", 100)),
            )
            db.expenseDao().save(
                base,
                listOf(ExpensePayerEntity("p2", "e", "m2", 100)),
                listOf(ExpenseSplitEntity("s2", "e", "m2", 100)),
            )

            val loaded = db.expenseDao().byId("e")!!
            assertEquals(listOf("p2"), loaded.payers.map { it.id })
            assertEquals(listOf("s2"), loaded.splits.map { it.id })
        }

    @Test
    fun settlement_round_trips() =
        runTest {
            seedGroup()
            db.settlementDao().upsert(
                SettlementEntity("st", "g", "m2", "m1", 2500, "USD", 1.0, 7, null, meta()),
            )
            val settlements = db.settlementDao().forGroup("g")
            assertEquals(1, settlements.size)
            assertEquals("m1", settlements.single().toMember)
        }

    @Test
    fun fx_rate_upsert_replaces_by_pair() =
        runTest {
            db.fxRateDao().put(FxRateEntity("USD", "EUR", 0.9, asOf = 1))
            db.fxRateDao().put(FxRateEntity("USD", "EUR", 0.95, asOf = 2))
            val rate = db.fxRateDao().get("USD", "EUR")!!
            assertEquals(0.95, rate.rate)
            assertEquals(2, rate.asOf)
            assertNull(db.fxRateDao().get("USD", "GBP"))
        }
}
