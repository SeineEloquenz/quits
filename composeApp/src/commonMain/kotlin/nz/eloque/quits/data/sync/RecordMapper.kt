package nz.eloque.quits.data.sync

import nz.eloque.quits.data.db.ExpenseEntity
import nz.eloque.quits.data.db.ExpensePayerEntity
import nz.eloque.quits.data.db.ExpenseSplitEntity
import nz.eloque.quits.data.db.ExpenseWithLines
import nz.eloque.quits.data.db.GroupEntity
import nz.eloque.quits.data.db.MemberEntity
import nz.eloque.quits.data.db.SettlementEntity
import nz.eloque.quits.data.db.SyncMeta

/**
 * Converts Room rows to/from opaque [SyncRecord]s. Payloads never carry the group id (it's
 * contextual); on apply the caller supplies the local `groupId` of the sync container.
 */
object RecordMapper {
    /** The singleton group record's id within a container (the group has no row-level id to use). */
    const val GROUP_RECORD_ID = "group"

    fun record(group: GroupEntity): SyncRecord =
        SyncRecord(
            id = GROUP_RECORD_ID,
            updatedAt = group.sync.updatedAt,
            deviceId = group.sync.deviceId,
            deleted = group.sync.deleted,
            payload = SyncPayload.Group(group.name, group.baseCurrency),
        )

    fun record(member: MemberEntity): SyncRecord =
        SyncRecord(
            id = member.id,
            updatedAt = member.sync.updatedAt,
            deviceId = member.sync.deviceId,
            deleted = member.sync.deleted,
            payload = SyncPayload.Member(member.id, member.name, member.color),
        )

    fun record(expense: ExpenseWithLines): SyncRecord {
        val e = expense.expense
        return SyncRecord(
            id = e.id,
            updatedAt = e.sync.updatedAt,
            deviceId = e.sync.deviceId,
            deleted = e.sync.deleted,
            payload =
                SyncPayload.Expense(
                    id = e.id,
                    title = e.title,
                    amountMinor = e.amountMinor,
                    currency = e.currency,
                    rateToBase = e.rateToBase,
                    category = e.category,
                    spentAt = e.spentAt,
                    note = e.note,
                    splitType = e.splitType,
                    payers = expense.payers.map { SyncPayload.Payer(it.id, it.memberId, it.amountMinor) },
                    splits = expense.splits.map { SyncPayload.SplitLine(it.id, it.memberId, it.shareMinor, it.weight) },
                ),
        )
    }

    fun record(settlement: SettlementEntity): SyncRecord =
        SyncRecord(
            id = settlement.id,
            updatedAt = settlement.sync.updatedAt,
            deviceId = settlement.sync.deviceId,
            deleted = settlement.sync.deleted,
            payload =
                SyncPayload.Settlement(
                    id = settlement.id,
                    fromMember = settlement.fromMember,
                    toMember = settlement.toMember,
                    amountMinor = settlement.amountMinor,
                    currency = settlement.currency,
                    rateToBase = settlement.rateToBase,
                    paidAt = settlement.paidAt,
                    note = settlement.note,
                ),
        )

    /** The [SyncMeta] to stamp on a row reconstructed from [record]; [dirty] is false for pulls. */
    fun meta(
        record: SyncRecord,
        dirty: Boolean,
    ): SyncMeta = SyncMeta(record.updatedAt, record.deviceId, record.deleted, dirty)

    fun groupEntity(
        payload: SyncPayload.Group,
        groupId: String,
        meta: SyncMeta,
    ): GroupEntity = GroupEntity(groupId, payload.name, payload.baseCurrency, meta)

    fun memberEntity(
        payload: SyncPayload.Member,
        groupId: String,
        meta: SyncMeta,
    ): MemberEntity = MemberEntity(payload.id, groupId, payload.name, payload.color, meta)

    fun expenseEntities(
        payload: SyncPayload.Expense,
        groupId: String,
        meta: SyncMeta,
    ): ExpenseWithLines =
        ExpenseWithLines(
            ExpenseEntity(
                id = payload.id,
                groupId = groupId,
                title = payload.title,
                amountMinor = payload.amountMinor,
                currency = payload.currency,
                rateToBase = payload.rateToBase,
                category = payload.category,
                spentAt = payload.spentAt,
                note = payload.note,
                splitType = payload.splitType,
                sync = meta,
            ),
            payload.payers.map { ExpensePayerEntity(it.id, payload.id, it.memberId, it.amountMinor) },
            payload.splits.map { ExpenseSplitEntity(it.id, payload.id, it.memberId, it.shareMinor, it.weight) },
        )

    fun settlementEntity(
        payload: SyncPayload.Settlement,
        groupId: String,
        meta: SyncMeta,
    ): SettlementEntity =
        SettlementEntity(
            id = payload.id,
            groupId = groupId,
            fromMember = payload.fromMember,
            toMember = payload.toMember,
            amountMinor = payload.amountMinor,
            currency = payload.currency,
            rateToBase = payload.rateToBase,
            paidAt = payload.paidAt,
            note = payload.note,
            sync = meta,
        )
}
