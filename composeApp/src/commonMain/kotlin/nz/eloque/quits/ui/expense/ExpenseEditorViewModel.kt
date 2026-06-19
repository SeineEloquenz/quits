package nz.eloque.quits.ui.expense

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nz.eloque.quits.data.fx.FxRates
import nz.eloque.quits.data.fx.RateResult
import nz.eloque.quits.data.repository.GroupRepository
import nz.eloque.quits.data.sync.SyncEngine
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.Expense
import nz.eloque.quits.domain.ExpenseId
import nz.eloque.quits.domain.Group
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.domain.Money
import nz.eloque.quits.domain.Payment
import nz.eloque.quits.domain.Split
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.editor_expense_fallback_title
import nz.eloque.quits.resources.error_currency_code
import nz.eloque.quits.resources.error_exact_sum
import nz.eloque.quits.resources.error_invalid_amount
import nz.eloque.quits.resources.error_invalid_expense
import nz.eloque.quits.resources.error_invalid_paid
import nz.eloque.quits.resources.error_invalid_percent
import nz.eloque.quits.resources.error_invalid_rate
import nz.eloque.quits.resources.error_invalid_share
import nz.eloque.quits.resources.error_invalid_split
import nz.eloque.quits.resources.error_no_exact
import nz.eloque.quits.resources.error_no_participant
import nz.eloque.quits.resources.error_no_payer
import nz.eloque.quits.resources.error_no_share
import nz.eloque.quits.resources.error_percent_sum
import nz.eloque.quits.resources.rate_cached
import nz.eloque.quits.resources.rate_fetch_failed
import nz.eloque.quits.util.formatDate
import nz.eloque.quits.util.newId
import org.jetbrains.compose.resources.getString

enum class SplitKind { EQUAL, SHARES, PERCENTAGE, EXACT }

data class MemberInput(
    val id: String,
    val name: String,
)

data class ExpenseEditorUiState(
    val loaded: Boolean = false,
    val editing: Boolean = false,
    val baseCurrency: Currency = Currency.of("EUR"),
    val members: List<MemberInput> = emptyList(),
    val title: String = "",
    val currency: String = "EUR",
    val rate: String = "1.0",
    val paid: Map<String, String> = emptyMap(),
    val splitKind: SplitKind = SplitKind.EQUAL,
    val equalSelected: Set<String> = emptySet(),
    val splitInput: Map<String, String> = emptyMap(),
    val error: String? = null,
    val fetchingRate: Boolean = false,
    val rateNotice: String? = null,
) {
    val isForeign: Boolean get() = currency.trim().uppercase() != baseCurrency.code
}

class ExpenseEditorViewModel(
    private val repo: GroupRepository,
    private val engine: SyncEngine,
    private val fxRates: FxRates,
    private val groupId: GroupId,
    private val expenseId: String?,
) : ViewModel() {
    private var rateJob: Job? = null
    private val _state = MutableStateFlow(ExpenseEditorUiState())
    val state: StateFlow<ExpenseEditorUiState> = _state.asStateFlow()

    private val _saved = Channel<Unit>(Channel.BUFFERED)
    val saved: Flow<Unit> = _saved.receiveAsFlow()

    init {
        viewModelScope.launch {
            val group = repo.load(groupId) ?: return@launch
            val existing = expenseId?.let { id -> group.expenses.firstOrNull { it.id.value == id } }
            _state.value = initialState(group, existing)
        }
    }

    private fun initialState(
        group: Group,
        existing: Expense?,
    ): ExpenseEditorUiState {
        val members = group.members.map { MemberInput(it.id.value, it.name) }
        val allIds = members.map { it.id }.toSet()
        if (existing == null) {
            return ExpenseEditorUiState(
                loaded = true,
                editing = false,
                baseCurrency = group.baseCurrency,
                members = members,
                currency = group.baseCurrency.code,
                equalSelected = allIds,
            )
        }
        val paid =
            existing.payments
                .groupBy { it.payer }
                .mapValues { (_, payments) -> payments.fold(Money.zero(existing.currency)) { a, p -> a + p.amount } }
                .entries
                .associate { (member, money) -> member.value to money.toDecimalString() }
        val split = existing.split
        return ExpenseEditorUiState(
            loaded = true,
            editing = true,
            baseCurrency = group.baseCurrency,
            members = members,
            title = existing.title,
            currency = existing.currency.code,
            rate = existing.rateToBase.toString(),
            paid = paid,
            splitKind =
                when (split) {
                    is Split.Equal -> SplitKind.EQUAL
                    is Split.Shares -> SplitKind.SHARES
                    is Split.Percentage -> SplitKind.PERCENTAGE
                    is Split.Exact -> SplitKind.EXACT
                },
            equalSelected = if (split is Split.Equal) split.participants.map { it.value }.toSet() else allIds,
            splitInput =
                when (split) {
                    is Split.Shares -> split.shares.entries.associate { it.key.value to it.value.toString() }
                    is Split.Percentage -> split.percent.entries.associate { it.key.value to it.value.toString() }
                    is Split.Exact -> split.amounts.entries.associate { it.key.value to it.value.toDecimalString() }
                    is Split.Equal -> emptyMap()
                },
        )
    }

    fun setTitle(value: String) = _state.update { it.copy(title = value) }

    fun setCurrency(value: String) {
        val code = value.uppercase()
        _state.update { it.copy(currency = code) }
        val base = _state.value.baseCurrency
        if (Currency.isValidCode(code) && code != base.code) {
            fetchRate(Currency.of(code), base)
        }
    }

    /** Fetches the live rate from the expense currency into the group base and prefills the field. */
    private fun fetchRate(
        from: Currency,
        to: Currency,
    ) {
        rateJob?.cancel()
        rateJob =
            viewModelScope.launch {
                _state.update { it.copy(fetchingRate = true, rateNotice = null) }
                try {
                    val result = fxRates.fetch(from, to)
                    _state.update {
                        it.copy(
                            rate = result.rate.rate.toString(),
                            fetchingRate = false,
                            rateNotice =
                                when (result) {
                                    is RateResult.Live -> null
                                    is RateResult.Cached -> getString(Res.string.rate_cached, formatDate(result.asOf))
                                },
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // No live or cached rate; fall back to manual entry.
                    _state.update {
                        it.copy(fetchingRate = false, rateNotice = getString(Res.string.rate_fetch_failed))
                    }
                }
            }
    }

    fun setRate(value: String) = _state.update { it.copy(rate = value) }

    fun setPaid(
        memberId: String,
        value: String,
    ) = _state.update { it.copy(paid = it.paid + (memberId to value)) }

    fun setKind(kind: SplitKind) = _state.update { it.copy(splitKind = kind) }

    fun toggleEqual(memberId: String) =
        _state.update {
            val next = if (memberId in it.equalSelected) it.equalSelected - memberId else it.equalSelected + memberId
            it.copy(equalSelected = next)
        }

    fun setSplitInput(
        memberId: String,
        value: String,
    ) = _state.update { it.copy(splitInput = it.splitInput + (memberId to value)) }

    fun save() {
        viewModelScope.launch {
            val s = _state.value
            val currency = Currency.parse(s.currency.trim().ifEmpty { s.baseCurrency.code })
            if (currency == null) {
                setError(getString(Res.string.error_currency_code))
                return@launch
            }
            val rate = if (currency == s.baseCurrency) 1.0 else s.rate.trim().toDoubleOrNull()
            if (rate == null || rate <= 0.0) {
                setError(getString(Res.string.error_invalid_rate, s.baseCurrency.code))
                return@launch
            }

            val payments = mutableListOf<Payment>()
            for (member in s.members) {
                val text = s.paid[member.id].orEmpty().trim()
                if (text.isEmpty()) continue
                val money = Money.parse(text, currency)
                if (money == null || !money.isPositive) {
                    setError(getString(Res.string.error_invalid_paid, member.name))
                    return@launch
                }
                payments += Payment(MemberId(member.id), money)
            }
            if (payments.isEmpty()) {
                setError(getString(Res.string.error_no_payer))
                return@launch
            }

            val total = payments.fold(Money.zero(currency)) { acc, p -> acc + p.amount }
            val split =
                try {
                    buildSplit(s, currency, total) ?: return@launch
                } catch (e: IllegalArgumentException) {
                    setError(getString(Res.string.error_invalid_split))
                    return@launch
                }

            val expense =
                try {
                    Expense(
                        ExpenseId(expenseId ?: newId()),
                        s.title.trim().ifEmpty { getString(Res.string.editor_expense_fallback_title) },
                        payments,
                        split,
                        rate,
                    )
                } catch (e: IllegalArgumentException) {
                    setError(getString(Res.string.error_invalid_expense))
                    return@launch
                }

            repo.upsertExpense(groupId, expense)
            // The expense is saved locally; a sync failure shouldn't block leaving the screen.
            try {
                engine.sync(groupId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Swallowed: it will sync on the next open/refresh.
            }
            _state.update { it.copy(error = null) }
            _saved.send(Unit)
        }
    }

    private suspend fun buildSplit(
        s: ExpenseEditorUiState,
        currency: Currency,
        total: Money,
    ): Split? =
        when (s.splitKind) {
            SplitKind.EQUAL -> {
                val participants = s.members.filter { it.id in s.equalSelected }.map { MemberId(it.id) }
                if (participants.isEmpty()) {
                    setError(getString(Res.string.error_no_participant))
                    null
                } else {
                    Split.Equal(participants)
                }
            }
            SplitKind.SHARES -> {
                val map = mutableMapOf<MemberId, Long>()
                for (member in s.members) {
                    val text = s.splitInput[member.id].orEmpty().trim()
                    if (text.isEmpty()) continue
                    val weight = text.toLongOrNull()
                    if (weight == null || weight < 0) {
                        setError(getString(Res.string.error_invalid_share, member.name))
                        return null
                    }
                    if (weight > 0) map[MemberId(member.id)] = weight
                }
                if (map.isEmpty()) {
                    setError(getString(Res.string.error_no_share))
                    null
                } else {
                    Split.Shares(map)
                }
            }
            SplitKind.PERCENTAGE -> {
                val map = mutableMapOf<MemberId, Int>()
                for (member in s.members) {
                    val text = s.splitInput[member.id].orEmpty().trim()
                    if (text.isEmpty()) continue
                    val percent = text.toIntOrNull()
                    if (percent == null || percent < 0) {
                        setError(getString(Res.string.error_invalid_percent, member.name))
                        return null
                    }
                    if (percent > 0) map[MemberId(member.id)] = percent
                }
                if (map.values.sum() != 100) {
                    setError(getString(Res.string.error_percent_sum))
                    null
                } else {
                    Split.Percentage(map)
                }
            }
            SplitKind.EXACT -> {
                val map = mutableMapOf<MemberId, Money>()
                for (member in s.members) {
                    val text = s.splitInput[member.id].orEmpty().trim()
                    if (text.isEmpty()) continue
                    val money = Money.parse(text, currency)
                    if (money == null) {
                        setError(getString(Res.string.error_invalid_amount, member.name))
                        return null
                    }
                    map[MemberId(member.id)] = money
                }
                if (map.isEmpty()) {
                    setError(getString(Res.string.error_no_exact))
                    null
                } else if (map.values.fold(Money.zero(currency)) { acc, m -> acc + m } != total) {
                    setError(getString(Res.string.error_exact_sum))
                    null
                } else {
                    Split.Exact(map)
                }
            }
        }

    private fun setError(message: String) = _state.update { it.copy(error = message) }
}
