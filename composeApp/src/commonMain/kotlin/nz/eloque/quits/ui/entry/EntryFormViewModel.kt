package nz.eloque.quits.ui.entry

import androidx.lifecycle.ViewModel
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.MemberId

/** A view model hosting an [EntryForm]. */
abstract class EntryFormViewModel : ViewModel() {
    protected abstract fun updateForm(transform: (EntryFormState) -> EntryFormState)

    fun setAmount(value: String) = updateForm { it.withAmount(value) }

    open fun setCurrency(value: Currency) = updateForm { it.withCurrency(value) }

    fun setRate(value: String) = updateForm { it.copy(rate = value) }

    fun togglePayer(member: MemberId) = updateForm { it.withPayerToggled(member) }

    fun setPayerMode(mode: PayerMode) = updateForm { it.withPayerMode(mode) }

    fun setPaid(
        member: MemberId,
        value: String,
    ) = updateForm { it.copy(paid = it.paid + (member to value)) }

    fun setKind(kind: SplitKind) = updateForm { it.withSplitKind(kind) }

    fun toggleEqual(member: MemberId) = updateForm { it.withEqualToggled(member) }

    fun setSplitInput(
        member: MemberId,
        value: String,
    ) = updateForm { it.copy(splitInput = it.splitInput + (member to value)) }

    fun removeItem(id: String) = updateForm { it.withItemRemoved(id) }

    fun setDraftLabel(value: String) = updateForm { it.copy(draftLabel = value) }

    fun setDraftAmount(value: String) = updateForm { it.copy(draftAmount = value) }

    fun toggleDraftParticipant(member: MemberId) = updateForm { it.withDraftParticipantToggled(member) }

    fun toggleAllDraftParticipants() = updateForm { it.withAllDraftParticipantsToggled() }

    fun submitDraft() = updateForm { it.withDraftSubmitted() }
}
