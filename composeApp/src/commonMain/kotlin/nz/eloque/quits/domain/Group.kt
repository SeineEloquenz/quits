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

    /** Net balance per member, converting every amount to [baseCurrency] via its captured rate. */
    fun balances(): Balances {
        val net = members.associate { it.id to 0L }.toMutableMap()

        fun add(
            member: MemberId,
            amount: Money,
            rateToBase: Double,
        ) {
            val converted = ExchangeRate(amount.currency, baseCurrency, rateToBase).convert(amount)
            net[member] = (net[member] ?: 0L) + converted.minorUnits
        }

        for (expense in expenses) {
            expense.payments.forEach { add(it.payer, it.amount, expense.rateToBase) }
            expense.shares.forEach { (member, share) -> add(member, -share, expense.rateToBase) }
        }
        for (settlement in settlements) {
            add(settlement.from, settlement.amount, settlement.rateToBase)
            add(settlement.to, -settlement.amount, settlement.rateToBase)
        }

        return Balances(baseCurrency, net.mapValues { Money(it.value, baseCurrency) })
    }
}
