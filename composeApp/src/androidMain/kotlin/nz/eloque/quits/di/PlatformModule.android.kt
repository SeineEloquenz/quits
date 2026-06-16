package nz.eloque.quits.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import nz.eloque.quits.data.db.QuitsDatabase
import nz.eloque.quits.data.sync.SyncScheduler
import nz.eloque.quits.sync.WorkManagerSyncScheduler
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
        single<Settings> {
            SharedPreferencesSettings(androidContext().getSharedPreferences("quits", Context.MODE_PRIVATE))
        }
        single<HttpClientEngine> { OkHttp.create() }
        single<SyncScheduler> { WorkManagerSyncScheduler(androidContext()) }
    }
