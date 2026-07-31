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
import nz.eloque.quits.data.sync.syncQuietly
import nz.eloque.quits.domain.Category
import nz.eloque.quits.domain.CategoryId
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.EntryKind
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
import nz.eloque.quits.resources.error_discount_too_large
import nz.eloque.quits.resources.error_exact_sum
import nz.eloque.quits.resources.error_invalid_amount
import nz.eloque.quits.resources.error_invalid_expense
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

/**
 * EQUAL: the common case — tap avatars to pick who paid, and [ExpenseEditorUiState.amount] is
 * split evenly between them (same avatar-chip pattern as the split section's Equal mode). CUSTOM:
 * a per-member amount table, only needed when the payment wasn't actually split evenly between
 * the selected payers.
 */
enum class PayerMode { EQUAL, CUSTOM }

data class MemberInput(
    val id: MemberId,
    val name: String,
)

/** One editable receipt line in the itemized split. [id] is a stable key for the list/UI only. */
data class ItemInput(
    val id: String,
    val label: String = "",
    val amount: String = "",
    val participants: Set<MemberId> = emptySet(),
)

data class ExpenseEditorUiState(
    val loaded: Boolean = false,
    val editing: Boolean = false,
    /** Whether this editor is creating/editing an expense or an income event. */
    val kind: EntryKind = EntryKind.EXPENSE,
    val baseCurrency: Currency = Currency.of("EUR"),
    val members: List<MemberInput> = emptyList(),
    val title: String = "",
    val categoryId: CategoryId? = null,
    /** The group's custom categories, for the editor's chips (presets come from the catalog). */
    val categories: List<Category> = emptyList(),
    val note: String = "",
    /** The expense total. Payments (in either payer mode) must add up to exactly this. */
    val amount: String = "",
    val currency: Currency = Currency.of("EUR"),
    val rate: String = "1.0",
    val payerMode: PayerMode = PayerMode.EQUAL,
    /** Who paid. In [PayerMode.EQUAL] this drives [paid] directly (split evenly); in
     * [PayerMode.CUSTOM] it's just which rows were selected before switching to custom amounts. */
    val payerSelected: Set<MemberId> = emptySet(),
    val paid: Map<MemberId, String> = emptyMap(),
    val splitKind: SplitKind = SplitKind.EQUAL,
    val equalSelected: Set<MemberId> = emptySet(),
    val splitInput: Map<MemberId, String> = emptyMap(),
    /** Committed (read-only) line items, used only when [splitKind] is [SplitKind.ITEMIZED]. */
    val items: List<ItemInput> = emptyList(),
    /** The in-progress line: filled in, then committed to [items] via submit. */
    val draftLabel: String = "",
    val draftAmount: String = "",
    val draftParticipants: Set<MemberId> = emptySet(),
    val error: String? = null,
    val fetchingRate: Boolean = false,
    val rateNotice: String? = null,
    /** When the expense was incurred (epoch millis); defaults to now for a new expense, editable via the date picker. */
    val spentAt: Long = 0L,
    /** UTC offset the date was entered in; captured on create, preserved on edit. */
    val tzOffsetMinutes: Int = 0,
) {
    val isForeign: Boolean get() = currency != baseCurrency
}

class ExpenseEditorViewModel(
    private val repo: GroupRepository,
    private val engine: SyncEngine,
    private val fxRates: FxRates,
    private val groupId: GroupId,
    private val expenseId: String?,
    private val kind: EntryKind,
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
        val members = group.members.map { MemberInput(it.id, it.name) }
        val allIds = members.map { it.id }.toSet()
        if (existing == null) {
            // No default payer — the user taps who paid (there's no "me" to assume). The split, by
            // contrast, defaults to everyone, since an even split across the whole group is the norm.
            return ExpenseEditorUiState(
                loaded = true,
                editing = false,
                kind = kind,
                baseCurrency = group.baseCurrency,
                members = members,
                categories = group.categories,
                currency = group.baseCurrency,
                payerMode = PayerMode.EQUAL,
                payerSelected = emptySet(),
                equalSelected = allIds,
                draftParticipants = emptySet(),
                spentAt = nowMillis(),
                tzOffsetMinutes = currentOffsetMinutes(),
            )
        }
        val paidMoney =
            existing.payments
                .groupBy { it.payer }
                .mapValues { (_, payments) -> payments.fold(Money.zero(existing.currency)) { a, p -> a + p.amount } }
        val paid = paidMoney.entries.associate { (member, money) -> member to money.toDecimalString() }
        val distinctPayers = paidMoney.keys.toList()
        // Was this actually an even split between the payers, or a deliberately uneven one?
        // Compare against what Split.Equal would produce for the same payers/total — the same
        // exact largest-remainder distribution used everywhere else, not an approximation.
        val isEvenSplit =
            distinctPayers.isNotEmpty() &&
                try {
                    val even = Split.Equal(distinctPayers).divide(existing.total)
                    distinctPayers.all { even[it] == paidMoney[it] }
                } catch (_: IllegalArgumentException) {
                    false
                }
        val split = existing.split
        return ExpenseEditorUiState(
            loaded = true,
            editing = true,
            kind = existing.kind,
            baseCurrency = group.baseCurrency,
            members = members,
            categories = group.categories,
            title = existing.title,
            categoryId = existing.categoryId,
            note = existing.note.orEmpty(),
            amount = existing.total.toDecimalString(),
            currency = existing.currency,
            rate = existing.rateToBase.toString(),
            // Don't collapse a deliberately uneven multi-payer expense into the equal-split chips
            // on open — that would silently hide the real amounts until re-expanded.
            payerMode = if (isEvenSplit) PayerMode.EQUAL else PayerMode.CUSTOM,
            payerSelected = distinctPayers.toSet(),
            paid = paid,
            spentAt = existing.spentAt,
            tzOffsetMinutes = existing.tzOffsetMinutes,
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
            draftParticipants = emptySet(),
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

    fun setAmount(value: String) =
        _state.update { s ->
            // In equal mode, `paid` IS the even split of the field — keep it in lockstep so
            // save() never has to special-case "equal" separately from "custom".
            val paid = if (s.payerMode == PayerMode.EQUAL) equalDistribution(value, s.currency, s.payerSelected, s.members) else s.paid
            s.copy(amount = value, paid = paid)
        }

    fun setCurrency(value: Currency) {
        _state.update { s ->
            val paid =
                if (s.payerMode == PayerMode.EQUAL) {
                    equalDistribution(s.amount, value, s.payerSelected, s.members)
                } else {
                    s.paid
                }
            // Re-derive the itemized total in the new currency (a no-op for other split kinds).
            s.copy(currency = value, paid = paid).withItemizedTotal()
        }
        val base = _state.value.baseCurrency
        if (value != base) {
            fetchRate(value, base)
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
                                    is RateResult.Cached -> getString(Res.string.rate_cached, formatLocalDate(result.asOf))
                                },
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // No live or cached rate; fall back to manual entry.
                    _state.update {
                        it.copy(fetchingRate = false, rateNotice = getString(Res.string.rate_fetch_failed))
                    }
                }
            }
    }

    fun setRate(value: String) = _state.update { it.copy(rate = value) }

    /**
     * Equal mode: toggling an avatar adds/removes them from the payer set, and the amount is
     * re-split evenly across whoever's left selected — same avatar-chip pattern as the split
     * section's Equal mode, just applied to "who paid" instead of "who owes".
     */
    fun togglePayer(memberId: MemberId) =
        _state.update { s ->
            val selected = if (memberId in s.payerSelected) s.payerSelected - memberId else s.payerSelected + memberId
            s.copy(payerSelected = selected, paid = equalDistribution(s.amount, s.currency, selected, s.members))
        }

    fun setPayerMode(mode: PayerMode) =
        _state.update { s ->
            when (mode) {
                PayerMode.CUSTOM -> {
                    // Seed the table with the current even split — only needed at all when the
                    // payment wasn't actually even, so this is always a sensible starting point.
                    val seeded = equalDistribution(s.amount, s.currency, s.payerSelected, s.members).ifEmpty { s.paid }
                    s.copy(payerMode = PayerMode.CUSTOM, paid = seeded)
                }

                PayerMode.EQUAL -> {
                    // Collapse back to the chips: keep whoever currently has a positive amount in
                    // the table selected, and re-split evenly between them.
                    val selected =
                        s.members
                            .filter { m -> Money.parse(s.paid[m.id].orEmpty(), s.currency)?.isPositive == true }
                            .map { it.id }
                            .toSet()
                            .ifEmpty { s.payerSelected }
                    s.copy(
                        payerMode = PayerMode.EQUAL,
                        payerSelected = selected,
                        paid = equalDistribution(s.amount, s.currency, selected, s.members),
                    )
                }
            }
        }

    /** Split mode only: editing one member's amount directly in the table. */
    fun setPaid(
        memberId: MemberId,
        value: String,
    ) = _state.update { it.copy(paid = it.paid + (memberId to value)) }

    fun setKind(kind: SplitKind) =
        _state.update { s ->
            val next = s.copy(splitKind = kind)
            // The itemized total is the sum of its lines, not a separately typed figure.
            if (kind == SplitKind.ITEMIZED) next.withItemizedTotal() else next
        }

    fun removeItem(id: String) = _state.update { s -> s.copy(items = s.items.filterNot { it.id == id }).withItemizedTotal() }

    fun setDraftLabel(value: String) = _state.update { it.copy(draftLabel = value) }

    fun setDraftAmount(value: String) = _state.update { it.copy(draftAmount = value) }

    fun toggleDraftParticipant(member: MemberId) =
        _state.update { s ->
            val next = if (member in s.draftParticipants) s.draftParticipants - member else s.draftParticipants + member
            s.copy(draftParticipants = next)
        }

    /** Select-all / clear toggle for the draft line's participants. */
    fun toggleAllDraftParticipants() =
        _state.update { s ->
            val all = s.members.map { it.id }.toSet()
            s.copy(draftParticipants = if (s.draftParticipants == all) emptySet() else all)
        }

    /** Commits the draft line to [items] and resets it for the next one; a no-op if the draft is invalid. */
    fun submitDraft() =
        _state.update { s ->
            if (!s.isDraftValid()) {
                s
            } else {
                // A negative amount (typed directly) makes this a discount line.
                s.copy(
                    items = s.items + ItemInput(newId(), s.draftLabel.trim(), s.draftAmount.trim(), s.draftParticipants),
                    draftLabel = "",
                    draftAmount = "",
                    draftParticipants = emptySet(),
                ).withItemizedTotal()
            }
        }

    fun toggleEqual(memberId: MemberId) =
        _state.update {
            val next = if (memberId in it.equalSelected) it.equalSelected - memberId else it.equalSelected + memberId
            it.copy(equalSelected = next)
        }

    fun setSplitInput(
        memberId: MemberId,
        value: String,
    ) = _state.update { it.copy(splitInput = it.splitInput + (memberId to value)) }

    fun save() {
        viewModelScope.launch {
            // Fold a filled-but-unsubmitted itemized line into the list so it isn't silently lost.
            if (_state.value.splitKind == SplitKind.ITEMIZED && _state.value.isDraftValid()) submitDraft()
            val s = _state.value
            val validated =
                when (val outcome = s.validate()) {
                    is ExpenseValidation.Invalid -> {
                        setError(errorMessage(outcome.reason))
                        return@launch
                    }

                    is ExpenseValidation.Valid -> {
                        outcome.expense
                    }
                }

            val expense =
                try {
                    Expense(
                        ExpenseId(expenseId ?: newId()),
                        s.title.trim().ifEmpty { getString(Res.string.editor_expense_fallback_title) },
                        validated.payments,
                        validated.split,
                        validated.rate,
                        spentAt = s.spentAt.takeIf { it > 0L } ?: nowMillis(),
                        tzOffsetMinutes = s.tzOffsetMinutes,
                        categoryId = s.categoryId,
                        note = s.note.trim().ifEmpty { null },
                        kind = s.kind,
                    )
                } catch (_: IllegalArgumentException) {
                    setError(getString(Res.string.error_invalid_expense))
                    return@launch
                }

            repo.upsertExpense(groupId, expense)
            // The expense is saved locally; a sync failure shouldn't block leaving the screen.
            engine.syncQuietly(groupId)
            _state.update { it.copy(error = null) }
            _saved.send(Unit)
        }
    }

    private suspend fun errorMessage(reason: ExpenseValidationError): String =
        when (reason) {
            is ExpenseValidationError.InvalidRate -> getString(Res.string.error_invalid_rate, reason.baseCode)
            is ExpenseValidationError.InvalidTotal -> getString(Res.string.error_invalid_total)
            is ExpenseValidationError.InvalidPaid -> getString(Res.string.error_invalid_paid, reason.memberName)
            is ExpenseValidationError.NoPayer -> getString(Res.string.error_no_payer)
            is ExpenseValidationError.PaidSumMismatch -> getString(Res.string.error_paid_sum, reason.totalText)
            is ExpenseValidationError.InvalidSplit -> getString(Res.string.error_invalid_split)
            is ExpenseValidationError.NoParticipant -> getString(Res.string.error_no_participant)
            is ExpenseValidationError.InvalidShare -> getString(Res.string.error_invalid_share, reason.memberName)
            is ExpenseValidationError.NoShare -> getString(Res.string.error_no_share)
            is ExpenseValidationError.InvalidPercent -> getString(Res.string.error_invalid_percent, reason.memberName)
            is ExpenseValidationError.PercentSum -> getString(Res.string.error_percent_sum)
            is ExpenseValidationError.InvalidAmount -> getString(Res.string.error_invalid_amount, reason.memberName)
            is ExpenseValidationError.NoExact -> getString(Res.string.error_no_exact)
            is ExpenseValidationError.ExactSum -> getString(Res.string.error_exact_sum)
            is ExpenseValidationError.NoItems -> getString(Res.string.error_no_items)
            is ExpenseValidationError.ItemsSum -> getString(Res.string.error_items_sum)
            is ExpenseValidationError.DiscountTooLarge -> getString(Res.string.error_discount_too_large)
        }

    private fun setError(message: String) = _state.update { it.copy(error = message) }
}

sealed class ExpenseValidationError {
    data class InvalidRate(
        val baseCode: String,
    ) : ExpenseValidationError()

    data object InvalidTotal : ExpenseValidationError()

    data class InvalidPaid(
        val memberName: String,
    ) : ExpenseValidationError()

    data object NoPayer : ExpenseValidationError()

    data class PaidSumMismatch(
        val totalText: String,
    ) : ExpenseValidationError()

    data object InvalidSplit : ExpenseValidationError()

    data object NoParticipant : ExpenseValidationError()

    data class InvalidShare(
        val memberName: String,
    ) : ExpenseValidationError()

    data object NoShare : ExpenseValidationError()

    data class InvalidPercent(
        val memberName: String,
    ) : ExpenseValidationError()

    data object PercentSum : ExpenseValidationError()

    data class InvalidAmount(
        val memberName: String,
    ) : ExpenseValidationError()

    data object NoExact : ExpenseValidationError()

    data object ExactSum : ExpenseValidationError()

    data object NoItems : ExpenseValidationError()

    data object ItemsSum : ExpenseValidationError()

    data object DiscountTooLarge : ExpenseValidationError()
}

/** Everything [ExpenseEditorViewModel.save] needs to build the domain [Expense] once fields check out. */
data class ValidatedExpense(
    val payments: List<Payment>,
    val split: Split,
    val rate: Double,
)

sealed class ExpenseValidation {
    data class Valid(
        val expense: ValidatedExpense,
    ) : ExpenseValidation()

    data class Invalid(
        val reason: ExpenseValidationError,
    ) : ExpenseValidation()
}

fun ExpenseEditorUiState.validate(): ExpenseValidation {
    val currency = this.currency
    val rate = if (currency == baseCurrency) 1.0 else this.rate.trim().toDoubleOrNull()
    if (rate == null || rate <= 0.0) {
        return ExpenseValidation.Invalid(ExpenseValidationError.InvalidRate(baseCurrency.code))
    }

    val total = Money.parse(amount.trim(), currency)
    if (total == null || !total.isPositive) {
        return ExpenseValidation.Invalid(ExpenseValidationError.InvalidTotal)
    }

    val payments = mutableListOf<Payment>()
    for (member in members) {
        val text = paid[member.id].orEmpty().trim()
        if (text.isEmpty()) continue
        val money = Money.parse(text, currency)
        if (money == null || !money.isPositive) {
            return ExpenseValidation.Invalid(ExpenseValidationError.InvalidPaid(member.name))
        }
        payments += Payment(member.id, money)
    }
    if (payments.isEmpty()) {
        return ExpenseValidation.Invalid(ExpenseValidationError.NoPayer)
    }

    val paidSum = payments.fold(Money.zero(currency)) { acc, p -> acc + p.amount }
    if (paidSum != total) {
        return ExpenseValidation.Invalid(
            ExpenseValidationError.PaidSumMismatch("${total.toDecimalString()} ${total.currency.code}"),
        )
    }

    val split =
        try {
            when (val outcome = buildSplit(this, currency, total)) {
                is SplitOutcome.Invalid -> return ExpenseValidation.Invalid(outcome.reason)
                is SplitOutcome.Valid -> outcome.split
            }
        } catch (_: IllegalArgumentException) {
            return ExpenseValidation.Invalid(ExpenseValidationError.InvalidSplit)
        }

    return ExpenseValidation.Valid(ValidatedExpense(payments, split, rate))
}

/** True once [ExpenseEditorUiState.validate] would succeed — drives the save button's enabled state. */
fun ExpenseEditorUiState.isValid(): Boolean = validate() is ExpenseValidation.Valid

/** Parses one editor line into a domain item, or null if it isn't a complete, valid line yet. */
private fun ItemInput.toItem(currency: Currency): Split.Itemized.Item? {
    val money = Money.parse(amount.trim(), currency) ?: return null
    if (money.isZero || participants.isEmpty()) return null
    return Split.Itemized.Item(label.trim(), money, participants)
}

/** The in-progress draft as a domain item, or null if it isn't complete enough to commit yet. */
private fun ExpenseEditorUiState.draftItem(): Split.Itemized.Item? =
    ItemInput("draft", draftLabel, draftAmount, draftParticipants).toItem(currency)

/** True once the draft line can be committed (a valid positive amount and at least one participant). */
fun ExpenseEditorUiState.isDraftValid(): Boolean = draftItem() != null

/** The committed line items an itemized split is built from. */
private fun ExpenseEditorUiState.itemizedItems(): List<Split.Itemized.Item> = items.mapNotNull { it.toItem(currency) }

/**
 * Re-derives the expense total ([amount]) from the sum of the committed items — for an itemized
 * split the lines *are* the total, so it's never typed separately — and refreshes the equal-payer
 * split to match. A no-op unless the split is itemized.
 */
private fun ExpenseEditorUiState.withItemizedTotal(): ExpenseEditorUiState {
    if (splitKind != SplitKind.ITEMIZED) return this
    val totalMinor = itemizedItems().sumOf { it.amount.minorUnits }
    val amount = if (totalMinor > 0) Money(totalMinor, currency).toDecimalString() else ""
    val newPaid = if (payerMode == PayerMode.EQUAL) equalDistribution(amount, currency, payerSelected, members) else paid
    return copy(amount = amount, paid = newPaid)
}

/** Result of [buildSplit]: either the [Split] to save, or why the split section isn't valid yet. */
private sealed class SplitOutcome {
    data class Valid(
        val split: Split,
    ) : SplitOutcome()

    data class Invalid(
        val reason: ExpenseValidationError,
    ) : SplitOutcome()
}

private fun buildSplit(
    s: ExpenseEditorUiState,
    currency: Currency,
    total: Money,
): SplitOutcome =
    when (s.splitKind) {
        SplitKind.EQUAL -> {
            val participants = s.members.filter { it.id in s.equalSelected }.map { it.id }
            if (participants.isEmpty()) {
                SplitOutcome.Invalid(ExpenseValidationError.NoParticipant)
            } else {
                SplitOutcome.Valid(Split.Equal(participants))
            }
        }

        SplitKind.SHARES -> {
            val map = mutableMapOf<MemberId, Long>()
            for (member in s.members) {
                val text = s.splitInput[member.id].orEmpty().trim()
                if (text.isEmpty()) continue
                val weight = text.toLongOrNull()
                if (weight == null || weight < 0) {
                    return SplitOutcome.Invalid(ExpenseValidationError.InvalidShare(member.name))
                }
                if (weight > 0) map[member.id] = weight
            }
            if (map.isEmpty()) {
                SplitOutcome.Invalid(ExpenseValidationError.NoShare)
            } else {
                SplitOutcome.Valid(Split.Shares(map))
            }
        }

        SplitKind.PERCENTAGE -> {
            val map = mutableMapOf<MemberId, Int>()
            for (member in s.members) {
                val text = s.splitInput[member.id].orEmpty().trim()
                if (text.isEmpty()) continue
                val percent = text.toIntOrNull()
                if (percent == null || percent < 0) {
                    return SplitOutcome.Invalid(ExpenseValidationError.InvalidPercent(member.name))
                }
                if (percent > 0) map[member.id] = percent
            }
            if (map.values.sum() != 100) {
                SplitOutcome.Invalid(ExpenseValidationError.PercentSum)
            } else {
                SplitOutcome.Valid(Split.Percentage(map))
            }
        }

        SplitKind.EXACT -> {
            val map = mutableMapOf<MemberId, Money>()
            for (member in s.members) {
                val text = s.splitInput[member.id].orEmpty().trim()
                if (text.isEmpty()) continue
                val money = Money.parse(text, currency)
                if (money == null) {
                    return SplitOutcome.Invalid(ExpenseValidationError.InvalidAmount(member.name))
                }
                map[member.id] = money
            }
            if (map.isEmpty()) {
                SplitOutcome.Invalid(ExpenseValidationError.NoExact)
            } else if (map.values.fold(Money.zero(currency)) { acc, m -> acc + m } != total) {
                SplitOutcome.Invalid(ExpenseValidationError.ExactSum)
            } else {
                SplitOutcome.Valid(Split.Exact(map))
            }
        }

        SplitKind.ITEMIZED -> {
            // Committed lines are already valid (submit enforces it) and [amount] is their sum, so
            // this only fails when there are no lines at all.
            val built = s.itemizedItems()
            if (built.isEmpty()) {
                SplitOutcome.Invalid(ExpenseValidationError.NoItems)
            } else if (built.fold(Money.zero(currency)) { acc, i -> acc + i.amount } != total) {
                SplitOutcome.Invalid(ExpenseValidationError.ItemsSum)
            } else {
                val itemized = Split.Itemized(built)
                if (itemized.divide(total).values.any { it.isNegative }) {
                    SplitOutcome.Invalid(ExpenseValidationError.DiscountTooLarge)
                } else {
                    SplitOutcome.Valid(itemized)
                }
            }
        }
    }

/**
 * The even split of [amount] across [selected], via the real [Split.Equal.divide] — the same
 * exact largest-remainder distribution the domain uses when the expense is actually saved, not
 * an approximation. Empty if the amount or selection isn't valid yet (still typing).
 */
private fun equalDistribution(
    amount: String,
    currency: Currency,
    selected: Set<MemberId>,
    members: List<MemberInput>,
): Map<MemberId, String> {
    if (selected.isEmpty()) return emptyMap()
    val total = Money.parse(amount.trim(), currency) ?: return emptyMap()
    if (!total.isPositive) return emptyMap()
    val ids = members.filter { it.id in selected }.map { it.id }
    if (ids.isEmpty()) return emptyMap()
    return try {
        Split.Equal(ids).divide(total).entries.associate { it.key to it.value.toDecimalString() }
    } catch (_: IllegalArgumentException) {
        emptyMap()
    }
}
