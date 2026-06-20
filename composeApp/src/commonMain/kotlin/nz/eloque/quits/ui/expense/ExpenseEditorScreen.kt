package nz.eloque.quits.ui.expense

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.chip.ChipSelector
import nz.eloque.compose_kit.components.SectionCard
import nz.eloque.compose_kit.scaffold.AppScaffold
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.Money
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
import nz.eloque.quits.resources.editor_remaining
import nz.eloque.quits.resources.editor_remaining_done
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
import nz.eloque.quits.ui.components.display
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
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

    AppScaffold(
        title =
            if (state.editing) {
                stringResource(Res.string.editor_title_edit)
            } else {
                stringResource(Res.string.editor_title_add)
            },
        navigationIcon = {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.action_cancel))
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
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))

            SectionCard {
                Column(Modifier.padding(16.dp)) {
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
                }
            }

            SectionCard(heading = stringResource(Res.string.editor_paid_by)) {
                Column(Modifier.padding(16.dp)) {
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

            SectionCard(heading = stringResource(Res.string.editor_split)) {
                Column(Modifier.padding(16.dp)) {
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

                    RemainingHint(state)
                }
            }

            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp))
            }

            Spacer(Modifier.height(16.dp))

            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text(
                    if (state.editing) {
                        stringResource(Res.string.editor_save_changes)
                    } else {
                        stringResource(Res.string.editor_save_expense)
                    },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Live "remaining to assign" feedback for the splits that must sum to a target (exact, percentage). */
@Composable
private fun RemainingHint(state: ExpenseEditorUiState) {
    val text =
        when (state.splitKind) {
            SplitKind.EXACT -> {
                val currency = Currency.parse(state.currency) ?: return
                val total = paidTotalMinor(state, currency)
                val assigned =
                    state.members.sumOf { m -> Money.parse(state.splitInput[m.id].orEmpty().trim(), currency)?.minorUnits ?: 0L }
                val remaining = Money(total - assigned, currency)
                if (remaining.isZero) {
                    stringResource(Res.string.editor_remaining_done)
                } else {
                    stringResource(Res.string.editor_remaining, remaining.display())
                }
            }
            SplitKind.PERCENTAGE -> {
                val assigned = state.members.sumOf { m -> state.splitInput[m.id].orEmpty().trim().toIntOrNull() ?: 0 }
                val remaining = 100 - assigned
                if (remaining == 0) {
                    stringResource(Res.string.editor_remaining_done)
                } else {
                    stringResource(Res.string.editor_remaining, "$remaining%")
                }
            }
            else -> return
        }

    val done =
        text == stringResource(Res.string.editor_remaining_done)
    Spacer(Modifier.height(8.dp))
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
    )
}

private fun paidTotalMinor(
    state: ExpenseEditorUiState,
    currency: Currency,
): Long = state.members.sumOf { m -> Money.parse(state.paid[m.id].orEmpty().trim(), currency)?.minorUnits ?: 0L }

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
