package nz.eloque.quits.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.error_member_in_use
import org.jetbrains.compose.resources.getString

data class MemberExpenseRow(
    val id: ExpenseId,
    val title: String,
    val total: Money,
    val paidByThem: Money,
    val owedByThem: Money,
)

data class MemberDetailUiState(
    val loaded: Boolean = false,
    val found: Boolean = true,
    val name: String = "",
    val net: Money = Money.zero(Currency.of("USD")),
    val expenses: List<MemberExpenseRow> = emptyList(),
)

class MemberDetailViewModel(
    private val repo: GroupRepository,
    private val engine: SyncEngine,
    private val groupId: GroupId,
    private val memberId: MemberId,
) : ViewModel() {
    val state: StateFlow<MemberDetailUiState> =
        repo
            .groupFlow(groupId)
            .map { group ->
                val member = group?.members?.find { it.id == memberId }
                if (group == null || member == null) {
                    MemberDetailUiState(loaded = true, found = false)
                } else {
                    val related =
                        group.expenses.filter { expense ->
                            memberId in expense.shares.keys || expense.payments.any { it.payer == memberId }
                        }
                    MemberDetailUiState(
                        loaded = true,
                        found = true,
                        name = member.name,
                        net = group.balances().of(memberId),
                        expenses =
                            related.map { expense ->
                                MemberExpenseRow(
                                    expense.id,
                                    expense.title,
                                    expense.total,
                                    expense.paidBy(memberId),
                                    expense.owedBy(memberId),
                                )
                            },
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemberDetailUiState())

    private val _removed = Channel<Unit>(Channel.BUFFERED)
    val removed: Flow<Unit> = _removed.receiveAsFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun rename(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repo.renameMember(memberId, trimmed)
            trySync()
        }
    }

    /** Same referential guard as the old overflow menu: refuses if still tied to an expense/settlement. */
    fun remove() {
        viewModelScope.launch {
            if (repo.removeMember(groupId, memberId)) {
                trySync()
                _removed.send(Unit)
            } else {
                _error.value = getString(Res.string.error_member_in_use)
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }

    private suspend fun trySync() {
        try {
            engine.sync(groupId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The change is already saved locally; a sync failure shouldn't block this screen.
        }
    }
}
