package nz.eloque.quits.data.invite

import nz.eloque.quits.data.crypto.SecretCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class InviteLinkRoundTripTest {
    @Test
    fun share_link_parses_back() {
        repeat(50) {
            val code = SecretCode.generate()
            val link = InviteLink.build(code)
            val parsed = InviteLink.parse(link)
            assertNotNull(parsed, "parse failed for link=$link")
            assertEquals(code, parsed)
        }
    }

    @Test
    fun android_intent_uri_tostring_parses() {
        val code = SecretCode.generate()
        val fromIntent = "https://quits.eloque.nz/join#$code"
        assertNotNull(InviteLink.parse(fromIntent), "intent form failed for $code")
    }
}
