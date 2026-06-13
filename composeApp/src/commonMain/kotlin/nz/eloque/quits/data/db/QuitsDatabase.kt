package nz.eloque.quits.data.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor

@Database(
    entities = [SyncStateEntity::class],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(QuitsDatabaseConstructor::class)
abstract class QuitsDatabase : RoomDatabase() {
    abstract fun syncStateDao(): SyncStateDao
}

// Room generates the actual per-platform implementation of this constructor.
@Suppress("KotlinNoActualForExpect")
expect object QuitsDatabaseConstructor : RoomDatabaseConstructor<QuitsDatabase> {
    override fun initialize(): QuitsDatabase
}
