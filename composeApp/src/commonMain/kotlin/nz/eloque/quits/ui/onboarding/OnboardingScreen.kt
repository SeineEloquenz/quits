package nz.eloque.quits.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nz.eloque.quits.domain.Currency
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.add_group_no_account
import nz.eloque.quits.resources.add_group_tagline
import nz.eloque.quits.resources.app_name
import nz.eloque.quits.resources.logo
import nz.eloque.quits.ui.groups.AddGroupContent
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun OnboardingScreen(
    onCreate: (name: String, currency: Currency) -> Unit,
    onJoin: (code: String) -> Unit,
    error: String?,
    onJoinInput: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
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
