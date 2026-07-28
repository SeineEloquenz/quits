package nz.eloque.quits.data.sync

/** A group's sync handle as known to the relay. */
data class GroupHandle(
    val remoteId: String,
    val token: String,
)

data class PushResult(
    val seq: Long,
    val applied: List<String>,
    val rejected: List<String>,
)

data class PullResult(
    val records: List<EncryptedRecord>,
    val seq: Long,
)

/**
 * The relay answered, but not with success. [message] carries the relay's own error text (its
 * `{"error": …}` body) when present, so the cause surfaces instead of a misleading
 * "missing fields" deserialization failure.
 */
class RelayException(
    val status: Int,
    message: String,
) : Exception(message)

interface Relay {
    suspend fun createGroup(lookupId: String): GroupHandle

    /** Returns the handle for [lookupId], or null if no such group exists. */
    suspend fun joinGroup(lookupId: String): GroupHandle?

    suspend fun push(
        remoteId: String,
        token: String,
        records: List<EncryptedRecord>,
    ): PushResult

    suspend fun pull(
        remoteId: String,
        token: String,
        since: Long,
    ): PullResult
}
