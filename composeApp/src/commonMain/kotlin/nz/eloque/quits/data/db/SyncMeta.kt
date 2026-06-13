package nz.eloque.quits.data.db

/**
 * Sync bookkeeping embedded into every syncable row. [dirty] marks a local change not yet pushed;
 * [deleted] is a tombstone; [updatedAt]/[deviceId] drive last-write-wins against the relay.
 */
data class SyncMeta(
    val updatedAt: Long,
    val deviceId: String,
    val deleted: Boolean = false,
    val dirty: Boolean = true,
)
