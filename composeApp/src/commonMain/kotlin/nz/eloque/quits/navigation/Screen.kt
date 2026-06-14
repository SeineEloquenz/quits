package nz.eloque.quits.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object GroupsKey : NavKey

@Serializable
data class GroupDetailKey(
    val groupId: String,
) : NavKey

@Serializable
data class ExpenseEditorKey(
    val groupId: String,
    val expenseId: String? = null,
) : NavKey
