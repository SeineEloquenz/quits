package nz.eloque.quits.util

import java.text.DateFormat
import java.util.Date

actual fun formatLocalDate(epochMillis: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))

actual fun formatLocalTime(epochMillis: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochMillis))
