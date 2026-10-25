package nz.eloque.quits.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.add_group_no_account
import nz.eloque.quits.resources.app_name
import nz.eloque.quits.resources.logo
import nz.eloque.quits.resources.onboarding_step_create
import nz.eloque.quits.resources.onboarding_step_expenses
import nz.eloque.quits.resources.onboarding_step_share
import nz.eloque.quits.resources.onboarding_tagline
import nz.eloque.quits.resources.quick_split_onboarding_hint
import nz.eloque.quits.resources.quick_split_title
import nz.eloque.quits.ui.groups.GroupStartChoices
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun OnboardingScreen(
    onStartGroup: () -> Unit,
    onJoinGroup: () -> Unit,
    onQuickSplit: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Image(
            painter = painterResource(Res.drawable.logo),
            contentDescription = null,
            modifier =
                Modifier
                    .size(88.dp)
                    .shadow(6.dp, RoundedCornerShape(20.dp), clip = false),
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(
            stringResource(Res.string.onboarding_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Step(1, stringResource(Res.string.onboarding_step_create))
            Step(2, stringResource(Res.string.onboarding_step_expenses))
            Step(3, stringResource(Res.string.onboarding_step_share))
        }
        Spacer(Modifier.height(24.dp))

        GroupStartChoices(onStartGroup = onStartGroup, onJoinGroup = onJoinGroup)
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(Res.string.add_group_no_account),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(Res.string.quick_split_onboarding_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onQuickSplit) {
                Text(stringResource(Res.string.quick_split_title))
            }
        }
    }
}

@Composable
private fun Step(
    number: Int,
    text: String,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
