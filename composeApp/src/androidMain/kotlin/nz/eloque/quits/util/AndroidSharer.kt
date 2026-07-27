package nz.eloque.quits.util

import android.content.Context
import android.content.Intent

/** Fires an `ACTION_SEND` chooser from the application context. */
class AndroidSharer(
    private val context: Context,
) : Sharer {
    override fun share(text: String) {
        val send =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
        // Launched from the application context, so a new task is required.
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
