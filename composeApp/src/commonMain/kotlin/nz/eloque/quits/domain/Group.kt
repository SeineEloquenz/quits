package nz.eloque.quits.domain

/**
 * Aggregate root: a shared-expense group. It owns its [members], [entries] (expenses and income) and
 * [settlements], guards referential invariants, and is the entry point for computing [balances] in
 * [baseCurrency].
 */
class Group(
    override val id: GroupId,
    val name: String,
    val baseCurrency: Currency,
    val members: List<Member>,
    val entries: List<Entry> = emptyList(),
    val settlements: List<Settlement> = emptyList(),
    /** Custom, user-created categories (preset categories are app-defined and not stored here). */
    val categories: List<Category> = emptyList(),
) : Entity<GroupId>() {
    private val memberIds: Set<MemberId> = members.map { it.id }.toSet()

    init {
        entries.forEach { entry ->
            (entry.payments.map { it.member } + entry.shares.keys).forEach { member ->
                require(member in memberIds) {
                    "entry ${entry.id.value} references unknown member ${member.value}"
                }
            }
        }
        settlements.forEach { settlement ->
            require(settlement.from in memberIds && settlement.to in memberIds) {
                "settlement ${settlement.id.value} references an unknown member"
            }
        }
    }

    /** Whether [member] is referenced by any entry (a payment or a share) or settlement. */
    fun references(member: MemberId): Boolean = member in referencedMemberIds(entries, settlements)

    companion object {
        /**
         * Member ids referenced by any of [entries] or [settlements] (as a payment party, a
         * share-holder, or a settlement party). Lets a reconstructed aggregate keep a tombstoned member
         * that is still tied to live financial records, instead of failing its referential invariant.
         */
        fun referencedMemberIds(
            entries: List<Entry>,
            settlements: List<Settlement>,
        ): Set<MemberId> =
            buildSet {
                entries.forEach { entry ->
                    entry.payments.forEach { add(it.member) }
                    addAll(entry.shares.keys)
                }
                settlements.forEach {
                    add(it.from)
                    add(it.to)
                }
            }
    }

    /**
     * Net balance per member in [baseCurrency]. Each entry (and each settlement) is converted as a
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

        for (entry in entries) {
            val baseTotal = ExchangeRate(entry.currency, baseCurrency, entry.rateToBase).convert(entry.total)
            if (!baseTotal.isPositive) continue // nothing to allocate; both sides would be zero
            // Income is the mirror of an expense: the receiver holds group money (debited) and the
            // beneficiaries are each owed their share (credited), so both signs flip.
            val sign = entry.kind.balanceSign
            val paymentsByMember = entry.payments.groupBy { it.member }.mapValues { (_, ps) -> ps.sumOf { it.amount.minorUnits } }
            val payers = paymentsByMember.keys.toList()
            distribute(baseTotal, payers, payers.map { paymentsByMember.getValue(it) })
                .forEach { (member, share) -> credit(member, sign * share.minorUnits) }
            val owers = entry.shares.keys.toList()
            distribute(baseTotal, owers, owers.map { entry.shares.getValue(it).minorUnits })
                .forEach { (member, share) -> credit(member, -sign * share.minorUnits) }
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
