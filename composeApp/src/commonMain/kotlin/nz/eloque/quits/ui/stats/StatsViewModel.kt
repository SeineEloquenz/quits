package nz.eloque.quits.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import nz.eloque.quits.data.repository.GroupRepository
import nz.eloque.quits.domain.CategoryTotal
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.domain.MemberTotal
import nz.eloque.quits.domain.Money
import nz.eloque.quits.domain.spending

/**
 * One row of a breakdown: a [label] ([null] = uncategorized, resolved by the screen), an [amount],
 * and a [fraction] of the section's largest bar — magnitude only, so a single hue reads correctly.
 */
data class StatBar(
    val label: String?,
    val amount: Money,
    val fraction: Float,
    val memberId: MemberId? = null,
)

data class StatsUiState(
    val loaded: Boolean = false,
    val found: Boolean = true,
    val groupName: String = "",
    val total: Money = Money.zero(Currency.of("EUR")),
    val hasExpenses: Boolean = false,
    val byCategory: List<StatBar> = emptyList(),
    val byMember: List<StatBar> = emptyList(),
)

class StatsViewModel(
    repo: GroupRepository,
    groupId: GroupId,
) : ViewModel() {
    val state: StateFlow<StatsUiState> =
        repo
            .groupFlow(groupId)
            .map { group ->
                if (group == null) {
                    StatsUiState(loaded = true, found = false)
                } else {
                    val spending = group.spending()
                    val names = group.members.associate { it.id to it.name }
                    StatsUiState(
                        loaded = true,
                        found = true,
                        groupName = group.name,
                        total = spending.total,
                        hasExpenses = group.expenses.isNotEmpty(),
                        byCategory = spending.byCategory.toBars(),
                        byMember = spending.byMember.toBars(names),
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())
}

/** Largest positive amount in a section, or 1 as a safe divisor when everything is zero. */
private fun maxMinor(amounts: List<Long>): Long = amounts.maxOrNull()?.takeIf { it > 0 } ?: 1L

private fun List<CategoryTotal>.toBars(): List<StatBar> {
    val max = maxMinor(map { it.amount.minorUnits })
    return map { StatBar(it.category, it.amount, (it.amount.minorUnits.toFloat() / max).coerceIn(0f, 1f)) }
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
