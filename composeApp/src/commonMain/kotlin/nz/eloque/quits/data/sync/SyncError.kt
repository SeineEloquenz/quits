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

    /** Any other status we have no specific meaning for. */
    class Unexpected(
        val status: Int,
    ) : SyncError("unexpected status $status") {
        override val retriable = false
    }
}

/** Maps an HTTP status (plus any hints already parsed from the response) to a [SyncError]. */
internal fun syncErrorForStatus(
    status: Int,
    retryAfter: Duration?,
    serverMessage: String?,
): SyncError =
    when (status) {
        400 -> SyncError.BadRequest(serverMessage)
        401, 403 -> SyncError.Unauthorized
        404 -> SyncError.GroupGone
        429 -> SyncError.RateLimited(retryAfter)
        503 -> SyncError.ServerUnavailable(retryAfter)
        in 500..599 -> SyncError.ServerError(status)
        else -> SyncError.Unexpected(status)
    }
