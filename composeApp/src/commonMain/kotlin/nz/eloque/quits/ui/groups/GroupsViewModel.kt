package nz.eloque.quits.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
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
import nz.eloque.quits.data.repository.GroupSummary
import nz.eloque.quits.data.sync.SyncEngine
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.Group
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.util.newId

data class GroupsUiState(
    val groups: List<GroupSummary> = emptyList(),
)

class GroupsViewModel(
    private val repo: GroupRepository,
    private val engine: SyncEngine,
) : ViewModel() {
    val state: StateFlow<GroupsUiState> =
        repo
            .groupsFlow()
            .map { GroupsUiState(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupsUiState())

    private val _joined = Channel<GroupId>(Channel.BUFFERED)
    val joined: Flow<GroupId> = _joined.receiveAsFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun createGroup(
        name: String,
        currencyCode: String,
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        val currency = Currency.parse(currencyCode.trim().ifEmpty { "USD" }) ?: return
        viewModelScope.launch {
            val group = Group(GroupId(newId()), trimmedName, currency, members = emptyList())
            repo.saveGroup(group)
        }
    }

    fun join(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                val id = engine.join(trimmed)
                if (id == null) {
                    _error.value = "No group found for code \"$trimmed\""
                } else {
                    _error.value = null
                    _joined.send(id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "Couldn't reach the relay: ${e.message}"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
