package nz.eloque.quits.ui.group

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nz.eloque.quits.data.repository.GroupRepository
import nz.eloque.quits.data.sync.SyncEngine
import nz.eloque.quits.data.sync.syncQuietly
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.ui.entry.EntryFormState
import nz.eloque.quits.ui.entry.EntryFormViewModel
import nz.eloque.quits.ui.entry.MemberInput
import nz.eloque.quits.ui.entry.toDefaultSplit
import nz.eloque.quits.ui.entry.withSplit

data class DefaultSplitUiState(
    val loaded: Boolean = false,
    val form: EntryFormState = EntryFormState(),
    val hasDefault: Boolean = false,
)

class DefaultSplitViewModel(
    private val repo: GroupRepository,
    private val engine: SyncEngine,
    private val groupId: GroupId,
) : EntryFormViewModel() {
    private val _state = MutableStateFlow(DefaultSplitUiState())
    val state: StateFlow<DefaultSplitUiState> = _state.asStateFlow()

    private val _done = Channel<Unit>(Channel.BUFFERED)
    val done: Flow<Unit> = _done.receiveAsFlow()

    init {
        viewModelScope.launch {
            val group = repo.load(groupId) ?: return@launch
            val members = group.members.map { MemberInput(it.id, it.name) }
            val ids = members.map { it.id }
            val defaultSplit = repo.defaultSplitFlow(groupId).first()
            val form = EntryFormState(members = members, equalSelected = ids.toSet())
            _state.value =
                DefaultSplitUiState(
                    loaded = true,
                    form = if (defaultSplit != null && ids.isNotEmpty()) form.withSplit(defaultSplit.splitFor(ids)) else form,
                    hasDefault = defaultSplit != null,
                )
        }
    }

    override fun updateForm(transform: (EntryFormState) -> EntryFormState) = _state.update { it.copy(form = transform(it.form)) }

    fun save() {
        val defaultSplit = _state.value.form.toDefaultSplit() ?: return
        viewModelScope.launch {
            repo.setDefaultSplit(groupId, defaultSplit)
            _done.send(Unit)
            engine.syncQuietly(groupId)
        }
    }

    fun clear() {
        viewModelScope.launch {
            repo.clearDefaultSplit(groupId)
            _done.send(Unit)
            engine.syncQuietly(groupId)
        }
    }
}
