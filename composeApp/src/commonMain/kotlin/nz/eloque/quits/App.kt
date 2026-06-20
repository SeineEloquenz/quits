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
import nz.eloque.quits.navigation.HomeKey
import nz.eloque.quits.navigation.SettingsKey
import nz.eloque.quits.theme.QuitsTheme
import nz.eloque.quits.ui.expense.ExpenseEditorScreen
import nz.eloque.quits.ui.home.HomeScreen
import nz.eloque.quits.ui.settings.SettingsScreen

private val navSavedStateConfiguration =
    SavedStateConfiguration {
        serializersModule =
            SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(HomeKey::class)
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
            val backStack = rememberNavBackStack(navSavedStateConfiguration, HomeKey)
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
                        entry<HomeKey> {
                            HomeScreen(
                                onOpenSettings = { backStack.add(SettingsKey) },
                                onAddExpense = { backStack.add(ExpenseEditorKey(it.value)) },
                                onEditExpense = { group, expense ->
                                    backStack.add(ExpenseEditorKey(group.value, expense.value))
                                },
                            )
                        }
                        entry<SettingsKey> {
                            SettingsScreen(onBack = { backStack.removeLastOrNull() })
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
