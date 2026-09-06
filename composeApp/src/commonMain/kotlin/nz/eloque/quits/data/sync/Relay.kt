package nz.eloque.quits.data.sync

import kotlin.time.Duration

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
 * What an instance publishes about itself, before a client holds any group token.
 *
 * `0` means unlimited for the record limits and disabled for the retention windows.
 */
data class RelayInfo(
    val maxBodyBytes: Long,
    val maxRecordBytes: Long,
    val maxRecordsPerGroup: Long,
    val emptyGroupTtl: Duration = Duration.ZERO,
    val inactiveGroupTtl: Duration = Duration.ZERO,
    val requiresInstanceSecret: Boolean = false,
    val fromRelay: Boolean = false,
) {
    companion object {
        /** Assumed for a relay that does not publish `/v1/info` yet, or could not be asked. */
        val CONSERVATIVE = RelayInfo(maxBodyBytes = 256L * 1024, maxRecordBytes = 0, maxRecordsPerGroup = 0)

        /** Info as reported by a relay. */
        fun published(
            maxBodyBytes: Long,
            maxRecordBytes: Long,
            maxRecordsPerGroup: Long,
            emptyGroupTtl: Duration = Duration.ZERO,
            inactiveGroupTtl: Duration = Duration.ZERO,
            requiresInstanceSecret: Boolean = false,
        ): RelayInfo =
            RelayInfo(
                maxBodyBytes = maxBodyBytes.takeIf { it > 0 } ?: CONSERVATIVE.maxBodyBytes,
                maxRecordBytes = maxRecordBytes,
                maxRecordsPerGroup = maxRecordsPerGroup,
                emptyGroupTtl = emptyGroupTtl,
                inactiveGroupTtl = inactiveGroupTtl,
                requiresInstanceSecret = requiresInstanceSecret,
                fromRelay = maxBodyBytes > 0,
            )
    }
}

interface Relay {
    /** What the relay publishes about itself, or [RelayInfo.CONSERVATIVE] if it does not say. */
    suspend fun info(): RelayInfo

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
