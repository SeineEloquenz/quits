package nz.eloque.quits.data.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SecretCodeTest {
    @Test
    fun generate_round_trips() {
        repeat(50) {
            val code = SecretCode.generate()
            val secret = SecretCode.decode(code)
            assertTrue(secret != null && secret.size == 16, "expected a 16-byte secret from $code")
            // Re-encoding the decoded secret yields the same canonical code.
            assertEquals(code, SecretCode.encode(secret))
        }
    }

    @Test
    fun encoded_shape_is_grouped_base32() {
        val code = SecretCode.encode(ByteArray(16))
        assertEquals("0000-0000-0000-0000-0000-0000-00", code)
    }

    @Test
    fun decode_is_lenient_about_case_separators_and_confusables() {
        val secret = SecretCode.decode(SecretCode.generate())!!
        val canonical = SecretCode.encode(secret)
        // Lowercase, spaces instead of hyphens, stray whitespace all resolve to the same secret.
        val messy = canonical.lowercase().replace("-", " ")
        assertContentEquals(secret, SecretCode.decode(" $messy ".trim()))

        // I/L fold to 1 and O folds to 0.
        val withConfusables = SecretCode.encode(ByteArray(16)).replace('0', 'O')
        assertContentEquals(ByteArray(16), SecretCode.decode(withConfusables))
    }

    @Test
    fun decode_rejects_wrong_length_and_bad_chars() {
        assertNull(SecretCode.decode(""))
        assertNull(SecretCode.decode("ABCD"))
        // 'U' is not in the Crockford alphabet.
        assertNull(SecretCode.decode("UUUU-UUUU-UUUU-UUUU-UUUU-UUUU-UU"))
    }
}
