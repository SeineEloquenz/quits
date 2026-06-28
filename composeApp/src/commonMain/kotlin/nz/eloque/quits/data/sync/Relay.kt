package nz.eloque.quits.data.sync

/** A group's sync handle as known to the relay. */
data class GroupHandle(
    val remoteId: String,
    val code: String,
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

interface Relay {
    suspend fun createGroup(): GroupHandle

    /** Returns the handle for [code], or null if no such group exists. */
    suspend fun joinGroup(code: String): GroupHandle?

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
