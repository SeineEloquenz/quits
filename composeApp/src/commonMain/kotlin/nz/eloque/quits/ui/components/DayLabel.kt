package nz.eloque.quits.ui.components

import androidx.compose.runtime.Composable
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.date_today
import nz.eloque.quits.resources.date_yesterday
import nz.eloque.quits.util.DayBucket
import nz.eloque.quits.util.currentOffsetMinutes
import nz.eloque.quits.util.dayBucket
import nz.eloque.quits.util.formatLocalDate
import org.jetbrains.compose.resources.stringResource

/** "Today" / "Yesterday" / a locale-formatted date, read in the record's captured [offsetMinutes]. */
@Composable
fun dayGroupLabel(
    epochMillis: Long,
    offsetMinutes: Int = currentOffsetMinutes(epochMillis),
): String =
    when (dayBucket(epochMillis, offsetMinutes)) {
        DayBucket.TODAY -> stringResource(Res.string.date_today)
        DayBucket.YESTERDAY -> stringResource(Res.string.date_yesterday)
        DayBucket.OTHER -> formatLocalDate(epochMillis, offsetMinutes)
    }
