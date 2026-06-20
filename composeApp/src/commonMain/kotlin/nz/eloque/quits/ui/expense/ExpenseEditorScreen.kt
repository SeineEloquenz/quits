package nz.eloque.quits.ui.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.chip.ChipSelector
import nz.eloque.compose_kit.components.Section
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.action_cancel
import nz.eloque.quits.resources.editor_label_currency
import nz.eloque.quits.resources.editor_label_rate
import nz.eloque.quits.resources.editor_label_title
import nz.eloque.quits.resources.editor_paid_by
import nz.eloque.quits.resources.editor_placeholder_amount
import nz.eloque.quits.resources.editor_placeholder_percent
import nz.eloque.quits.resources.editor_placeholder_shares
import nz.eloque.quits.resources.editor_rate_fetching
import nz.eloque.quits.resources.editor_save_changes
import nz.eloque.quits.resources.editor_save_expense
import nz.eloque.quits.resources.editor_split
import nz.eloque.quits.resources.editor_split_equal
import nz.eloque.quits.resources.editor_split_exact
import nz.eloque.quits.resources.editor_split_percentage
import nz.eloque.quits.resources.editor_split_shares
import nz.eloque.quits.resources.editor_title_add
import nz.eloque.quits.resources.editor_title_edit
import nz.eloque.quits.ui.components.CurrencyPicker
import nz.eloque.quits.ui.components.LoadingBox
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ExpenseEditorScreen(
    groupId: GroupId,
    expenseId: String?,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val viewModel = koinViewModel<ExpenseEditorViewModel> { parametersOf(groupId, expenseId) }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.saved.collect { onDone() }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(
            if (state.editing) stringResource(Res.string.editor_title_edit) else stringResource(Res.string.editor_title_add),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(12.dp))

        if (!state.loaded) {
            LoadingBox(Modifier.padding(top = 32.dp))
            return@Column
        }

        OutlinedTextField(
            value = state.title,
            onValueChange = viewModel::setTitle,
            label = { Text(stringResource(Res.string.editor_label_title)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        CurrencyPicker(
            label = stringResource(Res.string.editor_label_currency),
            selected = Currency.of(state.currency),
            onSelected = { viewModel.setCurrency(it.code) },
        )
        if (state.isForeign) {
            Spacer(Modifier.height(8.dp))
            val notice = state.rateNotice
            OutlinedTextField(
                value = state.rate,
                onValueChange = viewModel::setRate,
                label = { Text(stringResource(Res.string.editor_label_rate, state.baseCurrency.code)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText =
                    when {
                        state.fetchingRate -> ({ Text(stringResource(Res.string.editor_rate_fetching)) })
                        notice != null -> ({ Text(notice) })
                        else -> null
                    },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(8.dp))

        Section(heading = stringResource(Res.string.editor_paid_by)) {
            Column(Modifier.padding(8.dp)) {
                state.members.forEach { member ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(member.name, Modifier.weight(1f))
                        OutlinedTextField(
                            value = state.paid[member.id].orEmpty(),
                            onValueChange = { viewModel.setPaid(member.id, it) },
                            placeholder = { Text("0") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.width(140.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Section(heading = stringResource(Res.string.editor_split)) {
            Column(Modifier.padding(8.dp)) {
                val splitLabels = SplitKind.entries.associateWith { splitLabel(it) }
                ChipSelector(
                    options = SplitKind.entries,
                    selectedOptions = listOf(state.splitKind),
                    onOptionSelected = viewModel::setKind,
                    onOptionDeselected = {},
                    optionLabel = { splitLabels.getValue(it) },
                )
                Spacer(Modifier.height(8.dp))

                state.members.forEach { member ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        when (state.splitKind) {
                            SplitKind.EQUAL -> {
                                Checkbox(
                                    checked = member.id in state.equalSelected,
                                    onCheckedChange = { viewModel.toggleEqual(member.id) },
                                )
                                Text(member.name, Modifier.weight(1f).padding(start = 8.dp))
                            }
                            else -> {
                                Text(member.name, Modifier.weight(1f))
                                OutlinedTextField(
                                    value = state.splitInput[member.id].orEmpty(),
                                    onValueChange = { viewModel.setSplitInput(member.id, it) },
                                    placeholder = { Text(splitPlaceholder(state.splitKind)) },
                                    singleLine = true,
                                    keyboardOptions =
                                        KeyboardOptions(
                                            keyboardType =
                                                if (state.splitKind == SplitKind.EXACT) {
                                                    KeyboardType.Decimal
                                                } else {
                                                    KeyboardType.Number
                                                },
                                        ),
                                    modifier = Modifier.width(140.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        state.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onCancel) { Text(stringResource(Res.string.action_cancel)) }
            Button(onClick = viewModel::save, modifier = Modifier.weight(1f)) {
                Text(
                    if (state.editing) {
                        stringResource(Res.string.editor_save_changes)
                    } else {
                        stringResource(
                            Res.string.editor_save_expense,
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun splitLabel(kind: SplitKind): String =
    when (kind) {
        SplitKind.EQUAL -> stringResource(Res.string.editor_split_equal)
        SplitKind.SHARES -> stringResource(Res.string.editor_split_shares)
        SplitKind.PERCENTAGE -> stringResource(Res.string.editor_split_percentage)
        SplitKind.EXACT -> stringResource(Res.string.editor_split_exact)
    }

@Composable
private fun splitPlaceholder(kind: SplitKind): String =
    when (kind) {
        SplitKind.SHARES -> stringResource(Res.string.editor_placeholder_shares)
        SplitKind.PERCENTAGE -> stringResource(Res.string.editor_placeholder_percent)
        SplitKind.EXACT -> stringResource(Res.string.editor_placeholder_amount)
        SplitKind.EQUAL -> ""
    }
