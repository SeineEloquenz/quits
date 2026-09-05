package nz.eloque.quits.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nz.eloque.quits.domain.EntryId
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.domain.SettlementId
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.about_title
import nz.eloque.quits.resources.app_name
import nz.eloque.quits.resources.cd_add_group
import nz.eloque.quits.resources.cd_settings
import nz.eloque.quits.resources.drawer_archived
import nz.eloque.quits.resources.settings_title
import nz.eloque.quits.ui.components.LoadingBox
import nz.eloque.quits.ui.group.GroupDetailScreen
import nz.eloque.quits.ui.groups.GroupsViewModel
import nz.eloque.quits.ui.onboarding.OnboardingScreen
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
    onAddGroup: () -> Unit,
    onAddExpense: (GroupId) -> Unit,
    onAddIncome: (GroupId) -> Unit,
    onOpenEntry: (GroupId, EntryId) -> Unit,
    onOpenMember: (GroupId, MemberId) -> Unit,
    onOpenSettlement: (GroupId, SettlementId) -> Unit,
    onSettleUp: (GroupId) -> Unit,
    onOpenStats: (GroupId) -> Unit,
) {
    val viewModel = koinViewModel<GroupsViewModel>()
    val state by viewModel.state.collectAsState()
    val activeGroup by viewModel.activeGroup.collectAsState()
    val error by viewModel.error.collectAsState()

    if (state.loaded && state.groups.isEmpty()) {
        OnboardingScreen(
            onCreate = viewModel::createGroup,
            onJoin = viewModel::join,
            error = error,
            onJoinInput = viewModel::clearError,
        )
        return
    }

    val active = activeGroup
    if (active == null) {
        LoadingBox(Modifier.fillMaxSize())
        return
    }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val closeDrawer = { scope.launch { drawerState.close() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Leave horizontal swipes to the group screen's own gestures; the drawer opens from the menu button.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                GroupDrawer(
                    viewModel = viewModel,
                    activeGroup = active,
                    onSelect = {
                        closeDrawer()
                        scope.launch {
                            viewModel.setActiveGroup(it)
                        }
                    },
                    onAddGroup = {
                        closeDrawer()
                        onAddGroup()
                    },
                    onOpenSettings = {
                        closeDrawer()
                        onOpenSettings()
                    },
                    onOpenAbout = {
                        closeDrawer()
                        onOpenAbout()
                    },
                )
            }
        },
    ) {
        GroupDetailScreen(
            groupId = active,
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onAddExpense = { onAddExpense(active) },
            onAddIncome = { onAddIncome(active) },
            onOpenEntry = { onOpenEntry(active, it) },
            onOpenMember = { onOpenMember(active, it) },
            onOpenSettlement = { onOpenSettlement(active, it) },
            onSettleUp = { onSettleUp(active) },
            onOpenStats = { onOpenStats(active) },
            // The detail screen is keyed on the active group, so switching it is the navigation.
            onGroupCreated = { scope.launch { viewModel.setActiveGroup(it) } },
        )
    }
}

@Composable
private fun GroupDrawer(
    viewModel: GroupsViewModel,
    activeGroup: GroupId,
    onSelect: (GroupId) -> Unit,
    onAddGroup: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val rows by viewModel.homeRows.collectAsState()
    val (archived, active) = rows.partition { it.archived }
    var archivedExpanded by remember { mutableStateOf(false) }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(
            stringResource(Res.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(16.dp),
        )

        active.forEach { row ->
            NavigationDrawerItem(
                label = { Text(row.name) },
                selected = row.id == activeGroup,
                onClick = { onSelect(row.id) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }

        if (archived.isNotEmpty()) {
            NavigationDrawerItem(
                label = { Text(stringResource(Res.string.drawer_archived, archived.size)) },
                selected = false,
                icon = { Icon(Icons.Default.Inventory2, contentDescription = null) },
                badge = {
                    Icon(
                        if (archivedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                    )
                },
                onClick = { archivedExpanded = !archivedExpanded },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
            if (archivedExpanded) {
                archived.forEach { row ->
                    NavigationDrawerItem(
                        label = { Text(row.name) },
                        selected = row.id == activeGroup,
                        onClick = { onSelect(row.id) },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
            }
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.cd_add_group)) },
            selected = false,
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            onClick = onAddGroup,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.settings_title)) },
            selected = false,
            icon = { Icon(Icons.Default.Settings, contentDescription = stringResource(Res.string.cd_settings)) },
            onClick = onOpenSettings,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )

        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.about_title)) },
            selected = false,
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            onClick = onOpenAbout,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )

        Spacer(Modifier.height(8.dp))
    }
}
