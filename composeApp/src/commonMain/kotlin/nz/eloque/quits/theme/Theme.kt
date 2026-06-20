package nz.eloque.quits.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import nz.eloque.compose_kit.theme.AppTheme
import nz.eloque.compose_kit.theme.dynamicColorSchemeOrNull

@Composable
fun QuitsTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colorScheme = dynamicColorSchemeOrNull(dark) ?: if (dark) DarkColors else LightColors
    AppTheme(colorScheme = colorScheme, content = content)
}
