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
import nz.eloque.compose_kit.navigation.slideBackward
import nz.eloque.compose_kit.navigation.slideForward
import nz.eloque.quits.domain.ExpenseId
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.navigation.AddGroupKey
import nz.eloque.quits.navigation.ExpenseDetailKey
import nz.eloque.quits.navigation.ExpenseEditorKey
import nz.eloque.quits.navigation.GroupsHomeKey
import nz.eloque.quits.navigation.MemberDetailKey
import nz.eloque.quits.navigation.SettingsKey
import nz.eloque.quits.navigation.SettleUpKey
import nz.eloque.quits.theme.QuitsTheme
import nz.eloque.quits.ui.expense.ExpenseDetailScreen
import nz.eloque.quits.ui.expense.ExpenseEditorScreen
import nz.eloque.quits.ui.group.MemberDetailScreen
import nz.eloque.quits.ui.group.SettleUpScreen
import nz.eloque.quits.ui.groups.AddGroupScreen
import nz.eloque.quits.ui.home.HomeScreen
import nz.eloque.quits.ui.settings.SettingsScreen

private val navSavedStateConfiguration =
    SavedStateConfiguration {
        serializersModule =
            SerializersModule {
                polymorphic(NavKey::class) {
                    subclass(GroupsHomeKey::class)
                    subclass(AddGroupKey::class)
                    subclass(ExpenseDetailKey::class)
                    subclass(MemberDetailKey::class)
                    subclass(SettleUpKey::class)
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
            val backStack = rememberNavBackStack(navSavedStateConfiguration, GroupsHomeKey)
            NavDisplay(
                backStack = backStack,
                modifier = Modifier.fillMaxSize().safeDrawingPadding(),
                onBack = { backStack.removeLastOrNull() },
                transitionSpec = { slideForward() },
                popTransitionSpec = { slideBackward() },
                predictivePopTransitionSpec = { slideBackward() },
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                entryProvider =
                    entryProvider {
                        // Front door: sidebar drawer wrapping the last-selected group directly —
                        // switching groups happens in place, it's never a back-stack push/pop.
                        entry<GroupsHomeKey> {
                            HomeScreen(
                                onOpenSettings = { backStack.add(SettingsKey) },
                                onAddGroup = { backStack.add(AddGroupKey) },
                                onAddExpense = { groupId -> backStack.add(ExpenseEditorKey(groupId.value)) },
                                onOpenExpense = { groupId, expenseId -> backStack.add(ExpenseDetailKey(groupId.value, expenseId.value)) },
                                onOpenMember = { groupId, memberId -> backStack.add(MemberDetailKey(groupId.value, memberId.value)) },
                                onSettleUp = { groupId -> backStack.add(SettleUpKey(groupId.value)) },
                            )
                        }
                        entry<AddGroupKey> {
                            AddGroupScreen(
                                onBack = { backStack.removeLastOrNull() },
                                // createGroup()/join() already set the new group active; Home will
                                // show it automatically once this pops back to it.
                                onDone = { backStack.removeLastOrNull() },
                            )
                        }
                        entry<ExpenseDetailKey> { key ->
                            ExpenseDetailScreen(
                                groupId = GroupId(key.groupId),
                                expenseId = ExpenseId(key.expenseId),
                                onBack = { backStack.removeLastOrNull() },
                                onEdit = { backStack.add(ExpenseEditorKey(key.groupId, key.expenseId)) },
                            )
                        }
                        entry<MemberDetailKey> { key ->
                            MemberDetailScreen(
                                groupId = GroupId(key.groupId),
                                memberId = MemberId(key.memberId),
                                onBack = { backStack.removeLastOrNull() },
                            )
                        }
                        entry<SettleUpKey> { key ->
                            SettleUpScreen(
                                groupId = GroupId(key.groupId),
                                onBack = { backStack.removeLastOrNull() },
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
