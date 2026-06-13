package nz.eloque.quits.domain

/**
 * Base for domain entities: identity and equality are defined by [id], not by attribute values
 * (two loads of the same expense are equal even if a field changed).
 */
abstract class Entity<out ID : Any> {
    abstract val id: ID

    final override fun equals(other: Any?): Boolean =
        this === other || (other is Entity<*> && this::class == other::class && id == other.id)

    final override fun hashCode(): Int = id.hashCode()
}
