package nz.eloque.quits.data.sync

import kotlin.time.Duration

sealed class SyncError(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    /** Whether re-attempting later stands a chance of succeeding. */
    abstract val retriable: Boolean

    /** How long the server asked us to wait before retrying, if it said. */
    open val retryAfter: Duration? = null

    /** The relay was never reached: DNS, refused connection, TLS, or timeout. */
    class Unreachable(
        cause: Throwable?,
    ) : SyncError("relay unreachable", cause) {
        override val retriable = true
    }

    /** Rate limited by the relay (HTTP 429). */
    class RateLimited(
        override val retryAfter: Duration?,
    ) : SyncError("rate limited") {
        override val retriable = true
    }

    /** The relay is reachable but refusing work — at capacity or in maintenance (HTTP 503). */
    class ServerUnavailable(
        override val retryAfter: Duration?,
    ) : SyncError("server unavailable") {
        override val retriable = true
    }

    /** An unexpected server-side failure (HTTP 5xx other than 503). */
    class ServerError(
        val status: Int,
    ) : SyncError("server error $status") {
        override val retriable = true
    }

    /** The relay rejected our credentials for this group (HTTP 401/403). */
    data object Unauthorized : SyncError("unauthorized") {
        override val retriable = false
    }

    /** The group no longer exists on the relay — deleted or reaped (HTTP 404). */
    data object GroupGone : SyncError("group gone") {
        override val retriable = false
    }

    /** The relay rejected the request as malformed or incompatible (HTTP 400). */
    class BadRequest(
        val detail: String?,
    ) : SyncError("bad request" + (detail?.let { ": $it" } ?: "")) {
        override val retriable = false
    }

    /** A 2xx response whose body we couldn't parse — a protocol/version mismatch. */
    class Protocol(
        cause: Throwable?,
    ) : SyncError("unparseable relay response", cause) {
        override val retriable = false
    }

    /**
     * The relay refused records whose payload exceeds its per-record limit (HTTP 413).
     *
     * The whole push was refused, so nothing was stored. Retrying is pointless until the offending
     * entries shrink.
     */
    class RecordTooLarge(
        val recordIds: List<String>,
    ) : SyncError("record too large" + recordIds.takeIf { it.isNotEmpty() }?.let { ": $it" }.orEmpty()) {
        override val retriable = false
    }

    /**
     * The relay is already holding as many groups as it will store (HTTP 507 from group creation).
     *
     * Retriable, since the relay's reaper frees empty and inactive groups on its own.
     */
    data object RelayFull : SyncError("relay full") {
        override val retriable = true
    }

    /**
     * The push body as a whole exceeded the relay's request limit (HTTP 413 with no record ids).
     */
    data object BatchTooLarge : SyncError("batch too large") {
        override val retriable = false
    }

    /** The group has reached the relay's per-group record limit (HTTP 507). */
    data object GroupFull : SyncError("group full") {
        override val retriable = false
    }

    /** The relay no longer serves this app version (HTTP 426).*/
    class ClientTooOld(
        val minVersion: String,
    ) : SyncError("client too old, relay needs $minVersion") {
        override val retriable = false
    }

    /** Any other status we have no specific meaning for. */
    class Unexpected(
        val status: Int,
    ) : SyncError("unexpected status $status") {
        override val retriable = false
    }
}

/** Which call produced a response, for statuses whose meaning depends on the endpoint. */
internal enum class RelayOperation { CreateGroup, JoinGroup, Push, Pull, Info }

/** Maps an HTTP status (plus any hints already parsed from the response) to a [SyncError]. */
internal fun syncErrorForStatus(
    status: Int,
    retryAfter: Duration?,
    serverMessage: String?,
    recordIds: List<String>,
    minVersion: String?,
    operation: RelayOperation,
): SyncError =
    when (status) {
        400 -> SyncError.BadRequest(serverMessage)
        401, 403 -> SyncError.Unauthorized
        404 -> SyncError.GroupGone
        // A record-level 413 names the offenders. Without ids it is the body limit, which only a
        // push can reach. Anywhere else it is a proxy refusing a request that carries no records.
        413 ->
            when {
                recordIds.isNotEmpty() -> SyncError.RecordTooLarge(recordIds)
                operation == RelayOperation.Push -> SyncError.BatchTooLarge
                else -> SyncError.Unexpected(status)
            }

        426 -> minVersion?.let { SyncError.ClientTooOld(it) } ?: SyncError.Unexpected(status)
        429 -> SyncError.RateLimited(retryAfter)
        503 -> SyncError.ServerUnavailable(retryAfter)
        // Insufficient storage means the instance cannot hold another group when creating one, and
        // that this group is at its record limit anywhere else.
        507 -> if (operation == RelayOperation.CreateGroup) SyncError.RelayFull else SyncError.GroupFull
        in 500..599 -> SyncError.ServerError(status)
        else -> SyncError.Unexpected(status)
    }
