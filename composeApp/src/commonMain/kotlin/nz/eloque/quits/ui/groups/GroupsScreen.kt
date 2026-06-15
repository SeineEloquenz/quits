package nz.eloque.quits.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.components.Section
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.ui.components.CurrencyPicker
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun GroupsScreen(
    onOpenGroup: (GroupId) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel = koinViewModel<GroupsViewModel>()
    val state by viewModel.state.collectAsState()
    val error by viewModel.error.collectAsState()

    var name by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(Currency.of("USD")) }
    var joinCode by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.joined.collect { onOpenGroup(it) }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Quits", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
        Spacer(Modifier.height(16.dp))

        Section(heading = "New group") {
            Column(Modifier.padding(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                CurrencyPicker(
                    label = "Base currency",
                    selected = currency,
                    onSelected = { currency = it },
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.createGroup(name, currency.code)
                        name = ""
                    },
                    enabled = name.isNotBlank(),
                ) {
                    Text("Create")
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Section(heading = "Join group") {
            Column(Modifier.padding(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = joinCode,
                        onValueChange = {
                            joinCode = it.uppercase()
                            if (error != null) viewModel.clearError()
                        },
                        label = { Text("Share code") },
                        singleLine = true,
                        isError = error != null,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = { viewModel.join(joinCode) },
                        enabled = joinCode.isNotBlank(),
                    ) {
                        Text("Join")
                    }
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Section(heading = "Groups") {
            if (state.groups.isEmpty()) {
                Text("No groups yet.", Modifier.padding(16.dp))
            } else {
                Column {
                    state.groups.forEach { group ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenGroup(group.id) }
                                .padding(16.dp),
                        ) {
                            Text(group.name, Modifier.weight(1f))
                            Text(group.baseCurrency.code, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }
        }
    }
}
