package nz.eloque.quits.ui.groups

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.scaffold.AppScaffold
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.add_group_title
import nz.eloque.quits.resources.cd_back
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * Same join-or-create screen as [nz.eloque.quits.ui.onboarding.OnboardingScreen], reached from
 * the drawer's "add group" item once you already have at least one group — same fields, same
 * calls, just with a way back instead of being the only thing on screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGroupScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val viewModel = koinViewModel<GroupsViewModel>()
    val error by viewModel.error.collectAsState()
    val activeGroup by viewModel.activeGroup.collectAsState()

    // Only react to activeGroup changes caused by an explicit create/join on *this* screen —
    // not to whatever it happened to already be (e.g. still loading, or the group open before
    // navigating here) — so this can't fire a false "ready" before the person has done anything.
    // createGroup()/join() both call setActiveGroup() on success, so Home will already be showing
    // the new group once this pops back — no need to pass the id along.
    var requested by remember { mutableStateOf(false) }
    LaunchedEffect(activeGroup, requested) {
        if (requested && activeGroup != null) {
            onDone()
        }
    }

    AppScaffold(
        title = { Text(stringResource(Res.string.add_group_title), style = MaterialTheme.typography.headlineSmall) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.cd_back))
            }
        },
        contentHorizontalPadding = 0.dp,
    ) {
        AddGroupContent(
            onCreate = { name, currency ->
                requested = true
                viewModel.createGroup(name, currency)
            },
            onJoin = {
                requested = true
                viewModel.join(it)
            },
            error = error,
            onJoinInput = viewModel::clearError,
            modifier = Modifier,
        )
    }
}
