package nz.eloque.quits.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import nz.eloque.compose_kit.scaffold.AppScaffold
import nz.eloque.quits.domain.Category
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.SpendPeriod
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.cd_back
import nz.eloque.quits.resources.stats_by_category
import nz.eloque.quits.resources.stats_by_member
import nz.eloque.quits.resources.stats_empty
import nz.eloque.quits.resources.stats_over_time
import nz.eloque.quits.resources.stats_period_month
import nz.eloque.quits.resources.stats_period_week
import nz.eloque.quits.resources.stats_title
import nz.eloque.quits.resources.stats_total
import nz.eloque.quits.resources.stats_uncategorized
import nz.eloque.quits.resources.stats_week_of
import nz.eloque.quits.ui.category.categoryDisplay
import nz.eloque.quits.ui.components.EmptyHint
import nz.eloque.quits.ui.components.LoadingBox
import nz.eloque.quits.ui.components.MemberAvatar
import nz.eloque.quits.ui.components.MoneyText
import nz.eloque.quits.util.formatUtcDate
import nz.eloque.quits.util.formatUtcDayMonth
import nz.eloque.quits.util.formatUtcMonthAbbrev
import nz.eloque.quits.util.formatUtcMonthYear
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    groupId: GroupId,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<StatsViewModel>(key = groupId.value) { parametersOf(groupId) }
    val state by viewModel.state.collectAsState()

    AppScaffold(
        title = {
            Text(stringResource(Res.string.stats_title), style = MaterialTheme.typography.titleLarge, maxLines = 1)
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.cd_back))
            }
        },
    ) { scrollBehavior ->
        if (!state.loaded) {
            LoadingBox(Modifier.padding(top = 32.dp))
            return@AppScaffold
        }
        if (!state.found || !state.hasExpenses) {
            EmptyHint(stringResource(Res.string.stats_empty), Modifier.padding(top = 32.dp))
            return@AppScaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(Res.string.stats_total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                MoneyText(state.total, style = MaterialTheme.typography.headlineLarge)
            }

            if (state.availablePeriods.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                OverTimeSection(state.period, state.availablePeriods, state.overTime, viewModel::setPeriod)
            }

            if (state.byCategory.any { it.categoryId != null }) {
                Spacer(Modifier.height(24.dp))
                SectionHeader(stringResource(Res.string.stats_by_category))
                state.byCategory.forEach { StatBarRow(it, state.customCategories) }
            }

            Spacer(Modifier.height(16.dp))
            SectionHeader(stringResource(Res.string.stats_by_member))
            state.byMember.forEach { StatBarRow(it, state.customCategories) }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private val BAR_AREA_HEIGHT = 132.dp
private val BAR_WIDTH = 28.dp

/** UTC midnight of this calendar day, so the locale month/day formatters render the date as-is. */
@OptIn(ExperimentalTime::class)
private fun LocalDate.utcMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

@Composable
private fun OverTimeSection(
    period: SpendPeriod,
    availablePeriods: List<SpendPeriod>,
    bars: List<TimeBar>,
    onPeriod: (SpendPeriod) -> Unit,
) {
    SectionHeader(stringResource(Res.string.stats_over_time))
    if (availablePeriods.size > 1) {
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            availablePeriods.forEach { p ->
                FilterChip(
                    selected = p == period,
                    onClick = { onPeriod(p) },
                    label = { Text(stringResource(periodChipRes(p))) },
                )
            }
        }
    }
    if (bars.isEmpty()) return
    Spacer(Modifier.height(12.dp))
    SpendChart(bars, period)
}

private fun periodChipRes(period: SpendPeriod) =
    when (period) {
        SpendPeriod.WEEK -> Res.string.stats_period_week
        SpendPeriod.MONTH -> Res.string.stats_period_month
    }

@Composable
private fun SpendChart(
    bars: List<TimeBar>,
    period: SpendPeriod,
) {
    var selected by remember(bars) { mutableIntStateOf(bars.lastIndex) }
    val scroll = rememberScrollState()
    LaunchedEffect(bars, scroll.maxValue) { scroll.scrollTo(scroll.maxValue) }

    val current = bars.getOrElse(selected) { bars.last() }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            periodLabel(current.start, period),
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
        MoneyText(current.amount, style = MaterialTheme.typography.titleMedium)
    }
    Spacer(Modifier.height(8.dp))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(scroll),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        bars.forEachIndexed { index, bar ->
            BarColumn(bar, period, index == selected) { selected = index }
        }
    }
}

@Composable
private fun BarColumn(
    bar: TimeBar,
    period: SpendPeriod,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val color =
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(BAR_WIDTH).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick),
    ) {
        Box(Modifier.height(BAR_AREA_HEIGHT).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            // A nonzero bucket keeps a sliver of bar so small spend never vanishes to nothing.
            val fraction = if (bar.amount.minorUnits > 0) bar.fraction.coerceAtLeast(0.02f) else 0f
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .background(color),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            axisLabel(bar.start, period),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            maxLines = 1,
        )
    }
}

/** Caption label for the selected bucket: "July 2026" or "Week of 21 Jul 2026". */
@Composable
private fun periodLabel(
    start: LocalDate,
    period: SpendPeriod,
): String =
    when (period) {
        SpendPeriod.MONTH -> formatUtcMonthYear(start.utcMillis())
        SpendPeriod.WEEK -> stringResource(Res.string.stats_week_of, formatUtcDate(start.utcMillis()))
    }

/** Compact axis label under a bar: "Jul" for months, "21 Jul" for weeks. */
private fun axisLabel(
    start: LocalDate,
    period: SpendPeriod,
): String =
    when (period) {
        SpendPeriod.MONTH -> formatUtcMonthAbbrev(start.utcMillis())
        SpendPeriod.WEEK -> formatUtcDayMonth(start.utcMillis())
    }

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
}

/**
 * One breakdown row: label + value, then a single-hue magnitude bar (length ∝ the section's largest).
 * Colour is not used to distinguish rows — the label carries identity, the bar length the magnitude.
 */
@Composable
private fun StatBarRow(
    bar: StatBar,
    categories: List<Category>,
) {
    val display = if (bar.memberId == null) categoryDisplay(bar.categoryId, categories) else null
    val barColor = display?.color ?: MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when {
                bar.memberId != null -> {
                    MemberAvatar(name = bar.label ?: "?", id = bar.memberId, size = 28.dp)
                    Text(bar.label ?: "?", Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                display != null -> {
                    Icon(display.icon, contentDescription = null, tint = display.color, modifier = Modifier.size(22.dp))
                    Text(display.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                else ->
                    Text(
                        stringResource(Res.string.stats_uncategorized),
                        Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
            }
            MoneyText(bar.amount)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(bar.fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor),
            )
        }
    }
}
