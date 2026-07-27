package nz.eloque.quits.data.invite

import nz.eloque.quits.data.crypto.SecretCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InviteLinkTest {
    @Test
    fun build_then_parse_round_trips() {
        repeat(50) {
            val code = SecretCode.generate()
            val url = InviteLink.build(code)
            assertEquals(code, InviteLink.parse(url))
        }
    }

    @Test
    fun build_puts_the_secret_in_the_fragment() {
        val code = SecretCode.encode(ByteArray(16))
        assertEquals("https://quits.eloque.nz/join#$code", InviteLink.build(code))
    }

    @Test
    fun parse_tolerates_messy_codes_like_secretcode_does() {
        val code = SecretCode.generate()
        // Lowercased, spaced, and with an O/0 confusable — SecretCode.decode still resolves it.
        val messy = code.lowercase().replace("-", " ")
        assertEquals(messy, InviteLink.parse("https://quits.eloque.nz/join#$messy"))
    }

    @Test
    fun parse_rejects_non_invite_urls() {
        assertNull(InviteLink.parse("https://quits.eloque.nz/join"))
        assertNull(InviteLink.parse("https://quits.eloque.nz/join#not-a-valid-code"))
        assertNull(InviteLink.parse("https://example.com/"))
        assertNull(InviteLink.parse(""))
    }

    @Test
    fun parse_accepts_the_web_apps_redirected_form() {
        val code = SecretCode.generate()
        // The /join landing forwards to app.quits.eloque.nz/#code; that must parse too.
        assertEquals(code, InviteLink.parse("https://app.quits.eloque.nz/#$code"))
    }
}
