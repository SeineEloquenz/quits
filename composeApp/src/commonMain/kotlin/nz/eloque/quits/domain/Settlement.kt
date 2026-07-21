package nz.eloque.quits.domain

/** A recorded payment from one member to another, settling part of a debt. */
class Settlement(
    override val id: SettlementId,
    val from: MemberId,
    val to: MemberId,
    val amount: Money,
    /** Rate to convert into the group's base currency, captured at entry. */
    val rateToBase: Double = 1.0,
    /**
     * When the payment was made (epoch millis). 0 means unset — callers that care about
     * chronological ordering (the activity feed) must supply a real value; the persistence
     * layer already always does. Not part of [equals]/[hashCode] (identity is [id]-based, per
     * [Entity]), so this can be filled in without affecting anything that compares settlements.
     */
    val paidAt: Long = 0L,
) : Entity<SettlementId>() {
    init {
        require(from != to) { "a settlement must be between two different members" }
        require(amount.isPositive) { "a settlement amount must be positive" }
    }
}
