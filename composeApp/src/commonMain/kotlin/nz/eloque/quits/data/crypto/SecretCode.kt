package nz.eloque.quits.data.crypto

import dev.whyoleg.cryptography.random.CryptographyRandom

/**
 * The human-shareable group secret: 128 bits of CSPRNG entropy, Crockford base32-encoded and
 * grouped for readability (e.g. `K7Q2-M9XR-...`). It is the sole capability for a group — it derives
 * both the relay lookup id and the AES key (see [GroupCrypto]) — so it must never reach the server.
 *
 * Decoding is lenient: case-insensitive, hyphens/spaces ignored, and the confusable letters I/L/O
 * folded to 1/1/0, so a code the user reads aloud or mistypes slightly still resolves.
 */
object SecretCode {
    // Crockford base32: 0-9 A-Z minus I, L, O, U. 32 symbols, chosen to be unambiguous when read.
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private const val SECRET_BYTES = 16
    private const val ENCODED_LEN = 26 // ceil(128 / 5)
    private const val GROUP = 4

    /** A fresh random secret, encoded for display/sharing. */
    fun generate(): String = encode(CryptographyRandom.nextBytes(SECRET_BYTES))

    /** Encodes raw secret [bytes] as a grouped Crockford base32 string. */
    fun encode(bytes: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bits += 8
            while (bits >= 5) {
                bits -= 5
                sb.append(ALPHABET[(buffer ushr bits) and 0x1F])
            }
        }
        if (bits > 0) sb.append(ALPHABET[(buffer shl (5 - bits)) and 0x1F])
        return sb.toString().chunked(GROUP).joinToString("-")
    }

    /** Decodes a user-entered [code] back to the raw secret, or null if malformed. */
    fun decode(code: String): ByteArray? {
        val normalized =
            buildString {
                for (c in code) {
                    when (c) {
                        '-', ' ' -> Unit
                        'i', 'I', 'l', 'L' -> append('1')
                        'o', 'O' -> append('0')
                        else -> append(c.uppercaseChar())
                    }
                }
            }
        if (normalized.length != ENCODED_LEN) return null

        val out = ByteArray(SECRET_BYTES)
        var index = 0
        var buffer = 0
        var bits = 0
        for (c in normalized) {
            val v = ALPHABET.indexOf(c)
            if (v < 0) return null
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out[index++] = ((buffer ushr bits) and 0xFF).toByte()
            }
        }
        return if (index == SECRET_BYTES) out else null
    }
}
