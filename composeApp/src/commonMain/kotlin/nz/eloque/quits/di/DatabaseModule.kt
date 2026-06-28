package nz.eloque.quits.di

import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteDriver
import kotlinx.coroutines.Dispatchers
import nz.eloque.quits.data.db.MIGRATION_1_2
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
                .addMigrations(MIGRATION_1_2)
                .setQueryCoroutineContext(Dispatchers.Default)
                .build()
        }
        single { get<QuitsDatabase>().groupSyncDao() }
    }
