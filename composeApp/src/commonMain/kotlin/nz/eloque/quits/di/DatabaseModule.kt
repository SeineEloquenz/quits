package nz.eloque.quits.di

import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteDriver
import kotlinx.coroutines.Dispatchers
import nz.eloque.quits.data.db.MIGRATION_1_2
import nz.eloque.quits.data.db.MIGRATION_2_3
import nz.eloque.quits.data.db.MIGRATION_3_4
import nz.eloque.quits.data.db.MIGRATION_4_5
import nz.eloque.quits.data.db.MIGRATION_5_6
import nz.eloque.quits.data.db.MIGRATION_6_7
import nz.eloque.quits.data.db.MIGRATION_7_8
import nz.eloque.quits.data.db.QuitsDatabase
import org.koin.dsl.module

/**
 * Builds [QuitsDatabase] from the platform-provided [RoomDatabase.Builder] and [SQLiteDriver]
 * (see [platformModule]). The driver differs per platform: bundled SQLite on Android/iOS, the
 * Web Worker / OPFS driver on web.
 */
val databaseModule =
    module {
        single<QuitsDatabase> {
            get<RoomDatabase.Builder<QuitsDatabase>>()
                .setDriver(get<SQLiteDriver>())
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .setQueryCoroutineContext(Dispatchers.Default)
                .build()
        }
        single { get<QuitsDatabase>().groupSyncDao() }
    }
