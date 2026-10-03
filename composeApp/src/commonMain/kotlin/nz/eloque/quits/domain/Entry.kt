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

/** An entry entity. Its money movement is a [Bill], whose invariants hold at construction. */
class Entry(
    override val id: EntryId,
    val title: String,
    payments: List<Payment>,
    split: Split,
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
    val bill: Bill = Bill(payments, split)
    val payments: List<Payment> get() = bill.payments
    val split: Split get() = bill.split

    val isIncome: Boolean get() = kind.isIncome
    val currency: Currency get() = bill.currency
    val total: Money get() = bill.total
    val shares: Map<MemberId, Money> get() = bill.shares

    /** What this member paid (expense) or received (income). */
    fun paymentsBy(member: MemberId): Money = bill.paymentsBy(member)

    fun shareOf(member: MemberId): Money = bill.shareOf(member)
}
