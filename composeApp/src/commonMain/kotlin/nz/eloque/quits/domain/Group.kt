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
        /** Member ids referenced by any [entries] or [settlements], so a reconstructed aggregate can keep a tombstoned member still tied to live records. */
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
     * Net balance per member in [baseCurrency]. Each entry/settlement is converted as a single unit, then
     * split across payers and share-holders by the split's largest-remainder method, so both sides cancel
     * and the group nets to zero even across currencies.
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
            if (!baseTotal.isPositive) continue
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
