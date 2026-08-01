package nz.eloque.quits.ui.group

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.input.AbbreviatingText
import nz.eloque.compose_kit.scaffold.AppScaffold
import nz.eloque.quits.domain.Category
import nz.eloque.quits.domain.EntryId
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.domain.isIncome
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.action_cancel
import nz.eloque.quits.resources.action_save
import nz.eloque.quits.resources.cd_back
import nz.eloque.quits.resources.cd_more
import nz.eloque.quits.resources.cd_remove
import nz.eloque.quits.resources.cd_rename
import nz.eloque.quits.resources.detail_note
import nz.eloque.quits.resources.detail_split_unsupported
import nz.eloque.quits.resources.dialog_rename_title
import nz.eloque.quits.resources.label_name
import nz.eloque.quits.resources.member_detail_appears_in
import nz.eloque.quits.resources.member_detail_no_expenses
import nz.eloque.quits.resources.member_detail_not_found
import nz.eloque.quits.resources.member_detail_owed_only
import nz.eloque.quits.resources.member_detail_paid_and_owed
import nz.eloque.quits.resources.member_detail_remove_body_plain
import nz.eloque.quits.resources.member_detail_remove_body_referenced
import nz.eloque.quits.resources.member_detail_remove_title
import nz.eloque.quits.resources.member_detail_status_owed
import nz.eloque.quits.resources.member_detail_status_owes
import nz.eloque.quits.resources.member_detail_status_settled
import nz.eloque.quits.ui.category.CategoryPill
import nz.eloque.quits.ui.category.categoryDisplay
import nz.eloque.quits.ui.components.BalanceText
import nz.eloque.quits.ui.components.EmptyHint
import nz.eloque.quits.ui.components.EntryAmountText
import nz.eloque.quits.ui.components.LoadingBox
import nz.eloque.quits.ui.components.MemberAvatar
import nz.eloque.quits.ui.components.dayGroupLabel
import nz.eloque.quits.ui.components.display
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberDetailScreen(
    groupId: GroupId,
    memberId: MemberId,
    onBack: () -> Unit,
    onOpenEntry: (EntryId) -> Unit,
) {
    val viewModel = koinViewModel<MemberDetailViewModel>(key = memberId.value) { parametersOf(groupId, memberId) }
    val state by viewModel.state.collectAsState()
    val error by viewModel.error.collectAsState()

    var renaming by remember { mutableStateOf(false) }
    var confirmingRemove by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.removed.collect { onBack() }
    }
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    if (renaming) {
        RenameDialog(
            initial = state.name,
            onDismiss = { renaming = false },
            onConfirm = {
                viewModel.rename(it)
                renaming = false
            },
        )
    }

    if (confirmingRemove) {
        RemoveMemberDialog(
            state = state,
            onDismiss = { confirmingRemove = false },
            onConfirm = {
                confirmingRemove = false
                viewModel.remove()
            },
        )
    }

    AppScaffold(
        title = { Text(state.name, style = MaterialTheme.typography.headlineMedium, maxLines = 1) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.cd_back))
            }
        },
        actions = {
            // Rename/remove live behind the overflow menu, matching the group screen's app-bar pattern.
            if (state.loaded && state.found) {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(Res.string.cd_more))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.cd_rename, state.name)) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                renaming = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.cd_remove, state.name)) },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null)
                            },
                            onClick = {
                                menuExpanded = false
                                confirmingRemove = true
                            },
                        )
                    }
                }
            }
        },
        snackbarHostState = snackbarHostState,
    ) { scrollBehavior ->
        if (!state.loaded) {
            LoadingBox(Modifier.padding(top = 32.dp))
            return@AppScaffold
        }
        if (!state.found) {
            EmptyHint(stringResource(Res.string.member_detail_not_found), Modifier.padding(top = 32.dp))
            return@AppScaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                MemberAvatar(name = state.name, id = memberId, size = 64.dp)
                Spacer(Modifier.height(8.dp))
                BalanceText(state.net, style = MaterialTheme.typography.headlineMedium)
                Text(
                    statusWord(state.net.isPositive, state.net.isNegative),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(Res.string.member_detail_appears_in),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(4.dp))
            if (state.entries.isEmpty()) {
                EmptyHint(stringResource(Res.string.member_detail_no_expenses))
            } else {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    var lastDayLabel: String? = null
                    state.entries.forEach { row ->
                        val dayLabel = dayGroupLabel(row.spentAt, row.offsetMinutes)
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
                        MemberEntryCard(
                            row = row,
                            categories = state.customCategories,
                            onClick = { onOpenEntry(row.id) },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun statusWord(
    isPositive: Boolean,
    isNegative: Boolean,
): String =
    when {
        isPositive -> stringResource(Res.string.member_detail_status_owed)
        isNegative -> stringResource(Res.string.member_detail_status_owes)
        else -> stringResource(Res.string.member_detail_status_settled)
    }

@Composable
private fun MemberEntryCard(
    row: MemberEntryRow,
    categories: List<Category>,
    onClick: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                AbbreviatingText(row.title, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    categoryDisplay(row.categoryId, categories)?.let { display ->
                        CategoryPill(display)
                        Spacer(Modifier.width(6.dp))
                    }
                    val meta =
                        if (row.paidByThem.isPositive) {
                            stringResource(Res.string.member_detail_paid_and_owed, row.paidByThem.display(), row.owedByThem.display())
                        } else {
                            stringResource(Res.string.member_detail_owed_only, row.owedByThem.display())
                        }
                    Text(
                        meta,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!row.note.isNullOrBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.Notes,
                            contentDescription = stringResource(Res.string.detail_note),
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    if (!row.splitSupported) {
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
            EntryAmountText(row.total, row.kind.isIncome)
        }
    }
}

@Composable
private fun RenameDialog(
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

/** Consequence-aware confirmation: names what actually happens, using data the domain already exposes. */
@Composable
private fun RemoveMemberDialog(
    state: MemberDetailUiState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.member_detail_remove_title, state.name)) },
        text = {
            val body =
                if (state.entries.isNotEmpty() || state.net.isPositive || state.net.isNegative) {
                    stringResource(Res.string.member_detail_remove_body_referenced, state.name, state.net.display())
                } else {
                    stringResource(Res.string.member_detail_remove_body_plain, state.name)
                }
            Text(body)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.cd_remove, state.name))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}
