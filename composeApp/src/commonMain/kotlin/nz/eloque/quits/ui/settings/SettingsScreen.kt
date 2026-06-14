package nz.eloque.quits.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.components.Section
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val viewModel = koinViewModel<SettingsViewModel>()
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Settings", style = MaterialTheme.typography.headlineMedium)
        }

        Spacer(Modifier.height(8.dp))

        Section(heading = "Sync") {
            Column(Modifier.padding(8.dp)) {
                OutlinedTextField(
                    value = state.relayUrl,
                    onValueChange = viewModel::setRelayUrl,
                    label = { Text("Relay URL") },
                    singleLine = true,
                    isError = state.relayUrl.isBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "The sync server. Default: ${viewModel.defaultRelayUrl} (works from the Android emulator).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = viewModel::save, enabled = state.relayUrl.isNotBlank()) {
                        Text("Save")
                    }
                    TextButton(onClick = viewModel::resetToDefault) { Text("Reset") }
                    if (state.saved) {
                        Text("Saved", color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
