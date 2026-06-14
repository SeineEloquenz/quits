package nz.eloque.quits.data.sync

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

    /** Pushes local changes then pulls remote ones. No-op (false) for a local-only group. */
    suspend fun sync(localGroupId: GroupId): Boolean {
        val handle = db.groupSyncDao().byGroup(localGroupId.value) ?: return false
        push(localGroupId.value, handle)
        pull(localGroupId.value)
        return true
    }

    private suspend fun push(
        gid: String,
        handle: GroupSyncEntity,
    ) {
        val records = mutableListOf<SyncRecord>()
        val group = db.groupDao().byId(gid)
        if (group != null && group.sync.dirty) records += RecordMapper.record(group)
        val members = db.memberDao().dirty(gid).also { it.forEach { m -> records += RecordMapper.record(m) } }
        val expenses = db.expenseDao().dirty(gid).also { it.forEach { e -> records += RecordMapper.record(e) } }
        val settlements = db.settlementDao().dirty(gid).also { it.forEach { s -> records += RecordMapper.record(s) } }
        if (records.isEmpty()) return

        val applied = relay.push(handle.remoteId, handle.token, records).applied.toSet()
        if (group != null && RecordMapper.GROUP_RECORD_ID in applied) db.groupDao().clearDirty(gid)
        members.filter { it.id in applied }.forEach { db.memberDao().clearDirty(it.id) }
        expenses.filter { it.expense.id in applied }.forEach { db.expenseDao().clearDirty(it.expense.id) }
        settlements.filter { it.id in applied }.forEach { db.settlementDao().clearDirty(it.id) }
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
