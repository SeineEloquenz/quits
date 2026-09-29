package nz.eloque.quits.domain

/**
 * One member's side of a bill's money movement, in the bill currency. For an expense this is who paid
 * and how much, for income who received it.
 */
data class Payment(
    val member: MemberId,
    val amount: Money,
)

/**
 * Value object pairing who paid with how the total is split. Requires at least one payment, a single
 * currency, and a [split] whose shares sum exactly to the total.
 */
data class Bill(
    val payments: List<Payment>,
    val split: Split,
) {
    init {
        require(payments.isNotEmpty()) { "a bill needs at least one payment" }
        require(payments.map { it.amount.currency }.distinct().size == 1) {
            "all payments must be in the same currency"
        }
    }

    val currency: Currency = payments.first().amount.currency
    val total: Money = payments.fold(Money.zero(currency)) { acc, p -> acc + p.amount }

    /** Each participant's share of [total], derived from [split] and guaranteed to sum to it. */
    val shares: Map<MemberId, Money> = split.divide(total)

    /** Every member who paid or holds a share. */
    val members: Set<MemberId> get() = payments.map { it.member }.toSet() + shares.keys

    fun paymentsBy(member: MemberId): Money =
        payments.filter { it.member == member }.fold(Money.zero(currency)) { acc, p -> acc + p.amount }

    fun shareOf(member: MemberId): Money = shares[member] ?: Money.zero(currency)

    /** What each member paid minus their share. Sums to zero. */
    fun net(): Map<MemberId, Money> = members.associateWith { paymentsBy(it) - shareOf(it) }
}
