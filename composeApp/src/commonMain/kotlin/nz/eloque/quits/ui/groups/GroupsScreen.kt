package nz.eloque.quits.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.components.Section
import nz.eloque.compose_kit.input.SubmittableTextField
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun GroupsScreen(onOpenGroup: (String) -> Unit) {
    val viewModel = koinViewModel<GroupsViewModel>()
    val state by viewModel.state.collectAsState()

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Quits", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))

        SubmittableTextField(
            label = "New group",
            imageVector = Icons.Default.Add,
            onSubmit = viewModel::addGroup,
        )

        Section(heading = "Groups") {
            if (state.groups.isEmpty()) {
                Text("No groups yet.", Modifier.padding(16.dp))
            } else {
                Column {
                    state.groups.forEach { group ->
                        Text(
                            group,
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenGroup(group) }
                                .padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}
