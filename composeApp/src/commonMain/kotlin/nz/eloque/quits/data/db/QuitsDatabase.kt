package nz.eloque.quits.data.db

import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor

@Database(
    entities = [
        GroupSyncEntity::class,
        GroupEntity::class,
        GroupPrefsEntity::class,
        MemberEntity::class,
        EntryEntity::class,
        EntryPayerEntity::class,
        EntrySplitEntity::class,
        EntryItemEntity::class,
        EntryItemParticipantEntity::class,
        SettlementEntity::class,
        CategoryEntity::class,
        FxRateEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
@ConstructedBy(QuitsDatabaseConstructor::class)
abstract class QuitsDatabase : RoomDatabase() {
    abstract fun groupSyncDao(): GroupSyncDao

    abstract fun groupDao(): GroupDao

    abstract fun groupPrefsDao(): GroupPrefsDao

    abstract fun memberDao(): MemberDao

    abstract fun entryDao(): EntryDao

    abstract fun settlementDao(): SettlementDao

    abstract fun categoryDao(): CategoryDao

    abstract fun fxRateDao(): FxRateDao
}

// Room generates the actual per-platform implementation of this constructor.
expect object QuitsDatabaseConstructor : RoomDatabaseConstructor<QuitsDatabase> {
    override fun initialize(): QuitsDatabase
}
