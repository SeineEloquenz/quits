package nz.eloque.quits.ui.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.components.SectionCard
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.app_name
import nz.eloque.quits.resources.groups_join_group
import nz.eloque.quits.resources.groups_new_group
import nz.eloque.quits.ui.groups.CreateGroupForm
import nz.eloque.quits.ui.groups.JoinGroupForm
import org.jetbrains.compose.resources.stringResource

/** First-run welcome shown when there are no groups yet: create a group or join one with a code. */
@Composable
fun OnboardingScreen(
    onCreate: (name: String, currencyCode: String) -> Unit,
    onJoin: (code: String) -> Unit,
    error: String?,
    onJoinInput: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Icon(
            Icons.Outlined.Groups,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(96.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(Res.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        SectionCard(heading = stringResource(Res.string.groups_new_group)) {
            CreateGroupForm(onCreate = onCreate, modifier = Modifier.fillMaxWidth())
        }
        SectionCard(heading = stringResource(Res.string.groups_join_group)) {
            JoinGroupForm(onJoin = onJoin, error = error, onInput = onJoinInput, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(24.dp))
    }
}
