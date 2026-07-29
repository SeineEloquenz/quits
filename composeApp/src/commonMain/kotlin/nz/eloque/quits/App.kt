package nz.eloque.quits

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import nz.eloque.quits.data.invite.InviteResolution
import nz.eloque.quits.data.invite.InviteResolver
import nz.eloque.quits.data.invite.PendingInvite
import nz.eloque.quits.data.sync.SyncSettings
import nz.eloque.quits.domain.ExpenseId
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.domain.SettlementId
import nz.eloque.quits.navigation.AboutKey
import nz.eloque.quits.navigation.AddGroupKey
import nz.eloque.quits.navigation.ExpenseDetailKey
import nz.eloque.quits.navigation.ExpenseEditorKey
import nz.eloque.quits.navigation.GroupsHomeKey
import nz.eloque.quits.navigation.JoinInviteKey
import nz.eloque.quits.navigation.LibrariesKey
import nz.eloque.quits.navigation.MemberDetailKey
import nz.eloque.quits.navigation.SettingsKey
import nz.eloque.quits.navigation.SettleUpKey
import nz.eloque.quits.navigation.SettlementEditorKey
import nz.eloque.quits.navigation.StatsKey
import nz.eloque.quits.theme.QuitsTheme
import nz.eloque.quits.ui.about.AboutScreen
import nz.eloque.quits.ui.about.LibrariesScreen
import nz.eloque.quits.ui.expense.ExpenseDetailScreen
import nz.eloque.quits.ui.expense.ExpenseEditorScreen
import nz.eloque.quits.ui.group.MemberDetailScreen
import nz.eloque.quits.ui.group.SettleUpScreen
import nz.eloque.quits.ui.group.SettlementEditorScreen
import nz.eloque.quits.ui.groups.AddGroupScreen
import nz.eloque.quits.ui.groups.JoinInviteScreen
import nz.eloque.quits.ui.home.HomeScreen
import nz.eloque.quits.ui.settings.SettingsScreen
import nz.eloque.quits.ui.stats.StatsScreen
import org.koin.compose.koinInject

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
                    subclass(SettlementEditorKey::class)
                    subclass(StatsKey::class)
                    subclass(ExpenseEditorKey::class)
                    subclass(SettingsKey::class)
                    subclass(AboutKey::class)
                    subclass(LibrariesKey::class)
                    subclass(JoinInviteKey::class)
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
            val pendingInvite = koinInject<PendingInvite>()
            val inviteResolver = koinInject<InviteResolver>()
            val settings = koinInject<SyncSettings>()

            // React to invite decisions only; the resolver does the domain lookup once per code.
            LaunchedEffect(Unit) {
                inviteResolver.resolution.collect { resolution ->
                    when (resolution) {
                        InviteResolution.None -> {}
                        is InviteResolution.JumpTo -> {
                            settings.activeGroupId = resolution.groupId.value
                            while (backStack.size > 1) backStack.removeLastOrNull()
                            pendingInvite.consume()
                        }
                        is InviteResolution.Confirm ->
                            if (backStack.none { it is JoinInviteKey }) {
                                backStack.add(JoinInviteKey(resolution.code))
                            }
                    }
                }
            }
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
                                onOpenAbout = { backStack.add(AboutKey) },
                                onAddGroup = { backStack.add(AddGroupKey) },
                                onAddExpense = { groupId -> backStack.add(ExpenseEditorKey(groupId.value)) },
                                onOpenExpense = { groupId, expenseId -> backStack.add(ExpenseDetailKey(groupId.value, expenseId.value)) },
                                onOpenMember = { groupId, memberId -> backStack.add(MemberDetailKey(groupId.value, memberId.value)) },
                                onOpenSettlement = { groupId, settlementId ->
                                    backStack.add(SettlementEditorKey(groupId.value, settlementId.value))
                                },
                                onSettleUp = { groupId -> backStack.add(SettleUpKey(groupId.value)) },
                                onOpenStats = { groupId -> backStack.add(StatsKey(groupId.value)) },
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
                        entry<SettlementEditorKey> { key ->
                            SettlementEditorScreen(
                                groupId = GroupId(key.groupId),
                                settlementId = SettlementId(key.settlementId),
                                onBack = { backStack.removeLastOrNull() },
                            )
                        }
                        entry<StatsKey> { key ->
                            StatsScreen(
                                groupId = GroupId(key.groupId),
                                onBack = { backStack.removeLastOrNull() },
                            )
                        }
                        entry<SettingsKey> {
                            SettingsScreen(onBack = { backStack.removeLastOrNull() })
                        }
                        entry<AboutKey> {
                            AboutScreen(
                                onBack = { backStack.removeLastOrNull() },
                                onOpenLibraries = { backStack.add(LibrariesKey) },
                            )
                        }
                        entry<LibrariesKey> {
                            LibrariesScreen(onBack = { backStack.removeLastOrNull() })
                        }
                        entry<JoinInviteKey> { key ->
                            JoinInviteScreen(
                                code = key.code,
                                onCancel = {
                                    pendingInvite.consume()
                                    backStack.removeLastOrNull()
                                },
                                // join() sets the new group active; Home shows it once we pop back.
                                onJoined = {
                                    pendingInvite.consume()
                                    backStack.removeLastOrNull()
                                },
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
