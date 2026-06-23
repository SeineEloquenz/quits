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
        SettlementEntity::class,
        FxRateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(QuitsDatabaseConstructor::class)
abstract class QuitsDatabase : RoomDatabase() {
    abstract fun groupSyncDao(): GroupSyncDao

    abstract fun groupDao(): GroupDao

    abstract fun memberDao(): MemberDao

    abstract fun expenseDao(): ExpenseDao

    abstract fun settlementDao(): SettlementDao

    abstract fun fxRateDao(): FxRateDao
}

// Room generates the actual per-platform implementation of this constructor.
@Suppress("KotlinNoActualForExpect")
expect object QuitsDatabaseConstructor : RoomDatabaseConstructor<QuitsDatabase> {
    override fun initialize(): QuitsDatabase
}
