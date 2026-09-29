package nz.eloque.quits.ui.quicksplit

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.Member
import nz.eloque.quits.domain.MemberId
import nz.eloque.quits.domain.QuickSplit
import nz.eloque.quits.ui.entry.EntryFormState
import nz.eloque.quits.ui.entry.EntryFormViewModel
import nz.eloque.quits.ui.entry.EntryValidation
import nz.eloque.quits.ui.entry.MemberInput
import nz.eloque.quits.ui.entry.validate
import nz.eloque.quits.ui.entry.withCurrency
import nz.eloque.quits.ui.entry.withMember
import nz.eloque.quits.ui.entry.withMemberRenamed
import nz.eloque.quits.ui.entry.withoutMember
import nz.eloque.quits.util.newId

/** Splits a single receipt among ad-hoc people. Nothing is persisted. */
class QuickSplitViewModel : EntryFormViewModel() {
    private val _form =
        Currency.of("USD").let { MutableStateFlow(EntryFormState(baseCurrency = it, currency = it)) }
    val form: StateFlow<EntryFormState> = _form.asStateFlow()

    /** The settled split once the form is valid, otherwise null. */
    val result: StateFlow<QuickSplit?> =
        _form.map { it.toQuickSplit() }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    override fun updateForm(transform: (EntryFormState) -> EntryFormState) = _form.update(transform)

    override fun setCurrency(value: Currency) = updateForm { it.withCurrency(value).copy(baseCurrency = value) }

    fun addPerson(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        updateForm { it.withMember(MemberInput(MemberId(newId()), trimmed)) }
    }

    fun renamePerson(
        id: MemberId,
        name: String,
    ) = updateForm { it.withMemberRenamed(id, name) }

    fun removePerson(id: MemberId) = updateForm { it.withoutMember(id) }
}

private fun EntryFormState.toQuickSplit(): QuickSplit? {
    val valid = validate() as? EntryValidation.Valid ?: return null
    return QuickSplit(members.map { Member(it.id, it.name.trim()) }, valid.entry.bill)
}
