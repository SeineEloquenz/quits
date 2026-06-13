package nz.eloque.quits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import nz.eloque.quits.navigation.Screen
import nz.eloque.quits.theme.QuitsTheme
import nz.eloque.quits.ui.group.GroupDetailScreen
import nz.eloque.quits.ui.groups.GroupsScreen

@Composable
fun App() {
    QuitsTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            var screen by remember { mutableStateOf<Screen>(Screen.Groups) }
            Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                when (val current = screen) {
                    Screen.Groups ->
                        GroupsScreen(onOpenGroup = { screen = Screen.GroupDetail(it) })
                    is Screen.GroupDetail ->
                        GroupDetailScreen(name = current.name, onBack = { screen = Screen.Groups })
                }
            }
        }
    }
}
