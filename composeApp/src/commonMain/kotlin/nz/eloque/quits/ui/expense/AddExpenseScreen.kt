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
import androidx.compose.material3.FilterChip
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
import nz.eloque.compose_kit.components.Section
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.GroupId
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AddExpenseScreen(
    groupId: GroupId,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val viewModel = koinViewModel<AddExpenseViewModel> { parametersOf(groupId) }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.saved.collect { onDone() }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Add expense", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.title,
            onValueChange = viewModel::setTitle,
            label = { Text("Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.currency,
                onValueChange = viewModel::setCurrency,
                label = { Text("Currency") },
                singleLine = true,
                isError = !Currency.isValidCode(state.currency),
                modifier = Modifier.width(140.dp),
            )
            if (state.isForeign) {
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = state.rate,
                    onValueChange = viewModel::setRate,
                    label = { Text("Rate → ${state.baseCurrency.code}") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Section(heading = "Paid by") {
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

        Section(heading = "Split") {
            Column(Modifier.padding(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SplitKind.entries.forEach { kind ->
                        FilterChip(
                            selected = state.splitKind == kind,
                            onClick = { viewModel.setKind(kind) },
                            label = { Text(kind.name.lowercase().replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
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
            TextButton(onClick = onCancel) { Text("Cancel") }
            Button(onClick = viewModel::save, modifier = Modifier.weight(1f)) { Text("Save expense") }
        }
    }
}

private fun splitPlaceholder(kind: SplitKind): String =
    when (kind) {
        SplitKind.SHARES -> "shares"
        SplitKind.PERCENTAGE -> "%"
        SplitKind.EXACT -> "amount"
        SplitKind.EQUAL -> ""
    }
