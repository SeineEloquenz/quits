package nz.eloque.quits.domain

/**
 * Aggregate root: a shared-expense group. It owns its [members], [expenses] and [settlements],
 * guards referential invariants, and is the entry point for computing [balances] in [baseCurrency].
 */
class Group(
    override val id: GroupId,
    val name: String,
    val baseCurrency: Currency,
    val members: List<Member>,
    val expenses: List<Expense> = emptyList(),
    val settlements: List<Settlement> = emptyList(),
) : Entity<GroupId>() {
    private val memberIds: Set<MemberId> = members.map { it.id }.toSet()

    init {
        expenses.forEach { expense ->
            (expense.payments.map { it.payer } + expense.shares.keys).forEach { member ->
                require(member in memberIds) {
                    "expense ${expense.id.value} references unknown member ${member.value}"
                }
            }
        }
        settlements.forEach { settlement ->
            require(settlement.from in memberIds && settlement.to in memberIds) {
                "settlement ${settlement.id.value} references an unknown member"
            }
        }
    }

    /** Whether [member] is referenced by any expense (payer/share) or settlement. */
    fun references(member: MemberId): Boolean = member in referencedMemberIds(expenses, settlements)

    companion object {
        /**
         * Member ids referenced by any of [expenses] or [settlements] (as a payer, a share holder, or
         * a settlement party). Lets a reconstructed aggregate keep a member that has been tombstoned
         * but is still tied to live financial records, instead of failing its referential invariant.
         */
        fun referencedMemberIds(
            expenses: List<Expense>,
            settlements: List<Settlement>,
        ): Set<MemberId> =
            buildSet {
                expenses.forEach { expense ->
                    expense.payments.forEach { add(it.payer) }
                    addAll(expense.shares.keys)
                }
                settlements.forEach {
                    add(it.from)
                    add(it.to)
                }
            }
    }

    /**
     * Net balance per member in [baseCurrency]. Each expense (and each settlement) is converted as a
     * single unit, then its converted total is split across payers and across share-holders by the
     * same largest-remainder method the split uses. Both sides therefore sum to exactly the converted
     * total and cancel, so the group nets to zero even across currencies where per-amount rounding
     * otherwise would not.
     */
    fun balances(): Balances {
        val net = members.associate { it.id to 0L }.toMutableMap()

        fun credit(
            member: MemberId,
            minorUnits: Long,
        ) {
            net[member] = (net[member] ?: 0L) + minorUnits
        }

        for (expense in expenses) {
            val baseTotal = ExchangeRate(expense.currency, baseCurrency, expense.rateToBase).convert(expense.total)
            if (!baseTotal.isPositive) continue // nothing to allocate; both sides would be zero
            val paidByMember = expense.payments.groupBy { it.payer }.mapValues { (_, ps) -> ps.sumOf { it.amount.minorUnits } }
            val payers = paidByMember.keys.toList()
            distribute(baseTotal, payers, payers.map { paidByMember.getValue(it) })
                .forEach { (member, share) -> credit(member, share.minorUnits) }
            val owers = expense.shares.keys.toList()
            distribute(baseTotal, owers, owers.map { expense.shares.getValue(it).minorUnits })
                .forEach { (member, share) -> credit(member, -share.minorUnits) }
        }
        for (settlement in settlements) {
            val converted =
                ExchangeRate(settlement.amount.currency, baseCurrency, settlement.rateToBase)
                    .convert(settlement.amount)
                    .minorUnits
            credit(settlement.from, converted)
            credit(settlement.to, -converted)
        }

        return Balances(baseCurrency, net.mapValues { Money(it.value, baseCurrency) })
    }
}
