package nz.eloque.quits.ui.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.scaffold.AppScaffold
import nz.eloque.quits.domain.ExpenseId
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.action_cancel
import nz.eloque.quits.resources.action_delete
import nz.eloque.quits.resources.cd_back
import nz.eloque.quits.resources.detail_delete_expense
import nz.eloque.quits.resources.detail_edit_expense
import nz.eloque.quits.resources.detail_expense_not_found
import nz.eloque.quits.resources.detail_items
import nz.eloque.quits.resources.detail_note
import nz.eloque.quits.resources.detail_owed_by
import nz.eloque.quits.resources.detail_split_summary_dated
import nz.eloque.quits.resources.detail_split_unsupported
import nz.eloque.quits.resources.editor_expense_fallback_title
import nz.eloque.quits.resources.editor_item_label
import nz.eloque.quits.resources.editor_paid_by
import nz.eloque.quits.resources.expense_delete_body
import nz.eloque.quits.resources.expense_delete_title
import nz.eloque.quits.ui.components.EmptyHint
import nz.eloque.quits.ui.components.LoadingBox
import nz.eloque.quits.ui.components.MemberAvatar
import nz.eloque.quits.ui.components.MoneyText
import nz.eloque.quits.ui.components.dayGroupLabel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseDetailScreen(
    groupId: GroupId,
    expenseId: ExpenseId,
    onBack: () -> Unit,
    onEdit: () -> Unit,
) {
    val viewModel = koinViewModel<ExpenseDetailViewModel>(key = expenseId.value) { parametersOf(groupId, expenseId) }
    val state by viewModel.state.collectAsState()
    var confirmingDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.deleted.collect { onBack() }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = {
                Text(
                    stringResource(
                        Res.string.expense_delete_title,
                        state.title.ifEmpty { stringResource(Res.string.editor_expense_fallback_title) },
                    ),
                )
            },
            text = { Text(stringResource(Res.string.expense_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingDelete = false
                        viewModel.delete()
                    },
                ) {
                    Text(stringResource(Res.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }

    AppScaffold(
        title = {
            Text(
                state.title.ifEmpty { stringResource(Res.string.editor_expense_fallback_title) },
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.cd_back))
            }
        },
        actions = {
            if (state.found) {
                // No edit for an unsupported split: re-saving here would rewrite it as a plain Exact
                // split and downgrade it for everyone via sync. Deleting is still allowed.
                if (state.splitSupported) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(Res.string.detail_edit_expense))
                    }
                }
                IconButton(onClick = { confirmingDelete = true }) {
                    Icon(Icons.Default.Delete, contentDescription = stringResource(Res.string.detail_delete_expense))
                }
            }
        },
    ) { scrollBehavior ->
        if (!state.loaded) {
            LoadingBox(Modifier.padding(top = 32.dp))
            return@AppScaffold
        }
        if (!state.found) {
            EmptyHint(stringResource(Res.string.detail_expense_not_found), Modifier.padding(top = 32.dp))
            return@AppScaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                MoneyText(state.total, style = MaterialTheme.typography.headlineLarge)
                Text(
                    stringResource(
                        Res.string.detail_split_summary_dated,
                        dayGroupLabel(state.spentAt, state.tzOffsetMinutes),
                        state.splitKind.label(),
                        state.participantCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                )
                state.category?.takeIf { it.isNotBlank() }?.let { category ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        category,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (!state.splitSupported) {
                Spacer(Modifier.height(16.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            stringResource(Res.string.detail_split_unsupported),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(Res.string.editor_paid_by),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            state.paidBy.forEach { row -> ParticipantRow(row) }

            if (state.items.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(Res.string.detail_items),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                state.items.forEach { item -> ItemRow(item) }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(Res.string.detail_owed_by),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            state.owedBy.forEach { row -> ParticipantRow(row) }

            state.note?.takeIf { it.isNotBlank() }?.let { note ->
                Spacer(Modifier.height(24.dp))
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Notes,
                            contentDescription = stringResource(Res.string.detail_note),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ParticipantRow(row: ExpenseParticipantRow) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemberAvatar(name = row.name, id = row.id, size = 32.dp)
        Text(row.name, Modifier.weight(1f).padding(start = 12.dp))
        MoneyText(row.amount)
    }
}

@Composable
private fun ItemRow(item: ExpenseItemRow) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(item.label.ifEmpty { stringResource(Res.string.editor_item_label) })
            Text(
                item.participants.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        MoneyText(item.amount)
    }
}
