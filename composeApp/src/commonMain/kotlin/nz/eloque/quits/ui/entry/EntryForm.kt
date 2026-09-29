package nz.eloque.quits.ui.entry

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.chip.ChipSelector
import nz.eloque.quits.domain.Currencies
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.domain.Money
import nz.eloque.quits.domain.Split
import nz.eloque.quits.domain.isIncome
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.editor_add_item
import nz.eloque.quits.resources.editor_customize_paid_link
import nz.eloque.quits.resources.editor_equal_count
import nz.eloque.quits.resources.editor_equal_hint
import nz.eloque.quits.resources.editor_equal_paid_link
import nz.eloque.quits.resources.editor_item_deselect_all
import nz.eloque.quits.resources.editor_item_label
import nz.eloque.quits.resources.editor_item_select_all
import nz.eloque.quits.resources.editor_item_shared_by
import nz.eloque.quits.resources.editor_items_total
import nz.eloque.quits.resources.editor_label_amount
import nz.eloque.quits.resources.editor_label_currency
import nz.eloque.quits.resources.editor_label_rate
import nz.eloque.quits.resources.editor_placeholder_amount
import nz.eloque.quits.resources.editor_rate_fetching
import nz.eloque.quits.resources.editor_remaining
import nz.eloque.quits.resources.editor_remaining_done
import nz.eloque.quits.resources.editor_remove_item
import nz.eloque.quits.resources.editor_shares_decrease
import nz.eloque.quits.resources.editor_shares_increase
import nz.eloque.quits.resources.error_invalid_amount
import nz.eloque.quits.resources.error_invalid_paid
import nz.eloque.quits.resources.error_invalid_total
import nz.eloque.quits.ui.components.CurrencyPickerRow
import nz.eloque.quits.ui.components.InlineEntryField
import nz.eloque.quits.ui.components.ListFieldCard
import nz.eloque.quits.ui.components.ListRowDivider
import nz.eloque.quits.ui.components.ListTextRow
import nz.eloque.quits.ui.components.MemberAvatar
import nz.eloque.quits.ui.components.display
import nz.eloque.quits.ui.components.isValidAmountInput
import org.jetbrains.compose.resources.stringResource

/** The rows describing a bill, meant to sit inside a [ListFieldCard]. */
@Composable
fun EntryForm(
    state: EntryFormState,
    actions: EntryFormViewModel,
) {
    val splitOptions =
        if (state.kind.isIncome) SplitKind.entries.filter { it != SplitKind.ITEMIZED } else SplitKind.entries
    val splitLabels = splitOptions.associateWith { it.label() }
    Column(Modifier.padding(16.dp)) {
        ChipSelector(
            options = splitOptions,
            selectedOptions = listOf(state.splitKind),
            onOptionSelected = actions::setKind,
            onOptionDeselected = {},
            optionLabel = { splitLabels.getValue(it) },
        )
    }
    ListRowDivider()

    if (state.splitKind != SplitKind.ITEMIZED) {
        val amountValid = isValidAmountInput(state.amount, state.currency)
        ListTextRow(
            icon = Icons.Default.Numbers,
            label = stringResource(Res.string.editor_label_amount),
            value = state.amount,
            onValueChange = actions::setAmount,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = !amountValid,
            supporting = if (!amountValid) stringResource(Res.string.error_invalid_total) else null,
        )
        ListRowDivider()
    }

    CurrencyPickerRow(
        icon = Icons.Default.Payments,
        fieldLabel = stringResource(Res.string.editor_label_currency),
        selected = state.currency,
        onSelected = actions::setCurrency,
    )
    if (state.isForeign) {
        ListRowDivider()
        ListTextRow(
            icon = Icons.Default.CurrencyExchange,
            label = stringResource(Res.string.editor_label_rate, state.baseCurrency.code),
            value = state.rate,
            onValueChange = actions::setRate,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            supporting =
                when {
                    state.fetchingRate -> stringResource(Res.string.editor_rate_fetching)
                    else -> state.rateNotice
                },
        )
    }
    ListRowDivider()
    Column(Modifier.padding(16.dp)) {
        when (state.splitKind) {
            SplitKind.EQUAL -> {
                Text(
                    stringResource(state.kind.beneficiaryEditorHeadingRes()),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.members.forEach { member ->
                        MemberChip(
                            member = member,
                            selected = member.id in state.equalSelected,
                            onClick = { actions.toggleEqual(member.id) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    equalSplitHint(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            SplitKind.SHARES -> {
                state.members.forEach { member ->
                    SplitInputRow(member = member, preview = sharesPreview(state, member.id)?.display()) {
                        SharesStepper(
                            value = state.splitInput[member.id].orEmpty(),
                            onValueChange = { actions.setSplitInput(member.id, it) },
                        )
                    }
                }
            }

            SplitKind.PERCENTAGE -> {
                state.members.forEach { member ->
                    SplitInputRow(member = member, preview = percentagePreview(state, member.id)?.display()) {
                        InlineEntryField(
                            value = state.splitInput[member.id].orEmpty(),
                            onValueChange = { actions.setSplitInput(member.id, it) },
                            suffix = "%",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(84.dp),
                        )
                    }
                }
            }

            SplitKind.EXACT -> {
                val currency = state.currency
                state.members.forEach { member ->
                    val text = state.splitInput[member.id].orEmpty()
                    val valid = isValidAmountInput(text, currency, requirePositive = false)
                    SplitInputRow(
                        member = member,
                        preview = null,
                        isError = !valid,
                        supporting = if (!valid) stringResource(Res.string.error_invalid_amount, member.name) else null,
                    ) {
                        InlineEntryField(
                            value = text,
                            onValueChange = { actions.setSplitInput(member.id, it) },
                            suffix = Currencies.symbol(currency),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = !valid,
                            modifier = Modifier.width(120.dp),
                        )
                    }
                }
            }

            SplitKind.ITEMIZED -> {
                ItemizedEditor(
                    committed = state.items,
                    draftLabel = state.draftLabel,
                    draftAmount = state.draftAmount,
                    draftParticipants = state.draftParticipants,
                    members = state.members,
                    currency = state.currency,
                    draftValid = state.isDraftValid(),
                    onDraftLabel = actions::setDraftLabel,
                    onDraftAmount = actions::setDraftAmount,
                    onToggleParticipant = actions::toggleDraftParticipant,
                    onToggleAll = actions::toggleAllDraftParticipants,
                    onSubmit = actions::submitDraft,
                    onRemove = actions::removeItem,
                )
            }
        }

        RemainingHint(state)
    }

    ListRowDivider()
    Column(Modifier.padding(16.dp)) {
        Text(
            stringResource(state.kind.payerHeadingRes()),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(8.dp))
        when (state.payerMode) {
            PayerMode.EQUAL -> {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    state.members.forEach { member ->
                        MemberChip(
                            member = member,
                            selected = member.id in state.payerSelected,
                            onClick = { actions.togglePayer(member.id) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    paidByEqualHint(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { actions.setPayerMode(PayerMode.CUSTOM) },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(stringResource(Res.string.editor_customize_paid_link), style = MaterialTheme.typography.bodySmall)
                }
            }

            PayerMode.CUSTOM -> {
                val currency = state.currency
                state.members.forEach { member ->
                    val text = state.paid[member.id].orEmpty()
                    val valid = isValidAmountInput(text, currency)
                    SplitInputRow(
                        member = member,
                        preview = null,
                        isError = !valid,
                        supporting = if (!valid) stringResource(Res.string.error_invalid_paid, member.name) else null,
                    ) {
                        InlineEntryField(
                            value = text,
                            onValueChange = { actions.setPaid(member.id, it) },
                            suffix = Currencies.symbol(currency),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            isError = !valid,
                            modifier = Modifier.width(120.dp),
                        )
                    }
                }
                PaidRemainingHint(state)
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { actions.setPayerMode(PayerMode.EQUAL) },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text(stringResource(Res.string.editor_equal_paid_link), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** One tappable avatar chip; a checkmark badge marks selection. Used for both single-payer pick and equal-split toggle. */
@Composable
private fun MemberChip(
    member: MemberInput,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() }),
    ) {
        Box {
            MemberAvatar(name = member.name, id = member.id, size = 40.dp)
            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(16.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(member.name, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/**
 * "Sam paid ¥4,800" for one selected payer, "3 people · ¥1,600 each" for several, or a neutral
 * prompt with nobody selected yet.
 */
@Composable
private fun paidByEqualHint(state: EntryFormState): String {
    val selected = state.members.filter { it.id in state.payerSelected }
    if (selected.isEmpty()) return stringResource(state.kind.payerPromptRes())

    val total = Money.parse(state.amount.trim(), state.currency)
    if (total == null || !total.isPositive) {
        return if (selected.size == 1) {
            stringResource(state.kind.payerPromptRes())
        } else {
            stringResource(Res.string.editor_equal_count, selected.size)
        }
    }
    return if (selected.size == 1) {
        stringResource(state.kind.payerHintRes(), selected.first().name, total.display())
    } else {
        val each = Money(total.minorUnits / selected.size, total.currency)
        stringResource(Res.string.editor_equal_hint, selected.size, each.display())
    }
}

/** Split-payer mode's "remaining to assign" — mirrors the split section's own hint below. */
@Composable
private fun PaidRemainingHint(state: EntryFormState) {
    val currency = state.currency
    val total = Money.parse(state.amount.trim(), currency) ?: return
    val assigned = state.members.sumOf { m -> Money.parse(state.paid[m.id].orEmpty().trim(), currency)?.minorUnits ?: 0L }
    val remaining = Money(total.minorUnits - assigned, currency)
    RemainingHintText(done = remaining.isZero, remainingDisplay = remaining.display())
}

/** Live "remaining to assign" feedback for the splits that must sum to a target (exact, percentage). */
@Composable
private fun RemainingHint(state: EntryFormState) {
    val (done, remainingDisplay) =
        when (state.splitKind) {
            SplitKind.EXACT -> {
                val currency = state.currency
                val total = Money.parse(state.amount.trim(), currency)?.minorUnits ?: 0L
                val assigned =
                    state.members.sumOf { m -> Money.parse(state.splitInput[m.id].orEmpty().trim(), currency)?.minorUnits ?: 0L }
                val remaining = Money(total - assigned, currency)
                remaining.isZero to remaining.display()
            }

            SplitKind.PERCENTAGE -> {
                val assigned = state.members.sumOf { m -> state.splitInput[m.id].orEmpty().trim().toIntOrNull() ?: 0 }
                val remaining = 100 - assigned
                (remaining == 0) to "$remaining%"
            }

            else -> return
        }
    RemainingHintText(done = done, remainingDisplay = remainingDisplay)
}

@Composable
private fun RemainingHintText(
    done: Boolean,
    remainingDisplay: String,
) {
    val text =
        if (done) {
            stringResource(Res.string.editor_remaining_done)
        } else {
            stringResource(Res.string.editor_remaining, remainingDisplay)
        }
    Spacer(Modifier.height(8.dp))
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
    )
}

/** One member's row in the Shares/Percentage/Exact tables: avatar, name, optional live preview, input. */
@Composable
private fun SplitInputRow(
    member: MemberInput,
    preview: String?,
    supporting: String? = null,
    isError: Boolean = false,
    field: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MemberAvatar(name = member.name, id = member.id, size = 32.dp)
            Text(member.name, Modifier.weight(1f).padding(start = 12.dp, end = 8.dp))
            if (preview != null) {
                Text(
                    preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            field()
        }
        if (supporting != null) {
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 44.dp, bottom = 8.dp),
            )
        }
    }
}

/** The itemized-split editor: a running total, the committed line rows, and a draft card to add the next line. */
@Composable
private fun ItemizedEditor(
    committed: List<ItemInput>,
    draftLabel: String,
    draftAmount: String,
    draftParticipants: Set<MemberId>,
    members: List<MemberInput>,
    currency: Currency,
    draftValid: Boolean,
    onDraftLabel: (String) -> Unit,
    onDraftAmount: (String) -> Unit,
    onToggleParticipant: (MemberId) -> Unit,
    onToggleAll: () -> Unit,
    onSubmit: () -> Unit,
    onRemove: (String) -> Unit,
) {
    committed.forEach { item ->
        CommittedItemRow(item = item, members = members, currency = currency, onRemove = { onRemove(item.id) })
    }

    if (committed.isNotEmpty()) {
        val totalMinor = committed.sumOf { Money.parse(it.amount.trim(), currency)?.minorUnits ?: 0L }
        HorizontalDivider(Modifier.padding(top = 4.dp, bottom = 8.dp))
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(Res.string.editor_items_total), style = MaterialTheme.typography.titleLarge)
            Text(Money(totalMinor, currency).display(), style = MaterialTheme.typography.titleLarge)
        }
    }

    val labelFocus = remember { FocusRequester() }
    val submit = {
        if (draftValid) {
            onSubmit()
            labelFocus.requestFocus()
        }
    }
    val allSelected = members.isNotEmpty() && draftParticipants.size == members.size

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                InlineEntryField(
                    value = draftLabel,
                    onValueChange = onDraftLabel,
                    placeholder = stringResource(Res.string.editor_item_label),
                    alignEnd = false,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    fieldModifier = Modifier.focusRequester(labelFocus),
                    modifier = Modifier.weight(1f),
                )
                InlineEntryField(
                    value = draftAmount,
                    onValueChange = onDraftAmount,
                    placeholder = stringResource(Res.string.editor_placeholder_amount),
                    suffix = Currencies.symbol(currency),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.width(120.dp),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(Res.string.editor_item_shared_by),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline,
                )
                TextButton(onClick = onToggleAll, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(
                        stringResource(
                            if (allSelected) Res.string.editor_item_deselect_all else Res.string.editor_item_select_all,
                        ),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                members.forEach { member ->
                    FilterChip(
                        selected = member.id in draftParticipants,
                        onClick = { onToggleParticipant(member.id) },
                        label = { Text(member.name) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = { submit() }, enabled = draftValid, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.editor_add_item))
            }
        }
    }
}

/** A committed, read-only receipt line: its label, who shares it, and its cost, with a remove button. */
@Composable
private fun CommittedItemRow(
    item: ItemInput,
    members: List<MemberInput>,
    currency: Currency,
    onRemove: () -> Unit,
) {
    val names = members.filter { it.id in item.participants }.joinToString(", ") { it.name }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(item.label.ifEmpty { stringResource(Res.string.editor_item_label) })
            Text(names, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        }
        Money.parse(item.amount.trim(), currency)?.let { Text(it.display()) }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.editor_remove_item))
        }
    }
}

/** −/+ stepper for the Shares split field (small non-negative whole numbers). */
@Composable
private fun SharesStepper(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val current = value.trim().toLongOrNull()?.coerceAtLeast(0) ?: 0L
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepperButton(
            icon = Icons.Default.Remove,
            contentDescription = stringResource(Res.string.editor_shares_decrease),
            enabled = current > 0,
            onClick = { onValueChange((current - 1).coerceAtLeast(0).toString()) },
        )
        Text(
            text = current.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(28.dp),
        )
        StepperButton(
            icon = Icons.Default.Add,
            contentDescription = stringResource(Res.string.editor_shares_increase),
            enabled = true,
            onClick = { onValueChange((current + 1).toString()) },
        )
    }
}

/** Compact circular −/+ button for [SharesStepper]; skips [IconButton]'s 48dp min touch target to align with the compact split fields. */
@Composable
private fun StepperButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint =
                if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            modifier = Modifier.size(22.dp),
        )
    }
}

/** "4 people · ¥1,200 each" once the amount is valid; a plain count otherwise. */
@Composable
private fun equalSplitHint(state: EntryFormState): String {
    val count = state.equalSelected.size
    val total = Money.parse(state.amount.trim(), state.currency)
    return if (count > 0 && total != null && total.isPositive) {
        val each = Money(total.minorUnits / count, total.currency)
        stringResource(Res.string.editor_equal_hint, count, each.display())
    } else {
        stringResource(Res.string.editor_equal_count, count)
    }
}

/** Live € equivalent for a percentage input; uses the real [Split.Percentage.divide] once the percentages sum to 100, else per-row division. */
@Composable
private fun percentagePreview(
    state: EntryFormState,
    memberId: MemberId,
): Money? {
    val currency = state.currency
    val total = Money.parse(state.amount.trim(), currency) ?: return null
    val percent = state.splitInput[memberId].orEmpty().trim().toIntOrNull() ?: return null
    if (percent <= 0) return null

    val entries =
        state.members.mapNotNull { m ->
            val p = state.splitInput[m.id].orEmpty().trim().toIntOrNull()
            if (p != null && p > 0) m.id to p else null
        }
    if (entries.sumOf { it.second } == 100) {
        val exact = Split.Percentage(entries.toMap()).divide(total)
        return exact[memberId]
    }
    return Money(total.minorUnits * percent / 100, currency)
}

/** Live € equivalent for a shares input, via the real [Split.Shares.divide]. */
@Composable
private fun sharesPreview(
    state: EntryFormState,
    memberId: MemberId,
): Money? {
    val currency = state.currency
    val total = Money.parse(state.amount.trim(), currency) ?: return null
    if (!total.isPositive) return null
    val weight = state.splitInput[memberId].orEmpty().trim().toLongOrNull() ?: return null
    if (weight <= 0) return null

    val entries =
        state.members.mapNotNull { m ->
            val w = state.splitInput[m.id].orEmpty().trim().toLongOrNull()
            if (w != null && w > 0) m.id to w else null
        }
    if (entries.isEmpty()) return null
    return try {
        Split.Shares(entries.toMap()).divide(total)[memberId]
    } catch (_: IllegalArgumentException) {
        null
    }
}
