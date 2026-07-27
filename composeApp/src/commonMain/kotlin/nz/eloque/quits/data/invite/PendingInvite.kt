package nz.eloque.quits.data.invite

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A one-slot inbox for an inbound invite link. Platform entry points (Android `Intent`, iOS
 * Universal Link, web `window.location`) [offer] the opened URL; the Compose UI collects [code]
 * and routes to the join-confirmation screen, then [consume]s it.
 */
class PendingInvite {
    private val _code = MutableStateFlow<String?>(null)

    /** The pending invite code, or null when nothing is waiting. */
    val code: StateFlow<String?> = _code.asStateFlow()

    /** Records the code from [url] if it is a valid invite link; ignores anything else. */
    fun offer(url: String) {
        InviteLink.parse(url)?.let { _code.value = it }
    }

    /** Clears the pending invite once the UI has handled it. */
    fun consume() {
        _code.value = null
    }
}
