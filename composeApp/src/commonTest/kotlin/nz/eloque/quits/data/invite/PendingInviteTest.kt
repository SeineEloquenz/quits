package nz.eloque.quits.data.invite

import nz.eloque.quits.data.crypto.SecretCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PendingInviteTest {
    @Test
    fun offer_sets_code_from_full_link() {
        val code = SecretCode.generate()
        val pending = PendingInvite()
        pending.offer("https://quits.eloque.nz/join#$code")
        assertEquals(code, pending.code.value)
    }

    @Test
    fun offer_ignores_non_invite_url() {
        val pending = PendingInvite()
        pending.offer("https://quits.eloque.nz/")
        assertNull(pending.code.value)
    }
}
