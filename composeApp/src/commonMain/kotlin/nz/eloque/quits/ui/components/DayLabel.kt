package nz.eloque.quits.ui.components

import androidx.compose.runtime.Composable
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.date_today
import nz.eloque.quits.resources.date_yesterday
import nz.eloque.quits.util.DayBucket
import nz.eloque.quits.util.dayBucket
import nz.eloque.quits.util.formatDate
import org.jetbrains.compose.resources.stringResource

/** "Today" / "Yesterday" / a plain date, in the device's local timezone. */
@Composable
fun dayGroupLabel(epochMillis: Long): String =
    when (dayBucket(epochMillis)) {
        DayBucket.TODAY -> stringResource(Res.string.date_today)
        DayBucket.YESTERDAY -> stringResource(Res.string.date_yesterday)
        DayBucket.OTHER -> formatDate(epochMillis)
    }
