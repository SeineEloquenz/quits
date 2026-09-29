package nz.eloque.quits.domain

/** A one-off [bill] split among [people], outside any [Group]. Every payer and share-holder must be one of [people]. */
class QuickSplit(
    val people: List<Member>,
    val bill: Bill,
) {
    init {
        val ids = people.map { it.id }.toSet()
        require(ids.size == people.size) { "people must be distinct" }
        require(bill.members.all { it in ids }) { "the bill references someone who isn't part of the split" }
    }

    /** Net position per person in the bill currency. Nets to zero. */
    fun balances(): Balances {
        val net = bill.net()
        return Balances(bill.currency, people.associate { it.id to (net[it.id] ?: Money.zero(bill.currency)) })
    }

    fun transfers(): List<Transfer> = balances().simplify()
}
