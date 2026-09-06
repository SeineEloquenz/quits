package nz.eloque.quits.data.sync

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import nz.eloque.quits.data.crypto.GroupCrypto
import nz.eloque.quits.data.crypto.GroupKey
import nz.eloque.quits.data.crypto.SecretCode
import nz.eloque.quits.data.db.CategoryEntity
import nz.eloque.quits.data.db.EntryWithLines
import nz.eloque.quits.data.db.GroupEntity
import nz.eloque.quits.data.db.GroupSyncEntity
import nz.eloque.quits.data.db.MemberEntity
import nz.eloque.quits.data.db.QuitsDatabase
import nz.eloque.quits.data.db.SettlementEntity
import nz.eloque.quits.data.db.SyncMeta
import nz.eloque.quits.domain.GroupId
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Drives delta sync against the [relay]: push dirty rows, pull since the cursor, apply with
 * last-write-wins.
 *
 * Payloads are end-to-end encrypted: a group's secret share code derives both the relay lookup id
 * and the AES key (see [crypto]), so the relay only ever sees opaque ciphertext.
 */
class SyncEngine(
    private val db: QuitsDatabase,
    private val relay: Relay,
    private val crypto: GroupCrypto,
    private val deviceId: String,
    private val now: () -> Long = { 0L },
) {
    private val keys = mutableMapOf<String, GroupKey>()

    /** Registers a local group with the relay and pushes its current state. Returns the share code. */
    suspend fun share(localGroupId: GroupId): String {
        val secret = SecretCode.generate()
        val handle = relay.createGroup(lookupId(secret))
        db.groupSyncDao().put(GroupSyncEntity(localGroupId.value, handle.remoteId, secret, handle.token))
        sync(localGroupId, full = true)
        return secret
    }

    /** Joins an existing group by its secret share [code], then pulls it down. Returns the new local group id, or null. */
    suspend fun join(code: String): GroupId? {
        val secret = SecretCode.decode(code)?.let { SecretCode.encode(it) } ?: return null
        val handle = relay.joinGroup(lookupId(secret)) ?: return null
        // A joiner has no prior local group, so it adopts the relay's id as its local id.
        db.groupSyncDao().put(GroupSyncEntity(handle.remoteId, handle.remoteId, secret, handle.token))
        val id = GroupId(handle.remoteId)
        sync(id)
        return id
    }

    /** The local group already joined/shared under this share [code], or null if the user isn't a member yet. */
    suspend fun localGroupFor(code: String): GroupId? {
        val canonical = SecretCode.decode(code)?.let { SecretCode.encode(it) } ?: return null
        return db
            .groupSyncDao()
            .all()
            .firstOrNull { SecretCode.decode(it.code)?.let(SecretCode::encode) == canonical }
            ?.let { GroupId(it.groupId) }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun lookupId(secret: String): String = Base64.encode(crypto.lookupId(SecretCode.decode(secret)!!))

    /** Whether [localGroupId] has a relay handle (i.e. is shared/joined). */
    suspend fun isSynced(localGroupId: GroupId): Boolean = db.groupSyncDao().byGroup(localGroupId.value) != null

    /**
     * How close [localGroupId] is to the relay's per-group record limit, as the group changes.
     *
     * Emits null for a local-only group, a relay that does not cap records, or one that could not
     * be reached to ask.
     */
    fun usageFlow(localGroupId: GroupId): Flow<GroupUsage?> =
        flow {
            // Read once, then only while still unknown. The limit is constant per relay, so asking
            // per emission is waste, but completing on the first failure would hide the banner for
            // the rest of the visit even after the relay comes back.
            var limit: Long? = null
            emitAll(
                combine(
                    db.groupDao().countSyncedRecordsFlow(localGroupId.value),
                    db.groupSyncDao().byGroupFlow(localGroupId.value),
                ) { stored, handle ->
                    if (handle == null) {
                        null
                    } else {
                        val known = limit ?: recordLimit()?.also { limit = it }
                        known?.takeIf { it > 0 }?.let { GroupUsage(stored = stored, limit = it) }
                    }
                },
            )
        }

    /** The relay's per-group cap, 0 when it does not cap, or null when it could not be asked. */
    private suspend fun recordLimit(): Long? =
        try {
            // Only a published figure answers this. A fallback reports no cap, which the caller
            // would memoize as the answer and never ask again once the relay came back.
            relay.limits().takeIf { it.fromRelay }?.maxRecordsPerGroup
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // An indicator, never a reason to fail anything. The banner simply stays hidden.
            Logger.w(e) { "could not read the relay's record limit" }
            null
        }

    /** Reactive sync info (share code + last-synced time) for [localGroupId]; null fields until shared. */
    fun syncInfoFlow(localGroupId: GroupId): Flow<SyncInfo> =
        db.groupSyncDao().byGroupFlow(localGroupId.value).map {
            SyncInfo(code = it?.code, lastSyncedAt = it?.lastSyncedAt?.takeIf { ts -> ts > 0 })
        }

    /** Syncs every shared group. */
    suspend fun syncAll(): SyncRunResult {
        var sawRetriable = false
        var sawPermanent = false
        db.groupSyncDao().all().forEach { handle ->
            try {
                sync(GroupId(handle.groupId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: SyncError) {
                if (e.retriable) sawRetriable = true else sawPermanent = true
                Logger.w(e) { "sync of ${handle.groupId} failed: ${e.message}" }
            } catch (e: Exception) {
                sawRetriable = true
                Logger.w(e) { "sync of ${handle.groupId} failed unexpectedly" }
            }
        }
        return when {
            sawRetriable -> SyncRunResult.Retriable
            sawPermanent -> SyncRunResult.Permanent
            else -> SyncRunResult.Success
        }
    }

    /**
     * Pushes local changes then pulls remote ones. No-op (false) for a local-only group.
     *
     * A push this device cannot complete must not also stop it receiving, so a permanent push
     * failure is held back until the pull has run.
     */
    suspend fun sync(
        localGroupId: GroupId,
        full: Boolean = false,
    ): Boolean {
        val handle = db.groupSyncDao().byGroup(localGroupId.value) ?: return false
        val stuck =
            try {
                push(localGroupId.value, handle, full)
                null
            } catch (e: SyncError) {
                // Retriable means the relay is struggling and a pull would only add to it. Permanent
                // ones are about what was sent, so the group stays readable.
                if (e.retriable) throw e else e
            }
        pull(localGroupId.value)
        // Marks reception, not a complete exchange. A group at its record limit keeps pulling, and
        // reporting it as never synced would contradict the entries arriving under it.
        db.groupSyncDao().setLastSyncedAt(localGroupId.value, now())
        stuck?.let { throw it }
        return true
    }

    /**
     * Pushes local rows to the relay. By default only the dirty delta; when [full] every current
     * row (used on first upload to a freshly-created relay group, which holds nothing yet).
     */
    private suspend fun push(
        gid: String,
        handle: GroupSyncEntity,
        full: Boolean = false,
    ) {
        val group = db.groupDao().byId(gid)
        val members = if (full) db.memberDao().forGroupWithDeleted(gid) else db.memberDao().dirty(gid)
        val entries = if (full) db.entryDao().forGroup(gid) else db.entryDao().dirty(gid)
        val settlements = if (full) db.settlementDao().forGroup(gid) else db.settlementDao().dirty(gid)
        val categories = if (full) db.categoryDao().forGroup(gid) else db.categoryDao().dirty(gid)

        // Order is load-bearing. server_seq follows arrival order and a peer pulling between chunks
        // applies a prefix of this list. The group record must lead, since every other entity's
        // groupId foreign key needs that row. Members must precede the entries and settlements
        // naming them, or the peer's Group init rejects the aggregate and the group stops loading.
        // Categories lead entries only to spare a peer a moment of showing one uncategorized.
        val records = mutableListOf<SyncRecord>()
        if (group != null && (full || group.sync.dirty)) records += RecordMapper.record(group)
        records += members.map { RecordMapper.record(it) }
        // Only these. Leaving one out while keeping what names it costs the peer the whole group,
        // which is not true of a category, where the entry simply reads as uncategorized.
        val referents = records.mapTo(mutableSetOf()) { it.id }
        records += categories.map { RecordMapper.record(it) }
        records += entries.map { RecordMapper.record(it) }
        records += settlements.map { RecordMapper.record(it) }
        if (records.isEmpty()) return

        val key = keyFor(handle)
        val sealed = records.map { seal(key, it) }

        val applied = mutableSetOf<String>()
        val rejected = mutableListOf<String>()
        try {
            pushChunks(handle, sealed, relay.limits(), referents, applied, rejected)
        } finally {
            // NonCancellable or these DAO calls throw at their first suspension point when the sync
            // is cancelled, leaving records the relay already accepted marked dirty.
            withContext(NonCancellable) {
                clearDirty(gid, applied, group, members, entries, settlements, categories)
            }
        }
        if (rejected.isNotEmpty()) throw SyncError.RecordTooLarge(rejected)
    }

    /**
     * Offers [sealed] to the relay in body-sized chunks, collecting the accepted ids into [applied]
     * and the ids of records that can never be stored into [rejected].
     *
     * Halves the budget and re-chunks the remainder when a whole batch is refused. Callers do not
     * retry, so a batch that does not fit has to be resized within this call or not at all.
     */
    private suspend fun pushChunks(
        handle: GroupSyncEntity,
        sealed: List<EncryptedRecord>,
        limits: RelayLimits,
        referents: Set<String>,
        applied: MutableSet<String>,
        rejected: MutableList<String>,
    ) {
        var budget = limits.maxBodyBytes
        var remaining = sealed.withoutUnstorable(limits, budget, referents, rejected)
        while (true) {
            var sent = 0
            try {
                for (chunk in remaining.chunkedToFit(budget)) {
                    applied += relay.push(handle.remoteId, handle.token, chunk).applied
                    sent += chunk.size
                }
                return
            } catch (e: SyncError.BatchTooLarge) {
                val halved = (budget / 2).coerceAtLeast(MIN_BODY_BUDGET)
                // Progress needs a strictly smaller budget, or the next round rebuilds the chunk
                // the relay just refused and this loop never ends.
                if (halved >= budget) throw e
                // Whole batches are refused, so the count of records sent resumes at the failed one
                // and the order the relay assigns server_seq in still holds.
                remaining = remaining.drop(sent).withoutUnstorable(limits, halved, referents, rejected)
                budget = halved
            }
        }
    }

    /**
     * Clears the dirty flag keyed on the pushed (updatedAt, deviceId). If the row was edited again
     * during the round-trip its clock moved, the guarded update no-ops, and the edit stays pending.
     */
    private suspend fun clearDirty(
        gid: String,
        applied: Set<String>,
        group: GroupEntity?,
        members: List<MemberEntity>,
        entries: List<EntryWithLines>,
        settlements: List<SettlementEntity>,
        categories: List<CategoryEntity>,
    ) {
        if (group != null && RecordMapper.GROUP_RECORD_ID in applied) {
            db.groupDao().clearDirty(gid, group.sync.updatedAt, group.sync.deviceId)
        }
        members.filter { it.id in applied }.forEach { db.memberDao().clearDirty(it.id, it.sync.updatedAt, it.sync.deviceId) }
        entries.filter { it.entry.id in applied }.forEach {
            db.entryDao().clearDirty(it.entry.id, it.entry.sync.updatedAt, it.entry.sync.deviceId)
        }
        settlements.filter { it.id in applied }.forEach { db.settlementDao().clearDirty(it.id, it.sync.updatedAt, it.sync.deviceId) }
        categories.filter { it.id in applied }.forEach { db.categoryDao().clearDirty(it.id, it.sync.updatedAt, it.sync.deviceId) }
    }

    private suspend fun pull(gid: String) {
        val handle = db.groupSyncDao().byGroup(gid) ?: return
        val key = keyFor(handle)
        val result = relay.pull(handle.remoteId, handle.token, handle.lastSeq)
        for (record in result.records) {
            val opened =
                try {
                    open(key, record)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null // undecryptable (corrupt/tampered) record: skip rather than abort the pull.
                }
            if (opened != null) apply(gid, opened)
        }
        if (result.seq > handle.lastSeq) db.groupSyncDao().setLastSeq(gid, result.seq)
    }

    private suspend fun keyFor(handle: GroupSyncEntity): GroupKey =
        keys.getOrPut(handle.code) {
            val secret = SecretCode.decode(handle.code) ?: error("invalid group secret for ${handle.groupId}")
            crypto.groupKey(secret)
        }

    private suspend fun seal(
        key: GroupKey,
        record: SyncRecord,
    ): EncryptedRecord =
        EncryptedRecord(
            id = record.id,
            updatedAt = record.updatedAt,
            deviceId = record.deviceId,
            deleted = record.deleted,
            ciphertext =
                key.encrypt(
                    SyncJson.encode(record.payload).encodeToByteArray(),
                    aad(record.id, record.updatedAt, record.deviceId, record.deleted),
                ),
        )

    private suspend fun open(
        key: GroupKey,
        record: EncryptedRecord,
    ): SyncRecord =
        SyncRecord(
            id = record.id,
            updatedAt = record.updatedAt,
            deviceId = record.deviceId,
            deleted = record.deleted,
            payload =
                SyncJson.decode(
                    key.decrypt(record.ciphertext, aad(record.id, record.updatedAt, record.deviceId, record.deleted)).decodeToString(),
                ),
        )

    private fun aad(
        id: String,
        updatedAt: Long,
        deviceId: String,
        deleted: Boolean,
    ): ByteArray = "$id\u0000$updatedAt\u0000$deviceId\u0000$deleted".encodeToByteArray()

    private suspend fun apply(
        gid: String,
        record: SyncRecord,
    ) {
        val meta = RecordMapper.meta(record, dirty = false)
        when (val payload = record.payload) {
            is SyncPayload.Group -> {
                if (wins(db.groupDao().byId(gid)?.sync, record)) {
                    db.groupDao().upsert(RecordMapper.groupEntity(payload, gid, meta))
                }
            }

            is SyncPayload.Member -> {
                if (wins(db.memberDao().byId(payload.id)?.sync, record)) {
                    db.memberDao().upsert(listOf(RecordMapper.memberEntity(payload, gid, meta)))
                }
            }

            is SyncPayload.Entry -> {
                if (wins(db.entryDao().byId(payload.id)?.entry?.sync, record)) {
                    val entities = RecordMapper.entryEntities(payload, gid, meta)
                    db.entryDao().save(
                        entities.entry,
                        entities.payers,
                        entities.splits,
                        entities.items.map { it.item },
                        entities.items.flatMap { it.participants },
                    )
                }
            }

            is SyncPayload.Settlement -> {
                if (wins(db.settlementDao().byId(payload.id)?.sync, record)) {
                    db.settlementDao().upsert(RecordMapper.settlementEntity(payload, gid, meta))
                }
            }

            is SyncPayload.Category -> {
                if (wins(db.categoryDao().byId(payload.id)?.sync, record)) {
                    db.categoryDao().upsert(RecordMapper.categoryEntity(payload, gid, meta))
                }
            }
        }
    }

    /** Same rule the relay uses: newer wins; ties broken by the strictly larger device id. */
    private fun wins(
        local: SyncMeta?,
        record: SyncRecord,
    ): Boolean =
        local == null ||
            record.updatedAt > local.updatedAt ||
            (record.updatedAt == local.updatedAt && record.deviceId > local.deviceId)
}

/** `{"records":[…]}` and the separators between entries. */
private const val ENVELOPE_BYTES = 256L

/** Floor for the push budget, so a relay that keeps refusing cannot shrink batches to nothing. */
private const val MIN_BODY_BUDGET = 4L * 1024

/**
 * Room for records in one request, once the `{"records":[…]}` envelope is accounted for.
 *
 * Chunking and the unfittable-record check must both size against this one function.
 */
private fun bodyBudget(maxBodyBytes: Long): Long = (maxBodyBytes - ENVELOPE_BYTES).coerceAtLeast(1)

/** JSON keys, quoting, the updatedAt digits and the deleted flag, for one record. */
private const val RECORD_OVERHEAD_BYTES = 128L

/**
 * Drops records the relay can never store, naming them in [rejected] so the rest of the push still
 * goes out. Unstorable means past the per-record limit, or too large to occupy a request of
 * [maxBodyBytes] alone.
 *
 * A record in [referents] is never dropped. Peers reject an aggregate whose entries name a member
 * they do not have, so an unstorable one fails the whole push rather than leaving that behind.
 */
private fun List<EncryptedRecord>.withoutUnstorable(
    limits: RelayLimits,
    maxBodyBytes: Long,
    referents: Set<String>,
    rejected: MutableList<String>,
): List<EncryptedRecord> {
    // The body clause needs a budget the relay stands behind, either published or arrived at by it
    // refusing. Against an untested guess it would refuse a record the relay might well accept.
    val budget = if (limits.fromRelay || maxBodyBytes < limits.maxBodyBytes) bodyBudget(maxBodyBytes) else Long.MAX_VALUE
    val (unstorable, sendable) =
        partition { (limits.maxRecordBytes > 0 && it.ciphertext.size > limits.maxRecordBytes) || it.wireSize() > budget }
    rejected += unstorable.map { it.id }
    // Reports everything set aside so far, not just the referent, since the earlier rounds' ids
    // would otherwise go unmentioned until this one is fixed.
    if (unstorable.any { it.id in referents }) throw SyncError.RecordTooLarge(rejected.toList())
    return sendable
}

/** Splits into batches whose encoded size stays under [maxBodyBytes]. */
private fun List<EncryptedRecord>.chunkedToFit(maxBodyBytes: Long): List<List<EncryptedRecord>> {
    val budget = bodyBudget(maxBodyBytes)
    val chunks = mutableListOf<List<EncryptedRecord>>()
    var current = mutableListOf<EncryptedRecord>()
    var size = 0L

    for (record in this) {
        val wire = record.wireSize()
        if (current.isNotEmpty() && size + wire > budget) {
            chunks += current
            current = mutableListOf()
            size = 0
        }
        current += record
        size += wire
    }
    if (current.isNotEmpty()) chunks += current
    return chunks
}

/** Over-approximates a record's JSON size, with the payload base64-encoded. */
private fun EncryptedRecord.wireSize(): Long = 4L * ((ciphertext.size + 2) / 3) + id.length + deviceId.length + RECORD_OVERHEAD_BYTES

/**
 * The aggregate outcome of syncing every shared group
 */
enum class SyncRunResult { Success, Retriable, Permanent }

/**
 * Best-effort [sync][SyncEngine.sync] of [groupId]: the change that triggered it is already saved
 * locally, so a network failure is fine to swallow — it will sync on the next open/refresh.
 * Cancellation still propagates. For flows that must report success/failure to the UI, call
 * [SyncEngine.sync] directly instead.
 */
suspend fun SyncEngine.syncQuietly(groupId: GroupId) {
    try {
        sync(groupId)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logger.w(e) { "syncQuietly: sync of ${groupId.value} failed, will retry on next open/refresh" }
    }
}
