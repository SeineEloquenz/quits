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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nz.eloque.quits.domain.ExpenseId
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.app_name
import nz.eloque.quits.resources.cd_add_group
import nz.eloque.quits.resources.cd_settings
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
    onAddGroup: () -> Unit,
    onAddExpense: (GroupId) -> Unit,
    onOpenExpense: (GroupId, ExpenseId) -> Unit,
    onOpenMember: (GroupId, MemberId) -> Unit,
    onSettleUp: (GroupId) -> Unit,
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
                        viewModel.setActiveGroup(it)
                        closeDrawer()
                    },
                    onAddGroup = {
                        closeDrawer()
                        onAddGroup()
                    },
                    onOpenSettings = {
                        closeDrawer()
                        onOpenSettings()
                    },
                )
            }
        },
    ) {
        GroupDetailScreen(
            groupId = active,
            onOpenDrawer = { scope.launch { drawerState.open() } },
            onAddExpense = { onAddExpense(active) },
            onOpenExpense = { onOpenExpense(active, it) },
            onOpenMember = { onOpenMember(active, it) },
            onSettleUp = { onSettleUp(active) },
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
) {
    val rows by viewModel.homeRows.collectAsState()

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(
            stringResource(Res.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(16.dp),
        )

        rows.forEach { row ->
            NavigationDrawerItem(
                label = { Text(row.name) },
                selected = row.id == activeGroup,
                onClick = { onSelect(row.id) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
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

        Spacer(Modifier.height(8.dp))
    }
}
