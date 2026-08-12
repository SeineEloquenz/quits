package nz.eloque.quits.domain

/** A recorded payment from one member to another, settling part of a debt. */
class Settlement(
    override val id: SettlementId,
    val from: MemberId,
    val to: MemberId,
    val amount: Money,
    /** Rate to convert into the group's base currency, captured at entry. */
    val rateToBase: Double = 1.0,
    /** When the payment was made (epoch millis); 0 = unset. Not part of [equals]/[hashCode] (identity is [id]-based). */
    val paidAt: Long = 0L,
    /** UTC offset in minutes captured when [paidAt] was entered, so the day/time renders as the enterer meant it. Not part of identity. */
    val tzOffsetMinutes: Int = 0,
    /** Free-text note (optional). Like [paidAt], not part of identity/equality. */
    val note: String? = null,
) : Entity<SettlementId>() {
    init {
        require(from != to) { "a settlement must be between two different members" }
        require(amount.isPositive) { "a settlement amount must be positive" }
    }
}
