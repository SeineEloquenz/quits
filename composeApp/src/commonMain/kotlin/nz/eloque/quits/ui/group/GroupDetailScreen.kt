package nz.eloque.quits.ui.group

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.components.Section
import nz.eloque.compose_kit.input.SubmittableTextField
import nz.eloque.quits.domain.ExpenseId
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.domain.Money
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.action_record
import nz.eloque.quits.resources.cd_back
import nz.eloque.quits.resources.cd_remove
import nz.eloque.quits.resources.cd_rename
import nz.eloque.quits.resources.cd_sync
import nz.eloque.quits.resources.detail_add_member
import nz.eloque.quits.resources.detail_add_members_hint
import nz.eloque.quits.resources.detail_balances
import nz.eloque.quits.resources.detail_last_synced
import nz.eloque.quits.resources.detail_local_only
import nz.eloque.quits.resources.detail_members
import nz.eloque.quits.resources.detail_not_synced
import nz.eloque.quits.resources.detail_settle_up
import nz.eloque.quits.resources.detail_share_group
import nz.eloque.quits.resources.detail_share_hint
import nz.eloque.quits.resources.detail_sharing
import nz.eloque.quits.resources.detail_transfer_row
import nz.eloque.quits.resources.group_fallback_name
import nz.eloque.quits.resources.label_share_code
import nz.eloque.quits.ui.components.EmptyHint
import nz.eloque.quits.ui.components.LoadingBox
import nz.eloque.quits.util.formatDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private fun Money.display(): String = "${toDecimalString()} ${currency.code}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: GroupId,
    onBack: () -> Unit,
    onAddExpense: () -> Unit,
    onEditExpense: (ExpenseId) -> Unit,
) {
    val viewModel = koinViewModel<GroupDetailViewModel> { parametersOf(groupId) }
    val state by viewModel.state.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()

    // (memberId, currentName) of the member being renamed, or null.
    var renaming by remember { mutableStateOf<Pair<MemberId, String>?>(null) }

    renaming?.let { (id, current) ->
        RenameMemberDialog(
            initial = current,
            onDismiss = { renaming = null },
            onConfirm = { name ->
                viewModel.renameMember(id, name)
                renaming = null
            },
        )
    }

    PullToRefreshBox(
        isRefreshing = state.shareCode != null && syncStatus == SyncStatus.Syncing,
        onRefresh = viewModel::sync,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.cd_back))
                }
                Text(
                    state.name.ifEmpty { stringResource(Res.string.group_fallback_name) },
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                )
                if (state.shareCode != null) {
                    if (syncStatus == SyncStatus.Syncing) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = viewModel::sync) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.cd_sync))
                        }
                    }
                }
            }

            if (!state.loaded) {
                LoadingBox(Modifier.padding(top = 48.dp))
                return@Column
            }

            (syncStatus as? SyncStatus.Failed)?.let { failed ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(failed.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::dismissError) { Text("Dismiss") }
                }
            }

            Spacer(Modifier.height(8.dp))

            Section(heading = stringResource(Res.string.detail_sharing)) {
                Column(Modifier.padding(8.dp)) {
                    val code = state.shareCode
                    if (code == null) {
                        Text(stringResource(Res.string.detail_local_only))
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = viewModel::share) { Text(stringResource(Res.string.detail_share_group)) }
                    } else {
                        Text(stringResource(Res.string.label_share_code), style = MaterialTheme.typography.labelMedium)
                        Text(code, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            stringResource(Res.string.detail_share_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        Text(
                            state.lastSyncedAt
                                ?.let { stringResource(Res.string.detail_last_synced, formatDateTime(it)) }
                                ?: stringResource(Res.string.detail_not_synced),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Section(heading = stringResource(Res.string.detail_balances)) {
                Column(Modifier.padding(8.dp)) {
                    if (state.members.isEmpty()) {
                        Text(stringResource(Res.string.detail_add_members_hint))
                    } else {
                        state.members.forEach { member ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(member.name, Modifier.weight(1f))
                                Text(
                                    member.net.display(),
                                    color =
                                        when {
                                            member.net.isPositive -> MaterialTheme.colorScheme.primary
                                            member.net.isNegative -> MaterialTheme.colorScheme.error
                                            else -> MaterialTheme.colorScheme.onSurface
                                        },
                                )
                            }
                        }
                        if (state.transfers.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(Res.string.detail_settle_up), fontWeight = FontWeight.Bold)
                            state.transfers.forEach { row ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        stringResource(
                                            Res.string.detail_transfer_row,
                                            row.from,
                                            row.to,
                                            row.transfer.amount.display(),
                                        ),
                                        Modifier.weight(1f),
                                    )
                                    TextButton(onClick = { viewModel.record(row.transfer) }) {
                                        Text(stringResource(Res.string.action_record))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Section(heading = stringResource(Res.string.detail_members)) {
                Column(Modifier.padding(8.dp)) {
                    state.members.forEach { member ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(member.name, Modifier.weight(1f))
                            IconButton(onClick = { renaming = member.id to member.name }) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(Res.string.cd_rename, member.name))
                            }
                            IconButton(onClick = { viewModel.removeMember(member.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.cd_remove, member.name))
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SubmittableTextField(
                        label = stringResource(Res.string.detail_add_member),
                        imageVector = Icons.Default.Add,
                        onSubmit = viewModel::addMember,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Section(heading = "Expenses") {
                Column(Modifier.padding(8.dp)) {
                    if (state.expenses.isEmpty()) {
                        EmptyHint("No expenses yet.")
                    } else {
                        state.expenses.forEach { expense ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onEditExpense(expense.id) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(expense.title)
                                    Text(
                                        "paid by ${expense.paidBy}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                                Text(expense.total.display())
                                IconButton(onClick = { viewModel.deleteExpense(expense.id) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete ${expense.title}",
                                        tint = MaterialTheme.colorScheme.outline,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onAddExpense,
                        enabled = state.members.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.members.isEmpty()) "Add members first" else "Add expense")
                    }
                }
            }
        }
    }
}

@Composable
private fun RenameMemberDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename member") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
