package nz.eloque.quits.domain

/**
 * Whether an entry is money going out ([EXPENSE]) or coming in ([INCOME]). Income is the mirror of an
 * expense in [Group.balances]: the receiver holds group money (debited) and the beneficiaries are each
 * owed their share (credited) — the same structure with both signs flipped.
 */
enum class EntryKind { EXPENSE, INCOME }

val EntryKind.isIncome: Boolean get() = this == EntryKind.INCOME

val EntryKind.isExpense: Boolean get() = this == EntryKind.EXPENSE

/**
 * How this kind moves balances relative to an expense: `+1` for an expense (the payer is credited,
 * share-holders debited), `-1` for income (the mirror). Lets [Group.balances] stay kind-agnostic.
 */
val EntryKind.balanceSign: Long get() = if (isIncome) -1L else 1L

/**
 * One member's side of an entry's money movement, in the entry currency: for an expense, who paid and
 * how much; for income, who received it.
 */
data class Payment(
    val member: MemberId,
    val amount: Money,
)

/**
 * An entry entity. Invariants enforced at construction: at least one payment, all amounts in a
 * single currency, and the [split]'s shares sum exactly to the total. Shares are *derived* from the
 * split so every device computes them identically.
 */
class Entry(
    override val id: EntryId,
    val title: String,
    val payments: List<Payment>,
    val split: Split,
    /** Rate to convert this entry's currency into the group's base currency, captured at entry. */
    val rateToBase: Double = 1.0,
    /** When the entry was incurred (epoch millis); 0 = unset. Not part of [equals]/[hashCode] (identity is [id]-based). */
    val spentAt: Long = 0L,
    /** UTC offset in minutes captured when [spentAt] was entered, so the day/time renders as the enterer meant it. Not part of [equals]/[hashCode]. */
    val tzOffsetMinutes: Int = 0,
    /** Preset id (app-defined) or custom [Category] id; null when uncategorized. */
    val categoryId: CategoryId? = null,
    val note: String? = null,
    /** Whether this entry is money out (expense) or in (income). See [EntryKind]. */
    val kind: EntryKind = EntryKind.EXPENSE,
    /**
     * False when this entry was reconstructed from a split type this app version doesn't recognize
     * (created by a newer version). Its balances are still correct — [split] is rebuilt as [Split.Exact]
     * from the stored per-member shares — but the UI must treat it as read-only: re-saving here would
     * rewrite it as a plain Exact split and, via last-write-wins sync, downgrade it for everyone.
     */
    val splitSupported: Boolean = true,
) : Entity<EntryId>() {
    init {
        require(payments.isNotEmpty()) { "an entry needs at least one payment" }
        require(payments.map { it.amount.currency }.distinct().size == 1) {
            "all payments must be in the same currency"
        }
    }

    val isIncome: Boolean get() = kind.isIncome
    val currency: Currency = payments.first().amount.currency
    val total: Money = payments.fold(Money.zero(currency)) { acc, p -> acc + p.amount }

    /** Each participant's share of [total], derived from [split] and guaranteed to sum to it. */
    val shares: Map<MemberId, Money> = split.divide(total)

    /** Total this member moved on the payments side — what they paid (expense) or received (income). */
    fun paymentsBy(member: MemberId): Money =
        payments.filter { it.member == member }.fold(Money.zero(currency)) { acc, p -> acc + p.amount }

    /** This member's share of the entry, or zero if they have none. */
    fun shareOf(member: MemberId): Money = shares[member] ?: Money.zero(currency)
}
