package nz.eloque.quits.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import nz.eloque.quits.data.repository.GroupRepository
import nz.eloque.quits.domain.Category
import nz.eloque.quits.domain.CategoryId
import nz.eloque.quits.domain.CategoryTotal
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.domain.MemberTotal
import nz.eloque.quits.domain.Money
import nz.eloque.quits.domain.PeriodTotal
import nz.eloque.quits.domain.SpendPeriod
import nz.eloque.quits.domain.isExpense
import nz.eloque.quits.domain.spendOverTime
import nz.eloque.quits.domain.spending
import nz.eloque.quits.ui.category.PRESET_IDS

/**
 * One row of a breakdown: a [label] ([null] = uncategorized, resolved by the screen), an [amount],
 * and a [fraction] of the section's largest bar — magnitude only, so a single hue reads correctly.
 */
data class StatBar(
    val label: String?,
    val amount: Money,
    val fraction: Float,
    val memberId: MemberId? = null,
    val categoryId: CategoryId? = null,
)

/** One bar of the spend-over-time chart: a bucket [start] date, its [amount], and its [fraction] of the tallest bar. */
data class TimeBar(
    val start: LocalDate,
    val amount: Money,
    val fraction: Float,
)

data class StatsUiState(
    val loaded: Boolean = false,
    val found: Boolean = true,
    val groupName: String = "",
    val total: Money = Money.zero(Currency.of("EUR")),
    val hasExpenses: Boolean = false,
    val byCategory: List<StatBar> = emptyList(),
    val byMember: List<StatBar> = emptyList(),
    val period: SpendPeriod = SpendPeriod.MONTH,
    /** Periods worth charting — only those spanning more than one bucket. Empty hides the section. */
    val availablePeriods: List<SpendPeriod> = emptyList(),
    val overTime: List<TimeBar> = emptyList(),
    /** The group's custom categories, for resolving category bar names/icons/colors. */
    val customCategories: List<Category> = emptyList(),
)

class StatsViewModel(
    repo: GroupRepository,
    groupId: GroupId,
) : ViewModel() {
    private val period = MutableStateFlow(SpendPeriod.MONTH)

    fun setPeriod(value: SpendPeriod) {
        period.value = value
    }

    val state: StateFlow<StatsUiState> =
        combine(repo.groupFlow(groupId), period) { group, period ->
            if (group == null) {
                StatsUiState(loaded = true, found = false)
            } else {
                val spending = group.spending()
                val names = group.members.associate { it.id to it.name }
                // Fold any id this build can't resolve (a newer preset, or an unsynced custom
                // category) into the uncategorized bucket, so stats never show a nameless bar.
                val validIds = PRESET_IDS + group.categories.map { it.id }.toSet()
                val byCategory =
                    spending.byCategory
                        .groupBy { it.categoryId?.takeIf { id -> id in validIds } }
                        .map { (id, totals) -> CategoryTotal(id, totals.fold(Money.zero(spending.base)) { a, t -> a + t.amount }) }
                        .sortedByDescending { it.amount.minorUnits }
                val byPeriod = SpendPeriod.entries.associateWith { group.spendOverTime(it) }
                val available = SpendPeriod.entries.filter { (byPeriod.getValue(it).size) > 1 }
                val effective = period.takeIf { it in available } ?: available.firstOrNull() ?: period
                StatsUiState(
                    loaded = true,
                    found = true,
                    groupName = group.name,
                    total = spending.total,
                    hasExpenses = group.entries.any { it.kind.isExpense },
                    byCategory = byCategory.toBars(),
                    byMember = spending.byMember.toBars(names),
                    period = effective,
                    availablePeriods = available,
                    overTime = if (available.isEmpty()) emptyList() else byPeriod.getValue(effective).toTimeBars(),
                    customCategories = group.categories,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())
}

/** Largest positive amount in a section, or 1 as a safe divisor when everything is zero. */
private fun maxMinor(amounts: List<Long>): Long = amounts.maxOrNull()?.takeIf { it > 0 } ?: 1L

private fun List<CategoryTotal>.toBars(): List<StatBar> {
    val max = maxMinor(map { it.amount.minorUnits })
    return map {
        StatBar(
            label = null,
            amount = it.amount,
            fraction = (it.amount.minorUnits.toFloat() / max).coerceIn(0f, 1f),
            categoryId = it.categoryId,
        )
    }
}

private fun List<PeriodTotal>.toTimeBars(): List<TimeBar> {
    val max = maxMinor(map { it.amount.minorUnits })
    return map { TimeBar(it.start, it.amount, (it.amount.minorUnits.toFloat() / max).coerceIn(0f, 1f)) }
}

private fun List<MemberTotal>.toBars(names: Map<MemberId, String>): List<StatBar> {
    val max = maxMinor(map { it.amount.minorUnits })
    return map {
        StatBar(
            label = names[it.member] ?: "?",
            amount = it.amount,
            fraction = (it.amount.minorUnits.toFloat() / max).coerceIn(0f, 1f),
            memberId = it.member,
        )
    }
}
