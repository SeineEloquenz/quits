package nz.eloque.quits.data.db

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor

@Database(
    entities = [
        GroupSyncEntity::class,
        GroupEntity::class,
        MemberEntity::class,
        ExpenseEntity::class,
        ExpensePayerEntity::class,
        ExpenseSplitEntity::class,
        ExpenseItemEntity::class,
        ExpenseItemParticipantEntity::class,
        SettlementEntity::class,
        CategoryEntity::class,
        FxRateEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
@ConstructedBy(QuitsDatabaseConstructor::class)
abstract class QuitsDatabase : RoomDatabase() {
    abstract fun groupSyncDao(): GroupSyncDao

    abstract fun groupDao(): GroupDao

    abstract fun memberDao(): MemberDao

    abstract fun expenseDao(): ExpenseDao

    abstract fun settlementDao(): SettlementDao

    abstract fun categoryDao(): CategoryDao

    abstract fun fxRateDao(): FxRateDao
}

// Room generates the actual per-platform implementation of this constructor.
expect object QuitsDatabaseConstructor : RoomDatabaseConstructor<QuitsDatabase> {
    override fun initialize(): QuitsDatabase
}
