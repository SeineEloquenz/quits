package nz.eloque.quits.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The drawer-based home: switches groups and shows the last-selected group's detail screen (or onboarding when there are none). */
@Serializable
data object GroupsHomeKey : NavKey

/** Join-or-create a group; used both as inline onboarding (via [GroupsHomeKey]) and as its own drawer destination. */
@Serializable
data object AddGroupKey : NavKey

/** Read-only breakdown of one entry (who paid, who owes what) before dropping into edit. */
@Serializable
data class EntryDetailKey(
    val groupId: String,
    val entryId: String,
) : NavKey

/** A single member: net balance, every entry/settlement they're tied to, rename/remove. */
@Serializable
data class MemberDetailKey(
    val groupId: String,
    val memberId: String,
) : NavKey

/** The full suggested-settlement list, pulled out of the balance panel into its own flow. */
@Serializable
data class SettleUpKey(
    val groupId: String,
) : NavKey

/** Read-only spending breakdown for a group: total, by category, by member. */
@Serializable
data class StatsKey(
    val groupId: String,
) : NavKey

@Serializable
data class EntryEditorKey(
    val groupId: String,
    val entryId: String? = null,
    /** "EXPENSE" or "INCOME" — the kind for a new entry (ignored when editing an existing one). */
    val kind: String = "EXPENSE",
) : NavKey

/** Edit an existing recorded settlement (amount, who paid whom, date, note) or delete it. */
@Serializable
data class SettlementEditorKey(
    val groupId: String,
    val settlementId: String,
) : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data object AboutKey : NavKey

@Serializable
data object LibrariesKey : NavKey

@Serializable
data class JoinInviteKey(
    val code: String,
) : NavKey
