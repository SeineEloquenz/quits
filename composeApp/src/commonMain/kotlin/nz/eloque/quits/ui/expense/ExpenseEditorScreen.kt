package nz.eloque.quits.ui.expense

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.chip.ChipSelector
import nz.eloque.compose_kit.components.SectionCard
import nz.eloque.compose_kit.input.AbbreviatingText
import nz.eloque.compose_kit.scaffold.AppScaffold
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.domain.Money
import nz.eloque.quits.domain.Split
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.action_cancel
import nz.eloque.quits.resources.editor_customize_paid_link
import nz.eloque.quits.resources.editor_equal_count
import nz.eloque.quits.resources.editor_equal_hint
import nz.eloque.quits.resources.editor_equal_paid_link
import nz.eloque.quits.resources.editor_label_amount
import nz.eloque.quits.resources.editor_label_currency
import nz.eloque.quits.resources.editor_label_rate
import nz.eloque.quits.resources.editor_label_title
import nz.eloque.quits.resources.editor_paid_by
import nz.eloque.quits.resources.editor_paid_by_hint
import nz.eloque.quits.resources.editor_paid_by_prompt
import nz.eloque.quits.resources.editor_placeholder_amount
import nz.eloque.quits.resources.editor_placeholder_percent
import nz.eloque.quits.resources.editor_placeholder_shares
import nz.eloque.quits.resources.editor_rate_fetching
import nz.eloque.quits.resources.editor_remaining
import nz.eloque.quits.resources.editor_remaining_done
import nz.eloque.quits.resources.editor_save_changes
import nz.eloque.quits.resources.editor_save_expense
import nz.eloque.quits.resources.editor_shares_suffix
import nz.eloque.quits.resources.editor_split
import nz.eloque.quits.resources.editor_split_equal
import nz.eloque.quits.resources.editor_split_exact
import nz.eloque.quits.resources.editor_split_percentage
import nz.eloque.quits.resources.editor_split_shares
import nz.eloque.quits.resources.editor_title_add
import nz.eloque.quits.resources.editor_title_edit
import nz.eloque.quits.ui.components.CurrencyPicker
import nz.eloque.quits.ui.components.LoadingBox
import nz.eloque.quits.ui.components.MemberAvatar
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
        title = {
            AbbreviatingText(
                if (state.editing) {
                    stringResource(Res.string.editor_title_edit)
                } else {
                    stringResource(Res.string.editor_title_add)
                },
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
            )
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
                    OutlinedTextField(
                        value = state.amount,
                        onValueChange = viewModel::setAmount,
                        label = { Text(stringResource(Res.string.editor_label_amount)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                    when (state.payerMode) {
                        PayerMode.EQUAL -> {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                state.members.forEachIndexed { index, member ->
                                    if (index > 0) Spacer(Modifier.width(12.dp))
                                    MemberChip(
                                        member = member,
                                        selected = member.id in state.payerSelected,
                                        onClick = { viewModel.togglePayer(member.id) },
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
                                onClick = { viewModel.setPayerMode(PayerMode.CUSTOM) },
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(stringResource(Res.string.editor_customize_paid_link), style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        PayerMode.CUSTOM -> {
                            val currency = Currency.parse(state.currency)
                            state.members.forEach { member ->
                                SplitInputRow(member = member, preview = null) {
                                    OutlinedTextField(
                                        value = state.paid[member.id].orEmpty(),
                                        onValueChange = { viewModel.setPaid(member.id, it) },
                                        placeholder = { Text("0") },
                                        suffix = currency?.let { c -> { Text(c.code) } },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.width(140.dp),
                                    )
                                }
                            }
                            PaidRemainingHint(state)
                            Spacer(Modifier.height(4.dp))
                            TextButton(
                                onClick = { viewModel.setPayerMode(PayerMode.EQUAL) },
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(stringResource(Res.string.editor_equal_paid_link), style = MaterialTheme.typography.bodySmall)
                            }
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

                    when (state.splitKind) {
                        SplitKind.EQUAL -> {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                state.members.forEachIndexed { index, member ->
                                    if (index > 0) Spacer(Modifier.width(12.dp))
                                    MemberChip(
                                        member = member,
                                        selected = member.id in state.equalSelected,
                                        onClick = { viewModel.toggleEqual(member.id) },
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
                                    OutlinedTextField(
                                        value = state.splitInput[member.id].orEmpty(),
                                        onValueChange = { viewModel.setSplitInput(member.id, it) },
                                        placeholder = { Text(splitPlaceholder(state.splitKind)) },
                                        suffix = { Text(stringResource(Res.string.editor_shares_suffix)) },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.width(110.dp),
                                    )
                                }
                            }
                        }

                        SplitKind.PERCENTAGE -> {
                            state.members.forEach { member ->
                                SplitInputRow(member = member, preview = percentagePreview(state, member.id)?.display()) {
                                    OutlinedTextField(
                                        value = state.splitInput[member.id].orEmpty(),
                                        onValueChange = { viewModel.setSplitInput(member.id, it) },
                                        placeholder = { Text(splitPlaceholder(state.splitKind)) },
                                        suffix = { Text("%") },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.width(110.dp),
                                    )
                                }
                            }
                        }

                        SplitKind.EXACT -> {
                            val currency = Currency.parse(state.currency)
                            state.members.forEach { member ->
                                SplitInputRow(member = member, preview = null) {
                                    OutlinedTextField(
                                        value = state.splitInput[member.id].orEmpty(),
                                        onValueChange = { viewModel.setSplitInput(member.id, it) },
                                        placeholder = { Text(splitPlaceholder(state.splitKind)) },
                                        suffix = currency?.let { c -> { Text(c.code) } },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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

/** One tappable avatar chip; a checkmark badge marks selection. Used for both single-payer pick and equal-split toggle. */
@Composable
private fun MemberChip(
    member: MemberInput,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box {
            MemberAvatar(name = member.name, id = MemberId(member.id), size = 40.dp)
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
private fun paidByEqualHint(state: ExpenseEditorUiState): String {
    val selected = state.members.filter { it.id in state.payerSelected }
    if (selected.isEmpty()) return stringResource(Res.string.editor_paid_by_prompt)

    val currency = Currency.parse(state.currency)
    val total = currency?.let { Money.parse(state.amount.trim(), it) }
    if (total == null || !total.isPositive) {
        return if (selected.size == 1) {
            stringResource(Res.string.editor_paid_by_prompt)
        } else {
            stringResource(Res.string.editor_equal_count, selected.size)
        }
    }
    return if (selected.size == 1) {
        stringResource(Res.string.editor_paid_by_hint, selected.first().name, total.display())
    } else {
        val each = Money(total.minorUnits / selected.size, total.currency)
        stringResource(Res.string.editor_equal_hint, selected.size, each.display())
    }
}

/** Split-payer mode's "remaining to assign" — mirrors the split section's own hint below. */
@Composable
private fun PaidRemainingHint(state: ExpenseEditorUiState) {
    val currency = Currency.parse(state.currency) ?: return
    val total = Money.parse(state.amount.trim(), currency) ?: return
    val assigned = state.members.sumOf { m -> Money.parse(state.paid[m.id].orEmpty().trim(), currency)?.minorUnits ?: 0L }
    val remaining = Money(total.minorUnits - assigned, currency)
    val text =
        if (remaining.isZero) {
            stringResource(Res.string.editor_remaining_done)
        } else {
            stringResource(Res.string.editor_remaining, remaining.display())
        }
    Spacer(Modifier.height(8.dp))
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (remaining.isZero) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
    )
}

/** Live "remaining to assign" feedback for the splits that must sum to a target (exact, percentage). */
@Composable
private fun RemainingHint(state: ExpenseEditorUiState) {
    val text =
        when (state.splitKind) {
            SplitKind.EXACT -> {
                val currency = Currency.parse(state.currency) ?: return
                val total = Money.parse(state.amount.trim(), currency)?.minorUnits ?: 0L
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

            else -> {
                return
            }
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

/** One member's row in the Shares/Percentage/Exact tables: avatar, name, optional live preview, input. */
@Composable
private fun SplitInputRow(
    member: MemberInput,
    preview: String?,
    field: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemberAvatar(name = member.name, id = MemberId(member.id), size = 32.dp)
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
}

/** "4 people · ¥1,200 each" once the amount is valid; a plain count otherwise. */
@Composable
private fun equalSplitHint(state: ExpenseEditorUiState): String {
    val count = state.equalSelected.size
    val currency = Currency.parse(state.currency)
    val total = currency?.let { Money.parse(state.amount.trim(), it) }
    return if (count > 0 && total != null && total.isPositive) {
        val each = Money(total.minorUnits / count, total.currency)
        stringResource(Res.string.editor_equal_hint, count, each.display())
    } else {
        stringResource(Res.string.editor_equal_count, count)
    }
}

/**
 * Live € equivalent next to a percentage input. Once every entered percentage sums to 100, this
 * calls the real [Split.Percentage.divide] — the exact same largest-remainder allocation that
 * save() will use — so the preview never disagrees with what actually gets saved. Before that
 * (still typing), falls back to simple per-row division for immediate feedback.
 */
@Composable
private fun percentagePreview(
    state: ExpenseEditorUiState,
    memberId: String,
): Money? {
    val currency = Currency.parse(state.currency) ?: return null
    val total = Money.parse(state.amount.trim(), currency) ?: return null
    val percent = state.splitInput[memberId].orEmpty().trim().toIntOrNull() ?: return null
    if (percent <= 0) return null

    val entries =
        state.members.mapNotNull { m ->
            val p = state.splitInput[m.id].orEmpty().trim().toIntOrNull()
            if (p != null && p > 0) MemberId(m.id) to p else null
        }
    if (entries.sumOf { it.second } == 100) {
        val exact = Split.Percentage(entries.toMap()).divide(total)
        return exact[MemberId(memberId)]
    }
    return Money(total.minorUnits * percent / 100, currency)
}

/**
 * Live € equivalent next to a shares input. Unlike percentage, shares don't need to sum to
 * anything specific, so this can always call the real [Split.Shares.divide] once at least one
 * weight is entered — no "still typing" fallback needed.
 */
@Composable
private fun sharesPreview(
    state: ExpenseEditorUiState,
    memberId: String,
): Money? {
    val currency = Currency.parse(state.currency) ?: return null
    val total = Money.parse(state.amount.trim(), currency) ?: return null
    if (!total.isPositive) return null
    val weight = state.splitInput[memberId].orEmpty().trim().toLongOrNull() ?: return null
    if (weight <= 0) return null

    val entries =
        state.members.mapNotNull { m ->
            val w = state.splitInput[m.id].orEmpty().trim().toLongOrNull()
            if (w != null && w > 0) MemberId(m.id) to w else null
        }
    if (entries.isEmpty()) return null
    return try {
        Split.Shares(entries.toMap()).divide(total)[MemberId(memberId)]
    } catch (e: IllegalArgumentException) {
        null
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
