package nz.eloque.quits.data.sync

import kotlinx.coroutines.test.runTest
import nz.eloque.quits.data.crypto.GroupCrypto
import nz.eloque.quits.data.db.inMemoryDatabase
import nz.eloque.quits.data.repository.GroupRepository
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.Expense
import nz.eloque.quits.domain.ExpenseId
import nz.eloque.quits.domain.Group
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.Member
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.domain.Money
import nz.eloque.quits.domain.Payment
import nz.eloque.quits.domain.Split
import nz.eloque.quits.util.newId
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class FakeRelay : Relay {
    private data class Stored(
        val record: EncryptedRecord,
        val seq: Long,
    )

    private val containers = mutableMapOf<String, MutableMap<String, Stored>>()
    private val lookups = mutableMapOf<String, String>()
    private var seq = 0L

    override suspend fun createGroup(lookupId: String): GroupHandle {
        val remoteId = newId()
        containers[remoteId] = mutableMapOf()
        lookups[lookupId] = remoteId
        return GroupHandle(remoteId, "token-$remoteId")
    }

    override suspend fun joinGroup(lookupId: String): GroupHandle? {
        val remoteId = lookups[lookupId] ?: return null
        return GroupHandle(remoteId, "token-$remoteId")
    }

    override suspend fun push(
        remoteId: String,
        token: String,
        records: List<EncryptedRecord>,
    ): PushResult {
        val store = containers.getOrPut(remoteId) { mutableMapOf() }
        val applied = mutableListOf<String>()
        val rejected = mutableListOf<String>()
        for (record in records) {
            val current = store[record.id]
            val wins =
                current == null ||
                    record.updatedAt > current.record.updatedAt ||
                    (record.updatedAt == current.record.updatedAt && record.deviceId > current.record.deviceId)
            if (wins) {
                seq += 1
                store[record.id] = Stored(record, seq)
                applied += record.id
            } else {
                rejected += record.id
            }
        }
        return PushResult(store.values.maxOfOrNull { it.seq } ?: 0, applied, rejected)
    }

    override suspend fun pull(
        remoteId: String,
        token: String,
        since: Long,
    ): PullResult {
        val store = containers[remoteId] ?: return PullResult(emptyList(), since)
        val fresh = store.values.filter { it.seq > since }.sortedBy { it.seq }
        val seq = fresh.maxOfOrNull { it.seq } ?: since
        return PullResult(fresh.map { it.record }, seq)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyncEngineTest {
    private val usd = Currency.of("USD")
    private val a = MemberId("a")
    private val b = MemberId("b")

    @Test
    fun two_devices_converge_through_the_relay() =
        runTest {
            val relay = FakeRelay()
            var clock = 1000L

            val db1 = inMemoryDatabase()
            val repo1 = GroupRepository(db1, deviceId = "dev1", now = { clock })
            val engine1 = SyncEngine(db1, relay, GroupCrypto(), deviceId = "dev1")

            val db2 = inMemoryDatabase()
            val repo2 = GroupRepository(db2, deviceId = "dev2", now = { clock })
            val engine2 = SyncEngine(db2, relay, GroupCrypto(), deviceId = "dev2")

            try {
                // device 1 builds a group locally and shares it.
                val g = GroupId("g-local")
                repo1.saveGroup(Group(g, "Trip", usd, listOf(Member(a, "Alice"), Member(b, "Bob"))))
                repo1.upsertExpense(
                    g,
                    Expense(ExpenseId("e1"), "Dinner", listOf(Payment(a, Money(3000, usd))), Split.Equal(listOf(a, b))),
                    spentAt = 1,
                )
                val code = engine1.share(g)

                // device 2 joins by code and pulls the whole group down.
                clock = 1500L
                val joined = engine2.join(code)!!
                val onDevice1 = repo1.load(g)!!
                val onDevice2 = repo2.load(joined)!!
                assertEquals(2, onDevice2.members.size)
                assertEquals(1, onDevice2.expenses.size)
                assertEquals(
                    onDevice1.balances().net.values.map {
                        it.minorUnits
                    }.sorted(),
                    onDevice2.balances().net.values.map { it.minorUnits }.sorted(),
                )

                // device 2 adds an expense; both sync; device 1 sees it.
                clock = 2000L
                repo2.upsertExpense(
                    joined,
                    Expense(ExpenseId("e2"), "Taxi", listOf(Payment(b, Money(1000, usd))), Split.Equal(listOf(a, b))),
                    spentAt = 2,
                )
                engine2.sync(joined)
                engine1.sync(g)

                val device1After = repo1.load(g)!!
                assertEquals(2, device1After.expenses.size)
                assertTrue(device1After.expenses.any { it.id == ExpenseId("e2") })
                assertEquals(
                    device1After.balances().net.mapKeys { it.key.value },
                    repo2.load(joined)!!.balances().net.mapKeys { it.key.value },
                )
            } finally {
                db1.close()
                db2.close()
            }
        }

    @Test
    fun deletions_propagate() =
        runTest {
            val relay = FakeRelay()
            var clock = 1000L
            val db1 = inMemoryDatabase()
            val repo1 = GroupRepository(db1, deviceId = "dev1", now = { clock })
            val engine1 = SyncEngine(db1, relay, GroupCrypto(), deviceId = "dev1")
            val db2 = inMemoryDatabase()
            val repo2 = GroupRepository(db2, deviceId = "dev2", now = { clock })
            val engine2 = SyncEngine(db2, relay, GroupCrypto(), deviceId = "dev2")

            try {
                val g = GroupId("g-local")
                repo1.saveGroup(Group(g, "Trip", usd, listOf(Member(a, "Alice"), Member(b, "Bob"))))
                repo1.upsertExpense(
                    g,
                    Expense(ExpenseId("e1"), "Dinner", listOf(Payment(a, Money(3000, usd))), Split.Equal(listOf(a, b))),
                    spentAt = 1,
                )
                val code = engine1.share(g)
                val joined = engine2.join(code)!!
                assertEquals(1, repo2.load(joined)!!.expenses.size)

                clock = 2000L
                repo1.deleteExpense(ExpenseId("e1"))
                engine1.sync(g)
                engine2.sync(joined)

                assertEquals(0, repo2.load(joined)!!.expenses.size)
            } finally {
                db1.close()
                db2.close()
            }
        }
}
