package nz.eloque.quits.ui.entry

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.chip.ChipSelector
import nz.eloque.compose_kit.components.SectionCard
import nz.eloque.compose_kit.input.AbbreviatingText
import nz.eloque.compose_kit.scaffold.AppScaffold
import nz.eloque.quits.domain.Category
import nz.eloque.quits.domain.CategoryId
import nz.eloque.quits.domain.Currencies
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.EntryKind
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.domain.Money
import nz.eloque.quits.domain.Split
import nz.eloque.quits.domain.isIncome
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.action_add
import nz.eloque.quits.resources.action_cancel
import nz.eloque.quits.resources.action_ok
import nz.eloque.quits.resources.category_color
import nz.eloque.quits.resources.category_delete
import nz.eloque.quits.resources.category_edit_title
import nz.eloque.quits.resources.category_icon
import nz.eloque.quits.resources.editor_add_item
import nz.eloque.quits.resources.editor_category_name
import nz.eloque.quits.resources.editor_category_new
import nz.eloque.quits.resources.editor_category_new_title
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
import nz.eloque.quits.resources.editor_label_category
import nz.eloque.quits.resources.editor_label_currency
import nz.eloque.quits.resources.editor_label_date
import nz.eloque.quits.resources.editor_label_note
import nz.eloque.quits.resources.editor_label_rate
import nz.eloque.quits.resources.editor_label_time
import nz.eloque.quits.resources.editor_label_title
import nz.eloque.quits.resources.editor_placeholder_amount
import nz.eloque.quits.resources.editor_placeholder_percent
import nz.eloque.quits.resources.editor_placeholder_shares
import nz.eloque.quits.resources.editor_rate_fetching
import nz.eloque.quits.resources.editor_remaining
import nz.eloque.quits.resources.editor_remaining_done
import nz.eloque.quits.resources.editor_remove_item
import nz.eloque.quits.resources.editor_shares_decrease
import nz.eloque.quits.resources.editor_shares_increase
import nz.eloque.quits.resources.editor_split
import nz.eloque.quits.resources.editor_split_between
import nz.eloque.quits.resources.error_invalid_amount
import nz.eloque.quits.resources.error_invalid_paid
import nz.eloque.quits.resources.error_invalid_total
import nz.eloque.quits.ui.category.CATEGORY_COLORS
import nz.eloque.quits.ui.category.CATEGORY_ICON_KEYS
import nz.eloque.quits.ui.category.CategoryDisplay
import nz.eloque.quits.ui.category.PresetCategory
import nz.eloque.quits.ui.category.categoryColor
import nz.eloque.quits.ui.category.categoryIcon
import nz.eloque.quits.ui.category.presetsFor
import nz.eloque.quits.ui.components.CurrencyPicker
import nz.eloque.quits.ui.components.LoadingBox
import nz.eloque.quits.ui.components.MemberAvatar
import nz.eloque.quits.ui.components.display
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
fun EntryEditorScreen(
    groupId: GroupId,
    entryId: String?,
    kind: EntryKind,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val viewModel = koinViewModel<EntryEditorViewModel> { parametersOf(groupId, entryId, kind) }
    val state by viewModel.state.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.saved.collect { onDone() }
    }

    if (showDatePicker) {
        val zone = offsetZone(state.tzOffsetMinutes)
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = localDateMillisUtc(state.spentAt, zone))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { viewModel.setSpentAt(withPickedDate(state.spentAt, it, zone)) }
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
        val (initialHour, initialMinute) = remember { localHourMinute(state.spentAt, zone) }
        // No is24Hour argument: rememberTimePickerState defaults to the device's clock setting.
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
                            viewModel.setSpentAt(withPickedTime(state.spentAt, timeState.hour, timeState.minute, zone))
                            showTimePicker = false
                        }) { Text(stringResource(Res.string.action_ok)) }
                    }
                }
            }
        }
    }

    AppScaffold(
        title = {
            AbbreviatingText(
                stringResource(state.kind.titleRes(state.editing)),
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
                    CategoryField(
                        selectedId = state.categoryId,
                        presets = presetsFor(state.kind),
                        custom = state.categories,
                        onSelect = viewModel::setCategoryId,
                        onCreate = viewModel::createCategory,
                        onUpdate = viewModel::updateCategory,
                        onDelete = viewModel::deleteCategory,
                    )
                    Spacer(Modifier.height(8.dp))
                    // Read-only fields; a transparent overlay opens the relevant picker on tap.
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = formatLocalDate(state.spentAt, state.tzOffsetMinutes),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(Res.string.editor_label_date)) },
                                trailingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Box(Modifier.matchParentSize().clickable { showDatePicker = true })
                        }
                        Spacer(Modifier.width(8.dp))
                        Box {
                            OutlinedTextField(
                                value = formatLocalTime(state.spentAt, state.tzOffsetMinutes),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(Res.string.editor_label_time)) },
                                modifier = Modifier.width(116.dp),
                            )
                            Box(Modifier.matchParentSize().clickable { showTimePicker = true })
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    CurrencyPicker(
                        label = stringResource(Res.string.editor_label_currency),
                        selected = state.currency,
                        onSelected = viewModel::setCurrency,
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
                    OutlinedTextField(
                        value = state.note,
                        onValueChange = viewModel::setNote,
                        label = { Text(stringResource(Res.string.editor_label_note)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            SectionCard(heading = stringResource(Res.string.editor_split)) {
                Column(Modifier.padding(16.dp)) {
                    // Itemized (receipt line-items) doesn't apply to money coming in.
                    val splitOptions =
                        if (state.kind.isIncome) SplitKind.entries.filter { it != SplitKind.ITEMIZED } else SplitKind.entries
                    val splitLabels = splitOptions.associateWith { it.label() }
                    ChipSelector(
                        options = splitOptions,
                        selectedOptions = listOf(state.splitKind),
                        onOptionSelected = viewModel::setKind,
                        onOptionDeselected = {},
                        optionLabel = { splitLabels.getValue(it) },
                    )
                    Spacer(Modifier.height(12.dp))

                    // The total lives with the split for every method that needs one. Items has no
                    // total to type — it's the sum of the lines entered below.
                    if (state.splitKind != SplitKind.ITEMIZED) {
                        val amountValid = isValidAmountInput(state.amount, state.currency)
                        OutlinedTextField(
                            value = state.amount,
                            onValueChange = viewModel::setAmount,
                            label = { Text(stringResource(Res.string.editor_label_amount)) },
                            singleLine = true,
                            isError = !amountValid,
                            supportingText =
                                if (!amountValid) ({ Text(stringResource(Res.string.error_invalid_total)) }) else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    when (state.splitKind) {
                        SplitKind.EQUAL -> {
                            Text(
                                stringResource(Res.string.editor_split_between),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            Spacer(Modifier.height(8.dp))
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
                                    SharesStepper(
                                        value = state.splitInput[member.id].orEmpty(),
                                        onValueChange = { viewModel.setSplitInput(member.id, it) },
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
                            val currency = state.currency
                            state.members.forEach { member ->
                                val text = state.splitInput[member.id].orEmpty()
                                val valid = isValidAmountInput(text, currency, requirePositive = false)
                                SplitInputRow(member = member, preview = null) {
                                    OutlinedTextField(
                                        value = text,
                                        onValueChange = { viewModel.setSplitInput(member.id, it) },
                                        placeholder = { Text(splitPlaceholder(state.splitKind)) },
                                        suffix = { Text(Currencies.symbol(currency)) },
                                        singleLine = true,
                                        isError = !valid,
                                        supportingText =
                                            if (!valid) {
                                                { Text(stringResource(Res.string.error_invalid_amount, member.name)) }
                                            } else {
                                                null
                                            },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        modifier = Modifier.width(140.dp),
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
                                onDraftLabel = viewModel::setDraftLabel,
                                onDraftAmount = viewModel::setDraftAmount,
                                onToggleParticipant = viewModel::toggleDraftParticipant,
                                onToggleAll = viewModel::toggleAllDraftParticipants,
                                onSubmit = viewModel::submitDraft,
                                onRemove = viewModel::removeItem,
                            )
                        }
                    }

                    RemainingHint(state)
                }
            }

            SectionCard(heading = stringResource(state.kind.payerHeadingRes())) {
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
                            val currency = state.currency
                            state.members.forEach { member ->
                                val text = state.paid[member.id].orEmpty()
                                val valid = isValidAmountInput(text, currency)
                                SplitInputRow(member = member, preview = null) {
                                    OutlinedTextField(
                                        value = text,
                                        onValueChange = { viewModel.setPaid(member.id, it) },
                                        placeholder = { Text("0") },
                                        suffix = { Text(Currencies.symbol(currency)) },
                                        singleLine = true,
                                        isError = !valid,
                                        supportingText =
                                            if (!valid) {
                                                { Text(stringResource(Res.string.error_invalid_paid, member.name)) }
                                            } else {
                                                null
                                            },
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

            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp))
            }

            Spacer(Modifier.height(16.dp))

            Button(onClick = viewModel::save, enabled = state.isValid(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(state.kind.saveActionRes(state.editing)))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * Category picker, chip-first: built-in presets then the group's custom categories, each a
 * single-select icon chip — tap to choose, tap the chosen one again to clear. "New" opens a dialog
 * to create a custom category (name, icon, color); long-pressing a custom chip edits or deletes it.
 * Presets aren't editable.
 */
@Composable
private fun CategoryField(
    selectedId: CategoryId?,
    presets: List<PresetCategory>,
    custom: List<Category>,
    onSelect: (CategoryId?) -> Unit,
    onCreate: (name: String, icon: String, color: Long) -> Unit,
    onUpdate: (id: CategoryId, name: String, icon: String, color: Long) -> Unit,
    onDelete: (CategoryId) -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Category?>(null) }

    Text(
        stringResource(Res.string.editor_label_category),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.outline,
    )
    Spacer(Modifier.height(4.dp))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        presets.forEach { preset ->
            CategoryChip(
                display =
                    CategoryDisplay(
                        preset.id,
                        stringResource(preset.nameRes),
                        categoryIcon(preset.iconKey),
                        categoryColor(preset.color),
                    ),
                selected = selectedId == preset.id,
                onClick = { onSelect(if (selectedId == preset.id) null else preset.id) },
                onLongClick = null,
            )
        }
        custom.forEach { cat ->
            CategoryChip(
                display = CategoryDisplay(cat.id, cat.name, categoryIcon(cat.icon), categoryColor(cat.color)),
                selected = selectedId == cat.id,
                onClick = { onSelect(if (selectedId == cat.id) null else cat.id) },
                onLongClick = { editing = cat },
            )
        }
        AssistChip(
            onClick = { showCreate = true },
            label = { Text(stringResource(Res.string.editor_category_new)) },
            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
        )
    }

    if (showCreate) {
        CategoryDialog(
            existing = null,
            onConfirm = { name, icon, color ->
                onCreate(name, icon, color)
                showCreate = false
            },
            onDelete = null,
            onDismiss = { showCreate = false },
        )
    }
    editing?.let { cat ->
        CategoryDialog(
            existing = cat,
            onConfirm = { name, icon, color ->
                onUpdate(cat.id, name, icon, color)
                editing = null
            },
            onDelete = {
                onDelete(cat.id)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryChip(
    display: CategoryDisplay,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (selected) display.color.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (selected) BorderStroke(1.dp, display.color) else null,
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Icon(display.icon, contentDescription = null, tint = display.color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(display.name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/** Create/edit dialog for a custom category: name, an icon from the catalog, and a color. */
@Composable
private fun CategoryDialog(
    existing: Category?,
    onConfirm: (name: String, icon: String, color: Long) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var icon by remember { mutableStateOf(existing?.icon ?: CATEGORY_ICON_KEYS.first()) }
    var color by remember { mutableStateOf(existing?.color ?: CATEGORY_COLORS.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (existing == null) Res.string.editor_category_new_title else Res.string.category_edit_title))
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.editor_category_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(Res.string.category_icon),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CATEGORY_ICON_KEYS.forEach { key ->
                        val chosen = key == icon
                        Surface(
                            shape = CircleShape,
                            color = if (chosen) categoryColor(color).copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (chosen) BorderStroke(2.dp, categoryColor(color)) else null,
                            modifier = Modifier.size(44.dp).clickable { icon = key },
                        ) {
                            Icon(
                                categoryIcon(key),
                                contentDescription = null,
                                tint = categoryColor(color),
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(Res.string.category_color),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CATEGORY_COLORS.forEach { c ->
                        val chosen = c == color
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(categoryColor(c))
                                .border(
                                    width = if (chosen) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape,
                                )
                                .clickable { color = c },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim(), icon, color) }, enabled = name.isNotBlank()) {
                Text(stringResource(if (existing == null) Res.string.action_add else Res.string.action_ok))
            }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                onDelete?.let {
                    TextButton(onClick = it) {
                        Text(stringResource(Res.string.category_delete), color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
            }
        },
    )
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
private fun paidByEqualHint(state: EntryEditorUiState): String {
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
private fun PaidRemainingHint(state: EntryEditorUiState) {
    val currency = state.currency
    val total = Money.parse(state.amount.trim(), currency) ?: return
    val assigned = state.members.sumOf { m -> Money.parse(state.paid[m.id].orEmpty().trim(), currency)?.minorUnits ?: 0L }
    val remaining = Money(total.minorUnits - assigned, currency)
    RemainingHintText(done = remaining.isZero, remainingDisplay = remaining.display())
}

/** Live "remaining to assign" feedback for the splits that must sum to a target (exact, percentage). */
@Composable
private fun RemainingHint(state: EntryEditorUiState) {
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
    field: @Composable () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
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
}

/**
 * The itemized-split editor. The running total sits at the top; committed lines show as read-only
 * rows (each removable); and a framed draft card at the bottom builds the next line — a label, an
 * amount, who shares it, then "Add item" (or Enter on the amount) commits it and resets for another.
 */
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

    // Total sits under the lines, receipt-style.
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
    // Commit, then jump back to the label field so the next line can be typed without reaching up.
    val submit = {
        if (draftValid) {
            onSubmit()
            labelFocus.requestFocus()
        }
    }
    val allSelected = members.isNotEmpty() && draftParticipants.size == members.size

    OutlinedCard(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = draftLabel,
                    onValueChange = onDraftLabel,
                    placeholder = { Text(stringResource(Res.string.editor_item_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f).focusRequester(labelFocus),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = draftAmount,
                    onValueChange = onDraftAmount,
                    placeholder = { Text(stringResource(Res.string.editor_placeholder_amount)) },
                    suffix = { Text(Currencies.symbol(currency)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.width(130.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(Res.string.editor_item_shared_by),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.weight(1f))
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

/**
 * −/+ stepper for the Shares split field. Replaces free-text entry: shares are always a small
 * non-negative whole number, so tapping is faster and less error-prone than opening a keyboard.
 */
@Composable
private fun SharesStepper(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val current = value.trim().toLongOrNull()?.coerceAtLeast(0) ?: 0L
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onValueChange((current - 1).coerceAtLeast(0).toString()) },
            enabled = current > 0,
        ) {
            Icon(Icons.Default.Remove, contentDescription = stringResource(Res.string.editor_shares_decrease))
        }
        Text(
            text = current.toString(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(28.dp),
        )
        IconButton(
            onClick = { onValueChange((current + 1).toString()) },
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.editor_shares_increase))
        }
    }
}

/** "4 people · ¥1,200 each" once the amount is valid; a plain count otherwise. */
@Composable
private fun equalSplitHint(state: EntryEditorUiState): String {
    val count = state.equalSelected.size
    val total = Money.parse(state.amount.trim(), state.currency)
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
    state: EntryEditorUiState,
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

/**
 * Live € equivalent next to a shares input. Unlike percentage, shares don't need to sum to
 * anything specific, so this can always call the real [Split.Shares.divide] once at least one
 * weight is entered — no "still typing" fallback needed.
 */
@Composable
private fun sharesPreview(
    state: EntryEditorUiState,
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

@Composable
private fun splitPlaceholder(kind: SplitKind): String =
    when (kind) {
        SplitKind.SHARES -> stringResource(Res.string.editor_placeholder_shares)
        SplitKind.PERCENTAGE -> stringResource(Res.string.editor_placeholder_percent)
        SplitKind.EXACT -> stringResource(Res.string.editor_placeholder_amount)
        SplitKind.EQUAL -> ""
        SplitKind.ITEMIZED -> ""
    }
