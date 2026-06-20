package nz.eloque.quits.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Teal / cyan brand seed (distinct from heatr's red and fosswallet's purple),
// with a slate-cyan secondary and a warm amber tertiary accent for amounts/CTAs.

private val Teal40 = Color(0xFF006A60)
private val Teal80 = Color(0xFF53DBC9)
private val SlateCyan40 = Color(0xFF4A635E)
private val SlateCyan80 = Color(0xFFB1CCC5)
private val Amber40 = Color(0xFF7A5900)
private val Amber80 = Color(0xFFF0BF48)

val LightColors =
    lightColorScheme(
        primary = Teal40,
        secondary = SlateCyan40,
        tertiary = Amber40,
    )

val DarkColors =
    darkColorScheme(
        primary = Teal80,
        secondary = SlateCyan80,
        tertiary = Amber80,
    )
