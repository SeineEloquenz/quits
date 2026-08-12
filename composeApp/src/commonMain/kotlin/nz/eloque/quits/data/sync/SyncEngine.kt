package nz.eloque.quits.data.sync

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nz.eloque.quits.data.crypto.GroupCrypto
import nz.eloque.quits.data.crypto.GroupKey
import nz.eloque.quits.data.crypto.SecretCode
import nz.eloque.quits.data.db.GroupSyncEntity
import nz.eloque.quits.data.db.QuitsDatabase
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

    /** Pushes local changes then pulls remote ones. No-op (false) for a local-only group. */
    suspend fun sync(
        localGroupId: GroupId,
        full: Boolean = false,
    ): Boolean {
        val handle = db.groupSyncDao().byGroup(localGroupId.value) ?: return false
        push(localGroupId.value, handle, full)
        pull(localGroupId.value)
        db.groupSyncDao().setLastSyncedAt(localGroupId.value, now())
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

        val records = mutableListOf<SyncRecord>()
        if (group != null && (full || group.sync.dirty)) records += RecordMapper.record(group)
        records += members.map { RecordMapper.record(it) }
        records += entries.map { RecordMapper.record(it) }
        records += settlements.map { RecordMapper.record(it) }
        records += categories.map { RecordMapper.record(it) }
        if (records.isEmpty()) return

        val key = keyFor(handle)
        val applied = relay.push(handle.remoteId, handle.token, records.map { seal(key, it) }).applied.toSet()
        // Clear dirty keyed on the pushed (updatedAt, deviceId): if the row was edited again during
        // the round-trip its clock moved, the guarded update no-ops, and the edit stays pending.
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
