package nz.eloque.quits.domain

/** A group's starting split for new expenses. A member missing from [weights] joined after it was set. */
data class DefaultSplit(
    val kind: Kind,
    val weights: Map<MemberId, Long>,
) {
    enum class Kind { EQUAL, SHARES, PERCENTAGE }

    /** Whether this behaves like having no default. */
    fun isEveryoneEqual(): Boolean = kind == Kind.EQUAL && weights.values.all { it == 1L }

    /**
     * The split for [members], who must not be empty. A newcomer is included (1 share for SHARES). A
     * percentage default that no longer covers exactly [members] falls back to an equal split.
     */
    fun splitFor(members: List<MemberId>): Split {
        val equal = Split.Equal(members)
        return when (kind) {
            Kind.EQUAL -> Split.Equal(members.filter { (weights[it] ?: 1L) > 0 }.ifEmpty { members })

            Kind.SHARES -> {
                val shares = members.associateWith { weights[it] ?: 1L }.filterValues { it > 0 }
                // Synced weights may overflow the share sum, which Split.Shares rejects.
                if (shares.isEmpty()) equal else runCatching { Split.Shares(shares) }.getOrDefault(equal)
            }

            Kind.PERCENTAGE -> {
                val percent = members.associateWith { weights[it] ?: -1L }
                if (percent.values.any { it < 0 } || percent.values.sum() != 100L) {
                    equal
                } else {
                    Split.Percentage(percent.filterValues { it > 0 }.mapValues { it.value.toInt() })
                }
            }
        }
    }

    companion object {
        /** The default that reproduces [split] for [members], or null for a split kind that can't be a default. */
        fun from(
            split: Split,
            members: List<MemberId>,
        ): DefaultSplit? =
            when (split) {
                is Split.Equal -> DefaultSplit(Kind.EQUAL, members.associateWith { if (it in split.participants) 1L else 0L })
                is Split.Shares -> DefaultSplit(Kind.SHARES, members.associateWith { split.shares[it] ?: 0L })
                is Split.Percentage -> DefaultSplit(Kind.PERCENTAGE, members.associateWith { (split.percent[it] ?: 0).toLong() })
                is Split.Exact, is Split.Itemized -> null
            }
    }
}
