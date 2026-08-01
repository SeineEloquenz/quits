package nz.eloque.quits.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import nz.eloque.quits.data.sync.syncQuietly
import nz.eloque.quits.domain.Category
import nz.eloque.quits.domain.CategoryId
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.EntryId
import nz.eloque.quits.domain.EntryKind
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.domain.Money
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.error_member_in_use
import org.jetbrains.compose.resources.getString

data class MemberEntryRow(
    val id: EntryId,
    val title: String,
    val total: Money,
    val paidByThem: Money,
    val owedByThem: Money,
    val spentAt: Long,
    val offsetMinutes: Int = 0,
    val categoryId: CategoryId? = null,
    val kind: EntryKind = EntryKind.EXPENSE,
    val note: String? = null,
    /** False for a split type this version doesn't support (created by a newer version). */
    val splitSupported: Boolean = true,
)

data class MemberDetailUiState(
    val loaded: Boolean = false,
    val found: Boolean = true,
    val name: String = "",
    val net: Money = Money.zero(Currency.of("USD")),
    /** Entries the member appears in, newest-first, matching the group feed's order. */
    val entries: List<MemberEntryRow> = emptyList(),
    /** The group's custom categories, for resolving names/icons in the feed. */
    val customCategories: List<Category> = emptyList(),
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
                        group.entries
                            .filter { entry ->
                                memberId in entry.shares.keys || entry.payments.any { it.member == memberId }
                            }.sortedByDescending { it.spentAt }
                    MemberDetailUiState(
                        loaded = true,
                        found = true,
                        name = member.name,
                        net = group.balances().of(memberId),
                        entries =
                            related.map { entry ->
                                MemberEntryRow(
                                    entry.id,
                                    entry.title,
                                    entry.total,
                                    entry.paymentsBy(memberId),
                                    entry.shareOf(memberId),
                                    entry.spentAt,
                                    entry.tzOffsetMinutes,
                                    entry.categoryId,
                                    entry.kind,
                                    entry.note,
                                    entry.splitSupported,
                                )
                            },
                        customCategories = group.categories,
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
            engine.syncQuietly(groupId)
        }
    }

    /** Same referential guard as the old overflow menu: refuses if still tied to an entry/settlement. */
    fun remove() {
        viewModelScope.launch {
            if (repo.removeMember(groupId, memberId)) {
                engine.syncQuietly(groupId)
                _removed.send(Unit)
            } else {
                _error.value = getString(Res.string.error_member_in_use)
            }
        }
    }

    fun dismissError() {
        _error.value = null
    }
}
