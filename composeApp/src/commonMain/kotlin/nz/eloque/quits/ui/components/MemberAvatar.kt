package nz.eloque.quits.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nz.eloque.compose_kit.components.MonogramAvatar
import nz.eloque.quits.domain.MemberId

// A small fixed palette; each member gets a stable color derived from its id. Kept in the UI
// layer (presentation concern) so the domain model stays color-free.
private val AvatarPalette =
    listOf(
        Color(0xFF00897B),
        Color(0xFF3949AB),
        Color(0xFFD81B60),
        Color(0xFF8E24AA),
        Color(0xFFF4511E),
        Color(0xFF43A047),
        Color(0xFF1E88E5),
        Color(0xFFFB8C00),
        Color(0xFF6D4C41),
        Color(0xFF00ACC1),
    )

private fun colorFor(key: String): Color {
    if (key.isEmpty()) return AvatarPalette[0]
    val hash = key.fold(0) { acc, c -> (acc * 31 + c.code) and 0x7FFFFFFF }
    return AvatarPalette[hash % AvatarPalette.size]
}

/** Circular member monogram tinted by a stable color derived from the member [id]. */
@Composable
fun MemberAvatar(
    name: String,
    id: MemberId,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
) {
    MonogramAvatar(text = name, modifier = modifier, color = colorFor(id.value), size = size)
}
