package nz.eloque.quits.data.db

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * The sync handle for a local group: the relay-assigned [remoteId], the share [code], the bearer
 * [token], and [lastSeq] (highest `server_seq` already pulled). Absent for local-only groups.
 */
@Entity(tableName = "group_sync")
data class GroupSyncEntity(
    @PrimaryKey val groupId: String,
    val remoteId: String,
    val code: String,
    val token: String,
    val lastSeq: Long = 0,
    /** When this device last completed a sync of the group (epoch millis); 0 = never. */
    val lastSyncedAt: Long = 0,
)
