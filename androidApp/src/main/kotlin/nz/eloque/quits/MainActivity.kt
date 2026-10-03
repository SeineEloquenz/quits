package nz.eloque.quits

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import nz.eloque.quits.data.invite.PendingInvite
import org.koin.core.context.GlobalContext

class MainActivity : ComponentActivity() {
    private val pendingInvite: PendingInvite by lazy { GlobalContext.get().get() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // TODO remove after compose foundation-layout reaches 1.13.0
        reapplyInsetsAfterAnimations()
        handleIntent(intent) // cold start: an App Link that launched the app
        setContent { App() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent) // warm start: app already running
    }

    private fun handleIntent(intent: Intent?) {
        intent?.data?.toString()?.let(pendingInvite::offer)
    }

    /**
     * Works around Compose dropping the final insets when the IME is dismissed by the predictive back
     * gesture, which leaves imePadding stuck at keyboard height. Remove once Compose foundation-layout is
     * at 1.13.0 or later. https://android-review.googlesource.com/c/platform/frameworks/support/+/4240962
     */
    private fun reapplyInsetsAfterAnimations() {
        val decorView = window.decorView
        ViewCompat.setWindowInsetsAnimationCallback(
            decorView,
            object : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                override fun onProgress(
                    insets: WindowInsetsCompat,
                    runningAnimations: List<WindowInsetsAnimationCompat>,
                ): WindowInsetsCompat = insets

                // Posted so it runs after Compose's own onEnd has cleared its running-animation state.
                override fun onEnd(animation: WindowInsetsAnimationCompat) {
                    decorView.post { decorView.requestApplyInsets() }
                }
            },
        )
    }
}
