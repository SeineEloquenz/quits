package nz.eloque.quits.data.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import nz.eloque.quits.data.crypto.GroupCrypto
import nz.eloque.quits.data.db.inMemoryDatabase
import nz.eloque.quits.data.repository.GroupRepository
import nz.eloque.quits.domain.Category
import nz.eloque.quits.domain.CategoryId
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeRelay(
    private val limits: RelayLimits =
        RelayLimits(maxBodyBytes = Long.MAX_VALUE, maxRecordBytes = 0, maxRecordsPerGroup = 0, fromRelay = true),
    /** Batch index (0-based) to reject with [failWith], to exercise a part-way failure. */
    private val failOnPush: Int = -1,
    private val failWith: Throwable = SyncError.Unreachable(null),
    /** Batch index (0-based) to block on forever, after signalling [blocked]. */
    private val blockOnPush: Int = -1,
    private val blocked: CompletableDeferred<Unit> = CompletableDeferred(),
    /** Ciphertext a batch may total before the fake refuses it whole, standing in for a body limit. */
    private val refuseOverBytes: Int = Int.MAX_VALUE,
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

    /** Pull calls, so tests can assert one did or did not happen. */
    var pulls = 0
        private set

    /** Batches the fake stored, in order, excluding the ones it refused whole. */
    val accepted = mutableListOf<List<EncryptedRecord>>()

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
        if (pushes.size == blockOnPush) {
            pushes += records
            blocked.complete(Unit)
            awaitCancellation()
        }
        pushes += records
        if (records.sumOf { it.ciphertext.size } > refuseOverBytes) throw SyncError.BatchTooLarge

        accepted += records

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
        pulls += 1
        val store = containers[remoteId] ?: return PullResult(emptyList(), since)
        val fresh = store.values.filter { it.seq > since }.sortedBy { it.seq }
        val seq = fresh.maxOfOrNull { it.seq } ?: since
        return PullResult(fresh.map { it.record }, seq)
    }
}

/** Answers like [RelayClient] does: an unreachable relay yields a guess rather than throwing. */
private class FlakyLimitsRelay : Relay {
    @Volatile var reachable = false

    override suspend fun limits(): RelayLimits = if (reachable) RelayLimits.published(1048576, 8192, 5000) else RelayLimits.CONSERVATIVE

    override suspend fun createGroup(lookupId: String): GroupHandle = newId().let { GroupHandle(it, "token-$it") }

    override suspend fun joinGroup(lookupId: String): GroupHandle? = null

    override suspend fun push(
        remoteId: String,
        token: String,
        records: List<EncryptedRecord>,
    ): PushResult = PushResult(1, records.map { it.id }, emptyList())

    override suspend fun pull(
        remoteId: String,
        token: String,
        since: Long,
    ): PullResult = PullResult(emptyList(), since)
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
            // Small enough that the group cannot go out in one request.
            val relay = FakeRelay(limits = RelayLimits(maxBodyBytes = 2048, maxRecordBytes = 0, maxRecordsPerGroup = 0, fromRelay = true))
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
                    limits = RelayLimits(maxBodyBytes = 2048, maxRecordBytes = 0, maxRecordsPerGroup = 0, fromRelay = true),
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
            val relay =
                FakeRelay(limits = RelayLimits(maxBodyBytes = Long.MAX_VALUE, maxRecordBytes = 1, maxRecordsPerGroup = 0, fromRelay = true))
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

    @Test
    fun a_record_too_big_for_any_batch_is_rejected_against_a_published_budget() =
        runTest {
            val relay = FakeRelay(limits = RelayLimits(maxBodyBytes = 700, maxRecordBytes = 0, maxRecordsPerGroup = 0, fromRelay = true))
            val db = inMemoryDatabase()
            val repo = GroupRepository(db, deviceId = "dev1", now = { 1000L })
            val engine = SyncEngine(db, relay, GroupCrypto(), deviceId = "dev1")

            try {
                val g = GroupId("g-fat")
                repo.saveGroup(Group(g, "Trip", usd, listOf(Member(a, "Alice"))))
                repo.upsertEntry(
                    g,
                    Entry(EntryId("fat"), "x".repeat(600), listOf(Payment(a, Money(1000, usd))), Split.Equal(listOf(a))),
                    spentAt = 1,
                )

                assertFailsWith<SyncError.RecordTooLarge> { engine.share(g) }
            } finally {
                db.close()
            }
        }

    @Test
    fun a_guessed_budget_never_rejects_a_record_outright() =
        runTest {
            val relay = FakeRelay(limits = RelayLimits(maxBodyBytes = 700, maxRecordBytes = 0, maxRecordsPerGroup = 0, fromRelay = false))
            val db = inMemoryDatabase()
            val repo = GroupRepository(db, deviceId = "dev1", now = { 1000L })
            val engine = SyncEngine(db, relay, GroupCrypto(), deviceId = "dev1")

            try {
                val g = GroupId("g-guess")
                repo.saveGroup(Group(g, "Trip", usd, listOf(Member(a, "Alice"))))
                repo.upsertEntry(
                    g,
                    Entry(EntryId("fat"), "x".repeat(600), listOf(Payment(a, Money(1000, usd))), Split.Equal(listOf(a))),
                    spentAt = 1,
                )

                engine.share(g)

                assertTrue(relay.pushes.flatten().any { it.id == "fat" }, "the relay decides, not a guess")
            } finally {
                db.close()
            }
        }

    @Test
    fun cancelling_mid_push_still_clears_what_already_applied() =
        runTest {
            val reachedSecondBatch = CompletableDeferred<Unit>()
            val relay =
                FakeRelay(
                    limits = RelayLimits(maxBodyBytes = 2048, maxRecordBytes = 0, maxRecordsPerGroup = 0, fromRelay = true),
                    blockOnPush = 1,
                    blocked = reachedSecondBatch,
                )
            val db = inMemoryDatabase()
            val repo = GroupRepository(db, deviceId = "dev1", now = { 1000L })
            val engine = SyncEngine(db, relay, GroupCrypto(), deviceId = "dev1")

            try {
                val g = GroupId("g-cancel")
                repo.saveGroup(Group(g, "Trip", usd, listOf(Member(a, "Alice"), Member(b, "Bob"))))
                repeat(8) { i ->
                    repo.upsertEntry(
                        g,
                        Entry(EntryId("e$i"), "Entry number $i", listOf(Payment(a, Money(1000, usd))), Split.Equal(listOf(a, b))),
                        spentAt = i.toLong(),
                    )
                }

                // Really cancel the job. Throwing a CancellationException from the relay would
                // leave it live, and the clearing would run on a healthy coroutine either way.
                val job = launch { engine.share(g) }
                reachedSecondBatch.await()
                job.cancelAndJoin()

                val stillDirty = db.memberDao().dirty(g.value).map { it.id } + db.entryDao().dirty(g.value).map { it.entry.id }
                val firstBatch = relay.pushes.first().map { it.id }
                assertTrue(
                    stillDirty.none { it in firstBatch },
                    "cancellation stranded applied records as dirty: $stillDirty",
                )
            } finally {
                db.close()
            }
        }

    @Test
    fun a_refused_batch_is_re_chunked_and_still_lands() =
        runTest {
            // Advertises far more room than it will take, so batches come back refused until the
            // engine has halved its way down to something the relay actually accepts.
            val relay =
                FakeRelay(
                    limits = RelayLimits(maxBodyBytes = 65536, maxRecordBytes = 0, maxRecordsPerGroup = 0, fromRelay = true),
                    refuseOverBytes = 4700,
                )
            val db = inMemoryDatabase()
            val repo = GroupRepository(db, deviceId = "dev1", now = { 1000L })
            val engine = SyncEngine(db, relay, GroupCrypto(), deviceId = "dev1")

            try {
                val g = GroupId("g-rechunk")
                mixedSizeGroup(repo, g)

                engine.share(g)

                assertTrue(relay.accepted.size < relay.pushes.size, "expected a refusal to re-chunk after")
                assertTrue(relay.accepted.all { batch -> batch.sumOf { it.ciphertext.size } <= 4700 })

                // A round that stores some batches before one is refused resumes at the refused
                // batch, so nothing the relay already holds is offered again.
                val storedIds = relay.accepted.flatten().map { it.id }
                assertEquals(storedIds.size, storedIds.toSet().size, "a stored record was offered a second time")

                val stillDirty = db.memberDao().dirty(g.value).map { it.id } + db.entryDao().dirty(g.value).map { it.entry.id }
                assertTrue(stillDirty.isEmpty(), "re-chunking left records behind: $stillDirty")
            } finally {
                db.close()
            }
        }

    @Test
    fun re_chunking_keeps_what_earlier_rounds_stored() =
        runTest {
            // Low enough that the long entries never fit, however far the budget comes down.
            val relay =
                FakeRelay(
                    limits = RelayLimits(maxBodyBytes = 65536, maxRecordBytes = 0, maxRecordsPerGroup = 0, fromRelay = true),
                    refuseOverBytes = 2200,
                )
            val db = inMemoryDatabase()
            val repo = GroupRepository(db, deviceId = "dev1", now = { 1000L })
            val engine = SyncEngine(db, relay, GroupCrypto(), deviceId = "dev1")

            try {
                val g = GroupId("g-partial-rechunk")
                mixedSizeGroup(repo, g)

                assertFailsWith<SyncError.BatchTooLarge> { engine.share(g) }

                val storedIds = relay.accepted.flatten().map { it.id }
                assertEquals(storedIds.size, storedIds.toSet().size, "a stored record was offered a second time")
                assertTrue(storedIds.any { it.startsWith("s") }, "the short entries should have landed")

                val stillDirty = db.entryDao().dirty(g.value).map { it.entry.id }
                assertEquals(listOf("L0", "L1", "L2", "L3"), stillDirty.sorted(), "only the unfittable entries should remain")
            } finally {
                db.close()
            }
        }

    @Test
    fun re_chunking_stops_at_the_floor() =
        runTest {
            val relay =
                FakeRelay(
                    limits = RelayLimits(maxBodyBytes = 65536, maxRecordBytes = 0, maxRecordsPerGroup = 0, fromRelay = true),
                    refuseOverBytes = 0,
                )
            val db = inMemoryDatabase()
            val repo = GroupRepository(db, deviceId = "dev1", now = { 1000L })
            val engine = SyncEngine(db, relay, GroupCrypto(), deviceId = "dev1")

            try {
                val g = GroupId("g-stuck")
                mixedSizeGroup(repo, g)

                assertFailsWith<SyncError.BatchTooLarge> { engine.share(g) }

                assertTrue(relay.pushes.size < 40, "halving should reach a floor, not go on forever")
                assertTrue(relay.accepted.isEmpty())
            } finally {
                db.close()
            }
        }

    @Test
    fun a_record_that_can_never_be_pushed_does_not_stop_the_pull() =
        runTest {
            // Big enough for everything the test shares, too small for the entry added at the end.
            val relay =
                FakeRelay(limits = RelayLimits(maxBodyBytes = 65536, maxRecordBytes = 3000, maxRecordsPerGroup = 0, fromRelay = true))
            val db1 = inMemoryDatabase()
            val repo1 = GroupRepository(db1, deviceId = "dev1", now = { 1000L })
            val engine1 = SyncEngine(db1, relay, GroupCrypto(), deviceId = "dev1")

            val db2 = inMemoryDatabase()
            val repo2 = GroupRepository(db2, deviceId = "dev2", now = { 1500L })
            val engine2 = SyncEngine(db2, relay, GroupCrypto(), deviceId = "dev2")

            try {
                val g = GroupId("g-stuck-record")
                repo1.saveGroup(Group(g, "Trip", usd, listOf(Member(a, "Alice"), Member(b, "Bob"))))
                val code = engine1.share(g)

                val joined = engine2.join(code)!!
                repo2.upsertEntry(
                    joined,
                    Entry(EntryId("from-peer"), "Taxi", listOf(Payment(b, Money(1000, usd))), Split.Equal(listOf(a, b))),
                    spentAt = 1,
                )
                engine2.sync(joined)

                repo1.upsertEntry(
                    g,
                    Entry(EntryId("huge"), "H".repeat(4000), listOf(Payment(a, Money(1000, usd))), Split.Equal(listOf(a, b))),
                    spentAt = 2,
                )
                val error = assertFailsWith<SyncError.RecordTooLarge> { engine1.sync(g) }
                assertEquals(listOf("huge"), error.recordIds)

                val onDevice1 = repo1.load(g)!!
                assertTrue(onDevice1.entries.any { it.id == EntryId("from-peer") }, "the pull was skipped")
            } finally {
                db1.close()
                db2.close()
            }
        }

    @Test
    fun a_record_that_can_never_be_pushed_does_not_hold_back_the_others() =
        runTest {
            val relay =
                FakeRelay(limits = RelayLimits(maxBodyBytes = 65536, maxRecordBytes = 3000, maxRecordsPerGroup = 0, fromRelay = true))
            val db1 = inMemoryDatabase()
            val repo1 = GroupRepository(db1, deviceId = "dev1", now = { 1000L })
            val engine1 = SyncEngine(db1, relay, GroupCrypto(), deviceId = "dev1")

            val db2 = inMemoryDatabase()
            val repo2 = GroupRepository(db2, deviceId = "dev2", now = { 1500L })
            val engine2 = SyncEngine(db2, relay, GroupCrypto(), deviceId = "dev2")

            try {
                val g = GroupId("g-one-bad-record")
                repo1.saveGroup(Group(g, "Trip", usd, listOf(Member(a, "Alice"), Member(b, "Bob"))))
                val code = engine1.share(g)
                val joined = engine2.join(code)!!

                repo1.upsertEntry(
                    g,
                    Entry(EntryId("huge"), "H".repeat(4000), listOf(Payment(a, Money(1000, usd))), Split.Equal(listOf(a, b))),
                    spentAt = 1,
                )
                repo1.upsertEntry(
                    g,
                    Entry(EntryId("fine"), "Dinner", listOf(Payment(a, Money(2000, usd))), Split.Equal(listOf(a, b))),
                    spentAt = 2,
                )

                val error = assertFailsWith<SyncError.RecordTooLarge> { engine1.sync(g) }
                assertEquals(listOf("huge"), error.recordIds)

                assertTrue(
                    db1.entryDao().dirty(g.value).none { it.entry.id == "fine" },
                    "the storable entry was held back by the oversized one",
                )
                engine2.sync(joined)
                assertTrue(
                    repo2.load(joined)!!.entries.any { it.id == EntryId("fine") },
                    "peers never received the storable entry",
                )
            } finally {
                db1.close()
                db2.close()
            }
        }

    @Test
    fun an_unstorable_category_is_set_aside_like_any_other_record() =
        runTest {
            val relay =
                FakeRelay(limits = RelayLimits(maxBodyBytes = 65536, maxRecordBytes = 3000, maxRecordsPerGroup = 0, fromRelay = true))
            val db = inMemoryDatabase()
            val repo = GroupRepository(db, deviceId = "dev1", now = { 1000L })
            val engine = SyncEngine(db, relay, GroupCrypto(), deviceId = "dev1")

            try {
                val g = GroupId("g-huge-category")
                repo.saveGroup(Group(g, "Trip", usd, listOf(Member(a, "Alice"), Member(b, "Bob"))))
                val huge = CategoryId("cat-huge")
                repo.upsertCategory(g, Category(huge, "C".repeat(4000), "food", 0xFF0000L))
                repo.upsertEntry(
                    g,
                    Entry(EntryId("e1"), "Dinner", listOf(Payment(a, Money(1000, usd))), Split.Equal(listOf(a, b))),
                    spentAt = 1,
                )

                val error = assertFailsWith<SyncError.RecordTooLarge> { engine.share(g) }
                assertEquals(listOf(huge.value), error.recordIds)
                // An entry naming a missing category reads as uncategorized, so the rest still goes.
                assertTrue(
                    db.entryDao().dirty(g.value).none { it.entry.id == "e1" },
                    "the entry was held back by an unstorable category",
                )
            } finally {
                db.close()
            }
        }

    @Test
    fun an_unstorable_member_fails_the_push_instead_of_being_dropped() =
        runTest {
            val relay =
                FakeRelay(limits = RelayLimits(maxBodyBytes = 65536, maxRecordBytes = 3000, maxRecordsPerGroup = 0, fromRelay = true))
            val db = inMemoryDatabase()
            val repo = GroupRepository(db, deviceId = "dev1", now = { 1000L })
            val engine = SyncEngine(db, relay, GroupCrypto(), deviceId = "dev1")

            try {
                val g = GroupId("g-huge-member")
                repo.saveGroup(Group(g, "Trip", usd, listOf(Member(a, "A".repeat(4000)), Member(b, "Bob"))))
                repo.upsertEntry(
                    g,
                    Entry(EntryId("e1"), "Dinner", listOf(Payment(a, Money(1000, usd))), Split.Equal(listOf(a, b))),
                    spentAt = 1,
                )

                val error = assertFailsWith<SyncError.RecordTooLarge> { engine.share(g) }
                assertEquals(listOf(a.value), error.recordIds)
                // Dropping the member and keeping the entry would give peers an aggregate whose
                // Group init rejects it, so nothing goes out at all.
                assertTrue(relay.accepted.isEmpty(), "records were pushed without the member they name")
            } finally {
                db.close()
            }
        }

    @Test
    fun a_full_group_still_receives_what_peers_add() =
        runTest {
            // Device 1 shares (push 0), device 2 adds an entry (push 1), then the relay is full.
            val relay = FakeRelay(failOnPush = 2, failWith = SyncError.GroupFull)
            var clock = 1000L

            val db1 = inMemoryDatabase()
            val repo1 = GroupRepository(db1, deviceId = "dev1", now = { clock })
            val engine1 = SyncEngine(db1, relay, GroupCrypto(), deviceId = "dev1", now = { clock })

            val db2 = inMemoryDatabase()
            val repo2 = GroupRepository(db2, deviceId = "dev2", now = { clock })
            val engine2 = SyncEngine(db2, relay, GroupCrypto(), deviceId = "dev2", now = { clock })

            try {
                val g = GroupId("g-full")
                repo1.saveGroup(Group(g, "Trip", usd, listOf(Member(a, "Alice"), Member(b, "Bob"))))
                val code = engine1.share(g)

                clock = 2000L
                val joined = engine2.join(code)!!
                repo2.upsertEntry(
                    joined,
                    Entry(EntryId("from-peer"), "Taxi", listOf(Payment(b, Money(1000, usd))), Split.Equal(listOf(a, b))),
                    spentAt = 1,
                )
                engine2.sync(joined)

                val syncedBefore = db1.groupSyncDao().byGroup(g.value)!!.lastSyncedAt
                clock = 3000L
                repo1.upsertEntry(
                    g,
                    Entry(EntryId("mine"), "Dinner", listOf(Payment(a, Money(2000, usd))), Split.Equal(listOf(a, b))),
                    spentAt = 2,
                )
                assertFailsWith<SyncError.GroupFull> { engine1.sync(g) }

                val onDevice1 = repo1.load(g)!!
                assertTrue(onDevice1.entries.any { it.id == EntryId("from-peer") }, "a full group stopped receiving")
                assertTrue(
                    db1.entryDao().dirty(g.value).any { it.entry.id == "mine" },
                    "the refused entry must stay pending",
                )
                assertTrue(
                    db1.groupSyncDao().byGroup(g.value)!!.lastSyncedAt > syncedBefore,
                    "the pull succeeded, so the group is not still showing its last sync from before it filled",
                )
            } finally {
                db1.close()
                db2.close()
            }
        }

    @Test
    fun usage_tracks_entries_as_they_are_added() =
        runTest {
            val relay =
                FakeRelay(
                    limits = RelayLimits(maxBodyBytes = Long.MAX_VALUE, maxRecordBytes = 0, maxRecordsPerGroup = 100, fromRelay = true),
                )
            val db = inMemoryDatabase()
            val repo = GroupRepository(db, deviceId = "dev1", now = { 1000L })
            val engine = SyncEngine(db, relay, GroupCrypto(), deviceId = "dev1")

            try {
                val g = GroupId("g-usage")
                repo.saveGroup(Group(g, "Trip", usd, listOf(Member(a, "Alice"), Member(b, "Bob"))))
                engine.share(g)

                val before = engine.usageFlow(g).first()!!
                repo.upsertEntry(
                    g,
                    Entry(EntryId("e1"), "Dinner", listOf(Payment(a, Money(1000, usd))), Split.Equal(listOf(a, b))),
                    spentAt = 1,
                )
                val after = engine.usageFlow(g).first()!!

                // The warning exists to fire as entries are added, which is the one action that
                // used to leave it frozen.
                assertEquals(before.stored + 1, after.stored)
                assertEquals(100, after.limit)
            } finally {
                db.close()
            }
        }

    @Test
    fun a_relay_in_trouble_is_not_pulled_from_after_a_failed_push() =
        runTest {
            // Push 0 is the share, so the one after it is the push under test.
            val relay = FakeRelay(failOnPush = 1, failWith = SyncError.Unreachable(null))
            val db = inMemoryDatabase()
            val repo = GroupRepository(db, deviceId = "dev1", now = { 1000L })
            val engine = SyncEngine(db, relay, GroupCrypto(), deviceId = "dev1")

            try {
                val g = GroupId("g-offline")
                repo.saveGroup(Group(g, "Trip", usd, listOf(Member(a, "Alice"), Member(b, "Bob"))))
                engine.share(g)
                val pullsAfterShare = relay.pulls

                repo.upsertEntry(
                    g,
                    Entry(EntryId("e1"), "Dinner", listOf(Payment(a, Money(1000, usd))), Split.Equal(listOf(a, b))),
                    spentAt = 1,
                )
                assertFailsWith<SyncError.Unreachable> { engine.sync(g) }

                assertEquals(pullsAfterShare, relay.pulls, "a retriable failure must abort before the pull")
            } finally {
                db.close()
            }
        }

    @Test
    fun the_quota_warning_appears_once_the_relay_can_be_asked() =
        runBlocking {
            val relay = FlakyLimitsRelay()
            val db = inMemoryDatabase()
            val repo = GroupRepository(db, deviceId = "dev1", now = { 1000L })
            val engine = SyncEngine(db, relay, GroupCrypto(), deviceId = "dev1")
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

            try {
                val g = GroupId("g-late-limit")
                repo.saveGroup(Group(g, "Trip", usd, listOf(Member(a, "Alice"), Member(b, "Bob"))))
                engine.share(g)

                val seen = mutableListOf<GroupUsage?>()
                val job = scope.launch { engine.usageFlow(g).collect { seen += it } }
                awaitUntil("the flow never emitted") { seen.isNotEmpty() }
                assertNull(seen.last(), "an unreachable relay has no cap to report")

                // A guess must not be memoized as the answer, or the warning stays hidden for the
                // rest of the visit however long the relay has been back.
                relay.reachable = true
                repo.upsertEntry(
                    g,
                    Entry(EntryId("e1"), "Dinner", listOf(Payment(a, Money(1000, usd))), Split.Equal(listOf(a, b))),
                    spentAt = 1,
                )
                awaitUntil("the warning never recovered") { seen.last() != null }
                assertEquals(5000, seen.last()!!.limit)
                job.cancel()
            } finally {
                db.close()
            }
        }

    private suspend fun awaitUntil(
        message: String,
        condition: () -> Boolean,
    ) {
        repeat(100) {
            if (condition()) return
            delay(50)
        }
        throw AssertionError(message)
    }

    /** A group whose records vary enough in size that a round of chunks is not uniform. */
    private suspend fun mixedSizeGroup(
        repo: GroupRepository,
        g: GroupId,
    ) {
        repo.saveGroup(Group(g, "Trip", usd, listOf(Member(a, "Alice"), Member(b, "Bob"))))
        repeat(4) { i ->
            repo.upsertEntry(
                g,
                Entry(EntryId("s$i"), "s", listOf(Payment(a, Money(1000, usd))), Split.Equal(listOf(a, b))),
                spentAt = i.toLong(),
            )
        }
        repeat(4) { i ->
            repo.upsertEntry(
                g,
                Entry(EntryId("L$i"), "L".repeat(2000), listOf(Payment(a, Money(1000, usd))), Split.Equal(listOf(a, b))),
                spentAt = (100 + i).toLong(),
            )
        }
    }
}
