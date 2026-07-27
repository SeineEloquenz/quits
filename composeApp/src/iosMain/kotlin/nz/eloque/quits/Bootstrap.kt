package nz.eloque.quits

import nz.eloque.quits.data.invite.PendingInvite
import nz.eloque.quits.di.initKoin
import nz.eloque.quits.sync.IosBackgroundSync
import org.koin.core.context.GlobalContext

fun startApp() {
    initKoin()
    IosBackgroundSync.register()
}

fun scheduleBackgroundSync() = IosBackgroundSync.schedule()

/** Bridge for Universal Links / URL opens from the SwiftUI wrapper. Koin is up by now (startApp). */
fun handleDeepLink(url: String) {
    GlobalContext.get().get<PendingInvite>().offer(url)
}
