package nz.eloque.quits

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document
import nz.eloque.quits.di.initKoin

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    initKoin()
    // No WorkManager/BGTaskScheduler on the web; sync runs in the foreground (launch + manual).
    ComposeViewport(document.body!!) {
        App()
    }
}
