package nz.eloque.quits.ui.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nz.eloque.quits.data.repository.GroupRepository
import nz.eloque.quits.data.sync.SyncEngine
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.ExpenseId
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.domain.Money
import nz.eloque.quits.domain.Split

data class ExpenseParticipantRow(
    val id: MemberId,
    val name: String,
    val amount: Money,
)

data class ExpenseDetailUiState(
    val loaded: Boolean = false,
    /** False once the expense (or the group) no longer exists, e.g. it was deleted elsewhere. */
    val found: Boolean = true,
    val title: String = "",
    val total: Money = Money.zero(Currency.of("USD")),
    val splitKind: SplitKind = SplitKind.EQUAL,
    val participantCount: Int = 0,
    val spentAt: Long = 0L,
    val paidBy: List<ExpenseParticipantRow> = emptyList(),
    val owedBy: List<ExpenseParticipantRow> = emptyList(),
)

class ExpenseDetailViewModel(
    private val repo: GroupRepository,
    private val engine: SyncEngine,
    private val groupId: GroupId,
    private val expenseId: ExpenseId,
) : ViewModel() {
    val state: StateFlow<ExpenseDetailUiState> =
        repo
            .groupFlow(groupId)
            .map { group ->
                val expense = group?.expenses?.find { it.id == expenseId }
                if (group == null || expense == null) {
                    ExpenseDetailUiState(loaded = true, found = false)
                } else {
                    val names = group.members.associate { it.id to it.name }
                    ExpenseDetailUiState(
                        loaded = true,
                        found = true,
                        title = expense.title,
                        total = expense.total,
                        splitKind =
                            when (expense.split) {
                                is Split.Equal -> SplitKind.EQUAL
                                is Split.Shares -> SplitKind.SHARES
                                is Split.Percentage -> SplitKind.PERCENTAGE
                                is Split.Exact -> SplitKind.EXACT
                            },
                        participantCount = expense.shares.size,
                        spentAt = expense.spentAt,
                        paidBy =
                            expense.payments
                                .map { it.payer }
                                .distinct()
                                .sortedBy { names[it] ?: "" }
                                .map { ExpenseParticipantRow(it, names[it] ?: "?", expense.paidBy(it)) },
                        owedBy =
                            expense.shares.keys
                                .sortedBy { names[it] ?: "" }
                                .map { ExpenseParticipantRow(it, names[it] ?: "?", expense.owedBy(it)) },
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpenseDetailUiState())

    private val _deleted = Channel<Unit>(Channel.BUFFERED)

    /** Emits once the delete has been saved locally, so the screen can navigate back. */
    val deleted: Flow<Unit> = _deleted.receiveAsFlow()

    /**
     * Deletes immediately — this screen's trash icon is already the deliberate, confirmed action
     * (unlike the swipe-then-undo on the activity feed, there's no second affordance to layer an
     * undo window onto here without adding one), then best-effort syncs.
     */
    fun delete() {
        viewModelScope.launch {
            repo.deleteExpense(expenseId)
            _deleted.send(Unit)
            try {
                engine.sync(groupId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The delete is already saved locally; a sync failure shouldn't block leaving the screen.
            }
        }
    }
}
