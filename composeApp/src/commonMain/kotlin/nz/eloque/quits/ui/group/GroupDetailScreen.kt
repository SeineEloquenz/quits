package nz.eloque.quits.ui.group

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.input.AbbreviatingText
import nz.eloque.compose_kit.input.SubmittableTextField
import nz.eloque.compose_kit.scaffold.AppScaffold
import nz.eloque.quits.data.invite.InviteLink
import nz.eloque.quits.domain.Category
import nz.eloque.quits.domain.CategoryId
import nz.eloque.quits.domain.EntryId
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.domain.SettlementId
import nz.eloque.quits.domain.isIncome
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.action_cancel
import nz.eloque.quits.resources.action_copy
import nz.eloque.quits.resources.action_copy_link
import nz.eloque.quits.resources.action_share_link
import nz.eloque.quits.resources.cd_clear_search
import nz.eloque.quits.resources.cd_menu
import nz.eloque.quits.resources.cd_more
import nz.eloque.quits.resources.cd_search
import nz.eloque.quits.resources.cd_sync
import nz.eloque.quits.resources.detail_add_expense
import nz.eloque.quits.resources.detail_add_income
import nz.eloque.quits.resources.detail_add_member
import nz.eloque.quits.resources.detail_add_members_first
import nz.eloque.quits.resources.detail_add_members_hint
import nz.eloque.quits.resources.detail_balances
import nz.eloque.quits.resources.detail_balances_summary
import nz.eloque.quits.resources.detail_expenses
import nz.eloque.quits.resources.detail_expenses_empty
import nz.eloque.quits.resources.detail_last_synced
import nz.eloque.quits.resources.detail_local_only
import nz.eloque.quits.resources.detail_no_matches
import nz.eloque.quits.resources.detail_not_synced
import nz.eloque.quits.resources.detail_note
import nz.eloque.quits.resources.detail_search_hint
import nz.eloque.quits.resources.detail_settle_up_link
import nz.eloque.quits.resources.detail_settlement_row
import nz.eloque.quits.resources.detail_settlement_title
import nz.eloque.quits.resources.detail_share_group
import nz.eloque.quits.resources.detail_share_hint
import nz.eloque.quits.resources.detail_sharing
import nz.eloque.quits.resources.detail_split_unsupported
import nz.eloque.quits.resources.export_csv_menu
import nz.eloque.quits.resources.group_fallback_name
import nz.eloque.quits.resources.group_leave_body_local
import nz.eloque.quits.resources.group_leave_body_shared
import nz.eloque.quits.resources.group_leave_confirm
import nz.eloque.quits.resources.group_leave_menu
import nz.eloque.quits.resources.group_leave_title
import nz.eloque.quits.resources.label_share_code
import nz.eloque.quits.resources.stats_title
import nz.eloque.quits.resources.stats_uncategorized
import nz.eloque.quits.ui.category.CategoryPill
import nz.eloque.quits.ui.category.categoryDisplay
import nz.eloque.quits.ui.components.BalanceText
import nz.eloque.quits.ui.components.EmptyHint
import nz.eloque.quits.ui.components.EntryAmountText
import nz.eloque.quits.ui.components.LoadingBox
import nz.eloque.quits.ui.components.MemberAvatar
import nz.eloque.quits.ui.components.MoneyText
import nz.eloque.quits.ui.components.dayGroupLabel
import nz.eloque.quits.ui.entry.payerFeedRes
import nz.eloque.quits.util.Sharer
import nz.eloque.quits.util.formatLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupDetailScreen(
    groupId: GroupId,
    onOpenDrawer: () -> Unit,
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    onOpenEntry: (EntryId) -> Unit,
    onOpenMember: (MemberId) -> Unit,
    onOpenSettlement: (SettlementId) -> Unit,
    onSettleUp: () -> Unit,
    onOpenStats: () -> Unit,
) {
    val viewModel = koinViewModel<GroupDetailViewModel>(key = groupId.value) { parametersOf(groupId) }
    val state by viewModel.state.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()

    var balancesExpanded by remember(groupId) { mutableStateOf(false) }
    var showShare by remember(groupId) { mutableStateOf(false) }
    var menuExpanded by remember(groupId) { mutableStateOf(false) }
    var showLeave by remember(groupId) { mutableStateOf(false) }
    var showSearch by remember(groupId) { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(syncStatus) {
        (syncStatus as? SyncStatus.Failed)?.let {
            snackbarHostState.showSnackbar(it.message)
            viewModel.dismissError()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    if (showShare) {
        ShareSheet(state = state, onShare = viewModel::share, onDismiss = { showShare = false })
    }

    if (showLeave) {
        LeaveGroupDialog(
            groupName = state.name,
            shared = state.shareCode != null,
            onConfirm = {
                showLeave = false
                viewModel.leave()
            },
            onDismiss = { showLeave = false },
        )
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
            IconButton(onClick = {
                showSearch = !showSearch
                if (!showSearch) viewModel.clearFilters()
            }) {
                Icon(Icons.Default.Search, contentDescription = stringResource(Res.string.cd_search))
            }
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
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.cd_more))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.stats_title)) },
                        leadingIcon = { Icon(Icons.Default.BarChart, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onOpenStats()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.export_csv_menu)) },
                        leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            viewModel.exportCsv()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.group_leave_menu)) },
                        leadingIcon = {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        },
                        onClick = {
                            menuExpanded = false
                            showLeave = true
                        },
                    )
                }
            }
        },
        floatingActionButton = {
            if (state.members.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FloatingActionButton(
                        onClick = onAddIncome,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Icon(Icons.Default.Savings, contentDescription = stringResource(Res.string.detail_add_income))
                    }
                    ExtendedFloatingActionButton(
                        onClick = onAddExpense,
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text(stringResource(Res.string.detail_add_expense)) },
                    )
                }
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

            AnimatedVisibility(visible = showSearch) {
                ActivitySearchBar(
                    filter = state.filter,
                    categoryIds = state.categoryIds,
                    customCategories = state.customCategories,
                    members = state.members,
                    onQuery = viewModel::setQuery,
                    onToggleCategory = viewModel::toggleCategoryFilter,
                    onToggleMember = viewModel::toggleMemberFilter,
                )
            }

            if (state.activity.isEmpty()) {
                EmptyHint(
                    when {
                        state.filter.isActive -> stringResource(Res.string.detail_no_matches)
                        state.members.isEmpty() -> stringResource(Res.string.detail_add_members_first)
                        else -> stringResource(Res.string.detail_expenses_empty)
                    },
                )
            } else {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    var lastDayLabel: String? = null
                    state.activity.forEach { item ->
                        val dayLabel = dayGroupLabel(item.timestamp, item.offsetMinutes)
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
                        when (item) {
                            is ActivityItem.EntryItem -> {
                                val entry = item.row
                                EntryRowCard(
                                    entry = entry,
                                    categories = state.customCategories,
                                    onClick = { onOpenEntry(entry.id) },
                                )
                            }

                            is ActivityItem.SettlementItem -> {
                                val settlement = item.row
                                SettlementRowCard(settlement, onClick = { onOpenSettlement(settlement.id) })
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
private fun ActivitySearchBar(
    filter: ActivityFilter,
    categoryIds: List<CategoryId>,
    customCategories: List<Category>,
    members: List<MemberBalance>,
    onQuery: (String) -> Unit,
    onToggleCategory: (CategoryId) -> Unit,
    onToggleMember: (MemberId) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = filter.query,
            onValueChange = onQuery,
            label = { Text(stringResource(Res.string.detail_search_hint)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (filter.query.isNotEmpty()) {
                    IconButton(onClick = { onQuery("") }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.cd_clear_search))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (categoryIds.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categoryIds.forEach { id ->
                    val display = categoryDisplay(id, customCategories)
                    FilterChip(
                        selected = filter.categoryId == id,
                        onClick = { onToggleCategory(id) },
                        label = { Text(display?.name ?: stringResource(Res.string.stats_uncategorized)) },
                        leadingIcon =
                            display?.let {
                                { Icon(it.icon, contentDescription = null, tint = it.color, modifier = Modifier.size(18.dp)) }
                            },
                    )
                }
            }
        }
        if (members.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                members.forEach { member ->
                    FilterChip(
                        selected = member.id in filter.members,
                        onClick = { onToggleMember(member.id) },
                        label = { Text(member.name) },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SettlementRowCard(
    row: SettlementRow,
    onClick: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
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
private fun EntryRowCard(
    entry: EntryRow,
    categories: List<Category>,
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
                AbbreviatingText(entry.title, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    categoryDisplay(entry.categoryId, categories)?.let { display ->
                        CategoryPill(display)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        stringResource(entry.kind.payerFeedRes(), entry.paidBy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!entry.note.isNullOrBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.Notes,
                            contentDescription = stringResource(Res.string.detail_note),
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    if (!entry.splitSupported) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Info,
                            contentDescription = stringResource(Res.string.detail_split_unsupported),
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
            EntryAmountText(entry.total, entry.kind.isIncome)
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

/**
 * Confirms leaving the active group, which removes it from this device
 */
@Composable
private fun LeaveGroupDialog(
    groupName: String,
    shared: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val name = groupName.ifEmpty { stringResource(Res.string.group_fallback_name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.group_leave_title)) },
        text = {
            Text(
                if (shared) {
                    stringResource(Res.string.group_leave_body_shared, name)
                } else {
                    stringResource(Res.string.group_leave_body_local, name)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.group_leave_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
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
                val sharer = koinInject<Sharer>()
                val link = InviteLink.build(code)

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { sharer.share(link) }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Share, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.action_share_link))
                    }
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(link)) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(Res.string.action_copy_link))
                    }
                }
                Spacer(Modifier.height(16.dp))

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
                        ?.let { stringResource(Res.string.detail_last_synced, formatLocalDateTime(it)) }
                        ?: stringResource(Res.string.detail_not_synced),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
