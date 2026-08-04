package nz.eloque.quits.ui.about

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Source
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.components.About
import nz.eloque.compose_kit.components.AboutLink
import nz.eloque.compose_kit.input.AbbreviatingText
import nz.eloque.compose_kit.scaffold.AppScaffold
import nz.eloque.quits.BuildInfo
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.about_libraries
import nz.eloque.quits.resources.about_license
import nz.eloque.quits.resources.about_source_code
import nz.eloque.quits.resources.about_tagline
import nz.eloque.quits.resources.about_title
import nz.eloque.quits.resources.app_name
import nz.eloque.quits.resources.cd_back
import nz.eloque.quits.resources.logo
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val REPO_URL = "https://github.com/SeineEloquenz/quits"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenLibraries: () -> Unit,
) {
    AppScaffold(
        title = {
            AbbreviatingText(
                stringResource(Res.string.about_title),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.cd_back))
            }
        },
        contentHorizontalPadding = 0.dp,
    ) {
        About(
            appName = stringResource(Res.string.app_name),
            icon = painterResource(Res.drawable.logo),
            tagline = stringResource(Res.string.about_tagline),
            taglineIcon = Icons.Default.Construction,
            version = "v${BuildInfo.VERSION}-${BuildInfo.VERSION_CODE}",
            links =
                listOf(
                    AboutLink.Uri(
                        icon = Icons.Default.Source,
                        label = stringResource(Res.string.about_source_code),
                        url = REPO_URL,
                    ),
                    AboutLink.Uri(
                        icon = Icons.Default.Balance,
                        label = stringResource(Res.string.about_license),
                        url = "$REPO_URL/blob/main/LICENSE",
                    ),
                    AboutLink.Action(
                        icon = Icons.AutoMirrored.Filled.LibraryBooks,
                        label = stringResource(Res.string.about_libraries),
                        onClick = onOpenLibraries,
                    ),
                ),
        )
    }
}
