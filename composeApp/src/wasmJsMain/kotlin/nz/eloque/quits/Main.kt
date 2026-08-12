package nz.eloque.quits

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import kotlinx.browser.window
import nz.eloque.quits.data.invite.PendingInvite
import nz.eloque.quits.di.initKoin
import org.koin.core.context.GlobalContext

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initKoin()
    GlobalContext.get().get<PendingInvite>().offer(window.location.href)
    if (window.location.hash.isNotEmpty()) {
        // Drop the secret from the address bar / history once captured.
        window.history.replaceState(null, "", window.location.pathname + window.location.search)
    }
    ComposeViewport(document.body!!) {
        App()
    }
}
