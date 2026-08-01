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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.input.SearchablePickerField
import nz.eloque.compose_kit.scaffold.AppScaffold
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.SettlementId
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.action_cancel
import nz.eloque.quits.resources.action_delete
import nz.eloque.quits.resources.action_ok
import nz.eloque.quits.resources.editor_label_amount
import nz.eloque.quits.resources.editor_label_date
import nz.eloque.quits.resources.editor_label_note
import nz.eloque.quits.resources.editor_label_time
import nz.eloque.quits.resources.editor_save_changes
import nz.eloque.quits.resources.error_invalid_total
import nz.eloque.quits.resources.settle_up_from
import nz.eloque.quits.resources.settle_up_to
import nz.eloque.quits.resources.settlement_delete_body
import nz.eloque.quits.resources.settlement_delete_title
import nz.eloque.quits.resources.settlement_editor_title
import nz.eloque.quits.resources.settlement_not_found
import nz.eloque.quits.ui.components.EmptyHint
import nz.eloque.quits.ui.components.LoadingBox
import nz.eloque.quits.ui.components.isValidAmountInput
import nz.eloque.quits.util.formatLocalDate
import nz.eloque.quits.util.formatLocalTime
import nz.eloque.quits.util.localDateMillisUtc
import nz.eloque.quits.util.localHourMinute
import nz.eloque.quits.util.offsetZone
import nz.eloque.quits.util.withPickedDate
import nz.eloque.quits.util.withPickedTime
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
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.saved.collect { onBack() }
    }
    LaunchedEffect(Unit) {
        viewModel.deleted.collect { onBack() }
    }

    if (showDatePicker) {
        val zone = offsetZone(state.tzOffsetMinutes)
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = localDateMillisUtc(state.paidAt, zone))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { viewModel.setPaidAt(withPickedDate(state.paidAt, it, zone)) }
                    showDatePicker = false
                }) { Text(stringResource(Res.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(Res.string.action_cancel)) }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showTimePicker) {
        val zone = offsetZone(state.tzOffsetMinutes)
        val (initialHour, initialMinute) = remember { localHourMinute(state.paidAt, zone) }
        val timeState = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute)
        BasicAlertDialog(onDismissRequest = { showTimePicker = false }) {
            Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
                Column(Modifier.padding(20.dp)) {
                    TimePicker(state = timeState)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text(stringResource(Res.string.action_cancel))
                        }
                        TextButton(onClick = {
                            viewModel.setPaidAt(withPickedTime(state.paidAt, timeState.hour, timeState.minute, zone))
                            showTimePicker = false
                        }) { Text(stringResource(Res.string.action_ok)) }
                    }
                }
            }
        }
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
        title = { Text(stringResource(Res.string.settlement_editor_title), style = MaterialTheme.typography.headlineMedium) },
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))

            val from = state.from
            if (from != null) {
                SearchablePickerField(
                    label = stringResource(Res.string.settle_up_from),
                    selected = from,
                    selectedLabel = { it.name },
                    onSelected = { viewModel.setFrom(it.id) },
                    search = { query -> state.members.filter { it.name.contains(query, ignoreCase = true) } },
                    itemKey = { it.id.value },
                    itemLabel = { it.name },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val to = state.to
            if (to != null) {
                SearchablePickerField(
                    label = stringResource(Res.string.settle_up_to),
                    selected = to,
                    selectedLabel = { it.name },
                    onSelected = { viewModel.setTo(it.id) },
                    search = { query -> state.members.filter { it.name.contains(query, ignoreCase = true) } },
                    itemKey = { it.id.value },
                    itemLabel = { it.name },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            val amountValid = isValidAmountInput(state.amount, state.currency)
            OutlinedTextField(
                value = state.amount,
                onValueChange = viewModel::setAmount,
                label = { Text(stringResource(Res.string.editor_label_amount)) },
                singleLine = true,
                isError = !amountValid,
                supportingText = if (!amountValid) ({ Text(stringResource(Res.string.error_invalid_total)) }) else null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            // Read-only fields; a transparent overlay opens the relevant picker on tap.
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    OutlinedTextField(
                        value = formatLocalDate(state.paidAt, state.tzOffsetMinutes),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(Res.string.editor_label_date)) },
                        trailingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Box(Modifier.matchParentSize().clickable { showDatePicker = true })
                }
                Box {
                    OutlinedTextField(
                        value = formatLocalTime(state.paidAt, state.tzOffsetMinutes),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(Res.string.editor_label_time)) },
                        modifier = Modifier.width(116.dp),
                    )
                    Box(Modifier.matchParentSize().clickable { showTimePicker = true })
                }
            }

            OutlinedTextField(
                value = state.note,
                onValueChange = viewModel::setNote,
                label = { Text(stringResource(Res.string.editor_label_note)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            Button(onClick = viewModel::save, enabled = state.isValid(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.editor_save_changes))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
