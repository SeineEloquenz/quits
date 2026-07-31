package nz.eloque.quits.domain

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
 * (settlements are transfers, not spending, so they're excluded). [byCategory] and [byMember] each
 * sum to [total] and are sorted largest-first.
 */
data class Spending(
    val base: Currency,
    val total: Money,
    val byCategory: List<CategoryTotal>,
    val byMember: List<MemberTotal>,
)

/**
 * Computes the group's [Spending]. Each expense is converted to [baseCurrency] as a unit (via its
 * captured rate) and then, for the per-member view, split across its share-holders by the same
 * largest-remainder method [balances] uses — so both breakdowns reconcile exactly to the total.
 */
fun Group.spending(): Spending {
    // Income is money in, not spending — it's excluded from every breakdown here.
    val expenses = expenses.filter { it.kind == EntryKind.EXPENSE }

    fun baseTotal(expense: Expense): Money = ExchangeRate(expense.currency, baseCurrency, expense.rateToBase).convert(expense.total)

    val total = expenses.fold(Money.zero(baseCurrency)) { acc, e -> acc + baseTotal(e) }

    val byCategory =
        expenses
            .groupBy { it.categoryId }
            .map { (categoryId, es) -> CategoryTotal(categoryId, es.fold(Money.zero(baseCurrency)) { a, e -> a + baseTotal(e) }) }
            .sortedByDescending { it.amount.minorUnits }

    val perMember = members.associate { it.id to 0L }.toMutableMap()
    for (expense in expenses) {
        val baseAmount = baseTotal(expense)
        if (!baseAmount.isPositive) continue
        val owers = expense.shares.keys.toList()
        distribute(baseAmount, owers, owers.map { expense.shares.getValue(it).minorUnits })
            .forEach { (member, share) -> perMember[member] = (perMember[member] ?: 0L) + share.minorUnits }
    }
    val byMember =
        members
            .map { MemberTotal(it.id, Money(perMember[it.id] ?: 0L, baseCurrency)) }
            .sortedByDescending { it.amount.minorUnits }

    return Spending(baseCurrency, total, byCategory, byMember)
}
