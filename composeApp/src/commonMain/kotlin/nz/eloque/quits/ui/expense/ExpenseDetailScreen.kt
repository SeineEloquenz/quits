package nz.eloque.quits.ui.expense

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import nz.eloque.quits.resources.detail_owed_by
import nz.eloque.quits.resources.detail_split_summary_dated
import nz.eloque.quits.resources.editor_expense_fallback_title
import nz.eloque.quits.resources.editor_paid_by
import nz.eloque.quits.resources.editor_split_equal
import nz.eloque.quits.resources.editor_split_exact
import nz.eloque.quits.resources.editor_split_percentage
import nz.eloque.quits.resources.editor_split_shares
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
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(Res.string.detail_edit_expense))
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
                        dayGroupLabel(state.spentAt),
                        splitKindLabel(state.splitKind),
                        state.participantCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(Res.string.editor_paid_by),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            state.paidBy.forEach { row -> ParticipantRow(row) }

            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(Res.string.detail_owed_by),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            state.owedBy.forEach { row -> ParticipantRow(row) }

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
private fun splitKindLabel(kind: SplitKind): String =
    when (kind) {
        SplitKind.EQUAL -> stringResource(Res.string.editor_split_equal)
        SplitKind.SHARES -> stringResource(Res.string.editor_split_shares)
        SplitKind.PERCENTAGE -> stringResource(Res.string.editor_split_percentage)
        SplitKind.EXACT -> stringResource(Res.string.editor_split_exact)
    }
