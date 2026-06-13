package nz.eloque.quits.di

import androidx.room.Room
import androidx.room.RoomDatabase
import nz.eloque.quits.data.db.QuitsDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module =
    module {
        single<RoomDatabase.Builder<QuitsDatabase>> {
            val context = androidContext()
            Room.databaseBuilder<QuitsDatabase>(
                context = context,
                name = context.getDatabasePath("quits.db").absolutePath,
            )
        }
    }
