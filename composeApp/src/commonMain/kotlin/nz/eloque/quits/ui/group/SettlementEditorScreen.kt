package nz.eloque.quits.ui.group

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.scaffold.AppScaffold
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.SettlementId
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.action_cancel
import nz.eloque.quits.resources.action_delete
import nz.eloque.quits.resources.editor_save_changes
import nz.eloque.quits.resources.settlement_delete_body
import nz.eloque.quits.resources.settlement_delete_title
import nz.eloque.quits.resources.settlement_editor_title
import nz.eloque.quits.resources.settlement_not_found
import nz.eloque.quits.ui.components.EmptyHint
import nz.eloque.quits.ui.components.LoadingBox
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettlementEditorScreen(
    groupId: GroupId,
    settlementId: SettlementId,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<SettlementEditorViewModel> { parametersOf(groupId, settlementId) }
    val state by viewModel.state.collectAsState()
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.saved.collect { onBack() }
    }
    LaunchedEffect(Unit) {
        viewModel.deleted.collect { onBack() }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(Res.string.settlement_delete_title)) },
            text = { Text(stringResource(Res.string.settlement_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    viewModel.delete()
                }) {
                    Text(stringResource(Res.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text(stringResource(Res.string.action_cancel)) }
            },
        )
    }

    AppScaffold(
        title = { Text(stringResource(Res.string.settlement_editor_title), style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.action_cancel))
            }
        },
        actions = {
            if (state.found) {
                IconButton(onClick = { showDelete = true }) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(Res.string.settlement_delete_title),
                    )
                }
            }
        },
    ) { scrollBehavior ->
        if (!state.loaded) {
            LoadingBox(Modifier.padding(top = 32.dp))
            return@AppScaffold
        }
        if (!state.found) {
            EmptyHint(stringResource(Res.string.settlement_not_found))
            return@AppScaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))

            val from = state.from
            val to = state.to
            if (from != null && to != null) {
                SettlementFields(
                    members = state.members,
                    from = from,
                    to = to,
                    onFrom = { viewModel.setFrom(it.id) },
                    onTo = { viewModel.setTo(it.id) },
                    currency = state.currency,
                    amount = state.amount,
                    onAmount = viewModel::setAmount,
                    note = state.note,
                    onNote = viewModel::setNote,
                    timestamp = state.paidAt,
                    tzOffsetMinutes = state.tzOffsetMinutes,
                    onTimestamp = viewModel::setPaidAt,
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = viewModel::save, enabled = state.isValid(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.editor_save_changes))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
