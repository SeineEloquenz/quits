package nz.eloque.quits.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeKey : NavKey

@Serializable
data class ExpenseEditorKey(
    val groupId: String,
    val expenseId: String? = null,
) : NavKey

@Serializable
data object SettingsKey : NavKey
