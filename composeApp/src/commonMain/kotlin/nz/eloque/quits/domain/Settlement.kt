package nz.eloque.quits.domain

/** A recorded payment from one member to another, settling part of a debt. */
class Settlement(
    override val id: SettlementId,
    val from: MemberId,
    val to: MemberId,
    val amount: Money,
    /** Rate to convert into the group's base currency, captured at entry. */
    val rateToBase: Double = 1.0,
) : Entity<SettlementId>() {
    init {
        require(from != to) { "a settlement must be between two different members" }
        require(amount.isPositive) { "a settlement amount must be positive" }
    }
}
