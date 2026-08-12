@file:OptIn(ExperimentalTime::class)

package nz.eloque.quits.domain

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.asTimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** Spend for one category ([categoryId] null = uncategorized), in the group's base currency. */
data class CategoryTotal(
    val categoryId: CategoryId?,
    val amount: Money,
)

/** How much of the group's spend a member consumed (their share), in the base currency. */
data class MemberTotal(
    val member: MemberId,
    val amount: Money,
)

/**
 * Read-only spending breakdown of a group, all in [base]. [total] is the sum of every expense
 * (income and settlements aren't spending, so they're excluded). [byCategory] and [byMember] each
 * sum to [total] and are sorted largest-first.
 */
data class Spending(
    val base: Currency,
    val total: Money,
    val byCategory: List<CategoryTotal>,
    val byMember: List<MemberTotal>,
)

/**
 * Computes the group's [Spending]. Each entry is converted to [baseCurrency] as a unit (via its
 * captured rate) and then, for the per-member view, split across its share-holders by the same
 * largest-remainder method [balances] uses — so both breakdowns reconcile exactly to the total.
 */
fun Group.spending(): Spending {
    val spendingEntries = entries.filter { it.kind.isExpense }

    fun baseTotal(entry: Entry): Money = ExchangeRate(entry.currency, baseCurrency, entry.rateToBase).convert(entry.total)

    val total = spendingEntries.fold(Money.zero(baseCurrency)) { acc, e -> acc + baseTotal(e) }

    val byCategory =
        spendingEntries
            .groupBy { it.categoryId }
            .map { (categoryId, es) -> CategoryTotal(categoryId, es.fold(Money.zero(baseCurrency)) { a, e -> a + baseTotal(e) }) }
            .sortedByDescending { it.amount.minorUnits }

    val perMember = members.associate { it.id to 0L }.toMutableMap()
    for (entry in spendingEntries) {
        val baseAmount = baseTotal(entry)
        if (!baseAmount.isPositive) continue
        val owers = entry.shares.keys.toList()
        distribute(baseAmount, owers, owers.map { entry.shares.getValue(it).minorUnits })
            .forEach { (member, share) -> perMember[member] = (perMember[member] ?: 0L) + share.minorUnits }
    }
    val byMember =
        members
            .map { MemberTotal(it.id, Money(perMember[it.id] ?: 0L, baseCurrency)) }
            .sortedByDescending { it.amount.minorUnits }

    return Spending(baseCurrency, total, byCategory, byMember)
}

/** Bucket size for the spend-over-time chart. */
enum class SpendPeriod { WEEK, MONTH }

/** Total spend in one bucket, keyed by the bucket's [start] date (Monday for a week, the 1st for a month). */
data class PeriodTotal(
    val start: LocalDate,
    val amount: Money,
)

/** Chart never renders more than this many buckets; older ones are dropped. */
private const val MAX_PERIODS = 60

private fun SpendPeriod.startOf(date: LocalDate): LocalDate =
    when (this) {
        SpendPeriod.WEEK -> date.minus(DatePeriod(days = date.dayOfWeek.ordinal))
        SpendPeriod.MONTH -> LocalDate(date.year, date.month, 1)
    }

private fun SpendPeriod.next(start: LocalDate): LocalDate =
    when (this) {
        SpendPeriod.WEEK -> start.plus(DatePeriod(days = 7))
        SpendPeriod.MONTH -> start.plus(DatePeriod(months = 1))
    }

/**
 * Expense spend over time in [baseCurrency], bucketed by [period] (income and settlements excluded), each
 * expense counted in its entered offset. Gaps are zero-filled for a continuous timeline; capped to the
 * most recent [MAX_PERIODS] buckets.
 */
fun Group.spendOverTime(period: SpendPeriod): List<PeriodTotal> {
    val totals = HashMap<LocalDate, Long>()
    for (entry in entries) {
        if (!entry.kind.isExpense) continue
        val base = ExchangeRate(entry.currency, baseCurrency, entry.rateToBase).convert(entry.total)
        if (!base.isPositive) continue
        val date =
            Instant.fromEpochMilliseconds(entry.spentAt)
                .toLocalDateTime(UtcOffset(minutes = entry.tzOffsetMinutes).asTimeZone())
                .date
        val start = period.startOf(date)
        totals[start] = (totals[start] ?: 0L) + base.minorUnits
    }
    if (totals.isEmpty()) return emptyList()

    val out = mutableListOf<PeriodTotal>()
    var cur = totals.keys.min()
    val last = totals.keys.max()
    while (cur <= last) {
        out += PeriodTotal(cur, Money(totals[cur] ?: 0L, baseCurrency))
        cur = period.next(cur)
    }
    return out.takeLast(MAX_PERIODS)
}
