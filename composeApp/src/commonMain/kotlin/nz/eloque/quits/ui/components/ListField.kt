package nz.eloque.quits.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Grouped-list card (Direction C): an [ElevatedCard] whose children are field rows. Callers place
 * [ListPickerRow] / [ListTextRow] and separate them with [ListRowDivider]. Bakes in the same outer
 * vertical spacing as `SectionCard` so the two can sit next to each other evenly.
 */
@Composable
fun ListFieldCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(Modifier.fillMaxWidth(), content = content)
    }
}

/** Hairline divider inset past the leading-icon column, for use between rows in a [ListFieldCard]. */
@Composable
fun ListRowDivider() {
    HorizontalDivider(
        Modifier.padding(start = 52.dp),
        thickness = Dp.Hairline,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * A tap-to-open row: leading icon, label, the current value (right-aligned), and a chevron. Used for
 * fields whose value is chosen in a picker/dialog (date, time, currency). [placeholder] renders the
 * value muted when nothing is chosen yet.
 */
@Composable
fun ListPickerRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: Boolean = false,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(16.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (placeholder) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * An inline text-entry row: leading icon, label, and a borderless [BasicTextField] whose text is
 * right-aligned so it lines up with the values in [ListPickerRow]s. An optional [supporting] line
 * (tinted for [isError]) sits beneath, mirroring a Material field's supporting text.
 */
@Composable
fun ListTextRow(
    icon: ImageVector,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    isError: Boolean = false,
    supporting: String? = null,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(16.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.width(16.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle =
                        MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.End,
                        ),
                    singleLine = singleLine,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = fieldModifier.fillMaxWidth(),
                )
            }
        }
        if (supporting != null) {
            Text(
                supporting,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(start = 52.dp, end = 16.dp, bottom = 12.dp),
            )
        }
    }
}

/**
 * A compact borderless value field for member breakdown rows (percentage, exact amounts): a
 * right-aligned [BasicTextField] with an optional [suffix] and a thin underline, signalling an
 * editable field without the weight of a full outlined box.
 */
@Composable
fun InlineEntryField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    placeholder: String = "",
    suffix: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = true,
    isError: Boolean = false,
    alignEnd: Boolean = true,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f),
                contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        placeholder,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle =
                        MaterialTheme.typography.bodyLarge.copy(
                            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
                        ),
                    singleLine = singleLine,
                    keyboardOptions = keyboardOptions,
                    keyboardActions = keyboardActions,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = fieldModifier.fillMaxWidth(),
                )
            }
            if (suffix != null) {
                Spacer(Modifier.width(4.dp))
                Text(suffix, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.outline)
            }
        }
        Spacer(Modifier.height(3.dp))
        HorizontalDivider(color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline)
    }
}

/**
 * A [ListPickerRow] that opens a searchable bottom sheet — the row-styled counterpart of compose-kit's
 * `SearchablePickerField`, for pickers with too many options for a dropdown (e.g. currency).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SearchablePickerRow(
    icon: ImageVector,
    label: String,
    selected: T,
    selectedLabel: (T) -> String,
    onSelected: (T) -> Unit,
    search: (query: String) -> List<T>,
    itemKey: (T) -> Any,
    itemLabel: (T) -> String,
    modifier: Modifier = Modifier,
    searchLabel: String = "Search",
    emptyLabel: String = "No match",
) {
    var open by remember { mutableStateOf(false) }

    ListPickerRow(
        icon = icon,
        label = label,
        value = selectedLabel(selected),
        onClick = { open = true },
        modifier = modifier,
    )

    if (open) {
        ModalBottomSheet(onDismissRequest = { open = false }) {
            var query by remember { mutableStateOf("") }
            val results = remember(query) { search(query) }

            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(searchLabel) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                    items(results, key = itemKey) { item ->
                        Text(
                            itemLabel(item),
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelected(item)
                                    open = false
                                }
                                .padding(vertical = 14.dp),
                        )
                    }
                    if (results.isEmpty()) {
                        item {
                            Text(emptyLabel, Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}
