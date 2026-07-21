package nz.eloque.quits.ui.group

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.input.AbbreviatingText
import nz.eloque.compose_kit.input.SubmittableTextField
import nz.eloque.compose_kit.scaffold.AppScaffold
import nz.eloque.quits.domain.ExpenseId
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.action_copy
import nz.eloque.quits.resources.cd_menu
import nz.eloque.quits.resources.cd_sync
import nz.eloque.quits.resources.detail_add_expense
import nz.eloque.quits.resources.detail_add_member
import nz.eloque.quits.resources.detail_add_members_first
import nz.eloque.quits.resources.detail_add_members_hint
import nz.eloque.quits.resources.detail_balances
import nz.eloque.quits.resources.detail_balances_summary
import nz.eloque.quits.resources.detail_expenses
import nz.eloque.quits.resources.detail_expenses_empty
import nz.eloque.quits.resources.detail_last_synced
import nz.eloque.quits.resources.detail_local_only
import nz.eloque.quits.resources.detail_not_synced
import nz.eloque.quits.resources.detail_paid_by
import nz.eloque.quits.resources.detail_settle_up_link
import nz.eloque.quits.resources.detail_settlement_row
import nz.eloque.quits.resources.detail_settlement_title
import nz.eloque.quits.resources.detail_share_group
import nz.eloque.quits.resources.detail_share_hint
import nz.eloque.quits.resources.detail_sharing
import nz.eloque.quits.resources.group_fallback_name
import nz.eloque.quits.resources.label_share_code
import nz.eloque.quits.ui.components.BalanceText
import nz.eloque.quits.ui.components.EmptyHint
import nz.eloque.quits.ui.components.LoadingBox
import nz.eloque.quits.ui.components.MemberAvatar
import nz.eloque.quits.ui.components.MoneyText
import nz.eloque.quits.ui.components.dayGroupLabel
import nz.eloque.quits.util.formatDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: GroupId,
    onOpenDrawer: () -> Unit,
    onAddExpense: () -> Unit,
    onOpenExpense: (ExpenseId) -> Unit,
    onOpenMember: (MemberId) -> Unit,
    onSettleUp: () -> Unit,
) {
    val viewModel = koinViewModel<GroupDetailViewModel>(key = groupId.value) { parametersOf(groupId) }
    val state by viewModel.state.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()

    var balancesExpanded by remember(groupId) { mutableStateOf(false) }
    var showShare by remember(groupId) { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(syncStatus) {
        (syncStatus as? SyncStatus.Failed)?.let {
            snackbarHostState.showSnackbar(it.message)
            viewModel.dismissError()
        }
    }

    if (showShare) {
        ShareSheet(state = state, onShare = viewModel::share, onDismiss = { showShare = false })
    }

    AppScaffold(
        title = {
            AbbreviatingText(
                state.name.ifEmpty { stringResource(Res.string.group_fallback_name) },
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
            )
        },
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Default.Menu, contentDescription = stringResource(Res.string.cd_menu))
            }
        },
        actions = {
            IconButton(onClick = { showShare = true }) {
                Icon(Icons.Default.Share, contentDescription = stringResource(Res.string.detail_sharing))
            }
            if (syncStatus == SyncStatus.Syncing) {
                CircularProgressIndicator(Modifier.padding(12.dp).size(20.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = viewModel::sync) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(Res.string.cd_sync))
                }
            }
        },
        floatingActionButton = {
            if (state.members.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = onAddExpense,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(Res.string.detail_add_expense)) },
                )
            }
        },
        snackbarHostState = snackbarHostState,
        contentHorizontalPadding = 0.dp,
    ) { scrollBehavior ->
        if (!state.loaded) {
            LoadingBox(Modifier.padding(top = 48.dp))
            return@AppScaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))

            BalanceSummary(
                state = state,
                expanded = balancesExpanded,
                onToggle = { balancesExpanded = !balancesExpanded },
                onOpenMember = onOpenMember,
                onSettleUp = onSettleUp,
                onAddMember = viewModel::addMember,
            )

            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(Res.string.detail_expenses),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(4.dp))

            if (state.activity.isEmpty()) {
                EmptyHint(
                    if (state.members.isEmpty()) {
                        stringResource(Res.string.detail_add_members_first)
                    } else {
                        stringResource(Res.string.detail_expenses_empty)
                    },
                )
            } else {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    var lastDayLabel: String? = null
                    state.activity.forEach { entry ->
                        val dayLabel = dayGroupLabel(entry.timestamp)
                        if (dayLabel != lastDayLabel) {
                            if (lastDayLabel != null) Spacer(Modifier.height(8.dp))
                            Text(
                                dayLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                            lastDayLabel = dayLabel
                        }
                        when (entry) {
                            is ActivityEntry.ExpenseEntry -> {
                                val expense = entry.row
                                ExpenseRowCard(expense = expense, onClick = { onOpenExpense(expense.id) })
                            }

                            is ActivityEntry.SettlementEntry -> {
                                SettlementRowCard(entry.row)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            Spacer(Modifier.height(88.dp)) // room for the FAB
        }
    }
}

@Composable
private fun SettlementRowCard(row: SettlementRow) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("⇄", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.outline)
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(stringResource(Res.string.detail_settlement_title), style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(Res.string.detail_settlement_row, row.from, row.to),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            MoneyText(row.amount)
        }
    }
}

@Composable
private fun BalanceSummary(
    state: GroupDetailUiState,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpenMember: (MemberId) -> Unit,
    onSettleUp: () -> Unit,
    onAddMember: (String) -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onToggle)) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.detail_balances), style = MaterialTheme.typography.labelLarge)
                    Text(
                        stringResource(Res.string.detail_balances_summary, state.members.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))

                    if (state.members.isEmpty()) {
                        EmptyHint(stringResource(Res.string.detail_add_members_hint))
                    } else {
                        state.members.forEach { member ->
                            MemberBalanceRow(member = member, onClick = { onOpenMember(member.id) })
                        }

                        if (state.transfers.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                Modifier.fillMaxWidth().clickable(onClick = onSettleUp).padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(Res.string.detail_settle_up_link, state.transfers.size),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    SubmittableTextField(
                        label = stringResource(Res.string.detail_add_member),
                        imageVector = Icons.Default.Add,
                        onSubmit = onAddMember,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExpenseRowCard(
    expense: ExpenseRow,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                AbbreviatingText(expense.title, maxLines = 1)
                Text(
                    stringResource(Res.string.detail_paid_by, expense.paidBy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            MoneyText(expense.total)
        }
    }
}

@Composable
private fun MemberBalanceRow(
    member: MemberBalance,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemberAvatar(name = member.name, id = member.id, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Text(member.name, Modifier.weight(1f))
        BalanceText(member.net)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShareSheet(
    state: GroupDetailUiState,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(stringResource(Res.string.detail_sharing), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            val code = state.shareCode
            if (code == null) {
                Text(stringResource(Res.string.detail_local_only))
                Spacer(Modifier.height(16.dp))
                Button(onClick = onShare) { Text(stringResource(Res.string.detail_share_group)) }
            } else {
                val clipboard = LocalClipboardManager.current

                Text(stringResource(Res.string.label_share_code), style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(code, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = stringResource(Res.string.action_copy))
                    }
                }
                Spacer(Modifier.height(8.dp))
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
            Spacer(Modifier.height(24.dp))
        }
    }
}
