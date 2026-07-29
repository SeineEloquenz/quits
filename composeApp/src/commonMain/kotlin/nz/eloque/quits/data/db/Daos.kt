package nz.eloque.quits.data.db

import androidx.room3.Dao
import androidx.room3.Embedded
import androidx.room3.Query
import androidx.room3.Relation
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Upsert
    suspend fun upsert(group: GroupEntity)

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun byId(id: String): GroupEntity?

    @Query("SELECT * FROM groups WHERE deleted = 0")
    suspend fun all(): List<GroupEntity>

    @Query("SELECT * FROM groups WHERE deleted = 0")
    fun allFlow(): Flow<List<GroupEntity>>

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
    @Embedded val item: ExpenseItemEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["itemId"])
    val participants: List<ExpenseItemParticipantEntity>,
)

data class ExpenseWithLines(
    @Embedded val expense: ExpenseEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["expenseId"])
    val payers: List<ExpensePayerEntity>,
    @Relation(parentColumns = ["id"], entityColumns = ["expenseId"])
    val splits: List<ExpenseSplitEntity>,
    @Relation(entity = ExpenseItemEntity::class, parentColumns = ["id"], entityColumns = ["expenseId"])
    val items: List<ItemWithParticipants>,
)

@Dao
interface ExpenseDao {
    @Upsert
    suspend fun upsertExpense(expense: ExpenseEntity)

    @Upsert
    suspend fun upsertPayers(payers: List<ExpensePayerEntity>)

    @Upsert
    suspend fun upsertSplits(splits: List<ExpenseSplitEntity>)

    @Upsert
    suspend fun upsertItems(items: List<ExpenseItemEntity>)

    @Upsert
    suspend fun upsertItemParticipants(participants: List<ExpenseItemParticipantEntity>)

    @Query("DELETE FROM expense_payer WHERE expenseId = :expenseId")
    suspend fun clearPayers(expenseId: String)

    @Query("DELETE FROM expense_split WHERE expenseId = :expenseId")
    suspend fun clearSplits(expenseId: String)

    /** Cascades to expense_item_participant via the item foreign key. */
    @Query("DELETE FROM expense_item WHERE expenseId = :expenseId")
    suspend fun clearItems(expenseId: String)

    @Transaction
    suspend fun save(
        expense: ExpenseEntity,
        payers: List<ExpensePayerEntity>,
        splits: List<ExpenseSplitEntity>,
        items: List<ExpenseItemEntity>,
        itemParticipants: List<ExpenseItemParticipantEntity>,
    ) {
        upsertExpense(expense)
        clearPayers(expense.id)
        clearSplits(expense.id)
        clearItems(expense.id)
        upsertPayers(payers)
        upsertSplits(splits)
        upsertItems(items)
        upsertItemParticipants(itemParticipants)
    }

    @Transaction
    @Query("SELECT * FROM expense WHERE groupId = :groupId AND deleted = 0")
    suspend fun forGroup(groupId: String): List<ExpenseWithLines>

    @Transaction
    @Query("SELECT * FROM expense WHERE groupId = :groupId AND deleted = 0")
    fun forGroupFlow(groupId: String): Flow<List<ExpenseWithLines>>

    @Transaction
    @Query("SELECT * FROM expense WHERE id = :id")
    suspend fun byId(id: String): ExpenseWithLines?

    /** Distinct non-empty categories across all live expenses, for the editor's category suggestions. */
    @Query(
        "SELECT DISTINCT category FROM expense WHERE category IS NOT NULL AND category != '' AND deleted = 0 " +
            "ORDER BY category COLLATE NOCASE",
    )
    suspend fun distinctCategories(): List<String>

    @Transaction
    @Query("SELECT * FROM expense WHERE groupId = :groupId AND dirty = 1")
    suspend fun dirty(groupId: String): List<ExpenseWithLines>

    /** Clears dirty only if the row is unchanged since it was pushed, so a concurrent edit survives. */
    @Query("UPDATE expense SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt AND deviceId = :deviceId")
    suspend fun clearDirty(
        id: String,
        updatedAt: Long,
        deviceId: String,
    )

    @Query("UPDATE expense SET deleted = 1, dirty = 1, updatedAt = :updatedAt, deviceId = :deviceId WHERE id = :id")
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
