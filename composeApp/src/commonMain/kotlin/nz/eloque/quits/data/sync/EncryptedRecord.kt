package nz.eloque.quits.data.sync

/** The opaque record the relay transports: the sync envelope plus a ciphertext blob it never reads. */
class EncryptedRecord(
    val id: String,
    val updatedAt: Long,
    val deviceId: String,
    val deleted: Boolean,
    val ciphertext: ByteArray,
)
