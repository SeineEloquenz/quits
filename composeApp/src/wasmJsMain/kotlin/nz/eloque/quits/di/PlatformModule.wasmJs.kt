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
import org.koin.core.module.Module
import org.koin.dsl.module

actual val platformModule: Module =
    module {
        // OPFS-backed file; the Web Worker driver (see :sqliteWebWorker) persists it across reloads.
        single<RoomDatabase.Builder<QuitsDatabase>> { Room.databaseBuilder<QuitsDatabase>(name = "quits.db") }
        single<SQLiteDriver> { createWebSqliteDriver() }
        // multiplatform-settings backed by window.localStorage.
        single<Settings> { StorageSettings() }
        single<HttpClientEngine> { Js.create() }
    }
