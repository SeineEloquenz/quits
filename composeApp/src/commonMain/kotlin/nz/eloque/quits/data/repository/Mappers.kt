package nz.eloque.quits.data.repository

import nz.eloque.quits.data.db.CategoryEntity
import nz.eloque.quits.data.db.ExpenseItemEntity
import nz.eloque.quits.data.db.ExpenseItemParticipantEntity
import nz.eloque.quits.data.db.ExpensePayerEntity
import nz.eloque.quits.data.db.ExpenseSplitEntity
import nz.eloque.quits.data.db.ExpenseWithLines
import nz.eloque.quits.data.db.ItemWithParticipants
import nz.eloque.quits.data.db.MemberEntity
import nz.eloque.quits.data.db.SettlementEntity
import nz.eloque.quits.domain.Category
import nz.eloque.quits.domain.CategoryId
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.Expense
import nz.eloque.quits.domain.ExpenseId
import nz.eloque.quits.domain.Member
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.domain.Money
import nz.eloque.quits.domain.Payment
import nz.eloque.quits.domain.Settlement
import nz.eloque.quits.domain.SettlementId
import nz.eloque.quits.domain.Split

internal const val SPLIT_EQUAL = "EQUAL"
internal const val SPLIT_SHARES = "SHARES"
internal const val SPLIT_PERCENTAGE = "PERCENTAGE"
internal const val SPLIT_EXACT = "EXACT"
internal const val SPLIT_ITEMIZED = "ITEMIZED"

private val KNOWN_SPLIT_TYPES = setOf(SPLIT_EQUAL, SPLIT_SHARES, SPLIT_PERCENTAGE, SPLIT_EXACT, SPLIT_ITEMIZED)

internal fun splitTypeName(split: Split): String =
    when (split) {
        is Split.Equal -> SPLIT_EQUAL
        is Split.Shares -> SPLIT_SHARES
        is Split.Percentage -> SPLIT_PERCENTAGE
        is Split.Exact -> SPLIT_EXACT
        is Split.Itemized -> SPLIT_ITEMIZED
    }

internal fun MemberEntity.toDomain(): Member = Member(MemberId(id), name)

internal fun CategoryEntity.toDomain(): Category = Category(CategoryId(id), name, icon, color)

internal fun SettlementEntity.toDomain(): Settlement =
    Settlement(
        SettlementId(id),
        MemberId(fromMember),
        MemberId(toMember),
        Money(amountMinor, Currency.of(currency)),
        rateToBase,
        paidAt,
        tzOffsetMinutes,
        note,
    )

internal fun ExpenseWithLines.toDomain(): Expense {
    val currency = Currency.of(expense.currency)
    val payments = payers.map { Payment(MemberId(it.memberId), Money(it.amountMinor, currency)) }
    return Expense(
        ExpenseId(expense.id),
        expense.title,
        payments,
        toSplit(expense.splitType, splits, items, currency),
        expense.rateToBase,
        expense.spentAt,
        expense.tzOffsetMinutes,
        expense.categoryId?.let { CategoryId(it) },
        expense.note,
        splitSupported = expense.splitType in KNOWN_SPLIT_TYPES,
    )
}

private fun toSplit(
    type: String,
    rows: List<ExpenseSplitEntity>,
    items: List<ItemWithParticipants>,
    currency: Currency,
): Split =
    when (type) {
        SPLIT_EQUAL -> Split.Equal(rows.map { MemberId(it.memberId) })
        SPLIT_SHARES -> Split.Shares(rows.associate { MemberId(it.memberId) to (it.weight ?: 0.0).toLong() })
        SPLIT_PERCENTAGE -> Split.Percentage(rows.associate { MemberId(it.memberId) to (it.weight ?: 0.0).toInt() })
        SPLIT_EXACT -> Split.Exact(rows.associate { MemberId(it.memberId) to Money(it.shareMinor, currency) })
        SPLIT_ITEMIZED ->
            Split.Itemized(
                items
                    .sortedBy { it.item.position }
                    .map { iwp ->
                        Split.Itemized.Item(
                            iwp.item.label,
                            Money(iwp.item.amountMinor, currency),
                            iwp.participants.map { MemberId(it.memberId) }.toSet(),
                        )
                    },
            )
        // Forward-compat: a split type from a newer app version. Never throw on well-formed synced
        // data — rebuild it from the materialized per-member shares so balances stay correct. The
        // caller flags it unsupported (see [ExpenseWithLines.toDomain]) so the UI keeps it read-only.
        else -> Split.Exact(rows.associate { MemberId(it.memberId) to Money(it.shareMinor, currency) })
    }

/** Payer lines for an expense; synthetic ids since multiple payments may share a member. */
internal fun payerRows(expense: Expense): List<ExpensePayerEntity> =
    expense.payments.mapIndexed { i, payment ->
        ExpensePayerEntity("${expense.id.value}:payer:$i", expense.id.value, payment.payer.value, payment.amount.minorUnits)
    }

/** Split lines for an expense: the materialized share per member plus the spec weight (if any). */
internal fun splitRows(expense: Expense): List<ExpenseSplitEntity> {
    val eid = expense.id.value
    val split = expense.split
    val members: Collection<MemberId> =
        when (split) {
            is Split.Equal -> split.participants
            is Split.Shares -> split.shares.keys
            is Split.Percentage -> split.percent.keys
            is Split.Exact -> split.amounts.keys
            is Split.Itemized -> split.items.flatMap { it.participants }.toSet()
        }
    return members.map { member ->
        val weight: Double? =
            when (split) {
                is Split.Shares -> split.shares.getValue(member).toDouble()
                is Split.Percentage -> split.percent.getValue(member).toDouble()
                else -> null
            }
        ExpenseSplitEntity("$eid:${member.value}", eid, member.value, expense.owedBy(member).minorUnits, weight)
    }
}

/** Item id shared by an item row and its participant rows; position-based so it round-trips stably. */
private fun itemId(
    expenseId: String,
    position: Int,
): String = "$expenseId:item:$position"

/** Line-item rows for an itemized expense; empty for every other split kind. */
internal fun itemRows(expense: Expense): List<ExpenseItemEntity> {
    val split = expense.split
    if (split !is Split.Itemized) return emptyList()
    return split.items.mapIndexed { i, item ->
        ExpenseItemEntity(itemId(expense.id.value, i), expense.id.value, item.label, item.amount.minorUnits, i)
    }
}

/** Item-to-member assignment rows for an itemized expense; empty for every other split kind. */
internal fun itemParticipantRows(expense: Expense): List<ExpenseItemParticipantEntity> {
    val split = expense.split
    if (split !is Split.Itemized) return emptyList()
    return split.items.flatMapIndexed { i, item ->
        item.participants.map { ExpenseItemParticipantEntity(itemId(expense.id.value, i), it.value) }
    }
}
