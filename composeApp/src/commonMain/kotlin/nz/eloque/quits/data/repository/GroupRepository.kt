package nz.eloque.quits.data.repository

import nz.eloque.quits.data.db.ExpenseEntity
import nz.eloque.quits.data.db.GroupEntity
import nz.eloque.quits.data.db.MemberEntity
import nz.eloque.quits.data.db.QuitsDatabase
import nz.eloque.quits.data.db.SettlementEntity
import nz.eloque.quits.data.db.SyncMeta
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.Expense
import nz.eloque.quits.domain.Group
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.Settlement

class GroupRepository(
    private val db: QuitsDatabase,
    private val deviceId: String,
    private val now: () -> Long,
) {
    private fun meta() = SyncMeta(updatedAt = now(), deviceId = deviceId, deleted = false, dirty = true)

    suspend fun saveGroup(
        group: Group,
        code: String,
    ) {
        db.groupDao().upsert(
            GroupEntity(group.id.value, group.name, group.baseCurrency.code, code, meta()),
        )
        db.memberDao().upsert(
            group.members.map { MemberEntity(it.id.value, group.id.value, it.name, color = null, meta()) },
        )
    }

    suspend fun load(id: GroupId): Group? {
        val entity = db.groupDao().byId(id.value) ?: return null
        return Group(
            GroupId(entity.id),
            entity.name,
            Currency.of(entity.baseCurrency),
            db.memberDao().forGroup(id.value).map { it.toDomain() },
            db.expenseDao().forGroup(id.value).map { it.toDomain() },
            db.settlementDao().forGroup(id.value).map { it.toDomain() },
        )
    }

    suspend fun upsertExpense(
        groupId: GroupId,
        expense: Expense,
        spentAt: Long,
        category: String? = null,
        note: String? = null,
    ) {
        db.expenseDao().save(
            ExpenseEntity(
                id = expense.id.value,
                groupId = groupId.value,
                title = expense.title,
                amountMinor = expense.total.minorUnits,
                currency = expense.currency.code,
                rateToBase = expense.rateToBase,
                category = category,
                spentAt = spentAt,
                note = note,
                splitType = splitTypeName(expense.split),
                sync = meta(),
            ),
            payerRows(expense),
            splitRows(expense),
        )
    }

    suspend fun upsertSettlement(
        groupId: GroupId,
        settlement: Settlement,
        paidAt: Long,
        note: String? = null,
    ) {
        db.settlementDao().upsert(
            SettlementEntity(
                id = settlement.id.value,
                groupId = groupId.value,
                fromMember = settlement.from.value,
                toMember = settlement.to.value,
                amountMinor = settlement.amount.minorUnits,
                currency = settlement.amount.currency.code,
                rateToBase = settlement.rateToBase,
                paidAt = paidAt,
                note = note,
                sync = meta(),
            ),
        )
    }
}
