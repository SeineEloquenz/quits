package nz.eloque.quits.data.crypto

import dev.whyoleg.cryptography.BinarySize
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256

/**
 * Symmetric group cryptography for E2EE. Everything a group needs flows from one shared 128-bit
 * secret (see [SecretCode]): HKDF-SHA256 derives a relay [lookupId] (safe to hand the server) and a
 * separate AES-256 [groupKey] (never leaves the device). Domain-separating `info` labels keep the
 * two outputs independent, so exposing the lookup id tells the relay nothing about the key.
 *
 * Backed by cryptography-kotlin's optimal provider: JDK on Android, CryptoKit on iOS, WebCrypto on
 * the web. All operations are `suspend` because WebCrypto is async.
 */
class GroupCrypto(
    provider: CryptographyProvider = CryptographyProvider.Default,
) {
    private val aes = provider.get(AES.GCM)
    private val hkdf = provider.get(HKDF)

    /** Relay lookup id for [secret]; preimage resistance keeps the secret/key hidden from the server. */
    suspend fun lookupId(secret: ByteArray): ByteArray = derive(secret, LOOKUP_INFO, LOOKUP_LEN)

    /** Imports the AES-256-GCM group key derived from [secret]. */
    suspend fun groupKey(secret: ByteArray): GroupKey {
        val raw = derive(secret, KEY_INFO, KEY_LEN)
        return GroupKey(aes.keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, raw))
    }

    private suspend fun derive(
        secret: ByteArray,
        info: ByteArray,
        len: Int,
    ): ByteArray =
        hkdf
            .secretDerivation(
                digest = SHA256,
                outputSize = with(BinarySize.Companion) { len.bytes },
                salt = EMPTY,
                info = info,
            ).deriveSecretToByteArray(secret)

    private companion object {
        val EMPTY = ByteArray(0)
        val LOOKUP_INFO = "quits/lookup/v1".encodeToByteArray()
        val KEY_INFO = "quits/key/v1".encodeToByteArray()
        const val LOOKUP_LEN = 16
        const val KEY_LEN = 32
    }
}

/**
 * A group's AES-256-GCM key. [encrypt] seals a payload into a versioned blob (`version || iv ||
 * ciphertext || tag`); [decrypt] reverses it. The envelope is bound as associated data so the relay
 * can't move a ciphertext to another record or rewrite its last-write-wins metadata undetected.
 */
class GroupKey internal constructor(
    key: AES.GCM.Key,
) {
    private val cipher = key.cipher()

    suspend fun encrypt(
        plaintext: ByteArray,
        aad: ByteArray,
    ): ByteArray = byteArrayOf(VERSION) + cipher.encrypt(plaintext = plaintext, associatedData = aad)

    suspend fun decrypt(
        blob: ByteArray,
        aad: ByteArray,
    ): ByteArray {
        require(blob.isNotEmpty() && blob[0] == VERSION) { "unsupported payload version" }
        return cipher.decrypt(ciphertext = blob.copyOfRange(1, blob.size), associatedData = aad)
    }

    companion object {
        /** Payload format version; bump on any change to the blob layout. */
        const val VERSION: Byte = 1
    }
}
