package nz.eloque.quits.ui.about

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import nz.eloque.compose_kit.input.AbbreviatingText
import nz.eloque.compose_kit.scaffold.AppScaffold
import nz.eloque.quits.resources.Res
import nz.eloque.quits.resources.about_libraries
import nz.eloque.quits.resources.cd_back
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrariesScreen(onBack: () -> Unit) {
    AppScaffold(
        title = {
            AbbreviatingText(
                stringResource(Res.string.about_libraries),
                style = MaterialTheme.typography.headlineMedium,
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
        val libraries by produceLibraries {
            Res.readBytes("files/aboutlibraries.json").decodeToString()
        }
        LibrariesContainer(
            libraries = libraries,
            contentPadding = WindowInsets.navigationBars.asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        )
    }
}
