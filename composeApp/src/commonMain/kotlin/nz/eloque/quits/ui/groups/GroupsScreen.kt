package nz.eloque.quits.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.components.Section
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.action_create
import nz.eloque.quits.resources.action_join
import nz.eloque.quits.resources.app_name
import nz.eloque.quits.resources.cd_settings
import nz.eloque.quits.resources.groups_base_currency
import nz.eloque.quits.resources.groups_empty
import nz.eloque.quits.resources.groups_heading
import nz.eloque.quits.resources.groups_join_group
import nz.eloque.quits.resources.groups_new_group
import nz.eloque.quits.resources.label_name
import nz.eloque.quits.resources.label_share_code
import nz.eloque.quits.ui.components.CurrencyPicker
import nz.eloque.quits.ui.components.EmptyHint
import nz.eloque.quits.ui.components.LoadingBox
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(
    onOpenGroup: (GroupId) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel = koinViewModel<GroupsViewModel>()
    val state by viewModel.state.collectAsState()
    val error by viewModel.error.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()

    var name by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(Currency.of("USD")) }
    var joinCode by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.joined.collect { onOpenGroup(it) }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onOpenSettings) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(Res.string.cd_settings))
                }
            }
            Spacer(Modifier.height(16.dp))

            Section(heading = stringResource(Res.string.groups_new_group)) {
                Column(Modifier.padding(8.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(Res.string.label_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    CurrencyPicker(
                        label = stringResource(Res.string.groups_base_currency),
                        selected = currency,
                        onSelected = { currency = it },
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            viewModel.createGroup(name, currency.code)
                            name = ""
                        },
                        enabled = name.isNotBlank(),
                    ) {
                        Text(stringResource(Res.string.action_create))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Section(heading = stringResource(Res.string.groups_join_group)) {
                Column(Modifier.padding(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = joinCode,
                            onValueChange = {
                                joinCode = it.uppercase()
                                if (error != null) viewModel.clearError()
                            },
                            label = { Text(stringResource(Res.string.label_share_code)) },
                            singleLine = true,
                            isError = error != null,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = { viewModel.join(joinCode) },
                            enabled = joinCode.isNotBlank(),
                        ) {
                            Text(stringResource(Res.string.action_join))
                        }
                    }
                    error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Section(heading = stringResource(Res.string.groups_heading)) {
                if (!state.loaded) {
                    LoadingBox()
                } else if (state.groups.isEmpty()) {
                    EmptyHint(stringResource(Res.string.groups_empty))
                } else {
                    Column {
                        state.groups.forEach { group ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenGroup(group.id) }
                                    .padding(16.dp),
                            ) {
                                Text(group.name, Modifier.weight(1f))
                                Text(group.baseCurrency.code, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }
            }
        }
    }
}
