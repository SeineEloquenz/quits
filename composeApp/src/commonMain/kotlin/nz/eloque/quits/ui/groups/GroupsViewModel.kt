package nz.eloque.quits.ui.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nz.eloque.quits.data.repository.GroupRepository
import nz.eloque.quits.data.repository.GroupSummary
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.Group
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.util.newId
import nz.eloque.quits.util.newJoinCode

data class GroupsUiState(
    val groups: List<GroupSummary> = emptyList(),
)

class GroupsViewModel(
    private val repo: GroupRepository,
) : ViewModel() {
    val state: StateFlow<GroupsUiState> =
        repo
            .groupsFlow()
            .map { GroupsUiState(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupsUiState())

    fun createGroup(
        name: String,
        currencyCode: String,
    ) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        val currency = Currency.parse(currencyCode.trim().ifEmpty { "USD" }) ?: return
        viewModelScope.launch {
            val group = Group(GroupId(newId()), trimmedName, currency, members = emptyList())
            repo.saveGroup(group, code = newJoinCode())
        }
    }
}
