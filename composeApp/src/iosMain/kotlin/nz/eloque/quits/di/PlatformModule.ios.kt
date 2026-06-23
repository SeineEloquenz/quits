package nz.eloque.quits.di

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.russhwolf.settings.NSUserDefaultsSettings
import com.russhwolf.settings.Settings
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import kotlinx.cinterop.ExperimentalForeignApi
import nz.eloque.quits.data.db.QuitsDatabase
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
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
        single<SQLiteDriver> { BundledSQLiteDriver() }
        single<Settings> { NSUserDefaultsSettings(NSUserDefaults.standardUserDefaults) }
        single<HttpClientEngine> { Darwin.create() }
    }
