package nz.eloque.quits.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nz.eloque.quits.data.repository.GroupRepository
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.ExpenseId
import nz.eloque.quits.domain.Group
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.Member
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.domain.Money
import nz.eloque.quits.domain.Settlement
import nz.eloque.quits.domain.SettlementId
import nz.eloque.quits.domain.Transfer
import nz.eloque.quits.util.newId
import nz.eloque.quits.util.nowMillis

data class MemberBalance(
    val id: MemberId,
    val name: String,
    val net: Money,
)

data class TransferRow(
    val from: String,
    val to: String,
    val transfer: Transfer,
)

data class ExpenseRow(
    val id: ExpenseId,
    val title: String,
    val total: Money,
    val paidBy: String,
)

data class GroupDetailUiState(
    val loaded: Boolean = false,
    val name: String = "",
    val baseCurrency: Currency = Currency.of("USD"),
    val members: List<MemberBalance> = emptyList(),
    val transfers: List<TransferRow> = emptyList(),
    val expenses: List<ExpenseRow> = emptyList(),
)

class GroupDetailViewModel(
    private val repo: GroupRepository,
    private val groupId: GroupId,
) : ViewModel() {
    val state: StateFlow<GroupDetailUiState> =
        repo
            .groupFlow(groupId)
            .map { it?.toUiState() ?: GroupDetailUiState() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupDetailUiState())

    fun addMember(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repo.addMember(groupId, Member(MemberId(newId()), trimmed))
        }
    }

    fun record(transfer: Transfer) {
        viewModelScope.launch {
            repo.upsertSettlement(
                groupId,
                Settlement(SettlementId(newId()), transfer.from, transfer.to, transfer.amount),
                paidAt = nowMillis(),
            )
        }
    }

    fun deleteExpense(id: ExpenseId) {
        viewModelScope.launch {
            repo.deleteExpense(id)
        }
    }
}

private fun Group.toUiState(): GroupDetailUiState {
    val names = members.associate { it.id to it.name }
    val balances = balances()
    return GroupDetailUiState(
        loaded = true,
        name = name,
        baseCurrency = baseCurrency,
        members = members.map { MemberBalance(it.id, it.name, balances.of(it.id)) },
        transfers =
            balances.simplify().map {
                TransferRow(names[it.from] ?: "?", names[it.to] ?: "?", it)
            },
        expenses =
            expenses.map { expense ->
                val paidBy =
                    expense.payments
                        .map { names[it.payer] ?: "?" }
                        .distinct()
                        .joinToString(", ")
                ExpenseRow(expense.id, expense.title, expense.total, paidBy)
            },
    )
}
