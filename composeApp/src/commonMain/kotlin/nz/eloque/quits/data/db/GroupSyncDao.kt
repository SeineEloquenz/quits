package nz.eloque.quits.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface GroupSyncDao {
    @Upsert
    suspend fun put(handle: GroupSyncEntity)

    @Query("SELECT * FROM group_sync WHERE groupId = :groupId")
    suspend fun byGroup(groupId: String): GroupSyncEntity?

    @Query("UPDATE group_sync SET lastSeq = :lastSeq WHERE groupId = :groupId")
    suspend fun setLastSeq(
        groupId: String,
        lastSeq: Long,
    )
}
