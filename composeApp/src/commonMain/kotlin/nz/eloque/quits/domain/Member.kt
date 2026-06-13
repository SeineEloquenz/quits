package nz.eloque.quits.domain

class Member(
    override val id: MemberId,
    val name: String,
) : Entity<MemberId>()
