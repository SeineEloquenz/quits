package nz.eloque.quits.domain

/** A suggested payment to settle up: [from] pays [to] [amount]. */
data class Transfer(
    val from: MemberId,
    val to: MemberId,
    val amount: Money,
)

/**
 * Net position per member in a single [base] currency: positive = is owed money (creditor),
 * negative = owes (debtor). Across a group it nets to zero.
 */
data class Balances(
    val base: Currency,
    val net: Map<MemberId, Money>,
) {
    fun of(member: MemberId): Money = net[member] ?: Money.zero(base)

    /**
     * A small set of transfers that clears every debt (greedy min cash flow): repeatedly settle the
     * largest debtor against the largest creditor. Produces at most n-1 transfers.
     */
    fun simplify(): List<Transfer> {
        val creditors =
            net.filter { it.value.isPositive }
                .map { it.key to it.value.minorUnits }
                .sortedByDescending { it.second }
        val debtors =
            net.filter { it.value.isNegative }
                .map { it.key to -it.value.minorUnits } // amounts owed, as positives
                .sortedByDescending { it.second }

        val transfers = mutableListOf<Transfer>()
        var ci = 0
        var di = 0
        var credit = creditors.firstOrNull()?.second ?: 0L
        var debt = debtors.firstOrNull()?.second ?: 0L

        while (ci < creditors.size && di < debtors.size) {
            val amount = minOf(credit, debt)
            if (amount > 0) {
                transfers += Transfer(debtors[di].first, creditors[ci].first, Money(amount, base))
            }
            credit -= amount
            debt -= amount
            if (credit == 0L) {
                ci += 1
                if (ci < creditors.size) credit = creditors[ci].second
            }
            if (debt == 0L) {
                di += 1
                if (di < debtors.size) debt = debtors[di].second
            }
        }
        return transfers
    }
}
