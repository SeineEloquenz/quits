package nz.eloque.quits.ui.group

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nz.eloque.compose_kit.components.SwipeToDismiss
import nz.eloque.compose_kit.input.AbbreviatingText
import nz.eloque.compose_kit.input.SubmittableTextField
import nz.eloque.compose_kit.scaffold.AppScaffold
import nz.eloque.quits.domain.ExpenseId
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.domain.Transfer
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.action_cancel
import nz.eloque.quits.resources.action_copy
import nz.eloque.quits.resources.action_record
import nz.eloque.quits.resources.action_save
import nz.eloque.quits.resources.cd_delete
import nz.eloque.quits.resources.cd_menu
import nz.eloque.quits.resources.cd_more
import nz.eloque.quits.resources.cd_remove
import nz.eloque.quits.resources.cd_rename
import nz.eloque.quits.resources.cd_sync
import nz.eloque.quits.resources.detail_add_expense
import nz.eloque.quits.resources.detail_add_member
import nz.eloque.quits.resources.detail_add_members_first
import nz.eloque.quits.resources.detail_add_members_hint
import nz.eloque.quits.resources.detail_expenses
import nz.eloque.quits.resources.detail_expenses_empty
import nz.eloque.quits.resources.detail_last_synced
import nz.eloque.quits.resources.detail_local_only
import nz.eloque.quits.resources.detail_members
import nz.eloque.quits.resources.detail_not_synced
import nz.eloque.quits.resources.detail_paid_by
import nz.eloque.quits.resources.detail_settle_up
import nz.eloque.quits.resources.detail_share_group
import nz.eloque.quits.resources.detail_share_hint
import nz.eloque.quits.resources.detail_sharing
import nz.eloque.quits.resources.detail_transfer_row
import nz.eloque.quits.resources.dialog_rename_title
import nz.eloque.quits.resources.group_fallback_name
import nz.eloque.quits.resources.label_name
import nz.eloque.quits.resources.label_share_code
import nz.eloque.quits.ui.components.BalanceText
import nz.eloque.quits.ui.components.EmptyHint
import nz.eloque.quits.ui.components.LoadingBox
import nz.eloque.quits.ui.components.MemberAvatar
import nz.eloque.quits.ui.components.MoneyText
import nz.eloque.quits.ui.components.display
import nz.eloque.quits.util.formatDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import qrgenerator.qrkitpainter.rememberQrKitPainter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: GroupId,
    onOpenDrawer: () -> Unit,
    onAddExpense: () -> Unit,
    onEditExpense: (ExpenseId) -> Unit,
) {
    val viewModel = koinViewModel<GroupDetailViewModel>(key = groupId.value) { parametersOf(groupId) }
    val state by viewModel.state.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()

    val scope = rememberCoroutineScope()
    val pagerState = key(groupId) { rememberPagerState(pageCount = { 2 }) }
    var renaming by remember(groupId) { mutableStateOf<Pair<MemberId, String>?>(null) }
    var deleting by remember(groupId) { mutableStateOf<ExpenseRow?>(null) }
    var showShare by remember(groupId) { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(syncStatus) {
        (syncStatus as? SyncStatus.Failed)?.let {
            snackbarHostState.showSnackbar(it.message)
            viewModel.dismissError()
        }
    }

    renaming?.let { (id, current) ->
        RenameMemberDialog(
            initial = current,
            onDismiss = { renaming = null },
            onConfirm = {
                viewModel.renameMember(id, it)
                renaming = null
            },
        )
    }

    deleting?.let { expense ->
        DeleteExpenseDialog(
            title = expense.title,
            onDismiss = { deleting = null },
            onConfirm = {
                viewModel.deleteExpense(expense.id)
                deleting = null
            },
        )
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
            if (pagerState.currentPage == 0 && state.members.isNotEmpty()) {
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
        Column(Modifier.fillMaxSize()) {
            if (!state.loaded) {
                LoadingBox(Modifier.padding(top = 48.dp))
                return@Column
            }

            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text(stringResource(Res.string.detail_expenses)) },
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text(stringResource(Res.string.detail_members)) },
                )
            }

            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val scrollModifier =
                    Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)

                when (page) {
                    0 ->
                        ExpensesTab(
                            state = state,
                            modifier = scrollModifier,
                            onEditExpense = onEditExpense,
                            onDeleteRequest = { deleting = it },
                        )
                    else ->
                        MembersTab(
                            state = state,
                            modifier = scrollModifier,
                            onRename = { renaming = it },
                            onRemove = viewModel::removeMember,
                            onRecord = viewModel::record,
                            onAddMember = viewModel::addMember,
                        )
                }
            }
        }
    }
}

@Composable
private fun ExpensesTab(
    state: GroupDetailUiState,
    modifier: Modifier,
    onEditExpense: (ExpenseId) -> Unit,
    onDeleteRequest: (ExpenseRow) -> Unit,
) {
    Column(modifier) {
        Spacer(Modifier.height(8.dp))
        if (state.expenses.isEmpty()) {
            EmptyHint(
                if (state.members.isEmpty()) {
                    stringResource(Res.string.detail_add_members_first)
                } else {
                    stringResource(Res.string.detail_expenses_empty)
                },
            )
        } else {
            state.expenses.forEach { expense ->
                SwipeToDismiss(
                    onRightSwipe = { onDeleteRequest(expense) },
                    onLeftSwipe = { onDeleteRequest(expense) },
                    leftSwipeBackground = { DeleteBackground() },
                    rightSwipeBackground = { DeleteBackground() },
                ) {
                    ExpenseRowCard(expense = expense, onClick = { onEditExpense(expense.id) })
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(88.dp)) // room for the FAB
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
private fun DeleteBackground() {
    Icon(
        Icons.Default.Delete,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.error,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MembersTab(
    state: GroupDetailUiState,
    modifier: Modifier,
    onRename: (Pair<MemberId, String>) -> Unit,
    onRemove: (MemberId) -> Unit,
    onRecord: (Transfer) -> Unit,
    onAddMember: (String) -> Unit,
) {
    Column(modifier) {
        Spacer(Modifier.height(8.dp))
        if (state.members.isEmpty()) {
            EmptyHint(stringResource(Res.string.detail_add_members_hint))
        } else {
            state.members.forEach { member ->
                MemberBalanceRow(member = member, onRename = onRename, onRemove = onRemove)
            }

            if (state.transfers.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(Res.string.detail_settle_up),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                state.transfers.forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(Res.string.detail_transfer_row, row.from, row.to, row.transfer.amount.display()),
                            Modifier.weight(1f),
                        )
                        TextButton(onClick = { onRecord(row.transfer) }) {
                            Text(stringResource(Res.string.action_record))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        SubmittableTextField(
            label = stringResource(Res.string.detail_add_member),
            imageVector = Icons.Default.Add,
            onSubmit = onAddMember,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MemberBalanceRow(
    member: MemberBalance,
    onRename: (Pair<MemberId, String>) -> Unit,
    onRemove: (MemberId) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemberAvatar(name = member.name, id = member.id, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Text(member.name, Modifier.weight(1f))
        BalanceText(member.net)
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.cd_more))
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.cd_rename, member.name)) },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = {
                        menu = false
                        onRename(member.id to member.name)
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.cd_remove, member.name)) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = {
                        menu = false
                        onRemove(member.id)
                    },
                )
            }
        }
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
                Image(
                    painter = rememberQrKitPainter(data = code),
                    contentDescription = null,
                    modifier = Modifier.size(200.dp).align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.height(12.dp))
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

@Composable
private fun RenameMemberDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(Res.string.label_name)) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text(stringResource(Res.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

@Composable
private fun DeleteExpenseDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.cd_delete, title)) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
            ) {
                Text(stringResource(Res.string.cd_delete, title), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
