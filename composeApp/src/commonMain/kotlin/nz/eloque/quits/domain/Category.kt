package nz.eloque.quits.domain

/**
 * A user-defined, group-scoped entry category. Built-in preset categories are app-defined and live
 * outside the domain (they need no sync); only custom categories a user creates are [Category]
 * instances synced with the group. [icon] is a stable icon key resolved to a drawable by the UI;
 * [color] is packed ARGB.
 */
class Category(
    override val id: CategoryId,
    val name: String,
    val icon: String,
    val color: Long,
) : Entity<CategoryId>()
