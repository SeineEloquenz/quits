package nz.eloque.quits.di

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
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
        single<SQLiteDriver> { BundledSQLiteDriver() }
        single<Settings> {
            SharedPreferencesSettings(androidContext().getSharedPreferences("quits", Context.MODE_PRIVATE))
        }
        single<HttpClientEngine> { OkHttp.create() }
    }
