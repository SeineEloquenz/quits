package nz.eloque.quits

import nz.eloque.quits.di.initKoin
import nz.eloque.quits.sync.IosBackgroundSync

fun startApp() {
    initKoin()
    IosBackgroundSync.register()
}

fun scheduleBackgroundSync() = IosBackgroundSync.schedule()
