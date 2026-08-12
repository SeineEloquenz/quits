package nz.eloque.quits.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.action_cancel
import nz.eloque.quits.resources.action_ok
import nz.eloque.quits.resources.editor_label_date
import nz.eloque.quits.resources.editor_label_time
import nz.eloque.quits.util.formatLocalDate
import nz.eloque.quits.util.formatLocalTime
import nz.eloque.quits.util.localDateMillisUtc
import nz.eloque.quits.util.localHourMinute
import nz.eloque.quits.util.offsetZone
import nz.eloque.quits.util.withPickedDate
import nz.eloque.quits.util.withPickedTime
import org.jetbrains.compose.resources.stringResource

/**
 * A Date [ListPickerRow] and a Time [ListPickerRow] (with a divider between) that own their own
 * date/time picker dialogs. Emits the two rows for placement inside a [ListFieldCard]; the caller
 * adds any divider + further rows after. Centralises the picker-dialog boilerplate that every entry
 * timestamp shares — [onChange] receives the new epoch-milli timestamp.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeRows(
    timestamp: Long,
    tzOffsetMinutes: Int,
    onChange: (Long) -> Unit,
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    ListPickerRow(
        icon = Icons.Default.CalendarMonth,
        label = stringResource(Res.string.editor_label_date),
        value = formatLocalDate(timestamp, tzOffsetMinutes),
        onClick = { showDatePicker = true },
    )
    ListRowDivider()
    ListPickerRow(
        icon = Icons.Default.Schedule,
        label = stringResource(Res.string.editor_label_time),
        value = formatLocalTime(timestamp, tzOffsetMinutes),
        onClick = { showTimePicker = true },
    )

    if (showDatePicker) {
        val zone = offsetZone(tzOffsetMinutes)
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = localDateMillisUtc(timestamp, zone))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { onChange(withPickedDate(timestamp, it, zone)) }
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
        val zone = offsetZone(tzOffsetMinutes)
        val (initialHour, initialMinute) = remember { localHourMinute(timestamp, zone) }
        val timeState = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute)
        BasicAlertDialog(onDismissRequest = { showTimePicker = false }) {
            Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
                Column(Modifier.padding(20.dp)) {
                    TimePicker(state = timeState)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showTimePicker = false }) { Text(stringResource(Res.string.action_cancel)) }
                        TextButton(onClick = {
                            onChange(withPickedTime(timestamp, timeState.hour, timeState.minute, zone))
                            showTimePicker = false
                        }) { Text(stringResource(Res.string.action_ok)) }
                    }
                }
            }
        }
    }
}
