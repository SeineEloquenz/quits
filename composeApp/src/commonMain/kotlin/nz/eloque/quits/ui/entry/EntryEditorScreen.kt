package nz.eloque.quits.ui.entry

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.components.SectionCard
import nz.eloque.compose_kit.input.AbbreviatingText
import nz.eloque.compose_kit.scaffold.AppScaffold
import nz.eloque.quits.domain.Category
import nz.eloque.quits.domain.CategoryId
import nz.eloque.quits.domain.EntryKind
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.action_add
import nz.eloque.quits.resources.action_cancel
import nz.eloque.quits.resources.action_ok
import nz.eloque.quits.resources.category_color
import nz.eloque.quits.resources.category_delete
import nz.eloque.quits.resources.category_edit_title
import nz.eloque.quits.resources.category_icon
import nz.eloque.quits.resources.cd_category_color_option
import nz.eloque.quits.resources.editor_category_name
import nz.eloque.quits.resources.editor_category_new
import nz.eloque.quits.resources.editor_category_new_title
import nz.eloque.quits.resources.editor_label_category
import nz.eloque.quits.resources.editor_label_note
import nz.eloque.quits.resources.editor_label_title
import nz.eloque.quits.ui.category.CATEGORY_COLORS
import nz.eloque.quits.ui.category.CATEGORY_ICON_KEYS
import nz.eloque.quits.ui.category.CategoryDisplay
import nz.eloque.quits.ui.category.PresetCategory
import nz.eloque.quits.ui.category.categoryColor
import nz.eloque.quits.ui.category.categoryIcon
import nz.eloque.quits.ui.category.categoryIconLabel
import nz.eloque.quits.ui.category.presetsFor
import nz.eloque.quits.ui.components.DateTimeRows
import nz.eloque.quits.ui.components.ListFieldCard
import nz.eloque.quits.ui.components.ListRowDivider
import nz.eloque.quits.ui.components.ListTextRow
import nz.eloque.quits.ui.components.LoadingBox
import nz.eloque.quits.ui.components.display
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
    val titleFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        viewModel.saved.collect { onDone() }
    }

    LaunchedEffect(state.loaded) {
        if (state.loaded && !state.editing) titleFocus.requestFocus()
    }

    AppScaffold(
        title = {
            AbbreviatingText(
                stringResource(state.kind.titleRes(state.editing)),
                style = MaterialTheme.typography.titleLarge,
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

            ListFieldCard {
                ListTextRow(
                    icon = Icons.Default.Title,
                    label = stringResource(Res.string.editor_label_title),
                    value = state.title,
                    onValueChange = viewModel::setTitle,
                    fieldModifier = Modifier.focusRequester(titleFocus),
                )
                ListRowDivider()
                EntryForm(state.form, viewModel)
            }

            SectionCard {
                Column(Modifier.padding(16.dp)) {
                    CategoryField(
                        selectedId = state.categoryId,
                        presets = presetsFor(state.kind),
                        custom = state.categories,
                        onSelect = viewModel::setCategoryId,
                        onCreate = viewModel::createCategory,
                        onUpdate = viewModel::updateCategory,
                        onDelete = viewModel::deleteCategory,
                    )
                }
            }

            ListFieldCard {
                DateTimeRows(
                    timestamp = state.spentAt,
                    tzOffsetMinutes = state.tzOffsetMinutes,
                    onChange = viewModel::setSpentAt,
                )
                ListRowDivider()
                ListTextRow(
                    icon = Icons.AutoMirrored.Filled.Notes,
                    label = stringResource(Res.string.editor_label_note),
                    value = state.note,
                    onValueChange = viewModel::setNote,
                    singleLine = false,
                )
            }

            state.error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp))
            }

            Spacer(Modifier.height(16.dp))

            Button(onClick = viewModel::save, enabled = state.form.isValid(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(state.kind.saveActionRes(state.editing)))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Chip-first category picker: presets then custom categories as single-select icon chips; "New" creates one, long-press edits/deletes a custom chip. */
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
        modifier = Modifier.minimumInteractiveComponentSize().combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Icon(display.icon, contentDescription = null, tint = display.color, modifier = Modifier.size(18.dp))
            Text(
                display.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
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
                        val iconLabel = categoryIconLabel(key)
                        Surface(
                            shape = CircleShape,
                            color = if (chosen) categoryColor(color).copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (chosen) BorderStroke(2.dp, categoryColor(color)) else null,
                            modifier =
                                Modifier
                                    .minimumInteractiveComponentSize()
                                    .size(44.dp)
                                    .selectable(selected = chosen, role = Role.RadioButton) { icon = key }
                                    .semantics { contentDescription = iconLabel },
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
                    CATEGORY_COLORS.forEachIndexed { index, c ->
                        val chosen = c == color
                        val colorLabel = stringResource(Res.string.cd_category_color_option, index + 1)
                        Box(
                            Modifier
                                .minimumInteractiveComponentSize()
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(categoryColor(c))
                                .border(
                                    width = if (chosen) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape,
                                )
                                .selectable(selected = chosen, role = Role.RadioButton) { color = c }
                                .semantics { contentDescription = colorLabel },
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
                        Text(stringResource(Res.string.category_delete))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
            }
        },
    )
}
