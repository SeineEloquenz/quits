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
import androidx.compose.material.icons.filled.GroupAdd
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
import nz.eloque.quits.domain.ExpenseId
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.app_name
import nz.eloque.quits.resources.cd_settings
import nz.eloque.quits.resources.groups_join_group
import nz.eloque.quits.resources.groups_new_group
import nz.eloque.quits.resources.settings_title
import nz.eloque.quits.ui.components.LoadingBox
import nz.eloque.quits.ui.group.GroupDetailScreen
import nz.eloque.quits.ui.groups.CreateGroupForm
import nz.eloque.quits.ui.groups.GroupsViewModel
import nz.eloque.quits.ui.groups.JoinGroupForm
import nz.eloque.quits.ui.onboarding.OnboardingScreen
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onAddExpense: (GroupId) -> Unit,
    onEditExpense: (GroupId, ExpenseId) -> Unit,
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
        // Leave horizontal swipes to the group screen's tab pager; the drawer opens from the menu button.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                GroupDrawer(
                    viewModel = viewModel,
                    activeGroup = active,
                    error = error,
                    onSelect = {
                        viewModel.setActiveGroup(it)
                        closeDrawer()
                    },
                    onCreate = { name, currency ->
                        viewModel.createGroup(name, currency)
                        closeDrawer()
                    },
                    onJoin = viewModel::join,
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
            onEditExpense = { onEditExpense(active, it) },
        )
    }
}

@Composable
private fun GroupDrawer(
    viewModel: GroupsViewModel,
    activeGroup: GroupId,
    error: String?,
    onSelect: (GroupId) -> Unit,
    onCreate: (name: String, currencyCode: String) -> Unit,
    onJoin: (code: String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var creating by remember { mutableStateOf(false) }
    var joining by remember { mutableStateOf(false) }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Text(
            stringResource(Res.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(16.dp),
        )

        state.groups.forEach { group ->
            NavigationDrawerItem(
                label = { Text(group.name) },
                selected = group.id == activeGroup,
                badge = { Text(group.baseCurrency.code, color = MaterialTheme.colorScheme.outline) },
                onClick = { onSelect(group.id) },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.groups_new_group)) },
            selected = false,
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            onClick = { creating = !creating },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
        if (creating) {
            CreateGroupForm(onCreate = { name, currency ->
                onCreate(name, currency)
                creating = false
            })
        }

        NavigationDrawerItem(
            label = { Text(stringResource(Res.string.groups_join_group)) },
            selected = false,
            icon = { Icon(Icons.Default.GroupAdd, contentDescription = null) },
            onClick = { joining = !joining },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
        if (joining) {
            JoinGroupForm(onJoin = onJoin, error = error, onInput = viewModel::clearError)
        }

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
