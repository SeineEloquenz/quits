package nz.eloque.quits.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EntityTest {
    @Test
    fun entities_are_equal_by_identity_not_attributes() {
        assertEquals(Member(mid("a"), "Alice"), Member(mid("a"), "Alicia"))
        assertTrue(Member(mid("a"), "A") != Member(mid("b"), "A"))
    }
}
