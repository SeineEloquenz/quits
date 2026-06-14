package nz.eloque.quits.ui.settings

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import nz.eloque.quits.data.sync.SyncSettings

data class SettingsUiState(
    val relayUrl: String = "",
    val saved: Boolean = false,
)

class SettingsViewModel(
    private val settings: SyncSettings,
) : ViewModel() {
    val defaultRelayUrl: String = SyncSettings.DEFAULT_RELAY_URL

    private val _state = MutableStateFlow(SettingsUiState(relayUrl = settings.relayUrl))
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun setRelayUrl(value: String) = _state.update { it.copy(relayUrl = value, saved = false) }

    fun save() {
        val value = _state.value.relayUrl.trim()
        if (value.isEmpty()) return
        settings.relayUrl = value
        _state.update { it.copy(relayUrl = value, saved = true) }
    }

    fun resetToDefault() {
        settings.relayUrl = defaultRelayUrl
        _state.update { it.copy(relayUrl = defaultRelayUrl, saved = true) }
    }
}
