package nz.eloque.quits.di

import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteDriver
import com.russhwolf.settings.Settings
import com.russhwolf.settings.StorageSettings
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js
import nz.eloque.quits.data.db.QuitsDatabase
import nz.eloque.quits.sqlite.createWebSqliteDriver
import nz.eloque.quits.util.FileExporter
import nz.eloque.quits.util.Sharer
import nz.eloque.quits.util.WebFileExporter
import nz.eloque.quits.util.WebSharer
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module =
    module {
        single<RoomDatabase.Builder<QuitsDatabase>> { Room.databaseBuilder<QuitsDatabase>(name = "quits.db") }
        single<SQLiteDriver> { createWebSqliteDriver() }
        single<Settings> { StorageSettings() }
        single<HttpClientEngine> { Js.create() }
        single<Sharer> { WebSharer() }
        single<FileExporter> { WebFileExporter() }
    }
