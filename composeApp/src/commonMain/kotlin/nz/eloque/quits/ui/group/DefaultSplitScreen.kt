package nz.eloque.quits.ui.group

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.scaffold.AppScaffold
import nz.eloque.quits.domain.GroupId
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.action_cancel
import nz.eloque.quits.resources.action_save
import nz.eloque.quits.resources.default_split_clear
import nz.eloque.quits.resources.default_split_hint
import nz.eloque.quits.resources.default_split_title
import nz.eloque.quits.ui.components.ListFieldCard
import nz.eloque.quits.ui.components.LoadingBox
import nz.eloque.quits.ui.entry.EntryForm
import nz.eloque.quits.ui.entry.toDefaultSplit
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultSplitScreen(
    groupId: GroupId,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<DefaultSplitViewModel> { parametersOf(groupId) }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.done.collect { onBack() }
    }

    AppScaffold(
        title = { Text(stringResource(Res.string.default_split_title), style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.action_cancel))
            }
        },
    ) { scrollBehavior ->
        if (!state.loaded) {
            LoadingBox(Modifier.padding(top = 32.dp))
            return@AppScaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                stringResource(Res.string.default_split_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            ListFieldCard { EntryForm(state.form, viewModel, splitOnly = true) }
            Column(Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = viewModel::save, enabled = state.form.toDefaultSplit() != null, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.action_save))
                }
                if (state.hasDefault) {
                    OutlinedButton(onClick = viewModel::clear, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(Res.string.default_split_clear))
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
