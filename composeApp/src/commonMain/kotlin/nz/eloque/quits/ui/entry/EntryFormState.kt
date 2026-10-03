package nz.eloque.quits.ui.entry

import nz.eloque.quits.domain.Bill
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.EntryKind
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.domain.Money
import nz.eloque.quits.domain.Payment
import nz.eloque.quits.domain.Split
import nz.eloque.quits.util.newId

/** EQUAL splits the amount evenly between the selected payers. CUSTOM takes a per-member amount. */
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

/** The editable inputs that describe a [Bill]. */
data class EntryFormState(
    val kind: EntryKind = EntryKind.EXPENSE,
    /** The currency [rate] converts into, or null when the form does no conversion. */
    val baseCurrency: Currency? = null,
    val members: List<MemberInput> = emptyList(),
    /** The entry total. Payments (in either payer mode) must add up to exactly this. */
    val amount: String = "",
    val currency: Currency = Currency.of("EUR"),
    val rate: String = "1.0",
    val fetchingRate: Boolean = false,
    val rateNotice: String? = null,
    val payerMode: PayerMode = PayerMode.EQUAL,
    /** Who paid. Drives [paid] in [PayerMode.EQUAL] and only remembers the selection in [PayerMode.CUSTOM]. */
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
) {
    val isForeign: Boolean get() = baseCurrency != null && currency != baseCurrency
}

fun EntryFormState.withAmount(value: String): EntryFormState {
    val paid = if (payerMode == PayerMode.EQUAL) equalDistribution(value, currency, payerSelected, members) else paid
    return copy(amount = value, paid = paid)
}

fun EntryFormState.withCurrency(value: Currency): EntryFormState {
    val paid = if (payerMode == PayerMode.EQUAL) equalDistribution(amount, value, payerSelected, members) else paid
    return copy(currency = value, paid = paid).withItemizedTotal()
}

/** Toggles a payer in equal mode, re-splitting the amount evenly across those left selected. */
fun EntryFormState.withPayerToggled(member: MemberId): EntryFormState {
    val selected = if (member in payerSelected) payerSelected - member else payerSelected + member
    return copy(payerSelected = selected, paid = equalDistribution(amount, currency, selected, members))
}

fun EntryFormState.withPayerMode(mode: PayerMode): EntryFormState =
    when (mode) {
        PayerMode.CUSTOM -> {
            val seeded = equalDistribution(amount, currency, payerSelected, members).ifEmpty { paid }
            copy(payerMode = PayerMode.CUSTOM, paid = seeded)
        }

        PayerMode.EQUAL -> {
            val selected =
                members
                    .filter { m -> Money.parse(paid[m.id].orEmpty(), currency)?.isPositive == true }
                    .map { it.id }
                    .toSet()
                    .ifEmpty { payerSelected }
            copy(
                payerMode = PayerMode.EQUAL,
                payerSelected = selected,
                paid = equalDistribution(amount, currency, selected, members),
            )
        }
    }

fun EntryFormState.withSplitKind(kind: SplitKind): EntryFormState {
    if (kind == splitKind) return this
    val next = copy(splitKind = kind, splitInput = emptyMap())
    return if (kind == SplitKind.ITEMIZED) next.withItemizedTotal() else next
}

fun EntryFormState.withEqualToggled(member: MemberId): EntryFormState =
    copy(equalSelected = if (member in equalSelected) equalSelected - member else equalSelected + member)

fun EntryFormState.withItemRemoved(id: String): EntryFormState = copy(items = items.filterNot { it.id == id }).withItemizedTotal()

fun EntryFormState.withDraftParticipantToggled(member: MemberId): EntryFormState =
    copy(draftParticipants = if (member in draftParticipants) draftParticipants - member else draftParticipants + member)

/** Select-all / clear toggle for the draft line's participants. */
fun EntryFormState.withAllDraftParticipantsToggled(): EntryFormState {
    val all = members.map { it.id }.toSet()
    return copy(draftParticipants = if (draftParticipants == all) emptySet() else all)
}

/** Commits the draft line to [EntryFormState.items] and resets it for the next one. A no-op if the draft is invalid. */
fun EntryFormState.withDraftSubmitted(): EntryFormState {
    if (!isDraftValid()) return this
    return copy(
        items = items + ItemInput(newId(), draftLabel.trim(), draftAmount.trim(), draftParticipants),
        draftLabel = "",
        draftAmount = "",
        draftParticipants = emptySet(),
    ).withItemizedTotal()
}

/** Adds [member] and includes them in the equal split. */
fun EntryFormState.withMember(member: MemberInput): EntryFormState =
    copy(
        members = members + member,
        equalSelected =
            equalSelected + member.id,
    )

fun EntryFormState.withMemberRenamed(
    id: MemberId,
    name: String,
): EntryFormState = copy(members = members.map { if (it.id == id) it.copy(name = name) else it })

/** Removes [id] from every selection and input, dropping items nobody else shares. */
fun EntryFormState.withoutMember(id: MemberId): EntryFormState {
    val payers = payerSelected - id
    val remaining = members.filterNot { it.id == id }
    return copy(
        members = remaining,
        payerSelected = payers,
        paid = if (payerMode == PayerMode.EQUAL) equalDistribution(amount, currency, payers, remaining) else paid - id,
        equalSelected = equalSelected - id,
        splitInput = splitInput - id,
        items = items.map { it.copy(participants = it.participants - id) }.filter { it.participants.isNotEmpty() },
        draftParticipants = draftParticipants - id,
    ).withItemizedTotal()
}

sealed class EntryValidationError {
    data class InvalidRate(
        val baseCode: String,
    ) : EntryValidationError()

    data object InvalidTotal : EntryValidationError()

    data class InvalidPaid(
        val memberName: String,
    ) : EntryValidationError()

    data object NoPayer : EntryValidationError()

    data class PaidSumMismatch(
        val totalText: String,
    ) : EntryValidationError()

    data object InvalidSplit : EntryValidationError()

    data object NoParticipant : EntryValidationError()

    data class InvalidShare(
        val memberName: String,
    ) : EntryValidationError()

    data object NoShare : EntryValidationError()

    data class InvalidPercent(
        val memberName: String,
    ) : EntryValidationError()

    data object PercentSum : EntryValidationError()

    data class InvalidAmount(
        val memberName: String,
    ) : EntryValidationError()

    data object NoExact : EntryValidationError()

    data object ExactSum : EntryValidationError()

    data object NoItems : EntryValidationError()

    data object ItemsSum : EntryValidationError()

    data object DiscountTooLarge : EntryValidationError()
}

sealed class EntryValidation {
    data class Valid(
        val bill: Bill,
        val rate: Double,
    ) : EntryValidation()

    data class Invalid(
        val reason: EntryValidationError,
    ) : EntryValidation()
}

fun EntryFormState.validate(): EntryValidation {
    val base = baseCurrency
    val rate =
        if (base == null || base == currency) {
            1.0
        } else {
            rate.trim().toDoubleOrNull()?.takeIf { it > 0.0 }
                ?: return EntryValidation.Invalid(EntryValidationError.InvalidRate(base.code))
        }

    val total = Money.parse(amount.trim(), currency)
    if (total == null || !total.isPositive) {
        return EntryValidation.Invalid(EntryValidationError.InvalidTotal)
    }

    val payments = mutableListOf<Payment>()
    for (member in members) {
        val text = paid[member.id].orEmpty().trim()
        if (text.isEmpty()) continue
        val money = Money.parse(text, currency)
        if (money == null || !money.isPositive) {
            return EntryValidation.Invalid(EntryValidationError.InvalidPaid(member.name))
        }
        payments += Payment(member.id, money)
    }
    if (payments.isEmpty()) {
        return EntryValidation.Invalid(EntryValidationError.NoPayer)
    }

    val paidSum = payments.fold(Money.zero(currency)) { acc, p -> acc + p.amount }
    if (paidSum != total) {
        return EntryValidation.Invalid(
            EntryValidationError.PaidSumMismatch("${total.toDecimalString()} ${total.currency.code}"),
        )
    }

    val bill =
        try {
            when (val outcome = buildSplit(total)) {
                is SplitOutcome.Invalid -> return EntryValidation.Invalid(outcome.reason)
                is SplitOutcome.Valid -> Bill(payments, outcome.split)
            }
        } catch (_: IllegalArgumentException) {
            return EntryValidation.Invalid(EntryValidationError.InvalidSplit)
        }

    return EntryValidation.Valid(bill, rate)
}

/** True once [validate] would succeed. */
fun EntryFormState.isValid(): Boolean = validate() is EntryValidation.Valid

/** Parses one editor line into a domain item, or null if it isn't a complete, valid line yet. */
private fun ItemInput.toItem(currency: Currency): Split.Itemized.Item? {
    val money = Money.parse(amount.trim(), currency) ?: return null
    if (money.isZero || participants.isEmpty()) return null
    return Split.Itemized.Item(label.trim(), money, participants)
}

private fun EntryFormState.draftItem(): Split.Itemized.Item? =
    ItemInput("draft", draftLabel, draftAmount, draftParticipants).toItem(currency)

/** True once the draft line can be committed (a valid positive amount and at least one participant). */
fun EntryFormState.isDraftValid(): Boolean = draftItem() != null

private fun EntryFormState.itemizedItems(): List<Split.Itemized.Item> = items.mapNotNull { it.toItem(currency) }

/** For an itemized split, re-derives [EntryFormState.amount] from the item sum and refreshes the equal-payer split. A no-op otherwise. */
private fun EntryFormState.withItemizedTotal(): EntryFormState {
    if (splitKind != SplitKind.ITEMIZED) return this
    val totalMinor = itemizedItems().sumOf { it.amount.minorUnits }
    val amount = if (totalMinor > 0) Money(totalMinor, currency).toDecimalString() else ""
    val newPaid = if (payerMode == PayerMode.EQUAL) equalDistribution(amount, currency, payerSelected, members) else paid
    return copy(amount = amount, paid = newPaid)
}

private sealed class SplitOutcome {
    data class Valid(
        val split: Split,
    ) : SplitOutcome()

    data class Invalid(
        val reason: EntryValidationError,
    ) : SplitOutcome()
}

private fun EntryFormState.buildSplit(total: Money): SplitOutcome =
    when (splitKind) {
        SplitKind.EQUAL -> {
            val participants = members.filter { it.id in equalSelected }.map { it.id }
            if (participants.isEmpty()) {
                SplitOutcome.Invalid(EntryValidationError.NoParticipant)
            } else {
                SplitOutcome.Valid(Split.Equal(participants))
            }
        }

        SplitKind.SHARES -> {
            val map = mutableMapOf<MemberId, Long>()
            for (member in members) {
                val text = splitInput[member.id].orEmpty().trim()
                if (text.isEmpty()) continue
                val weight = text.toLongOrNull()
                if (weight == null || weight < 0) {
                    return SplitOutcome.Invalid(EntryValidationError.InvalidShare(member.name))
                }
                if (weight > 0) map[member.id] = weight
            }
            if (map.isEmpty()) {
                SplitOutcome.Invalid(EntryValidationError.NoShare)
            } else {
                SplitOutcome.Valid(Split.Shares(map))
            }
        }

        SplitKind.PERCENTAGE -> {
            val map = mutableMapOf<MemberId, Int>()
            for (member in members) {
                val text = splitInput[member.id].orEmpty().trim()
                if (text.isEmpty()) continue
                val percent = text.toIntOrNull()
                if (percent == null || percent < 0) {
                    return SplitOutcome.Invalid(EntryValidationError.InvalidPercent(member.name))
                }
                if (percent > 0) map[member.id] = percent
            }
            if (map.values.sum() != 100) {
                SplitOutcome.Invalid(EntryValidationError.PercentSum)
            } else {
                SplitOutcome.Valid(Split.Percentage(map))
            }
        }

        SplitKind.EXACT -> {
            val map = mutableMapOf<MemberId, Money>()
            for (member in members) {
                val text = splitInput[member.id].orEmpty().trim()
                if (text.isEmpty()) continue
                val money = Money.parse(text, currency)
                if (money == null) {
                    return SplitOutcome.Invalid(EntryValidationError.InvalidAmount(member.name))
                }
                map[member.id] = money
            }
            if (map.isEmpty()) {
                SplitOutcome.Invalid(EntryValidationError.NoExact)
            } else if (map.values.fold(Money.zero(currency)) { acc, m -> acc + m } != total) {
                SplitOutcome.Invalid(EntryValidationError.ExactSum)
            } else {
                SplitOutcome.Valid(Split.Exact(map))
            }
        }

        SplitKind.ITEMIZED -> {
            val built = itemizedItems()
            if (built.isEmpty()) {
                SplitOutcome.Invalid(EntryValidationError.NoItems)
            } else if (built.fold(Money.zero(currency)) { acc, i -> acc + i.amount } != total) {
                SplitOutcome.Invalid(EntryValidationError.ItemsSum)
            } else {
                val itemized = Split.Itemized(built)
                if (itemized.divide(total).values.any { it.isNegative }) {
                    SplitOutcome.Invalid(EntryValidationError.DiscountTooLarge)
                } else {
                    SplitOutcome.Valid(itemized)
                }
            }
        }
    }

/** Even split of [amount] across [selected] via [Split.Equal.divide]. Empty if not yet valid. */
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
