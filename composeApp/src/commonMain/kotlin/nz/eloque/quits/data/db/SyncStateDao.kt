package nz.eloque.quits.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SyncStateDao {
    @Upsert
    suspend fun put(state: SyncStateEntity)

    @Query("SELECT lastSeq FROM sync_state WHERE groupId = :groupId")
    suspend fun lastSeq(groupId: String): Long?
}
