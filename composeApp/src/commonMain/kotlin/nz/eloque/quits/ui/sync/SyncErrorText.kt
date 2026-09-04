package nz.eloque.quits.ui.sync

import nz.eloque.quits.data.sync.SyncError
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.error_sync_group_full
import nz.eloque.quits.resources.error_sync_group_gone
import nz.eloque.quits.resources.error_sync_incompatible
import nz.eloque.quits.resources.error_sync_rate_limited
import nz.eloque.quits.resources.error_sync_rate_limited_wait
import nz.eloque.quits.resources.error_sync_record_too_large
import nz.eloque.quits.resources.error_sync_rejected
import nz.eloque.quits.resources.error_sync_server_busy
import nz.eloque.quits.resources.error_sync_server_error
import nz.eloque.quits.resources.error_sync_unauthorized
import nz.eloque.quits.resources.error_sync_unexpected
import nz.eloque.quits.resources.error_sync_unreachable
import org.jetbrains.compose.resources.getString

suspend fun SyncError.toUserMessage(): String =
    when (this) {
        is SyncError.Unreachable -> getString(Res.string.error_sync_unreachable)
        is SyncError.RateLimited ->
            retryAfter?.inWholeSeconds?.coerceAtLeast(1)?.let {
                getString(Res.string.error_sync_rate_limited_wait, it.toInt())
            } ?: getString(Res.string.error_sync_rate_limited)
        is SyncError.ServerUnavailable -> getString(Res.string.error_sync_server_busy)
        is SyncError.ServerError -> getString(Res.string.error_sync_server_error)
        SyncError.Unauthorized -> getString(Res.string.error_sync_unauthorized)
        SyncError.GroupGone -> getString(Res.string.error_sync_group_gone)
        SyncError.GroupFull -> getString(Res.string.error_sync_group_full)
        is SyncError.RecordTooLarge -> getString(Res.string.error_sync_record_too_large)
        is SyncError.BadRequest -> getString(Res.string.error_sync_rejected)
        is SyncError.Protocol -> getString(Res.string.error_sync_incompatible)
        is SyncError.Unexpected -> getString(Res.string.error_sync_unexpected)
    }

suspend fun Throwable.toSyncMessage(): String = (this as? SyncError)?.toUserMessage() ?: getString(Res.string.error_sync_unexpected)
