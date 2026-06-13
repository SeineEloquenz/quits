package nz.eloque.quits.di

import androidx.room.Room
import androidx.room.RoomDatabase
import nz.eloque.quits.data.db.QuitsDatabase
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

actual val platformModule: Module =
    module {
        single<RoomDatabase.Builder<QuitsDatabase>> {
            val documents =
                NSFileManager.defaultManager.URLForDirectory(
                    directory = NSDocumentDirectory,
                    inDomain = NSUserDomainMask,
                    appropriateForURL = null,
                    create = false,
                    error = null,
                )
            val path = requireNotNull(documents?.path) { "no documents directory" } + "/quits.db"
            Room.databaseBuilder<QuitsDatabase>(name = path)
        }
    }
