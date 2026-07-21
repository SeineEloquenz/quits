package nz.eloque.quits.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import nz.eloque.quits.data.db.ExpenseEntity
import nz.eloque.quits.data.db.ExpenseWithLines
import nz.eloque.quits.data.db.GroupEntity
import nz.eloque.quits.data.db.MemberEntity
import nz.eloque.quits.data.db.QuitsDatabase
import nz.eloque.quits.data.db.SettlementEntity
import nz.eloque.quits.data.db.SyncMeta
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.Expense
import nz.eloque.quits.domain.ExpenseId
import nz.eloque.quits.domain.Group
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.Member
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.domain.Settlement

/** Lightweight projection for the groups list (avoids loading each full aggregate). */
data class GroupSummary(
    val id: GroupId,
    val name: String,
    val baseCurrency: Currency,
)

class GroupRepository(
    private val db: QuitsDatabase,
    private val deviceId: String,
    private val now: () -> Long,
) {
    private fun meta() = SyncMeta(updatedAt = now(), deviceId = deviceId, deleted = false, dirty = true)

    suspend fun saveGroup(group: Group) {
        db.groupDao().upsert(
            GroupEntity(group.id.value, group.name, group.baseCurrency.code, meta()),
        )
        db.memberDao().upsert(
            group.members.map { MemberEntity(it.id.value, group.id.value, it.name, color = null, meta()) },
        )
    }

    fun groupsFlow(): Flow<List<GroupSummary>> =
        db.groupDao().allFlow().map { groups ->
            groups.map { GroupSummary(GroupId(it.id), it.name, Currency.of(it.baseCurrency)) }
        }

    /** The full aggregate as a reactive stream; emits null while the group doesn't exist. */
    fun groupFlow(id: GroupId): Flow<Group?> =
        combine(
            db.groupDao().byIdFlow(id.value),
            db.memberDao().forGroupWithDeletedFlow(id.value),
            db.expenseDao().forGroupFlow(id.value),
            db.settlementDao().forGroupFlow(id.value),
        ) { entity, members, expenses, settlements ->
            entity?.let { assemble(it, members, expenses, settlements) }
        }

    /**
     * Reconstructs the aggregate, keeping any tombstoned member still tied to a live expense or
     * settlement so the [Group]'s referential invariant holds even after a concurrent delete-vs-use
     * across devices.
     */
    private fun assemble(
        entity: GroupEntity,
        memberEntities: List<MemberEntity>,
        expenseEntities: List<ExpenseWithLines>,
        settlementEntities: List<SettlementEntity>,
    ): Group {
        val expenses = expenseEntities.map { it.toDomain() }
        val settlements = settlementEntities.map { it.toDomain() }
        val referenced = Group.referencedMemberIds(expenses, settlements)
        val members =
            memberEntities
                .filter { !it.sync.deleted || MemberId(it.id) in referenced }
                .map { it.toDomain() }
        return Group(
            GroupId(entity.id),
            entity.name,
            Currency.of(entity.baseCurrency),
            members,
            expenses,
            settlements,
        )
    }

    suspend fun addMember(
        groupId: GroupId,
        member: Member,
    ) {
        db.memberDao().upsert(
            listOf(MemberEntity(member.id.value, groupId.value, member.name, color = null, meta())),
        )
    }

    suspend fun renameMember(
        memberId: MemberId,
        name: String,
    ) {
        val existing = db.memberDao().byId(memberId.value) ?: return
        db.memberDao().upsert(listOf(existing.copy(name = name, sync = meta())))
    }

    /** Soft-deletes a member, unless they're still referenced by an expense or settlement. */
    suspend fun removeMember(
        groupId: GroupId,
        memberId: MemberId,
    ): Boolean {
        val group = load(groupId) ?: return false
        if (group.references(memberId)) return false
        db.memberDao().tombstone(memberId.value, now(), deviceId)
        return true
    }

    suspend fun load(id: GroupId): Group? {
        val entity = db.groupDao().byId(id.value) ?: return null
        return assemble(
            entity,
            db.memberDao().forGroupWithDeleted(id.value),
            db.expenseDao().forGroup(id.value),
            db.settlementDao().forGroup(id.value),
        )
    }

    /**
     * Inserts or updates [expense]. The timestamp is [expense].spentAt when set (> 0); the
     * [spentAt] parameter can still override it explicitly (existing callers keep working
     * unchanged). Display-only fields ([category]/[note]) are kept from the existing row when not
     * supplied, so editing the money/split parts never drops them.
     */
    suspend fun upsertExpense(
        groupId: GroupId,
        expense: Expense,
        spentAt: Long? = null,
        category: String? = null,
        note: String? = null,
    ) {
        val existing = db.expenseDao().byId(expense.id.value)?.expense
        val resolvedSpentAt = spentAt ?: expense.spentAt.takeIf { it > 0L } ?: existing?.spentAt ?: now()
        db.expenseDao().save(
            ExpenseEntity(
                id = expense.id.value,
                groupId = groupId.value,
                title = expense.title,
                amountMinor = expense.total.minorUnits,
                currency = expense.currency.code,
                rateToBase = expense.rateToBase,
                category = category ?: existing?.category,
                spentAt = resolvedSpentAt,
                note = note ?: existing?.note,
                splitType = splitTypeName(expense.split),
                sync = meta(),
            ),
            payerRows(expense),
            splitRows(expense),
        )
    }

    /** Soft-deletes (tombstones) an expense so it drops out of queries and syncs as a deletion. */
    suspend fun deleteExpense(expenseId: ExpenseId) {
        db.expenseDao().tombstone(expenseId.value, now(), deviceId)
    }

    /**
     * Inserts or updates [settlement]. The timestamp is [settlement].paidAt when set (> 0); the
     * [paidAt] parameter can still override it explicitly (existing callers keep working
     * unchanged).
     */
    suspend fun upsertSettlement(
        groupId: GroupId,
        settlement: Settlement,
        paidAt: Long? = null,
        note: String? = null,
    ) {
        val resolvedPaidAt = paidAt ?: settlement.paidAt.takeIf { it > 0L } ?: now()
        db.settlementDao().upsert(
            SettlementEntity(
                id = settlement.id.value,
                groupId = groupId.value,
                fromMember = settlement.from.value,
                toMember = settlement.to.value,
                amountMinor = settlement.amount.minorUnits,
                currency = settlement.amount.currency.code,
                rateToBase = settlement.rateToBase,
                paidAt = resolvedPaidAt,
                note = note,
                sync = meta(),
            ),
        )
    }
}
