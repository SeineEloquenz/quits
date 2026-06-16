package nz.eloque.quits.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupSyncDao {
    @Upsert
    suspend fun put(handle: GroupSyncEntity)

    @Query("SELECT * FROM group_sync WHERE groupId = :groupId")
    suspend fun byGroup(groupId: String): GroupSyncEntity?

    @Query("SELECT * FROM group_sync")
    suspend fun all(): List<GroupSyncEntity>

    @Query("SELECT * FROM group_sync WHERE groupId = :groupId")
    fun byGroupFlow(groupId: String): Flow<GroupSyncEntity?>

    @Query("UPDATE group_sync SET lastSeq = :lastSeq WHERE groupId = :groupId")
    suspend fun setLastSeq(
        groupId: String,
        lastSeq: Long,
    )

    @Query("UPDATE group_sync SET lastSyncedAt = :timestamp WHERE groupId = :groupId")
    suspend fun setLastSyncedAt(
        groupId: String,
        timestamp: Long,
    )
}
