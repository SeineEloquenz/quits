package nz.eloque.quits.ui.group

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.Member
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.editor_label_amount
import nz.eloque.quits.resources.editor_label_note
import nz.eloque.quits.resources.error_invalid_total
import nz.eloque.quits.resources.settle_up_from
import nz.eloque.quits.resources.settle_up_to
import nz.eloque.quits.ui.components.DateTimeRows
import nz.eloque.quits.ui.components.ListFieldCard
import nz.eloque.quits.ui.components.ListRowDivider
import nz.eloque.quits.ui.components.ListTextRow
import nz.eloque.quits.ui.components.SearchablePickerRow
import nz.eloque.quits.ui.components.isValidAmountInput
import org.jetbrains.compose.resources.stringResource

/**
 * The shared settlement form (Direction C): a From / To / Amount card and a Date / Time / Note card.
 * Stateless, so both the standalone editor and the record sheet drive it from their own state — each
 * only needs a member's id + name, so both talk in domain [Member]s.
 */
@Composable
fun SettlementFields(
    members: List<Member>,
    from: Member,
    to: Member,
    onFrom: (Member) -> Unit,
    onTo: (Member) -> Unit,
    currency: Currency,
    amount: String,
    onAmount: (String) -> Unit,
    note: String,
    onNote: (String) -> Unit,
    timestamp: Long,
    tzOffsetMinutes: Int,
    onTimestamp: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        ListFieldCard {
            SearchablePickerRow(
                icon = Icons.Default.ArrowUpward,
                label = stringResource(Res.string.settle_up_from),
                selected = from,
                selectedLabel = { it.name },
                onSelected = onFrom,
                search = { query -> members.filter { it.name.contains(query, ignoreCase = true) } },
                itemKey = { it.id.value },
                itemLabel = { it.name },
            )
            ListRowDivider()
            SearchablePickerRow(
                icon = Icons.Default.ArrowDownward,
                label = stringResource(Res.string.settle_up_to),
                selected = to,
                selectedLabel = { it.name },
                onSelected = onTo,
                search = { query -> members.filter { it.name.contains(query, ignoreCase = true) } },
                itemKey = { it.id.value },
                itemLabel = { it.name },
            )
            ListRowDivider()
            val amountValid = isValidAmountInput(amount, currency)
            ListTextRow(
                icon = Icons.Default.Numbers,
                label = stringResource(Res.string.editor_label_amount),
                value = amount,
                onValueChange = onAmount,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = !amountValid,
                supporting = if (!amountValid) stringResource(Res.string.error_invalid_total) else null,
            )
        }
        ListFieldCard {
            DateTimeRows(timestamp = timestamp, tzOffsetMinutes = tzOffsetMinutes, onChange = onTimestamp)
            ListRowDivider()
            ListTextRow(
                icon = Icons.AutoMirrored.Filled.Notes,
                label = stringResource(Res.string.editor_label_note),
                value = note,
                onValueChange = onNote,
                singleLine = false,
            )
        }
    }
}
