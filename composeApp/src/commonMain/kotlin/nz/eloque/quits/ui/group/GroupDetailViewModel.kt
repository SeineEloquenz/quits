package nz.eloque.quits.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nz.eloque.quits.data.repository.GroupRepository
import nz.eloque.quits.data.sync.SyncEngine
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
    val shareCode: String? = null,
    val lastSyncedAt: Long? = null,
)

class GroupDetailViewModel(
    private val repo: GroupRepository,
    private val engine: SyncEngine,
    private val groupId: GroupId,
) : ViewModel() {
    val state: StateFlow<GroupDetailUiState> =
        combine(repo.groupFlow(groupId), engine.syncInfoFlow(groupId)) { group, info ->
            (group?.toUiState() ?: GroupDetailUiState()).copy(shareCode = info.code, lastSyncedAt = info.lastSyncedAt)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupDetailUiState())

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    init {
        // Pull the latest on open (no-op for a local-only group).
        viewModelScope.launch { trySync() }
    }

    /** Registers the group with the relay and shows its share code. */
    fun share() {
        viewModelScope.launch {
            _syncStatus.value = SyncStatus.Syncing
            try {
                engine.share(groupId)
                _syncStatus.value = SyncStatus.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _syncStatus.value = SyncStatus.Failed("Couldn't reach the relay: ${e.message}")
            }
        }
    }

    /** Push local changes and pull remote ones. */
    fun sync() {
        viewModelScope.launch { trySync() }
    }

    fun dismissError() {
        if (_syncStatus.value is SyncStatus.Failed) _syncStatus.value = SyncStatus.Idle
    }

    fun addMember(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repo.addMember(groupId, Member(MemberId(newId()), trimmed))
            trySync()
        }
    }

    fun renameMember(
        id: MemberId,
        name: String,
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repo.renameMember(id, trimmed)
            trySync()
        }
    }

    fun removeMember(id: MemberId) {
        viewModelScope.launch {
            if (repo.removeMember(groupId, id)) {
                trySync()
            } else {
                _syncStatus.value = SyncStatus.Failed("Can't remove a member who's in an expense or settlement.")
            }
        }
    }

    fun record(transfer: Transfer) {
        viewModelScope.launch {
            repo.upsertSettlement(
                groupId,
                Settlement(SettlementId(newId()), transfer.from, transfer.to, transfer.amount),
                paidAt = nowMillis(),
            )
            trySync()
        }
    }

    fun deleteExpense(id: ExpenseId) {
        viewModelScope.launch {
            repo.deleteExpense(id)
            trySync()
        }
    }

    /** Syncs without letting a network failure crash the app; the local change is already saved. */
    private suspend fun trySync() {
        _syncStatus.value = SyncStatus.Syncing
        try {
            engine.sync(groupId)
            _syncStatus.value = SyncStatus.Idle
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _syncStatus.value = SyncStatus.Failed("Sync failed: ${e.message}")
        }
    }
}

sealed interface SyncStatus {
    data object Idle : SyncStatus

    data object Syncing : SyncStatus

    data class Failed(
        val message: String,
    ) : SyncStatus
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
