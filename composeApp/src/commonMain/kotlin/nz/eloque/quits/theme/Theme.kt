package nz.eloque.quits.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import nz.eloque.compose_kit.theme.AppTheme
import nz.eloque.compose_kit.theme.DefaultTypography
import nz.eloque.compose_kit.theme.dynamicColorSchemeOrNull
import nz.eloque.quits.resources.InterVariable
import nz.eloque.quits.resources.Res
import org.jetbrains.compose.resources.Font

@Composable
fun QuitsTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colorScheme = dynamicColorSchemeOrNull(dark) ?: if (dark) DarkColors else LightColors
    val fontFamily = FontFamily(Font(Res.font.InterVariable))

    AppTheme(
        colorScheme = colorScheme,
        content = content,
        typography =
            DefaultTypography.run {
                copy(
                    bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
                    bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
                    bodySmall = bodySmall.copy(fontFamily = fontFamily),
                    titleLarge = titleLarge.copy(fontFamily = fontFamily),
                    titleMedium = titleMedium.copy(fontFamily = fontFamily),
                    titleSmall = titleSmall.copy(fontFamily = fontFamily),
                    headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
                    headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
                    headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
                    displayLarge = displayLarge.copy(fontFamily = fontFamily),
                    displayMedium = displayMedium.copy(fontFamily = fontFamily),
                    displaySmall = displaySmall.copy(fontFamily = fontFamily),
                    labelLarge = labelLarge.copy(fontFamily = fontFamily),
                    labelMedium = labelMedium.copy(fontFamily = fontFamily),
                    labelSmall = labelSmall.copy(fontFamily = fontFamily),
                )
            },
    )
}
