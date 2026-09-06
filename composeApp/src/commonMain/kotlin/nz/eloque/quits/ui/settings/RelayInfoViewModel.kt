package nz.eloque.quits.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nz.eloque.quits.data.sync.Relay
import nz.eloque.quits.data.sync.RelayInfo
import nz.eloque.quits.data.sync.SyncSettings

data class RelayInfoUiState(
    val relayUrl: String = "",
    val info: RelayInfo? = null,
    val loading: Boolean = true,
)

class RelayInfoViewModel(
    settings: SyncSettings,
    private val relay: Relay,
) : ViewModel() {
    private val _state = MutableStateFlow(RelayInfoUiState(relayUrl = settings.relayUrl))
    val state: StateFlow<RelayInfoUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /**
     * Reads what the relay publishes.
     *
     * The client caches a published answer, so this reflects what a sync would size itself against
     * rather than forcing a round trip. [RelayInfo.fromRelay] separates a real answer from the
     * assumption that stands in for one.
     */
    fun refresh() {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val info = relay.info()
            _state.update { it.copy(info = info, loading = false) }
        }
    }
}
