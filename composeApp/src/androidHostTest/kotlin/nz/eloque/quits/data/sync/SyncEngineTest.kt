package nz.eloque.quits.data.sync

import kotlinx.coroutines.test.runTest
import nz.eloque.quits.data.crypto.GroupCrypto
import nz.eloque.quits.data.db.inMemoryDatabase
import nz.eloque.quits.data.repository.GroupRepository
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.Entry
import nz.eloque.quits.domain.EntryId
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
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private class FakeRelay(
    private val limits: RelayLimits = RelayLimits(maxBodyBytes = Long.MAX_VALUE, maxRecordBytes = 0, maxRecordsPerGroup = 0),
    /** Batch index (0-based) to reject with [failWith], to exercise a part-way failure. */
    private val failOnPush: Int = -1,
    private val failWith: SyncError = SyncError.Unreachable(null),
) : Relay {
    private data class Stored(
        val record: EncryptedRecord,
        val seq: Long,
    )

    private val containers = mutableMapOf<String, MutableMap<String, Stored>>()
    private val lookups = mutableMapOf<String, String>()
    private var seq = 0L

    /** Records offered per push call, in order, so tests can assert how a batch was split. */
    val pushes = mutableListOf<List<EncryptedRecord>>()

    override suspend fun limits(): RelayLimits = limits

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
        if (pushes.size == failOnPush) {
            pushes += records
            throw failWith
        }
        pushes += records
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
                repo1.upsertEntry(
                    g,
                    Entry(EntryId("e1"), "Dinner", listOf(Payment(a, Money(3000, usd))), Split.Equal(listOf(a, b))),
                    spentAt = 1,
                )
                val code = engine1.share(g)

                // device 2 joins by code and pulls the whole group down.
                clock = 1500L
                val joined = engine2.join(code)!!
                val onDevice1 = repo1.load(g)!!
                val onDevice2 = repo2.load(joined)!!
                assertEquals(2, onDevice2.members.size)
                assertEquals(1, onDevice2.entries.size)
                assertEquals(
                    onDevice1.balances().net.values.map {
                        it.minorUnits
                    }.sorted(),
                    onDevice2.balances().net.values.map { it.minorUnits }.sorted(),
                )

                // device 2 adds an entry; both sync; device 1 sees it.
                clock = 2000L
                repo2.upsertEntry(
                    joined,
                    Entry(EntryId("e2"), "Taxi", listOf(Payment(b, Money(1000, usd))), Split.Equal(listOf(a, b))),
                    spentAt = 2,
                )
                engine2.sync(joined)
                engine1.sync(g)

                val device1After = repo1.load(g)!!
                assertEquals(2, device1After.entries.size)
                assertTrue(device1After.entries.any { it.id == EntryId("e2") })
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
    fun itemized_expense_syncs_with_items_intact() =
        runTest {
            val relay = FakeRelay()
            val clock = 1000L
            val db1 = inMemoryDatabase()
            val repo1 = GroupRepository(db1, deviceId = "dev1", now = { clock })
            val engine1 = SyncEngine(db1, relay, GroupCrypto(), deviceId = "dev1")
            val db2 = inMemoryDatabase()
            val repo2 = GroupRepository(db2, deviceId = "dev2", now = { clock })
            val engine2 = SyncEngine(db2, relay, GroupCrypto(), deviceId = "dev2")

            try {
                val g = GroupId("g-local")
                repo1.saveGroup(Group(g, "Trip", usd, listOf(Member(a, "Alice"), Member(b, "Bob"))))
                repo1.upsertEntry(
                    g,
                    Entry(
                        EntryId("e-items"),
                        "Groceries",
                        listOf(Payment(a, Money(5000, usd))),
                        Split.Itemized(
                            listOf(
                                Split.Itemized.Item("Pasta", Money(2000, usd), setOf(a)),
                                Split.Itemized.Item("Wine", Money(3000, usd), setOf(a, b)),
                            ),
                        ),
                    ),
                    spentAt = 1,
                )
                val code = engine1.share(g)

                val joined = engine2.join(code)!!
                val synced = repo2.load(joined)!!.entries.first { it.id == EntryId("e-items") }
                val split = synced.split
                assertTrue(split is Split.Itemized)
                assertEquals(2, split.items.size)
                assertEquals("Wine", split.items[1].label)
                assertEquals(setOf(a, b), split.items[1].participants)
                // The shares the second device computes from the synced items match the first device's.
                assertEquals(
                    repo1.load(g)!!.entries.first { it.id == EntryId("e-items") }.shares,
                    synced.shares,
                )
            } finally {
                db1.close()
                db2.close()
            }
        }

    @Test
    fun re_share_uploads_full_state_to_the_fresh_relay_group() =
        runTest {
            val relay = FakeRelay()
            val clock = 1000L
            val db1 = inMemoryDatabase()
            val repo1 = GroupRepository(db1, deviceId = "dev1", now = { clock })
            val engine1 = SyncEngine(db1, relay, GroupCrypto(), deviceId = "dev1")
            val db2 = inMemoryDatabase()
            val repo2 = GroupRepository(db2, deviceId = "dev2", now = { clock })
            val engine2 = SyncEngine(db2, relay, GroupCrypto(), deviceId = "dev2")

            try {
                val g = GroupId("g-local")
                repo1.saveGroup(Group(g, "Trip", usd, listOf(Member(a, "Alice"), Member(b, "Bob"))))
                repo1.upsertEntry(
                    g,
                    Entry(EntryId("e1"), "Dinner", listOf(Payment(a, Money(3000, usd))), Split.Equal(listOf(a, b))),
                    spentAt = 1,
                )
                // First share clears every dirty flag; re-sharing then registers a brand-new, empty
                // relay group whose upload has no delta to send — the full state must still go up.
                engine1.share(g)
                val code2 = engine1.share(g)

                val joined = engine2.join(code2)!!
                val onDevice2 = repo2.load(joined)!!
                assertEquals(2, onDevice2.members.size)
                assertEquals(1, onDevice2.entries.size)
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
                repo1.upsertEntry(
                    g,
                    Entry(EntryId("e1"), "Dinner", listOf(Payment(a, Money(3000, usd))), Split.Equal(listOf(a, b))),
                    spentAt = 1,
                )
                val code = engine1.share(g)
                val joined = engine2.join(code)!!
                assertEquals(1, repo2.load(joined)!!.entries.size)

                clock = 2000L
                repo1.deleteEntry(EntryId("e1"))
                engine1.sync(g)
                engine2.sync(joined)

                assertEquals(0, repo2.load(joined)!!.entries.size)
            } finally {
                db1.close()
                db2.close()
            }
        }

    @Test
    fun a_group_larger_than_the_body_limit_is_pushed_in_several_requests() =
        runTest {
            // Small enough that a handful of records cannot share one batch.
            val relay = FakeRelay(limits = RelayLimits(maxBodyBytes = 600, maxRecordBytes = 0, maxRecordsPerGroup = 0))
            val db = inMemoryDatabase()
            val repo = GroupRepository(db, deviceId = "dev1", now = { 1000L })
            val engine = SyncEngine(db, relay, GroupCrypto(), deviceId = "dev1")

            try {
                val g = GroupId("g-big")
                repo.saveGroup(Group(g, "Trip", usd, listOf(Member(a, "Alice"), Member(b, "Bob"))))
                repeat(8) { i ->
                    repo.upsertEntry(
                        g,
                        Entry(EntryId("e$i"), "Entry number $i", listOf(Payment(a, Money(1000, usd))), Split.Equal(listOf(a, b))),
                        spentAt = i.toLong(),
                    )
                }

                engine.share(g)

                assertTrue(relay.pushes.size > 1, "expected several batches, got ${relay.pushes.size}")
                assertTrue(relay.pushes.all { it.isNotEmpty() })
                // Nothing may be dropped or duplicated by the split.
                val pushedIds = relay.pushes.flatten().map { it.id }
                assertEquals(pushedIds.size, pushedIds.toSet().size, "a record was sent twice")
            } finally {
                db.close()
            }
        }

    @Test
    fun a_failed_batch_keeps_the_progress_of_earlier_ones() =
        runTest {
            val relay =
                FakeRelay(
                    limits = RelayLimits(maxBodyBytes = 600, maxRecordBytes = 0, maxRecordsPerGroup = 0),
                    failOnPush = 1,
                )
            val db = inMemoryDatabase()
            val repo = GroupRepository(db, deviceId = "dev1", now = { 1000L })
            val engine = SyncEngine(db, relay, GroupCrypto(), deviceId = "dev1")

            try {
                val g = GroupId("g-partial")
                repo.saveGroup(Group(g, "Trip", usd, listOf(Member(a, "Alice"), Member(b, "Bob"))))
                repeat(8) { i ->
                    repo.upsertEntry(
                        g,
                        Entry(EntryId("e$i"), "Entry number $i", listOf(Payment(a, Money(1000, usd))), Split.Equal(listOf(a, b))),
                        spentAt = i.toLong(),
                    )
                }

                assertFailsWith<SyncError.Unreachable> { engine.share(g) }

                // The first batch applied, so its records must not still be queued as dirty.
                val stillDirty = db.memberDao().dirty(g.value).map { it.id } + db.entryDao().dirty(g.value).map { it.entry.id }
                val firstBatch = relay.pushes.first().map { it.id }
                assertTrue(
                    stillDirty.none { it in firstBatch },
                    "records from the applied batch are still dirty: $stillDirty",
                )
            } finally {
                db.close()
            }
        }

    @Test
    fun a_record_over_the_relays_record_limit_is_rejected_without_a_request() =
        runTest {
            val relay = FakeRelay(limits = RelayLimits(maxBodyBytes = Long.MAX_VALUE, maxRecordBytes = 1, maxRecordsPerGroup = 0))
            val db = inMemoryDatabase()
            val repo = GroupRepository(db, deviceId = "dev1", now = { 1000L })
            val engine = SyncEngine(db, relay, GroupCrypto(), deviceId = "dev1")

            try {
                val g = GroupId("g-oversize")
                repo.saveGroup(Group(g, "Trip", usd, listOf(Member(a, "Alice"))))

                assertFailsWith<SyncError.RecordTooLarge> { engine.share(g) }
                assertTrue(relay.pushes.isEmpty(), "the relay should not have been asked")
            } finally {
                db.close()
            }
        }
}
