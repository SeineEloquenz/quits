package nz.eloque.quits.di

import androidx.room.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import nz.eloque.quits.data.db.QuitsDatabase
import org.koin.dsl.module

/**
 * Builds [QuitsDatabase] from the platform-provided [RoomDatabase.Builder] (see [platformModule]).
 */
val databaseModule =
    module {
        single<QuitsDatabase> {
            get<RoomDatabase.Builder<QuitsDatabase>>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.Default)
                .build()
        }
        single { get<QuitsDatabase>().groupSyncDao() }
    }
