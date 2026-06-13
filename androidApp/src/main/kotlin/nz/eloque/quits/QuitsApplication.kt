package nz.eloque.quits

import android.app.Application
import nz.eloque.quits.di.initKoin
import org.koin.android.ext.koin.androidContext

class QuitsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@QuitsApplication)
        }
    }
}
