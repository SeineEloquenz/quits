package nz.eloque.quits.data.invite

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import nz.eloque.quits.data.sync.SyncEngine
import nz.eloque.quits.domain.GroupId

/** What, if anything, the UI should do about the currently pending invite. */
sealed interface InviteResolution {
    /** Nothing is waiting. */
    data object None : InviteResolution

    /** The user already belongs to the group behind this code; jump straight into it. */
    data class JumpTo(
        val groupId: GroupId,
    ) : InviteResolution

    /** The user isn't a member yet; ask them to confirm joining under this [code]. */
    data class Confirm(
        val code: String,
    ) : InviteResolution
}

/**
 * Turns the one-slot [PendingInvite] into a navigation-free decision. Each inbound code is
 * classified against the local groups exactly once — when the code changes — so the UI can react
 * without doing any domain lookups of its own.
 */
class InviteResolver(
    private val pendingInvite: PendingInvite,
    private val engine: SyncEngine,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    val resolution: Flow<InviteResolution> =
        pendingInvite.code.mapLatest { code ->
            when {
                code == null -> InviteResolution.None
                else ->
                    engine.localGroupFor(code)
                        ?.let { InviteResolution.JumpTo(it) }
                        ?: InviteResolution.Confirm(code)
            }
        }
}
