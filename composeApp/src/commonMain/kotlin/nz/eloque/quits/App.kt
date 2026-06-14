package nz.eloque.quits

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.navigation.ExpenseEditorKey
import nz.eloque.quits.navigation.GroupDetailKey
import nz.eloque.quits.navigation.GroupsKey
import nz.eloque.quits.navigation.SettingsKey
import nz.eloque.quits.theme.QuitsTheme
import nz.eloque.quits.ui.expense.ExpenseEditorScreen
import nz.eloque.quits.ui.group.GroupDetailScreen
import nz.eloque.quits.ui.groups.GroupsScreen
import nz.eloque.quits.ui.settings.SettingsScreen

private val navSavedStateConfiguration =
    SavedStateConfiguration {
        serializersModule =
            SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(GroupsKey::class)
                    subclass(GroupDetailKey::class)
                    subclass(ExpenseEditorKey::class)
                    subclass(SettingsKey::class)
                }
            }
    }

@Composable
fun App() {
    QuitsTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            val backStack = rememberNavBackStack(navSavedStateConfiguration, GroupsKey)
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                onBack = { backStack.removeLastOrNull() },
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                entryProvider =
                    entryProvider {
                        entry<GroupsKey> {
                            GroupsScreen(
                                onOpenGroup = { backStack.add(GroupDetailKey(it.value)) },
                                onOpenSettings = { backStack.add(SettingsKey) },
                            )
                        }
                        entry<SettingsKey> {
                            SettingsScreen(onBack = { backStack.removeLastOrNull() })
                        }
                        entry<GroupDetailKey> { key ->
                            GroupDetailScreen(
                                groupId = GroupId(key.groupId),
                                onBack = { backStack.removeLastOrNull() },
                                onAddExpense = { backStack.add(ExpenseEditorKey(key.groupId)) },
                                onEditExpense = { backStack.add(ExpenseEditorKey(key.groupId, it.value)) },
                            )
                        }
                        entry<ExpenseEditorKey> { key ->
                            ExpenseEditorScreen(
                                groupId = GroupId(key.groupId),
                                expenseId = key.expenseId,
                                onDone = { backStack.removeLastOrNull() },
                                onCancel = { backStack.removeLastOrNull() },
                            )
                        }
                    },
            )
        }
    }
}
