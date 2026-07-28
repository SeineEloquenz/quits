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
import kotlinx.coroutines.flow.update
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
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.error_relay_unreachable
import nz.eloque.quits.resources.error_sync_failed
import nz.eloque.quits.util.newId
import nz.eloque.quits.util.nowMillis
import org.jetbrains.compose.resources.getString

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
    val spentAt: Long,
    val category: String? = null,
    val note: String? = null,
    /** Everyone tied to the expense (payers and share-holders), for the member filter. */
    val participants: Set<MemberId> = emptySet(),
)

data class SettlementRow(
    val id: SettlementId,
    val from: String,
    val to: String,
    val amount: Money,
    val paidAt: Long,
    val fromId: MemberId? = null,
    val toId: MemberId? = null,
)

/**
 * Feed search/filter, applied in the UI layer over the merged activity. A blank query and empty
 * selections mean "show everything".
 */
data class ActivityFilter(
    val query: String = "",
    val category: String? = null,
    val members: Set<MemberId> = emptySet(),
) {
    val isActive: Boolean get() = query.isNotBlank() || category != null || members.isNotEmpty()
}

/**
 * One row in the merged activity feed. Expenses and settlements are unrelated domain types with
 * different fields, but the feed shows them interleaved by time — this is the UI-layer join, not
 * a new domain concept.
 */
sealed interface ActivityEntry {
    val timestamp: Long

    data class ExpenseEntry(
        val row: ExpenseRow,
    ) : ActivityEntry {
        override val timestamp: Long get() = row.spentAt
    }

    data class SettlementEntry(
        val row: SettlementRow,
    ) : ActivityEntry {
        override val timestamp: Long get() = row.paidAt
    }
}

data class GroupDetailUiState(
    val loaded: Boolean = false,
    val name: String = "",
    val baseCurrency: Currency = Currency.of("EUR"),
    val members: List<MemberBalance> = emptyList(),
    val transfers: List<TransferRow> = emptyList(),
    /** Expenses and recorded settlements, merged, sorted newest-first, and filtered by [filter]. */
    val activity: List<ActivityEntry> = emptyList(),
    /** Distinct categories present across all (unfiltered) expenses, for the filter chips. */
    val categories: List<String> = emptyList(),
    val filter: ActivityFilter = ActivityFilter(),
    val settled: Boolean = true,
    val shareCode: String? = null,
    val lastSyncedAt: Long? = null,
)

class GroupDetailViewModel(
    private val repo: GroupRepository,
    private val engine: SyncEngine,
    private val groupId: GroupId,
) : ViewModel() {
    private val filter = MutableStateFlow(ActivityFilter())

    val state: StateFlow<GroupDetailUiState> =
        combine(repo.groupFlow(groupId), engine.syncInfoFlow(groupId), filter) { group, info, filter ->
            (group?.toUiState(filter) ?: GroupDetailUiState(filter = filter))
                .copy(shareCode = info.code, lastSyncedAt = info.lastSyncedAt)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupDetailUiState())

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    fun setQuery(value: String) = filter.update { it.copy(query = value) }

    fun toggleCategoryFilter(category: String) =
        filter.update { if (it.category == category) it.copy(category = null) else it.copy(category = category) }

    fun toggleMemberFilter(id: MemberId) =
        filter.update { if (id in it.members) it.copy(members = it.members - id) else it.copy(members = it.members + id) }

    fun clearFilters() = filter.update { ActivityFilter() }

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
                _syncStatus.value = SyncStatus.Failed(getString(Res.string.error_relay_unreachable, e.message ?: ""))
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

    /**
     * Leaves the group, removing it from this device only
     */
    fun leave() {
        viewModelScope.launch { repo.leaveGroup(groupId) }
    }

    fun addMember(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repo.addMember(groupId, Member(MemberId(newId()), trimmed))
            trySync()
        }
    }

    fun record(transfer: Transfer) {
        viewModelScope.launch {
            // paidAt lives on the domain object now — the repository still accepts an explicit
            // override, but recording always means "now" here, so this is the single source of truth.
            repo.upsertSettlement(
                groupId,
                Settlement(SettlementId(newId()), transfer.from, transfer.to, transfer.amount, paidAt = nowMillis()),
            )
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
            _syncStatus.value = SyncStatus.Failed(getString(Res.string.error_sync_failed, e.message ?: ""))
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

private fun Group.toUiState(filter: ActivityFilter): GroupDetailUiState {
    val names = members.associate { it.id to it.name }
    val balances = balances()

    val expenseEntries =
        expenses.map { expense ->
            val paidBy =
                expense.payments
                    .map { names[it.payer] ?: "?" }
                    .distinct()
                    .joinToString(", ")
            val participants = (expense.payments.map { it.payer } + expense.shares.keys).toSet()
            ActivityEntry.ExpenseEntry(
                ExpenseRow(
                    expense.id,
                    expense.title,
                    expense.total,
                    paidBy,
                    expense.spentAt,
                    expense.category,
                    expense.note,
                    participants,
                ),
            )
        }
    val settlementEntries =
        settlements.map { settlement ->
            ActivityEntry.SettlementEntry(
                SettlementRow(
                    settlement.id,
                    names[settlement.from] ?: "?",
                    names[settlement.to] ?: "?",
                    settlement.amount,
                    settlement.paidAt,
                    settlement.from,
                    settlement.to,
                ),
            )
        }

    val allEntries = (expenseEntries + settlementEntries).sortedByDescending { it.timestamp }

    return GroupDetailUiState(
        loaded = true,
        name = name,
        baseCurrency = baseCurrency,
        members = members.map { MemberBalance(it.id, it.name, balances.of(it.id)) },
        transfers =
            balances.simplify().map {
                TransferRow(names[it.from] ?: "?", names[it.to] ?: "?", it)
            },
        activity = allEntries.filter { it.matches(filter) },
        categories =
            expenses
                .mapNotNull { it.category?.takeIf(String::isNotBlank) }
                .distinct()
                .sorted(),
        filter = filter,
        settled = balances.net.values.all { it.isZero },
    )
}

/** True if this entry passes every active facet of [filter] (facets combine with AND). */
private fun ActivityEntry.matches(filter: ActivityFilter): Boolean {
    val query = filter.query.trim()
    return when (this) {
        is ActivityEntry.ExpenseEntry -> {
            if (filter.category != null && row.category != filter.category) return false
            if (filter.members.isNotEmpty() && row.participants.none { it in filter.members }) return false
            query.isEmpty() ||
                listOfNotNull(row.title, row.category, row.note).any { it.contains(query, ignoreCase = true) }
        }

        is ActivityEntry.SettlementEntry -> {
            // Settlements have no category, so any category filter excludes them.
            if (filter.category != null) return false
            if (filter.members.isNotEmpty() && row.fromId !in filter.members && row.toId !in filter.members) return false
            query.isEmpty() || row.from.contains(query, ignoreCase = true) || row.to.contains(query, ignoreCase = true)
        }
    }
}
