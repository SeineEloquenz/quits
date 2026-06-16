package nz.eloque.quits.data.sync

/** UI-facing sync state for a group: its share [code] and when it was [lastSyncedAt] (null if never). */
data class SyncInfo(
    val code: String?,
    val lastSyncedAt: Long?,
)
