package nz.eloque.quits.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nz.eloque.quits.data.repository.GroupRepository
import nz.eloque.quits.data.sync.SyncEngine
import nz.eloque.quits.data.sync.syncQuietly
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.Member
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.domain.Money
import nz.eloque.quits.domain.Settlement
import nz.eloque.quits.domain.SettlementId
import nz.eloque.quits.util.nowMillis

data class SettlementEditorUiState(
    val loaded: Boolean = false,
    /** False once the settlement (or the group) no longer exists, e.g. it was deleted elsewhere. */
    val found: Boolean = true,
    val baseCurrency: Currency = Currency.of("EUR"),
    val members: List<Member> = emptyList(),
    val fromId: MemberId? = null,
    val toId: MemberId? = null,
    val amount: String = "",
    val note: String = "",
    /** When the payment was made (epoch millis), editable via the date/time pickers. */
    val paidAt: Long = 0L,
    /** UTC offset the date was entered in; preserved on edit so the day/time renders consistently. */
    val tzOffsetMinutes: Int = 0,
    /** The currency the settlement was recorded in; not editable here, only displayed. */
    val currency: Currency = Currency.of("EUR"),
) {
    val from: Member? get() = members.firstOrNull { it.id == fromId }
    val to: Member? get() = members.firstOrNull { it.id == toId }

    fun isValid(): Boolean {
        val money = Money.parse(amount.trim(), currency)
        return fromId != null && toId != null && fromId != toId && money != null && money.isPositive
    }
}

class SettlementEditorViewModel(
    private val repo: GroupRepository,
    private val engine: SyncEngine,
    private val groupId: GroupId,
    private val settlementId: SettlementId,
) : ViewModel() {
    private val _state = MutableStateFlow(SettlementEditorUiState())
    val state: StateFlow<SettlementEditorUiState> = _state.asStateFlow()

    private val _saved = Channel<Unit>(Channel.BUFFERED)
    val saved: Flow<Unit> = _saved.receiveAsFlow()

    private val _deleted = Channel<Unit>(Channel.BUFFERED)
    val deleted: Flow<Unit> = _deleted.receiveAsFlow()

    // Preserves the entry-captured rate-to-base and currency for save.
    private var loaded: Settlement? = null

    init {
        viewModelScope.launch {
            val group = repo.load(groupId)
            val existing = group?.settlements?.firstOrNull { it.id == settlementId }
            if (group == null || existing == null) {
                _state.value = SettlementEditorUiState(loaded = true, found = false)
                return@launch
            }
            loaded = existing
            _state.value =
                SettlementEditorUiState(
                    loaded = true,
                    found = true,
                    baseCurrency = group.baseCurrency,
                    members = group.members,
                    fromId = existing.from,
                    toId = existing.to,
                    amount = existing.amount.toDecimalString(),
                    note = existing.note.orEmpty(),
                    paidAt = existing.paidAt,
                    tzOffsetMinutes = existing.tzOffsetMinutes,
                    currency = existing.amount.currency,
                )
        }
    }

    fun setFrom(id: MemberId) = _state.update { it.copy(fromId = id) }

    fun setTo(id: MemberId) = _state.update { it.copy(toId = id) }

    fun setAmount(value: String) = _state.update { it.copy(amount = value) }

    fun setNote(value: String) = _state.update { it.copy(note = value) }

    fun setPaidAt(millis: Long) = _state.update { it.copy(paidAt = millis) }

    fun save() {
        viewModelScope.launch {
            val s = _state.value
            val original = loaded ?: return@launch
            val from = s.fromId ?: return@launch
            val to = s.toId ?: return@launch
            val money = Money.parse(s.amount.trim(), s.currency) ?: return@launch
            val settlement =
                try {
                    Settlement(
                        settlementId,
                        from,
                        to,
                        money,
                        rateToBase = original.rateToBase,
                        paidAt = s.paidAt.takeIf { it > 0L } ?: nowMillis(),
                        tzOffsetMinutes = s.tzOffsetMinutes,
                        note = s.note.trim().ifEmpty { null },
                    )
                } catch (_: IllegalArgumentException) {
                    return@launch
                }
            repo.upsertSettlement(groupId, settlement)
            engine.syncQuietly(groupId)
            _saved.send(Unit)
        }
    }

    /** Deletes immediately — the trash icon here is already the deliberate, confirmed action. */
    fun delete() {
        viewModelScope.launch {
            repo.deleteSettlement(settlementId)
            _deleted.send(Unit)
            engine.syncQuietly(groupId)
        }
    }
}
