package nz.eloque.quits.ui.entry

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
import nz.eloque.quits.data.sync.syncQuietly
import nz.eloque.quits.domain.Category
import nz.eloque.quits.domain.CategoryId
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.Entry
import nz.eloque.quits.domain.EntryId
import nz.eloque.quits.domain.EntryKind
import nz.eloque.quits.domain.Group
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.domain.Split
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.error_discount_too_large
import nz.eloque.quits.resources.error_exact_sum
import nz.eloque.quits.resources.error_invalid_amount
import nz.eloque.quits.resources.error_invalid_paid
import nz.eloque.quits.resources.error_invalid_percent
import nz.eloque.quits.resources.error_invalid_rate
import nz.eloque.quits.resources.error_invalid_share
import nz.eloque.quits.resources.error_invalid_split
import nz.eloque.quits.resources.error_invalid_total
import nz.eloque.quits.resources.error_items_sum
import nz.eloque.quits.resources.error_no_exact
import nz.eloque.quits.resources.error_no_items
import nz.eloque.quits.resources.error_no_participant
import nz.eloque.quits.resources.error_no_payer
import nz.eloque.quits.resources.error_no_share
import nz.eloque.quits.resources.error_paid_sum
import nz.eloque.quits.resources.error_percent_sum
import nz.eloque.quits.resources.rate_cached
import nz.eloque.quits.resources.rate_fetch_failed
import nz.eloque.quits.util.currentOffsetMinutes
import nz.eloque.quits.util.formatLocalDate
import nz.eloque.quits.util.newId
import nz.eloque.quits.util.nowMillis
import org.jetbrains.compose.resources.getString

data class EntryEditorUiState(
    val loaded: Boolean = false,
    val editing: Boolean = false,
    val form: EntryFormState = EntryFormState(),
    val title: String = "",
    val categoryId: CategoryId? = null,
    /** The group's custom categories, for the editor's chips (presets come from the catalog). */
    val categories: List<Category> = emptyList(),
    val note: String = "",
    val error: String? = null,
    /** When the entry was incurred (epoch millis). Defaults to now for a new entry. */
    val spentAt: Long = 0L,
    /** UTC offset the date was entered in. Captured on create, preserved on edit. */
    val tzOffsetMinutes: Int = 0,
) {
    val kind: EntryKind get() = form.kind
}

class EntryEditorViewModel(
    private val repo: GroupRepository,
    private val engine: SyncEngine,
    private val fxRates: FxRates,
    private val groupId: GroupId,
    private val entryId: String?,
    private val kind: EntryKind,
) : EntryFormViewModel() {
    private var rateJob: Job? = null
    private val _state = MutableStateFlow(EntryEditorUiState())
    val state: StateFlow<EntryEditorUiState> = _state.asStateFlow()

    private val _saved = Channel<Unit>(Channel.BUFFERED)
    val saved: Flow<Unit> = _saved.receiveAsFlow()

    init {
        viewModelScope.launch {
            val group = repo.load(groupId) ?: return@launch
            val existing = entryId?.let { id -> group.entries.firstOrNull { it.id.value == id } }
            _state.value = initialState(group, existing)
        }
    }

    override fun updateForm(transform: (EntryFormState) -> EntryFormState) = _state.update { it.copy(form = transform(it.form)) }

    private fun initialState(
        group: Group,
        existing: Entry?,
    ): EntryEditorUiState {
        val members = group.members.map { MemberInput(it.id, it.name) }
        val allIds = members.map { it.id }.toSet()
        if (existing == null) {
            return EntryEditorUiState(
                loaded = true,
                editing = false,
                form =
                    EntryFormState(
                        kind = kind,
                        baseCurrency = group.baseCurrency,
                        members = members,
                        currency = group.baseCurrency,
                        equalSelected = allIds,
                    ),
                categories = group.categories,
                spentAt = nowMillis(),
                tzOffsetMinutes = currentOffsetMinutes(),
            )
        }
        val paidMoney = existing.payments.map { it.member }.distinct().associateWith(existing::paymentsBy)
        val paid = paidMoney.entries.associate { (member, money) -> member to money.toDecimalString() }
        val distinctPayers = paidMoney.keys.toList()
        val isEvenSplit =
            distinctPayers.isNotEmpty() &&
                try {
                    val even = Split.Equal(distinctPayers).divide(existing.total)
                    distinctPayers.all { even[it] == paidMoney[it] }
                } catch (_: IllegalArgumentException) {
                    false
                }
        val split = existing.split
        return EntryEditorUiState(
            loaded = true,
            editing = true,
            form =
                EntryFormState(
                    kind = existing.kind,
                    baseCurrency = group.baseCurrency,
                    members = members,
                    amount = existing.total.toDecimalString(),
                    currency = existing.currency,
                    rate = existing.rateToBase.toString(),
                    payerMode = if (isEvenSplit) PayerMode.EQUAL else PayerMode.CUSTOM,
                    payerSelected = distinctPayers.toSet(),
                    paid = paid,
                    splitKind = split.kind(),
                    equalSelected = if (split is Split.Equal) split.participants.toSet() else allIds,
                    splitInput =
                        when (split) {
                            is Split.Shares -> split.shares.entries.associate { it.key to it.value.toString() }
                            is Split.Percentage -> split.percent.entries.associate { it.key to it.value.toString() }
                            is Split.Exact -> split.amounts.entries.associate { it.key to it.value.toDecimalString() }
                            is Split.Equal -> emptyMap()
                            is Split.Itemized -> emptyMap()
                        },
                    items =
                        if (split is Split.Itemized) {
                            split.items.map { ItemInput(newId(), it.label, it.amount.toDecimalString(), it.participants) }
                        } else {
                            emptyList()
                        },
                ),
            categories = group.categories,
            title = existing.title,
            categoryId = existing.categoryId,
            note = existing.note.orEmpty(),
            spentAt = existing.spentAt,
            tzOffsetMinutes = existing.tzOffsetMinutes,
        )
    }

    fun setTitle(value: String) = _state.update { it.copy(title = value) }

    fun setSpentAt(millis: Long) = _state.update { it.copy(spentAt = millis) }

    fun setCategoryId(value: CategoryId?) = _state.update { it.copy(categoryId = value) }

    /** Creates a custom category, selects it, and persists it (optimistic: state updates immediately). */
    fun createCategory(
        name: String,
        icon: String,
        color: Long,
    ) {
        val category = Category(CategoryId(newId()), name.trim(), icon, color)
        _state.update { it.copy(categories = it.categories + category, categoryId = category.id) }
        viewModelScope.launch {
            repo.upsertCategory(groupId, category)
            engine.syncQuietly(groupId)
        }
    }

    fun updateCategory(
        id: CategoryId,
        name: String,
        icon: String,
        color: Long,
    ) {
        val category = Category(id, name.trim(), icon, color)
        _state.update { s -> s.copy(categories = s.categories.map { if (it.id == id) category else it }) }
        viewModelScope.launch {
            repo.upsertCategory(groupId, category)
            engine.syncQuietly(groupId)
        }
    }

    fun deleteCategory(id: CategoryId) {
        _state.update { s ->
            s.copy(
                categories = s.categories.filterNot { it.id == id },
                categoryId = if (s.categoryId == id) null else s.categoryId,
            )
        }
        viewModelScope.launch {
            repo.deleteCategory(id)
            engine.syncQuietly(groupId)
        }
    }

    fun setNote(value: String) = _state.update { it.copy(note = value) }

    override fun setCurrency(value: Currency) {
        super.setCurrency(value)
        val base = _state.value.form.baseCurrency
        if (base != null && value != base) {
            fetchRate(value, base)
        }
    }

    /** Fetches the live rate from the entry currency into the group base and prefills the field. */
    private fun fetchRate(
        from: Currency,
        to: Currency,
    ) {
        rateJob?.cancel()
        rateJob =
            viewModelScope.launch {
                updateForm { it.copy(fetchingRate = true, rateNotice = null) }
                try {
                    val result = fxRates.fetch(from, to)
                    val notice =
                        when (result) {
                            is RateResult.Live -> null
                            is RateResult.Cached -> getString(Res.string.rate_cached, formatLocalDate(result.asOf))
                        }
                    updateForm { it.copy(rate = result.rate.rate.toString(), fetchingRate = false, rateNotice = notice) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    val notice = getString(Res.string.rate_fetch_failed)
                    updateForm { it.copy(fetchingRate = false, rateNotice = notice) }
                }
            }
    }

    fun save() {
        viewModelScope.launch {
            if (_state.value.form.splitKind == SplitKind.ITEMIZED) submitDraft()
            val s = _state.value
            val validated =
                when (val outcome = s.form.validate()) {
                    is EntryValidation.Invalid -> {
                        setError(outcome.reason.message())
                        return@launch
                    }

                    is EntryValidation.Valid -> {
                        outcome
                    }
                }

            val entry =
                Entry(
                    EntryId(entryId ?: newId()),
                    s.title.trim().ifEmpty { getString(s.kind.fallbackTitleRes()) },
                    validated.bill.payments,
                    validated.bill.split,
                    validated.rate,
                    spentAt = s.spentAt.takeIf { it > 0L } ?: nowMillis(),
                    tzOffsetMinutes = s.tzOffsetMinutes,
                    categoryId = s.categoryId,
                    note = s.note.trim().ifEmpty { null },
                    kind = s.kind,
                )

            repo.upsertEntry(groupId, entry)
            engine.syncQuietly(groupId)
            _state.update { it.copy(error = null) }
            _saved.send(Unit)
        }
    }

    private fun setError(message: String) = _state.update { it.copy(error = message) }
}

suspend fun EntryValidationError.message(): String =
    when (this) {
        is EntryValidationError.InvalidRate -> getString(Res.string.error_invalid_rate, baseCode)
        is EntryValidationError.InvalidTotal -> getString(Res.string.error_invalid_total)
        is EntryValidationError.InvalidPaid -> getString(Res.string.error_invalid_paid, memberName)
        is EntryValidationError.NoPayer -> getString(Res.string.error_no_payer)
        is EntryValidationError.PaidSumMismatch -> getString(Res.string.error_paid_sum, totalText)
        is EntryValidationError.InvalidSplit -> getString(Res.string.error_invalid_split)
        is EntryValidationError.NoParticipant -> getString(Res.string.error_no_participant)
        is EntryValidationError.InvalidShare -> getString(Res.string.error_invalid_share, memberName)
        is EntryValidationError.NoShare -> getString(Res.string.error_no_share)
        is EntryValidationError.InvalidPercent -> getString(Res.string.error_invalid_percent, memberName)
        is EntryValidationError.PercentSum -> getString(Res.string.error_percent_sum)
        is EntryValidationError.InvalidAmount -> getString(Res.string.error_invalid_amount, memberName)
        is EntryValidationError.NoExact -> getString(Res.string.error_no_exact)
        is EntryValidationError.ExactSum -> getString(Res.string.error_exact_sum)
        is EntryValidationError.NoItems -> getString(Res.string.error_no_items)
        is EntryValidationError.ItemsSum -> getString(Res.string.error_items_sum)
        is EntryValidationError.DiscountTooLarge -> getString(Res.string.error_discount_too_large)
    }
