package nz.eloque.quits.data.repository

import nz.eloque.quits.data.db.ExpensePayerEntity
import nz.eloque.quits.data.db.ExpenseSplitEntity
import nz.eloque.quits.data.db.ExpenseWithLines
import nz.eloque.quits.data.db.MemberEntity
import nz.eloque.quits.data.db.SettlementEntity
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

internal fun splitTypeName(split: Split): String =
    when (split) {
        is Split.Equal -> SPLIT_EQUAL
        is Split.Shares -> SPLIT_SHARES
        is Split.Percentage -> SPLIT_PERCENTAGE
        is Split.Exact -> SPLIT_EXACT
    }

internal fun MemberEntity.toDomain(): Member = Member(MemberId(id), name)

internal fun SettlementEntity.toDomain(): Settlement =
    Settlement(
        SettlementId(id),
        MemberId(fromMember),
        MemberId(toMember),
        Money(amountMinor, Currency.of(currency)),
        rateToBase,
        paidAt,
    )

internal fun ExpenseWithLines.toDomain(): Expense {
    val currency = Currency.of(expense.currency)
    val payments = payers.map { Payment(MemberId(it.memberId), Money(it.amountMinor, currency)) }
    return Expense(
        ExpenseId(expense.id),
        expense.title,
        payments,
        toSplit(expense.splitType, splits, currency),
        expense.rateToBase,
        expense.spentAt,
    )
}

private fun toSplit(
    type: String,
    rows: List<ExpenseSplitEntity>,
    currency: Currency,
): Split =
    when (type) {
        SPLIT_EQUAL -> Split.Equal(rows.map { MemberId(it.memberId) })
        SPLIT_SHARES -> Split.Shares(rows.associate { MemberId(it.memberId) to (it.weight ?: 0.0).toLong() })
        SPLIT_PERCENTAGE -> Split.Percentage(rows.associate { MemberId(it.memberId) to (it.weight ?: 0.0).toInt() })
        SPLIT_EXACT -> Split.Exact(rows.associate { MemberId(it.memberId) to Money(it.shareMinor, currency) })
        else -> error("unknown split type: $type")
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
