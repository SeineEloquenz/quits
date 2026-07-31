package nz.eloque.quits.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import nz.eloque.quits.data.sync.syncQuietly
import nz.eloque.quits.domain.Category
import nz.eloque.quits.domain.CategoryId
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.EntryId
import nz.eloque.quits.domain.EntryKind
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.domain.Money
import nz.eloque.quits.domain.Split

data class EntryParticipantRow(
    val id: MemberId,
    val name: String,
    val amount: Money,
)

/** A receipt line on the detail screen: what it cost and who shared it. */
data class EntryItemRow(
    val label: String,
    val amount: Money,
    val participants: List<String>,
)

data class EntryDetailUiState(
    val loaded: Boolean = false,
    /** False once the entry (or the group) no longer exists, e.g. it was deleted elsewhere. */
    val found: Boolean = true,
    val title: String = "",
    val kind: EntryKind = EntryKind.EXPENSE,
    val categoryId: CategoryId? = null,
    /** The group's custom categories, for resolving the category's name/icon/color. */
    val categories: List<Category> = emptyList(),
    val note: String? = null,
    val total: Money = Money.zero(Currency.of("USD")),
    val splitKind: SplitKind = SplitKind.EQUAL,
    val participantCount: Int = 0,
    val spentAt: Long = 0L,
    val tzOffsetMinutes: Int = 0,
    val paidBy: List<EntryParticipantRow> = emptyList(),
    val owedBy: List<EntryParticipantRow> = emptyList(),
    /** Line items, present only when the split is itemized. */
    val items: List<EntryItemRow> = emptyList(),
    /** False for an entry whose split type this version doesn't support — read-only, no editing. */
    val splitSupported: Boolean = true,
)

class EntryDetailViewModel(
    private val repo: GroupRepository,
    private val engine: SyncEngine,
    private val groupId: GroupId,
    private val entryId: EntryId,
) : ViewModel() {
    val state: StateFlow<EntryDetailUiState> =
        repo
            .groupFlow(groupId)
            .map { group ->
                val entry = group?.entries?.find { it.id == entryId }
                if (group == null || entry == null) {
                    EntryDetailUiState(loaded = true, found = false)
                } else {
                    val names = group.members.associate { it.id to it.name }
                    EntryDetailUiState(
                        loaded = true,
                        found = true,
                        title = entry.title,
                        kind = entry.kind,
                        categoryId = entry.categoryId,
                        categories = group.categories,
                        note = entry.note,
                        total = entry.total,
                        splitKind = entry.split.kind(),
                        participantCount = entry.shares.size,
                        spentAt = entry.spentAt,
                        tzOffsetMinutes = entry.tzOffsetMinutes,
                        paidBy =
                            entry.payments
                                .map { it.member }
                                .distinct()
                                .sortedBy { names[it] ?: "" }
                                .map { EntryParticipantRow(it, names[it] ?: "?", entry.paymentsBy(it)) },
                        owedBy =
                            entry.shares.keys
                                .sortedBy { names[it] ?: "" }
                                .map { EntryParticipantRow(it, names[it] ?: "?", entry.shareOf(it)) },
                        items =
                            (entry.split as? Split.Itemized)?.items?.map { item ->
                                EntryItemRow(
                                    item.label,
                                    item.amount,
                                    item.participants.map { names[it] ?: "?" }.sorted(),
                                )
                            } ?: emptyList(),
                        splitSupported = entry.splitSupported,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EntryDetailUiState())

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
            repo.deleteEntry(entryId)
            _deleted.send(Unit)
            // The deletion is already saved locally; a sync failure shouldn't block leaving the screen.
            engine.syncQuietly(groupId)
        }
    }
}
