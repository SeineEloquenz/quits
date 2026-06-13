package nz.eloque.quits.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface GroupDao {
    @Upsert
    suspend fun upsert(group: GroupEntity)

    @Query("SELECT * FROM groups WHERE id = :id")
    suspend fun byId(id: String): GroupEntity?

    @Query("SELECT * FROM groups WHERE deleted = 0")
    suspend fun all(): List<GroupEntity>

    @Query("DELETE FROM groups WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface MemberDao {
    @Upsert
    suspend fun upsert(members: List<MemberEntity>)

    @Query("SELECT * FROM member WHERE groupId = :groupId AND deleted = 0")
    suspend fun forGroup(groupId: String): List<MemberEntity>
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
    @Query("SELECT * FROM expense WHERE id = :id")
    suspend fun byId(id: String): ExpenseWithLines?
}

@Dao
interface SettlementDao {
    @Upsert
    suspend fun upsert(settlement: SettlementEntity)

    @Query("SELECT * FROM settlement WHERE groupId = :groupId AND deleted = 0")
    suspend fun forGroup(groupId: String): List<SettlementEntity>
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
