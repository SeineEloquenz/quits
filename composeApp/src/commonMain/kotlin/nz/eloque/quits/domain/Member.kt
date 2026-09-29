package nz.eloque.quits.domain

/** A person taking part in a [Group] or a [QuickSplit]. */
class Member(
    override val id: MemberId,
    val name: String,
) : Entity<MemberId>()
