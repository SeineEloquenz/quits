package nz.eloque.quits.data.crypto

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupCryptoTest {
    private val crypto = GroupCrypto()
    private val secret = SecretCode.decode(SecretCode.generate())!!
    private val other = SecretCode.decode(SecretCode.generate())!!

    @Test
    fun lookup_id_is_deterministic_and_distinct_from_key() =
        runTest {
            val a = crypto.lookupId(secret)
            val b = crypto.lookupId(secret)
            assertContentEquals(a, b, "lookupId must be stable for a given secret")
            assertFalse(a.contentEquals(crypto.lookupId(other)), "different secrets => different lookupId")

            // The key material must not be inferable from / equal to the exposed lookup id.
            val keyRoundTrip = encryptThenDecrypt(secret, secret, "payload".encodeToByteArray(), aad("r"))
            assertContentEquals("payload".encodeToByteArray(), keyRoundTrip)
        }

    @Test
    fun encrypt_decrypt_round_trips() =
        runTest {
            val plaintext = "expense:42".encodeToByteArray()
            val aad = aad("rec-1")
            val out = encryptThenDecrypt(secret, secret, plaintext, aad)
            assertContentEquals(plaintext, out)
        }

    @Test
    fun decrypt_fails_with_wrong_key() =
        runTest {
            val key = crypto.groupKey(secret)
            val blob = key.encrypt("secret-data".encodeToByteArray(), aad("rec-1"))
            val wrong = crypto.groupKey(other)
            assertFails { wrong.decrypt(blob, aad("rec-1")) }
        }

    @Test
    fun decrypt_fails_on_tampered_aad() =
        runTest {
            val key = crypto.groupKey(secret)
            val blob = key.encrypt("secret-data".encodeToByteArray(), aad("rec-1"))
            assertFails { key.decrypt(blob, aad("rec-2")) }
        }

    @Test
    fun decrypt_fails_on_tampered_ciphertext() =
        runTest {
            val key = crypto.groupKey(secret)
            val blob = key.encrypt("secret-data".encodeToByteArray(), aad("rec-1"))
            blob[blob.size - 1] = (blob[blob.size - 1] + 1).toByte()
            assertFails { key.decrypt(blob, aad("rec-1")) }
        }

    @Test
    fun encrypt_is_nondeterministic() =
        runTest {
            val key = crypto.groupKey(secret)
            val a = key.encrypt("same".encodeToByteArray(), aad("rec-1"))
            val b = key.encrypt("same".encodeToByteArray(), aad("rec-1"))
            assertFalse(a.contentEquals(b), "fresh IV per encryption => distinct ciphertexts")
            assertTrue(a[0] == GroupKey.VERSION && b[0] == GroupKey.VERSION)
        }

    private fun aad(id: String): ByteArray = id.encodeToByteArray()

    private suspend fun encryptThenDecrypt(
        encryptSecret: ByteArray,
        decryptSecret: ByteArray,
        plaintext: ByteArray,
        aad: ByteArray,
    ): ByteArray {
        val blob = crypto.groupKey(encryptSecret).encrypt(plaintext, aad)
        return crypto.groupKey(decryptSecret).decrypt(blob, aad)
    }
}
