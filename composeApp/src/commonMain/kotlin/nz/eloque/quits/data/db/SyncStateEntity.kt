package nz.eloque.quits.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Per-group sync cursor: the highest `server_seq` we have already pulled from the relay. */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val groupId: String,
    val lastSeq: Long,
)
