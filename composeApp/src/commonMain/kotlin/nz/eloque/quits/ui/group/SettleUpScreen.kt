package nz.eloque.quits.ui.group

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.scaffold.AppScaffold
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.Member
import nz.eloque.quits.domain.Money
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.action_record
import nz.eloque.quits.resources.cd_back
import nz.eloque.quits.resources.detail_settle_up
import nz.eloque.quits.resources.settle_up_all_settled
import nz.eloque.quits.resources.settle_up_custom_link
import nz.eloque.quits.resources.settle_up_none
import nz.eloque.quits.resources.settle_up_payer_owes
import nz.eloque.quits.resources.settle_up_record_title
import nz.eloque.quits.resources.settle_up_suggested
import nz.eloque.quits.ui.components.LoadingBox
import nz.eloque.quits.ui.components.MemberAvatar
import nz.eloque.quits.ui.components.MoneyText
import nz.eloque.quits.util.currentOffsetMinutes
import nz.eloque.quits.util.nowMillis
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleUpScreen(
    groupId: GroupId,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<GroupDetailViewModel>(key = groupId.value) { parametersOf(groupId) }
    val state by viewModel.state.collectAsState()

    var recording by remember { mutableStateOf<RecordTarget?>(null) }

    recording?.let { target ->
        RecordSettlementSheet(
            members = state.members.map { Member(it.id, it.name) },
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
                state.transfers.groupBy { it.transfer.from }.forEach { (_, rows) ->
                    PayerGroup(
                        rows = rows,
                        onSelect = { row ->
                            val from = state.members.firstOrNull { it.id == row.transfer.from }
                            val to = state.members.firstOrNull { it.id == row.transfer.to }
                            if (from != null && to != null) {
                                recording = RecordTarget(Member(from.id, from.name), Member(to.id, to.name), row.transfer.amount)
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
    val from: Member?,
    val to: Member?,
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

/** All of one payer's suggested payments: a debtor header, then a tappable row per creditor. */
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

/** Sheet to record a payment; prefilled from a tapped suggestion or empty for custom, with the amount editable for partial payments. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordSettlementSheet(
    members: List<Member>,
    baseCurrency: Currency,
    initialFrom: Member?,
    initialTo: Member?,
    initialAmount: Money?,
    onDismiss: () -> Unit,
    onRecord: (from: Member, to: Member, amount: Money, note: String?, paidAt: Long, tzOffset: Int) -> Unit,
) {
    if (members.size < 2) return

    val start = initialFrom ?: members[0]
    var from by remember { mutableStateOf(start) }
    var to by remember { mutableStateOf(initialTo ?: members.first { it.id != start.id }) }
    var amount by remember { mutableStateOf(initialAmount?.toDecimalString() ?: "") }
    var note by remember { mutableStateOf("") }
    val tzOffset = remember { currentOffsetMinutes() }
    var paidAt by remember { mutableStateOf(nowMillis()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Text(
                stringResource(Res.string.settle_up_record_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            SettlementFields(
                members = members,
                from = from,
                to = to,
                onFrom = { from = it },
                onTo = { to = it },
                currency = baseCurrency,
                amount = amount,
                onAmount = { amount = it },
                note = note,
                onNote = { note = it },
                timestamp = paidAt,
                tzOffsetMinutes = tzOffset,
                onTimestamp = { paidAt = it },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Spacer(Modifier.height(16.dp))
            val money = Money.parse(amount.trim(), baseCurrency)
            val canRecord = from.id != to.id && money != null && money.isPositive
            Button(
                onClick = {
                    val toRecord = money ?: return@Button
                    onRecord(from, to, toRecord, note, paidAt, tzOffset)
                },
                enabled = canRecord,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            ) {
                Text(stringResource(Res.string.action_record))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
