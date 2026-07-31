package nz.eloque.quits.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Front door: a sidebar drawer for switching groups, wrapping the last-selected group's detail
 * screen directly (no separate "groups list" screen). Shows onboarding in place when there are
 * no groups yet.
 */
@Serializable
data object GroupsHomeKey : NavKey

/**
 * Join-or-create a group. Reused both as the first-run onboarding content (rendered inline by
 * [GroupsHomeKey] when there are no groups) and as its own destination reached from the drawer's
 * "add group" item once at least one group already exists.
 */
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
