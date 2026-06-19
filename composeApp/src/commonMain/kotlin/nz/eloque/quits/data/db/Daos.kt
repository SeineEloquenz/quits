package nz.eloque.quits.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert
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

    @Query("UPDATE groups SET dirty = 0 WHERE id = :id")
    suspend fun clearDirty(id: String)
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

    @Query("UPDATE member SET dirty = 0 WHERE id = :id")
    suspend fun clearDirty(id: String)

    @Query("UPDATE member SET deleted = 1, dirty = 1, updatedAt = :updatedAt, deviceId = :deviceId WHERE id = :id")
    suspend fun tombstone(
        id: String,
        updatedAt: Long,
        deviceId: String,
    )
}

data class ExpenseWithLines(
    @Embedded val expense: ExpenseEntity,
    @Relation(parentColumn = "id", entityColumn = "expenseId")
    val payers: List<ExpensePayerEntity>,
    @Relation(parentColumn = "id", entityColumn = "expenseId")
    val splits: List<ExpenseSplitEntity>,
)

@Dao
interface ExpenseDao {
    @Upsert
    suspend fun upsertExpense(expense: ExpenseEntity)

    @Upsert
    suspend fun upsertPayers(payers: List<ExpensePayerEntity>)

    @Upsert
    suspend fun upsertSplits(splits: List<ExpenseSplitEntity>)

    @Query("DELETE FROM expense_payer WHERE expenseId = :expenseId")
    suspend fun clearPayers(expenseId: String)

    @Query("DELETE FROM expense_split WHERE expenseId = :expenseId")
    suspend fun clearSplits(expenseId: String)

    @Transaction
    suspend fun save(
        expense: ExpenseEntity,
        payers: List<ExpensePayerEntity>,
        splits: List<ExpenseSplitEntity>,
    ) {
        upsertExpense(expense)
        clearPayers(expense.id)
        clearSplits(expense.id)
        upsertPayers(payers)
        upsertSplits(splits)
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

    @Transaction
    @Query("SELECT * FROM expense WHERE groupId = :groupId AND dirty = 1")
    suspend fun dirty(groupId: String): List<ExpenseWithLines>

    @Query("UPDATE expense SET dirty = 0 WHERE id = :id")
    suspend fun clearDirty(id: String)

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

    @Query("UPDATE settlement SET dirty = 0 WHERE id = :id")
    suspend fun clearDirty(id: String)
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
