package nz.eloque.quits

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import nz.eloque.quits.data.invite.PendingInvite
import org.koin.core.context.GlobalContext

class MainActivity : ComponentActivity() {
    private val pendingInvite: PendingInvite by lazy { GlobalContext.get().get() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
}
