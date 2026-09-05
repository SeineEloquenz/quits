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
 * The relay's request and storage limits. `0` means unlimited, as it does in the relay's own
 * configuration.
 */
data class RelayLimits(
    val maxBodyBytes: Long,
    val maxRecordBytes: Long,
    val maxRecordsPerGroup: Long,
) {
    companion object {
        /**
         * Assumed for a relay that does not publish `/v1/limits` yet.
         */
        val CONSERVATIVE = RelayLimits(maxBodyBytes = 256L * 1024, maxRecordBytes = 0, maxRecordsPerGroup = 0)
    }
}

interface Relay {
    /** The relay's limits, or [RelayLimits.CONSERVATIVE] if it does not publish them. */
    suspend fun limits(): RelayLimits

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
