package nz.eloque.quits.data.db

import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Query
import androidx.room3.Relation
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

/** A groups-list row: the group's synced identity plus its local [archived] preference (false if none). */
data class GroupListRow(
    val id: String,
    val name: String,
    val baseCurrency: String,
    val archived: Boolean,
)

@Dao
interface GroupDao {
    @Upsert
    suspend fun upsert(group: GroupEntity)

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun byId(id: String): GroupEntity?

    @Query("SELECT * FROM groups WHERE deleted = 0")
    suspend fun all(): List<GroupEntity>

    @Query(
        "SELECT g.id, g.name, g.baseCurrency, COALESCE(p.archived, 0) AS archived " +
            "FROM groups g LEFT JOIN group_prefs p ON p.groupId = g.id WHERE g.deleted = 0",
    )
    fun summariesFlow(): Flow<List<GroupListRow>>

    @Query("SELECT * FROM groups WHERE id = :id")
    fun byIdFlow(id: String): Flow<GroupEntity?>

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun delete(id: String)

    /** Clears dirty only if the row is unchanged since it was pushed, so a concurrent edit survives. */
    @Query("UPDATE groups SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt AND deviceId = :deviceId")
    suspend fun clearDirty(
        id: String,
        updatedAt: Long,
        deviceId: String,
    )
}

@Dao
interface GroupPrefsDao {
    @Query("SELECT archived FROM group_prefs WHERE groupId = :groupId")
    fun archivedFlow(groupId: String): Flow<Boolean?>

    @Query(
        "INSERT INTO group_prefs (groupId, archived) VALUES (:groupId, :archived) " +
            "ON CONFLICT(groupId) DO UPDATE SET archived = :archived",
    )
    suspend fun setArchived(
        groupId: String,
        archived: Boolean,
    )
}

@Dao
interface MemberDao {
    @Upsert
    suspend fun upsert(members: List<MemberEntity>)

    @Query("SELECT * FROM member WHERE groupId = :groupId AND deleted = 0")
    suspend fun forGroup(groupId: String): List<MemberEntity>

    @Query("SELECT * FROM member WHERE groupId = :groupId AND deleted = 0")
    fun forGroupFlow(groupId: String): Flow<List<MemberEntity>>

    /** Includes tombstoned members; callers keep only those still referenced by live records. */
    @Query("SELECT * FROM member WHERE groupId = :groupId")
    suspend fun forGroupWithDeleted(groupId: String): List<MemberEntity>

    @Query("SELECT * FROM member WHERE groupId = :groupId")
    fun forGroupWithDeletedFlow(groupId: String): Flow<List<MemberEntity>>

    @Query("SELECT * FROM member WHERE id = :id")
    suspend fun byId(id: String): MemberEntity?

    @Query("SELECT * FROM member WHERE groupId = :groupId AND dirty = 1")
    suspend fun dirty(groupId: String): List<MemberEntity>

    /** Clears dirty only if the row is unchanged since it was pushed, so a concurrent edit survives. */
    @Query("UPDATE member SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt AND deviceId = :deviceId")
    suspend fun clearDirty(
        id: String,
        updatedAt: Long,
        deviceId: String,
    )

    @Query("UPDATE member SET deleted = 1, dirty = 1, updatedAt = :updatedAt, deviceId = :deviceId WHERE id = :id")
    suspend fun tombstone(
        id: String,
        updatedAt: Long,
        deviceId: String,
    )
}

data class ItemWithParticipants(
    @Embedded val item: EntryItemEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["itemId"])
    val participants: List<EntryItemParticipantEntity>,
)

data class EntryWithLines(
    @Embedded val entry: EntryEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["entryId"])
    val payers: List<EntryPayerEntity>,
    @Relation(parentColumns = ["id"], entityColumns = ["entryId"])
    val splits: List<EntrySplitEntity>,
    @Relation(entity = EntryItemEntity::class, parentColumns = ["id"], entityColumns = ["entryId"])
    val items: List<ItemWithParticipants>,
)

@Dao
interface EntryDao {
    @Upsert
    suspend fun upsertEntry(entry: EntryEntity)

    @Upsert
    suspend fun upsertPayers(payers: List<EntryPayerEntity>)

    @Upsert
    suspend fun upsertSplits(splits: List<EntrySplitEntity>)

    @Upsert
    suspend fun upsertItems(items: List<EntryItemEntity>)

    @Upsert
    suspend fun upsertItemParticipants(participants: List<EntryItemParticipantEntity>)

    @Query("DELETE FROM entry_payer WHERE entryId = :entryId")
    suspend fun clearPayers(entryId: String)

    @Query("DELETE FROM entry_split WHERE entryId = :entryId")
    suspend fun clearSplits(entryId: String)

    /** Cascades to entry_item_participant via the item foreign key. */
    @Query("DELETE FROM entry_item WHERE entryId = :entryId")
    suspend fun clearItems(entryId: String)

    @Transaction
    suspend fun save(
        entry: EntryEntity,
        payers: List<EntryPayerEntity>,
        splits: List<EntrySplitEntity>,
        items: List<EntryItemEntity>,
        itemParticipants: List<EntryItemParticipantEntity>,
    ) {
        upsertEntry(entry)
        clearPayers(entry.id)
        clearSplits(entry.id)
        clearItems(entry.id)
        upsertPayers(payers)
        upsertSplits(splits)
        upsertItems(items)
        upsertItemParticipants(itemParticipants)
    }

    @Transaction
    @Query("SELECT * FROM entry WHERE groupId = :groupId AND deleted = 0")
    suspend fun forGroup(groupId: String): List<EntryWithLines>

    @Transaction
    @Query("SELECT * FROM entry WHERE groupId = :groupId AND deleted = 0")
    fun forGroupFlow(groupId: String): Flow<List<EntryWithLines>>

    @Transaction
    @Query("SELECT * FROM entry WHERE id = :id")
    suspend fun byId(id: String): EntryWithLines?

    @Transaction
    @Query("SELECT * FROM entry WHERE groupId = :groupId AND dirty = 1")
    suspend fun dirty(groupId: String): List<EntryWithLines>

    /** Clears dirty only if the row is unchanged since it was pushed, so a concurrent edit survives. */
    @Query("UPDATE entry SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt AND deviceId = :deviceId")
    suspend fun clearDirty(
        id: String,
        updatedAt: Long,
        deviceId: String,
    )

    @Query("UPDATE entry SET deleted = 1, dirty = 1, updatedAt = :updatedAt, deviceId = :deviceId WHERE id = :id")
    suspend fun tombstone(
        id: String,
        updatedAt: Long,
        deviceId: String,
    )
}

@Dao
interface SettlementDao {
    @Upsert
    suspend fun upsert(settlement: SettlementEntity)

    @Query("SELECT * FROM settlement WHERE groupId = :groupId AND deleted = 0")
    suspend fun forGroup(groupId: String): List<SettlementEntity>

    @Query("SELECT * FROM settlement WHERE groupId = :groupId AND deleted = 0")
    fun forGroupFlow(groupId: String): Flow<List<SettlementEntity>>

    @Query("SELECT * FROM settlement WHERE id = :id")
    suspend fun byId(id: String): SettlementEntity?

    @Query("SELECT * FROM settlement WHERE groupId = :groupId AND dirty = 1")
    suspend fun dirty(groupId: String): List<SettlementEntity>

    /** Clears dirty only if the row is unchanged since it was pushed, so a concurrent edit survives. */
    @Query("UPDATE settlement SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt AND deviceId = :deviceId")
    suspend fun clearDirty(
        id: String,
        updatedAt: Long,
        deviceId: String,
    )

    @Query("UPDATE settlement SET deleted = 1, dirty = 1, updatedAt = :updatedAt, deviceId = :deviceId WHERE id = :id")
    suspend fun tombstone(
        id: String,
        updatedAt: Long,
        deviceId: String,
    )
}

@Dao
interface CategoryDao {
    @Upsert
    suspend fun upsert(category: CategoryEntity)

    @Query("SELECT * FROM category WHERE groupId = :groupId AND deleted = 0")
    suspend fun forGroup(groupId: String): List<CategoryEntity>

    @Query("SELECT * FROM category WHERE groupId = :groupId AND deleted = 0")
    fun forGroupFlow(groupId: String): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM category WHERE id = :id")
    suspend fun byId(id: String): CategoryEntity?

    @Query("SELECT * FROM category WHERE groupId = :groupId AND dirty = 1")
    suspend fun dirty(groupId: String): List<CategoryEntity>

    /** Clears dirty only if the row is unchanged since it was pushed, so a concurrent edit survives. */
    @Query("UPDATE category SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt AND deviceId = :deviceId")
    suspend fun clearDirty(
        id: String,
        updatedAt: Long,
        deviceId: String,
    )

    @Query("UPDATE category SET deleted = 1, dirty = 1, updatedAt = :updatedAt, deviceId = :deviceId WHERE id = :id")
    suspend fun tombstone(
        id: String,
        updatedAt: Long,
        deviceId: String,
    )
}

@Dao
interface FxRateDao {
    @Upsert
    suspend fun put(rate: FxRateEntity)

    @Query("SELECT * FROM fx_rate WHERE base = :base AND quote = :quote")
    suspend fun get(
        base: String,
        quote: String,
    ): FxRateEntity?
}
