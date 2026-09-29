package nz.eloque.quits.ui.quicksplit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.scaffold.AppScaffold
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.domain.QuickSplit
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.cd_back
import nz.eloque.quits.resources.cd_remove
import nz.eloque.quits.resources.detail_transfer_row
import nz.eloque.quits.resources.quick_split_add_person
import nz.eloque.quits.resources.quick_split_even
import nz.eloque.quits.resources.quick_split_incomplete
import nz.eloque.quits.resources.quick_split_paid_and_share
import nz.eloque.quits.resources.quick_split_people
import nz.eloque.quits.resources.quick_split_share
import nz.eloque.quits.resources.quick_split_share_total
import nz.eloque.quits.resources.quick_split_share_transfer
import nz.eloque.quits.resources.quick_split_title
import nz.eloque.quits.resources.quick_split_who_pays
import nz.eloque.quits.ui.components.BalanceText
import nz.eloque.quits.ui.components.InlineEntryField
import nz.eloque.quits.ui.components.ListFieldCard
import nz.eloque.quits.ui.components.MemberAvatar
import nz.eloque.quits.ui.components.MoneyText
import nz.eloque.quits.ui.components.display
import nz.eloque.quits.ui.entry.EntryForm
import nz.eloque.quits.ui.entry.MemberInput
import nz.eloque.quits.util.Sharer
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickSplitScreen(onBack: () -> Unit) {
    val viewModel = koinViewModel<QuickSplitViewModel>()
    val form by viewModel.form.collectAsState()
    val result by viewModel.result.collectAsState()
    val sharer = koinInject<Sharer>()
    val shareText = result?.let { shareText(it) }

    AppScaffold(
        title = { Text(stringResource(Res.string.quick_split_title), style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.cd_back))
            }
        },
        actions = {
            IconButton(onClick = { shareText?.let(sharer::share) }, enabled = shareText != null) {
                Icon(Icons.Default.Share, contentDescription = stringResource(Res.string.quick_split_share))
            }
        },
    ) { scrollBehavior ->
        Column(
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))
            PeopleCard(
                people = form.members,
                onAdd = viewModel::addPerson,
                onRename = viewModel::renamePerson,
                onRemove = viewModel::removePerson,
            )
            ListFieldCard { EntryForm(form, viewModel) }
            ResultCard(result)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PeopleCard(
    people: List<MemberInput>,
    onAdd: (String) -> Unit,
    onRename: (MemberId, String) -> Unit,
    onRemove: (MemberId) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val submit = {
        onAdd(draft)
        draft = ""
    }
    ListFieldCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(Res.string.quick_split_people),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.outline,
            )
            people.forEach { person ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MemberAvatar(name = person.name, id = person.id, size = 32.dp)
                    InlineEntryField(
                        value = person.name,
                        onValueChange = { onRename(person.id, it) },
                        alignEnd = false,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onRemove(person.id) }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.cd_remove, person.name))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InlineEntryField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = stringResource(Res.string.quick_split_add_person),
                    alignEnd = false,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = submit, enabled = draft.isNotBlank()) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.quick_split_add_person))
                }
            }
        }
    }
}

@Composable
private fun ResultCard(split: QuickSplit?) {
    ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(Res.string.quick_split_who_pays),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (split == null) {
                Text(
                    stringResource(Res.string.quick_split_incomplete),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                return@Column
            }
            val names = split.people.associate { it.id to it.name }
            val transfers = remember(split) { split.transfers() }
            if (transfers.isEmpty()) {
                Text(stringResource(Res.string.quick_split_even), style = MaterialTheme.typography.bodyMedium)
            }
            transfers.forEach { transfer ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MemberAvatar(name = names.getValue(transfer.from), id = transfer.from, size = 32.dp)
                    Text(
                        stringResource(Res.string.detail_transfer_row, names.getValue(transfer.from), names.getValue(transfer.to)),
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    MoneyText(transfer.amount, style = MaterialTheme.typography.titleMedium)
                }
            }
            HorizontalDivider()
            val balances = split.balances()
            split.people.forEach { person ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MemberAvatar(name = person.name, id = person.id, size = 32.dp)
                    Column(Modifier.weight(1f)) {
                        Text(person.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(
                                Res.string.quick_split_paid_and_share,
                                split.bill.paymentsBy(person.id).display(),
                                split.bill.shareOf(person.id).display(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    BalanceText(balances.of(person.id))
                }
            }
        }
    }
}

@Composable
private fun shareText(split: QuickSplit): String {
    val names = split.people.associate { it.id to it.name }
    val header = stringResource(Res.string.quick_split_share_total, split.bill.total.display())
    val transfers = split.transfers()
    val lines =
        if (transfers.isEmpty()) {
            listOf(stringResource(Res.string.quick_split_even))
        } else {
            transfers.map {
                stringResource(Res.string.quick_split_share_transfer, names.getValue(it.from), names.getValue(it.to), it.amount.display())
            }
        }
    return (listOf(header) + lines).joinToString("\n")
}
