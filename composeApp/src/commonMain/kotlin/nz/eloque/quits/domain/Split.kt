package nz.eloque.quits.domain

/**
 * How an expense's total is divided among participants — a value object (strategy). Every variant
 * guarantees the resulting shares sum exactly to the total, distributing leftover minor units by the
 * largest-remainder method.
 */
sealed interface Split {
    /** Divides [total] into a share per participant. */
    fun divide(total: Money): Map<MemberId, Money>

    /** Equal split between [participants]. */
    data class Equal(
        val participants: List<MemberId>,
    ) : Split {
        init {
            require(participants.isNotEmpty()) { "an expense needs at least one participant" }
        }

        override fun divide(total: Money) = distribute(total, participants, participants.map { 1L })
    }

    /** Weighted by share counts (e.g. 2 shares vs 1). */
    data class Shares(
        val shares: Map<MemberId, Long>,
    ) : Split {
        init {
            require(shares.values.all { it >= 0 } && shares.values.sum() > 0) {
                "shares must be non-negative and not all zero"
            }
        }

        override fun divide(total: Money): Map<MemberId, Money> {
            val members = shares.keys.toList()
            return distribute(total, members, members.map { shares.getValue(it) })
        }
    }

    /** Weighted by whole-percent points; must sum to 100. */
    data class Percentage(
        val percent: Map<MemberId, Int>,
    ) : Split {
        init {
            require(percent.values.all { it >= 0 }) { "percentages must be non-negative" }
            require(percent.values.sum() == 100) { "percentages must sum to 100" }
        }

        override fun divide(total: Money): Map<MemberId, Money> {
            val members = percent.keys.toList()
            return distribute(total, members, members.map { percent.getValue(it).toLong() })
        }
    }

    /** Exact amount per participant; must sum to the total. */
    data class Exact(
        val amounts: Map<MemberId, Money>,
    ) : Split {
        init {
            require(amounts.isNotEmpty()) { "an expense needs at least one participant" }
        }

        override fun divide(total: Money): Map<MemberId, Money> {
            val sum = amounts.values.fold(Money.zero(total.currency)) { acc, m -> acc + m }
            require(sum == total) {
                "exact amounts (${sum.toDecimalString()}) must equal the total (${total.toDecimalString()})"
            }
            return amounts
        }
    }
}

/**
 * Allocates [total] across [members] proportionally to [weights], handing leftover minor units to
 * the largest fractional remainders so the parts sum exactly to [total]. Deterministic: ties break
 * by member id, so every device computes identical shares.
 */
private fun distribute(
    total: Money,
    members: List<MemberId>,
    weights: List<Long>,
): Map<MemberId, Money> {
    require(total.minorUnits >= 0) { "total must be non-negative" }
    val totalWeight = weights.sum()
    require(totalWeight > 0) { "weights must not all be zero" }

    val amounts = LongArray(members.size) { total.minorUnits * weights[it] / totalWeight }
    var leftover = total.minorUnits - amounts.sum()

    val byRemainder =
        members.indices.sortedWith(
            compareByDescending<Int> { total.minorUnits * weights[it] % totalWeight }
                .thenBy { members[it].value },
        )
    var i = 0
    while (leftover > 0) {
        amounts[byRemainder[i % byRemainder.size]] += 1
        leftover -= 1
        i += 1
    }
    return members.indices.associate { members[it] to Money(amounts[it], total.currency) }
}
