package nz.eloque.quits

import nz.eloque.quits.di.initKoin
import nz.eloque.quits.sync.IosBackgroundSync

fun initApp() {
    initKoin()
    IosBackgroundSync.register()
}

fun scheduleBackgroundSync() = IosBackgroundSync.schedule()
