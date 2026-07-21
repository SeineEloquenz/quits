package nz.eloque.quits.domain

/** Who paid, and how much (in the expense currency). */
data class Payment(
    val payer: MemberId,
    val amount: Money,
)

/**
 * An expense entity. Invariants enforced at construction: at least one payment, all amounts in a
 * single currency, and the [split]'s shares sum exactly to the total. Shares are *derived* from the
 * split so every device computes them identically.
 */
class Expense(
    override val id: ExpenseId,
    val title: String,
    val payments: List<Payment>,
    val split: Split,
    /** Rate to convert this expense's currency into the group's base currency, captured at entry. */
    val rateToBase: Double = 1.0,
    /**
     * When the expense was incurred (epoch millis). 0 means unset — callers that care about
     * chronological ordering (the activity feed) must supply a real value; the persistence
     * layer already always does. Not part of [equals]/[hashCode] (identity is [id]-based, per
     * [Entity]), so this can be filled in without affecting anything that compares expenses.
     */
    val spentAt: Long = 0L,
) : Entity<ExpenseId>() {
    init {
        require(payments.isNotEmpty()) { "an expense needs at least one payer" }
        require(payments.map { it.amount.currency }.distinct().size == 1) {
            "all payments must be in the same currency"
        }
    }

    val currency: Currency = payments.first().amount.currency
    val total: Money = payments.fold(Money.zero(currency)) { acc, p -> acc + p.amount }

    /** The owed share per participant; derived from [split] and guaranteed to sum to [total]. */
    val shares: Map<MemberId, Money> = split.divide(total)

    fun paidBy(member: MemberId): Money = payments.filter { it.payer == member }.fold(Money.zero(currency)) { acc, p -> acc + p.amount }

    fun owedBy(member: MemberId): Money = shares[member] ?: Money.zero(currency)
}
