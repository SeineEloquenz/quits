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
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.add_group_no_account
import nz.eloque.quits.resources.add_group_tagline
import nz.eloque.quits.resources.app_name
import nz.eloque.quits.ui.groups.AddGroupContent
import org.jetbrains.compose.resources.stringResource

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
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Icon(
            Icons.Outlined.Groups,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(
            stringResource(Res.string.add_group_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        AddGroupContent(onCreate = onCreate, onJoin = onJoin, error = error, onJoinInput = onJoinInput, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(Res.string.add_group_no_account),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(24.dp))
    }
}
