package nz.eloque.quits.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nz.eloque.quits.data.db.GroupSyncEntity
import nz.eloque.quits.data.db.QuitsDatabase
import nz.eloque.quits.data.db.SyncMeta
import nz.eloque.quits.domain.GroupId

/**
 * Drives delta sync against the [relay]: push dirty rows, pull since the cursor, apply with
 * last-write-wins. [deviceId] identifies this device for LWW tiebreaks. One group at a time.
 */
class SyncEngine(
    private val db: QuitsDatabase,
    private val relay: Relay,
    private val deviceId: String,
    private val now: () -> Long = { 0L },
) {
    /** Registers a local group with the relay and pushes its current state. Returns the share handle. */
    suspend fun share(localGroupId: GroupId): GroupHandle {
        val handle = relay.createGroup()
        db.groupSyncDao().put(GroupSyncEntity(localGroupId.value, handle.remoteId, handle.code, handle.token))
        sync(localGroupId)
        return handle
    }

    /** Joins an existing group by [code], then pulls it down. Returns the new local group id, or null. */
    suspend fun join(code: String): GroupId? {
        val handle = relay.joinGroup(code) ?: return null
        // A joiner has no prior local group, so it adopts the relay's id as its local id.
        db.groupSyncDao().put(GroupSyncEntity(handle.remoteId, handle.remoteId, handle.code, handle.token))
        val id = GroupId(handle.remoteId)
        sync(id)
        return id
    }

    /** Whether [localGroupId] has a relay handle (i.e. is shared/joined). */
    suspend fun isSynced(localGroupId: GroupId): Boolean = db.groupSyncDao().byGroup(localGroupId.value) != null

    /** Reactive sync info (share code + last-synced time) for [localGroupId]; null fields until shared. */
    fun syncInfoFlow(localGroupId: GroupId): Flow<SyncInfo> =
        db.groupSyncDao().byGroupFlow(localGroupId.value).map {
            SyncInfo(code = it?.code, lastSyncedAt = it?.lastSyncedAt?.takeIf { ts -> ts > 0 })
        }

    /** Syncs every shared group; returns false if any group failed (so the worker can retry). */
    suspend fun syncAll(): Boolean {
        var ok = true
        db.groupSyncDao().all().forEach { handle ->
            try {
                sync(GroupId(handle.groupId))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ok = false
            }
        }
        return ok
    }

    /** Pushes local changes then pulls remote ones. No-op (false) for a local-only group. */
    suspend fun sync(localGroupId: GroupId): Boolean {
        val handle = db.groupSyncDao().byGroup(localGroupId.value) ?: return false
        push(localGroupId.value, handle)
        pull(localGroupId.value)
        db.groupSyncDao().setLastSyncedAt(localGroupId.value, now())
        return true
    }

    private suspend fun push(
        gid: String,
        handle: GroupSyncEntity,
    ) {
        val group = db.groupDao().byId(gid)
        val members = db.memberDao().dirty(gid)
        val expenses = db.expenseDao().dirty(gid)
        val settlements = db.settlementDao().dirty(gid)

        val records = mutableListOf<SyncRecord>()
        if (group != null && group.sync.dirty) records += RecordMapper.record(group)
        records += members.map { RecordMapper.record(it) }
        records += expenses.map { RecordMapper.record(it) }
        records += settlements.map { RecordMapper.record(it) }
        if (records.isEmpty()) return

        val applied = relay.push(handle.remoteId, handle.token, records).applied.toSet()
        // Clear dirty keyed on the pushed (updatedAt, deviceId): if the row was edited again during
        // the round-trip its clock moved, the guarded update no-ops, and the edit stays pending.
        if (group != null && RecordMapper.GROUP_RECORD_ID in applied) {
            db.groupDao().clearDirty(gid, group.sync.updatedAt, group.sync.deviceId)
        }
        members.filter { it.id in applied }.forEach { db.memberDao().clearDirty(it.id, it.sync.updatedAt, it.sync.deviceId) }
        expenses.filter { it.expense.id in applied }.forEach {
            db.expenseDao().clearDirty(it.expense.id, it.expense.sync.updatedAt, it.expense.sync.deviceId)
        }
        settlements.filter { it.id in applied }.forEach { db.settlementDao().clearDirty(it.id, it.sync.updatedAt, it.sync.deviceId) }
    }

    private suspend fun pull(gid: String) {
        val handle = db.groupSyncDao().byGroup(gid) ?: return
        val result = relay.pull(handle.remoteId, handle.token, handle.lastSeq)
        for (record in result.records) apply(gid, record)
        if (result.seq > handle.lastSeq) db.groupSyncDao().setLastSeq(gid, result.seq)
    }

    private suspend fun apply(
        gid: String,
        record: SyncRecord,
    ) {
        val meta = RecordMapper.meta(record, dirty = false)
        when (val payload = record.payload) {
            is SyncPayload.Group ->
                if (wins(db.groupDao().byId(gid)?.sync, record)) {
                    db.groupDao().upsert(RecordMapper.groupEntity(payload, gid, meta))
                }
            is SyncPayload.Member ->
                if (wins(db.memberDao().byId(payload.id)?.sync, record)) {
                    db.memberDao().upsert(listOf(RecordMapper.memberEntity(payload, gid, meta)))
                }
            is SyncPayload.Expense ->
                if (wins(db.expenseDao().byId(payload.id)?.expense?.sync, record)) {
                    val entities = RecordMapper.expenseEntities(payload, gid, meta)
                    db.expenseDao().save(entities.expense, entities.payers, entities.splits)
                }
            is SyncPayload.Settlement ->
                if (wins(db.settlementDao().byId(payload.id)?.sync, record)) {
                    db.settlementDao().upsert(RecordMapper.settlementEntity(payload, gid, meta))
                }
        }
    }

    /** Same rule the relay uses: newer wins; ties broken by the larger device id. */
    private fun wins(
        local: SyncMeta?,
        record: SyncRecord,
    ): Boolean =
        local == null ||
            record.updatedAt > local.updatedAt ||
            (record.updatedAt == local.updatedAt && record.deviceId >= local.deviceId)
}
