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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.input.SearchablePickerField
import nz.eloque.compose_kit.scaffold.AppScaffold
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.Money
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.action_cancel
import nz.eloque.quits.resources.action_ok
import nz.eloque.quits.resources.action_record
import nz.eloque.quits.resources.cd_back
import nz.eloque.quits.resources.detail_settle_up
import nz.eloque.quits.resources.editor_label_amount
import nz.eloque.quits.resources.editor_label_date
import nz.eloque.quits.resources.editor_label_note
import nz.eloque.quits.resources.editor_label_time
import nz.eloque.quits.resources.error_invalid_total
import nz.eloque.quits.resources.settle_up_all_settled
import nz.eloque.quits.resources.settle_up_custom_link
import nz.eloque.quits.resources.settle_up_from
import nz.eloque.quits.resources.settle_up_none
import nz.eloque.quits.resources.settle_up_payer_owes
import nz.eloque.quits.resources.settle_up_record_title
import nz.eloque.quits.resources.settle_up_suggested
import nz.eloque.quits.resources.settle_up_to
import nz.eloque.quits.ui.components.LoadingBox
import nz.eloque.quits.ui.components.MemberAvatar
import nz.eloque.quits.ui.components.MoneyText
import nz.eloque.quits.ui.components.isValidAmountInput
import nz.eloque.quits.util.currentOffsetMinutes
import nz.eloque.quits.util.formatLocalDate
import nz.eloque.quits.util.formatLocalTime
import nz.eloque.quits.util.localDateMillisUtc
import nz.eloque.quits.util.localHourMinute
import nz.eloque.quits.util.nowMillis
import nz.eloque.quits.util.offsetZone
import nz.eloque.quits.util.withPickedDate
import nz.eloque.quits.util.withPickedTime
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

    // Non-null while the record sheet is open; carries what to prefill (a tapped suggestion, or
    // nulls for a from-scratch custom payment).
    var recording by remember { mutableStateOf<RecordTarget?>(null) }

    recording?.let { target ->
        RecordSettlementSheet(
            members = state.members,
            baseCurrency = state.baseCurrency,
            initialFrom = target.from,
            initialTo = target.to,
            initialAmount = target.amount,
            onDismiss = { recording = null },
            onRecord = { from, to, amount, note, paidAt, tzOffset ->
                viewModel.record(from.id, to.id, amount, note, paidAt, tzOffset)
                recording = null
            },
        )
    }

    AppScaffold(
        title = { Text(stringResource(Res.string.detail_settle_up), style = MaterialTheme.typography.titleLarge) },
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState()),
        ) {
            if (state.transfers.isEmpty()) {
                SettledUpHint()
            } else {
                Text(
                    stringResource(Res.string.settle_up_suggested),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                // Grouped by who pays, so a debtor who owes several people reads as one block
                // instead of repeating their name down every row.
                state.transfers.groupBy { it.transfer.from }.forEach { (_, rows) ->
                    PayerGroup(
                        rows = rows,
                        onSelect = { row ->
                            val from = state.members.firstOrNull { it.id == row.transfer.from }
                            val to = state.members.firstOrNull { it.id == row.transfer.to }
                            if (from != null && to != null) {
                                recording = RecordTarget(from, to, row.transfer.amount)
                            }
                        },
                    )
                }
            }

            if (state.members.size >= 2) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                TextButton(onClick = { recording = RecordTarget(null, null, null) }) {
                    Text(stringResource(Res.string.settle_up_custom_link))
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** What the record sheet should prefill: a tapped suggestion's parties/amount, or nulls for custom. */
private data class RecordTarget(
    val from: MemberBalance?,
    val to: MemberBalance?,
    val amount: Money?,
)

@Composable
private fun SettledUpHint() {
    Column(
        Modifier.fillMaxWidth().padding(top = 48.dp, start = 32.dp, end = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(Res.string.settle_up_all_settled), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(Res.string.settle_up_none),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * All of one payer's suggested payments: a header naming the debtor once, then a tappable row per
 * creditor (arrow + avatar + name + amount). Amounts share a right-aligned column so the block reads
 * cleanly regardless of name lengths.
 */
@Composable
private fun PayerGroup(
    rows: List<TransferRow>,
    onSelect: (TransferRow) -> Unit,
) {
    val payer = rows.first()
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MemberAvatar(name = payer.from, id = payer.transfer.from, size = 28.dp)
                Text(
                    stringResource(Res.string.settle_up_payer_owes, payer.from),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            rows.forEach { row ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(row) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(20.dp),
                    )
                    MemberAvatar(name = row.to, id = row.transfer.to, size = 32.dp)
                    Text(row.to, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    MoneyText(row.transfer.amount, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/**
 * One sheet for every way to record a payment. Prefilled from a tapped suggestion (parties + full
 * amount) or empty for a custom one; the amount stays editable so a partial payment is just typing a
 * smaller number. Note and date are optional and default to "no note, now".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordSettlementSheet(
    members: List<MemberBalance>,
    baseCurrency: Currency,
    initialFrom: MemberBalance?,
    initialTo: MemberBalance?,
    initialAmount: Money?,
    onDismiss: () -> Unit,
    onRecord: (from: MemberBalance, to: MemberBalance, amount: Money, note: String?, paidAt: Long, tzOffset: Int) -> Unit,
) {
    if (members.size < 2) return

    val start = initialFrom ?: members[0]
    var from by remember { mutableStateOf(start) }
    var to by remember { mutableStateOf(initialTo ?: members.first { it.id != start.id }) }
    var amount by remember { mutableStateOf(initialAmount?.toDecimalString() ?: "") }
    var note by remember { mutableStateOf("") }
    // The offset is captured once, as "now" in the enterer's zone; date/time edits keep it fixed.
    val tzOffset = remember { currentOffsetMinutes() }
    var paidAt by remember { mutableStateOf(nowMillis()) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val zone = offsetZone(tzOffset)
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = localDateMillisUtc(paidAt, zone))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { paidAt = withPickedDate(paidAt, it, zone) }
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
        val zone = offsetZone(tzOffset)
        val (initialHour, initialMinute) = remember { localHourMinute(paidAt, zone) }
        val timeState = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute)
        BasicAlertDialog(onDismissRequest = { showTimePicker = false }) {
            Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
                Column(Modifier.padding(20.dp)) {
                    TimePicker(state = timeState)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showTimePicker = false }) { Text(stringResource(Res.string.action_cancel)) }
                        TextButton(onClick = {
                            paidAt = withPickedTime(paidAt, timeState.hour, timeState.minute, zone)
                            showTimePicker = false
                        }) { Text(stringResource(Res.string.action_ok)) }
                    }
                }
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).imePadding(),
        ) {
            Text(stringResource(Res.string.settle_up_record_title), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            SearchablePickerField(
                label = stringResource(Res.string.settle_up_from),
                selected = from,
                selectedLabel = { it.name },
                onSelected = { from = it },
                search = { query -> members.filter { it.name.contains(query, ignoreCase = true) } },
                itemKey = { it.id.value },
                itemLabel = { it.name },
                modifier = Modifier.fillMaxWidth(),
            )
            SearchablePickerField(
                label = stringResource(Res.string.settle_up_to),
                selected = to,
                selectedLabel = { it.name },
                onSelected = { to = it },
                search = { query -> members.filter { it.name.contains(query, ignoreCase = true) } },
                itemKey = { it.id.value },
                itemLabel = { it.name },
                modifier = Modifier.fillMaxWidth(),
            )

            val amountValid = isValidAmountInput(amount, baseCurrency)
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
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
                        value = formatLocalDate(paidAt, tzOffset),
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
                        value = formatLocalTime(paidAt, tzOffset),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(Res.string.editor_label_time)) },
                        modifier = Modifier.width(116.dp),
                    )
                    Box(Modifier.matchParentSize().clickable { showTimePicker = true })
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text(stringResource(Res.string.editor_label_note)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            val money = Money.parse(amount.trim(), baseCurrency)
            val canRecord = from.id != to.id && money != null && money.isPositive
            Button(
                onClick = {
                    val toRecord = money ?: return@Button
                    onRecord(from, to, toRecord, note, paidAt, tzOffset)
                },
                enabled = canRecord,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(Res.string.action_record))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
