package nz.eloque.quits.data.sync

/** UI-facing sync state for a group: its share [code] and when it was [lastSyncedAt] (null if never). */
data class SyncInfo(
    val code: String?,
    val lastSyncedAt: Long?,
)

/**
 * How much of the relay's per-group record allowance this group occupies.
 *
 * Only meaningful for a shared group on a relay that caps records; absent otherwise.
 */
data class GroupUsage(
    val stored: Int,
    val limit: Long,
) {
    /** 0..1, saturating — the relay refuses new records at 1. */
    val fraction: Float get() = (stored.toFloat() / limit.toFloat()).coerceIn(0f, 1f)

    /** Past halfway, so the remaining headroom is worth reporting rather than noise. */
    val filling: Boolean get() = fraction >= 0.5f

    /**
     * Roughly how many more entries fit.
     *
     * An entry is exactly one record, so this is exact until members, categories or settlements are
     * added too — which is why it is presented to the user as an approximation.
     */
    val remainingEntries: Int get() = (limit - stored).coerceAtLeast(0).toInt()
}
