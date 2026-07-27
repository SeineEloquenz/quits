package nz.eloque.quits.util

/** Hands [text] (e.g. an invite link) to the platform's native share affordance. */
interface Sharer {
    fun share(text: String)
}
