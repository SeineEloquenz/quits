package nz.eloque.quits.ui.group

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.input.SearchablePickerField
import nz.eloque.compose_kit.scaffold.AppScaffold
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.Money
import nz.eloque.quits.domain.Transfer
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.action_record
import nz.eloque.quits.resources.cd_back
import nz.eloque.quits.resources.detail_settle_up
import nz.eloque.quits.resources.detail_transfer_row
import nz.eloque.quits.resources.editor_placeholder_amount
import nz.eloque.quits.resources.settle_up_custom_link
import nz.eloque.quits.resources.settle_up_from
import nz.eloque.quits.resources.settle_up_none
import nz.eloque.quits.resources.settle_up_to
import nz.eloque.quits.ui.components.EmptyHint
import nz.eloque.quits.ui.components.LoadingBox
import nz.eloque.quits.ui.components.display
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleUpScreen(
    groupId: GroupId,
    onBack: () -> Unit,
) {
    // Same aggregate as GroupDetailScreen, keyed by group — reactive to the same balances, so
    // recording here or there shows up identically either way.
    val viewModel = koinViewModel<GroupDetailViewModel>(key = groupId.value) { parametersOf(groupId) }
    val state by viewModel.state.collectAsState()

    AppScaffold(
        title = { Text(stringResource(Res.string.detail_settle_up), style = MaterialTheme.typography.headlineMedium) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.cd_back))
            }
        },
    ) { scrollBehavior ->
        if (!state.loaded) {
            LoadingBox(Modifier.padding(top = 32.dp))
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

            if (state.transfers.isEmpty()) {
                EmptyHint(stringResource(Res.string.settle_up_none))
            } else {
                state.transfers.forEach { row ->
                    ElevatedCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
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
                            Button(onClick = { viewModel.record(row.transfer) }) {
                                Text(stringResource(Res.string.action_record))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            CustomSettlementForm(state = state, onRecord = viewModel::record)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** "Record a different amount": any from → to → amount, not just the suggested minimal set. */
@Composable
private fun CustomSettlementForm(
    state: GroupDetailUiState,
    onRecord: (Transfer) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    if (!expanded) {
        TextButton(onClick = { expanded = true }) {
            Text(stringResource(Res.string.settle_up_custom_link))
        }
        return
    }
    if (state.members.size < 2) return

    var from by remember { mutableStateOf(state.members[0]) }
    var to by remember { mutableStateOf(state.members[1]) }
    var amount by remember { mutableStateOf("") }

    Column {
        SearchablePickerField(
            label = stringResource(Res.string.settle_up_from),
            selected = from,
            selectedLabel = { it.name },
            onSelected = { from = it },
            search = { query -> state.members.filter { it.name.contains(query, ignoreCase = true) } },
            itemKey = { it.id.value },
            itemLabel = { it.name },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        SearchablePickerField(
            label = stringResource(Res.string.settle_up_to),
            selected = to,
            selectedLabel = { it.name },
            onSelected = { to = it },
            search = { query -> state.members.filter { it.name.contains(query, ignoreCase = true) } },
            itemKey = { it.id.value },
            itemLabel = { it.name },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text(stringResource(Res.string.editor_placeholder_amount)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val money = Money.parse(amount, state.baseCurrency) ?: return@Button
                if (from.id == to.id || !money.isPositive) return@Button
                onRecord(Transfer(from.id, to.id, money))
                amount = ""
                expanded = false
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.action_record))
        }
    }
}
