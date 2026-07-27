package nz.eloque.quits

import nz.eloque.quits.data.invite.PendingInvite
import nz.eloque.quits.di.initKoin
import nz.eloque.quits.sync.IosBackgroundSync
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

// KoinComponent is how the rest of iosMain reaches the container (see IosBackgroundSync); the
// koin-core Kotlin/Native artifact doesn't expose GlobalContext the way wasm/JVM do.
private object DeepLinkKoin : KoinComponent

fun startApp() {
    initKoin()
    IosBackgroundSync.register()
}

fun scheduleBackgroundSync() = IosBackgroundSync.schedule()

/** Bridge for Universal Links / URL opens from the SwiftUI wrapper. Koin is up by now (startApp). */
fun handleDeepLink(url: String) {
    DeepLinkKoin.get<PendingInvite>().offer(url)
}
