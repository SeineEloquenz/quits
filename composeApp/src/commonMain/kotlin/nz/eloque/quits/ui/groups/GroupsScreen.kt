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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun GroupsScreen(onOpenGroup: (GroupId) -> Unit) {
    val viewModel = koinViewModel<GroupsViewModel>()
    val state by viewModel.state.collectAsState()

    var name by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("USD") }
    val currencyValid = Currency.isValidCode(currency)

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Quits", style = MaterialTheme.typography.headlineLarge)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it.uppercase() },
                        label = { Text("Base currency") },
                        singleLine = true,
                        isError = !currencyValid,
                        supportingText = if (!currencyValid) ({ Text("3-letter code") }) else null,
                        modifier = Modifier.width(160.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Button(
                        onClick = {
                            viewModel.createGroup(name, currency)
                            name = ""
                        },
                        enabled = name.isNotBlank() && currencyValid,
                    ) {
                        Text("Create")
                    }
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
