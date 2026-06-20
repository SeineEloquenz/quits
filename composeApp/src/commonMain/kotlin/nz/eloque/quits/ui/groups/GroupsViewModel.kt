package nz.eloque.quits.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nz.eloque.quits.data.repository.GroupRepository
import nz.eloque.quits.data.repository.GroupSummary
import nz.eloque.quits.data.sync.SyncEngine
import nz.eloque.quits.data.sync.SyncSettings
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.Group
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.error_relay_unreachable
import nz.eloque.quits.resources.groups_join_not_found
import nz.eloque.quits.util.newId
import org.jetbrains.compose.resources.getString

data class GroupsUiState(
    val groups: List<GroupSummary> = emptyList(),
    val loaded: Boolean = false,
)

class GroupsViewModel(
    private val repo: GroupRepository,
    private val engine: SyncEngine,
    private val settings: SyncSettings,
) : ViewModel() {
    val state: StateFlow<GroupsUiState> =
        repo
            .groupsFlow()
            .map { GroupsUiState(it, loaded = true) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupsUiState())

    private val selectedGroupId = MutableStateFlow(settings.activeGroupId?.let { GroupId(it) })

    /**
     * The group the home screen should show: the persisted selection when it still exists,
     * otherwise the first group, or null while loading / when there are none.
     */
    val activeGroup: StateFlow<GroupId?> =
        combine(state, selectedGroupId) { s, active ->
            when {
                !s.loaded -> null
                active != null && s.groups.any { it.id == active } -> active
                else -> s.groups.firstOrNull()?.id
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        // Pull updates for every shared group when the app opens.
        refresh()
    }

    fun setActiveGroup(id: GroupId) {
        settings.activeGroupId = id.value
        selectedGroupId.value = id
    }

    /** Syncs all shared groups; drives the pull-to-refresh indicator. Failures are swallowed. */
    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            try {
                engine.syncAll()
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun createGroup(
        name: String,
        currencyCode: String,
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        val currency = Currency.parse(currencyCode.trim().ifEmpty { "USD" }) ?: return
        viewModelScope.launch {
            val id = GroupId(newId())
            repo.saveGroup(Group(id, trimmedName, currency, members = emptyList()))
            setActiveGroup(id)
        }
    }

    fun join(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            try {
                val id = engine.join(trimmed)
                if (id == null) {
                    _error.value = getString(Res.string.groups_join_not_found, trimmed)
                } else {
                    _error.value = null
                    setActiveGroup(id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = getString(Res.string.error_relay_unreachable, e.message ?: "")
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
