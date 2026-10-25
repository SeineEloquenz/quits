package nz.eloque.quits.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The drawer-based home: switches groups and shows the last-selected group's detail screen (or onboarding when there are none). */
@Serializable
data object GroupsHomeKey : NavKey

/** The drawer's start-or-join choice for adding another group. */
@Serializable
data object AddGroupKey : NavKey

@Serializable
data object CreateGroupKey : NavKey

/** Join a group by share code or pasted invite link. */
@Serializable
data object JoinGroupKey : NavKey

/** Shown once after creating a group, asking whether to share it. */
@Serializable
data class GroupCreatedKey(
    val groupId: String,
    val name: String,
) : NavKey

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
data object RelayInfoKey : NavKey

@Serializable
data object AboutKey : NavKey

@Serializable
data object LibrariesKey : NavKey

@Serializable
data class JoinInviteKey(
    val code: String,
) : NavKey

/** Split a single receipt among ad-hoc people, outside any group. [currency] is the starting currency code. */
@Serializable
data class QuickSplitKey(
    val currency: String? = null,
) : NavKey

/** Sets the group's default split for new expenses. */
@Serializable
data class DefaultSplitKey(
    val groupId: String,
) : NavKey
